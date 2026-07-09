package com.djt.jukeanator_engine.domain.backgroundmusic.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;

/**
 * A {@link BackgroundMusicSongEntity} that was dynamically selected as a "smart addition" —
 * e.g. the same artist/album as, or a popular song from the same genre as, some other
 * background-music song. {@link #getSourceSong()} and {@link #getReason()} record why this
 * particular song was chosen.
 *
 * @author tmyers
 */
public class SmartBackgroundMusicSongEntity extends BackgroundMusicSongEntity {

  private static final long serialVersionUID = 1L;

  private String sourceSong; // natural identity (path) of the song that seeded this pick
  private SmartAdditionReason reason;

  public SmartBackgroundMusicSongEntity() {}

  public SmartBackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath,
      String sourceSong, SmartAdditionReason reason) {
    super(persistentIdentity, songFilePath);
    requireNonNull(sourceSong, "sourceSong cannot be null");
    requireNonNull(reason, "reason cannot be null");
    this.sourceSong = sourceSong;
    this.reason = reason;
  }

  public SmartBackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath,
      Instant timeLastPlayed, int numberOfPlays, String sourceSong, SmartAdditionReason reason) {
    super(persistentIdentity, songFilePath, timeLastPlayed, numberOfPlays);
    requireNonNull(sourceSong, "sourceSong cannot be null");
    requireNonNull(reason, "reason cannot be null");
    this.sourceSong = sourceSong;
    this.reason = reason;
  }

  public String getSourceSong() {
    return sourceSong;
  }

  public void setSourceSong(String sourceSong) {
    this.sourceSong = sourceSong;
  }

  public SmartAdditionReason getReason() {
    return reason;
  }

  public void setReason(SmartAdditionReason reason) {
    this.reason = reason;
  }
}
