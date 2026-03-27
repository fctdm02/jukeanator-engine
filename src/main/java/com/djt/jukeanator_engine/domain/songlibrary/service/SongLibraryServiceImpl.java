package com.djt.jukeanator_engine.domain.songlibrary.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;

/**
 * @author tmyers
 */
public final class SongLibraryServiceImpl implements SongLibraryService {

  private static final Logger log = LoggerFactory.getLogger(SongLibraryServiceImpl.class);
  
  private String scanPath;
  private RootFolderEntity root;
  private SongLibraryRepository songLibraryRepository;
  private SongScanner songScanner;
  private boolean isInitialized;
  
  private List<GenreFolderEntity> genres = new ArrayList<>();
  private List<ArtistFolderEntity> artists = new ArrayList<>();
  private List<AlbumFolderEntity> albums = new ArrayList<>();
  private List<SongFileEntity> songs = new ArrayList<>();

  public SongLibraryServiceImpl(
      String scanPath,
      SongLibraryRepository songLibraryRepository,
      SongScanner songScanner) {

      requireNonNull(scanPath, "scanPath cannot be null");
      requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
      requireNonNull(songScanner, "songScanner cannot be null");

      this.scanPath = scanPath;
      this.songLibraryRepository = songLibraryRepository;
      this.songScanner = songScanner;
      
      // Initialize the song library
      initializeSongLibrary();
  }
  
  // Service methods
  @Override  
  public List<GenreFolderEntity> getGenres() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return genres;
  }

  @Override
  public List<ArtistFolderEntity> getArtists() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }    
    return artists;
  }

  @Override
  public List<AlbumFolderEntity> getAlbums() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return albums;
  }   
  
  @Override
  public List<SongFileEntity> getSongs() {
    
    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return songs;
  }   
  
  @Override
  public RootFolderEntity scanFileSystemForSongs(
      String scanPath,
      Set<String> acceptedSongFileExtensions) throws SongScanFailedException {

    try {
      
      // Scan the file system for songs
      this.scanPath = scanPath;
      this.root = songScanner.scanFileSystemForSongs(this.scanPath, acceptedSongFileExtensions);
      
      // Store the song library
      if (this.songLibraryRepository instanceof SongLibraryRepositoryFileSystemImpl) {
        ((SongLibraryRepositoryFileSystemImpl)this.songLibraryRepository).setBasePath(this.scanPath);
      }
      this.songLibraryRepository.storeAggregateRoot(this.root);
      
      // Initialize the song library
      initializeSongLibrary();
      
      return root;
      
    } catch (Exception e) {
      throw new SongScanFailedException("Could not scan file system for songs in: "
          + scanPath 
          + " with acceptedSongFileExtensions: " 
          + acceptedSongFileExtensions, e);
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
  public QueryResponse<QueryRequest, QueryResponseItem> query(QueryRequest queryRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }

  @Override
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
    
    this.genres = this.root.getAllGenres();
    this.artists = this.root.getAllArtists();
    this.albums = this.root.getAllAlbums();
    
    this.songs = new ArrayList<>();
    for (AlbumFolderEntity album : albums) {
      songs.addAll(album.getChildSongs());
    }
    
    this.isInitialized = true;
  }  
  
  @Override
  public void setScanPath(String scanPath) {
    
    if (this.songLibraryRepository instanceof SongLibraryRepositoryFileSystemImpl) {
      ((SongLibraryRepositoryFileSystemImpl)this.songLibraryRepository).setBasePath(scanPath);
    }
  }
}