package com.djt.jukeanator_engine.domain.location.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code location:} YAML prefix. Only meaningful when {@code app.mode}
 * is {@code master} (repository/storage settings) or {@code slave} (nothing here yet — slave
 * connection settings live under {@code app.*}, see {@link com.djt.jukeanator_engine.config.AppProperties}).
 */
@Validated
@ConfigurationProperties(prefix = "location")
public class LocationProperties {

  private String repositoryType = "filesystem"; // "filesystem" or "postgres"
  private String storageRoot; // master-only: where per-location library/cover-art syncs land
  private long commandTimeoutMs = 10_000L; // master-only: Phase 2 slave command round-trip timeout

  public String getRepositoryType() {
    return repositoryType;
  }

  public void setRepositoryType(String repositoryType) {
    this.repositoryType = repositoryType;
  }

  public String getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(String storageRoot) {
    this.storageRoot = storageRoot;
  }

  public long getCommandTimeoutMs() {
    return commandTimeoutMs;
  }

  public void setCommandTimeoutMs(long commandTimeoutMs) {
    this.commandTimeoutMs = commandTimeoutMs;
  }
}
