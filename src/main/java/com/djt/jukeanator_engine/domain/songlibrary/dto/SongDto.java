package com.djt.jukeanator_engine.domain.songlibrary.dto;
public class SongDto {
    private String name;
    private String album;

    public SongDto(String name, String album) {
        this.name = name;
        this.album = album;
    }

    public String getName() {
        return name;
    }

    public String getAlbum() {
        return album;
    }
}