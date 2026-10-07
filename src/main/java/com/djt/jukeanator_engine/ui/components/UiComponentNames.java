package com.djt.jukeanator_engine.ui.components;

/**
 * Component names ({@link java.awt.Component#setName(String)}) for the JFC/Swing controls that
 * functional tests drive or inspect.
 *
 * <p>
 * Both the production components and the tests reference these constants, so a locator can never
 * drift out of sync with the component it finds. Names follow the convention
 * {@code ClassName.role}. Components that repeat (queue rows, album tiles, result rows, genre
 * tiles, track rows) share a single name; a test picks the one it wants by the text of a
 * descendant label.
 *
 * <p>
 * CardLayout cards keep the names they are registered under (for example
 * {@link JukeANatorFrame#CARD_LOGIN} and {@link HomePanel#CARD_DETAIL}), so those are not repeated
 * here.
 */
public final class UiComponentNames {

  private UiComponentNames() {}

  // ── JukeANatorFrame ─────────────────────────────────────────────────────────
  public static final String FRAME_TABS = "JukeANatorFrame.tabs";
  public static final String FRAME_OVERLAY_ROOT = "JukeANatorFrame.overlayRoot";
  public static final String FRAME_ADD_SONG_GLASS_PANE = "JukeANatorFrame.addSongGlassPane";
  public static final String FRAME_CREDITS_PANEL = "JukeANatorFrame.creditsPanel";
  public static final String FRAME_CREDITS_TITLE = "JukeANatorFrame.creditsTitle";
  public static final String FRAME_NOW_PLAYING_PANEL = "JukeANatorFrame.nowPlayingPanel";
  public static final String FRAME_NOW_PLAYING_SONG_LABEL = "JukeANatorFrame.nowPlayingSongLabel";

  // ── HomePanel / album grids ─────────────────────────────────────────────────
  public static final String HOME_LEGACY_TOGGLE = "HomePanel.legacyToggle";
  public static final String HOME_SORT_ARTIST_BUTTON = "HomePanel.sortArtistButton";
  public static final String HOME_SORT_ALBUM_BUTTON = "HomePanel.sortAlbumButton";
  public static final String ALBUM_TILE = "AlbumGridPanel.albumTile";
  public static final String ALBUM_GRID_PREV_BUTTON = "AlbumGridPanel.prevButton";
  public static final String ALBUM_GRID_NEXT_BUTTON = "AlbumGridPanel.nextButton";
  public static final String LEGACY_ALBUM_CARD = "LegacyAlbumGridPanel.albumCard";
  public static final String LEGACY_TRACK_ROW = "LegacyAlbumGridPanel.trackRow";

  // ── Album detail ────────────────────────────────────────────────────────────
  public static final String ALBUM_VIEW_TRACK_ROW = "AlbumViewCard.trackRow";
  public static final String ALBUM_VIEW_PREV_BUTTON = "AlbumViewCard.prevButton";
  public static final String ALBUM_VIEW_NEXT_BUTTON = "AlbumViewCard.nextButton";
  public static final String ALBUM_DETAIL_BACK_BUTTON = "AlbumDetailCard.backButton";
  public static final String DETAIL_HEADER_BACK_BUTTON = "DetailHeaderPanel.backButton";

  // ── SearchPanel / result columns ────────────────────────────────────────────
  public static final String SEARCH_TEXT_LABEL = "SearchPanel.searchTextLabel";
  public static final String SEARCH_BUTTON = "SearchPanel.searchButton";
  public static final String RESULTS_ROW = "ResultsColumnPanel.row";
  public static final String RESULTS_COLUMN_HEADER_LABEL = "ResultsColumnPanel.headerLabel";
  public static final String RESULTS_COLUMN_UP_BUTTON = "ResultsColumnPanel.upButton";
  public static final String RESULTS_COLUMN_DOWN_BUTTON = "ResultsColumnPanel.downButton";

  /** Name of a result column, e.g. {@code resultsColumn("SONGS")}. */
  public static String resultsColumn(String header) {
    return "ResultsColumnPanel.column." + header;
  }

  // ── KeyboardPanel ───────────────────────────────────────────────────────────
  /** Name of an on-screen keyboard key by its caption, e.g. {@code keyboardKey("A")}. */
  public static String keyboardKey(String caption) {
    return "KeyboardPanel.key." + caption;
  }

  // ── HotHerePanel ────────────────────────────────────────────────────────────
  public static final String HOT_HERE_SORT_POPULARITY_BUTTON = "HotHerePanel.sortPopularityButton";
  public static final String HOT_HERE_SORT_ALBUM_BUTTON = "HotHerePanel.sortAlbumButton";

  // ── GenrePanel / GenreDetailPanel ───────────────────────────────────────────
  public static final String GENRE_TILE = "GenrePanel.genreTile";
  public static final String GENRE_PREV_BUTTON = "GenrePanel.prevButton";
  public static final String GENRE_NEXT_BUTTON = "GenrePanel.nextButton";
  public static final String GENRE_DETAIL_SORT_POPULARITY_BUTTON =
      "GenreDetailPanel.sortPopularityButton";
  public static final String GENRE_DETAIL_SORT_ALBUM_BUTTON = "GenreDetailPanel.sortAlbumButton";

  // ── AddSongToQueueCard ──────────────────────────────────────────────────────
  public static final String ADD_SONG_PLAY_BUTTON = "AddSongToQueueCard.playButton";
  public static final String ADD_SONG_PRIORITY_PLAY_BUTTON = "AddSongToQueueCard.priorityPlayButton";
  public static final String ADD_SONG_CANCEL_BUTTON = "AddSongToQueueCard.cancelButton";
  public static final String ADD_SONG_OK_BUTTON = "AddSongToQueueCard.okButton";
  public static final String ADD_SONG_CONSTRAINT_LABEL = "AddSongToQueueCard.constraintLabel";

  // ── QueuePanel ──────────────────────────────────────────────────────────────
  public static final String QUEUE_ROW = "QueuePanel.row";
  public static final String QUEUE_MOVE_UP_BUTTON = "QueuePanel.moveUpButton";
  public static final String QUEUE_MOVE_DOWN_BUTTON = "QueuePanel.moveDownButton";
  public static final String QUEUE_REMOVE_BUTTON = "QueuePanel.removeButton";

  // ── LoginToAdminPanelCard ───────────────────────────────────────────────────
  public static final String LOGIN_USERNAME_FIELD = "LoginToAdminPanelCard.usernameField";
  public static final String LOGIN_PASSWORD_FIELD = "LoginToAdminPanelCard.passwordField";
  public static final String LOGIN_LOGIN_BUTTON = "LoginToAdminPanelCard.loginButton";
  public static final String LOGIN_CANCEL_BUTTON = "LoginToAdminPanelCard.cancelButton";
  public static final String LOGIN_ERROR_LABEL = "LoginToAdminPanelCard.errorLabel";
  public static final String LOGIN_TIMEOUT_LABEL = "LoginToAdminPanelCard.timeoutLabel";

  // ── AdminPanel: lists ───────────────────────────────────────────────────────
  public static final String ADMIN_ALBUM_LIST = "AdminPanel.albumList";
  public static final String ADMIN_QUEUE_LIST = "AdminPanel.queueList";
  public static final String ADMIN_FILTER_FIELD = "AdminPanel.filterField";

  // ── AdminPanel: west (library) side buttons ─────────────────────────────────
  public static final String ADMIN_FINANCIAL_LEDGER_BUTTON = "AdminPanel.financialLedgerButton";
  public static final String ADMIN_VIEW_ACTIVITY_BUTTON = "AdminPanel.viewActivityButton";
  public static final String ADMIN_QUEUE_ALBUM_BUTTON = "AdminPanel.queueAlbumButton";
  public static final String ADMIN_LOCK_QUEUE_BUTTON = "AdminPanel.lockQueueButton";
  public static final String ADMIN_UNLOCK_QUEUE_BUTTON = "AdminPanel.unlockQueueButton";
  public static final String ADMIN_EDIT_ALBUM_BUTTON = "AdminPanel.editAlbumButton";
  public static final String ADMIN_RESET_STATS_BUTTON = "AdminPanel.resetStatsButton";
  public static final String ADMIN_RESCAN_LIBRARY_BUTTON = "AdminPanel.rescanLibraryButton";
  public static final String ADMIN_ADD_ADMIN_USER_BUTTON = "AdminPanel.addAdminUserButton";
  public static final String ADMIN_ADD_LOCATION_BUTTON = "AdminPanel.addLocationButton";
  public static final String ADMIN_EDIT_LOCATION_INFO_BUTTON = "AdminPanel.editLocationInfoButton";
  public static final String ADMIN_MINIMIZE_BUTTON = "AdminPanel.minimizeButton";
  public static final String ADMIN_EXIT_BUTTON = "AdminPanel.exitButton";

  // ── AdminPanel: east (queue) side buttons ───────────────────────────────────
  public static final String ADMIN_NEXT_BUTTON = "AdminPanel.nextButton";
  public static final String ADMIN_PAUSE_BUTTON = "AdminPanel.pauseButton";
  public static final String ADMIN_PLAY_BUTTON = "AdminPanel.playButton";
  public static final String ADMIN_MOVE_UP_BUTTON = "AdminPanel.moveUpButton";
  public static final String ADMIN_MOVE_DOWN_BUTTON = "AdminPanel.moveDownButton";
  public static final String ADMIN_REMOVE_BUTTON = "AdminPanel.removeButton";
  public static final String ADMIN_FLUSH_BUTTON = "AdminPanel.flushButton";
  public static final String ADMIN_SHUFFLE_BUTTON = "AdminPanel.shuffleButton";
  public static final String ADMIN_LOAD_PLAYLIST_BUTTON = "AdminPanel.loadPlaylistButton";
  public static final String ADMIN_SAVE_PLAYLIST_BUTTON = "AdminPanel.savePlaylistButton";
  public static final String ADMIN_INCREMENT_CREDITS_BUTTON = "AdminPanel.incrementCreditsButton";
  public static final String ADMIN_DECREMENT_CREDITS_BUTTON = "AdminPanel.decrementCreditsButton";

  // ── AdminPanel: in-window message / confirm overlay ─────────────────────────
  public static final String ADMIN_OVERLAY_SCRIM = "AdminPanel.overlayScrim";
  public static final String ADMIN_OVERLAY_CARD = "AdminPanel.overlayCard";
  public static final String ADMIN_OVERLAY_FORM_CARD = "AdminPanel.overlayFormCard";
  public static final String ADMIN_OVERLAY_TITLE_LABEL = "AdminPanel.overlayTitleLabel";
  public static final String ADMIN_OVERLAY_MESSAGE_LABEL = "AdminPanel.overlayMessageLabel";
  public static final String ADMIN_OVERLAY_PRIMARY_BUTTON = "AdminPanel.overlayPrimaryButton";
  public static final String ADMIN_OVERLAY_SECONDARY_BUTTON = "AdminPanel.overlaySecondaryButton";

  // ── AdminPanel: forms and dialogs ───────────────────────────────────────────
  public static final String ADMIN_FORM_CANCEL_BUTTON = "AdminPanel.formCancelButton";
  public static final String ADMIN_USER_FIRST_NAME_FIELD = "AdminPanel.addAdminUser.firstNameField";
  public static final String ADMIN_USER_LAST_NAME_FIELD = "AdminPanel.addAdminUser.lastNameField";
  public static final String ADMIN_USER_EMAIL_FIELD = "AdminPanel.addAdminUser.emailField";
  public static final String ADMIN_USER_PASSWORD_FIELD = "AdminPanel.addAdminUser.passwordField";
  public static final String ADMIN_USER_ERROR_LABEL = "AdminPanel.addAdminUser.errorLabel";
  public static final String ADMIN_USER_CREATE_BUTTON = "AdminPanel.addAdminUser.createButton";
  public static final String ADMIN_LOCATION_NAME_FIELD = "AdminPanel.editLocationInfo.nameField";
  public static final String ADMIN_LOCATION_SAVE_BUTTON = "AdminPanel.editLocationInfo.saveButton";
  public static final String ADMIN_LEDGER_CONTENT = "AdminPanel.financialLedger.content";
  public static final String ADMIN_LEDGER_TABLE = "AdminPanel.financialLedger.table";
  public static final String ADMIN_ACTIVITY_CONTENT = "AdminPanel.activity.content";
  public static final String ADMIN_ACTIVITY_TABLE = "AdminPanel.activity.table";

  // ── EditAlbumCard ───────────────────────────────────────────────────────────
  public static final String EDIT_ALBUM_HEADER_LABEL = "EditAlbumCard.headerLabel";
  public static final String EDIT_ALBUM_PREV_ALBUM_BUTTON = "EditAlbumCard.prevAlbumButton";
  public static final String EDIT_ALBUM_NEXT_ALBUM_BUTTON = "EditAlbumCard.nextAlbumButton";
  public static final String EDIT_ALBUM_SEARCH_ALBUM_FIELD = "EditAlbumCard.searchAlbumField";
  public static final String EDIT_ALBUM_CANCEL_BUTTON = "EditAlbumCard.cancelButton";
}
