package com.djt.jukeanator_engine.domain.songlibrary.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFromSongEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
public final class SongLibraryMapper {

  public static GenreDto toGenreDto(GenreFolderEntity genreEntity, List<Integer> albumIds,
      Integer numPlays) {

    return new GenreDto(genreEntity.getPersistentIdentity(), genreEntity.getName(), albumIds,
        numPlays);
  }

  public static List<ArtistDto> toArtistDtoList(Collection<ArtistFolderEntity> artistEntities) {

    List<ArtistDto> artistDtos = new ArrayList<>();
    for (ArtistFolderEntity artistEntity : artistEntities) {

      artistDtos.add(toArtistDto(artistEntity));
    }
    return artistDtos;
  }

  public static ArtistDto toArtistDto(ArtistFolderEntity artistEntity) {

    List<AlbumFolderEntity> albums = artistEntity.getAlbums().stream()
        .sorted(Comparator
            .comparing(AlbumFolderEntity::getNumPlays, Comparator.nullsFirst(Integer::compareTo))
            .reversed())
        .toList();

    return new ArtistDto(artistEntity.getPersistentIdentity(), artistEntity.getName(),
        artistEntity.getCoverArtPath(), artistEntity.getAlbumCount(), artistEntity.getSongCount(),
        artistEntity.getNumPlays(), SongLibraryMapper.toAlbumDtoList(artistEntity, albums));
  }

  public static List<AlbumDto> toAlbumDtoList(Collection<AlbumFolderEntity> albumEntities) {

    List<AlbumDto> albumDtos = new ArrayList<>();
    for (AlbumFolderEntity albumEntity : albumEntities) {

      ArtistFolderEntity artist = albumEntity.getParentArtist();

      albumDtos.add(toAlbumDto(artist, albumEntity));
    }
    return albumDtos;
  }

  public static List<AlbumDto> toAlbumDtoList(ArtistFolderEntity artist,
      Collection<AlbumFolderEntity> albumEntities) {

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

    return new AlbumDto(albumEntity.getParentGenre().getPersistentIdentity(),
        albumEntity.getParentGenre().getName(), artist.getPersistentIdentity(), artist.getName(),
        albumEntity.getPersistentIdentity(), albumEntity.getName(), albumEntity.hasExplicit(),
        albumEntity.getRecordLabel(), albumEntity.getReleaseDate().toString(),
        albumEntity.getCoverArtPath(), albumEntity.isCompilation(),
        SongLibraryMapper.toSongDtoList(artist, albumEntity, albumEntity.getChildSongs()));
  }

  public static List<SongDto> toSongDtoList(Collection<SongFileEntity> songEntities) {

    List<SongDto> songDtos = new ArrayList<>();
    for (SongFileEntity songEntity : songEntities) {

      AlbumFolderEntity album = songEntity.getAlbum();
      ArtistFolderEntity artist = album.getParentArtist();

      songDtos.add(toSongDto(artist, album, songEntity));
    }
    return songDtos;
  }

  public static List<SongDto> toSongDtoList(ArtistFolderEntity artist, AlbumFolderEntity album,
      Collection<SongFileEntity> songEntities) {

    List<SongDto> songDtos = new ArrayList<>();
    for (SongFileEntity songEntity : songEntities) {

      songDtos.add(toSongDto(artist, album, songEntity));
    }
    return songDtos;
  }

  public static SongDto toSongDto(SongFileEntity songEntity) {

    AlbumFolderEntity album = songEntity.getAlbum();
    ArtistFolderEntity artist = album.getParentArtist();

    return SongLibraryMapper.toSongDto(artist, album, songEntity);
  }

  public static SongDto toSongDto(ArtistFolderEntity artist, AlbumFolderEntity album,
      SongFileEntity songEntity) {

    // For compilation albums the parent artist is the "Compilations" folder, which is excluded from
    // the artist browse map. Use the individual song-level ArtistFromSongEntity ID instead so that
    // "View Artist" in the song popup navigates to the correct, browseable artist.
    Integer artistId = artist.getPersistentIdentity();
    if (album.isCompilation() && songEntity.getArtistName() != null) {
      ArtistFromSongEntity artistFromSong =
          album.getRootFolder().getArtistFromSong(songEntity.getArtistName());
      if (artistFromSong != null) {
        artistId = artistFromSong.getPersistentIdentity();
      }
    }

    return new SongDto(album.getParentGenre().getPersistentIdentity(),
        album.getParentGenre().getName(), artistId,
        songEntity.getArtistName(), album.getPersistentIdentity(), album.getName(),
        album.getCoverArtPath(), songEntity.getPersistentIdentity(), songEntity.getSongName(),
        songEntity.getTrackNumber(), songEntity.getNumPlays());
  }
}