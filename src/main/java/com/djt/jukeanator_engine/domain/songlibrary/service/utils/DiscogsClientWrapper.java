package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static java.util.Objects.requireNonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import com.amilesend.client.connection.ThrottledException;
import com.amilesend.discogs.Discogs;
import com.amilesend.discogs.model.database.SearchRequest;
import com.amilesend.discogs.model.database.SearchResponse;
import com.amilesend.discogs.model.database.type.SearchResult;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.config.SongLibraryProperties;

/**
 * @author tmyers
 * 
 *
 *         <pre>
    Consumer Key    vBSFEvNtGflHQnULBNnL
    Consumer Secret AOOYhlvSshYkJieLRrdTCUoLcsWACfWW    
    Request Token URL   https://api.discogs.com/oauth/request_token
    Authorize URL   https://www.discogs.com/oauth/authorize
    Access Token URL    https://api.discogs.com/oauth/access_token
 *         </pre>
 */
public final class DiscogsClientWrapper {
  
  private static final Logger log = LoggerFactory.getLogger(DiscogsClientWrapper.class);

  public static final String USER_AGENT = "JukeANatorUserAgent/1.0";

  private Discogs discogsClient;

  @Bean
  public DiscogsClientWrapper discogsClientWrapper(SongLibraryProperties props) {
      return new DiscogsClientWrapper(
              props.getDiscogs().getConsumerKey(),
              props.getDiscogs().getConsumerSecret()
      );
  }
  
  public DiscogsClientWrapper(String consumerKey, String consumerSecret) {

    requireNonNull(consumerKey, "consumerKey cannot be null");
    requireNonNull(consumerSecret, "consumerSecret cannot be null");

    discogsClient =
        Discogs.newKeySecretAuthenticatedInstance(consumerKey, consumerSecret, USER_AGENT);
  }

  public Map<String, String> searchForAlbumMetadata(String artist, String album) {

    log.info("searchForAlbumMetadata(): artist: {}, album: {}", artist, album);
    
    Map<String, String> albumMetadataResults = new HashMap<>();

    SearchRequest request = SearchRequest
        .builder()
        .artist(artist)
        .releaseTitle(album)
        .perPage(1)
        .page(1)
        .build();

    SearchResponse response = doSearchWrapper(request);
    
    List<SearchResult> searchResults = response.getResults();
    if (searchResults != null && !searchResults.isEmpty()) {

      SearchResult searchResult = searchResults.get(0);

      albumMetadataResults.put(AlbumMetaDataFileEntity.CoverArtURL, searchResult.getCoverImage());

      List<String> labels = searchResult.getLabel();
      if (labels != null && !labels.isEmpty()) {
        albumMetadataResults.put(AlbumMetaDataFileEntity.RecordLabel, labels.get(0));
      }

      albumMetadataResults.put(AlbumMetaDataFileEntity.ReleaseDate, searchResult.getYear());

      if (searchResult.getTitle().toLowerCase().contains("explicit")) {
        albumMetadataResults.put(AlbumMetaDataFileEntity.HasExplicit, "true");
      } else {
        List<String> formats = searchResult.getFormat();
        if (formats != null && !formats.isEmpty()
            && formats.get(0).toLowerCase().contains("explicit")) {
          albumMetadataResults.put(AlbumMetaDataFileEntity.HasExplicit, "true");
        }
      }
    }

    return albumMetadataResults;
  }

  private SearchResponse doSearchWrapper(SearchRequest request) {
    
    int maxRetries = 5;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return doSearch(request);
        } catch (ThrottledException ex) {

            int waitSeconds = 1; // fallback

            // If message contains "Retry after X seconds"
            String msg = ex.getMessage();
            if (msg != null && msg.contains("Retry after")) {
                waitSeconds = extractWaitTime(msg);
            }

            try {
                Thread.sleep(waitSeconds * 1000L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SongLibraryException(ie.getMessage(), ie);
            }
        }
    }

    try {
      int waitSeconds = 60;
      Thread.sleep(waitSeconds * 1000L);
  } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new SongLibraryException(ie.getMessage(), ie);
  }
    
    maxRetries = 5;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return doSearch(request);
        } catch (ThrottledException ex) {

            int waitSeconds = 1; // fallback

            // If message contains "Retry after X seconds"
            String msg = ex.getMessage();
            if (msg != null && msg.contains("Retry after")) {
                waitSeconds = extractWaitTime(msg);
            }

            try {
                Thread.sleep(waitSeconds * 1000L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SongLibraryException(ie.getMessage(), ie);
            }
        }
    }    
    throw new SongLibraryException("Max retries exceeded calling Discogs");
  }
  
  private SearchResponse doSearch(SearchRequest request) {
    
    return discogsClient.getDatabaseApi().search(request);
  }
    
  private int extractWaitTime(String msg) {
    try {
        return Integer.parseInt(msg.replaceAll("\\D+", ""));
    } catch (Exception e) {
        return 1;
    }
}  
}
