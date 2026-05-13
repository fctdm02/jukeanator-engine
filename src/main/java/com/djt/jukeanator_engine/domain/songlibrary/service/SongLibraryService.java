package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;

/**
 * @author tmyers
 */
public interface SongLibraryService {

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
  List<AlbumDto> getAlbums();
  
  /**
   * 
   * @param scanRequest
   * @return number of albums scanned
   * @throws SongScanFailedException
   */
  Integer scanFileSystemForSongs(ScanRequest request) throws SongScanFailedException;
}
