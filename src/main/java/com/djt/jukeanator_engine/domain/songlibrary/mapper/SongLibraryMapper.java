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

  public static List<GenreDto> toGenreDtoList(Collection<GenreFolderEntity> genreEntities) {

    List<GenreDto> genreDtos = new ArrayList<>();
    for (GenreFolderEntity genreEntity : genreEntities) {

      genreDtos.add(new GenreDto(
          genreEntity.getPersistentIdentity(),
          genreEntity.getName()));
    }
    return genreDtos;
  }
  
  public static List<ArtistDto> toArtistDtoList(Collection<ArtistFolderEntity> artistEntities) {

    List<ArtistDto> artistDtos = new ArrayList<>();
    for (ArtistFolderEntity artistEntity : artistEntities) {

      artistDtos.add(new ArtistDto(
          artistEntity.getPersistentIdentity(),
          artistEntity.getName(),
          SongLibraryMapper.toAlbumDtoList(
              artistEntity,
              artistEntity.getAlbums())));
    }
    return artistDtos;
  }

  public static List<AlbumDto> toAlbumDtoList(Collection<AlbumFolderEntity> albumEntities) {

    List<AlbumDto> albumDtos = new ArrayList<>();
    for (AlbumFolderEntity albumEntity : albumEntities) {

      ArtistFolderEntity artist = albumEntity.getParentArtist();

      albumDtos.add(new AlbumDto(
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
              albumEntity.getChildSongs())));
    }
    return albumDtos;
  }

  public static List<AlbumDto> toAlbumDtoList(ArtistFolderEntity artist, Collection<AlbumFolderEntity> albumEntities) {

    List<AlbumDto> albumDtos = new ArrayList<>();
    for (AlbumFolderEntity albumEntity : albumEntities) {

      albumDtos.add(new AlbumDto(
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
              albumEntity.getChildSongs())));
    }
    return albumDtos;
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
        artist.getName(),
        album.getPersistentIdentity(),
        album.getName(),
        album.getCoverArtPath(),
        songEntity.getPersistentIdentity(),
        songEntity.getSongName(),
        songEntity.getNumPlays());
  }
}