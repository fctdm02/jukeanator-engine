package com.djt.jukeanator_engine.domain.songlibrary.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code song-library:} YAML prefix.
 *
 * <p>
 * Note: the filesystem root path previously held here is gone entirely -- the song library's
 * aggregate root is now discovered directly from {@code app.data-dir}, see
 * {@code SongLibraryServiceImpl#initialize()}.
 */
@Validated
@ConfigurationProperties(prefix = "song-library")
public class SongLibraryProperties {

  private boolean requiresMetadata;
  private boolean disableInternetSearch;
  private boolean useGenre;
  private boolean useTopFolderForGenre;
  private Set<String> acceptedSongFileExtensions;
  private Integer searchResultSize = Integer.valueOf(50);

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
  
  public boolean isRequiresMetadata() {
    return requiresMetadata;
  }

  public void setRequiresMetadata(boolean requiresMetadata) {
    this.requiresMetadata = requiresMetadata;
  }

  public boolean isDisableInternetSearch() {
    return disableInternetSearch;
  }

  public void setDisableInternetSearch(boolean disableInternetSearch) {
    this.disableInternetSearch = disableInternetSearch;
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

  public Set<String> getAcceptedSongFileExtensions() {
    return this.acceptedSongFileExtensions;
  }

  public void setAcceptedSongFileExtensions(Set<String> acceptedSongFileExtensions) {
    this.acceptedSongFileExtensions = acceptedSongFileExtensions;
  }

  public Discogs getDiscogs() {
    return discogs;
  }

  public void setDiscogs(Discogs discogs) {
    this.discogs = discogs;
  }

  public Integer getSearchResultSize() {
    return searchResultSize;
  }

  public void setSearchResultSize(Integer searchResultSize) {
    this.searchResultSize = searchResultSize;
  }
}
