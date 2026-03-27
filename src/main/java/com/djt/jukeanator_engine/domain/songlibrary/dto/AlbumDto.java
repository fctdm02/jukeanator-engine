package com.djt.jukeanator_engine.domain.songlibrary.dto;
public class AlbumDto {
    private String name;
    private String artist;

    public AlbumDto(String name, String artist) {
        this.name = name;
        this.artist = artist;
    }

    public String getName() {
        return name;
    }

    public String getArtist() {
        return artist;
    }
}