package com.djt.jukeanator_engine.domain.songlibrary.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
public final class SongLibraryMapper {
  
  public static List<AlbumDto> toDto(List<AlbumFolderEntity> albumEntities) {
    
    List<AlbumDto> albumDtos = new ArrayList<>();
    
    for (AlbumFolderEntity albumEntity: albumEntities) {
      
      List<SongDto> songDtos = new ArrayList<>();
      for (SongFileEntity songEntity: albumEntity.getChildSongs()) {
        songDtos.add(new SongDto(
            songEntity.getName(), 
            songEntity.getNumPlays()));
      }
      
      AlbumDto albumDto = new AlbumDto(
          albumEntities.indexOf(albumEntity), 
          albumEntity.getName(),
          albumEntity.getParentGenre().getName(),
          albumEntity.getParentArtist().getName(), 
          albumEntity.hasExplicit(), 
          albumEntity.getRecordLabel(),
          albumEntity.getReleaseDate(), 
          albumEntity.getCoverArtPath(), 
          songDtos);

      albumDtos.add(albumDto);
    }
    
    return albumDtos;
  }
}