package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-queue")
public class SongQueueController implements SongQueueService {

  private final SongQueueService songQueueService;

  public SongQueueController(@Qualifier("songQueueService") SongQueueService songQueueService) {

    requireNonNull(songQueueService, "songQueueService cannot be null");
    this.songQueueService = songQueueService;
  }

  @Override
  @GetMapping("/dequeueNextSong")
  public SongQueueEntryDto dequeueNextSong() {
    return songQueueService.dequeueNextSong();
  }

  @Override
  @GetMapping("/highestPriority")
  public Integer getHighestPriority() {

    return songQueueService.getHighestPriority();
  }

  @Override
  @GetMapping("/queuedSongs")
  public List<SongQueueEntryDto> getQueuedSongs() {
    return songQueueService.getQueuedSongs();
  }

  @Override
  @PostMapping("/addSong")
  public SongQueueEntryDto addSongToQueue(
      @RequestBody AddSongToQueueRequest addSongToQueueRequest) {

    return songQueueService.addSongToQueue(addSongToQueueRequest);
  }

  @Override
  @PostMapping("/addAlbum")
  public List<SongQueueEntryDto> addAlbumToQueue(
      @RequestBody AddAlbumToQueueRequest addAlbumToQueueRequest) {

    return songQueueService.addAlbumToQueue(addAlbumToQueueRequest);
  }

  @Override
  @PostMapping("/addMultipleSongs")
  public List<SongQueueEntryDto> addMultipleSongsToQueue(
      @RequestBody AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {

    return songQueueService.addMultipleSongsToQueue(addMultipleSongsToQueueRequest);
  }

  @Override
  @PostMapping("/flushQueue")
  public Integer flushQueue() {

    return songQueueService.flushQueue();
  }

  @Override
  @PostMapping("/randomizeQueue")
  public Integer randomizeQueue() {

    return songQueueService.randomizeQueue();
  }

  @Override
  @PostMapping("/moveSongUpInQueue")
  public Integer moveSongUpInQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    return songQueueService.moveSongUpInQueue(changeSongQueueRequest);
  }

  @Override
  @PostMapping("/moveSongDownInQueue")
  public Integer moveSongDownInQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    return songQueueService.moveSongDownInQueue(changeSongQueueRequest);
  }

  @Override
  @PostMapping("/removeSongDownFromQueue")
  public Integer removeSongDownFromQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    return songQueueService.removeSongDownFromQueue(changeSongQueueRequest);
  }


  @Override
  @PostMapping("/saveQueueAsPlaylist")
  public Integer saveQueueAsPlaylist(String filename) {

    return songQueueService.saveQueueAsPlaylist(filename);
  }

  @Override
  @PostMapping("/loadPlaylistIntoQueue")
  public Integer loadPlaylistIntoQueue(String filename) {

    return songQueueService.loadPlaylistIntoQueue(filename);
  }
}
