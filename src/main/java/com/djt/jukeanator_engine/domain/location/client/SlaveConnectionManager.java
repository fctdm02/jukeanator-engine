package com.djt.jukeanator_engine.domain.location.client;

import java.lang.reflect.Type;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.location.dto.CommandEnvelope;
import com.djt.jukeanator_engine.domain.location.dto.CommandReplyDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationEventMessage;
import com.djt.jukeanator_engine.domain.location.dto.LocationPricingConfigDto;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.event.AllSongsDonePlayingEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackPausedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStoppedEvent;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;

/**
 * Slave-only. Opens and maintains the outbound persistent connection to
 * {@code {master-instance-url}/ws-slave} — only the slave can dial out (no public IP), so this is
 * a STOMP <em>client</em> connecting to master's STOMP server. Executes commands master sends
 * against this slave's own real, local {@code SongQueueService}/{@code SongPlayerService} (the
 * exact same beans the JFC/Swing UI and local touchscreen already use), replies with the result,
 * and forwards this slave's own domain events upward for master to rebroadcast to web/mobile
 * clients.
 *
 * <p>
 * <strong>Must never block or degrade local operation.</strong> If the master is unreachable —
 * at startup, or at any later point — the slave's local UI, bill acceptor, queue, and player keep
 * working exactly as they do in standalone mode. Every network operation here is asynchronous and
 * failures are swallowed (logged, not thrown) for that reason.
 *
 * @author tmyers
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.mode",
    havingValue = "slave")
public class SlaveConnectionManager {

  private static final Logger log = LoggerFactory.getLogger(SlaveConnectionManager.class);

  private static final long RECONNECT_CHECK_INTERVAL_SECONDS = 5;

  private final AppProperties appProperties;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;
  private final SongLibraryService songLibraryService;
  private final LocationService locationService;
  private final ObjectMapper objectMapper;
  private final JukeANatorUserInterfaceProperties userInterfaceProperties;

  private final WebSocketStompClient stompClient;
  private final ScheduledExecutorService reconnectExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "slave-connection-manager");
        t.setDaemon(true);
        return t;
      });

  private final AtomicReference<StompSession> currentSession = new AtomicReference<>();
  private final AtomicBoolean connecting = new AtomicBoolean(false);

  public SlaveConnectionManager(AppProperties appProperties, SongQueueService songQueueService,
      SongPlayerService songPlayerService, SongLibraryService songLibraryService,
      LocationService locationService, ObjectMapper objectMapper,
      JukeANatorUserInterfaceProperties userInterfaceProperties) {

    this.appProperties = appProperties;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    this.songLibraryService = songLibraryService;
    this.locationService = locationService;
    this.objectMapper = objectMapper;
    this.userInterfaceProperties = userInterfaceProperties;

    this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    // Not MappingJackson2MessageConverter (deprecated since Spring 7, removed in a future
    // release) — its replacement, JacksonJsonMessageConverter, is Jackson 3.x-based
    // (tools.jackson.databind) and can't take this app's Jackson 2.x ObjectMapper. That's fine
    // here: this converter only handles the outer CommandEnvelope/CommandReplyDto/
    // LocationEventMessage envelope (plain fields + a generic Object payload), never anything
    // needing this app's specific Jackson 2 module config — the payload itself is decoded
    // separately via the app's own ObjectMapper in dispatch()/convert() below, regardless of
    // which Jackson generation produced the intermediate LinkedHashMap.
    this.stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    // Required by WebSocketStompClient whenever STOMP heartbeats are enabled (see
    // connectHeaders.setHeartbeat below) — it schedules the heartbeat send/expect timers.
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler =
        new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    taskScheduler.setPoolSize(1);
    taskScheduler.setThreadNamePrefix("slave-connection-heartbeat-");
    taskScheduler.setDaemon(true);
    taskScheduler.initialize();
    this.stompClient.setTaskScheduler(taskScheduler);

    reconnectExecutor.scheduleWithFixedDelay(this::maintainConnection, 0,
        RECONNECT_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void shutdown() {
    reconnectExecutor.shutdownNow();
    StompSession session = currentSession.get();
    if (session != null && session.isConnected()) {
      session.disconnect();
    }
  }

  private void maintainConnection() {

    StompSession session = currentSession.get();
    if (session != null && session.isConnected()) {
      return;
    }
    if (!connecting.compareAndSet(false, true)) {
      return;
    }

    try {
      String wsUrl = toWebSocketUrl(appProperties.getMasterInstanceUrl()) + "/ws-slave";
      StompHeaders connectHeaders = new StompHeaders();
      // Purely informational: master authenticates by location-api-key alone (see
      // StompLocationApiKeyChannelInterceptor) and hands back the authoritative id, since this
      // slave's own guess (its locally corrected metadata locationId, if known yet) may not match
      // the id an admin assigns by hand when provisioning it on master.
      Integer currentLocationId = currentLocationId();
      connectHeaders.set("location-id",
          String.valueOf(currentLocationId != null ? currentLocationId : appProperties.getLocationId()));
      connectHeaders.set("location-api-key", appProperties.getLocationApiKey());
      connectHeaders.setHeartbeat(new long[] {10_000, 10_000});

      stompClient
          .connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeaders,
              new SlaveStompSessionHandler())
          .whenComplete((session2, throwable) -> {
            connecting.set(false);
            if (throwable != null) {
              log.debug("Could not connect to master at {}: {}", wsUrl, throwable.getMessage());
            }
          });
    } catch (Exception e) {
      connecting.set(false);
      log.debug("Could not initiate connection to master", e);
    }
  }

  private Integer currentLocationId() {
    try {
      return songLibraryService.getOwnLocationId();
    } catch (Exception e) {
      return null;
    }
  }

  private static String toWebSocketUrl(String httpUrl) {
    if (httpUrl.startsWith("https://")) {
      return "wss://" + httpUrl.substring("https://".length());
    }
    if (httpUrl.startsWith("http://")) {
      return "ws://" + httpUrl.substring("http://".length());
    }
    return httpUrl;
  }

  @EventListener
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {
    sendEvent("queue", event.queuedSongs());
  }

  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {
    sendEvent("now-playing", event.songQueueEntry().song());
    sendEvent("playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handlePlaybackPaused(SongPlaybackPausedEvent event) {
    sendEvent("playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleSongPlaybackStoppedEvent(SongPlaybackStoppedEvent event) {
    sendEvent("now-playing", null);
    sendEvent("playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleAllSongsDonePlayingEvent(AllSongsDonePlayingEvent event) {
    sendEvent("now-playing", null);
    sendEvent("playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  /**
   * Pushes this slave's own {@code user-interface.*} credit-config bundle to master so master can
   * price this location's Web/Mobile UI consistently with what this slave's JFC/Swing UI charges
   * — the slave's application.yml remains the single source of truth; master just caches this on
   * the corresponding {@code LocationEntity}. Sent on every (re)connect so config changes and
   * reconnect-after-downtime both stay in sync.
   */
  private void sendPricingConfig(StompSession session) {
    try {
      session.send("/location-pricing-config",
          new LocationPricingConfigDto(userInterfaceProperties.getPriorityCostMultiplier(),
              userInterfaceProperties.getCreditsPerDollar(),
              userInterfaceProperties.getFiveDollarBonusCredits(),
              userInterfaceProperties.getTenDollarBonusCredits(),
              userInterfaceProperties.getWebCostMultiplier(),
              userInterfaceProperties.isDisplayCurrencyForCost()));
    } catch (Exception e) {
      log.debug("Could not send pricing config to master", e);
    }
  }

  private void sendEvent(String eventType, Object payload) {

    StompSession session = currentSession.get();
    if (session == null || !session.isConnected()) {
      return;
    }
    try {
      session.send("/location-events", new LocationEventMessage(eventType, payload));
    } catch (Exception e) {
      log.debug("Could not forward {} event to master", eventType, e);
    }
  }

  /** Handles the connected session: subscribes for commands, executes them locally, replies. */
  private class SlaveStompSessionHandler extends StompSessionHandlerAdapter {

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {

      log.info("Connected to master at {}", appProperties.getMasterInstanceUrl());
      currentSession.set(session);
      session.subscribe("/user/queue/commands", new CommandFrameHandler());
      session.subscribe("/user/queue/location-id-confirmed", new LocationIdConfirmedFrameHandler());
      sendPricingConfig(session);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      log.debug("Transport error on master connection, will reconnect", exception);
      currentSession.compareAndSet(session, null);
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers,
        byte[] payload, Throwable exception) {
      log.warn("Error handling STOMP frame from master: {}", command, exception);
    }
  }

  private class CommandFrameHandler implements org.springframework.messaging.simp.stomp.StompFrameHandler {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return CommandEnvelope.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {

      CommandEnvelope envelope = (CommandEnvelope) payload;
      CommandReplyDto reply = executeCommand(envelope);
      StompSession session = currentSession.get();
      if (session != null && session.isConnected()) {
        session.send("/location-command-reply", reply);
      }
    }
  }

  /**
   * Receives the authoritative locationId master resolved via {@code location-api-key} alone
   * (see {@code StompLocationApiKeyChannelInterceptor}) and corrects this slave's local {@code
   * locationMetadata.txt} if it doesn't already match — this is the "communicate back the actual
   * locationId" step of the manual-provisioning workflow: an admin's hand-inserted row on master
   * can carry a different id than this slave's own initial guess.
   */
  private class LocationIdConfirmedFrameHandler
      implements org.springframework.messaging.simp.stomp.StompFrameHandler {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return Integer.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {

      Integer confirmedLocationId = (Integer) payload;
      try {
        Integer currentLocationId = songLibraryService.getOwnLocationId();
        if (!confirmedLocationId.equals(currentLocationId)) {
          log.info("Master assigned locationId {} (was {} locally) — correcting local location record",
              confirmedLocationId, currentLocationId);
          locationService.reconcileOwnLocationId(confirmedLocationId);
          // Re-key SongLibraryServiceImpl's own locationId->root cache under the corrected id --
          // it was populated under the old (pre-correction) id at startup.
          songLibraryService.reinitializeOwnLocation();
        }
      } catch (Exception e) {
        log.warn("Could not reconcile confirmed locationId {} with local location record",
            confirmedLocationId, e);
      }
    }
  }

  private CommandReplyDto executeCommand(CommandEnvelope envelope) {

    // This runs on the STOMP client's own message-handling thread, which — like any other
    // non-HTTP-request thread in REST/remote mode — starts with an empty SecurityContextHolder.
    // ServiceSecurityAspect requires an authenticated principal for every service call, so this
    // needs the same SystemPrincipal treatment SongPlayerServiceImpl's queue-processing thread
    // already uses (see SecurityContextPropagatingRunnable).
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(SystemPrincipal.SystemAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(ctx);
    try {
      Object result = dispatch(envelope.commandType(), envelope.payload());
      return new CommandReplyDto(envelope.correlationId(), true, result, null);
    } catch (Exception e) {
      log.warn("Command {} failed locally", envelope.commandType(), e);
      return new CommandReplyDto(envelope.correlationId(), false, null, e.getMessage());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private Object dispatch(String commandType, Object payload) throws Exception {

    Integer ownLocationId = songLibraryService.getOwnLocationId();

    switch (commandType) {
      case "getHighestPriority":
        return songQueueService.getHighestPriority(ownLocationId);
      case "getQueuedSongs":
        return songQueueService.getQueuedSongs(ownLocationId);
      case "isSongEligibleForQueue": {
        EligibilityCheckPayload p = convert(payload, EligibilityCheckPayload.class);
        return songQueueService.isSongEligibleForQueue(ownLocationId, p.albumId(), p.songId(),
            p.priority());
      }
      case "addSongToQueue":
        return songQueueService.addSongToQueue(ownLocationId,
            convert(payload, AddSongToQueueRequest.class));
      case "addAlbumToQueue":
        return songQueueService.addAlbumToQueue(ownLocationId,
            convert(payload, AddAlbumToQueueRequest.class));
      case "addMultipleSongsToQueue":
        return songQueueService.addMultipleSongsToQueue(ownLocationId,
            convert(payload, AddMultipleSongsToQueueRequest.class));
      case "flushQueue":
        return songQueueService.flushQueue(ownLocationId);
      case "randomizeQueue":
        return songQueueService.randomizeQueue(ownLocationId);
      case "moveSongUpInQueue":
        return songQueueService.moveSongUpInQueue(ownLocationId,
            convert(payload, ChangeSongQueueRequest.class));
      case "moveSongDownInQueue":
        return songQueueService.moveSongDownInQueue(ownLocationId,
            convert(payload, ChangeSongQueueRequest.class));
      case "removeSongDownFromQueue":
        return songQueueService.removeSongDownFromQueue(ownLocationId,
            convert(payload, ChangeSongQueueRequest.class));
      case "saveQueueAsPlaylist":
        return songQueueService.saveQueueAsPlaylist(ownLocationId, convert(payload, String.class));
      case "loadPlaylistIntoQueue":
        return songQueueService.loadPlaylistIntoQueue(ownLocationId,
            convert(payload, LoadPlaylistIntoQueueRequest.class));
      case "getNowPlayingSong":
        return songPlayerService.getNowPlayingSong(ownLocationId);
      case "getPlaybackStatus":
        return songPlayerService.getPlaybackStatus(ownLocationId);
      case "playNextTrack":
        songPlayerService.playNextTrack(ownLocationId);
        return null;
      case "pause":
        songPlayerService.pause(ownLocationId);
        return null;
      case "stop":
        songPlayerService.stop(ownLocationId);
        return null;
      case "lockQueue":
        songPlayerService.lockQueue(ownLocationId);
        return null;
      case "unlockQueue":
        songPlayerService.unlockQueue(ownLocationId);
        return null;
      default:
        throw new IllegalArgumentException("Unknown commandType: " + commandType);
    }
  }

  /**
   * {@code ObjectMapper.convertValue(Object, Class)} does not reliably trigger Jackson's implicit
   * single-constructor binding for classes like {@code AddSongToQueueRequest} (no default
   * constructor, no {@code @JsonCreator}) the way deserializing from a raw JSON token stream does
   * — round-tripping through an actual JSON string reproduces the exact path Spring MVC's
   * {@code @RequestBody} binding already relies on elsewhere in this app.
   */
  private <T> T convert(Object payload, Class<T> targetClass) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.readValue(objectMapper.writeValueAsString(payload), targetClass);
  }

  private record EligibilityCheckPayload(Integer albumId, Integer songId, Integer priority) {}
}
