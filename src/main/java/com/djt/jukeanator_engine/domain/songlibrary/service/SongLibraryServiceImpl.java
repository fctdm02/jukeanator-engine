package com.djt.jukeanator_engine.domain.songlibrary.service;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.NumPlaysComparable;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;

/**
 * @author tmyers
 */
public final class SongLibraryServiceImpl implements SongLibraryService, AggregateRootService<RootFolderEntity> {

  private static final Logger log = LoggerFactory.getLogger(SongLibraryServiceImpl.class);
  
  private final ApplicationEventPublisher eventPublisher;
  
  private String scanPath;
  private RootFolderEntity root;
  private SongLibraryRepository songLibraryRepository;
  private SongScanner songScanner;
  private Integer searchResultSize = Integer.valueOf(50);
  private boolean isInitialized;
  
  private List<String> genres = new ArrayList<>();
  private List<ArtistFolderEntity> artists = new ArrayList<>();
  private List<AlbumFolderEntity> albums = new ArrayList<>();
  private List<SongFileEntity> songs = new ArrayList<>();

  public SongLibraryServiceImpl(
      String scanPath,
      SongLibraryRepository songLibraryRepository,
      SongScanner songScanner,
      Integer searchResultSize,
      ApplicationEventPublisher eventPublisher) {

      requireNonNull(scanPath, "scanPath cannot be null");
      requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
      requireNonNull(songScanner, "songScanner cannot be null");
      requireNonNull(searchResultSize, "searchResultSize cannot be null");
      requireNonNull(eventPublisher, "eventPublisher cannot be null");
            
      this.scanPath = scanPath;
      this.songLibraryRepository = songLibraryRepository;
      this.songScanner = songScanner;
      this.searchResultSize = searchResultSize;
      this.eventPublisher = eventPublisher;
      
      // Initialize the song library
      initializeSongLibrary();
  }
  
  // Service methods
  @Override
  public SearchResultDto getMusicByPopularity() {

    if (!isInitialized) {
      throw new SongLibraryException(
          "SongLibraryService has not been initialized yet!");
    }

    Comparator<NumPlaysComparable> byNumPlaysDescending =
        Comparator.comparing(
            NumPlaysComparable::getNumPlays,
            Comparator.nullsFirst(Integer::compareTo))
        .reversed();

    List<SongFileEntity> popularSongs = songs.stream()
        .sorted(byNumPlaysDescending)
        .limit(searchResultSize)
        .toList();

    List<ArtistFolderEntity> popularArtists = artists.stream()
        .sorted(byNumPlaysDescending)
        .limit(searchResultSize)
        .toList();

    List<AlbumFolderEntity> popularAlbums = albums.stream()
        .sorted(byNumPlaysDescending)
        .limit(searchResultSize)
        .toList();

    return new SearchResultDto(
        SongLibraryMapper.toSongDtoList(popularSongs),
        SongLibraryMapper.toArtistDtoList(popularArtists),
        SongLibraryMapper.toAlbumDtoList(popularAlbums));
  }

  @Override
  public SearchResultDto getMusicBySearch(String searchFor) {

    if (!isInitialized) {
      throw new SongLibraryException(
          "SongLibraryService has not been initialized yet!");
    }

    requireNonNull(searchFor, "searchFor cannot be null");

    final String normalizedSearch = searchFor.trim().toLowerCase();

    if (normalizedSearch.isEmpty()) {
      return new SearchResultDto(
          List.of(),
          List.of(),
          List.of());
    }

    Comparator<NumPlaysComparable> bySearchWeightThenPopularityDescending =
        Comparator
            .comparingInt((NumPlaysComparable npc) -> {

              if (npc instanceof SongFileEntity song) {
                return calculateSearchResultWeight(
                    song.getName(),
                    normalizedSearch);
              }

              if (npc instanceof ArtistFolderEntity artist) {
                return calculateSearchResultWeight(
                    artist.getName(),
                    normalizedSearch);
              }

              if (npc instanceof AlbumFolderEntity album) {
                return calculateSearchResultWeight(
                    album.getName(),
                    normalizedSearch);
              }

              return Integer.valueOf(0);
            })
            .thenComparing(
                NumPlaysComparable::getNumPlays,
                Comparator.nullsFirst(Integer::compareTo))
            .reversed();

    List<SongFileEntity> matchingSongs = songs.stream()
        .filter(song ->
            calculateSearchResultWeight(
                song.getName(),
                normalizedSearch) > 0)
        .sorted(bySearchWeightThenPopularityDescending)
        .limit(searchResultSize)
        .toList();

    List<ArtistFolderEntity> matchingArtists = artists.stream()
        .filter(artist ->
            calculateSearchResultWeight(
                artist.getName(),
                normalizedSearch) > 0)
        .sorted(bySearchWeightThenPopularityDescending)
        .limit(searchResultSize)
        .toList();

    List<AlbumFolderEntity> matchingAlbums = albums.stream()
        .filter(album ->
            calculateSearchResultWeight(
                album.getName(),
                normalizedSearch) > 0)
        .sorted(bySearchWeightThenPopularityDescending)
        .limit(searchResultSize)
        .toList();

    return new SearchResultDto(
        SongLibraryMapper.toSongDtoList(matchingSongs),
        SongLibraryMapper.toArtistDtoList(matchingArtists),
        SongLibraryMapper.toAlbumDtoList(matchingAlbums));
  }

  private int calculateSearchResultWeight(
      String value,
      String normalizedSearch) {

    if (value == null) {
      return Integer.valueOf(0);
    }

    String normalizedValue = value.toLowerCase().trim();

    // Full word match (highest priority)
    for (String word : normalizedValue.split("\\s+")) {
      if (word.equals(normalizedSearch)) {
        return Integer.valueOf(4);
      }
    }

    // Starts with
    if (normalizedValue.startsWith(normalizedSearch)) {
      return Integer.valueOf(3);
    }

    // Ends with
    if (normalizedValue.endsWith(normalizedSearch)) {
      return Integer.valueOf(2);
    }

    // Contains
    if (normalizedValue.contains(normalizedSearch)) {
      return Integer.valueOf(1);
    }

    return Integer.valueOf(0);
  }
  
  @Override  
  public List<String> getGenres() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return genres;
  }

  @Override
  public List<ArtistDto> getArtists() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return SongLibraryMapper.toArtistDtoList(artists);
  }   
  
  @Override
  public List<AlbumDto> getAlbums() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return SongLibraryMapper.toAlbumDtoList(albums);
  }   
  
  @Override
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    try {
      
      // Scan the file system for songs
      this.scanPath = scanRequest.getScanPath();
      this.root = songScanner.scanFileSystemForSongs(this.scanPath);
      
      // Store the song library
      if (this.songLibraryRepository instanceof SongLibraryRepositoryFileSystemImpl) {
        ((SongLibraryRepositoryFileSystemImpl)this.songLibraryRepository).setBasePath(this.scanPath);
      }
      this.songLibraryRepository.storeAggregateRoot(this.root);
      
      // Initialize the song library
      initializeSongLibrary();
      
      // Publish the event
      eventPublisher.publishEvent(new ScanFileSystemForSongsEvent(
          scanPath,
          albums.size(), 
          Instant.now()));
      
      return Integer.valueOf(albums.size());
    } catch (SongLibraryException sle) {
    	throw sle;      
    } catch (Exception e) {
      throw new SongScanFailedException("Could not scan file system for songs in: "
          + scanPath 
          + " with acceptedSongFileExtensions: " 
          + songScanner.getAcceptedSongFileExtensions(), e);
    }
  }

  // Repository methods
  @Override
  public RootFolderEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    return this.songLibraryRepository.loadAggregateRoot(naturalIdentity);
  }
  
  @Override
  public RootFolderEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    return this.songLibraryRepository.loadAggregateRoot(persistentIdentity);
  }

  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    this.songLibraryRepository.storeAggregateRoot(root);
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

  public void initializeSongLibrary() {
    
    // If we cannot load the song library from disk at startup, then assume a new install and return an
    // empty root folder.  The application will automatically ask the user to scan for songs at startup. 
    try {
      
        this.root = this.songLibraryRepository.loadAggregateRoot(this.scanPath);
        
    } catch (EntityDoesNotExistException ednee) {
      
      log.error("Could not load song library from: " 
          + scanPath
          + ", using empty song library root for now, error: " 
          + ednee.getMessage());
      
      this.root = new RootFolderEntity();
    }
    
    this.albums = this.root.getAllAlbums();
    this.artists.clear();    
    this.genres.clear();
    
    for (AlbumFolderEntity album : this.albums) {
    	
      ArtistFolderEntity artist = album.getParentArtist();
      if (!this.artists.contains(artist)) {
    	  this.artists.add(artist);
      }    	
    	
      String genre = album.getParentGenre().getName();
      if (!this.genres.contains(genre)) {
        this.genres.add(genre);
      }
    }
    
    this.isInitialized = true;
  }
  
  // Event handlers
  @EventListener
  public void handleSongPlaybackStartedEvent(SongPlaybackStartedEvent event) {

    SongDto song = event.songQueueEntry().getSong();
    
    Integer albumId = song.getAlbumId();
    Integer songId = song.getSongId();
    
    try {
      
      this.songLibraryRepository.incrementNumPlaysForSong(albumId, songId);
      
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryException("Could not increment num plays for song with albumId: " + albumId + ", songId: " + songId,  ednee);
    }
  }   
}