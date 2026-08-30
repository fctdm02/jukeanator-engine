package com.djt.jukeanator_engine.domain.songlibrary.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;

/**
 * Covers {@link SongLibraryServiceImpl#renameOwnLocationLibraryFileIfNameChanged}, locally
 * constructed with mocked dependencies (fast, deterministic, no Spring context needed) -- unlike
 * {@code SongLibraryServiceTest}, which exercises a Spring-managed bean end-to-end. The actual
 * file rename mechanics are covered separately by {@code
 * SongLibraryRepositoryFileSystemImplTest#renameLocationLibraryFile_*}; this only covers the
 * service-level wiring (deriving old/new names, the no-op-when-unchanged check, and the
 * master-mode guard).
 */
public class SongLibraryServiceImplTest {

  private SongLibraryServiceImpl newService(boolean isMaster, String ownLocationName,
      SongLibraryRepository songLibraryRepository, LocationService locationService)
      throws EntityDoesNotExistException {

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir("unused-for-this-test");
    appProperties.setMode(isMaster ? "master" : "standalone");

    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    if (!isMaster) {
      LocationEntity ownLocation =
          new LocationEntity(1, ownLocationName, null, null, "test-api-key-hash");
      // appProperties.getLocationId() is null unless configured (not set above), so
      // initialize() calls getOrCreateOwnLocation(null) -- standalone mode's own-first-boot path.
      when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
      // Simplest "fresh install, no .oos file yet" setup -- initialize() falls back to an empty
      // placeholder root, which is still wired up to ownLocation the same as a real load.
      when(songLibraryRepository.loadAggregateRoot(anyInt()))
          .thenThrow(new EntityDoesNotExistException("no library yet"));
    }

    return new SongLibraryServiceImpl(appProperties, songLibraryRepository, locationService,
        songScanner, Integer.valueOf(100), eventPublisher);
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_delegatesToRepository_whenNameChanged()
      throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongLibraryServiceImpl service =
        newService(false, "New Name", songLibraryRepository, locationService);

    service.renameOwnLocationLibraryFileIfNameChanged("Old Name");

    verify(songLibraryRepository).renameLocationLibraryFile("Old Name", "New Name");
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_isNoOp_whenNameUnchanged() throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongLibraryServiceImpl service =
        newService(false, "Same Name", songLibraryRepository, locationService);

    service.renameOwnLocationLibraryFileIfNameChanged("Same Name");

    verify(songLibraryRepository, never()).renameLocationLibraryFile(anyString(), anyString());
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_throws_onMaster() throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongLibraryServiceImpl service =
        newService(true, null, songLibraryRepository, locationService);

    assertThrows(SongLibraryServiceException.class,
        () -> service.renameOwnLocationLibraryFileIfNameChanged("Old Name"));
  }
}
