package com.djt.jukeanator_engine.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;

@Configuration
public class ClientConfiguration {

  @Bean
  SongLibraryServiceHttpClient songLibraryServiceHttpClient(UserInterfaceProperties properties) {

    return new SongLibraryServiceHttpClient(properties.getBaseUrl());
  }

  @Bean
  SongQueueServiceHttpClient songQueueServiceHttpClient(UserInterfaceProperties properties) {

    return new SongQueueServiceHttpClient(properties.getBaseUrl());
  }

  @Bean
  SongPlayerServiceHttpClient songPlayerServiceHttpClient(UserInterfaceProperties properties) {

    return new SongPlayerServiceHttpClient(properties.getBaseUrl());
  }
}
