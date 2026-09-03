package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;

public record SearchResultDto(List<SongDto> songs, List<ArtistDto> artists, List<AlbumDto> albums,
    int numArtists, int numAlbums, int numSongs) {
}
