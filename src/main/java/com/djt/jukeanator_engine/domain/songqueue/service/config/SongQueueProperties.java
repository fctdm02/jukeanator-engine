package com.djt.jukeanator_engine.domain.songqueue.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "song-queue")
public class SongQueueProperties {

  private String repositoryType; // "filesystem" or "postgres"

  public String getRepositoryType() {
    return repositoryType;
  }

  public void setRepositoryType(String repositoryType) {
    this.repositoryType = repositoryType;
  }
}
