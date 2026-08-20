package com.djt.jukeanator_engine.domain.songplayer.service;

import static java.util.Objects.requireNonNull;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * Master-only implementation of {@link SongPlayerService} -- every call is forwarded over {@link
 * SlaveCommandGateway} to the locationId's real, local player. Deliberately kept as a separate
 * class from {@link SongPlayerServiceImpl} rather than merged into one locationId-branching class
 * (the pattern used for {@code SongQueueServiceImpl}): that class's constructor has real side
 * effects -- spinning up a VLC/Winamp process and a continuously-running watchdog thread -- which
 * must never happen on a headless master host that likely has no audio hardware/software
 * installed at all. Selected instead of {@link SongPlayerServiceImpl} by the same
 * repository-type-style {@code @ConditionalOnProperty}/{@code NotMasterModeCondition} bean
 * selection pattern already used elsewhere in {@code AppConfig}.
 *
 * @author tmyers
 */
public class SongPlayerServiceMasterImpl implements SongPlayerService {

  private static final String SYSTEM_METHOD_MESSAGE =
      "System method, not to be invoked on behalf of a user!";

  private final SlaveCommandGateway slaveCommandGateway;

  public SongPlayerServiceMasterImpl(SlaveCommandGateway slaveCommandGateway) {
    this.slaveCommandGateway =
        requireNonNull(slaveCommandGateway, "slaveCommandGateway cannot be null");
  }

  @Override
  public SongDto getNowPlayingSong(Integer locationId) {
    return slaveCommandGateway.sendCommand(locationId, "getNowPlayingSong", null, SongDto.class);
  }

  @Override
  public SongPlaybackStatusDto getPlaybackStatus(Integer locationId) {
    return slaveCommandGateway.sendCommand(locationId, "getPlaybackStatus", null,
        SongPlaybackStatusDto.class);
  }

  @Override
  public void playNextTrack(Integer locationId) {
    slaveCommandGateway.sendCommand(locationId, "playNextTrack", null);
  }

  @Override
  public void pause(Integer locationId) {
    slaveCommandGateway.sendCommand(locationId, "pause", null);
  }

  @Override
  public void stop(Integer locationId) {
    slaveCommandGateway.sendCommand(locationId, "stop", null);
  }

  @Override
  public void lockQueue(Integer locationId) {
    slaveCommandGateway.sendCommand(locationId, "lockQueue", null);
  }

  @Override
  public void unlockQueue(Integer locationId) {
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
