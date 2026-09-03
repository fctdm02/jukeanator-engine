package com.djt.jukeanator_engine.domain.songlibrary.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AuthenticateForAdminPanelRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

class SongLibraryControllerTest extends AbstractControllerTest {

  private static final Integer LOCATION_ID = 7;
  private static final String BASE_PATH = "/api/locations/" + LOCATION_ID + "/song-library";

  @Mock
  private SongLibraryService songLibraryService;

  @Mock
  private LocationService locationService;

  @InjectMocks
  private SongLibraryController songLibraryController;

  @Override
  protected Object getController() {
    return songLibraryController;
  }

  private SongDto aSong() {
    return new SongDto(1, "Genre", 2, "Artist", 3, "Album", "/cover.jpg", 4, "Song", 1, 0);
  }

  @Test
  void getMusicByPopularity_delegatesToService() throws Exception {
    SearchResultDto result = new SearchResultDto(List.of(aSong()), List.of(), List.of(), 0, 0, 0);
    when(songLibraryService.getMusicByPopularity(LOCATION_ID)).thenReturn(result);

    mockMvc.perform(get(BASE_PATH + "/popular"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songs[0].songName", is("Song")));

    verify(songLibraryService).getMusicByPopularity(LOCATION_ID);
  }

  @Test
  void getMusicBySearch_passesSearchParam() throws Exception {
    when(songLibraryService.getMusicBySearch(LOCATION_ID, "foo", 20)).thenReturn(new SearchResultDto(List.of(), List.of(), List.of(), 0, 0, 0));

    mockMvc.perform(get(BASE_PATH + "/search").param("searchFor", "foo"))
        .andExpect(status().isOk());

    verify(songLibraryService).getMusicBySearch(LOCATION_ID, "foo", 20);
  }

  @Test
  void getGenres_returnsListFromService() throws Exception {
    GenreDto genre = new GenreDto(1, "Rock", List.of(1, 2), 5);
    when(songLibraryService.getGenres(LOCATION_ID)).thenReturn(List.of(genre));

    mockMvc.perform(get(BASE_PATH + "/genres"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].genreName", is("Rock")));
  }

  @Test
  void getGenreMusicByPopularity_passesGenreName() throws Exception {
    when(songLibraryService.getGenreMusicByPopularity(LOCATION_ID, "Rock"))
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of(), 0, 0, 0));

    mockMvc.perform(get(BASE_PATH + "/genres/popular").param("genreName", "Rock"))
        .andExpect(status().isOk());

    verify(songLibraryService).getGenreMusicByPopularity(LOCATION_ID, "Rock");
  }

  @Test
  void getGenreMusicByTitle_passesGenreName() throws Exception {
    when(songLibraryService.getGenreMusicByTitle(LOCATION_ID, "Rock")).thenReturn(new SearchResultDto(List.of(), List.of(), List.of(), 0, 0, 0));

    mockMvc.perform(get(BASE_PATH + "/genres/title").param("genreName", "Rock"))
        .andExpect(status().isOk());

    verify(songLibraryService).getGenreMusicByTitle(LOCATION_ID, "Rock");
  }

  @Test
  void getArtists_returnsListFromService() throws Exception {
    ArtistDto artist = new ArtistDto(1, "Artist", "/cover.jpg", 1, 10, 5, List.of());
    when(songLibraryService.getArtists(LOCATION_ID)).thenReturn(List.of(artist));

    mockMvc.perform(get(BASE_PATH + "/artists"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].artistName", is("Artist")));
  }

  @Test
  void getArtistByName_passesArtistName() throws Exception {
    ArtistDto artist = new ArtistDto(1, "Artist", "/cover.jpg", 1, 10, 5, List.of());
    when(songLibraryService.getArtistByName(LOCATION_ID, "Artist")).thenReturn(artist);

    mockMvc.perform(get(BASE_PATH + "/artist").param("artistName", "Artist"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.artistName", is("Artist")));
  }

  @Test
  void getArtistById_passesId() throws Exception {
    // Called by the web UI's artist detail screen (renderArtistDetail in app.js) when navigated
    // to via an artist's own tile rather than via an album.
    ArtistDto artist = new ArtistDto(1, "Artist", "/cover.jpg", 1, 10, 5, List.of());
    when(songLibraryService.getArtistById(LOCATION_ID, 1)).thenReturn(artist);

    mockMvc.perform(get(BASE_PATH + "/artists/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.artistName", is("Artist")));
  }

  @Test
  void getArtistByAlbumId_passesAlbumId() throws Exception {
    // Called by the web UI's artist detail screen (renderArtistDetail in app.js) when navigated
    // to from an album tile, which only carries an albumId, not the artist's own id.
    ArtistDto artist = new ArtistDto(1, "Artist", "/cover.jpg", 1, 10, 5, List.of());
    when(songLibraryService.getArtistByAlbumId(LOCATION_ID, 3)).thenReturn(artist);

    mockMvc.perform(get(BASE_PATH + "/artistByAlbum/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.artistName", is("Artist")));
  }

  @Test
  void getArtistCoverArt_returnsNotFoundWhenCoverArtPathMissing() throws Exception {
    ArtistDto artist = new ArtistDto(1, "Artist", null, 1, 10, 5, List.of());
    when(songLibraryService.getArtistById(LOCATION_ID, 1)).thenReturn(artist);

    mockMvc.perform(get(BASE_PATH + "/artists/1/coverArt"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getAlbums_returnsListFromService() throws Exception {
    AlbumDto album = new AlbumDto(1, "Genre", 2, "Artist", 3, "Album", false, "Label", "2020",
        "/cover.jpg", false, 0, List.of(aSong()));
    when(songLibraryService.getAlbums(LOCATION_ID)).thenReturn(List.of(album));

    mockMvc.perform(get(BASE_PATH + "/albums"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].albumName", is("Album")));
  }

  @Test
  void getAlbumById_serializesBooleanFieldsUsingRecordComponentNames() throws Exception {
    // Locks down the wire format of AlbumDto's boolean fields now that it's a record: Jackson
    // names a record's JSON properties after the record component verbatim (no JavaBean-style
    // "is"/"get" prefix stripping), so "isCompilation" no longer collapses to "compilation" the
    // way it did when AlbumDto was a plain class with an isCompilation() getter. If AlbumDto ever
    // moves back to a class, or the component gets renamed, this test will catch the wire-format
    // change.
    AlbumDto album = new AlbumDto(1, "Genre", 2, "Artist", 3, "Album", true, "Label", "2020",
        "/cover.jpg", true, 0, List.of(aSong()));
    when(songLibraryService.getAlbumById(LOCATION_ID, 3)).thenReturn(album);

    mockMvc.perform(get(BASE_PATH + "/albums/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasExplicit", is(true)))
        .andExpect(jsonPath("$.isCompilation", is(true)));
  }

  @Test
  void getAlbumsForGenre_passesGenreId() throws Exception {
    when(songLibraryService.getAlbumsForGenre(LOCATION_ID, 1)).thenReturn(List.of());

    mockMvc.perform(get(BASE_PATH + "/genres/1/albums"))
        .andExpect(status().isOk());

    verify(songLibraryService).getAlbumsForGenre(LOCATION_ID, 1);
  }

  @Test
  void getAlbumById_passesId() throws Exception {
    AlbumDto album = new AlbumDto(1, "Genre", 2, "Artist", 3, "Album", false, "Label", "2020",
        "/cover.jpg", false, 0, List.of(aSong()));
    when(songLibraryService.getAlbumById(LOCATION_ID, 3)).thenReturn(album);

    mockMvc.perform(get(BASE_PATH + "/albums/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.albumName", is("Album")));
  }

  @Test
  void getAlbumCoverArt_returnsNotFoundWhenAlbumMissing() throws Exception {
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songLibraryService.getAlbumById(LOCATION_ID, 99)).thenReturn(null);

    mockMvc.perform(get(BASE_PATH + "/albums/99/coverArt"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getAlbumCoverArt_returnsNotFoundWhenCoverArtPathMissing() throws Exception {
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    AlbumDto album = new AlbumDto(1, "Genre", 2, "Artist", 3, "Album", false, "Label", "2020",
        null, false, 0, List.of());
    when(songLibraryService.getAlbumById(LOCATION_ID, 3)).thenReturn(album);

    mockMvc.perform(get(BASE_PATH + "/albums/3/coverArt"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getSongById_passesAlbumAndSongId() throws Exception {
    when(songLibraryService.getSongById(LOCATION_ID, 3, 4)).thenReturn(aSong());

    mockMvc.perform(get(BASE_PATH + "/songs/3/4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songName", is("Song")));
  }

  @Test
  void scanFileSystemForSongsNoPath_delegatesToService() throws Exception {
    when(songLibraryService.scanFileSystemForSongs()).thenReturn(5);

    mockMvc.perform(post(BASE_PATH + "/scanNoPath"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(5)));
  }

  @Test
  void scanFileSystemForSongsWithRequest_passesScanRequest() throws Exception {
    ScanRequest request = new ScanRequest("/music");
    when(songLibraryService.scanFileSystemForSongs(any(ScanRequest.class))).thenReturn(10);

    mockMvc.perform(post(BASE_PATH + "/scan")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(10)));

    verify(songLibraryService).scanFileSystemForSongs(any(ScanRequest.class));
  }

  @Test
  void resetSongStatistics_delegatesToService() throws Exception {
    when(songLibraryService.resetSongStatistics()).thenReturn(1);

    mockMvc.perform(post(BASE_PATH + "/resetSongStatistics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void restoreSongStatistics_passesFilename() throws Exception {
    when(songLibraryService.restoreSongStatistics("backup.oos")).thenReturn(1);

    mockMvc.perform(post(BASE_PATH + "/restoreSongStatistics")
            .contentType(MediaType.TEXT_PLAIN)
            .content("backup.oos"))
        .andExpect(status().isOk());

    verify(songLibraryService).restoreSongStatistics("backup.oos");
  }

  @Test
  void updateAlbumMetadata_passesAlbumIdAndMetadata() throws Exception {
    // hasExplicit/isEmpty are true here (rather than reusing anAlbumMetadataDto()'s all-false
    // defaults) so the response-body assertions below actually distinguish "field serialized
    // under the right key" from "field defaulted to false anyway."
    AlbumMetadataDto metadata =
        new AlbumMetadataDto("Artist", "Album", "Label", "2020", "Rock", "/cover.jpg", true, true);
    when(songLibraryService.updateAlbumMetadata(eq(3), any(AlbumMetadataDto.class)))
        .thenReturn(metadata);

    String requestBody = """
        {
          "artistName": "Artist",
          "albumName": "Album",
          "recordLabel": "Label",
          "releaseDate": "2020",
          "genre": "Rock",
          "coverArtUrl": "/cover.jpg",
          "hasExplicit": true,
          "isEmpty": true
        }""";

    mockMvc.perform(post(BASE_PATH + "/albums/3/updateAlbumMetadata")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk())
        // Locks down AlbumMetadataDto's wire format now that it's a record: both fields must
        // round-trip under their literal component names ("hasExplicit", "isEmpty"), not the
        // JavaBean-stripped names ("explicit", "empty") the old getter-based class would have
        // produced for a boolean isEmpty() accessor.
        .andExpect(jsonPath("$.hasExplicit", is(true)))
        .andExpect(jsonPath("$.isEmpty", is(true)));

    verify(songLibraryService).updateAlbumMetadata(eq(3), any(AlbumMetadataDto.class));
  }

  @Test
  void authenticateForAdminPanel_passesCredentials() throws Exception {
    AuthenticateForAdminPanelRequest request =
        new AuthenticateForAdminPanelRequest("admin", "password");
    when(songLibraryService.authenticateForAdminPanel(any(AuthenticateForAdminPanelRequest.class)))
        .thenReturn(true);

    mockMvc.perform(post(BASE_PATH + "/authenticateForAdminPanel")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(true)));
  }

  @Test
  void searchInternetForAlbumMetadata_passesParams() throws Exception {
    when(songLibraryService.searchInternetForAlbumMetadata("Artist", "Album", 5))
        .thenReturn(List.of(anAlbumMetadataDto()));

    mockMvc.perform(get(BASE_PATH + "/searchInternetForAlbumMetadata")
            .param("artistName", "Artist")
            .param("albumName", "Album")
            .param("limit", "5"))
        .andExpect(status().isOk());

    verify(songLibraryService).searchInternetForAlbumMetadata("Artist", "Album", 5);
  }

  private AlbumMetadataDto anAlbumMetadataDto() {
    return new AlbumMetadataDto("Artist", "Album", "Label", "2020", "Rock", "/cover.jpg", false, false);
  }
}
