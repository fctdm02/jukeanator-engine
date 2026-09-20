package com.djt.jukeanator_engine.domain.location.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotAlbumDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotArtistDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotGenreDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotSongDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;

/**
 * End-to-end simulation of a master/slave pair's library-sync exchange, run against a live local
 * MySQL instance with {@code app.mode=master} and {@code app.repository-type=jpa} -- the same
 * combination {@link com.djt.jukeanator_engine.MySqlMasterModeJukeanatorEngineApplicationTests}
 * proves the Spring context assembles under, but driven end to end here over real HTTP instead of
 * a bare {@code contextLoads()} smoke test.
 *
 * <p>{@link LocationServiceTest} already covers every branch of {@link LocationService} against
 * mocked collaborators, but deliberately stubs {@code SongLibraryRepository} with a plain mock so
 * {@link LocationServiceImpl}'s {@code persistSnapshotToJpa} branch never runs there (see that
 * class's {@code songLibraryRepository} field comment). This test fills that gap: it stands up a
 * real embedded servlet container ({@code webEnvironment = RANDOM_PORT}) and drives the exact HTTP
 * requests a slave's {@code LibrarySyncService} sends and a master's {@code LocationController}
 * receives -- {@code POST /api/locations/{locationId}/library-sync/metadata} and
 * {@code POST /api/locations/{locationId}/library-sync/cover-art/{sourceAlbumId}}, both
 * authenticated the real way via the {@code location-id}/{@code location-api-key} headers (see
 * {@code LocationApiKeyAuthenticationFilter}) -- then asserts the synced catalog actually lands in
 * the JPA-backed {@code location} and {@code song_library} tables, not just the {@code
 * library.json} fallback file.
 *
 * <p>Provisioning itself ({@code registerLocation}) is called directly against the Spring-managed
 * {@link LocationService} bean rather than over HTTP: on a real master, that's an admin-authenticated
 * ({@code POST /api/locations} requires {@code hasRole("ADMIN")}) one-time setup step distinct from
 * the slave-to-master sync protocol this test is actually about, and re-deriving an admin JWT login
 * flow here would only add unrelated surface area. Calling it directly requires the same
 * authenticated-service-call context {@link AbstractServiceIntegrationTest} already provides for
 * every other direct-service-call integration test in this suite.
 *
 * <p>Requires a real MySQL server with a {@code jukeanator_test} database the {@code jukeanator}
 * user can access -- see {@code src/test/resources/application-test.yml}. No Docker/Testcontainers
 * dependency. Like {@code SongLibraryRepositoryJpaImplTest}, every location is provisioned with a
 * name unique to this test run (see {@link #uniqueLocationName}), so this test needs no table
 * truncation and is safe to run repeatedly against a persistent (non-ephemeral) database.
 *
 * @author tmyers
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa" })
class MasterSlaveLibrarySyncIntegrationTest extends AbstractServiceIntegrationTest {

  @Autowired
  private LocationService locationService;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private DataSource dataSource;

  @Test
  void masterSlavePair_provisionThenSyncMetadataOverHttpThenSyncCoverArtOverHttp_persistsCatalogToJpaAndDetectsCoverArtDelta()
      throws Exception {

    // ── master: an admin provisions a new slave location ───────────────────
    ProvisionedLocationDto provisioned = locationService.registerLocation(
        new RegisterLocationRequest(uniqueLocationName("Corner Tavern"), 42.33, -83.04));
    Integer locationId = provisioned.locationId();
    String apiKey = provisioned.apiKey();
    assertNotNull(locationId);
    assertNotNull(apiKey);
    assertLocationStatusAndSyncedAt(locationId, "PROVISIONED", false);

    // ── slave: scan completes, build the same flattened snapshot
    // LibrarySyncService.buildSnapshot() would, and POST it to master over real HTTP ─────
    LibrarySyncAckDto ack = postMetadataSync(locationId, apiKey,
        twoAlbumSnapshot("hash-album-one", "hash-album-two"));

    // Master tells the slave both albums' cover art is new and needs uploading.
    assertEquals(2, ack.sourceAlbumIdsNeedingCoverArt().size());
    assertTrue(ack.sourceAlbumIdsNeedingCoverArt().containsAll(List.of(501, 502)));

    // The location row reflects the sync (drives the admin-facing "online"/"last synced"
    // reporting) -- proves the HTTP request really reached LocationServiceImpl, not a stub.
    assertLocationStatusAndSyncedAt(locationId, "ACTIVE", true);

    // The catalog landed in the multi-tenant song_library table, not just library.json --
    // this is exactly the persistSnapshotToJpa branch LocationServiceTest's mock never runs.
    assertSongLibraryRowCount(locationId, 8); // 1 root + 1 genre + 1 artist + 2 albums + 3 songs
    assertAlbumRow(locationId, 501, "Album One");
    assertAlbumRow(locationId, 502, "Album Two");
    assertSongNumPlays(locationId, 9001, 3);
    assertSongNumPlays(locationId, 9002, 5);
    assertSongNumPlays(locationId, 9003, 1);

    // ── slave: follow up with the cover art master flagged as missing, over real HTTP ──
    byte[] albumOneCoverArt = { 1, 2, 3 };
    byte[] albumTwoCoverArt = { 4, 5, 6 };
    postCoverArt(locationId, apiKey, 501, albumOneCoverArt);
    postCoverArt(locationId, apiKey, 502, albumTwoCoverArt);

    assertArrayEquals(albumOneCoverArt,
        Files.readAllBytes(locationService.getCoverArtPath(locationId, 501)));
    assertArrayEquals(albumTwoCoverArt,
        Files.readAllBytes(locationService.getCoverArtPath(locationId, 502)));

    // ── slave: a later rescan re-syncs with album one's cover art unchanged and album two's
    // genuinely changed (e.g. new artwork tagged) -- master should only ask for the delta ──
    LibrarySyncAckDto secondAck = postMetadataSync(locationId, apiKey,
        twoAlbumSnapshot("hash-album-one", "hash-album-two-CHANGED"));

    assertEquals(List.of(502), secondAck.sourceAlbumIdsNeedingCoverArt());
    // The rescan rebuilds the tenant's song_library subtree rather than accumulating duplicate
    // rows on every sync -- row count must stay stable across syncs.
    assertSongLibraryRowCount(locationId, 8);
  }

  @Test
  void receiveLibraryMetadataSync_overHttp_rejectsWrongApiKey_andNeverPersistsToJpaOrLocationRow()
      throws Exception {

    ProvisionedLocationDto provisioned = locationService
        .registerLocation(new RegisterLocationRequest(uniqueLocationName("Locked Bar"), 1.0, 2.0));
    Integer locationId = provisioned.locationId();

    HttpHeaders headers = new HttpHeaders();
    headers.set(LocationController.LOCATION_API_KEY_HEADER, "not-the-real-key");
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<LibrarySnapshotDto> request =
        new HttpEntity<>(twoAlbumSnapshot("h1", "h2"), headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/api/locations/{locationId}/library-sync/metadata", request, String.class, locationId);

    assertTrue(response.getStatusCode().is4xxClientError(),
        "A sync request with the wrong api key must never reach LocationServiceImpl -- got "
            + response.getStatusCode());
    assertLocationStatusAndSyncedAt(locationId, "PROVISIONED", false);
    assertSongLibraryRowCount(locationId, 0);
  }

  // ── HTTP calls, exactly as LibrarySyncService (the slave) makes them ───────────────────

  private LibrarySyncAckDto postMetadataSync(Integer locationId, String apiKey,
      LibrarySnapshotDto snapshot) {

    HttpHeaders headers = new HttpHeaders();
    headers.set(LocationController.LOCATION_ID_HEADER, String.valueOf(locationId));
    headers.set(LocationController.LOCATION_API_KEY_HEADER, apiKey);
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<LibrarySnapshotDto> request = new HttpEntity<>(snapshot, headers);

    ResponseEntity<LibrarySyncAckDto> response = restTemplate.postForEntity(
        "/api/locations/{locationId}/library-sync/metadata", request, LibrarySyncAckDto.class,
        locationId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    return response.getBody();
  }

  private void postCoverArt(Integer locationId, String apiKey, int sourceAlbumId,
      byte[] imageBytes) {

    HttpHeaders headers = new HttpHeaders();
    headers.set(LocationController.LOCATION_ID_HEADER, String.valueOf(locationId));
    headers.set(LocationController.LOCATION_API_KEY_HEADER, apiKey);
    headers.setContentType(MediaType.IMAGE_JPEG);
    HttpEntity<byte[]> request = new HttpEntity<>(imageBytes, headers);

    ResponseEntity<Void> response = restTemplate.postForEntity(
        "/api/locations/{locationId}/library-sync/cover-art/{sourceAlbumId}", request, Void.class,
        locationId, sourceAlbumId);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
  }

  // ── fixtures ────────────────────────────────────────────────────────────

  /**
   * {@code location.name} is unique-constrained, and unlike {@code
   * SongLibraryRepositoryJpaImplTest}'s fixture helper (which registers via the raw repository and
   * so already knows its freshly minted locationId to suffix with), {@code registerLocation}
   * itself hands back the id only after the name has already been used -- so this suffixes with
   * {@code System.nanoTime()} instead, to stay collision-free against a persistent (non-ephemeral)
   * database across repeated runs of this test.
   */
  private static String uniqueLocationName(String baseName) {
    return baseName + " " + System.nanoTime();
  }

  private LibrarySnapshotDto twoAlbumSnapshot(String albumOneCoverHash, String albumTwoCoverHash) {

    List<LibrarySnapshotGenreDto> genres = List.of(new LibrarySnapshotGenreDto(2, "Rock"));
    List<LibrarySnapshotArtistDto> artists =
        List.of(new LibrarySnapshotArtistDto(101, "Artist One"));

    LibrarySnapshotAlbumDto albumOne = new LibrarySnapshotAlbumDto(501, "Album One", 101,
        "Artist One", 2, "Rock", albumOneCoverHash, false, "Indie Label", "2020-01-01", false,
        List.of(new LibrarySnapshotSongDto(9001, "Song A", 1, 3),
            new LibrarySnapshotSongDto(9002, "Song B", 2, 5)));

    LibrarySnapshotAlbumDto albumTwo = new LibrarySnapshotAlbumDto(502, "Album Two", 101,
        "Artist One", 2, "Rock", albumTwoCoverHash, false, "Indie Label", "2021-01-01", false,
        List.of(new LibrarySnapshotSongDto(9003, "Song C", 1, 1)));

    return new LibrarySnapshotDto(genres, artists, List.of(albumOne, albumTwo));
  }

  // ── raw-JDBC assertions against the live MySQL schema ──────────────────

  private void assertLocationStatusAndSyncedAt(Integer locationId, String expectedStatus,
      boolean expectSyncedAtSet) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection
            .prepareStatement("select status, library_last_synced_at from location where id = ?")) {

      statement.setInt(1, locationId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(expectedStatus, rs.getString("status"));
        rs.getTimestamp("library_last_synced_at");
        assertEquals(expectSyncedAtSet, !rs.wasNull());
      }
    }
  }

  private void assertSongLibraryRowCount(Integer locationId, int expectedCount)
      throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection
            .prepareStatement("select count(*) from song_library where parent_location_id = ?")) {

      statement.setInt(1, locationId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(expectedCount, rs.getInt(1));
      }
    }
  }

  private void assertAlbumRow(Integer locationId, int albumId, String expectedName)
      throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select name from song_library where parent_location_id = ? and id = ? "
                + "and class_discriminator = 'ALBUM'")) {

      statement.setInt(1, locationId);
      statement.setInt(2, albumId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected a synced ALBUM row for albumId " + albumId);
        assertEquals(expectedName, rs.getString("name"));
      }
    }
  }

  private void assertSongNumPlays(Integer locationId, int songId, int expectedNumPlays)
      throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select song_num_plays from song_library where parent_location_id = ? and id = ? "
                + "and class_discriminator = 'SONG'")) {

      statement.setInt(1, locationId);
      statement.setInt(2, songId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected a synced SONG row for songId " + songId);
        assertEquals(expectedNumPlays, rs.getInt("song_num_plays"));
      }
    }
  }
}
