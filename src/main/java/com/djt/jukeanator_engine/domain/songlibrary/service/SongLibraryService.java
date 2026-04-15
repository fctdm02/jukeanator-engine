package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.List;
import java.util.Set;

import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public interface SongLibraryService extends AggregateRootService<RootFolderEntity> {

  /**
   * 
   * @return
   */
  List<String> getGenres();

  /**
   * 
   * @return
   */
  List<String> getArtists();
  
  /**
   * 
   * @return
   */
  List<AlbumFolderEntity> getAlbums();
  
  /**
   * 
   * @param scanPath
   * @param acceptedSongFileExtensions
   * @return
   * @throws SongScanFailedException
   */
  RootFolderEntity scanFileSystemForSongs(String scanPath, Set<String> acceptedSongFileExtensions) throws SongScanFailedException;
}
