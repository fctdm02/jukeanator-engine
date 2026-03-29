package com.djt.jukeanator_engine.domain.songlibrary.client;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.djt.jukeanator_engine.domain.songlibrary.dto.*;

public class SongLibraryServiceClient {

    private final RestClient restClient;

    public SongLibraryServiceClient(String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // ----------------------------------------
    // Get genres
    // ----------------------------------------
    public List<String> getGenres() {
        return restClient.get()
                .uri("/api/song-library/genres")
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