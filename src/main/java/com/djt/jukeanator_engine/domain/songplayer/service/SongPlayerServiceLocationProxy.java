package com.djt.jukeanator_engine.domain.songplayer.service;

import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * Master-only, one instance per locationId — the {@code SongPlayerService} analog of
 * {@link com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceLocationProxy}.
 *
 * @author tmyers
 */
public class SongPlayerServiceLocationProxy implements SongPlayerService {

  private static final String SYSTEM_METHOD_MESSAGE =
      "System method, not to be invoked on behalf of a user!";

  private final String locationId;
  private final SlaveCommandGateway slaveCommandGateway;

  public SongPlayerServiceLocationProxy(String locationId,
      SlaveCommandGateway slaveCommandGateway) {
    this.locationId = locationId;
    this.slaveCommandGateway = slaveCommandGateway;
  }

  @Override
  public SongDto getNowPlayingSong() {
    return slaveCommandGateway.sendCommand(locationId, "getNowPlayingSong", null, SongDto.class);
  }

  @Override
  public SongPlaybackStatusDto getPlaybackStatus() {
    return slaveCommandGateway.sendCommand(locationId, "getPlaybackStatus", null,
        SongPlaybackStatusDto.class);
  }

  @Override
  public void playNextTrack() {
    slaveCommandGateway.sendCommand(locationId, "playNextTrack", null);
  }

  @Override
  public void pause() {
    slaveCommandGateway.sendCommand(locationId, "pause", null);
  }

  @Override
  public void stop() {
    slaveCommandGateway.sendCommand(locationId, "stop", null);
  }

  @Override
  public void lockQueue() {
    slaveCommandGateway.sendCommand(locationId, "lockQueue", null);
  }

  @Override
  public void unlockQueue() {
    slaveCommandGateway.sendCommand(locationId, "unlockQueue", null);
  }

  @Override
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }
}
