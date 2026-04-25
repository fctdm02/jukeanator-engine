package com.djt.jukeanator_engine.domain.songlibrary.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public final class SongLibraryRepositoryFileSystemImpl implements SongLibraryRepository {
  
  public static final String SONG_LIBRARY_FILENAME = "JukeANator.oos";
	
  private SongLibraryObjectPersistor songLibraryObjectPersistor;
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
      return this.songLibraryObjectPersistor.loadSongLibraryFromDisk(filePath);
      
    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException("Could not read song library from disk with naturalIdentity: " 
          + naturalIdentity
          + " and filePath: "
          + filePath );
    }
  }
    
  @Override
  public void storeAggregateRoot(RootFolderEntity rootFolder) {

    try {
      this.songLibraryObjectPersistor.writeSongLibraryToDisk(rootFolder, filePath);
    } catch (IOException ioe) {
      throw new SongLibraryException("Could not write song library to disk with naturalIdentity: " 
          + rootFolder.getNaturalIdentity()
          + " and filePath: "
          + filePath);
    }
  }
  
  @Override
  public RootFolderEntity loadAggregateRoot(int persistentIdentity) throws EntityDoesNotExistException {

    throw new SongLibraryException("This method is unsupported for the file system implementation");
  }  
}
