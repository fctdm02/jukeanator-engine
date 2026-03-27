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
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SetScanPathRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
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

    @PostMapping("/initialize")
    public ResponseEntity<Void> initialize() {
        songLibraryService.initializeSongLibrary();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/scan-path")
    public ResponseEntity<Void> setScanPath(@RequestBody SetScanPathRequest request) {
        songLibraryService.setScanPath(request.getScanPath());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/genres")
    public List<GenreDto> getGenres() {
        return songLibraryService.getGenres()
                .stream()
                .map(SongLibraryMapper::toDto)
                .toList();
    }

    @GetMapping("/artists")
    public List<ArtistDto> getArtists() {
        return songLibraryService.getArtists()
                .stream()
                .map(SongLibraryMapper::toDto)
                .toList();
    }

    @GetMapping("/albums")
    public List<AlbumDto> getAlbums() {
        return songLibraryService.getAlbums()
                .stream()
                .map(SongLibraryMapper::toDto)
                .toList();
    }

    @GetMapping("/songs")
    public List<SongDto> getSongs() {
        return songLibraryService.getSongs()
                .stream()
                .map(SongLibraryMapper::toDto)
                .toList();
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