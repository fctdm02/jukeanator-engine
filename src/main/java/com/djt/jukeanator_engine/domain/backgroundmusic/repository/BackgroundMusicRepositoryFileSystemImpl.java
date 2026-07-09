package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.dto.BackgroundMusicSongDto;
import com.djt.jukeanator_engine.domain.backgroundmusic.mapper.BackgroundMusicMapper;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * @author tmyers
 */
public final class BackgroundMusicRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements BackgroundMusicRepository {

  public static final String BACKGROUND_MUSIC_FILENAME = "BackgroundMusicSongs.json";

  private final String filePath;

  public BackgroundMusicRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    this.filePath = basePath + File.separator + BACKGROUND_MUSIC_FILENAME;
  }

  @Override
  public List<BackgroundMusicSongEntity> loadAll() {

    List<BackgroundMusicSongDto> dtos =
        readJsonList(filePath, new TypeReference<List<BackgroundMusicSongDto>>() {});

    List<BackgroundMusicSongEntity> entities = BackgroundMusicMapper.toEntityList(dtos);

    seedNextPersistentIdentityFrom(entities.stream().map(BackgroundMusicSongEntity::getPersistentIdentity));

    return entities;
  }

  @Override
  public void storeAll(List<BackgroundMusicSongEntity> songs) {

    for (BackgroundMusicSongEntity song : songs) {
      if (song.getPersistentIdentity() == null) {
        song.setPersistentIdentity(getNextPersistentIdentityValue());
      }
    }

    writeJsonList(filePath, BackgroundMusicMapper.toDtoList(songs));
  }
}
