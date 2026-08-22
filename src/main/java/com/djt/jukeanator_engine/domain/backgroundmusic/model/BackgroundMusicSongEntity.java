package com.djt.jukeanator_engine.domain.backgroundmusic.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * {@code TABLE_PER_CLASS} is the root of a two-table inheritance hierarchy shared with {@link
 * SmartBackgroundMusicSongEntity} (own table: {@code smart_background_music_songs}) -- each
 * concrete subtype gets its own complete table with no shared parent table and no discriminator
 * column, mirroring the pre-existing split between {@code BackgroundMusicSongs.json} and {@code
 * SmartBackgroundMusicSongs.json}. See {@code
 * com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepositoryJpaImpl}'s
 * class javadoc for how queries against this table stay restricted to exact-type rows.
 *
 * @author tmyers
 */
@Entity
@Table(name = "background_music_songs")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class BackgroundMusicSongEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "song_file_path", nullable = false)
  private String songFilePath;

  @Column(name = "time_last_played")
  private Instant timeLastPlayed; // null means the song has not yet been played this cycle

  @Column(name = "number_of_plays", nullable = false)
  private int numberOfPlays;

  protected BackgroundMusicSongEntity() {} // for JPA

  public BackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath) {
    super(persistentIdentity);
    requireNonNull(songFilePath, "songFilePath cannot be null");
    this.songFilePath = songFilePath;
    this.timeLastPlayed = null;
    this.numberOfPlays = 0;
  }

  public BackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath,
      Instant timeLastPlayed, int numberOfPlays) {
    super(persistentIdentity);
    requireNonNull(songFilePath, "songFilePath cannot be null");
    this.songFilePath = songFilePath;
    this.timeLastPlayed = timeLastPlayed;
    this.numberOfPlays = numberOfPlays;
  }

  @Override
  public String getNaturalIdentity() {
    return this.songFilePath;
  }

  public String getSongFilePath() {
    return songFilePath;
  }

  public void setSongFilePath(String songFilePath) {
    this.songFilePath = songFilePath;
  }

  public Instant getTimeLastPlayed() {
    return timeLastPlayed;
  }

  public void setTimeLastPlayed(Instant timeLastPlayed) {
    this.timeLastPlayed = timeLastPlayed;
  }

  public int getNumberOfPlays() {
    return numberOfPlays;
  }

  public void setNumberOfPlays(int numberOfPlays) {
    this.numberOfPlays = numberOfPlays;
  }

  public boolean isNotYetPlayed() {
    return this.timeLastPlayed == null;
  }

  public void markPlayed(Instant when) {
    this.timeLastPlayed = when;
    this.numberOfPlays = this.numberOfPlays + 1;
  }
}
