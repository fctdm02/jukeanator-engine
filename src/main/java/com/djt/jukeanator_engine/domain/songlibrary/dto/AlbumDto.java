package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AlbumDto(Integer genreId, String genreName, Integer artistId, String artistName,
    Integer albumId, String albumName, Boolean hasExplicit, String recordLabel,
    String releaseDate, String coverArtPath, Boolean isCompilation, Integer songNumPlays,
    List<SongDto> songs, List<Integer> matchedSongIds) {

  /**
   * {@code matchedSongIds} is only set on a search result album that was pulled in solely by a
   * song-artist match (e.g. a compilation track credited to the searched artist), or on another
   * artist's album listed in an artist's own view: it names the matching / credited tracks, so the
   * album detail can list just those. Null means show every track.
   */
  public AlbumDto(Integer genreId, String genreName, Integer artistId, String artistName,
      Integer albumId, String albumName, Boolean hasExplicit, String recordLabel,
      String releaseDate, String coverArtPath, Boolean isCompilation, Integer songNumPlays,
      List<SongDto> songs) {
    this(genreId, genreName, artistId, artistName, albumId, albumName, hasExplicit, recordLabel,
        releaseDate, coverArtPath, isCompilation, songNumPlays, songs, null);
  }

  public AlbumDto withMatchedSongIds(List<Integer> ids) {
    return new AlbumDto(genreId, genreName, artistId, artistName, albumId, albumName, hasExplicit,
        recordLabel, releaseDate, coverArtPath, isCompilation, songNumPlays, songs, ids);
  }

  /** {@link #matchedSongIds} as a set for track-list filtering, or null to show every track. */
  public Set<Integer> visibleSongIds() {
    return matchedSongIds != null ? Set.copyOf(matchedSongIds) : null;
  }

  public int numSongs() {
    return songs.size();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    AlbumDto other = (AlbumDto) obj;
    return Objects.equals(albumId, other.albumId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId);
  }

  @Override
  public String toString() {
    return "AlbumDto [artistName=" + artistName + ", albumName=" + albumName + "]";
  }
}
