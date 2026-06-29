package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author tmyers
 */
public class MusicBrainzClientWrapper {

  private static final Logger log = LoggerFactory.getLogger(MusicBrainzClientWrapper.class);

  private static final String BASE_URL = "https://musicbrainz.org/ws/2";
  public static final String USER_AGENT = "JukeANatorUserAgent/1.0 (tmyers1@yahoo.com)";
  private static final int MAX_RETRIES = 5;

  private RestClient client;
  private Semaphore rateLimiter;
  private ScheduledExecutorService scheduler;
  private boolean isInitialized = false;

  public MusicBrainzClientWrapper() {
    super();
  }

  // =========================================================
  // PUBLIC BLOCKING API (NO EXPLICIT CONCURRENCY)
  // =========================================================
  public List<AlbumMetadataDto> searchForAlbumMetadata(String artistName, String albumName,
      boolean useGenre) {

    return searchForAlbumMetadata(artistName, albumName, useGenre, 1);
  }

  public List<AlbumMetadataDto> searchForAlbumMetadata(String artistName, String albumName,
      boolean useGenre, int limit) {

    if (!isInitialized) {
      initialize();
    }

    log.info("searchForAlbumMetadata(): artist: {}, album: {}, limit: {}", artistName, albumName,
        limit);

    List<AlbumMetadataDto> albumMetadataResults = new ArrayList<>();

    List<AlbumResult> albumResults = lookupAlbum(artistName, albumName, useGenre, limit);
    for (AlbumResult albumResult : albumResults) {

      if (albumResult != null) {

        String genre = albumResult.genre;
        if (genre != null && !genre.trim().isBlank()) {
          genre = GenreNormalizer.normalize(genre);
        }

        String coverArtUrl = albumResult.coverArtUrl;
        if (coverArtUrl == null || coverArtUrl.trim().isBlank()) {
          coverArtUrl = "";
        }

        String recordLabel = albumResult.label;
        if (recordLabel == null || recordLabel.trim().isBlank()) {
          recordLabel = "";
        }

        String releaseDate = "";
        String year = albumResult.releaseDate;
        if (year != null && !year.trim().isBlank()) {

          if (year.length() > 4) {
            releaseDate = year.substring(0, 4);
          } else {
            releaseDate = year;
          }
        }

        boolean hasExplicit = false;

        albumMetadataResults.add(new AlbumMetadataDto(artistName, albumName, recordLabel,
            releaseDate, genre, coverArtUrl, hasExplicit));
      }
    }

    return albumMetadataResults;
  }

  private void initialize() {

    this.rateLimiter = new Semaphore(1);

    this.client =
        RestClient.builder().baseUrl(BASE_URL).defaultHeader("User-Agent", USER_AGENT).build();

    this.scheduler = Executors.newSingleThreadScheduledExecutor();

    this.scheduler.scheduleAtFixedRate(() -> {
      if (rateLimiter.availablePermits() == 0) {
        rateLimiter.release();
      }
    }, 0, 1, TimeUnit.SECONDS);

    this.isInitialized = true;
  }

  private List<AlbumResult> lookupAlbum(String artist, String album, boolean useGenre, int limit) {

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

      return executor.submit(() -> executeLookup(artist, album, useGenre, limit)).get();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // =========================================================
  // CORE LOGIC (SEQUENTIAL INSIDE VIRTUAL THREAD)
  // =========================================================
  private List<AlbumResult> executeLookup(String artist, String album, boolean useGenre,
      int limit) {

    String query = String.format("artist:\"%s\" AND release:\"%s\"", artist, album);

    ReleaseSearchResponse search = executeWithRetry(() -> {
      acquireRateLimit();

      return client.get()
          .uri(uriBuilder -> uriBuilder.path("/release").queryParam("query", query)
              .queryParam("fmt", "json").queryParam("limit", limit).build())
          .retrieve().body(ReleaseSearchResponse.class);
    });

    if (search == null || search.releases == null || search.releases.isEmpty()) {
      return List.of();
    }

    List<AlbumResult> albumResults = new ArrayList<>();
    for (Release release : search.releases) {

      Release full = executeWithRetry(() -> {
        acquireRateLimit();

        return client.get()
            .uri(uriBuilder -> uriBuilder.path("/release/{id}")
                .queryParam("inc", "labels+tags+genres+recordings").queryParam("fmt", "json")
                .build(release.id))
            .retrieve().body(Release.class);
      });

      // label
      String label = null;
      if (full != null && full.labelInfo != null) {
        label = full.labelInfo.stream().map(li -> li.label != null ? li.label.name : null)
            .filter(n -> n != null && !n.isBlank()).findFirst().orElse(null);
      }

      // genre
      String genre = null;
      if (full != null && full.tags != null) {
        genre = full.tags.stream().sorted((a, b) -> Integer.compare(b.count, a.count))
            .map(t -> t.name).findFirst().orElse(null);
      }
      if (useGenre && genre == null) {
        Optional<String> artistMbid = findArtistMbid(artist);
        if (artistMbid.isPresent()) {
          Optional<String> topGenre = getTopGenreByArtist(artistMbid.get());
          if (topGenre.isPresent()) {
            genre = topGenre.get();
          }
        }
      }

      String coverArt = "https://coverartarchive.org/release/" + release.id + "/front";

      albumResults.add(new AlbumResult(release.title, release.date, label, genre, coverArt));

      if (albumResults.size() >= limit) {
        break;
      }
    }
    return albumResults;
  }

  private Optional<String> findArtistMbid(String artistName) {

    ArtistSearchResponse response = executeWithRetry(() -> {
      acquireRateLimit();

      return client
          .get().uri(uriBuilder -> uriBuilder.path("/artist")
              .queryParam("query", "artist:" + artistName).queryParam("fmt", "json").build())
          .retrieve().body(ArtistSearchResponse.class);
    });

    if (response == null || response.artists() == null || response.artists().isEmpty()) {
      return Optional.empty();
    }

    return response.artists().stream().filter(a -> a.name() != null)
        .sorted(
            Comparator.comparing((ArtistSummary a) -> a.name().equalsIgnoreCase(artistName) ? 0 : 1)
                .thenComparing(a -> a.disambiguation() == null ? 1 : 0))
        .map(ArtistSummary::id).findFirst();
  }

  private Optional<String> getTopGenreByArtist(String artistMbid) {

    ArtistResponse response = executeWithRetry(() -> {
      acquireRateLimit();

      return client.get().uri(uriBuilder -> uriBuilder.path("/artist/{mbid}")
          .queryParam("inc", "genres").queryParam("fmt", "json").build(artistMbid)).retrieve()
          .body(ArtistResponse.class);
    });

    if (response == null || response.genres() == null || response.genres().isEmpty()) {
      return Optional.empty();
    }

    return response.genres().stream().filter(g -> g.name() != null && g.count() != null)
        .max(Comparator.comparingInt(Genre::count)).map(Genre::name);
  }

  // =========================================================
  // RATE LIMIT + RETRY
  // =========================================================
  private void acquireRateLimit() {
    try {
      rateLimiter.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private <T> T executeWithRetry(java.util.function.Supplier<T> call) {

    int attempt = 0;

    while (true) {
      try {
        return call.get();

      } catch (HttpServerErrorException.ServiceUnavailable
          | HttpClientErrorException.TooManyRequests ex) {

        attempt++;

        if (attempt > MAX_RETRIES) {
          throw ex;
        }

        long backoff = (long) (500 * Math.pow(2, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, 250);

        sleep(backoff + jitter);
      }
    }
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // =========================================================
  // RESULT MODEL
  // =========================================================
  public record AlbumResult(String title, String releaseDate, String label, String genre,
      String coverArtUrl) {
  }
  
  // =========================================================
  // DTOs
  // =========================================================
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ReleaseSearchResponse {
    public List<Release> releases;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Release {
    public String id;
    public String title;
    public String date;

    @JsonProperty("label-info")
    public List<LabelInfo> labelInfo;

    public List<Tag> tags;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LabelInfo {
    public Label label;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Label {
    public String name;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Tag {
    public String name;
    public int count;
  }

  public record ArtistSearchResponse(List<ArtistSummary> artists) {
  }

  public record ArtistSummary(String id, String name, String disambiguation, String country) {
  }

  public record ArtistResponse(String id, String name, List<Genre> genres) {
  }

  public record Genre(String name, Integer count) {
  }
}
