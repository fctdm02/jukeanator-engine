package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-queue")
public class SongQueueController implements SongQueueService {

  private final SongQueueService songQueueService;

  public SongQueueController(SongQueueService songQueueService) {
    requireNonNull(songQueueService, "songQueueService cannot be null");
    this.songQueueService = songQueueService;
  }

  @Override
  @GetMapping("/queuedSongs")
  public List<SongQueueEntryDto> getQueuedSongs() {
    return songQueueService.getQueuedSongs();
  }

  @Override
  @GetMapping("/first")
  public SongQueueEntryDto getFirstEntryInSongQueue() {
    return songQueueService.getFirstEntryInSongQueue();
  }

  /**
   * HTTP adapter endpoint using request DTO.
   */
  @PostMapping("/addSong")
  public Integer addSongToQueue(@RequestBody AddSongToQueueRequest request) {

    return songQueueService.addSongToQueue(request.getAlbumId(), request.getSongId(),
        request.getPriority());
  }

  /**
   * Interface implementation.
   */
  @Override
  public Integer addSongToQueue(Integer albumId, Integer songId, Integer priority) {

    return songQueueService.addSongToQueue(albumId, songId, priority);
  }
}
