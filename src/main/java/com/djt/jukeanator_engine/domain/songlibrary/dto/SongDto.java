package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.io.Serializable;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SongDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final Integer genreId;
  private final String genreName;
  private final Integer artistId;
  private final String artistName;
  private final Integer albumId;
  private final String albumName;
  private final String coverArtPath;
  private final Integer songId;
  private final String songName;
  private final Integer trackNumber;
  private final Integer numPlays;

  /**
   * See {@code AddSongToQueueRequest} for why this needs an explicit {@code @JsonCreator}.
   */
  @JsonCreator
  public SongDto(@JsonProperty("genreId") Integer genreId,
      @JsonProperty("genreName") String genreName, @JsonProperty("artistId") Integer artistId,
      @JsonProperty("artistName") String artistName, @JsonProperty("albumId") Integer albumId,
      @JsonProperty("albumName") String albumName,
      @JsonProperty("coverArtPath") String coverArtPath, @JsonProperty("songId") Integer songId,
      @JsonProperty("songName") String songName,
      @JsonProperty("trackNumber") Integer trackNumber,
      @JsonProperty("numPlays") Integer numPlays) {
    super();
    this.genreId = genreId;
    this.genreName = genreName;
    this.artistId = artistId;
    this.artistName = artistName;
    this.albumId = albumId;
    this.albumName = albumName;
    this.coverArtPath = coverArtPath;
    this.songId = songId;
    this.songName = songName;
    this.trackNumber = trackNumber;
    this.numPlays = numPlays;
  }

  public Integer getGenreId() {
    return genreId;
  }

  public String getGenreName() {
    return genreName;
  }

  public Integer getArtistId() {
    return artistId;
  }

  public String getArtistName() {
    return artistName;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public String getAlbumName() {
    return albumName;
  }

  public String getCoverArtPath() {
    return coverArtPath;
  }

  public Integer getSongId() {
    return songId;
  }

  public String getSongName() {
    return songName;
  }

  public Integer getTrackNumber() {
    return trackNumber;
  }

  public Integer getNumPlays() {
    return numPlays;
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId, songId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SongDto other = (SongDto) obj;
    return Objects.equals(albumId, other.albumId) && Objects.equals(songId, other.songId);
  }

  @Override
  public String toString() {
    return "SongDto [artistId=" + artistId + ", artistName=" + artistName + ", albumId=" + albumId
        + ", albumName=" + albumName + ", songId=" + songId + ", songName=" + songName
        + ", trackNumber=" + trackNumber + "]";
  }
}
