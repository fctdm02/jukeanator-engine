package com.djt.jukeanator_engine.domain.location.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotAlbumDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotArtistDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotGenreDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotSongDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationSummaryDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.user.dto.CreditTransactionDto;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionType;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * This controller previously had no test coverage at all. Adding it now because every DTO on its
 * endpoints (LibrarySnapshotDto's whole nested graph in particular) was converted from a
 * hand-written class to a record as part of the DTO-to-record migration, and this is the one
 * controller in the app that both serializes and deserializes that graph over the wire (master
 * receiving a slave's library sync).
 */
class LocationControllerTest extends AbstractControllerTest {

  private static final Integer LOCATION_ID = 7;

  @Mock
  private LocationService locationService;

  @Mock
  private UserService userService;

  @InjectMocks
  private LocationController locationController;

  @Override
  protected Object getController() {
    return locationController;
  }

  @Test
  void registerLocation_returnsProvisionedLocation() throws Exception {
    RegisterLocationRequest request = new RegisterLocationRequest("Bar Downtown", 40.0, -73.0);
    ProvisionedLocationDto provisioned = new ProvisionedLocationDto(LOCATION_ID, "plaintext-key", "Bar Downtown");
    when(locationService.registerLocation(any(RegisterLocationRequest.class)))
        .thenReturn(provisioned);

    mockMvc.perform(post("/api/locations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locationId", is(LOCATION_ID)))
        .andExpect(jsonPath("$.apiKey", is("plaintext-key")))
        .andExpect(jsonPath("$.name", is("Bar Downtown")));

    verify(locationService).registerLocation(any(RegisterLocationRequest.class));
  }

  @Test
  void listLocations_returnsSummariesFromService() throws Exception {
    LocationSummaryDto summary =
        new LocationSummaryDto(LOCATION_ID, "Bar Downtown", "logo.png", 40.0, -73.0, true);
    when(locationService.listLocations()).thenReturn(List.of(summary));

    mockMvc.perform(get("/api/locations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].locationId", is(LOCATION_ID)))
        .andExpect(jsonPath("$[0].name", is("Bar Downtown")))
        .andExpect(jsonPath("$[0].online", is(true)));
  }

  @Test
  void syncLibraryMetadata_deserializesNestedSnapshotAndReturnsAck() throws Exception {
    // Exercises deserialization all the way down LibrarySnapshotDto's nested record graph
    // (genres/artists/albums/songs) plus serialization of the ack response — the fullest
    // round-trip test of the record conversion available anywhere in the REST layer.
    LibrarySnapshotSongDto song = new LibrarySnapshotSongDto(1, "Song One", 1, 5);
    LibrarySnapshotAlbumDto album = new LibrarySnapshotAlbumDto(1, "Album One", 1, "Artist One", 1,
        "Rock", "hash123", true, "Label", "2020", false, List.of(song));
    LibrarySnapshotDto snapshot = new LibrarySnapshotDto(
        List.of(new LibrarySnapshotGenreDto(1, "Rock")),
        List.of(new LibrarySnapshotArtistDto(1, "Artist One")),
        List.of(album));

    LibrarySyncAckDto ack = new LibrarySyncAckDto(List.of(1, 2, 3));
    when(locationService.receiveLibraryMetadataSync(eq(LOCATION_ID), eq("secret-key"), any(LibrarySnapshotDto.class)))
        .thenReturn(ack);

    mockMvc.perform(post("/api/locations/" + LOCATION_ID + "/library-sync/metadata")
            .header(LocationController.LOCATION_API_KEY_HEADER, "secret-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(snapshot)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceAlbumIdsNeedingCoverArt", is(List.of(1, 2, 3))));

    verify(locationService).receiveLibraryMetadataSync(eq(LOCATION_ID), eq("secret-key"),
        any(LibrarySnapshotDto.class));
  }

  @Test
  void syncCoverArt_delegatesToServiceAndReturnsNoContent() throws Exception {
    byte[] imageBytes = new byte[] {1, 2, 3, 4};

    mockMvc.perform(post("/api/locations/" + LOCATION_ID + "/library-sync/cover-art/9")
            .header(LocationController.LOCATION_API_KEY_HEADER, "secret-key")
            .contentType(MediaType.IMAGE_JPEG)
            .content(imageBytes))
        .andExpect(status().isNoContent());

    verify(locationService).receiveLibraryCoverArt(eq(LOCATION_ID), eq("secret-key"), eq(9),
        any(byte[].class));
  }

  @Test
  void getCreditLedger_returnsTransactionsFromService() throws Exception {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-02-01T00:00:00Z");
    CreditTransactionDto transaction = new CreditTransactionDto("jane@example.com", LOCATION_ID, 5,
        CreditTransactionType.QUEUE_ADD, Instant.parse("2026-01-15T00:00:00Z"), 3, 4, 12);
    when(userService.getCreditLedgerForLocation(LOCATION_ID, from, to))
        .thenReturn(List.of(transaction));

    mockMvc.perform(get("/api/locations/" + LOCATION_ID + "/credit-ledger")
            .param("from", from.toString())
            .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userEmail", is("jane@example.com")))
        .andExpect(jsonPath("$[0].amount", is(5)))
        .andExpect(jsonPath("$[0].type", is("QUEUE_ADD")))
        .andExpect(jsonPath("$[0].resultingBalance", is(12)));
  }
}
