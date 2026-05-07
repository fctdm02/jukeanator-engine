package com.djt.jukeanator_engine.domain.songlibrary.controller;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-library")
public class SongLibraryController implements SongLibraryService {

  private final SongLibraryService songLibraryService;

  public SongLibraryController(SongLibraryService songLibraryService) {
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    this.songLibraryService = songLibraryService;
  }

  @Override
  @GetMapping("/genres")
  public List<String> getGenres() {
    return songLibraryService.getGenres();
  }

  @Override
  @GetMapping("/artists")
  public List<String> getArtists() {
    return songLibraryService.getArtists();
  }

  @Override
  @GetMapping("/albums")
  public List<AlbumDto> getAlbums() {
    return songLibraryService.getAlbums();
  }

  @Override
  @PostMapping("/scan")
  public Integer scanFileSystemForSongs(@RequestParam String scanPath,
      @RequestParam Set<String> acceptedSongFileExtensions) throws SongScanFailedException {

    return songLibraryService.scanFileSystemForSongs(scanPath, acceptedSongFileExtensions);
  }
}
