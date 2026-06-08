package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataSearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;

/**
 * @author tmyers
 */
public interface SongLibraryService {
  
  /**
   * 
   * @return
   */
  SearchResultDto getMusicByPopularity();
  
  /**
   * @param searchFor
   * @return
   */
  SearchResultDto getMusicBySearch(String searchFor);
  
  /**
   * 
   * @return
   */
  List<GenreDto> getGenres();

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByPopularity(String genreName);

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByTitle(String genreName);

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByReleaseDate(String genreName);
  
  /**
   * 
   * @return
   */
  List<ArtistDto> getArtists();
  
  /**
   * 
   * @return
   */
  List<AlbumDto> getAlbums();

  /**
   * @param genreId
   * @return
   */
  List<AlbumDto> getAlbumsForGenre(Integer genreId);
  
  /**
   * 
   * @param artistId
   * @return
   */
  ArtistDto getArtistById(Integer artistId);
  
  /**
   * 
   * @param albumId
   * @return
   */
  AlbumDto getAlbumById(Integer albumId);
  
  /**
   * 
   * @param albumId
   * @param songId
   * @return
   */
  SongDto getSongById(Integer albumId, Integer songId);
  
  /**
   * 
   * @param scanRequest
   * @return number of albums scanned
   * @throws SongScanFailedException
   */
  Integer scanFileSystemForSongs(ScanRequest request) throws SongScanFailedException;
  
  /**
   * 
   * @return
   * @throws SongScanFailedException
   */
  Integer scanFileSystemForSongs() throws SongScanFailedException;
  
  /**
   * @return
   */
  Integer resetSongStatistics();
  
  /**
   * 
   * @param artistName
   * @param albumName
   * @return
   */
  List<AlbumMetadataSearchResultDto> searchInternetForAlbumMetadata(String artistName, String albumName);
}
