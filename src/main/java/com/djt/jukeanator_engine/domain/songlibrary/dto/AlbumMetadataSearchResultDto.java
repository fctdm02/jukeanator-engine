package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class AlbumMetadataSearchResultDto {
  
  private final String artistName;
  private final String albumName;
  private final String recordLabel;
  private final String releaseDate;
  private final String genre;
  private final String coverArtUrl;
  
  public AlbumMetadataSearchResultDto(String artistName, String albumName, String recordLabel,
      String releaseDate, String genre, String coverArtUrl) {
    super();
    this.artistName = artistName;
    this.albumName = albumName;
    this.recordLabel = recordLabel;
    this.releaseDate = releaseDate;
    this.genre = genre;
    this.coverArtUrl = coverArtUrl;
  }

  public String getArtistName() {
    return artistName;
  }

  public String getAlbumName() {
    return albumName;
  }

  public String getRecordLabel() {
    return recordLabel;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public String getGenre() {
    return genre;
  }

  public String getCoverArtUrl() {
    return coverArtUrl;
  }  
}
