package com.djt.jukeanator_engine.domain.songlibrary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * Verifies that paging all the way through a genre's artists/albums/songs -- exactly as
 * GenreDetailPanel's PagedCategoryBuffer does on the Genres screen -- reconstructs the complete,
 * unduplicated set that genre actually contains: nothing missing, nothing repeated across
 * server-page boundaries. The configured page size is overridden to 2 so the small test fixture
 * (2-3 items per category) still forces multiple pages per category.
 *
 * @author tmyers
 */
@SpringBootTest(properties = "song-library.search-result-page-size=2")
@ActiveProfiles("test")
class GenrePaginationTest extends AbstractServiceIntegrationTest {

  @Autowired
  private SongLibraryService songLibraryService;

  @Test
  void pagingThroughAGenreReturnsExactlyEverythingInIt() throws IOException {

    // STEP 1: ARRANGE -- scan the same fixture SongLibraryServiceTest uses; its "80s" genre has
    // multiple artists/albums/songs, more than the page size of 2 configured above.
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    songLibraryService.scanFileSystemForSongs(scanRequest);

    Integer locationId = songLibraryService.getOwnLocationId();
    String genreName = "80s";
    int pageSize = 2;

    // Ground truth: filter the raw, unpaginated library tree by genre directly -- the same
    // source (root.getArtists()/getAlbums()/getSongs()) and the same case-insensitive genre-name
    // filter that SongLibraryServiceImpl#getMusic uses internally, just without any paging
    // applied, so this is independent of the pagination logic under test.
    RootFolderEntity root = songLibraryService.getSongLibraryRoot(locationId);
    List<ArtistFolderEntity> expectedArtistEntities = root.getArtists().stream()
        .filter(a -> genreName.equalsIgnoreCase(a.getParentGenre().getName())).toList();
    List<AlbumFolderEntity> expectedAlbumEntities = root.getAlbums().stream()
        .filter(a -> genreName.equalsIgnoreCase(a.getParentGenre().getName())).toList();
    List<SongFileEntity> expectedSongEntities = root.getSongs().stream()
        .filter(s -> genreName.equalsIgnoreCase(s.getParentGenre().getName())).toList();

    Set<ArtistDto> expectedArtists =
        new HashSet<>(SongLibraryMapper.toArtistDtoList(expectedArtistEntities));
    Set<AlbumDto> expectedAlbums =
        new HashSet<>(SongLibraryMapper.toAlbumDtoList(expectedAlbumEntities));
    Set<SongDto> expectedSongs =
        new HashSet<>(SongLibraryMapper.toSongDtoList(expectedSongEntities));

    // Sanity-check the fixture actually forces multi-page paging for at least one category --
    // otherwise this test wouldn't be exercising anything a single unpaginated fetch didn't
    // already cover.
    assertTrue(
        expectedArtists.size() > pageSize || expectedAlbums.size() > pageSize
            || expectedSongs.size() > pageSize,
        "Fixture genre should have more than one page's worth of at least one category");

    // STEP 2: ACT -- page through each category independently, exactly like
    // GenreDetailPanel/PagedCategoryBuffer does: keep requesting the next page for a category
    // until it comes back shorter than the page size (or empty), accumulating along the way.
    List<ArtistDto> pagedArtists = new ArrayList<>();
    List<AlbumDto> pagedAlbums = new ArrayList<>();
    List<SongDto> pagedSongs = new ArrayList<>();

    int artistPage = 0, albumPage = 0, songPage = 0;
    boolean artistsDone = false, albumsDone = false, songsDone = false;

    // Safety cap so a pagination bug (e.g. a page index that never advances) fails the test
    // instead of looping forever.
    for (int iterations = 0; iterations < 1000
        && !(artistsDone && albumsDone && songsDone); iterations++) {

      SearchResultDto result = songLibraryService.getGenreMusicByPopularity(locationId, genreName,
          artistPage, albumPage, songPage);

      if (!artistsDone) {
        pagedArtists.addAll(result.artists());
        if (result.artists().size() < pageSize) {
          artistsDone = true;
        } else {
          artistPage++;
        }
      }
      if (!albumsDone) {
        pagedAlbums.addAll(result.albums());
        if (result.albums().size() < pageSize) {
          albumsDone = true;
        } else {
          albumPage++;
        }
      }
      if (!songsDone) {
        pagedSongs.addAll(result.songs());
        if (result.songs().size() < pageSize) {
          songsDone = true;
        } else {
          songPage++;
        }
      }
    }

    assertTrue(artistsDone && albumsDone && songsDone,
        "Paging should have terminated for all three categories within the iteration cap");

    // STEP 3: ASSERT -- the concatenation across all pages, deduplicated, exactly equals the
    // true unpaginated set (same size, same members) -- i.e. paging through the Genres screen
    // shows everything in the genre, and nothing twice.
    Set<ArtistDto> pagedArtistSet = new HashSet<>(pagedArtists);
    Set<AlbumDto> pagedAlbumSet = new HashSet<>(pagedAlbums);
    Set<SongDto> pagedSongSet = new HashSet<>(pagedSongs);

    assertEquals(pagedArtists.size(), pagedArtistSet.size(),
        "No artist should appear on more than one page");
    assertEquals(pagedAlbums.size(), pagedAlbumSet.size(),
        "No album should appear on more than one page");
    assertEquals(pagedSongs.size(), pagedSongSet.size(),
        "No song should appear on more than one page");

    assertEquals(expectedArtists, pagedArtistSet,
        "Paged artists should exactly match the genre's true artist set");
    assertEquals(expectedAlbums, pagedAlbumSet,
        "Paged albums should exactly match the genre's true album set");
    assertEquals(expectedSongs, pagedSongSet,
        "Paged songs should exactly match the genre's true song set");
  }
}
