package com.djt.jukeanator_engine.domain.songlibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "song-library")
public class SongLibraryProperties {

  private String repositoryType; // "filesystem" or "postgres"
  private String rootPath;
  private boolean requiresMetadata;
  private boolean useGenre;
  private boolean useTopFolderForGenre;

  private Discogs discogs = new Discogs();

  public static class Discogs {
    
    private String consumerKey;
    private String consumerSecret;

    public String getConsumerKey() {
      return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
      this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
      return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
      this.consumerSecret = consumerSecret;
    }
  }

  public String getRepositoryType() {
    return repositoryType;
  }

  public void setRepositoryType(String repositoryType) {
    this.repositoryType = repositoryType;
  }

  public String getRootPath() {
    return rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  public boolean isRequiresMetadata() {
    return requiresMetadata;
  }

  public void setRequiresMetadata(boolean requiresMetadata) {
    this.requiresMetadata = requiresMetadata;
  }

  public boolean isUseGenre() {
    return useGenre;
  }

  public void setUseGenre(boolean useGenre) {
    this.useGenre = useGenre;
  }

  public boolean isUseTopFolderForGenre() {
    return useTopFolderForGenre;
  }

  public void setUseTopFolderForGenre(boolean useTopFolderForGenre) {
    this.useTopFolderForGenre = useTopFolderForGenre;
  }

  public Discogs getDiscogs() {
    return discogs;
  }

  public void setDiscogs(Discogs discogs) {
    this.discogs = discogs;
  }
}
