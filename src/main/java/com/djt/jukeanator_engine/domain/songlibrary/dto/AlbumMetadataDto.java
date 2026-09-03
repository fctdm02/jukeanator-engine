package com.djt.jukeanator_engine.domain.songlibrary.dto;

public record AlbumMetadataDto(String artistName, String albumName, String recordLabel,
    String releaseDate, String genre, String coverArtUrl, boolean hasExplicit, boolean isEmpty) {
}
