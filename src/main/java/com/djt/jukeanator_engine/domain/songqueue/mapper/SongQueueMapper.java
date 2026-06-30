package com.djt.jukeanator_engine.domain.songqueue.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
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

    SongQueueEntryDto dto = new SongQueueEntryDto(entity.getUsername(),
        SongLibraryMapper.toSongDto(song), entity.getPriority(), song.getNaturalIdentity());

    return dto;
  }
}
