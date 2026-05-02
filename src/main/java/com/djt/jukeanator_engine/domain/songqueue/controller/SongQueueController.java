package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.mapper.SongQueueMapper;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-queue")
public class SongQueueController {

  private final SongQueueService songQueueService;

  public SongQueueController(SongQueueService songQueueService) {
    requireNonNull(songQueueService, "songQueueService cannot be null");
    this.songQueueService = songQueueService;
  }

  @GetMapping("/queuedSongs")
  public List<SongQueueEntryDto> getQueuedSongs() {
    return SongQueueMapper.toDto(this.songQueueService.getQueuedSongs());
  }

  @PostMapping("/addSong")
  public ResponseEntity<Void> addSongToQueue(@RequestBody AddSongToQueueRequest request) throws IOException {

    songQueueService.addSongToQueue(request.getAlbumId(), request.getSongId(), request.getPriority());

    return ResponseEntity.ok().build();
  }
}
