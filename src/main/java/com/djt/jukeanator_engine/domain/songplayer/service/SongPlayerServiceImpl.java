package com.djt.jukeanator_engine.domain.songplayer.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.mapper.SongPlayerMapper;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;

/**
 * @author tmyers
 */
public final class SongPlayerServiceImpl implements SongPlayerService {

  private static final Logger log = LoggerFactory.getLogger(SongPlayerServiceImpl.class);
  
  private String playerType;

  private String rootPath;
  private SongLibraryRepository songLibraryRepository;
  private RootFolderEntity songLibraryRoot;

  private SongQueueRepository songQueueRepository;
  private SongQueueRootEntity songQueueRoot;
  private SongQueueEntryEntity nowPlayingSong;

  private List<String> genres = new ArrayList<>();
  private List<String> artists = new ArrayList<>();
  private List<AlbumFolderEntity> albums = new ArrayList<>();

  public SongPlayerServiceImpl(
      String playerType,
      String rootPath, 
      SongLibraryRepository songLibraryRepository, 
      SongQueueRepository songQueueRepository) {

    
    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
    this.rootPath = rootPath;
    this.songLibraryRepository = songLibraryRepository;
    this.songQueueRepository = songQueueRepository;

    // Initialize the song library root and song queue
    initialize();

    log.info("Using song library root: " + this.songLibraryRoot);
  }

  public NowPlayingSongDto getNowPlayingSong() {

    if (nowPlayingSong != null) {
      return SongPlayerMapper.toDto(nowPlayingSong);  
    }
    return SongPlayerMapper.EMPTY_NOW_PLAYING_SONG_DTO;
  }

  private void initialize() {

    // If we cannot load the song library from disk at startup, then assume a new install and return an
    // empty root folder. The application will automatically ask the user to scan for songs at startup.
    try {
      this.songLibraryRoot = this.songLibraryRepository.loadAggregateRoot(rootPath);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load song library from: " + rootPath + ", using empty song library root for now, error: " + ednee.getMessage());
      this.songQueueRoot = new SongQueueRootEntity(rootPath);
    }

    try {
      this.songQueueRoot = this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load song queue from: " + rootPath + ", using empty song library root for now, error: " + ednee.getMessage());
      this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    }

    this.albums = this.songLibraryRoot.getAllAlbums();
    this.artists.clear();
    this.genres.clear();

    for (AlbumFolderEntity album : this.albums) {

      String artist = album.getParentArtist().getName();
      if (!this.artists.contains(artist)) {
        this.artists.add(artist);
      }

      String genre = album.getParentGenre().getName();
      if (!this.genres.contains(genre)) {
        this.genres.add(genre);
      }
    }
  }
}
