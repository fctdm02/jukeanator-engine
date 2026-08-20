package com.djt.jukeanator_engine.domain.songqueue.repository;

import static java.util.Objects.requireNonNull;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryPersistenceDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueRootDto;
import com.djt.jukeanator_engine.domain.songqueue.mapper.SongQueueMapper;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;

/**
 * @author tmyers
 */
public final class SongQueueRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements SongQueueRepository {

  private static final Logger log = LoggerFactory.getLogger(SongQueueRepositoryFileSystemImpl.class);

  private final SongLibraryService songLibraryService;

  private String filePath;

  public SongQueueRepositoryFileSystemImpl(String basePath, SongLibraryService songLibraryService) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    this.songLibraryService = songLibraryService;
    this.filePath = basePath + File.separator + SongQueueRootEntity.SONG_QUEUE_FILENAME;
  }

  @Override
  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    super.setBasePath(basePath);
    this.filePath = basePath + File.separator + SongQueueRootEntity.SONG_QUEUE_FILENAME;
  }

  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // TODO: How to reconcile naturalIdentity with filePath?
    SongQueueRootDto dto = readJson(filePath, SongQueueRootDto.class);
    if (dto == null) {
      throw new EntityDoesNotExistException(
          "Could not read song queue from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }

    RootFolderEntity songLibraryRoot =
        songLibraryService.getSongLibraryRoot(songLibraryService.getOwnLocationId());

    SongQueueRootEntity root = new SongQueueRootEntity(dto.getRootPath());
    for (SongQueueEntryPersistenceDto entryDto : dto.getEntries()) {

      try {
        SongFileEntity song = songLibraryRoot.getSongById(entryDto.getAlbumId(), entryDto.getSongId());
        SongQueueEntryEntity entry = SongQueueMapper.toEntity(entryDto, song);
        root.getSongs().add(entry);
      } catch (EntityDoesNotExistException ednee) {
        log.warn(
            "Skipping persisted song queue entry whose song no longer exists in the library: albumId={}, songId={}",
            entryDto.getAlbumId(), entryDto.getSongId());
      }
    }

    return root;
  }

  @Override
  public void storeAggregateRoot(SongQueueRootEntity root) {

    writeJson(filePath, SongQueueMapper.toPersistenceDto(root));
  }

  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new SongLibraryServiceException("This method is unsupported for the file system implementation");
  }
}
