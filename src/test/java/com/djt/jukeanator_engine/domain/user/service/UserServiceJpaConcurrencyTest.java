package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.common.security.LocalPrincipal;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;

/**
 * Regression test for the {@code OptimisticLockException} reported against a standalone/slave
 * instance running with {@code app.repository-type=jpa} when adding a song to the queue triggers
 * {@code LOCAL}-user auto-registration.
 *
 * <p>The failure isn't actually a concurrency race (an earlier hypothesis, disproven by
 * reproducing it with a single, purely sequential call): {@code UserServiceImpl.registerWithRole}
 * assigns a brand-new {@code UserEntity} a placeholder id ({@code userRoot.getUsers().size() + 1}
 * -- needed only so the filesystem-backed repository, which has no DB/sequence of its own, has an
 * id to write), and the very first user ever registered under JPA gets id {@code 1}. {@link
 * com.djt.jukeanator_engine.domain.user.repository.UserRepositoryJpaImpl#storeAggregateRoot}
 * used to {@code merge()} every user unconditionally; merging an entity that already carries a
 * non-null id but has no matching row yet makes Hibernate assume the row exists and issue an
 * {@code UPDATE}, which matches zero rows and throws exactly this exception ("...or unsaved-value
 * mapping was incorrect"). The fix there now {@code persist()}s ids not yet present in the
 * database instead, letting the real {@code @GeneratedValue(SEQUENCE)} mint the actual id.
 *
 * <p>This test still exercises two concurrent adds (rather than just one sequential add) to also
 * cover the unsynchronized-shared-{@code userRoot} race that {@link UserServiceImpl} independently
 * guards against now ({@code synchronized} on every method that touches {@code userRoot}) --
 * both issues had to be fixed for this to pass reliably.
 *
 * <p>Runs against the real, JPA-backed {@link UserService} bean (unlike {@code UserServiceTest},
 * which exercises the filesystem-backed bean by default) -- {@code app.repository-type=jpa} is
 * required to reproduce the original bug. Requires a live local MySQL instance (see {@code
 * src/test/resources/application-test.yml}), the same setup {@code
 * SongQueueRepositoryJpaImplTest}/{@code SongLibraryRepositoryJpaImplTest} already depend on.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class UserServiceJpaConcurrencyTest extends AbstractServiceIntegrationTest {

  @Autowired
  private UserService userService;

  @Test
  void concurrentHandleSongAddedToQueueEvent_forLocalUser_doesNotThrowAndPersistsBothPlays()
      throws Exception {

    // Unique per run so repeated executions against a persistent (non-rolled-back) database don't
    // collide on a song identifier left behind by a prior run.
    int albumIdA = uniquePositiveInt();
    int songIdA = uniquePositiveInt();
    int albumIdB = uniquePositiveInt();
    int songIdB = uniquePositiveInt();

    SongQueueEntryDto entryA =
        new SongQueueEntryDto(LocalPrincipal.LOCAL_USERNAME, buildSongDto(albumIdA, songIdA), 1, null);
    SongQueueEntryDto entryB =
        new SongQueueEntryDto(LocalPrincipal.LOCAL_USERNAME, buildSongDto(albumIdB, songIdB), 1, null);

    // ServiceSecurityAspect requires an authenticated principal in the (thread-local, in REST/test
    // mode) SecurityContextHolder -- setUpSecurityContext() only installed one on this test thread,
    // so it must be propagated explicitly into each worker thread below.
    SecurityContext callerContext = SecurityContextHolder.getContext();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch bothReady = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);

    Callable<Void> addSongA = () -> {
      SecurityContextHolder.setContext(callerContext);
      bothReady.countDown();
      go.await();
      userService.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entryA));
      return null;
    };
    Callable<Void> addSongB = () -> {
      SecurityContextHolder.setContext(callerContext);
      bothReady.countDown();
      go.await();
      userService.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entryB));
      return null;
    };

    try {
      Future<Void> futureA = executor.submit(addSongA);
      Future<Void> futureB = executor.submit(addSongB);

      // Line both worker threads up at the starting gate before releasing them together, matching
      // how two near-simultaneous "Add to Queue" clicks race through this same path in production.
      bothReady.await();
      go.countDown();

      assertDoesNotThrow(() -> futureA.get(30, TimeUnit.SECONDS),
          "concurrent handleSongAddedToQueueEvent should not throw OptimisticLockException");
      assertDoesNotThrow(() -> futureB.get(30, TimeUnit.SECONDS),
          "concurrent handleSongAddedToQueueEvent should not throw OptimisticLockException");
    } finally {
      executor.shutdownNow();
    }

    UserProfileDto profile = userService.getProfile(LocalPrincipal.LOCAL_USERNAME);
    assertTrue(profile.songPlayHistory().contains(new SongIdentifier(null, albumIdA, songIdA)),
        "song A should have been recorded in play history (lost update if missing)");
    assertTrue(profile.songPlayHistory().contains(new SongIdentifier(null, albumIdB, songIdB)),
        "song B should have been recorded in play history (lost update if missing)");
  }

  private static SongDto buildSongDto(int albumId, int songId) {
    return new SongDto(null, null, null, "Artist", albumId, "Album", null, songId, "Song", 1, 0);
  }

  private static int uniquePositiveInt() {
    return (int) (System.nanoTime() & 0x7FFFFFFF);
  }
}
