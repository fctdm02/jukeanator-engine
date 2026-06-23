package com.djt.jukeanator_engine.domain.songplayer.audio.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.djt.jukeanator_engine.domain.songplayer.audio.LineInService;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.audio.lineinput.LineInServiceImpl;
import com.djt.jukeanator_engine.domain.songplayer.audio.linux.LinuxMasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.audio.mac.MacMasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.audio.windows.WindowsMasterVolumeService;

@Configuration
@EnableConfigurationProperties(LineInProperties.class)
public class AudioPlatformConfig {

  @Bean
  public MasterVolumeService masterVolumeService() {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) {
      return new WindowsMasterVolumeService();
    } else if (os.contains("mac") || os.contains("darwin")) {
      return new MacMasterVolumeService();
    } else {
      return new LinuxMasterVolumeService();
    }
  }

  @Bean
  public LineInService lineInService(LineInProperties properties) {
    return new LineInServiceImpl(properties);
  }
}
