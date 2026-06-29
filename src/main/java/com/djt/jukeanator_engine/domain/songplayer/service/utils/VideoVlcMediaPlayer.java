package com.djt.jukeanator_engine.domain.songplayer.service.utils;

import java.util.concurrent.atomic.AtomicReference;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.exception.SongPlayerServiceException;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

public class VideoVlcMediaPlayer implements Player {

  private final MediaPlayerFactory factory;
  private final MediaPlayer mediaPlayer;
  private final AtomicReference<SongPlayerStatus> status =
      new AtomicReference<>(SongPlayerStatus.STOPPED);

  private volatile Runnable onFinished;
  private volatile long durationMillis = 0;

  // Track the desired volume state (LibVLC default is 100)
  private volatile int currentVolume = 100;

  public VideoVlcMediaPlayer(int volume) {

    this.currentVolume = volume;

    this.factory = new MediaPlayerFactory();
    this.mediaPlayer = factory.mediaPlayers().newMediaPlayer();

    this.mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

      @Override
      public void playing(MediaPlayer mediaPlayer) {
        status.set(SongPlayerStatus.PLAYING);

        // FIX: Enforce the tracked volume reliably as soon as the media starts playing
        mediaPlayer.audio().setVolume(currentVolume);
      }

      @Override
      public void paused(MediaPlayer mediaPlayer) {
        status.set(SongPlayerStatus.PAUSED);
      }

      @Override
      public void stopped(MediaPlayer mediaPlayer) {
        status.set(SongPlayerStatus.STOPPED);
      }

      @Override
      public void finished(MediaPlayer mediaPlayer) {

        status.set(SongPlayerStatus.STOPPED);
        Runnable callback = onFinished;
        if (callback != null) {
          callback.run();
        }
      }

      // NOTE: no @Override (VLCJ version mismatch safe)
      /*
       * public void mediaReady(MediaPlayer mediaPlayer) { var info = mediaPlayer.media().info(); if
       * (info != null) { durationMillis = info.duration(); } }
       */
    });
  }

  @Override
  public boolean playSongMedia(String songPath) {

    try {
      status.set(SongPlayerStatus.STOPPED);
      durationMillis = 0;
      return mediaPlayer.media().play(songPath);

    } catch (Exception e) {

      status.set(SongPlayerStatus.STOPPED);
      durationMillis = 0;
      throw new SongPlayerServiceException(
          "UNABLE TO PLAY: " + songPath + ", error: " + e.getMessage());
    }
  }

  @Override
  public int getVolume() {

    // If actively playing, get the real-time runtime volume; otherwise return our tracked value
    return status.get() == SongPlayerStatus.PLAYING ? mediaPlayer.audio().volume() : currentVolume;
  }

  @Override
  public void setVolume(int volume) {

    this.currentVolume = volume;
    // Only attempt to pass it to LibVLC if the media is actively playing to prevent silent drops
    if (status.get() == SongPlayerStatus.PLAYING) {
      mediaPlayer.audio().setVolume(volume);
    }
  }

  @Override
  public void pause() {
    mediaPlayer.controls().pause();
  }

  @Override
  public void stop() {
    mediaPlayer.controls().stop();
  }

  @Override
  public SongPlayerStatus getStatus() {
    return status.get();
  }

  @Override
  public long getElapsedSeconds() {
    return mediaPlayer.status().time() / 1000;
  }

  @Override
  public long getTotalLengthSeconds() {
    return durationMillis / 1000;
  }

  @Override
  public void release() {
    mediaPlayer.release();
    factory.release();
  }

  @Override
  public void setOnFinished(Runnable callback) {
    this.onFinished = callback;
  }
}
