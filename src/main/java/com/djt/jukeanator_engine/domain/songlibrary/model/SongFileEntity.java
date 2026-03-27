package com.djt.jukeanator_engine.domain.songlibrary.model;

public class SongFileEntity extends AbstractFileEntity {
  private static final long serialVersionUID = 1L;
  
  private Integer numPlays = Integer.valueOf(0);
  
  public SongFileEntity() {}

  public SongFileEntity(AlbumFolderEntity parentAlbum, String name) {
    super(parentAlbum, name);
  }

  public Integer getNumPlays() {
    return numPlays;
  }

  public void setNumPlays(Integer numPlays) {
    this.numPlays = numPlays;
  }
}