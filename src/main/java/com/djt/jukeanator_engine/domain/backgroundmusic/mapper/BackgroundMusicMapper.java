package com.djt.jukeanator_engine.domain.backgroundmusic.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.dto.BackgroundMusicSongDto;
import com.djt.jukeanator_engine.domain.backgroundmusic.dto.SmartBackgroundMusicSongDto;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;

/**
 * @author tmyers
 */
public final class BackgroundMusicMapper {

  private BackgroundMusicMapper() {}

  public static List<BackgroundMusicSongDto> toDtoList(List<BackgroundMusicSongEntity> entities) {

    List<BackgroundMusicSongDto> dtos = new ArrayList<>();

    for (BackgroundMusicSongEntity entity : entities) {
      dtos.add(toDto(entity));
    }

    return dtos;
  }

  public static BackgroundMusicSongDto toDto(BackgroundMusicSongEntity entity) {

    return new BackgroundMusicSongDto(
        entity.getPersistentIdentity(),
        entity.getSongFilePath(),
        entity.getTimeLastPlayed(),
        entity.getNumberOfPlays());
  }

  public static List<BackgroundMusicSongEntity> toEntityList(List<BackgroundMusicSongDto> dtos) {

    List<BackgroundMusicSongEntity> entities = new ArrayList<>();

    for (BackgroundMusicSongDto dto : dtos) {
      entities.add(toEntity(dto));
    }

    return entities;
  }

  public static BackgroundMusicSongEntity toEntity(BackgroundMusicSongDto dto) {

    return new BackgroundMusicSongEntity(
        dto.getPersistentIdentity(),
        dto.getSongFilePath(),
        dto.getTimeLastPlayed(),
        dto.getNumberOfPlays());
  }

  public static List<SmartBackgroundMusicSongDto> toSmartDtoList(
      List<SmartBackgroundMusicSongEntity> entities) {

    List<SmartBackgroundMusicSongDto> dtos = new ArrayList<>();

    for (SmartBackgroundMusicSongEntity entity : entities) {
      dtos.add(toSmartDto(entity));
    }

    return dtos;
  }

  public static SmartBackgroundMusicSongDto toSmartDto(SmartBackgroundMusicSongEntity entity) {

    return new SmartBackgroundMusicSongDto(
        entity.getPersistentIdentity(),
        entity.getSongFilePath(),
        entity.getTimeLastPlayed(),
        entity.getNumberOfPlays(),
        entity.getSourceSong(),
        entity.getReason());
  }

  public static List<SmartBackgroundMusicSongEntity> toSmartEntityList(
      List<SmartBackgroundMusicSongDto> dtos) {

    List<SmartBackgroundMusicSongEntity> entities = new ArrayList<>();

    for (SmartBackgroundMusicSongDto dto : dtos) {
      entities.add(toSmartEntity(dto));
    }

    return entities;
  }

  public static SmartBackgroundMusicSongEntity toSmartEntity(SmartBackgroundMusicSongDto dto) {

    return new SmartBackgroundMusicSongEntity(
        dto.getPersistentIdentity(),
        dto.getSongFilePath(),
        dto.getTimeLastPlayed(),
        dto.getNumberOfPlays(),
        dto.getSourceSong(),
        dto.getReason());
  }
}
