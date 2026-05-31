package com.djt.jukeanator_engine.domain.songlibrary.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

  public static GenreDto toGenreDto(GenreFolderEntity genreEntity, List<Integer> albumIds, Integer numPlays) {

    return new GenreDto(genreEntity.getPersistentIdentity(), genreEntity.getName(), albumIds, numPlays);
  }
  
  public static List<ArtistDto> toArtistDtoList(Collection<ArtistFolderEntity> artistEntities) {

    List<ArtistDto> artistDtos = new ArrayList<>();
    for (ArtistFolderEntity artistEntity : artistEntities) {

      artistDtos.add(toArtistDto(artistEntity));
    }
    return artistDtos;
  }

  public static ArtistDto toArtistDto(ArtistFolderEntity artistEntity) {

    return new ArtistDto(artistEntity.getPersistentIdentity(), artistEntity.getName(),
        SongLibraryMapper.toAlbumDtoList(artistEntity, artistEntity.getAlbums()));
  }
  
  public static List<AlbumDto> toAlbumDtoList(Collection<AlbumFolderEntity> albumEntities) {

    List<AlbumDto> albumDtos = new ArrayList<>();
    for (AlbumFolderEntity albumEntity : albumEntities) {

      ArtistFolderEntity artist = albumEntity.getParentArtist();

      albumDtos.add(toAlbumDto(artist, albumEntity));
    }
    return albumDtos;
  }

  public static List<AlbumDto> toAlbumDtoList(ArtistFolderEntity artist, Collection<AlbumFolderEntity> albumEntities) {

    List<AlbumDto> albumDtos = new ArrayList<>();
    for (AlbumFolderEntity albumEntity : albumEntities) {

      albumDtos.add(toAlbumDto(artist, albumEntity));
    }
    return albumDtos;
  }

  public static AlbumDto toAlbumDto(AlbumFolderEntity albumEntity) {

    ArtistFolderEntity artist = albumEntity.getParentArtist();
    return toAlbumDto(artist, albumEntity);
  }
  
  public static AlbumDto toAlbumDto(ArtistFolderEntity artist, AlbumFolderEntity albumEntity) {

    return new AlbumDto(
        artist.getPersistentIdentity(), 
        artist.getName(),
        albumEntity.getPersistentIdentity(), 
        albumEntity.getName(), 
        albumEntity.hasExplicit(),
        albumEntity.getRecordLabel(), 
        albumEntity.getReleaseDate(), 
        albumEntity.getCoverArtPath(),
        SongLibraryMapper.toSongDtoList(
            artist, 
            albumEntity, 
            albumEntity.getChildSongs()));
  }
  
  public static List<SongDto> toSongDtoList(Collection<SongFileEntity> songEntities) {

    List<SongDto> songDtos = new ArrayList<>();
    for (SongFileEntity songEntity : songEntities) {

      AlbumFolderEntity album = songEntity.getAlbum();
      ArtistFolderEntity artist = album.getParentArtist();

      songDtos.add(toSongDto(
          artist,
          album,
          songEntity));
    }
    return songDtos;
  }

  public static List<SongDto> toSongDtoList(
      ArtistFolderEntity artist,
      AlbumFolderEntity album,
      Collection<SongFileEntity> songEntities) {

    List<SongDto> songDtos = new ArrayList<>();
    for (SongFileEntity songEntity : songEntities) {

      songDtos.add(toSongDto(
          artist,
          album,
          songEntity));
    }
    return songDtos;
  }

  public static SongDto toSongDto(SongFileEntity songEntity) {
    
    AlbumFolderEntity album = songEntity.getAlbum();
    ArtistFolderEntity artist = album.getParentArtist();
    
    return SongLibraryMapper.toSongDto(artist, album, songEntity);
  }
  
  public static SongDto toSongDto(ArtistFolderEntity artist, AlbumFolderEntity album, SongFileEntity songEntity) {

    return new SongDto(
        artist.getPersistentIdentity(),
        (artist.getName().equals("Compilations")) ? songEntity.getArtistName() : artist.getName(),
        album.getPersistentIdentity(),
        album.getName(),
        album.getCoverArtPath(),
        songEntity.getPersistentIdentity(),
        songEntity.getSongName(),
        songEntity.getTrackNumber(),
        songEntity.getNumPlays());
  }
}