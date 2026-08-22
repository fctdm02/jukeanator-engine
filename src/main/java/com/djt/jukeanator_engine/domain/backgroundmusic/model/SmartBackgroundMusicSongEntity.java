package com.djt.jukeanator_engine.domain.backgroundmusic.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

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
 * <p>
 * Own complete table ({@code smart_background_music_songs}) under {@link BackgroundMusicSongEntity}'s
 * {@code TABLE_PER_CLASS} inheritance -- see that class's javadoc.
 *
 * @author tmyers
 */
@Entity
@Table(name = "smart_background_music_songs")
public class SmartBackgroundMusicSongEntity extends BackgroundMusicSongEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "source_song")
  private String sourceSong; // natural identity (path) of the song that seeded this pick

  @Column(name = "source_song_num_plays")
  private Integer sourceSongNumPlays; // sourceSong's play count as of when this pick was made

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false)
  private SmartAdditionReason reason;

  protected SmartBackgroundMusicSongEntity() {} // for JPA

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
