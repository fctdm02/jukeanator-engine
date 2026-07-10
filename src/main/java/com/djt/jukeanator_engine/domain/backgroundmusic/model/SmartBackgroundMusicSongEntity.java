package com.djt.jukeanator_engine.domain.backgroundmusic.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;

/**
 * A {@link BackgroundMusicSongEntity} that was dynamically selected as a "smart addition" —
 * e.g. the same artist/album as, or a popular song from the same genre as, some other
 * background-music song, or a song from a favorite album (see
 * {@link SmartAdditionReason#SONG_FROM_FAVORITE_ALBUM}). {@link #getSourceSong()} and
 * {@link #getReason()} record why this particular song was chosen.
 *
 * <p>
 * {@link #getSourceSong()} and {@link #getSourceSongNumPlays()} are {@code null} for
 * {@link SmartAdditionReason#SONG_FROM_FAVORITE_ALBUM} entries — favorite-album songs are
 * included by virtue of their album, not because some other song seeded the pick.
 *
 * @author tmyers
 */
public class SmartBackgroundMusicSongEntity extends BackgroundMusicSongEntity {

  private static final long serialVersionUID = 1L;

  private String sourceSong; // natural identity (path) of the song that seeded this pick
  private Integer sourceSongNumPlays; // sourceSong's play count as of when this pick was made
  private SmartAdditionReason reason;

  public SmartBackgroundMusicSongEntity() {}

  public SmartBackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath,
      String sourceSong, Integer sourceSongNumPlays, SmartAdditionReason reason) {
    super(persistentIdentity, songFilePath);
    requireNonNull(reason, "reason cannot be null");
    this.sourceSong = sourceSong;
    this.sourceSongNumPlays = sourceSongNumPlays;
    this.reason = reason;
  }

  public SmartBackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath,
      Instant timeLastPlayed, int numberOfPlays, String sourceSong, Integer sourceSongNumPlays,
      SmartAdditionReason reason) {
    super(persistentIdentity, songFilePath, timeLastPlayed, numberOfPlays);
    requireNonNull(reason, "reason cannot be null");
    this.sourceSong = sourceSong;
    this.sourceSongNumPlays = sourceSongNumPlays;
    this.reason = reason;
  }

  public String getSourceSong() {
    return sourceSong;
  }

  public void setSourceSong(String sourceSong) {
    this.sourceSong = sourceSong;
  }

  public Integer getSourceSongNumPlays() {
    return sourceSongNumPlays;
  }

  public void setSourceSongNumPlays(Integer sourceSongNumPlays) {
    this.sourceSongNumPlays = sourceSongNumPlays;
  }

  public SmartAdditionReason getReason() {
    return reason;
  }

  public void setReason(SmartAdditionReason reason) {
    this.reason = reason;
  }
}
