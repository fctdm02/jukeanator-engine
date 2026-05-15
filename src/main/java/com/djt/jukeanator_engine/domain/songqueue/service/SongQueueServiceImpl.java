package com.djt.jukeanator_engine.domain.songqueue.service;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.AddSongToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.exception.SongQueueException;
import com.djt.jukeanator_engine.domain.songqueue.mapper.SongQueueMapper;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;

/**
 * @author tmyers
 */
public final class SongQueueServiceImpl implements SongQueueService, AggregateRootService<SongQueueRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(SongQueueServiceImpl.class);
  
  private final ApplicationEventPublisher eventPublisher;
  
  private String rootPath;
  private SongLibraryRepository songLibraryRepository;
  private RootFolderEntity songLibraryRoot;
  
  private SongQueueRepository songQueueRepository;
  private SongQueueRootEntity songQueueRoot;
  
  private List<String> genres = new ArrayList<>();
  private List<String> artists = new ArrayList<>();
  private List<AlbumFolderEntity> albums = new ArrayList<>(); 
  
  public SongQueueServiceImpl(
      String rootPath,
      SongLibraryRepository songLibraryRepository, 
      SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher) {

      requireNonNull(rootPath, "rootPath cannot be null");
      requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
      requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
      requireNonNull(eventPublisher, "eventPublisher cannot be null");
      
      this.rootPath = rootPath;
      this.songLibraryRepository = songLibraryRepository;      
      this.songQueueRepository = songQueueRepository;
      this.eventPublisher = eventPublisher;

      // Initialize the song library root and song queue
      initialize();
      
      log.info("Using song library root: " + this.songLibraryRoot);
  }
  
  // Service methods
  @Override
  public List<SongQueueEntryDto> getQueuedSongs() {
    
    return SongQueueMapper.toDto(songQueueRoot.getSongs());
  }
  
  @Override
  public Integer addSongToQueue(AddSongToQueueRequest addSongToQueueRequest) {
    
    Integer albumId = addSongToQueueRequest.getAlbumId();
    Integer songId = addSongToQueueRequest.getSongId();
    Integer priority = addSongToQueueRequest.getPriority();
    
    try {
      AlbumFolderEntity album = albums.get(albumId);
      if (album != null) {
        
        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {
          
          Integer songQueueIndex = songQueueRoot.addSongToQueue(song, priority);
          
          songQueueRepository.storeAggregateRoot(songQueueRoot);

          // Publish the event
          eventPublisher.publishEvent(
              new AddSongToQueueEvent(
                  albumId,
                  songId,
                  priority,
                  songQueueIndex,
                  Instant.now()));
          
          return songQueueIndex;          
        }
      }         
    } catch (EntityDoesNotExistException e) { }
    
    throw new SongQueueException("Could not add song to queue, albumId: " + albumId + ", songId: " + songId + ", priority: " + priority);
  }
  
  @Override
  public SongQueueEntryDto getFirstEntryInSongQueue() {
    
    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();
    
    if (!songs.isEmpty()) {
      
      SongQueueEntryEntity songQueueEntry = songs.get(0);
      
      songQueueRoot.removeSongFromQueue(songQueueEntry);
      
      return SongQueueMapper.toDto(songQueueEntry);
    }
    
    return null;
  }

  // Repository methods
  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    return this.songQueueRepository.loadAggregateRoot(naturalIdentity);
  }
  
  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    return this.songQueueRepository.loadAggregateRoot(persistentIdentity);
  }

  @Override
  public void storeAggregateRoot(SongQueueRootEntity root) {

    this.songQueueRepository.storeAggregateRoot(root);
  }

  // Command methods
  @Override
  public CommandResponse processCommand(CommandRequest commandRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }

  // Query methods
  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }
  
  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    log.info("""
        Received ScanFileSystemForSongsEvent:
        scanPath={}
        albumCount={}
        """,
        event.scanPath(),
        event.albumCount()
    );
    
    this.rootPath = event.scanPath();

    // Refresh song library state
    initialize();
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