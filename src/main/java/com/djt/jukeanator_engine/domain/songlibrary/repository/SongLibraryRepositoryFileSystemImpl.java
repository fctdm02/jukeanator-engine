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
  
  private RootFolderEntity root;	
  private SongLibraryObjectPersistor songLibraryObjectPersistor;
  private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
  private String basePath;
  
  public SongLibraryRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    this.basePath = basePath;
    this.songLibraryObjectPersistor = new SongLibraryObjectPersistor(); 
  }
  
  public void setBasePath(String basePath) {    
    requireNonNull(basePath, "basePath cannot be null");
    this.basePath = basePath;
  }

  @Override
  public RootFolderEntity loadAggregateRoot(String locationName) throws EntityDoesNotExistException {

    String filePath = buildFilePath(locationName);
    File file = new File(filePath);
    
    boolean needToSetLocationName = false;
    if (!file.exists()) {
      filePath = buildFilePath(DEFAULT_SONG_LIBRARY_LOCATION_NAME);
      needToSetLocationName = true;
    }
    
    try {
      
      this.root = this.songLibraryObjectPersistor.loadSongLibraryFromDisk(filePath);
      
      if (needToSetLocationName) {
        this.root.setLocationName(locationName);
      }
      
      this.root.initialize();
      return this.root;
      
    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException("Could not read song library from disk with locationName: " 
          + locationName
          + " and filePath: "
          + filePath );
    }
  }
    
  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    String locationName = root.getLocationName();
    String filePath = buildFilePath(locationName);
    
    try {
      this.root = root;
      this.songLibraryObjectPersistor.writeSongLibraryToDisk(root, filePath);
    } catch (IOException ioe) {
      throw new SongLibraryServiceException("Could not write song library to disk with locationName: " 
          + locationName
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
  
  private String buildFilePath(String locationName) {
    return basePath + File.separator + locationName.replaceAll(" ", "_") + ".oos";
  }
}
