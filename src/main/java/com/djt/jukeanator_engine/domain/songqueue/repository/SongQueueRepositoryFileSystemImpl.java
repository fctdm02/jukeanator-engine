package com.djt.jukeanator_engine.domain.songqueue.repository;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;

/**
 * @author tmyers
 */
public final class SongQueueRepositoryFileSystemImpl implements SongQueueRepository {
	
  private SongQueueObjectPersistor objectPersistor;
  private String filePath;
  
  public SongQueueRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + SongQueueRootEntity.SONG_QUEUE_FILENAME;
    this.objectPersistor = new SongQueueObjectPersistor(); 
  }
  
  public void setBasePath(String basePath) {    
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + SongQueueRootEntity.SONG_QUEUE_FILENAME;
  }

  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity) throws EntityDoesNotExistException {

    try {
      
      // TODO: How to reconcile naturalIdentity with filePath?
      return this.objectPersistor.loadSongQueueFromDisk(filePath);
      
    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException("Could not read song queue from disk with naturalIdentity: " 
          + naturalIdentity
          + " and filePath: "
          + filePath );
    }
  }
    
  @Override
  public void storeAggregateRoot(SongQueueRootEntity rootFolder) {

    try {
		this.objectPersistor.writeSongQueueToDisk(rootFolder, filePath);
    } catch (IOException ioe) {
      throw new SongLibraryException("Could not write song queue to disk with naturalIdentity: " 
          + rootFolder.getNaturalIdentity()
          + " and filePath: "
          + filePath);
    }
  }
  
  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity) throws EntityDoesNotExistException {

    throw new SongLibraryException("This method is unsupported for the file system implementation");
  }  
}
