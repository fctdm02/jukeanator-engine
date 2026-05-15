package com.djt.jukeanator_engine.domain.songqueue.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;

/**
 * @author tmyers
 */
public final class SongQueueMapper {

  public static List<SongQueueEntryDto> toDto(List<SongQueueEntryEntity> entities) {

    List<SongQueueEntryDto> dtos = new ArrayList<>();

    for (SongQueueEntryEntity entity : entities) {

      SongQueueEntryDto dto = toDto(entity);

      dtos.add(dto);
    }

    return dtos;
  }

  public static SongQueueEntryDto toDto(SongQueueEntryEntity entity) {
    
    SongFileEntity song = entity.getSong();
    AlbumFolderEntity album = song.getAlbum();

    SongQueueEntryDto dto = new SongQueueEntryDto(
        album.getCoverArtPath(),
        album.getName(),
        song.getArtistName(),
        song.getName(),
        song.getNumPlays(),
        entity.getPriority());

    return dto;
  }
}
