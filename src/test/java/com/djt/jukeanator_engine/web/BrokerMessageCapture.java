package com.djt.jukeanator_engine.web;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Records every message the server hands to its STOMP broker -- what a browser subscribed over
 * {@code /ws} would receive -- without opening a socket. Attached to the {@code brokerChannel},
 * where {@code SimpMessagingTemplate} sends both topic broadcasts ({@code /topic/queue}) and
 * user-addressed messages, the latter still in their {@code /user/{email}/queue/...} form before
 * {@code UserDestinationMessageHandler} resolves them to that user's sessions.
 */
final class BrokerMessageCapture implements ChannelInterceptor {

  private static final long WAIT_MILLIS = 5_000L;

  /** One message as the UI's {@code JSON.parse(frame.body)} would see it. */
  record CapturedMessage(String destination, JsonNode payload) {}

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<CapturedMessage> messages = new CopyOnWriteArrayList<>();
  private final AbstractSubscribableChannel brokerChannel;

  BrokerMessageCapture(AbstractSubscribableChannel brokerChannel) {
    this.brokerChannel = brokerChannel;
    brokerChannel.addInterceptor(this);
  }

  void detach() {
    brokerChannel.removeInterceptor(this);
  }

  void clear() {
    messages.clear();
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {

    String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
    if (destination != null && message.getPayload() instanceof byte[] bytes) {
      try {
        messages.add(new CapturedMessage(destination, objectMapper.readTree(bytes)));
      } catch (Exception e) {
        // Not JSON -- nothing the UI subscribes to.
      }
    }
    return message;
  }

  List<CapturedMessage> to(String destination) {
    return messages.stream().filter(m -> destination.equals(m.destination())).toList();
  }

  /** The destination a {@code convertAndSendToUser(email, destination, ...)} message lands on. */
  static String userDestination(String emailAddress, String destination) {
    return "/user/" + emailAddress + destination;
  }

  /**
   * Waits for the first message on {@code destination} matching {@code condition} -- listeners
   * normally run on the request thread, but this does not depend on it.
   */
  CapturedMessage await(String destination, Predicate<JsonNode> condition)
      throws InterruptedException {

    long deadline = System.currentTimeMillis() + WAIT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      for (CapturedMessage message : to(destination)) {
        if (condition.test(message.payload())) {
          return message;
        }
      }
      Thread.sleep(25);
    }
    fail("No matching STOMP message on " + destination + "; received: " + messages);
    return null;
  }
}
