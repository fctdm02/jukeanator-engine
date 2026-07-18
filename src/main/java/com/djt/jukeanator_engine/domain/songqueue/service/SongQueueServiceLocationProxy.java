package com.djt.jukeanator_engine.domain.songqueue.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Master-only, one instance per locationId (see {@code LocationServiceRegistry}). Every mutating
 * method is a round trip over {@link SlaveCommandGateway} to that location's real, local
 * {@code SongQueueServiceImpl} — the slave executes it against its own queue exactly as it would
 * for a local walk-up action, then replies with the result. This is the master-side analog of the
 * dead {@code SongQueueServiceHttpClient}, transport-swapped from direct HTTP (which can't reach a
 * slave with no public IP) to the push/reply gateway.
 *
 * @author tmyers
 */
public class SongQueueServiceLocationProxy implements SongQueueService {

  private static final String SYSTEM_METHOD_MESSAGE =
      "System method, not to be invoked on behalf of a user!";

  private final String locationId;
  private final SlaveCommandGateway slaveCommandGateway;

  public SongQueueServiceLocationProxy(String locationId, SlaveCommandGateway slaveCommandGateway) {
    this.locationId = locationId;
    this.slaveCommandGateway = slaveCommandGateway;
  }

  @Override
  public SongQueueEntryDto dequeueNextSong() {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public boolean isQueueEmpty() {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public boolean isBackgroundMusicEnabled() {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public Integer getHighestPriority() {
    return slaveCommandGateway.sendCommand(locationId, "getHighestPriority", null, Integer.class);
  }

  @Override
  public List<SongQueueEntryDto> getQueuedSongs() {
    return slaveCommandGateway.sendCommand(locationId, "getQueuedSongs", null,
        new TypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public String isSongEligibleForQueue(Integer albumId, Integer songId, Integer priority) {
    return slaveCommandGateway.sendCommand(locationId, "isSongEligibleForQueue",
        new EligibilityCheckPayload(albumId, songId, priority), String.class);
  }

  @Override
  public SongQueueEntryDto addSongToQueue(AddSongToQueueRequest addSongToQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "addSongToQueue", addSongToQueueRequest,
        SongQueueEntryDto.class);
  }

  @Override
  public List<SongQueueEntryDto> addAlbumToQueue(AddAlbumToQueueRequest addAlbumToQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "addAlbumToQueue", addAlbumToQueueRequest,
        new TypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public List<SongQueueEntryDto> addMultipleSongsToQueue(
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "addMultipleSongsToQueue",
        addMultipleSongsToQueueRequest, new TypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public Integer flushQueue() {
    return slaveCommandGateway.sendCommand(locationId, "flushQueue", null, Integer.class);
  }

  @Override
  public Integer randomizeQueue() {
    return slaveCommandGateway.sendCommand(locationId, "randomizeQueue", null, Integer.class);
  }

  @Override
  public Integer moveSongUpInQueue(ChangeSongQueueRequest changeSongQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "moveSongUpInQueue", changeSongQueueRequest,
        Integer.class);
  }

  @Override
  public Integer moveSongDownInQueue(ChangeSongQueueRequest changeSongQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "moveSongDownInQueue",
        changeSongQueueRequest, Integer.class);
  }

  @Override
  public Integer removeSongDownFromQueue(ChangeSongQueueRequest changeSongQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "removeSongDownFromQueue",
        changeSongQueueRequest, Integer.class);
  }

  @Override
  public Integer saveQueueAsPlaylist(String filename) {
    return slaveCommandGateway.sendCommand(locationId, "saveQueueAsPlaylist", filename,
        Integer.class);
  }

  @Override
  public Integer loadPlaylistIntoQueue(LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {
    return slaveCommandGateway.sendCommand(locationId, "loadPlaylistIntoQueue",
        loadPlaylistIntoQueueRequest, Integer.class);
  }

  private record EligibilityCheckPayload(Integer albumId, Integer songId, Integer priority) {}
}
