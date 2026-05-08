package com.djt.jukeanator_engine.domain.songplayer.mapper;

import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;

/**
 * @author tmyers
 */
public final class SongPlayerMapper {

  public static final NowPlayingSongDto EMPTY_NOW_PLAYING_SONG_DTO = new NowPlayingSongDto(
      "",
      "",
      "",
      "");
  
  /**
   * @param entity
   * @return
   */
  public static NowPlayingSongDto toDto(SongQueueEntryEntity entity) {

    SongFileEntity song = entity.getSong();
    AlbumFolderEntity album = song.getAlbum();
    song.getArtistName();
    
    NowPlayingSongDto dto = new NowPlayingSongDto(
        album.getCoverArtPath(),
        song.getArtistName(),
        album.getName(),
        song.getSongName());

    return dto;
  }
}
