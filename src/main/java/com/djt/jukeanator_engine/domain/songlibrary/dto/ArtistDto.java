package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;

public class ArtistDto {

  private Integer artistId;
  private String artistName;
  private List<AlbumDto> albums;

  public ArtistDto(Integer artistId, String artistName, List<AlbumDto> albums) {
    super();
    this.artistId = artistId;
    this.artistName = artistName;
    this.albums = albums;
  }

  public Integer getArtistId() {
    return artistId;
  }

  public String getArtistName() {
    return artistName;
  }

  public List<AlbumDto> getAlbums() {
    return albums;
  }

  @Override
  public int hashCode() {
    return Objects.hash(artistId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ArtistDto other = (ArtistDto) obj;
    return Objects.equals(artistId, other.artistId);
  }

  @Override
  public String toString() {
    return "ArtistDto [" + this.artistName + "]";
  }

  public String getCoverArtPath() {

    String coverArtPath = "";

    // Return the first, most popular album that is not a compilation
    // If all are compilations, then return t
    int maxAlbumNumPlays = 0;
    for (AlbumDto album : albums) {

      int albumNumPlays = album.getNumPlays();
      if (!album.isCompilation() && albumNumPlays > maxAlbumNumPlays) {

        maxAlbumNumPlays = album.getNumPlays();
        coverArtPath = album.getCoverArtPath();
      }
    }

    // As a failsafe, if coverArtPath is still empty, then
    // set it to be from the first album: NOTE: All artists
    // are going to have at least one album.
    if (coverArtPath.equals("")) {
      coverArtPath = albums.get(0).getCoverArtPath();
    }
    return coverArtPath;
  }

  public Integer getAlbumCount() {
    return Integer.valueOf(albums.size());
  }

  public Integer getSongCount() {

    int songCount = 0;
    for (AlbumDto album : albums) {

      if (!album.isCompilation()) {
        songCount = songCount + album.getSongs().size();
      } else {
        for (SongDto song : album.getSongs()) {
          if (song.getArtistName().equals(artistName)) {
            songCount = songCount + 1;
          }
        }
      }
    }
    return Integer.valueOf(songCount);
  }

  public Integer getNumPlays() {

    int numPlays = 0;
    for (AlbumDto album : albums) {

      numPlays = numPlays + album.getNumPlays();
    }
    return Integer.valueOf(numPlays);
  }
}
