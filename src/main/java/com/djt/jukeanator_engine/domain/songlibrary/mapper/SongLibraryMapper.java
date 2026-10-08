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

    return new GenreDto(genreEntity.getId(), genreEntity.getName(), albumIds,
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

    List<AlbumDto> albumDtos = new ArrayList<>();
    for (AlbumFolderEntity album : albums) {

      AlbumDto albumDto = toAlbumDto(artistEntity, album);
      List<Integer> creditedSongIds = getCreditedSongIdsOnOtherArtistsAlbum(artistEntity, album);
      albumDtos.add(
          creditedSongIds.isEmpty() ? albumDto : albumDto.withMatchedSongIds(creditedSongIds));
    }

    return new ArtistDto(artistEntity.getId(), artistEntity.getName(),
        artistEntity.getCoverArtPath(), artistEntity.getAlbumCount(), artistEntity.getSongCount(),
        artistEntity.getNumPlays(), albumDtos);
  }

  /**
   * For an album that belongs to a different artist folder (e.g. a "Compilations" soundtrack that
   * an {@link ArtistFromSongEntity} is merely credited on), returns the ids of the tracks credited
   * to {@code artist}, so the album detail opened from that artist's view can list just those.
   * Returns an empty list for the artist's own albums, which list every track.
   */
  private static List<Integer> getCreditedSongIdsOnOtherArtistsAlbum(ArtistFolderEntity artist,
      AlbumFolderEntity album) {

    if (album.getParentArtist().getName().equalsIgnoreCase(artist.getName())) {
      return List.of();
    }
    return album.getChildSongs().stream()
        .filter(song -> artist.getName().equalsIgnoreCase(song.getArtistName()))
        .map(SongFileEntity::getId).toList();
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

		List<SongDto> songs = SongLibraryMapper.toSongDtoList(artist, albumEntity, albumEntity.getChildSongs());

		int songNumPlays = 0;
		for (SongDto song : songs) {
			songNumPlays = songNumPlays + song.numPlays();
		}

		return new AlbumDto(albumEntity.getParentGenre().getId(), albumEntity.getParentGenre().getName(),
				artist.getId(), artist.getName(), albumEntity.getId(), albumEntity.getName(), albumEntity.hasExplicit(),
				albumEntity.getRecordLabel(), albumEntity.getReleaseDate().toString(), albumEntity.getCoverArtPath(),
				albumEntity.isCompilation(), Integer.valueOf(songNumPlays), songs);
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

    // RootFolderEntity.initialize() builds artistsMap keyed by name, adding ArtistFromSongEntity
    // instances first. When a regular ArtistFolderEntity shares a name with an ArtistFromSongEntity
    // (which happens whenever artist names appear in song filenames), the two have different
    // id values and the ArtistFolderEntity ends up absent from artistsMap. Using
    // the ArtistFromSongEntity's ID here keeps the artistId in the DTO consistent with what
    // getArtistById can actually resolve.
    Integer artistId = artist.getId();
    // Use the song's embedded artist name for lookup if present; otherwise fall back to the folder
    // artist's name. This ensures we get the ID that artistsMap actually holds — which may be the
    // ArtistFromSongEntity's ID rather than the ArtistFolderEntity's ID when both share a name.
    String lookupName =
        songEntity.getArtistName() != null ? songEntity.getArtistName() : artist.getName();
    ArtistFromSongEntity artistFromSong =
        album.getRootFolder().getArtistFromSong(lookupName);
    if (artistFromSong != null) {
      artistId = artistFromSong.getId();
    }

    return new SongDto(album.getParentGenre().getId(),
        album.getParentGenre().getName(), artistId,
        songEntity.getArtistName(), album.getId(), album.getName(),
        album.getCoverArtPath(), songEntity.getId(), songEntity.getSongName(),
        songEntity.getTrackNumber(), songEntity.getNumPlays());
  }
}