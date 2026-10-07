package com.djt.jukeanator_engine.ui;

import static com.djt.jukeanator_engine.ui.SwingTestSupport.await;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.awaitEquals;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.click;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.clickComponent;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.containsLabelText;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.descendants;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.findAll;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.findAllShowing;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.findShowing;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.findShowingWithText;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.findWindow;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.flushEdt;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.isCardShowing;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.onEdt;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.pressKeyBinding;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.requireShowing;
import static com.djt.jukeanator_engine.ui.SwingTestSupport.runOnEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.ui.components.GenrePanel;
import com.djt.jukeanator_engine.ui.components.HomePanel;
import com.djt.jukeanator_engine.ui.components.HotHerePanel;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;
import com.djt.jukeanator_engine.ui.components.SearchPanel;
import com.djt.jukeanator_engine.ui.components.SongTrackCellRenderer;
import com.djt.jukeanator_engine.ui.components.UiComponentNames;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;

/**
 * End-to-end functional test of the JFC/Swing kiosk UI against the whole running application —
 * live services, the real fullscreen {@link JukeANatorFrame} and the real event wiring, no mocks.
 *
 * <h3>How the UI is started</h3>
 * {@code app.ui-enabled=true} activates the production launch path
 * ({@code JukeANatorLauncherConfiguration} → {@code JukeANatorUserInterfaceApplication.launch()}),
 * which builds the frame, connects {@code JukeANatorEventListener} to it and installs
 * {@code LocalAuthenticatedEventQueue}. Under {@code @SpringBootTest}
 * {@code JukeANatorBackendApplication.main()} never runs, so this class turns headless mode off
 * itself, in a static initializer that runs before AWT is first touched. If another test in the
 * same JVM has already started AWT headless, the whole class is skipped rather than failing.
 *
 * <h3>How the UI is driven</h3>
 * Programmatically, through {@link SwingTestSupport}: components are found by the names in
 * {@link UiComponentNames}, buttons are pressed with {@code doClick()}, clickable panels have
 * their {@code mouseClicked} listeners fired, and hardware keys (bill acceptor, Esc) invoke the
 * real key bindings. The mouse and keyboard are never moved, so the laptop stays usable apart from
 * the fullscreen window. The steps run in order against one application instance; once a step
 * fails, the remaining steps are skipped rather than failing for the same reason.
 *
 * <h3>Test data</h3>
 * {@link AbstractServiceIntegrationTest} deletes the persisted song library before the context
 * starts, so every run exercises the first-time-use flow: the application asks for a music folder
 * and the test answers with the fixture library. The admin password file and the recent-plays file
 * in the test data dir are set up here (see {@link #prepareTestDataDir()}). One fixture song is
 * really played through the configured player for a few seconds.
 *
 * <h3>Deliberately not exercised</h3>
 * Exit and first-time-use Cancel ({@code System.exit}), Minimize, Load/Save Playlist and the Edit
 * Album "Browse..." button ({@code JFileChooser}), Edit Album internet Search / Google / Download
 * Cover Art (network and external browser), the credit-card reader key, screen saver, hibernation
 * and the 10-minute idle reset.
 */
@SpringBootTest(properties = {"app.ui-enabled=true", "user-interface.enable-screen-saver=false",
    "user-interface.enable-hibernation=false", "user-interface.always-on-top=false",
    "user-interface.enable-type-ahead-search=true",
    "user-interface.enable-credit-card-processing=false"})
@ActiveProfiles("test") // loads application-test.yml
@DirtiesContext // closes the context afterwards so the player is released
@Tag("gui")
@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(JukeANatorUserInterfaceApplicationFunctionalTest.SkipAfterFailure.class)
class JukeANatorUserInterfaceApplicationFunctionalTest extends AbstractServiceIntegrationTest {

  static {
    // Must run before anything touches AWT; see the class javadoc.
    System.setProperty("java.awt.headless", "false");
  }

  private static final String SCAN_PATH =
      "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
          + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder";
  private static final Path TEST_DATA_DIR =
      Path.of("src/test/resources/com/djt/jukeanator_engine/test-data-dir");
  private static final Path ADMIN_PASSWORD_FILE = TEST_DATA_DIR.resolve("admin.sha");
  private static final Path RECENT_SONG_PLAYS_FILE =
      TEST_DATA_DIR.resolve("JukeANator_RecentSongPlays.json");

  private static final String SCAN_DIALOG_TITLE = "Select Music Folder to Scan";
  private static final String ADMIN_USERNAME = "ADMIN";
  // Letters only: the on-screen keyboard's default ABC layout types upper-case letters
  private static final String ADMIN_PASSWORD = "JUKEBOX";
  private static final long SCAN_TIMEOUT_MS = 120_000;

  private static final String TAB_HOME = "HOME";
  private static final String TAB_SEARCH = "SEARCH";
  private static final String TAB_HOT_HERE = "HOT HERE";
  private static final String TAB_GENRES = "GENRES";
  private static final String TAB_QUEUE = "QUEUE";
  private static final String TAB_ADMIN = "ADMIN";

  /** Set by {@link SkipAfterFailure} once a step fails. */
  private static volatile boolean stepFailed;

  /** Admin password file content present before this test, restored afterwards (or null). */
  private static String originalAdminPassword;

  // Songs and albums chosen from the scanned library in step 1 and used by later steps
  private static SongDto homeSong;
  private static AlbumDto homeAlbum;
  private static SongDto hotHereSong;
  private static AlbumDto queueAlbum;
  /** An album in the Hot Here song's genre, used to open the Artist View from the Genres tab. */
  private static AlbumDto genreAlbum;
  private static Set<String> librarySongNames = Set.of();

  @Autowired
  private SongLibraryService songLibraryService;

  @Autowired
  private SongQueueService songQueueService;

  @Autowired
  private FinancialLedgerService financialLedgerService;

  @Autowired
  private UserService userService;

  @Autowired
  private JukeANatorUserInterfaceProperties userInterfaceProperties;

  /** Marks the run as failed so the remaining ordered steps are skipped. */
  static class SkipAfterFailure implements TestWatcher {
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
      stepFailed = true;
    }
  }

  // Named so it does not hide AbstractServiceIntegrationTest.beforeAll(), which must still run
  @BeforeAll
  static void prepareTestDataDir() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(),
        "AWT is already headless in this JVM (another test started it first); run this class on "
            + "its own, from the IDE or with ./mvnw test -Pgui-tests");

    Files.createDirectories(TEST_DATA_DIR);
    originalAdminPassword =
        Files.exists(ADMIN_PASSWORD_FILE) ? Files.readString(ADMIN_PASSWORD_FILE) : null;
    Files.writeString(ADMIN_PASSWORD_FILE, sha256Hex(ADMIN_PASSWORD), StandardCharsets.UTF_8);

    // Songs played by an earlier run within minimum-minutes-between-song-plays would otherwise be
    // refused by the queue rules
    Files.deleteIfExists(RECENT_SONG_PLAYS_FILE);
  }

  @AfterAll
  static void restoreTestDataDir() throws Exception {
    Optional<JukeANatorFrame> frame = findFrame();
    if (frame.isPresent()) {
      runOnEdt(frame.get()::dispose);
    }
    if (originalAdminPassword != null) {
      Files.writeString(ADMIN_PASSWORD_FILE, originalAdminPassword, StandardCharsets.UTF_8);
    } else {
      Files.deleteIfExists(ADMIN_PASSWORD_FILE);
    }
  }

  @BeforeEach
  void skipAfterEarlierFailure() {
    assumeFalse(stepFailed, "Skipped because an earlier step failed");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // STEPS
  // ═══════════════════════════════════════════════════════════════════════════

  @Test
  @Order(1)
  void step01_startsAndRunsTheFirstTimeLibraryScan() throws Exception {

    // The UI is launched on the EDT once the context is ready
    await(() -> findFrame().isPresent(), "the JukeANator frame to be shown");
    JukeANatorFrame frame = frame();

    // With no persisted library, launch() opens the first-time-use folder chooser (modal)
    await(() -> findWindow(this::isScanDialog).isPresent(), "the first-time-use scan dialog");
    JDialog dialog = (JDialog) findWindow(this::isScanDialog).orElseThrow();
    JFileChooser chooser = onEdt(() -> descendants(dialog.getContentPane(), JFileChooser.class))
        .getFirst();
    runOnEdt(() -> {
      chooser.setSelectedFile(Path.of(SCAN_PATH).toAbsolutePath().toFile());
      chooser.approveSelection();
    });

    // The scan runs in the Admin panel's progress overlay and ends with a Scan Complete notice
    awaitAdminOverlay("Scan Complete", SCAN_TIMEOUT_MS);
    click(adminPanel(), UiComponentNames.ADMIN_OVERLAY_PRIMARY_BUTTON);

    List<AlbumDto> albums = songLibraryService.getAlbums(locationId());
    assertFalse(albums.isEmpty(), "the fixture library should have been scanned");
    chooseFixtureSongs(albums);

    // The scan's ScanFileSystemForSongsEvent reloads every tab through JukeANatorEventListener
    await(() -> onEdt(
        () -> !findAll(tab(TAB_HOME), UiComponentNames.ALBUM_TILE, JPanel.class).isEmpty()),
        "Home to show the scanned albums");

    // Keep queued songs in the queue until step 12 deliberately plays one
    click(adminPanel(), UiComponentNames.ADMIN_LOCK_QUEUE_BUTTON);
    assertTrue(songQueueService.isLocked(), "Lock Queue should lock the song queue");
    assertFalse(onEdt(() -> button(adminPanel(), UiComponentNames.ADMIN_LOCK_QUEUE_BUTTON)
        .isEnabled()), "Lock Queue should be disabled while the queue is locked");

    // The first-time scan opened Admin without a login; Esc returns to the default tab
    pressEscape();
    assertEquals(TAB_HOT_HERE, selectedTabTitle(), "HOT HERE should be the default tab");
    assertEquals(0, credits(), "the test profile starts with no credits");
    assertTrue(onEdt(() -> creditsLabel().getText().startsWith("CREDITS: ")),
        "credits label: " + onEdt(() -> creditsLabel().getText()));
    assertTrue(onEdt(frame::isShowing));
  }

  @Test
  @Order(2)
  void step02_billAcceptorKeyAddsCreditsAndRecordsCash() throws Exception {

    BigDecimal cashBefore = openPeriodCashTotal();

    insertDollar();
    insertDollar();

    assertEquals(2 * userInterfaceProperties.getCreditsPerDollar(), credits());
    awaitEquals(cashBefore.add(BigDecimal.valueOf(2)), this::openPeriodCashTotal,
        "the ledger's cash total for the open period");
  }

  @Test
  @Order(3)
  void step03_homeAlbumDetailPlaysASong() throws Exception {

    selectTab(TAB_HOME);
    Container home = tab(TAB_HOME);
    int tileCount = showingCount(home, UiComponentNames.ALBUM_TILE);
    assertTrue(tileCount > 0, "Home should show album tiles");

    // Order By buttons re-sort without re-querying; Legacy switches to the legacy grid and back
    click(home, UiComponentNames.HOME_SORT_ALBUM_BUTTON);
    assertEquals(tileCount, showingCount(home, UiComponentNames.ALBUM_TILE));
    click(home, UiComponentNames.HOME_SORT_ARTIST_BUTTON);
    assertEquals(tileCount, showingCount(home, UiComponentNames.ALBUM_TILE));

    click(home, UiComponentNames.HOME_LEGACY_TOGGLE);
    assertTrue(showingCount(home, UiComponentNames.LEGACY_ALBUM_CARD) > 0,
        "Legacy mode should show legacy album cards");
    click(home, UiComponentNames.HOME_LEGACY_TOGGLE);
    assertEquals(tileCount, showingCount(home, UiComponentNames.ALBUM_TILE));

    // Album tile → album detail → track row → Add Song card → Play Song
    clickComponent(requireShowingWithText(home, UiComponentNames.ALBUM_TILE,
        homeAlbum.albumName()));
    assertTrue(isCardShowing(home, HomePanel.CARD_DETAIL), "the album detail card should show");

    int creditsBefore = credits();
    clickComponent(requireShowingWithText(home, UiComponentNames.ALBUM_VIEW_TRACK_ROW,
        homeSong.songName()));
    playFromAddSongCard(UiComponentNames.ADD_SONG_PLAY_BUTTON);

    assertEquals(creditsBefore - 1, credits(), "Play Song costs one credit");
    awaitStoredQueueContains(homeSong.songName());
    awaitQueueTabShows(homeSong.songName());

    click(home, UiComponentNames.ALBUM_DETAIL_BACK_BUTTON);
    assertTrue(isCardShowing(home, HomePanel.CARD_GRID), "Back should return to the album grid");
  }

  @Test
  @Order(4)
  void step04_searchWithTheOnScreenKeyboard() throws Exception {

    selectTab(TAB_SEARCH);
    Container search = tab(TAB_SEARCH);
    String query = searchWord(homeSong.artistName());

    // Type the query, plus a stray letter removed again with backspace
    typeOnKeyboard(search, query + "X");
    click(search, UiComponentNames.keyboardKey("⌫"));
    assertEquals(query, onEdt(() -> requireShowing(search, UiComponentNames.SEARCH_TEXT_LABEL,
        JLabel.class).getText()));
    assertTrue(isCardShowing(search, SearchPanel.CARD_RESULTS), "type-ahead shows results");

    JLabel artistsHeader = onEdt(() -> requireShowing(column(search, "ARTISTS"),
        UiComponentNames.RESULTS_COLUMN_HEADER_LABEL, JLabel.class));
    assertTrue(onEdt(artistsHeader::getText).matches("Artists \\(\\d+\\)"),
        "artists header: " + onEdt(artistsHeader::getText));

    // Artist result → Artist View → album → album details, and an Album result directly
    exerciseArtistView(search, SearchPanel.CARD_RESULTS, SearchPanel.CARD_ARTIST,
        SearchPanel.CARD_DETAIL, homeAlbum);
    exerciseAlbumResult(search, SearchPanel.CARD_RESULTS, SearchPanel.CARD_DETAIL, homeAlbum);

    // A song row opens the Add Song card; Cancel closes it without queueing anything
    List<String> queueBefore = storedSongNames();
    JPanel songRow = onEdt(() -> findAllShowing(column(search, "SONGS"),
        UiComponentNames.RESULTS_ROW, JPanel.class).stream()
        .filter(r -> !containsLabelText(r, homeSong.songName())).findFirst()
        .orElseThrow(() -> new AssertionError("no unqueued song in the search results")));
    clickComponent(songRow);
    assertTrue(isAddSongCardShowing(), "a song row should open the Add Song card");
    click(frame(), UiComponentNames.ADD_SONG_CANCEL_BUTTON);
    assertFalse(isAddSongCardShowing(), "Cancel should close the Add Song card");
    assertEquals(queueBefore, storedSongNames(), "Cancel must not queue anything");

    click(search, UiComponentNames.keyboardKey("CLEAR"));
    assertTrue(isCardShowing(search, SearchPanel.CARD_ENTRY), "CLEAR returns to the entry card");
  }

  @Test
  @Order(5)
  void step05_hotHereListsPlayedSongsAndShowsTheQueueConstraint() throws Exception {

    selectTab(TAB_HOT_HERE);
    Container hotHere = tab(TAB_HOT_HERE);

    click(hotHere, UiComponentNames.HOT_HERE_SORT_ALBUM_BUTTON);
    click(hotHere, UiComponentNames.HOT_HERE_SORT_POPULARITY_BUTTON);

    // Hot Here lists only songs with plays, and queueing a song counts as a play: the song queued
    // in step 3 is listed, while a song never queued is not
    await(() -> onEdt(() -> findShowingWithText(column(hotHere, "SONGS"),
        UiComponentNames.RESULTS_ROW, JPanel.class, homeSong.songName()).isPresent()),
        "Hot Here to list " + homeSong.songName());
    assertFalse(onEdt(() -> findShowingWithText(column(hotHere, "SONGS"),
        UiComponentNames.RESULTS_ROW, JPanel.class, hotHereSong.songName()).isPresent()),
        "Hot Here must not list a song that has never been played");

    // That song is already queued, so the card opens on the constraint view
    int creditsBefore = credits();
    clickComponent(resultRow(hotHere, "SONGS", homeSong.songName()));
    assertTrue(isAddSongCardShowing(), "the Add Song card should open");
    JLabel reason = onEdt(() -> requireShowing(frame(), UiComponentNames.ADD_SONG_CONSTRAINT_LABEL,
        JLabel.class));
    assertTrue(onEdt(reason::getText).contains("already in the queue"),
        "constraint: " + onEdt(reason::getText));
    click(frame(), UiComponentNames.ADD_SONG_OK_BUTTON);
    assertFalse(isAddSongCardShowing(), "OK should close the Add Song card");
    assertEquals(creditsBefore, credits(), "an ineligible song costs nothing");

    // The played song's artist and album are listed too: Artist View and album details from here
    exerciseArtistView(hotHere, HotHerePanel.CARD_CONTENT, HotHerePanel.CARD_ARTIST,
        HotHerePanel.CARD_DETAIL, homeAlbum);
    exerciseAlbumResult(hotHere, HotHerePanel.CARD_CONTENT, HotHerePanel.CARD_DETAIL, homeAlbum);
  }

  @Test
  @Order(6)
  void step06_genresPriorityPlay() throws Exception {

    selectTab(TAB_GENRES);
    Container genres = tab(TAB_GENRES);

    // Genre detail lists every song in the genre, played or not
    clickComponent(requireShowingWithText(genres, UiComponentNames.GENRE_TILE,
        hotHereSong.genreName()));
    assertTrue(isCardShowing(genres, GenrePanel.CARD_ALBUMS), "the genre detail card should show");
    click(genres, UiComponentNames.GENRE_DETAIL_SORT_ALBUM_BUTTON);
    click(genres, UiComponentNames.GENRE_DETAIL_SORT_POPULARITY_BUTTON);

    // Priority play costs the next free priority × priority-cost-multiplier
    int priority = songQueueService.getHighestPriority(locationId());
    int cost = priority * userInterfaceProperties.getPriorityCostMultiplier();
    insertDollarsUntil(cost);
    int creditsBefore = credits();

    clickComponent(resultRow(genres, "SONGS", hotHereSong.songName()));
    playFromAddSongCard(UiComponentNames.ADD_SONG_PRIORITY_PLAY_BUTTON);

    assertEquals(creditsBefore - cost, credits(), "Priority Play cost");
    awaitStoredQueueContains(hotHereSong.songName());
    SongQueueEntryDto entry = storedQueue().stream()
        .filter(e -> e.song().songName().equals(hotHereSong.songName())).findFirst().orElseThrow();
    assertEquals(priority, entry.priority(), "queued priority");
    assertEquals(List.of(hotHereSong.songName(), homeSong.songName()), storedSongNames(),
        "a priority play goes ahead of normal plays");
    awaitQueueTabShows(hotHereSong.songName());

    // Artist View and album details from the genre's Artists and Albums columns
    exerciseArtistView(genres, GenrePanel.CARD_ALBUMS, GenrePanel.CARD_ARTIST,
        GenrePanel.CARD_DETAIL, genreAlbum);
    exerciseAlbumResult(genres, GenrePanel.CARD_ALBUMS, GenrePanel.CARD_DETAIL, genreAlbum);

    click(genres, UiComponentNames.DETAIL_HEADER_BACK_BUTTON);
    assertTrue(isCardShowing(genres, GenrePanel.CARD_GENRES), "Back returns to the genre tiles");
  }

  @Test
  @Order(7)
  void step07_queueTabMoveAndRemove() throws Exception {

    selectTab(TAB_QUEUE);
    Container queue = tab(TAB_QUEUE);
    String top = hotHereSong.songName();
    String bottom = homeSong.songName();
    assertEquals(List.of(top, bottom), displayedQueue());

    JButton moveUp = button(queue, UiComponentNames.QUEUE_MOVE_UP_BUTTON);
    JButton moveDown = button(queue, UiComponentNames.QUEUE_MOVE_DOWN_BUTTON);
    JButton remove = button(queue, UiComponentNames.QUEUE_REMOVE_BUTTON);
    assertEquals("GREY", buttonState(moveUp), "nothing selected yet");
    assertEquals("GREY", buttonState(remove), "nothing selected yet");

    // Select the bottom (priority 1) song: cost 3, more than the credits left after step 5
    int cost = 3;
    assertTrue(credits() < cost, "this step expects to start short of credits");
    selectQueueRow(bottom);
    assertEquals("WARN", buttonState(moveUp));
    assertEquals("GREY", buttonState(moveDown), "the last row cannot move down");
    assertEquals("WARN", buttonState(remove));
    assertFalse(onEdt(remove::isEnabled));

    // Inserting money turns the warnings into live buttons
    insertDollarsUntil(cost);
    assertEquals("NORMAL", buttonState(moveUp));
    assertEquals("NORMAL", buttonState(remove));

    int creditsBefore = credits();
    click(moveUp);
    awaitEquals(List.of(bottom, top), this::storedSongNames, "stored order after Move Up");
    awaitEquals(List.of(bottom, top), this::displayedQueue, "displayed order after Move Up");
    assertEquals(creditsBefore - cost, credits());
    assertEquals("GREY", buttonState(moveUp), "the selection followed the song to the top");

    // A disabled (short of credits or locked) button does nothing
    if (credits() < cost) {
      click(moveDown);
      Thread.sleep(500);
      assertEquals(List.of(bottom, top), storedSongNames(), "a disabled button changes nothing");
    }

    insertDollarsUntil(cost);
    creditsBefore = credits();
    click(moveDown);
    awaitEquals(List.of(top, bottom), this::storedSongNames, "stored order after Move Down");
    awaitEquals(List.of(top, bottom), this::displayedQueue, "displayed order after Move Down");
    assertEquals(creditsBefore - cost, credits());

    insertDollarsUntil(cost);
    creditsBefore = credits();
    click(remove);
    awaitEquals(List.of(top), this::storedSongNames, "stored queue after Remove");
    awaitEquals(List.of(top), this::displayedQueue, "displayed queue after Remove");
    assertEquals(creditsBefore - cost, credits());
  }

  @Test
  @Order(8)
  void step08_adminLogin() throws Exception {

    JukeANatorFrame frame = frame();

    // Clicking the credits panel opens the login card
    clickComponent(onEdt(() -> requireShowing(frame, UiComponentNames.FRAME_CREDITS_PANEL,
        JPanel.class)));
    Container login = loginCard();

    // A wrong password is refused and the card stays up
    typeOnKeyboard(login, ADMIN_USERNAME); // five letters, so focus moves to PASSWORD
    typeOnKeyboard(login, "WRONG");
    click(login, UiComponentNames.LOGIN_LOGIN_BUTTON);
    assertEquals("Invalid credentials. Please try again.", onEdt(() -> requireShowing(login,
        UiComponentNames.LOGIN_ERROR_LABEL, JLabel.class).getText()));
    assertTrue(isCardShowing(frame, JukeANatorFrame.CARD_LOGIN));

    // Cancel closes the card and stops its countdown, so it cannot close a later overlay
    JLabel timeout = onEdt(() -> requireShowing(login, UiComponentNames.LOGIN_TIMEOUT_LABEL,
        JLabel.class));
    click(login, UiComponentNames.LOGIN_CANCEL_BUTTON);
    assertFalse(isCardShowing(frame, JukeANatorFrame.CARD_LOGIN), "Cancel should close the card");
    String timeoutAfterCancel = onEdt(timeout::getText);
    Thread.sleep(1_500);
    assertEquals(timeoutAfterCancel, onEdt(timeout::getText),
        "the login countdown must stop on Cancel");

    // The right credentials open the Admin panel
    String tabBeforeLogin = selectedTabTitle();
    clickComponent(onEdt(() -> requireShowing(frame, UiComponentNames.FRAME_CREDITS_PANEL,
        JPanel.class)));
    Container secondLogin = loginCard();
    typeOnKeyboard(secondLogin, ADMIN_USERNAME);
    typeOnKeyboard(secondLogin, ADMIN_PASSWORD);
    click(secondLogin, UiComponentNames.LOGIN_LOGIN_BUTTON);
    awaitEquals(TAB_ADMIN, JukeANatorUserInterfaceApplicationFunctionalTest::selectedTabTitle,
        "the Admin panel after login");
    assertFalse(isCardShowing(frame, JukeANatorFrame.CARD_LOGIN));
    assertEquals(TAB_QUEUE, tabBeforeLogin);
  }

  @Test
  @Order(9)
  void step09_adminQueueControls() throws Exception {

    Container admin = adminPanel();

    // ➕ / ➖ Credits
    int creditsBefore = credits();
    click(admin, UiComponentNames.ADMIN_INCREMENT_CREDITS_BUTTON);
    assertTrue(credits() > creditsBefore, "➕ Credits adds a dollar's worth of credits");
    creditsBefore = credits();
    click(admin, UiComponentNames.ADMIN_DECREMENT_CREDITS_BUTTON);
    assertEquals(creditsBefore - 1, credits(), "➖ Credits removes one credit");

    // Queue Album queues every song of the selected album
    selectAdminAlbum(queueAlbum);
    click(admin, UiComponentNames.ADMIN_QUEUE_ALBUM_BUTTON);
    int expectedSize = 1 + queueAlbum.songs().size();
    await(() -> storedQueue().size() == expectedSize, "the album's songs to be queued");
    await(() -> adminQueueSize() == expectedSize, "the admin queue list to show the album");
    await(() -> displayedQueue().size() == Math.min(expectedSize, 10),
        "the QUEUE tab to show the album");

    // Move Up / Move Dn / Remove on the selected queue entry
    List<String> order = storedSongNames();
    int last = order.size() - 1;
    selectAdminQueueIndex(last);
    click(admin, UiComponentNames.ADMIN_MOVE_UP_BUTTON);
    List<String> movedUp = new ArrayList<>(order);
    movedUp.set(last - 1, order.get(last));
    movedUp.set(last, order.get(last - 1));
    awaitEquals(movedUp, this::storedSongNames, "stored order after admin Move Up");

    click(admin, UiComponentNames.ADMIN_MOVE_DOWN_BUTTON);
    awaitEquals(order, this::storedSongNames, "stored order after admin Move Dn");

    selectAdminQueueIndex(last);
    click(admin, UiComponentNames.ADMIN_REMOVE_BUTTON);
    awaitEquals(order.subList(0, last), this::storedSongNames, "stored queue after admin Remove");

    // Shuffle keeps the same songs; Flush empties the queue everywhere
    List<String> beforeShuffle = sorted(storedSongNames());
    click(admin, UiComponentNames.ADMIN_SHUFFLE_BUTTON);
    confirmAdminOverlay("Shuffle Queue");
    awaitEquals(beforeShuffle, () -> sorted(storedSongNames()), "songs after Shuffle");

    click(admin, UiComponentNames.ADMIN_FLUSH_BUTTON);
    confirmAdminOverlay("Clear Queue");
    await(() -> storedQueue().isEmpty(), "Flush to empty the stored queue");
    await(() -> adminQueueSize() == 0, "Flush to empty the admin queue list");
    await(() -> displayedQueue().isEmpty(), "Flush to empty the QUEUE tab");
  }

  @Test
  @Order(10)
  void step10_adminTools() throws Exception {

    Container admin = adminPanel();
    int albumCount = songLibraryService.getAlbums(locationId()).size();

    click(admin, UiComponentNames.ADMIN_RESET_STATS_BUTTON);
    confirmAdminOverlay("Reset Statistics");

    // Rescan the existing library root (no chooser); ends with a Scan Complete notice
    click(admin, UiComponentNames.ADMIN_RESCAN_LIBRARY_BUTTON);
    confirmAdminOverlay("Rescan Library");
    awaitAdminOverlay("Scan Complete", SCAN_TIMEOUT_MS);
    click(admin, UiComponentNames.ADMIN_OVERLAY_PRIMARY_BUTTON);
    List<AlbumDto> rescanned = songLibraryService.getAlbums(locationId());
    assertEquals(albumCount, rescanned.size(), "a rescan of the same folder finds the same albums");
    // A rescan rebuilds the library, so re-resolve the chosen songs' ids by name
    chooseFixtureSongs(rescanned);

    // Add Admin User (unique per run, since users persist in the test data dir)
    String email = "functional.test." + System.currentTimeMillis() + "@jukeanator.test";
    click(admin, UiComponentNames.ADMIN_ADD_ADMIN_USER_BUTTON);
    await(() -> onEdt(() -> findShowing(admin, UiComponentNames.ADMIN_USER_EMAIL_FIELD,
        JTextField.class).isPresent()), "the Add Admin User form");
    runOnEdt(() -> {
      requireShowing(admin, UiComponentNames.ADMIN_USER_FIRST_NAME_FIELD, JTextField.class)
          .setText("Functional");
      requireShowing(admin, UiComponentNames.ADMIN_USER_LAST_NAME_FIELD, JTextField.class)
          .setText("Test");
      requireShowing(admin, UiComponentNames.ADMIN_USER_EMAIL_FIELD, JTextField.class)
          .setText(email);
      requireShowing(admin, UiComponentNames.ADMIN_USER_PASSWORD_FIELD, JTextField.class)
          .setText("FunctionalTest1");
    });
    click(admin, UiComponentNames.ADMIN_USER_CREATE_BUTTON);
    awaitAdminOverlay("Admin User Created", SwingTestSupport.DEFAULT_TIMEOUT_MS);
    click(admin, UiComponentNames.ADMIN_OVERLAY_PRIMARY_BUTTON);
    assertNotNull(userService.getProfile(email), "the admin user should exist");

    // Edit Location Info opens pre-filled; Cancel leaves it unchanged
    click(admin, UiComponentNames.ADMIN_EDIT_LOCATION_INFO_BUTTON);
    await(() -> onEdt(() -> findShowing(admin, UiComponentNames.ADMIN_LOCATION_NAME_FIELD,
        JTextField.class).map(f -> !f.getText().isBlank()).orElse(false)),
        "the Edit Location Info form with the current name");
    click(admin, UiComponentNames.ADMIN_FORM_CANCEL_BUTTON);
    assertFalse(isAdminOverlayShowing(), "Cancel should close the form");

    // Financial Ledger and View Activity show rows from the earlier steps and close on Esc
    click(admin, UiComponentNames.ADMIN_FINANCIAL_LEDGER_BUTTON);
    await(() -> tableRows(admin, UiComponentNames.ADMIN_LEDGER_TABLE) > 0,
        "the ledger to show the open period");
    pressEscapeOn(admin, UiComponentNames.ADMIN_LEDGER_CONTENT);
    assertFalse(isAdminOverlayShowing(), "Esc should close the ledger");

    click(admin, UiComponentNames.ADMIN_VIEW_ACTIVITY_BUTTON);
    await(() -> tableRows(admin, UiComponentNames.ADMIN_ACTIVITY_TABLE) > 0,
        "the activity log to show the navigation from the earlier steps");
    pressEscapeOn(admin, UiComponentNames.ADMIN_ACTIVITY_CONTENT);
    assertFalse(isAdminOverlayShowing(), "Esc should close the activity log");
  }

  @Test
  @Order(11)
  void step11_editAlbumCard() throws Exception {

    JukeANatorFrame frame = frame();
    selectAdminAlbum(homeAlbum);
    click(adminPanel(), UiComponentNames.ADMIN_EDIT_ALBUM_BUTTON);
    assertTrue(isCardShowing(frame, JukeANatorFrame.CARD_EDIT_ALBUM), "Edit Album should open");

    JLabel header = onEdt(() -> requireShowing(frame, UiComponentNames.EDIT_ALBUM_HEADER_LABEL,
        JLabel.class));
    assertTrue(onEdt(header::getText).contains(homeAlbum.albumName()),
        "header: " + onEdt(header::getText));
    assertEquals(homeAlbum.albumName(), onEdt(() -> requireShowing(frame,
        UiComponentNames.EDIT_ALBUM_SEARCH_ALBUM_FIELD, JTextField.class).getText()));

    // Prev/Next step through albums with invalid metadata (if any); the card stays usable
    click(frame, UiComponentNames.EDIT_ALBUM_NEXT_ALBUM_BUTTON);
    click(frame, UiComponentNames.EDIT_ALBUM_PREV_ALBUM_BUTTON);
    assertTrue(onEdt(header::getText).startsWith("Editing: "));

    click(frame, UiComponentNames.EDIT_ALBUM_CANCEL_BUTTON);
    assertFalse(isCardShowing(frame, JukeANatorFrame.CARD_EDIT_ALBUM), "Cancel should close it");
    assertTrue(onEdt(() -> tabs().isShowing()), "the tabs should be back");
  }

  @Test
  @Order(12)
  void step12_playbackAndNowPlaying() throws Exception {

    JukeANatorFrame frame = frame();
    Container admin = adminPanel();

    // Queue the Hot Here song's album while locked, then unlock so the player starts it
    AlbumDto playbackAlbum = songLibraryService.getAlbumById(locationId(), hotHereSong.albumId());
    selectAdminAlbum(playbackAlbum);
    click(admin, UiComponentNames.ADMIN_QUEUE_ALBUM_BUTTON);
    await(() -> !storedQueue().isEmpty(), "the playback album to be queued");
    String firstSong = storedSongNames().getFirst();

    click(admin, UiComponentNames.ADMIN_UNLOCK_QUEUE_BUTTON);
    assertFalse(songQueueService.isLocked(), "Unlock Queue should unlock the queue");
    assertTrue(onEdt(() -> button(admin, UiComponentNames.ADMIN_LOCK_QUEUE_BUTTON).isEnabled()));
    assertFalse(onEdt(() -> button(admin, UiComponentNames.ADMIN_UNLOCK_QUEUE_BUTTON)
        .isEnabled()));

    // SongPlaybackStartedEvent → JukeANatorEventListener → the Now Playing panel
    await(() -> onEdt(() -> nowPlayingPanel().isVisible()
        && firstSong.equals(nowPlayingSongLabel().getText())), "Now Playing to show " + firstSong);

    // Clicking Now Playing opens the album card; Back restores the tab
    clickComponent(onEdt(this::nowPlayingPanel));
    assertTrue(isCardShowing(frame, JukeANatorFrame.CARD_NOW_PLAYING_ALBUM),
        "the Now Playing album card should open");
    click(frame, UiComponentNames.ALBUM_DETAIL_BACK_BUTTON);
    assertFalse(isCardShowing(frame, JukeANatorFrame.CARD_NOW_PLAYING_ALBUM));
    assertEquals(TAB_ADMIN, selectedTabTitle(), "Back restores the tab that was showing");

    // Pause, then lock and skip: with nothing left to dequeue, Now Playing clears
    click(admin, UiComponentNames.ADMIN_PAUSE_BUTTON);
    click(admin, UiComponentNames.ADMIN_LOCK_QUEUE_BUTTON);
    assertTrue(songQueueService.isLocked());
    click(admin, UiComponentNames.ADMIN_NEXT_BUTTON);
    await(() -> onEdt(() -> !nowPlayingPanel().isVisible()), "Now Playing to clear");

    // Popularity bars reflect the stored play counts (JukeANatorFrame's thresholds)
    selectTab(TAB_HOT_HERE);
    await(() -> {
      SongDto stored = storedSong(hotHereSong);
      Optional<Integer> shown = onEdt(() -> findShowingWithText(column(tab(TAB_HOT_HERE),
          "SONGS"), UiComponentNames.RESULTS_ROW, JPanel.class, hotHereSong.songName())
          .flatMap(row -> descendants(row, SongTrackCellRenderer.PopularityBarsPanel.class)
              .stream().findFirst())
          .map(SongTrackCellRenderer.PopularityBarsPanel::getActiveBars));
      return shown.isPresent() && shown.get() == expectedBars(stored);
    }, "the Hot Here popularity bars to match the stored play count");

    // Back to Admin (its buttons only respond while it is showing), then the same check for the
    // QUEUE tab rows, and flush the queue again
    selectTab(TAB_ADMIN);
    flushStoredQueueThroughAdmin();
    selectAdminAlbum(queueAlbum);
    click(admin, UiComponentNames.ADMIN_QUEUE_ALBUM_BUTTON);
    SongDto queuedSong = queueAlbum.songs().getFirst();
    awaitQueueTabShows(queuedSong.songName());
    int shownBars = onEdt(() -> queueRows().stream()
        .filter(r -> containsLabelText(r, queuedSong.songName())).findFirst()
        .flatMap(r -> descendants(r, SongTrackCellRenderer.PopularityBarsPanel.class).stream()
            .findFirst())
        .map(SongTrackCellRenderer.PopularityBarsPanel::getActiveBars).orElse(-1));
    assertEquals(expectedBars(storedSong(queuedSong)), shownBars, "QUEUE row popularity bars");
    flushStoredQueueThroughAdmin();
  }

  @Test
  @Order(13)
  void step13_escapeTogglesAdmin() throws Exception {

    assertEquals(TAB_ADMIN, selectedTabTitle());

    // Esc leaves Admin for the tab shown before login, and comes back without a login
    pressEscape();
    String previous = selectedTabTitle();
    assertEquals(TAB_QUEUE, previous, "Esc returns to the tab shown before Admin");
    pressEscape();
    assertEquals(TAB_ADMIN, selectedTabTitle(), "Esc from a patron tab opens Admin");
    pressEscape();
    assertEquals(previous, selectedTabTitle());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // FIXTURES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Picks the songs the steps use, by artist, so that no queue rule other than the one a step
   * tests (already queued) can get in the way.
   */
  private static void chooseFixtureSongs(List<AlbumDto> albums) {

    List<AlbumDto> byName =
        albums.stream().filter(a -> a.songs() != null && !a.songs().isEmpty())
            .sorted(Comparator.comparing(AlbumDto::albumName)).toList();
    librarySongNames = byName.stream().flatMap(a -> a.songs().stream()).map(SongDto::songName)
        .collect(Collectors.toUnmodifiableSet());

    homeAlbum = byName.stream().filter(a -> !Boolean.TRUE.equals(a.isCompilation())).findFirst()
        .orElse(byName.getFirst());
    homeSong = homeAlbum.songs().getFirst();

    AlbumDto hotHereAlbum = byName.stream()
        .filter(a -> !a.albumId().equals(homeAlbum.albumId()))
        .filter(a -> a.songs().stream().noneMatch(s -> s.artistName().equals(homeSong.artistName())))
        .findFirst().orElseThrow(() -> new AssertionError("need a second artist in the fixtures"));
    hotHereSong = hotHereAlbum.songs().getFirst();

    queueAlbum = byName.stream()
        .filter(a -> !a.albumId().equals(homeAlbum.albumId()))
        .filter(a -> !a.albumId().equals(hotHereAlbum.albumId())).findFirst()
        .orElseThrow(() -> new AssertionError("need a third album in the fixtures"));

    // Prefer a regular artist over a compilation, whose "artist" is the compilations folder
    List<AlbumDto> inGenre = byName.stream()
        .filter(a -> hotHereSong.genreName().equals(a.genreName())).toList();
    genreAlbum = inGenre.stream().filter(a -> !Boolean.TRUE.equals(a.isCompilation())).findFirst()
        .orElse(inGenre.getFirst());
  }

  private static String sha256Hex(String value) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private Integer locationId() {
    return songLibraryService.getOwnLocationId();
  }

  private List<SongQueueEntryDto> storedQueue() {
    return songQueueService.getQueuedSongs(locationId());
  }

  private List<String> storedSongNames() {
    return storedQueue().stream().map(e -> e.song().songName()).toList();
  }

  private SongDto storedSong(SongDto song) {
    return songLibraryService.getAlbumById(locationId(), song.albumId()).songs().stream()
        .filter(s -> s.songId().equals(song.songId())).findFirst().orElseThrow();
  }

  private static int expectedBars(SongDto song) {
    return SongTrackCellRenderer.barsForPlays(song.numPlays() == null ? 0 : song.numPlays(),
        JukeANatorFrame.POPULARITY_THRESHOLD_1, JukeANatorFrame.POPULARITY_THRESHOLD_2,
        JukeANatorFrame.POPULARITY_THRESHOLD_3);
  }

  private BigDecimal openPeriodCashTotal() {
    return financialLedgerService.getAllPeriods().stream().filter(p -> p.endDate() == null)
        .map(JukeboxSplitPeriodDto::cashTotal).filter(t -> t != null).findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static List<String> sorted(List<String> names) {
    return names.stream().sorted().toList();
  }

  private static String searchWord(String artistName) {
    String firstWord = artistName.trim().split("\\s+")[0];
    return firstWord.toUpperCase().replaceAll("[^A-Z]", "");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // UI HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  private static Optional<JukeANatorFrame> findFrame() {
    for (Frame frame : Frame.getFrames()) {
      if (frame instanceof JukeANatorFrame jukeANatorFrame && frame.isShowing()) {
        return Optional.of(jukeANatorFrame);
      }
    }
    return Optional.empty();
  }

  private static JukeANatorFrame frame() {
    return findFrame().orElseThrow(() -> new AssertionError("the JukeANator frame is not showing"));
  }

  private boolean isScanDialog(java.awt.Window window) {
    return window instanceof JDialog dialog && SCAN_DIALOG_TITLE.equals(dialog.getTitle());
  }

  private static JTabbedPane tabs() throws Exception {
    return onEdt(() -> SwingTestSupport.find(frame(), UiComponentNames.FRAME_TABS,
        JTabbedPane.class).orElseThrow());
  }

  private static Container tab(String title) throws Exception {
    JTabbedPane tabs = tabs();
    return onEdt(() -> (Container) tabs.getComponentAt(tabs.indexOfTab(title)));
  }

  private static Container adminPanel() throws Exception {
    return tab(TAB_ADMIN);
  }

  /** Selects a tab, as tapping its header does (fires the same ChangeListener). */
  private static void selectTab(String title) throws Exception {
    JTabbedPane tabs = tabs();
    runOnEdt(() -> tabs.setSelectedIndex(tabs.indexOfTab(title)));
    flushEdt();
  }

  private static String selectedTabTitle() throws Exception {
    JTabbedPane tabs = tabs();
    return onEdt(() -> tabs.getTitleAt(tabs.getSelectedIndex()));
  }

  private static JLabel creditsLabel() {
    return SwingTestSupport.find(frame(), UiComponentNames.FRAME_CREDITS_TITLE, JLabel.class)
        .orElseThrow();
  }

  /** The balance shown in the credits panel, e.g. {@code CREDITS: 6cr} → 6. */
  private static int credits() throws Exception {
    flushEdt(); // CreditManager notifies its listeners with invokeLater
    String text = onEdt(() -> creditsLabel().getText());
    return Integer.parseInt(text.replaceAll("\\D", ""));
  }

  /** Fires the bill acceptor's key binding, as inserting a one-dollar bill does. */
  private void insertDollar() throws Exception {
    KeyStroke billAcceptor = KeyStroke.getKeyStroke(userInterfaceProperties.getIncrementCreditsKey());
    pressKeyBinding(frame().getRootPane(), billAcceptor);
  }

  private void insertDollarsUntil(int minimumCredits) throws Exception {
    while (credits() < minimumCredits) {
      insertDollar();
    }
  }

  private static void pressEscape() throws Exception {
    pressKeyBinding(frame().getRootPane(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
  }

  private static void pressEscapeOn(Container root, String name) throws Exception {
    JComponent component = onEdt(() -> requireShowing(root, name, JComponent.class));
    pressKeyBinding(component, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
  }

  private static int showingCount(Container root, String name) throws Exception {
    return onEdt(() -> findAllShowing(root, name, Component.class).size());
  }

  private static JPanel requireShowingWithText(Container root, String name, String text)
      throws Exception {
    return onEdt(() -> findShowingWithText(root, name, JPanel.class, text)
        .orElseThrow(() -> new AssertionError("No showing " + name + " with text " + text)));
  }

  private static JButton button(Container root, String name) throws Exception {
    return onEdt(() -> SwingTestSupport.find(root, name, JButton.class).orElseThrow());
  }

  /** Types {@code text} with the on-screen keyboard showing under {@code root}. */
  private static void typeOnKeyboard(Container root, String text) throws Exception {
    for (char c : text.toCharArray()) {
      String caption = c == ' ' ? "SPACE" : String.valueOf(c);
      click(onEdt(() -> requireShowing(root, UiComponentNames.keyboardKey(caption),
          AbstractButton.class)));
    }
  }

  private static Container column(Container root, String header) throws Exception {
    return onEdt(() -> requireShowing(root, UiComponentNames.resultsColumn(header),
        Container.class));
  }

  /** The result row in {@code header}'s column containing {@code text}, paging down to find it. */
  private static JPanel resultRow(Container root, String header, String text) throws Exception {
    for (int page = 0; page < 50; page++) {
      Container column = column(root, header);
      Optional<JPanel> row =
          onEdt(() -> findShowingWithText(column, UiComponentNames.RESULTS_ROW, JPanel.class, text));
      if (row.isPresent()) {
        return row.get();
      }
      JButton down = onEdt(() -> requireShowing(column, UiComponentNames.RESULTS_COLUMN_DOWN_BUTTON,
          JButton.class));
      if (!onEdt(down::isEnabled)) {
        break;
      }
      click(down);
    }
    throw new AssertionError("No " + header + " row with text " + text);
  }

  /**
   * Artist result → Artist View → album → album details → Back → Back, from a tab's result
   * columns (Search, Hot Here or a genre's detail). The Artist View must show a tile for every
   * album by the artist; the album details must list the album's songs. Album details Back returns
   * to the Artist View it was opened from, and the Artist View's Back to the result columns.
   */
  private void exerciseArtistView(Container tab, String resultsCard, String artistCard,
      String detailCard, AlbumDto album) throws Exception {

    String artistName = album.artistName();
    clickComponent(resultRow(tab, "ARTISTS", artistName));
    assertTrue(isCardShowing(tab, artistCard), "the Artist View for " + artistName + " should show");

    Container artistView = onEdt(() -> requireShowing(tab, artistCard, Container.class));
    assertTrue(onEdt(() -> containsLabelText(artistView, artistName)),
        "the Artist View header should name " + artistName);
    List<String> artistAlbums = songLibraryService.getAlbums(locationId()).stream()
        .filter(a -> artistName.equals(a.artistName())).map(AlbumDto::albumName).toList();
    assertFalse(artistAlbums.isEmpty(), "the library should have albums by " + artistName);
    for (String albumName : artistAlbums) {
      assertTrue(onEdt(() -> findShowingWithText(artistView, UiComponentNames.ALBUM_TILE,
          JPanel.class, albumName).isPresent()), "the Artist View should show " + albumName);
    }

    clickComponent(requireShowingWithText(artistView, UiComponentNames.ALBUM_TILE,
        album.albumName()));
    assertAlbumDetailsShowing(tab, detailCard, album);

    click(tab, UiComponentNames.ALBUM_DETAIL_BACK_BUTTON);
    assertTrue(isCardShowing(tab, artistCard), "album details Back returns to the Artist View");
    click(tab, UiComponentNames.DETAIL_HEADER_BACK_BUTTON);
    assertTrue(isCardShowing(tab, resultsCard), "Artist View Back returns to the results");
  }

  /** Album result → album details → Back, the same details the Artist View leads to. */
  private void exerciseAlbumResult(Container tab, String resultsCard, String detailCard,
      AlbumDto album) throws Exception {

    clickComponent(resultRow(tab, "ALBUMS", album.albumName()));
    assertAlbumDetailsShowing(tab, detailCard, album);
    click(tab, UiComponentNames.ALBUM_DETAIL_BACK_BUTTON);
    assertTrue(isCardShowing(tab, resultsCard), "album details Back returns to the results");
  }

  /** The album details card is showing and lists every song of {@code album}. */
  private void assertAlbumDetailsShowing(Container tab, String detailCard, AlbumDto album)
      throws Exception {

    assertTrue(isCardShowing(tab, detailCard), "the album details for " + album.albumName()
        + " should show");
    Container details = onEdt(() -> requireShowing(tab, detailCard, Container.class));
    AlbumDto stored = songLibraryService.getAlbumById(locationId(), album.albumId());
    for (SongDto song : stored.songs()) {
      assertTrue(onEdt(() -> findShowingWithText(details, UiComponentNames.ALBUM_VIEW_TRACK_ROW,
          JPanel.class, song.songName()).isPresent()),
          "the album details should list " + song.songName());
    }
  }

  private static boolean isAddSongCardShowing() throws Exception {
    return onEdt(() -> SwingTestSupport.find(frame(), UiComponentNames.FRAME_ADD_SONG_GLASS_PANE,
        JPanel.class).orElseThrow().isVisible());
  }

  /** Presses Play or Priority Play on the open Add Song card and waits for it to close. */
  private static void playFromAddSongCard(String buttonName) throws Exception {
    assertTrue(isAddSongCardShowing(), "the Add Song card should open");
    JButton play = onEdt(() -> requireShowing(frame(), buttonName, JButton.class));
    assertTrue(onEdt(play::isEnabled), buttonName + " should be enabled with enough credits");
    click(play);
    await(() -> !isAddSongCardShowing(), "the Add Song card to close");
  }

  private void awaitStoredQueueContains(String songName) throws Exception {
    await(() -> storedSongNames().contains(songName), songName + " in the stored queue");
  }

  private static List<JPanel> queueRows() throws Exception {
    return onEdt(() -> findAll(tab(TAB_QUEUE), UiComponentNames.QUEUE_ROW, JPanel.class));
  }

  /** Song names in the QUEUE tab, top to bottom (the tab need not be selected). */
  private List<String> displayedQueue() throws Exception {
    List<String> names = new ArrayList<>();
    for (JPanel row : queueRows()) {
      onEdt(() -> SwingTestSupport.labelTexts(row)).stream().filter(librarySongNames::contains)
          .findFirst().ifPresent(names::add);
    }
    return names;
  }

  private static void awaitQueueTabShows(String songName) throws Exception {
    await(() -> queueRows().stream().anyMatch(r -> {
      try {
        return onEdt(() -> containsLabelText(r, songName));
      } catch (Exception e) {
        return false;
      }
    }), "the QUEUE tab to show " + songName);
  }

  private static void selectQueueRow(String songName) throws Exception {
    clickComponent(onEdt(() -> findAll(tab(TAB_QUEUE), UiComponentNames.QUEUE_ROW, JPanel.class)
        .stream().filter(r -> containsLabelText(r, songName)).findFirst().orElseThrow()));
  }

  private static String buttonState(JButton button) throws Exception {
    flushEdt();
    return onEdt(() -> String.valueOf(button.getClientProperty("buttonState")));
  }

  private static Container loginCard() throws Exception {
    JukeANatorFrame frame = frame();
    assertTrue(isCardShowing(frame, JukeANatorFrame.CARD_LOGIN), "the login card should open");
    return onEdt(() -> SwingTestSupport.findShowing(frame, JukeANatorFrame.CARD_LOGIN,
        Container.class).orElseThrow());
  }

  private JPanel nowPlayingPanel() {
    return SwingTestSupport.find(frame(), UiComponentNames.FRAME_NOW_PLAYING_PANEL, JPanel.class)
        .orElseThrow();
  }

  private JLabel nowPlayingSongLabel() {
    return SwingTestSupport.find(frame(), UiComponentNames.FRAME_NOW_PLAYING_SONG_LABEL,
        JLabel.class).orElseThrow();
  }

  // ── Admin panel ─────────────────────────────────────────────────────────────

  private static boolean isAdminOverlayShowing() throws Exception {
    Container admin = adminPanel();
    return onEdt(() -> SwingTestSupport.find(admin, UiComponentNames.ADMIN_OVERLAY_SCRIM,
        Component.class).orElseThrow().isVisible());
  }

  /** Waits for the admin message/confirm overlay with the given title. */
  private static void awaitAdminOverlay(String title, long timeoutMs) throws Exception {
    Container admin = adminPanel();
    await(() -> onEdt(() -> {
      Component card = SwingTestSupport.find(admin, UiComponentNames.ADMIN_OVERLAY_CARD,
          Component.class).orElseThrow();
      JLabel titleLabel = SwingTestSupport.find(admin, UiComponentNames.ADMIN_OVERLAY_TITLE_LABEL,
          JLabel.class).orElseThrow();
      return card.isShowing() && title.equals(titleLabel.getText());
    }), "the admin overlay \"" + title + "\"", timeoutMs);
  }

  /** Answers Yes to the admin confirm overlay with the given title. */
  private static void confirmAdminOverlay(String title) throws Exception {
    awaitAdminOverlay(title, SwingTestSupport.DEFAULT_TIMEOUT_MS);
    click(adminPanel(), UiComponentNames.ADMIN_OVERLAY_PRIMARY_BUTTON);
  }

  private void flushStoredQueueThroughAdmin() throws Exception {
    click(adminPanel(), UiComponentNames.ADMIN_FLUSH_BUTTON);
    confirmAdminOverlay("Clear Queue");
    await(() -> storedQueue().isEmpty(), "Flush to empty the stored queue");
  }

  @SuppressWarnings("unchecked")
  private static JList<Object> adminList(String name) throws Exception {
    return (JList<Object>) onEdt(() -> SwingTestSupport.find(adminPanel(), name, JList.class)
        .orElseThrow());
  }

  /** Selects the album in the admin album list, as tapping its row does. */
  private static void selectAdminAlbum(AlbumDto album) throws Exception {
    JList<Object> list = adminList(UiComponentNames.ADMIN_ALBUM_LIST);
    runOnEdt(() -> {
      for (int i = 0; i < list.getModel().getSize(); i++) {
        if (list.getModel().getElementAt(i) instanceof AlbumDto a
            && a.albumId().equals(album.albumId())) {
          list.setSelectedIndex(i);
          list.ensureIndexIsVisible(i);
          return;
        }
      }
      throw new AssertionError("album not in the admin list: " + album.albumName());
    });
  }

  private static void selectAdminQueueIndex(int index) throws Exception {
    JList<Object> list = adminList(UiComponentNames.ADMIN_QUEUE_LIST);
    runOnEdt(() -> list.setSelectedIndex(index));
  }

  private static int adminQueueSize() throws Exception {
    JList<Object> list = adminList(UiComponentNames.ADMIN_QUEUE_LIST);
    return onEdt(() -> list.getModel().getSize());
  }

  private static int tableRows(Container root, String name) throws Exception {
    return onEdt(() -> findShowing(root, name, JTable.class).map(JTable::getRowCount).orElse(0));
  }
}
