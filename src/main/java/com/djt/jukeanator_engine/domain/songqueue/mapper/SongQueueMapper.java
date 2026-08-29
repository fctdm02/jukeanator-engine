package com.djt.jukeanator_engine.domain.songqueue.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryPersistenceDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueRootDto;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;

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

  public static SongQueueRootDto toPersistenceDto(SongQueueRootEntity root) {

    List<SongQueueEntryPersistenceDto> entries = new ArrayList<>();

    for (SongQueueEntryEntity entity : root.getSongs()) {
      entries.add(toPersistenceDto(entity));
    }

    return new SongQueueRootDto(root.getRootPath(), entries);
  }

  public static SongQueueEntryPersistenceDto toPersistenceDto(SongQueueEntryEntity entity) {

    SongFileEntity song = entity.getSong();

    return new SongQueueEntryPersistenceDto(entity.getUsername(),
        song.getAlbum().getId(), song.getId(),
        song.getNaturalIdentity(), entity.getPriority(), entity.getQueuedAtTime());
  }

  public static SongQueueEntryEntity toEntity(SongQueueEntryPersistenceDto dto,
      SongFileEntity song) {

    SongQueueEntryEntity entity = new SongQueueEntryEntity(dto.getUsername(), song, dto.getPriority());
    entity.setQueuedAtTime(dto.getQueuedAtTime());

    return entity;
  }
}
