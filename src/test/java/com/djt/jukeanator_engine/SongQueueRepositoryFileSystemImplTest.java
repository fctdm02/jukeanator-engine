package com.djt.jukeanator_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicService;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.config.SongQueueProperties;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceImpl;

/**
 * Reproduces the reported reset-queue-at-startup=false bug against
 * {@link SongQueueRepositoryFileSystemImpl}: store a queue with a real (non-BG_MUSIC) entry, then
 * simulate an app restart by constructing a brand-new repository instance against the same
 * dataDir, exactly like {@code SongQueueServiceImpl}'s constructor does on every boot.
 */
class SongQueueRepositoryFileSystemImplTest {

  private static final Integer LOCATION_ID = 42;
  private static final Integer ALBUM_ID = 501;
  private static final Integer SONG_ID = 9001;

  @Test
  void storeAggregateRoot_thenLoadAggregateRoot_fromAFreshRepositoryInstance_roundTrips(
      @TempDir Path dataDir) throws Exception {

    RootFolderEntity libraryRoot = buildOneAlbumRoot();
    SongLibraryService songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songLibraryService.getSongLibraryRoot(LOCATION_ID)).thenReturn(libraryRoot);

    String basePath = dataDir.toAbsolutePath().toString();

    SongQueueRepositoryFileSystemImpl writer =
        new SongQueueRepositoryFileSystemImpl(basePath, songLibraryService);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    SongFileEntity song = libraryRoot.getSongById(ALBUM_ID, SONG_ID);
    root.addSongToQueue("real-user@example.com", song, 1);

    writer.storeAggregateRoot(root);

    // Simulate a restart: a brand new repository instance, same basePath -- exactly what
    // AppConfig.songQueueRepositoryFileSystemImpl() constructs fresh on every boot.
    SongQueueRepositoryFileSystemImpl reader =
        new SongQueueRepositoryFileSystemImpl(basePath, songLibraryService);

    SongQueueRootEntity reloaded =
        reader.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);

    assertEquals(1, reloaded.getSongs().size());
    assertEquals("real-user@example.com", reloaded.getSongs().get(0).getUsername());
  }

  @Test
  void serviceRestart_withResetQueueAtStartupFalse_reloadsThePersistedQueueInstead_ofOnlyBackgroundMusic(
      @TempDir Path dataDir) throws Exception {

    RootFolderEntity libraryRoot = buildOneAlbumRoot();
    SongLibraryService songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songLibraryService.getSongLibraryRoot(LOCATION_ID)).thenReturn(libraryRoot);

    BackgroundMusicService backgroundMusicService = mock(BackgroundMusicService.class);
    when(backgroundMusicService.isEnabled()).thenReturn(false);

    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    SongQueueProperties properties = new SongQueueProperties();
    properties.setResetQueueAtStartup(false);

    String basePath = dataDir.toAbsolutePath().toString();

    // "Process 1" boots, queues a real (non-BG) song, then "shuts down".
    SongQueueRepository repository1 =
        new SongQueueRepositoryFileSystemImpl(basePath, songLibraryService);
    SongQueueServiceImpl service1 = new SongQueueServiceImpl(properties, songLibraryService,
        backgroundMusicService, repository1, eventPublisher, Optional.empty());

    service1.addSongToQueue(LOCATION_ID,
        new com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest(
            "real-user@example.com", ALBUM_ID, SONG_ID, 1));

    // "Process 2" boots fresh -- brand-new repository AND service instances, same dataDir,
    // exactly like AppConfig's @Bean factory methods construct on every application restart.
    SongQueueRepository repository2 =
        new SongQueueRepositoryFileSystemImpl(basePath, songLibraryService);
    SongQueueServiceImpl service2 = new SongQueueServiceImpl(properties, songLibraryService,
        backgroundMusicService, repository2, eventPublisher, Optional.empty());

    java.util.List<SongQueueEntryDto> reloadedQueue = service2.getQueuedSongs(LOCATION_ID);

    assertEquals(1, reloadedQueue.size());
    assertEquals("real-user@example.com", reloadedQueue.get(0).username());
  }

  private RootFolderEntity buildOneAlbumRoot() throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/song-queue-fs-test");

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(1);
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    artist.setId(2);
    genre.addChildFolder(artist);

    AlbumFolderEntity album = new AlbumFolderEntity(artist, "Album One");
    album.setId(ALBUM_ID);
    album.createCoverArtEntity();
    artist.addChildFolder(album);

    SongFileEntity song = new SongFileEntity(album, "01 - Song A.mp3");
    song.setId(SONG_ID);
    song.setArtistName("Artist One");
    song.setSongName("Song A");
    song.setTrackNumber(1);
    song.setNumPlays(0);
    album.addChildSong(song);

    root.initialize();
    return root;
  }
}
