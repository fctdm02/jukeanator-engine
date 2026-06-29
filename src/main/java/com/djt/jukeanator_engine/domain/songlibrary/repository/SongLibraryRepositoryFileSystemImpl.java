package com.djt.jukeanator_engine.domain.songlibrary.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public final class SongLibraryRepositoryFileSystemImpl implements SongLibraryRepository {
  
  public static final String SONG_LIBRARY_FILENAME = "JukeANator.oos";
  
  private RootFolderEntity root;	
  private SongLibraryObjectPersistor songLibraryObjectPersistor;
  private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
  private String filePath;
  
  public SongLibraryRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + SONG_LIBRARY_FILENAME;
    this.songLibraryObjectPersistor = new SongLibraryObjectPersistor(); 
  }
  
  public void setBasePath(String basePath) {    
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + SONG_LIBRARY_FILENAME;
  }

  @Override
  public RootFolderEntity loadAggregateRoot(String naturalIdentity) throws EntityDoesNotExistException {

    try {
      
      // TODO: How to reconcile naturalIdentity with filePath?
      this.root = this.songLibraryObjectPersistor.loadSongLibraryFromDisk(filePath);
      this.root.initialize();
      return this.root;
      
    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException("Could not read song library from disk with naturalIdentity: " 
          + naturalIdentity
          + " and filePath: "
          + filePath );
    }
  }
    
  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    try {
      this.root = root;
      this.songLibraryObjectPersistor.writeSongLibraryToDisk(root, filePath);
    } catch (IOException ioe) {
      throw new SongLibraryServiceException("Could not write song library to disk with naturalIdentity: " 
          + root.getNaturalIdentity()
          + " and filePath: "
          + filePath);
    }
  }
  
  @Override
  public RootFolderEntity loadAggregateRoot(int persistentIdentity) throws EntityDoesNotExistException {

    throw new SongLibraryServiceException("This method is unsupported for the file system implementation");
  }
  
  @Override
  public void storeSongLibraryAsync() throws EntityDoesNotExistException {
    
    RootFolderEntity rootToPersist = this.root;
    
    persistenceExecutor.submit(() -> {

      try {
        storeAggregateRoot(rootToPersist);
      } catch (Exception e) {
        throw new SongLibraryServiceException("Could not asynchronously persist song library", e);
      }
    });
  }
}
