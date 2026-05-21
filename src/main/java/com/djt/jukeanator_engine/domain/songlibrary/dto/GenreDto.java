package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class GenreDto {
  
  private Integer genreId;
  private String genreName;
  
  public GenreDto(
      Integer genreId,
      String genreName) {
    super();
    this.genreId = genreId;
    this.genreName = genreName;
  }
  
  public Integer getGenreId() {
    return genreId;
  }

  public String getGenreName() {
    return genreName;
  }
}