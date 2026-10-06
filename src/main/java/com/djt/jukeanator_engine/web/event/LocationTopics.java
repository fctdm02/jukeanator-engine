package com.djt.jukeanator_engine.web.event;

/**
 * The STOMP topics the Web/Mobile UI subscribes to for one location's live state. Every
 * {@code app.mode} publishes the same topics with the same payloads: a standalone/slave instance
 * for its own location ({@link WebSocketEventBroadcaster}), and master for every connected slave
 * by relaying the events each slave forwards ({@code LocationEventStompController}).
 *
 * <ul>
 * <li>{@link #queue} -- the whole queue, {@code List<SongQueueEntryDto>} ({@code []} when
 * empty)</li>
 * <li>{@link #nowPlaying} -- {@code {"song": SongDto}}, with {@code song} null when nothing is
 * playing ({@link NowPlayingMessage})</li>
 * <li>{@link #playbackStatus} -- {@code SongPlaybackStatusDto}</li>
 * </ul>
 */
public final class LocationTopics {

  /** The event types a slave forwards to master, which master republishes under these topics. */
  public static final String QUEUE = "queue";
  public static final String NOW_PLAYING = "now-playing";
  public static final String PLAYBACK_STATUS = "playback-status";

  private LocationTopics() {}

  public static String queue(Object locationId) {
    return topic(locationId, QUEUE);
  }

  public static String nowPlaying(Object locationId) {
    return topic(locationId, NOW_PLAYING);
  }

  public static String playbackStatus(Object locationId) {
    return topic(locationId, PLAYBACK_STATUS);
  }

  public static String topic(Object locationId, String eventType) {
    return "/topic/location/" + locationId + "/" + eventType;
  }
}
