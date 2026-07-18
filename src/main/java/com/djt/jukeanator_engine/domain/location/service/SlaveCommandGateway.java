package com.djt.jukeanator_engine.domain.location.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.djt.jukeanator_engine.domain.location.dto.CommandEnvelope;
import com.djt.jukeanator_engine.domain.location.dto.CommandReplyDto;
import com.djt.jukeanator_engine.domain.location.exception.LocationOfflineException;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Master-only. Bridges a synchronous-looking master-side service call to an async round trip
 * over the {@code /ws-slave} push channel: only the slave can dial master (no public IP), so
 * master cannot simply {@code RestClient.post()} a slave the way the dead
 * {@code SongQueueServiceHttpClient} does. Instead, a command is pushed to the slave's user
 * destination via {@code convertAndSendToUser}, and this class holds the calling thread on a
 * {@link CompletableFuture} keyed by a correlationId until the slave's reply arrives (via
 * {@code LocationEventStompController}) or {@code location.command-timeout-ms} elapses.
 *
 * @author tmyers
 */
public class SlaveCommandGateway {

  private static final Logger log = LoggerFactory.getLogger(SlaveCommandGateway.class);

  private static final String COMMAND_DESTINATION = "/queue/commands";

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;
  private final ConnectedSlaveRegistry connectedSlaveRegistry;
  private final long commandTimeoutMs;

  private final ConcurrentHashMap<String, CompletableFuture<CommandReplyDto>> pendingReplies =
      new ConcurrentHashMap<>();

  public SlaveCommandGateway(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper,
      ConnectedSlaveRegistry connectedSlaveRegistry, long commandTimeoutMs) {
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
    this.connectedSlaveRegistry = connectedSlaveRegistry;
    this.commandTimeoutMs = commandTimeoutMs;
  }

  /** Sends a command and awaits the reply, converting its payload to {@code replyType}. */
  public <T> T sendCommand(String locationId, String commandType, Object payload,
      Class<T> replyType) {

    CommandReplyDto reply = sendCommandAndAwaitReply(locationId, commandType, payload);
    if (replyType == Void.class || reply.payload() == null) {
      return null;
    }
    try {
      return objectMapper.readValue(objectMapper.writeValueAsString(reply.payload()), replyType);
    } catch (Exception e) {
      throw new LocationServiceException(
          "Could not parse reply payload from locationId: " + locationId, e);
    }
  }

  /** Same as {@link #sendCommand(String, String, Object, Class)} but for generic/list reply types. */
  public <T> T sendCommand(String locationId, String commandType, Object payload,
      TypeReference<T> replyTypeRef) {

    CommandReplyDto reply = sendCommandAndAwaitReply(locationId, commandType, payload);
    if (reply.payload() == null) {
      return null;
    }
    try {
      return objectMapper.readValue(objectMapper.writeValueAsString(reply.payload()),
          replyTypeRef);
    } catch (Exception e) {
      throw new LocationServiceException(
          "Could not parse reply payload from locationId: " + locationId, e);
    }
  }

  /** Convenience for commands with no meaningful reply payload (e.g. {@code pause()}). */
  public void sendCommand(String locationId, String commandType, Object payload) {
    sendCommandAndAwaitReply(locationId, commandType, payload);
  }

  private CommandReplyDto sendCommandAndAwaitReply(String locationId, String commandType,
      Object payload) {

    // Fail fast rather than waiting out a full timeout for a location that is obviously not
    // connected right now.
    if (!connectedSlaveRegistry.isConnected(locationId)) {
      throw new LocationOfflineException(locationId);
    }

    String correlationId = UUID.randomUUID().toString();
    CompletableFuture<CommandReplyDto> future = new CompletableFuture<>();
    pendingReplies.put(correlationId, future);

    try {
      CommandEnvelope envelope = new CommandEnvelope(correlationId, commandType, payload);
      messagingTemplate.convertAndSendToUser(locationId, COMMAND_DESTINATION, envelope);

      CommandReplyDto reply;
      try {
        reply = future.get(commandTimeoutMs, TimeUnit.MILLISECONDS);
      } catch (TimeoutException te) {
        throw new LocationOfflineException(locationId);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new LocationServiceException(
            "Interrupted awaiting reply from locationId: " + locationId, ie);
      } catch (ExecutionException ee) {
        throw new LocationServiceException(
            "Command failed for locationId: " + locationId, ee.getCause());
      }

      if (!reply.success()) {
        throw new LocationServiceException("Command " + commandType + " rejected by locationId "
            + locationId + ": " + reply.errorMessage());
      }
      return reply;
    } finally {
      pendingReplies.remove(correlationId);
    }
  }

  /** Called by {@code LocationEventStompController} when a slave's reply frame arrives. */
  public void completeReply(CommandReplyDto reply) {

    CompletableFuture<CommandReplyDto> future = pendingReplies.get(reply.correlationId());
    if (future == null) {
      log.debug("Received reply for unknown/expired correlationId: {}", reply.correlationId());
      return;
    }
    future.complete(reply);
  }
}
