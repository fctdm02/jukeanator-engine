package com.djt.jukeanator_engine.domain.songlibrary.client;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.djt.jukeanator_engine.domain.songlibrary.dto.*;

public class SongLibraryClient {

    private final RestClient restClient;

    public SongLibraryClient(String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // ----------------------------------------
    // Initialize
    // ----------------------------------------
    public void initialize() {
        restClient.post()
                .uri("/api/song-library/initialize")
                .retrieve()
                .toBodilessEntity();
    }

    // ----------------------------------------
    // Set scan path
    // ----------------------------------------
    public void setScanPath(String path) {
        SetScanPathRequest request = new SetScanPathRequest();
        request.setScanPath(path);

        restClient.post()
                .uri("/api/song-library/scan-path")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    // ----------------------------------------
    // Get genres
    // ----------------------------------------
    public List<GenreDto> getGenres() {
        return restClient.get()
                .uri("/api/song-library/genres")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ----------------------------------------
    // Get artists
    // ----------------------------------------
    public List<ArtistDto> getArtists() {
        return restClient.get()
                .uri("/api/song-library/artists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ----------------------------------------
    // Get albums
    // ----------------------------------------
    public List<AlbumDto> getAlbums() {
        return restClient.get()
                .uri("/api/song-library/albums")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ----------------------------------------
    // Scan filesystem
    // ----------------------------------------
    public void scan(ScanRequest request) {
        restClient.post()
                .uri("/api/song-library/scan")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}