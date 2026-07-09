package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.dto.SmartBackgroundMusicSongDto;
import com.djt.jukeanator_engine.domain.backgroundmusic.mapper.BackgroundMusicMapper;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * @author tmyers
 */
public final class SmartBackgroundMusicRepositoryFileSystemImpl
    extends AbstractRepositoryFileSystemImpl implements SmartBackgroundMusicRepository {

  public static final String SMART_BACKGROUND_MUSIC_FILENAME = "SmartBackgroundMusicSongs.json";

  private final String filePath;

  public SmartBackgroundMusicRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    this.filePath = basePath + File.separator + SMART_BACKGROUND_MUSIC_FILENAME;
  }

  @Override
  public List<SmartBackgroundMusicSongEntity> loadAll() {

    List<SmartBackgroundMusicSongDto> dtos =
        readJsonList(filePath, new TypeReference<List<SmartBackgroundMusicSongDto>>() {});

    List<SmartBackgroundMusicSongEntity> entities = BackgroundMusicMapper.toSmartEntityList(dtos);

    seedNextPersistentIdentityFrom(
        entities.stream().map(SmartBackgroundMusicSongEntity::getPersistentIdentity));

    return entities;
  }

  @Override
  public void storeAll(List<SmartBackgroundMusicSongEntity> songs) {

    for (SmartBackgroundMusicSongEntity song : songs) {
      if (song.getPersistentIdentity() == null) {
        song.setPersistentIdentity(getNextPersistentIdentityValue());
      }
    }

    writeJsonList(filePath, BackgroundMusicMapper.toSmartDtoList(songs));
  }

  @Override
  public boolean exists() {
    return Files.exists(Path.of(filePath));
  }
}
