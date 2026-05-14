package com.djt.jukeanator_engine.domain.songplayer.service.utils;

import java.util.concurrent.atomic.AtomicReference;

import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

public class VlcMediaPlayer implements Player {

  private final MediaPlayerFactory factory;
  private final MediaPlayer mediaPlayer;

  private final AtomicReference<SongPlayerStatus> status =
      new AtomicReference<>(SongPlayerStatus.STOPPED);

  private volatile long durationMillis = 0;

  public VlcMediaPlayer() {

    this.factory = new MediaPlayerFactory();
    this.mediaPlayer = factory.mediaPlayers().newMediaPlayer();

    this.mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

      @Override
      public void playing(MediaPlayer mediaPlayer) {
        status.set(SongPlayerStatus.PLAYING);
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
      }

      // NOTE: no @Override (VLCJ version mismatch safe)
      /*
       * public void mediaReady(MediaPlayer mediaPlayer) { var info = mediaPlayer.media().info(); if
       * (info != null) { durationMillis = info.duration(); } }
       */
    });
  }

  public boolean playSongMedia(String songPath) {

    try {
      status.set(SongPlayerStatus.STOPPED);
      durationMillis = 0;

      // ✅ FIX: no MediaRef usage needed
      return mediaPlayer.media().play(songPath);

    } catch (Exception e) {
      status.set(SongPlayerStatus.STOPPED);
      return false;
    }
  }

  public void pause() {
    mediaPlayer.controls().pause();
  }

  public void stop() {
    mediaPlayer.controls().stop();
  }

  public SongPlayerStatus getStatus() {
    return status.get();
  }

  public long getElapsedSeconds() {
    return mediaPlayer.status().time() / 1000;
  }

  public long getTotalLengthSeconds() {
    return durationMillis / 1000;
  }

  public boolean isPlaying() {
    return status.get() == SongPlayerStatus.PLAYING;
  }

  public void release() {
    mediaPlayer.release();
    factory.release();
  }
}
/*
 * package com.djt.jukeanator_engine.domain.songplayer.service.utils;
 * 
 * import java.util.concurrent.atomic.AtomicReference; import
 * com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus; import
 * uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
 * 
 * public class VlcMediaPlayer implements Player {
 * 
 * private final EmbeddedMediaPlayerComponent mediaPlayerComponent;
 * 
 * private final AtomicReference<SongPlayerStatus> status = new
 * AtomicReference<>(SongPlayerStatus.STOPPED);
 * 
 * public VlcMediaPlayer() {
 * 
 * this.mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
 * 
 * mediaPlayerComponent.mediaPlayer().events() .addMediaPlayerEventListener(new
 * uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
 * 
 * @Override public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
 * 
 * status.set(SongPlayerStatus.PLAYING); }
 * 
 * @Override public void paused(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
 * 
 * status.set(SongPlayerStatus.PAUSED); }
 * 
 * @Override public void stopped(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
 * 
 * status.set(SongPlayerStatus.STOPPED); }
 * 
 * @Override public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
 * 
 * status.set(SongPlayerStatus.STOPPED); } }); }
 * 
 * public boolean playSongMedia(String songPath) {
 * 
 * return mediaPlayerComponent.mediaPlayer().media().play(songPath); }
 * 
 * public void pause() {
 * 
 * mediaPlayerComponent.mediaPlayer().controls().pause(); }
 * 
 * public void stop() {
 * 
 * mediaPlayerComponent.mediaPlayer().controls().stop(); }
 * 
 * public SongPlayerStatus getStatus() {
 * 
 * return status.get(); }
 * 
 * public long getElapsedSeconds() {
 * 
 * return mediaPlayerComponent.mediaPlayer().status().time() / 1000; }
 * 
 * public long getTotalLengthSeconds() {
 * 
 * return mediaPlayerComponent.mediaPlayer().media().info().duration() / 1000; }
 * 
 * public boolean isPlaying() {
 * 
 * return status.get() == SongPlayerStatus.PLAYING; }
 * 
 * public void release() {
 * 
 * mediaPlayerComponent.release(); } }
 */
