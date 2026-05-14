package com.djt.jukeanator_engine.domain.songplayer.event;

public sealed interface SongPlayerEvent permits 
  SongPlaybackStartedEvent, 
  SongPlaybackPausedEvent, 
  SongPlaybackStoppedEvent,
  SongPlaybackFinishedEvent, 
  SongPlaybackNextTrackRequestedEvent, 
  SongPlaybackShutdownEvent {
}
