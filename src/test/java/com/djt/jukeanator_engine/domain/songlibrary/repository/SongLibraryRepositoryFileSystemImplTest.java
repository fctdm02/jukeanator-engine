package com.djt.jukeanator_engine.domain.songlibrary.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/** Unit tests for {@link SongLibraryRepositoryFileSystemImpl}. */
public class SongLibraryRepositoryFileSystemImplTest {

  // ── sanitizeLocationNameForFilename ─────────────────────────────────────

  @Test
  void sanitize_replacesSpacesWithUnderscores() {
    assertEquals("Rock_On_Third",
        SongLibraryRepositoryFileSystemImpl.sanitizeLocationNameForFilename("Rock On Third"));
  }

  @Test
  void sanitize_replacesUnsafeCharactersWithUnderscores() {
    assertEquals("Rox_on_3rd_",
        SongLibraryRepositoryFileSystemImpl.sanitizeLocationNameForFilename("Rox/on:3rd?"));
    assertEquals("a_b_c_d_e_f_g_h",
        SongLibraryRepositoryFileSystemImpl.sanitizeLocationNameForFilename(
            "a/b\\c:d*e?f\"g<h"));
  }

  @Test
  void sanitize_fallsBackToDefault_whenNullOrBlank() {
    assertEquals("SongLibrary", SongLibraryRepositoryFileSystemImpl.sanitizeLocationNameForFilename(null));
    assertEquals("SongLibrary", SongLibraryRepositoryFileSystemImpl.sanitizeLocationNameForFilename("   "));
  }

  // ── loadAggregateRoot / storeAggregateRoot ──────────────────────────────

  @Test
  void loadAggregateRoot_loadsDirectly_whenExpectedFileExists(@TempDir Path basePath,
      @TempDir Path rootPath) throws Exception {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());
    repository.storeAggregateRoot(newRoot(rootPath, "Rock On Third"));

    RootFolderEntity loaded = repository.loadAggregateRoot("Rock On Third");

    // parentLocation is transient -- it never survives (de)serialization by design (see
    // RootFolderEntity's field javadoc); SongLibraryServiceImpl re-wires it uniformly after any
    // repository load, so a raw repository-level load correctly leaves it null here.
    assertEquals(rootPath.toString(), loaded.getRootPath());
    assertEquals(basePath.resolve("Rock_On_Third.oos").toString(), repository.getResolvedFilePath());
    assertTrue(Files.exists(basePath.resolve("Rock_On_Third.oos")));
  }

  @Test
  void loadAggregateRoot_renamesOtherOosFile_whenExpectedFileMissing(@TempDir Path basePath,
      @TempDir Path rootPath) throws Exception {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());
    repository.storeAggregateRoot(newRoot(rootPath, "Rock On Third"));

    Path oldPath = basePath.resolve("Rock_On_Third.oos");
    Path renamedAwayPath = basePath.resolve("SongLibrary.oos");
    Files.move(oldPath, renamedAwayPath);

    RootFolderEntity loaded = repository.loadAggregateRoot("Rox on 3rd");

    assertEquals(rootPath.toString(), loaded.getRootPath(),
        "Deserialized content should be preserved, not just an empty new library");
    assertFalse(Files.exists(renamedAwayPath), "Old-named file should no longer exist");
    assertTrue(Files.exists(basePath.resolve("Rox_on_3rd.oos")), "New-named file should exist");
    assertEquals(basePath.resolve("Rox_on_3rd.oos").toString(), repository.getResolvedFilePath());
  }

  @Test
  void loadAggregateRoot_throws_whenNoOosFilesExist(@TempDir Path basePath) {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());

    assertThrows(EntityDoesNotExistException.class,
        () -> repository.loadAggregateRoot("Rock On Third"));
  }

  @Test
  void loadAggregateRoot_choosesMostRecentlyModified_whenMultipleOtherOosFilesExist(
      @TempDir Path basePath) throws Exception {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());

    Path older = basePath.resolve("Older.oos");
    Path newer = basePath.resolve("Newer.oos");
    Files.writeString(older, "older");
    Files.writeString(newer, "newer");
    Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
    Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));

    // Both are unreadable as real song libraries -- the newer one should be picked and attempted
    // (and fail on deserialization), proving selection happened before any load error surfaces.
    EntityDoesNotExistException ex = assertThrows(EntityDoesNotExistException.class,
        () -> repository.loadAggregateRoot("Rock On Third"));

    assertTrue(ex.getMessage().contains("Newer.oos"),
        "Should have attempted the most recently modified candidate: " + ex.getMessage());
    assertTrue(Files.exists(older), "Non-chosen candidates should be left untouched");
    assertTrue(Files.exists(newer));
  }

  @Test
  void storeAggregateRoot_writesUnderCurrentLocationName(@TempDir Path basePath,
      @TempDir Path rootPath) throws Exception {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());

    repository.storeAggregateRoot(newRoot(rootPath, "Rox on 3rd?"));

    assertTrue(Files.exists(basePath.resolve("Rox_on_3rd_.oos")));
    assertEquals(basePath.resolve("Rox_on_3rd_.oos").toString(), repository.getResolvedFilePath());
  }

  @Test
  void getResolvedFilePath_isNull_beforeAnyLoadOrStore(@TempDir Path basePath) {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());

    assertNull(repository.getResolvedFilePath());
  }

  private RootFolderEntity newRoot(Path rootPath, String locationName) {

    RootFolderEntity root = new RootFolderEntity(rootPath.toString());
    root.setParentLocation(
        new LocationEntity(1, locationName, null, null, "test-api-key-hash"));
    root.initialize();
    return root;
  }

  // ── updateNumPlaysForSong ────────────────────────────────────────────────

  /**
   * Unlike {@code SongLibraryRepositoryJpaImpl} (see its dedicated targeted-UPDATE tests), this
   * filesystem impl has no per-row store to target: the whole {@code .oos} file is always
   * rewritten wholesale, and locationId/albumId/songId are only used by the caller (see {@code
   * SongLibraryServiceImpl}) to mutate the in-memory {@link SongFileEntity} before calling this
   * method -- they're never consulted here. So besides the numPlays argument passing straight
   * through as the return value, the only other observable effect is that a {@code
   * storeAggregateRoot} gets scheduled (mirroring {@code storeSongLibraryAsync}) rather than
   * thrown from.
   */
  @Test
  void updateNumPlaysForSong_returnsNumPlaysUnchanged_andSchedulesAPersist(@TempDir Path basePath,
      @TempDir Path rootPath) throws Exception {

    SongLibraryRepositoryFileSystemImpl repository =
        new SongLibraryRepositoryFileSystemImpl(basePath.toString());
    RootFolderEntity root = newRoot(rootPath, "Rock On Third");

    Integer result = repository.updateNumPlaysForSong(root, 1, 2, 3, 99);

    assertEquals(99, result);

    // The persist above runs on a background executor -- wait for it to actually finish writing
    // (and release its file handle) before the test method returns, otherwise @TempDir's own
    // cleanup can race that write and fail to delete the .oos file out from under it.
    Path oosFile = basePath.resolve("Rock_On_Third.oos");
    awaitFileHandleReleased(oosFile);
    assertTrue(Files.exists(oosFile), "Async persist should have written the .oos file");
  }

  /**
   * Polls until {@code path} can be opened for writing, proving whatever background writer
   * created it has closed its own handle. Needed because {@code updateNumPlaysForSong}/{@code
   * storeSongLibraryAsync} persist on a fire-and-forget background executor with no
   * completion signal to await directly.
   */
  private void awaitFileHandleReleased(Path path) throws IOException, InterruptedException {

    long deadline = System.currentTimeMillis() + 5_000;
    IOException lastFailure = null;

    while (System.currentTimeMillis() < deadline) {

      if (Files.exists(path)) {
        try (java.nio.channels.FileChannel channel =
            java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.WRITE)) {
          return;
        } catch (IOException e) {
          lastFailure = e;
        }
      }
      Thread.sleep(50);
    }

    throw new AssertionError("Timed out waiting for " + path + " to be released", lastFailure);
  }
}
