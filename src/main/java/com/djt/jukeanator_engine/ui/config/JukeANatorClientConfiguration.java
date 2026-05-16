package com.djt.jukeanator_engine.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;

@Configuration
public class JukeANatorClientConfiguration {

  @Bean
  SongLibraryServiceHttpClient songLibraryServiceHttpClient(JukeANatorUserInterfaceProperties properties) {

    return new SongLibraryServiceHttpClient(properties.getBaseUrl());
  }

  @Bean
  SongQueueServiceHttpClient songQueueServiceHttpClient(JukeANatorUserInterfaceProperties properties) {

    return new SongQueueServiceHttpClient(properties.getBaseUrl());
  }

  @Bean
  SongPlayerServiceHttpClient songPlayerServiceHttpClient(JukeANatorUserInterfaceProperties properties) {

    return new SongPlayerServiceHttpClient(properties.getBaseUrl());
  }
}
