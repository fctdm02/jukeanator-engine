package com.djt.jukeanator_engine.domain.backgroundmusic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code background-music:} YAML prefix.
 */
@Validated
@ConfigurationProperties(prefix = "background-music")
public class BackgroundMusicProperties {

  // BACKGROUND MUSIC (MUTUALLY EXCLUSIVE TO LINE IN MUSIC), WILL BE EMPLOYED TO KEEP QUEUE AT A MIN
  // SIZE. ASSUMES PLAYLIST FILE CALLED: "BackgroundMusic.TXT" EXISTS IN rootPath
  private boolean enableBackgroundMusic = false;
  private boolean enableSmartBackgroundMusicAdditions = true; // will play songs from same
                                                               // artist/album from background music
  private int smartBackgroundMusicAdditionsFactor = 2; // for every song in BackgroundMusic.TXT,
                                                         // supplant with this number of songs by
                                                         // same album/artist, preferring popular
                                                         // songs
  private int smartBackgroundMusicAdditionsBegin = 19; // start time for
                                                         // enableSmartBackgroundMusicAdditions
  private int smartBackgroundMusicAdditionsEnd = 5; // end time for
                                                      // enableSmartBackgroundMusicAdditions
  private int smartBackgroundMusicMinPlays = 0; // minimum number of song plays a candidate must
                                                  // have to be eligible as a smart addition

  public boolean isEnableBackgroundMusic() {
    return enableBackgroundMusic;
  }

  public void setEnableBackgroundMusic(boolean enableBackgroundMusic) {
    this.enableBackgroundMusic = enableBackgroundMusic;
  }

  public boolean isEnableSmartBackgroundMusicAdditions() {
    return enableSmartBackgroundMusicAdditions;
  }

  public void setEnableSmartBackgroundMusicAdditions(boolean enableSmartBackgroundMusicAdditions) {
    this.enableSmartBackgroundMusicAdditions = enableSmartBackgroundMusicAdditions;
  }

  public int getSmartBackgroundMusicAdditionsFactor() {
    return smartBackgroundMusicAdditionsFactor;
  }

  public void setSmartBackgroundMusicAdditionsFactor(int smartBackgroundMusicAdditionsFactor) {
    this.smartBackgroundMusicAdditionsFactor = smartBackgroundMusicAdditionsFactor;
  }

  public int getSmartBackgroundMusicAdditionsBegin() {
    return smartBackgroundMusicAdditionsBegin;
  }

  public void setSmartBackgroundMusicAdditionsBegin(int smartBackgroundMusicAdditionsBegin) {
    this.smartBackgroundMusicAdditionsBegin = smartBackgroundMusicAdditionsBegin;
  }

  public int getSmartBackgroundMusicAdditionsEnd() {
    return smartBackgroundMusicAdditionsEnd;
  }

  public void setSmartBackgroundMusicAdditionsEnd(int smartBackgroundMusicAdditionsEnd) {
    this.smartBackgroundMusicAdditionsEnd = smartBackgroundMusicAdditionsEnd;
  }

  public int getSmartBackgroundMusicMinPlays() {
    return smartBackgroundMusicMinPlays;
  }

  public void setSmartBackgroundMusicMinPlays(int smartBackgroundMusicMinPlays) {
    this.smartBackgroundMusicMinPlays = smartBackgroundMusicMinPlays;
  }
}
