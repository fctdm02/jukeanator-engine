package com.djt.jukeanator_engine.domain.songlibrary.mapper;

import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
public final class SongLibraryMapper {

    public static GenreDto toDto(GenreFolderEntity genre) {
        return new GenreDto(genre.getName());
    }

    public static ArtistDto toDto(ArtistFolderEntity artist) {
        return new ArtistDto(artist.getName());
    }

    public static AlbumDto toDto(AlbumFolderEntity album) {
        return new AlbumDto(
            album.getName(),
            album.getParentFolder().getName() // artist
        );
    }
    
    public static SongDto toDto(SongFileEntity song) {
      return new SongDto(
          song.getName(),
          song.getParentFolder().getName() // album
      );
  }    
}