package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.List;
import java.util.Set;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
public interface SongLibraryService extends AggregateRootService<RootFolderEntity> {

  /**
   * 
   */
  void initializeSongLibrary();
  
  /**
   * 
   * @param scanPath
   */
  void setScanPath(String scanPath);
  
  /**
   * 
   * @return
   */
  List<GenreFolderEntity> getGenres();

  /**
   * 
   * @return
   */
  List<ArtistFolderEntity> getArtists();
  
  /**
   * 
   * @return
   */
  List<AlbumFolderEntity> getAlbums();

  /**
   * 
   * @return
   */
  List<SongFileEntity> getSongs();
  
  /**
   * 
   * @param scanPath
   * @param acceptedSongFileExtensions
   * @return
   * @throws SongScanFailedException
   */
  RootFolderEntity scanFileSystemForSongs(String scanPath, Set<String> acceptedSongFileExtensions) throws SongScanFailedException;
}
