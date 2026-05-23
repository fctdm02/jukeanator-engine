package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;

public class GenreDto implements Comparable<GenreDto> {
  
  private Integer genreId;
  private String genreName;
  private List<Integer> albumIds;
  private Integer numPlays;
  
  public GenreDto(
      Integer genreId,
      String genreName,
      List<Integer> albumIds,
      Integer numPlays) {
    super();
    this.genreId = genreId;
    this.genreName = genreName;
    this.albumIds = albumIds;
    this.numPlays = numPlays;
  }
  
  public Integer getGenreId() {
    return genreId;
  }

  public String getGenreName() {
    return genreName;
  }
  
  public List<Integer> getAlbumIds() {
    return albumIds;
  }
  
  public Integer getNumPlays() {
    return numPlays;
  }

  @Override
  public int hashCode() {
    return Objects.hash(genreId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    GenreDto other = (GenreDto) obj;
    return Objects.equals(genreId, other.genreId);
  }
  
  @Override
  public String toString() {
    return "GenreDto [genreName=" + genreName + "]";
  }

  @Override
  public int compareTo(GenreDto that) {
    return this.numPlays.compareTo(that.numPlays);
  }  
}