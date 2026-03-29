package com.djt.jukeanator_engine.domain.songlibrary.controller;

import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-library")
public class SongLibraryController {

    private final SongLibraryService songLibraryService;

    public SongLibraryController(SongLibraryService songLibraryService) {
        this.songLibraryService = songLibraryService;
    }

    @GetMapping("/genres")
    public List<String> getGenres() {
        return songLibraryService.getGenres();
    }

    @GetMapping("/albums")
    public List<AlbumDto> getAlbums() {
      return SongLibraryMapper.toDto(this.songLibraryService.getAlbums());
    }

    @PostMapping("/scan")
    public ResponseEntity<Void> scanFileSystem(@RequestBody ScanRequest request) throws IOException {

        songLibraryService.scanFileSystemForSongs(
                request.getScanPath(),
                request.getAcceptedExtensions()
        );

        return ResponseEntity.ok().build();
    }
}