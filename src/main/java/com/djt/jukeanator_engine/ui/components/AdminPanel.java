package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;
import com.djt.jukeanator_engine.domain.license.service.LicenseService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanCancelledException;
import com.djt.jukeanator_engine.domain.songlibrary.service.ScanProgressMonitor;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.UpdateLocationInfoRequest;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationStatus;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;
import com.djt.jukeanator_engine.ui.model.CreditManager;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

public class AdminPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  /**
   * Fixed size for every sidebar button — narrow enough to leave the lists dominant, tall enough to
   * be touch-friendly and readable. The width is intentionally capped rather than Integer.MAX_VALUE
   * so the sidebar columns stay anchored.
   */
  private static final Dimension BTN_SIZE =
      new Dimension(LayoutTheme.get().adminBtnW, LayoutTheme.get().adminBtnH);

  /** How far a pressed sidebar button sinks toward its 3 px drop shadow. */
  private static final int SIDE_BTN_PRESS_DROP = 2;

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;
  private final UserService userService;
  private final LocationService locationService;
  private final FinancialLedgerService financialLedgerService;
  private final UserActivityService userActivityService;
  private final CreditManager creditManager;

  // Licensing (set after construction; null when no license service exists)
  private LicenseService licenseService;
  private Runnable showRegistration;
  private final Frame ownerFrame;
  private final ImageLoader imageLoader;
  private final List<String> splitSummaryRecipients; // email.financial-ledger-recipients
  private final String dataDir; // e.g. C:\kiosk\data

  // ── Album list ────────────────────────────────────────────────────────────
  private final DefaultListModel<AlbumDto> albumListModel = new DefaultListModel<>();
  private final JList<AlbumDto> albumList = new JList<>(albumListModel);

  // ── Queue list ────────────────────────────────────────────────────────────
  private final DefaultListModel<SongQueueEntryDto> queueListModel = new DefaultListModel<>();
  private final JList<SongQueueEntryDto> queueList = new JList<>(queueListModel);

  // ── Popularity thresholds (passed through to the queue cell renderer) ─────
  private int popularityT1 = 1;
  private int popularityT2 = 5;
  private int popularityT3 = 15;

  // ── Song queue lock state UI ──────────────────────────────────────────────
  // Set once by buildLibraryButtons() during construction, then toggled by
  // doLockQueue()/doUnlockQueue() and initialized from songQueueService.isLocked().
  private JButton lockQueueButton;
  private JButton unlockQueueButton;
  private final JLabel queueLockedLabel = buildQueueLockedLabel();

  // ── Invalid Metadata Tracking Cache ──────────────────────────────────────
  private final List<AlbumDto> albumsWithInvalidMetadata = new ArrayList<>();

  // ── In-place modal overlay ────────────────────────────────────────────────
  /**
   * A modal-style notice/confirm card painted on top of the panel's own content instead of in a
   * separate top-level window. While {@link JukeANatorFrame#showFullscreen()} holds true full-screen
   * exclusive mode, Windows does not reliably hand keyboard focus to a newly created window, so a
   * dialog in its own window would appear unfocused and force the user to Alt+Tab back to it.
   * Painting the notice inside the existing window (via {@link JLayeredPane}) avoids that entirely.
   * {@link #overlayFormCard} below reuses the same trick for the admin panel's data-entry forms
   * (Add Admin User, Location), which used to be separate {@code JDialog}
   * windows and suffered exactly this focus loss.
   */
  private Color overlayCardAccent = Color.WHITE;
  private final JPanel overlayCard = newRoundedOverlayCard();
  private final JPanel overlayFormCard = newRoundedOverlayCard();
  private final JPanel overlayScrim = new JPanel();
  private final JLabel overlayTitleLabel = new JLabel();
  private final JLabel overlayMessageLabel = new JLabel();
  private final JButton overlayPrimaryBtn = overlayButton("");
  private final JButton overlaySecondaryBtn = overlayButton("");
  /** Third choice (Cancel) -- only shown by {@link #showOverlayChoice}. */
  private final JButton overlayTertiaryBtn = overlayButton("");

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AdminPanel(Frame ownerFrame, SongLibraryService songLibraryService,
      SongQueueService songQueueService, SongPlayerService songPlayerService,
      UserService userService, LocationService locationService,
      FinancialLedgerService financialLedgerService, UserActivityService userActivityService,
      CreditManager creditManager, ImageLoader imageLoader, List<String> splitSummaryRecipients,
      String dataDir) {

    this.ownerFrame = ownerFrame;
    this.dataDir = dataDir;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    this.userService = userService;
    this.locationService = locationService;
    this.financialLedgerService = financialLedgerService;
    this.userActivityService = userActivityService;
    this.creditManager = creditManager;
    this.imageLoader = imageLoader;
    this.splitSummaryRecipients = List.copyOf(splitSummaryRecipients);

    initOverlay();

    JPanel contentPanel = new JPanel(new BorderLayout(0, 0));
    contentPanel.setOpaque(false);
    contentPanel.add(buildLibraryButtons(), BorderLayout.WEST);
    contentPanel.add(buildListsCenter(), BorderLayout.CENTER);
    contentPanel.add(buildQueueButtons(), BorderLayout.EAST);

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);
    add(buildOverlayHost(contentPanel), BorderLayout.CENTER);

    loadAlbumList();
    setQueue(songQueueService.getQueuedSongs(songQueueService.getOwnLocationId()));
    updateQueueLockUi(songQueueService.isLocked());

    requestFocusInWindow();
  }

  /**
   * Stacks {@code contentPanel} and {@link #overlayScrim} in a {@link JLayeredPane} so the overlay
   * can be painted on top without a separate window. Both children are kept sized to fill the
   * layered pane, since {@code JLayeredPane} otherwise has no layout manager of its own.
   */
  private JLayeredPane buildOverlayHost(JPanel contentPanel) {

    JLayeredPane layeredPane = new JLayeredPane();
    layeredPane.setLayout(null);
    layeredPane.add(contentPanel, JLayeredPane.DEFAULT_LAYER);
    layeredPane.add(overlayScrim, JLayeredPane.MODAL_LAYER);

    layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentResized(java.awt.event.ComponentEvent e) {
        contentPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        overlayScrim.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
      }
    });
    return layeredPane;
  }

  /**
   * A rounded-rect card styled with {@link #overlayCardAccent}, shared by {@link #overlayCard} and
   * {@link #overlayFormCard} -- only one of the two is ever visible at a time, so both can safely
   * read the same accent field.
   */
  private JPanel newRoundedOverlayCard() {
    return new JPanel() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ColorTheme.get().bgAdminHeader);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.setColor(overlayCardAccent);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.dispose();
      }
    };
  }

  /** Builds the (initially hidden) scrim + card chrome reused by every overlay message/confirm. */
  private void initOverlay() {

    overlayScrim.setLayout(new GridBagLayout());
    overlayScrim.setOpaque(false);
    overlayScrim.setVisible(false);
    overlayScrim.setName(UiComponentNames.ADMIN_OVERLAY_SCRIM);
    overlayCard.setName(UiComponentNames.ADMIN_OVERLAY_CARD);
    overlayFormCard.setName(UiComponentNames.ADMIN_OVERLAY_FORM_CARD);
    overlayTitleLabel.setName(UiComponentNames.ADMIN_OVERLAY_TITLE_LABEL);
    overlayMessageLabel.setName(UiComponentNames.ADMIN_OVERLAY_MESSAGE_LABEL);
    overlayPrimaryBtn.setName(UiComponentNames.ADMIN_OVERLAY_PRIMARY_BUTTON);
    overlaySecondaryBtn.setName(UiComponentNames.ADMIN_OVERLAY_SECONDARY_BUTTON);
    overlayTertiaryBtn.setName(UiComponentNames.ADMIN_OVERLAY_TERTIARY_BUTTON);

    overlayCard.setOpaque(false);
    overlayCard.setLayout(new BoxLayout(overlayCard, BoxLayout.Y_AXIS));
    overlayCard.setBorder(new EmptyBorder(20, 28, 18, 28));

    overlayFormCard.setOpaque(false);
    overlayFormCard.setLayout(new BorderLayout(0, 16));
    overlayFormCard.setBorder(new EmptyBorder(20, 24, 16, 24));
    overlayFormCard.setVisible(false);

    overlayTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
    overlayTitleLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

    overlayMessageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
    overlayMessageLabel.setForeground(ColorTheme.get().textPrimary);
    overlayMessageLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

    JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
    buttonRow.setOpaque(false);
    buttonRow.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
    buttonRow.add(overlayPrimaryBtn);
    buttonRow.add(overlaySecondaryBtn);
    buttonRow.add(overlayTertiaryBtn);

    overlayCard.add(overlayTitleLabel);
    overlayCard.add(Box.createVerticalStrut(10));
    overlayCard.add(overlayMessageLabel);
    overlayCard.add(Box.createVerticalStrut(18));
    overlayCard.add(buttonRow);

    overlayScrim.add(overlayCard, new GridBagConstraints());
    overlayScrim.add(overlayFormCard, new GridBagConstraints());
  }

  /**
   * Creates a button for the in-window overlay cards: blue face with the shared hover and pressed
   * look (see {@link ButtonFactory#createTextButton}). Fires through its {@code ActionListener} on
   * release, like every user-screen button, so it works with the touchscreen's Mouse Emulation mode.
   */
  private static JButton overlayButton(String text) {
    return ButtonFactory.createTextButton(text,
        new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSideBtn2));
  }

  /** Shows an OK-only overlay notice. */
  private void showOverlayMessage(String title, String message, Color accent) {
    showOverlay(title, message, accent, "OK", null, null, null, null);
  }

  /** Shows a Yes/No overlay, running {@code onConfirm} only if the user picks Yes. */
  private void showOverlayConfirm(String title, String message, Color accent, Runnable onConfirm) {
    showOverlay(title, message, accent, "Yes", onConfirm, "No", null, null);
  }

  /**
   * Shows a three-way overlay: two actions plus Cancel. The chosen action runs after the overlay
   * closes; Cancel only closes it.
   */
  private void showOverlayChoice(String title, String message, Color accent, String primaryText,
      Runnable onPrimary, String secondaryText, Runnable onSecondary) {
    showOverlay(title, message, accent, primaryText, onPrimary, secondaryText, onSecondary,
        "Cancel");
  }

  /**
   * Shows the shared overlay card with up to three buttons, left to right. A {@code null} button
   * text hides that button. Every button closes the overlay, then runs its action if it has one.
   * The rightmost visible button -- the one that does nothing (OK / No / Cancel) -- gets the focus,
   * so a stray Enter never starts an action.
   */
  private void showOverlay(String title, String message, Color accent,
      String primaryText, Runnable onPrimary,
      String secondaryText, Runnable onSecondary,
      String tertiaryText) {

    overlayCardAccent = accent;
    overlayTitleLabel.setText(title);
    overlayTitleLabel.setForeground(accent);
    overlayMessageLabel.setText(htmlWrap(message));

    configureOverlayButton(overlayPrimaryBtn, primaryText, onPrimary);
    configureOverlayButton(overlaySecondaryBtn, secondaryText, onSecondary);
    configureOverlayButton(overlayTertiaryBtn, tertiaryText, null);

    overlayFormCard.setVisible(false);
    overlayCard.setVisible(true);
    overlayScrim.setVisible(true);
    overlayCard.repaint();
    overlayScrim.revalidate();
    JButton toFocus = tertiaryText != null ? overlayTertiaryBtn
        : secondaryText != null ? overlaySecondaryBtn : overlayPrimaryBtn;
    SwingUtilities.invokeLater(toFocus::requestFocusInWindow);
  }

  private void configureOverlayButton(JButton button, String text, Runnable action) {

    for (java.awt.event.ActionListener al : button.getActionListeners()) {
      button.removeActionListener(al);
    }
    button.setVisible(text != null);
    if (text == null) {
      return;
    }
    button.setText(text);
    button.addActionListener(e -> {
      hideOverlay();
      if (action != null) {
        action.run();
      }
    });
  }

  /**
   * Shows {@code content} in the same in-window modal scrim as {@link #showOverlayMessage}/
   * {@link #showOverlayConfirm}, instead of a separate {@code JDialog} window -- see the
   * {@link #overlayCard} javadoc for why a real top-level window loses keyboard focus while
   * {@link JukeANatorFrame#showFullscreen()} holds full-screen exclusive mode on Windows.
   */
  private void showOverlayForm(Color accent, JComponent content) {

    overlayCardAccent = accent;
    overlayFormCard.removeAll();
    overlayFormCard.add(content, BorderLayout.CENTER);

    overlayCard.setVisible(false);
    overlayFormCard.setVisible(true);
    overlayScrim.setVisible(true);
    overlayFormCard.revalidate();
    overlayFormCard.repaint();
    overlayScrim.revalidate();
  }

  private void hideOverlay() {
    overlayScrim.setVisible(false);
  }

  private static String htmlWrap(String message) {
    String escaped = message == null ? ""
        : message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    return "<html><body style='width: 260px; text-align:center;'>" + escaped + "</body></html>";
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CENTER — side-by-side lists
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildListsCenter() {

    JPanel center = new JPanel(new GridLayout(1, 2, 6, 0));
    center.setOpaque(false);
    center.setBorder(new EmptyBorder(6, 0, 6, 0));

    // ── Album list ────────────────────────────────────────────────────────
    albumList.setName(UiComponentNames.ADMIN_ALBUM_LIST);
    albumList.setOpaque(true);
    albumList.setBackground(ColorTheme.get().bgList);
    albumList.setForeground(ColorTheme.get().textPrimary);
    albumList.setSelectionBackground(ColorTheme.get().bgListSelected);
    albumList.setSelectionForeground(ColorTheme.get().textPrimary);
    albumList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
    albumList.setFixedCellHeight(LayoutTheme.get().adminAlbumCellH);
    albumList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    albumList.setCellRenderer(new AlbumCellRenderer());

    JPanel albumPane = new JPanel(new BorderLayout(0, 4));
    albumPane.setOpaque(false);
    albumPane.add(buildAlbumSectionHeader(), BorderLayout.NORTH);

    albumPane.add(darkScrollPane(albumList), BorderLayout.CENTER);

    // ── Queue list ────────────────────────────────────────────────────────
    queueList.setName(UiComponentNames.ADMIN_QUEUE_LIST);
    queueList.setOpaque(true);
    queueList.setBackground(ColorTheme.get().bgList);
    queueList.setForeground(ColorTheme.get().textPrimary);
    queueList.setSelectionBackground(ColorTheme.get().bgListSelected);
    queueList.setSelectionForeground(ColorTheme.get().textPrimary);
    queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    SongTrackCellRenderer.installForAdmin(queueList, popularityT1, popularityT2, popularityT3,
        imageLoader);

    JPanel queuePane = new JPanel(new BorderLayout(0, 4));
    queuePane.setOpaque(false);
    queuePane.add(buildQueueSectionHeader(), BorderLayout.NORTH);
    queuePane.add(darkScrollPane(queueList), BorderLayout.CENTER);

    center.add(albumPane);
    center.add(queuePane);
    return center;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // WEST — library action buttons (operate on selected album)
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildLibraryButtons() {

    JPanel strip = buildButtonStrip();

    strip.add(sideButton(UiComponentNames.ADMIN_FINANCIAL_LEDGER_BUTTON, "Financial\nLedger", ColorTheme.get().accentGold,
        e -> doFinancialLedger()));
    strip.add(sideButton(UiComponentNames.ADMIN_VIEW_ACTIVITY_BUTTON, "View\nActivity", ColorTheme.get().accentGold, e -> doViewActivity()));

    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_QUEUE_ALBUM_BUTTON, "Queue\nAlbum", ColorTheme.get().accentGreen, e -> doAddAlbumToQueue()));
    lockQueueButton = sideButton(UiComponentNames.ADMIN_LOCK_QUEUE_BUTTON, "Lock\nQueue", ColorTheme.get().accentGreen, e -> doLockQueue());
    unlockQueueButton =
        sideButton(UiComponentNames.ADMIN_UNLOCK_QUEUE_BUTTON, "Unlock\nQueue", ColorTheme.get().accentGreen, e -> doUnlockQueue());
    strip.add(lockQueueButton);
    strip.add(unlockQueueButton);
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_EDIT_ALBUM_BUTTON, "Edit\nAlbum", ColorTheme.get().accentGold, e -> doEditAlbum()));
    strip.add(sideButton(UiComponentNames.ADMIN_RESET_STATS_BUTTON, "Reset\nStats", ColorTheme.get().accentOrange, e -> doResetStats()));
    strip.add(sideButton(UiComponentNames.ADMIN_RESCAN_LIBRARY_BUTTON, "Rescan\nLibrary", ColorTheme.get().accentViolet, e -> doRescan()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_ADD_ADMIN_USER_BUTTON, "Add Admin\nUser", ColorTheme.get().accentGold, e -> doAddAdminUser()));
    strip.add(sideButton(UiComponentNames.ADMIN_LOCATION_BUTTON, "Location", ColorTheme.get().accentGold, e -> doLocation()));
    strip.add(sideButton(UiComponentNames.ADMIN_LICENSE_BUTTON, "License", ColorTheme.get().accentGold, e -> doLicense()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_MINIMIZE_BUTTON, "⊟ Minimize", ColorTheme.get().accentBlue, e -> doMinimize()));
    strip.add(sideButton(UiComponentNames.ADMIN_EXIT_BUTTON, "✕ Exit", ColorTheme.get().accentRed, e -> doExit()));

    // Wrap so the strip itself is opaque-background-free but has a right border separator
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 0, 1, ColorTheme.get().colorAdminSeparator),
        new EmptyBorder(6, 6, 6, 6)));
    wrapper.add(strip, BorderLayout.CENTER);
    return wrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // EAST — queue action buttons (operate on selected queue entry)
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildQueueButtons() {

    JPanel strip = buildButtonStrip();
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_NEXT_BUTTON, "▶▶\nNext", ColorTheme.get().accentGreen, e -> doPlayNextTrack()));
    strip.add(sideButton(UiComponentNames.ADMIN_PAUSE_BUTTON, "❚❚\nPause", ColorTheme.get().accentBlue, e -> doPause()));
    strip.add(sideButton(UiComponentNames.ADMIN_PLAY_BUTTON, "▶\nPlay", ColorTheme.get().accentGreen, e -> doPlaySelected()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_MOVE_UP_BUTTON, "▲\nMove Up", ColorTheme.get().accentBlue, e -> doMoveUp()));
    strip.add(sideButton(UiComponentNames.ADMIN_MOVE_DOWN_BUTTON, "▼\nMove Dn", ColorTheme.get().accentBlue, e -> doMoveDown()));
    strip.add(sideButton(UiComponentNames.ADMIN_REMOVE_BUTTON, "✕\nRemove", ColorTheme.get().accentRed, e -> doRemoveSong()));
    strip.add(verticalSpacer(20));
    strip.add(sideButton(UiComponentNames.ADMIN_FLUSH_BUTTON, "🗑\nFlush", ColorTheme.get().accentRed, e -> doFlushQueue()));
    strip.add(sideButton(UiComponentNames.ADMIN_SHUFFLE_BUTTON, "🔀\nShuffle", ColorTheme.get().accentViolet, e -> doRandomizeQueue()));
    strip.add(verticalSpacer(20));
    strip.add(sideButton(UiComponentNames.ADMIN_LOAD_PLAYLIST_BUTTON, "📂\nLoad Playlist", ColorTheme.get().accentGold, e -> doLoadPlaylist()));
    strip.add(sideButton(UiComponentNames.ADMIN_SAVE_PLAYLIST_BUTTON, "💾\nSave Playlist", ColorTheme.get().accentGold, e -> doSavePlaylist()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton(UiComponentNames.ADMIN_INCREMENT_CREDITS_BUTTON, "➕\nCredits", ColorTheme.get().accentGreen, e -> doIncrementCredits()));
    strip.add(sideButton(UiComponentNames.ADMIN_DECREMENT_CREDITS_BUTTON, "➖\nCredits", ColorTheme.get().accentOrange, e -> doDecrementCredits()));

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 1, 0, 0, ColorTheme.get().colorAdminSeparator),
        new EmptyBorder(6, 6, 6, 6)));
    wrapper.add(strip, BorderLayout.CENTER);
    return wrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ALBUM ACTIONS (SongLibraryService)
  // ─────────────────────────────────────────────────────────────────────────
  private void doAddAlbumToQueue() {
    AlbumDto selected = albumList.getSelectedValue();

    if (selected == null) {
      showOverlayMessage("No Selection", "Please select an album first.",
          ColorTheme.get().accentOrange);
      return;
    }

    // Capture the ID safely
    final Integer albumId = selected.albumId();

    SwingSecurityUtil.runAsync(() -> {
      try {
        // 1. Fetch full album entity context in the background
        AlbumDto full = songLibraryService.getAlbumById(songLibraryService.getOwnLocationId(), albumId);

        // 2. Submit to the queue engine (Fires events, updates data models)
        songQueueService.addAlbumToQueue(songQueueService.getOwnLocationId(),
            new AddAlbumToQueueRequest(SongQueueService.LOCAL_USERNAME, full.albumId(), 1));

        // 3. Explicitly request the fresh queue list from the service layer
        // WHILE STILL on the background thread.
        var freshQueue = songQueueService.getQueuedSongs(songQueueService.getOwnLocationId());

        // 4. Safely push the isolated DTO snapshot to the Swing EDT
        SwingUtilities.invokeLater(() -> refreshQueueList(freshQueue));

      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
  }

  private void doLockQueue() {
    try {
      songQueueService.lock();
      updateQueueLockUi(true);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doUnlockQueue() {
    try {
      songQueueService.unlock();
      updateQueueLockUi(false);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Reflects the song queue's lock state in the sidebar buttons (the button for the action that is
   * NOT currently available is greyed out/disabled) and toggles the "LOCKED" indicator next to the
   * "Queue:" section header.
   */
  private void updateQueueLockUi(boolean locked) {
    lockQueueButton.setEnabled(!locked);
    unlockQueueButton.setEnabled(locked);
    lockQueueButton.repaint();
    unlockQueueButton.repaint();
    queueLockedLabel.setVisible(locked);
  }

  /** Hidden by default -- shown next to the "Queue:" header while the queue is locked. */
  private static JLabel buildQueueLockedLabel() {
    JLabel label = new JLabel("🔒");
    label.setForeground(ColorTheme.get().accentYellow);
    label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
    label.setVisible(false);
    return label;
  }

  private void doEditAlbum() {

    AlbumDto selected = albumList.getSelectedValue();
    if (selected == null) {
      if (!albumsWithInvalidMetadata.isEmpty()) {
        selected = albumsWithInvalidMetadata.getFirst();
      } else {
        showOverlayMessage("No Selection", "Please select an album first.",
            ColorTheme.get().accentOrange);
        return;
      }
    }

    if (ownerFrame instanceof JukeANatorFrame frame) {
      frame.showEditAlbumCard(selected, albumsWithInvalidMetadata);
    }
  }

  private void doResetStats() {

    showOverlayConfirm("Reset Statistics", "Reset all song play statistics?",
        ColorTheme.get().accentOrange, () -> SwingSecurityUtil.runAsync(() -> {
          try {
            songLibraryService.resetSongStatistics();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }));
  }

  /**
   * A {@link JFileChooser} whose dialog, on Windows, stays always-on-top and is raised and focused
   * once shown. On first use the scan dialog appears during startup, when Windows may still be
   * keeping the launching command prompt in the foreground; without this the dialog can open hidden
   * behind that console. It also makes sure the dialog (a separate window, opened over the
   * full-screen frame) receives the physical keyboard. Playlist load/save do not use it: they
   * embed their chooser in the in-window overlay instead (see showPlaylistChooser).
   * Skipped on Linux/macOS (see ForegroundWindowUtil#isApplicable()), which get the plain dialog.
   */
  private static JFileChooser newForegroundFileChooser() {
    return new JFileChooser() {
      private static final long serialVersionUID = 1L;

      @Override
      protected JDialog createDialog(Component parent) throws HeadlessException {

        JDialog dialog = super.createDialog(parent);
        if (!ForegroundWindowUtil.isApplicable()) {
          return dialog;
        }

        dialog.setAlwaysOnTop(true);
        dialog.addWindowListener(new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            dialog.toFront();
            ForegroundWindowUtil.forceToForeground(dialog);
            dialog.requestFocus();
          }
        });
        return dialog;
      }
    };
  }

  /**
   * Opens a directory chooser so the operator can pick the file-system folder to scan for music,
   * then kicks off a scan against the selected path. Invoked automatically at startup (via
   * {@link JukeANatorFrame#promptForInitialLibraryScan()}) when there is no usable song library, so
   * the operator can point the app at a music folder on first use, and from Rescan Library's
   * "Scan New Folder" choice.
   *
   * @param firstTimeUse true when there is no song library yet; cancelling either the directory
   *        chooser or the scan then exits the application instead of returning to the Admin panel
   */
  public void showScanFileSystemDialog(boolean firstTimeUse) {

    JFileChooser chooser = newForegroundFileChooser();
    chooser.setDialogTitle("Select Music Folder to Scan");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);

    if (chooser.showDialog(this, "Scan") != JFileChooser.APPROVE_OPTION) {
      // With no library to fall back on, there is nothing useful to do without a music folder.
      if (firstTimeUse) {
        System.exit(0);
      }
      return;
    }

    String scanPath = chooser.getSelectedFile().getAbsolutePath();

    startScan(scanPath, firstTimeUse);
  }

  /**
   * Scans the given folder with the in-window progress card, without the directory chooser.
   *
   * @param firstTimeUse true when there is no song library yet; cancelling the scan then exits the
   *        application instead of returning to the Admin panel
   */
  public void startScan(String scanPath, boolean firstTimeUse) {

    new ScanProgressForm(scanPath, firstTimeUse).start();
  }

  /**
   * Asks whether to re-scan the current music folder or pick a new one. With no music folder
   * scanned yet there is nothing to re-scan, so it goes straight to the folder chooser.
   */
  private void doRescan() {

    String currentFolder = songLibraryService.getOwnLibraryRootPath();

    if (currentFolder == null || currentFolder.isBlank()) {
      showScanFileSystemDialog(false);
      return;
    }

    showOverlayChoice("Rescan Library",
        "Re-scan the current music folder (" + currentFolder + "), or scan a new folder? "
            + "This may take a while.",
        ColorTheme.get().accentViolet,
        "Re-scan Current Folder", () -> new ScanProgressForm(null, false).start(),
        "Scan New Folder", () -> showScanFileSystemDialog(false));
  }

  /**
   * In-window progress card shown for the whole of a file-system scan, which can run from minutes
   * to hours depending on library size and how many albums need an internet lookup. Shows an
   * indeterminate progress bar (the tree is walked recursively, so there's no cheap up-front total)
   * plus running album/song counts, and offers:
   * <ul>
   * <li><b>Stop</b> -- end the scan early but keep every album found so far (see
   * {@link ScanProgressMonitor}).</li>
   * <li><b>Cancel</b> -- discard the scan entirely, leaving the existing library untouched; on
   * first-time use (no library at all) this exits the application, since the operator presumably
   * needs to put music on the file system before trying again.</li>
   * </ul>
   * Shown via {@link #showOverlayForm} rather than a separate {@code JDialog} window -- see
   * {@link #overlayCard}.
   */
  private class ScanProgressForm {

    private static final int REFRESH_INTERVAL_MILLIS = 200;
    private static final int MAX_CURRENT_ITEM_CHARS = 70;

    /** null means rescan this instance's current library root. */
    private final String scanPath;
    private final boolean firstTimeUse;

    // Written by the scan thread, read by the EDT timer below -- discovery can report thousands
    // of updates per second, so only the latest snapshot is kept and painted a few times a second.
    private final AtomicReference<ScanProgressMonitor.Progress> latestProgress =
        new AtomicReference<>();
    private final ScanProgressMonitor monitor = new ScanProgressMonitor(latestProgress::set);
    private final javax.swing.Timer refreshTimer =
        new javax.swing.Timer(REFRESH_INTERVAL_MILLIS, e -> refresh());

    private final JLabel statusLabel = new JLabel("Starting scan...");
    private final JLabel albumsLabel = new JLabel();
    private final JLabel songsLabel = new JLabel();
    private final JLabel currentItemLabel = new JLabel(" ");
    private final JButton stopBtn = overlayButton("Stop");
    private final JButton cancelBtn = overlayButton("Cancel");
    private final JPanel content = new JPanel(new BorderLayout(0, 16));

    ScanProgressForm(String scanPath, boolean firstTimeUse) {

      this.scanPath = scanPath;
      this.firstTimeUse = firstTimeUse;

      content.setOpaque(false);

      JLabel title = new JLabel(firstTimeUse ? "Building Song Library" : "Scanning Song Library");
      title.setForeground(ColorTheme.get().accentViolet);
      title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
      content.add(title, BorderLayout.NORTH);

      content.add(buildBody(), BorderLayout.CENTER);
      content.add(buildButtonRow(), BorderLayout.SOUTH);

      updateCounts(0, 0);
    }

    void start() {

      showOverlayForm(ColorTheme.get().accentViolet, content);
      refreshTimer.start();

      // This card is painted inside the frame, not in a window of its own. The directory chooser
      // that precedes it is a separate, forced-to-the-foreground window (see
      // showScanFileSystemDialog), and once it closes Windows does not reliably hand the
      // foreground back to the full-screen frame -- on first use it can fall back to the console
      // that launched the app, leaving this card hidden. Re-raise the frame so it is visible.
      raiseFrame();

      SwingSecurityUtil.runAsync(() -> {
        try {
          Integer albumCount = scanPath == null
              ? songLibraryService.scanFileSystemForSongs(monitor)
              : songLibraryService.scanFileSystemForSongs(new ScanRequest(scanPath), monitor);
          SwingUtilities.invokeLater(() -> onScanSucceeded(albumCount));

        } catch (SongScanCancelledException ex) {
          SwingUtilities.invokeLater(this::onScanCancelled);

        } catch (Exception ex) {
          ex.printStackTrace();
          SwingUtilities.invokeLater(() -> {
            refreshTimer.stop();
            hideOverlay();
            showOverlayMessage("Scan Failed", "Could not scan: " + ex.getMessage(),
                ColorTheme.get().accentRed);
          });
        }
      });
    }

    private JPanel buildBody() {

      JPanel body = new JPanel();
      body.setOpaque(false);
      body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

      Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum);
      Font countFont = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist);

      if (scanPath != null) {
        body.add(leftAligned(newLabel("Folder: " + abbreviate(scanPath), labelFont,
            ColorTheme.get().textSecondary)));
        body.add(Box.createVerticalStrut(10));
      }

      styleLabel(statusLabel, labelFont, ColorTheme.get().textPrimary);
      body.add(leftAligned(statusLabel));
      body.add(Box.createVerticalStrut(10));

      JProgressBar progressBar = new JProgressBar();
      progressBar.setIndeterminate(true);
      progressBar.setForeground(ColorTheme.get().accentViolet);
      progressBar.setBackground(ColorTheme.get().bgFieldDark);
      progressBar.setBorder(BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1));
      progressBar.setPreferredSize(new Dimension(480, 16));
      progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
      body.add(leftAligned(progressBar));
      body.add(Box.createVerticalStrut(12));

      styleLabel(albumsLabel, countFont, ColorTheme.get().textPrimary);
      styleLabel(songsLabel, countFont, ColorTheme.get().textPrimary);
      body.add(leftAligned(albumsLabel));
      body.add(Box.createVerticalStrut(4));
      body.add(leftAligned(songsLabel));
      body.add(Box.createVerticalStrut(10));

      styleLabel(currentItemLabel, labelFont, ColorTheme.get().textSecondary);
      body.add(leftAligned(currentItemLabel));
      body.add(Box.createVerticalStrut(14));

      String hint = firstTimeUse
          ? "Stop keeps the songs scanned so far. Cancel discards the scan and exits JukeANator."
          : "Stop keeps the songs scanned so far. Cancel discards the scan and keeps the current library.";
      body.add(leftAligned(newLabel(hint, labelFont, ColorTheme.get().textSecondary)));

      return body;
    }

    private JPanel buildButtonRow() {

      stopBtn.addActionListener(e -> {
        monitor.requestStop();
        stopBtn.setEnabled(false);
        refresh();
      });

      cancelBtn.addActionListener(e -> {
        monitor.requestCancel();
        stopBtn.setEnabled(false);
        cancelBtn.setEnabled(false);
        refresh();
      });

      JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
      row.setOpaque(false);
      row.add(stopBtn);
      row.add(cancelBtn);
      return row;
    }

    /** Repaints the card from the latest snapshot; runs on the EDT via {@link #refreshTimer}. */
    private void refresh() {

      ScanProgressMonitor.Progress progress = latestProgress.get();

      if (monitor.isCancelRequested()) {
        statusLabel.setText("Cancelling... (waiting for the current step to finish)");
      }

      if (progress == null) {
        return;
      }

      updateCounts(progress.albumsFound(), progress.songsFound());
      currentItemLabel.setText(abbreviate(progress.currentItem()));

      if (progress.phase() == ScanProgressMonitor.Phase.SAVING) {
        statusLabel.setText("Saving the song library...");
        stopBtn.setEnabled(false);
        cancelBtn.setEnabled(false);
        return;
      }

      if (monitor.isCancelRequested()) {
        return;
      }

      String albumsOfTotal = String.format("%,d of %,d albums", progress.albumsProcessed(),
          progress.albumsFound());

      if (monitor.isStopRequested()) {
        statusLabel.setText(progress.phase() == ScanProgressMonitor.Phase.PROCESSING
            ? "Stopping... finishing up the albums found so far (" + albumsOfTotal + ")"
            : "Stopping... finishing up the albums found so far");
      } else if (progress.phase() == ScanProgressMonitor.Phase.DISCOVERING) {
        statusLabel.setText("Scanning folders for songs...");
      } else {
        statusLabel.setText("Reading tags and looking up cover art / album info ("
            + albumsOfTotal + ")");
      }
    }

    private void onScanSucceeded(Integer albumCount) {

      refreshTimer.stop();
      hideOverlay();
      refreshAlbumList();

      ScanProgressMonitor.Progress progress = latestProgress.get();
      int songCount = progress != null ? progress.songsFound() : 0;
      String counts = String.format("%,d albums (%,d songs)",
          albumCount != null ? albumCount : 0, songCount);

      if (monitor.isCancelRequested()) {
        showOverlayMessage("Scan Complete",
            "The scan finished saving before it could be cancelled. Loaded " + counts + ".",
            ColorTheme.get().accentOrange);
      } else if (monitor.isStopRequested()) {
        showOverlayMessage("Scan Stopped", "Kept the " + counts + " found before the scan was "
            + "stopped. Cover art and album info that were not looked up can be filled in by a "
            + "later rescan.", ColorTheme.get().accentOrange);
      } else {
        showOverlayMessage("Scan Complete", "Loaded " + counts + ".",
            ColorTheme.get().accentGreen);
      }

      // A first-time scan can run for hours, during which the operator may well have switched to
      // another window; make sure the result (and the now-loaded jukebox) is what they come back to.
      if (firstTimeUse) {
        raiseFrame();
      }
    }

    private void raiseFrame() {
      if (ownerFrame instanceof JukeANatorFrame frame) {
        SwingUtilities.invokeLater(frame::bringToFront);
      }
    }

    private void onScanCancelled() {

      refreshTimer.stop();

      if (firstTimeUse) {
        System.exit(0);
        return;
      }

      hideOverlay();
      showOverlayMessage("Scan Cancelled", "No changes were made to the song library.",
          ColorTheme.get().accentOrange);
    }

    private void updateCounts(int albums, int songs) {
      albumsLabel.setText(String.format("Albums scanned:  %,d", albums));
      songsLabel.setText(String.format("Songs scanned:  %,d", songs));
    }

    /** Keeps the end of long folder paths, which is the part that changes. */
    private String abbreviate(String text) {
      if (text == null || text.isBlank()) {
        return " ";
      }
      return text.length() <= MAX_CURRENT_ITEM_CHARS ? text
          : "..." + text.substring(text.length() - MAX_CURRENT_ITEM_CHARS + 3);
    }

    private JLabel newLabel(String text, Font font, Color color) {
      JLabel label = new JLabel(text);
      styleLabel(label, font, color);
      return label;
    }

    private void styleLabel(JLabel label, Font font, Color color) {
      label.setFont(font);
      label.setForeground(color);
    }

    private JComponent leftAligned(JComponent component) {
      component.setAlignmentX(Component.LEFT_ALIGNMENT);
      return component;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ADMIN USER ACTIONS (UserService)
  // ─────────────────────────────────────────────────────────────────────────
  private void doAddAdminUser() {
    new AddAdminUserForm().show();
  }

  /**
   * Custom (non-{@code JOptionPane}) modal prompt collecting the fields for a
   * {@link RegisterRequest}, then calling {@link UserService#addAdminUser(RegisterRequest)} with
   * {@code ROLE_ADMIN}. Shown via {@link #showOverlayForm} rather than a separate {@code JDialog}
   * window -- see {@link #overlayCard}.
   */
  private class AddAdminUserForm {

    private final JTextField firstNameField = new JTextField(18);
    private final JTextField lastNameField = new JTextField(18);
    private final JTextField emailField = new JTextField(18);
    /** Same footprint as the other (identically styled, 18-column) text fields, icon included. */
    private final PeekPasswordField passwordField = new PeekPasswordField(18) {
      private static final long serialVersionUID = 1L;

      @Override
      public java.awt.Dimension getPreferredSize() {
        return firstNameField.getPreferredSize();
      }
    };
    private final JLabel errorLabel = new JLabel(" ");
    private final JPanel content = new JPanel(new BorderLayout(0, 16));

    AddAdminUserForm() {

      firstNameField.setName(UiComponentNames.ADMIN_USER_FIRST_NAME_FIELD);
      lastNameField.setName(UiComponentNames.ADMIN_USER_LAST_NAME_FIELD);
      emailField.setName(UiComponentNames.ADMIN_USER_EMAIL_FIELD);
      passwordField.setName(UiComponentNames.ADMIN_USER_PASSWORD_FIELD);
      errorLabel.setName(UiComponentNames.ADMIN_USER_ERROR_LABEL);
      content.setOpaque(false);

      JLabel title = new JLabel("Create Admin User");
      title.setForeground(ColorTheme.get().accentGold);
      title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
      content.add(title, BorderLayout.NORTH);

      content.add(buildFieldsPanel(), BorderLayout.CENTER);
      content.add(buildButtonRow(), BorderLayout.SOUTH);

      content.registerKeyboardAction(e -> hideOverlay(),
          javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_IN_FOCUSED_WINDOW);

      // Physical keyboard: Enter in any field submits, like the Create Admin button.
      for (JTextField field : new JTextField[] {firstNameField, lastNameField, emailField,
          passwordField}) {
        field.addActionListener(e -> attemptCreate());
      }
    }

    void show() {
      showOverlayForm(ColorTheme.get().accentGold, content);
      SwingUtilities.invokeLater(firstNameField::requestFocusInWindow);
    }

    private JPanel buildFieldsPanel() {

      JPanel fields = new JPanel(new GridBagLayout());
      fields.setOpaque(false);

      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(6, 6, 6, 6);
      c.anchor = GridBagConstraints.WEST;

      addFieldRow(fields, c, 0, "First Name:", firstNameField);
      addFieldRow(fields, c, 1, "Last Name:", lastNameField);
      addFieldRow(fields, c, 2, "Email:", emailField);
      addFieldRow(fields, c, 3, "Password:", passwordField);

      c.gridx = 0;
      c.gridy = 4;
      c.gridwidth = 2;
      c.insets = new Insets(2, 6, 0, 6);
      errorLabel.setForeground(ColorTheme.get().accentRed);
      errorLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
      fields.add(errorLabel, c);

      return fields;
    }

    /** Styles {@code field} and adds it as a labelled row. */
    private void addFieldRow(JPanel fields, GridBagConstraints c, int row, String labelText,
        JTextField field) {

      JLabel label = new JLabel(labelText);
      label.setForeground(ColorTheme.get().textSecondary);
      label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist));

      field.setForeground(ColorTheme.get().textPrimary);
      field.setBackground(ColorTheme.get().bgFieldDark);
      field.setCaretColor(ColorTheme.get().accentBlue);
      field.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1),
          new EmptyBorder(4, 6, 4, 6)));

      c.gridx = 0;
      c.gridy = row;
      c.gridwidth = 1;
      fields.add(label, c);

      c.gridx = 1;
      fields.add(field, c);
    }

    private JPanel buildButtonRow() {

      JButton createBtn = overlayButton("Create Admin");
      createBtn.setName(UiComponentNames.ADMIN_USER_CREATE_BUTTON);
      createBtn.addActionListener(e -> attemptCreate());

      JButton cancelBtn = overlayButton("Cancel");
      cancelBtn.setName(UiComponentNames.ADMIN_FORM_CANCEL_BUTTON);
      cancelBtn.addActionListener(e -> hideOverlay());

      JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
      row.setOpaque(false);
      row.add(createBtn);
      row.add(cancelBtn);
      return row;
    }

    private void attemptCreate() {

      String firstName = firstNameField.getText().trim();
      String lastName = lastNameField.getText().trim();
      String email = emailField.getText().trim();
      String password = new String(passwordField.getPassword());

      if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
        errorLabel.setText("All fields are required.");
        return;
      }

      RegisterRequest request = new RegisterRequest(firstName, lastName, email, password);

      SwingSecurityUtil.runAsync(() -> {
        try {
          userService.addAdminUser(request);
          SwingUtilities.invokeLater(() -> {
            hideOverlay();
            showOverlayMessage("Admin User Created", "Created admin user: " + email,
                ColorTheme.get().accentGreen);
          });
        } catch (Exception ex) {
          SwingUtilities.invokeLater(() -> errorLabel.setText(
              ex.getMessage() != null ? ex.getMessage() : "Could not create admin user."));
        }
      });
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FINANCIAL LEDGER ACTIONS (FinancialLedgerService)
  // ─────────────────────────────────────────────────────────────────────────
  // Reused across every open rather than constructed fresh per click (unlike LocationForm and
  // friends) -- IdleMonitor registers a permanent, application-lifetime AWT event listener with
  // no way to unregister it (see its own class javadoc), so a new FinancialLedgerDialog per click
  // would leak one more of those listeners every time the operator opens this screen.
  private FinancialLedgerDialog financialLedgerDialog;

  private void doFinancialLedger() {
    if (financialLedgerDialog == null) {
      financialLedgerDialog = new FinancialLedgerDialog();
    }
    financialLedgerDialog.show();
  }

  /**
   * Shows every jukebox-split period (current period's sub-totals computed live) in a table, with
   * an "Add Split" button to finalize the current period and start a new one. Shown via
   * {@link #showOverlayForm} rather than a separate {@code JDialog} window -- see {@link
   * #overlayCard}. Auto-dismisses after one minute of inactivity via {@link IdleMonitor}, since
   * this dialog can display real financial figures that shouldn't linger on-screen unattended.
   */
  private class FinancialLedgerDialog {

    private final JPanel content = new JPanel(new BorderLayout(0, 12));
    private final JPanel topRow = new JPanel(new BorderLayout(0, 8));
    private final JLabel statusLabel = new JLabel(" ");
    private final SplitPeriodTableModel tableModel = new SplitPeriodTableModel();
    private final JTable table = new JTable(tableModel);

    // Guards the IdleMonitor's onIdle callback below: since this dialog instance (and its one
    // IdleMonitor) is reused for the dialog's entire lifetime rather than recreated per open, a
    // timer tick that lands after the dialog was already closed some other way (e.g. Escape) must
    // be a no-op instead of hiding whatever unrelated overlay happens to be showing at that moment.
    private boolean visible;

    FinancialLedgerDialog() {

      content.setName(UiComponentNames.ADMIN_LEDGER_CONTENT);
      table.setName(UiComponentNames.ADMIN_LEDGER_TABLE);
      content.setOpaque(false);

      JLabel title = new JLabel("Financial Ledger");
      title.setForeground(ColorTheme.get().accentGold);
      title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));

      topRow.setOpaque(false);
      topRow.add(title, BorderLayout.WEST);
      topRow.add(buildAddSplitRow(), BorderLayout.EAST);

      content.add(topRow, BorderLayout.NORTH);
      content.add(buildTablePanel(), BorderLayout.CENTER);

      statusLabel.setForeground(ColorTheme.get().textSecondary);
      statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));

      // Touchscreen-only deployments have no Escape key, so this is the only way to close the
      // ledger short of waiting out the IdleMonitor timeout below.
      JButton dismissBtn = overlayButton("Dismiss");
      dismissBtn.setName(UiComponentNames.ADMIN_LEDGER_DISMISS);
      dismissBtn.addActionListener(e -> close());
      JPanel dismissRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
      dismissRow.setOpaque(false);
      dismissRow.add(dismissBtn);

      JPanel bottomRow = new JPanel(new BorderLayout(0, 8));
      bottomRow.setOpaque(false);
      bottomRow.add(statusLabel, BorderLayout.NORTH);
      bottomRow.add(dismissRow, BorderLayout.SOUTH);
      content.add(bottomRow, BorderLayout.SOUTH);

      content.registerKeyboardAction(e -> close(),
          javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_IN_FOCUSED_WINDOW);

      // Constructed once (see the FinancialLedgerDialog field above), not per show() -- its
      // 60-second inactivity timeout runs for as long as the admin panel itself is open.
      new IdleMonitor(60_000, () -> SwingUtilities.invokeLater(() -> {
        if (visible) {
          close();
        }
      }), () -> {});
    }

    void show() {
      visible = true;
      showOverlayForm(ColorTheme.get().accentGold, content);
      reload();
    }

    private void close() {
      visible = false;
      hideOverlay();
    }

    private JComponent buildAddSplitRow() {

      JButton addSplitBtn = overlayButton("Add Split");

      JButton confirmYesBtn = overlayButton("Yes, finalize");
      JButton confirmNoBtn = overlayButton("Cancel");

      JLabel confirmLabel = new JLabel("Finalize the current period and start a new one?");
      confirmLabel.setForeground(ColorTheme.get().accentOrange);
      confirmLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));

      JPanel confirmRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
      confirmRow.setOpaque(false);
      confirmRow.add(confirmLabel);
      confirmRow.add(confirmYesBtn);
      confirmRow.add(confirmNoBtn);
      confirmRow.setVisible(false);

      JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
      buttonRow.setOpaque(false);
      buttonRow.add(addSplitBtn);

      JPanel container = new JPanel(new BorderLayout());
      container.setOpaque(false);
      container.add(buttonRow, BorderLayout.CENTER);

      addSplitBtn.addActionListener(e -> {
        container.remove(buttonRow);
        container.add(confirmRow, BorderLayout.CENTER);
        confirmRow.setVisible(true);
        container.revalidate();
        container.repaint();
      });

      Runnable backToButton = () -> {
        container.remove(confirmRow);
        container.add(buttonRow, BorderLayout.CENTER);
        container.revalidate();
        container.repaint();
      };

      confirmNoBtn.addActionListener(e -> backToButton.run());
      confirmYesBtn.addActionListener(e -> {
        backToButton.run();
        statusLabel.setText("Finalizing current period...");
        SwingSecurityUtil.runAsync(() -> {
          try {
            financialLedgerService.addSplit();
            // The summary itself is emailed in the background (FinancialLedgerSplitEmailListener).
            int recipients = splitSummaryRecipients.size();
            String doneMessage = recipients == 0 ? "Split finalized."
                : "Split finalized. Emailing the summary to " + recipients
                    + (recipients == 1 ? " recipient." : " recipients.");
            SwingUtilities.invokeLater(() -> reload(doneMessage));
          } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(
                ex.getMessage() != null ? ex.getMessage() : "Could not finalize the split."));
          }
        });
      });

      return container;
    }

    private JScrollPane buildTablePanel() {

      table.setOpaque(true);
      table.setBackground(ColorTheme.get().bgList);
      table.setForeground(ColorTheme.get().textPrimary);
      table.setGridColor(ColorTheme.get().colorAdminSeparator);
      table.setSelectionBackground(ColorTheme.get().bgListSelected);
      table.setSelectionForeground(ColorTheme.get().textPrimary);
      table.setRowHeight(LayoutTheme.get().adminAlbumCellH);
      table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
      table.getTableHeader()
          .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist));
      table.setFillsViewportHeight(true);
      table.setRowSelectionAllowed(false);
      table.setDefaultRenderer(Object.class, new SplitPeriodCellRenderer());

      // Sized to fit each column's actual content rather than defaulting to equal widths.
      int[] columnWidths = {170, 80, 80, 80, 90, 100, 100};
      for (int i = 0; i < columnWidths.length; i++) {
        table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
      }

      JScrollPane scrollPane = darkScrollPane(table);
      scrollPane.setPreferredSize(new Dimension(750, 360));
      return scrollPane;
    }

    private void reload() {
      reload(" ");
    }

    /** @param doneMessage shown on the status line once the periods have loaded */
    private void reload(String doneMessage) {

      statusLabel.setText("Loading...");
      SwingSecurityUtil.runAsync(() -> {
        try {
          List<JukeboxSplitPeriodDto> periods = financialLedgerService.getAllPeriods();
          SwingUtilities.invokeLater(() -> {
            tableModel.setPeriods(periods);
            statusLabel.setText(doneMessage);
          });
        } catch (Exception ex) {
          SwingUtilities.invokeLater(() -> statusLabel.setText(
              ex.getMessage() != null ? ex.getMessage() : "Could not load the financial ledger."));
        }
      });
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // VIEW ACTIVITY (UserActivityService)
  // ─────────────────────────────────────────────────────────────────────────
  // Reused across every open rather than constructed fresh per click -- same rationale as
  // financialLedgerDialog above.
  private ActivityDialog activityDialog;

  private void doViewActivity() {
    if (activityDialog == null) {
      activityDialog = new ActivityDialog();
    }
    activityDialog.show();
  }

  /**
   * Shows this location's most recent user-activity records -- Swing tab/page/search/artist/album
   * navigation plus queue actions from both the Swing UI and mobile/web -- in a scrollable table,
   * newest first. Shown via {@link #showOverlayForm} rather than a separate {@code JDialog} window
   * -- see {@link #overlayCard}. Auto-dismisses after one minute of inactivity via
   * {@link IdleMonitor}, mirroring {@link FinancialLedgerDialog}.
   */
  private class ActivityDialog {

    /** Plenty for one scrollable screen's worth of history without an unbounded query. */
    private static final int ACTIVITY_LIMIT = 500;

    private final JPanel content = new JPanel(new BorderLayout(0, 12));
    private final JLabel statusLabel = new JLabel(" ");
    private final ActivityTableModel tableModel = new ActivityTableModel();
    private final JTable table = new JTable(tableModel);

    // Same reuse-not-recreate rationale as FinancialLedgerDialog.visible above.
    private boolean visible;

    ActivityDialog() {

      content.setName(UiComponentNames.ADMIN_ACTIVITY_CONTENT);
      table.setName(UiComponentNames.ADMIN_ACTIVITY_TABLE);
      content.setOpaque(false);

      JLabel title = new JLabel("Activity");
      title.setForeground(ColorTheme.get().accentGold);
      title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));

      content.add(title, BorderLayout.NORTH);
      content.add(buildTablePanel(), BorderLayout.CENTER);

      statusLabel.setForeground(ColorTheme.get().textSecondary);
      statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));

      // Same touchscreen rationale as FinancialLedgerDialog's Dismiss button above.
      JButton dismissBtn = overlayButton("Dismiss");
      dismissBtn.setName(UiComponentNames.ADMIN_ACTIVITY_DISMISS);
      dismissBtn.addActionListener(e -> close());
      JPanel dismissRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
      dismissRow.setOpaque(false);
      dismissRow.add(dismissBtn);

      JPanel bottomRow = new JPanel(new BorderLayout(0, 8));
      bottomRow.setOpaque(false);
      bottomRow.add(statusLabel, BorderLayout.NORTH);
      bottomRow.add(dismissRow, BorderLayout.SOUTH);
      content.add(bottomRow, BorderLayout.SOUTH);

      content.registerKeyboardAction(e -> close(),
          javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_IN_FOCUSED_WINDOW);

      // Constructed once (see the activityDialog field above), not per show() -- its 60-second
      // inactivity timeout runs for as long as the admin panel itself is open.
      new IdleMonitor(60_000, () -> SwingUtilities.invokeLater(() -> {
        if (visible) {
          close();
        }
      }), () -> {});
    }

    void show() {
      visible = true;
      showOverlayForm(ColorTheme.get().accentGold, content);
      reload();
    }

    private void close() {
      visible = false;
      hideOverlay();
    }

    private JScrollPane buildTablePanel() {

      table.setOpaque(true);
      table.setBackground(ColorTheme.get().bgList);
      table.setForeground(ColorTheme.get().textPrimary);
      table.setGridColor(ColorTheme.get().colorAdminSeparator);
      table.setSelectionBackground(ColorTheme.get().bgListSelected);
      table.setSelectionForeground(ColorTheme.get().textPrimary);
      table.setRowHeight(LayoutTheme.get().adminAlbumCellH);
      table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
      table.getTableHeader()
          .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist));
      table.setFillsViewportHeight(true);
      table.setRowSelectionAllowed(false);
      table.setDefaultRenderer(Object.class, new ActivityCellRenderer());
      table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

      // Sized to fit each column's actual content; Details (the last column) stretches to fill
      // whatever width remains, per AUTO_RESIZE_LAST_COLUMN above.
      int[] columnWidths = {150, 90, 150, 170, 300};
      for (int i = 0; i < columnWidths.length; i++) {
        table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
      }

      JScrollPane scrollPane = darkScrollPane(table);
      scrollPane.setPreferredSize(new Dimension(900, 420));
      return scrollPane;
    }

    private void reload() {

      statusLabel.setText("Loading...");
      SwingSecurityUtil.runAsync(() -> {
        try {
          Integer locationId = songLibraryService.getOwnLocationId();
          List<UserActivityRecord> records =
              userActivityService.findRecentActivity(locationId, ACTIVITY_LIMIT);
          SwingUtilities.invokeLater(() -> {
            tableModel.setRecords(records);
            statusLabel.setText(" ");
          });
        } catch (Exception ex) {
          SwingUtilities.invokeLater(() -> statusLabel
              .setText(ex.getMessage() != null ? ex.getMessage() : "Could not load activity."));
        }
      });
    }
  }

  /** Plain dark-theme cell padding/coloring for the Activity dialog's table. */
  private static class ActivityCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column) {

      JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected,
          hasFocus, row, column);
      label.setOpaque(true);
      label.setBackground(ColorTheme.get().bgList);
      label.setForeground(ColorTheme.get().textPrimary);
      label.setBorder(new EmptyBorder(2, 8, 2, 8));
      return label;
    }
  }

  /**
   * Backs the Activity dialog's table. Records arrive already newest-first from {@code
   * UserActivityService#findRecentActivity} (backed by an {@code ORDER BY occurred_at DESC} query
   * or, for the filesystem backend, newest-day-file-first/newest-line-first reading), so unlike
   * {@link SplitPeriodTableModel} this never needs to reorder what it's given.
   */
  private static class ActivityTableModel extends javax.swing.table.AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"Time", "Source", "User", "Activity", "Details"};

    private static final java.time.format.DateTimeFormatter TIME_FORMAT =
        java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a")
            .withZone(java.time.ZoneId.systemDefault());

    private List<UserActivityRecord> records = new ArrayList<>();

    void setRecords(List<UserActivityRecord> newRecords) {
      this.records = new ArrayList<>(newRecords);
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return records.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      UserActivityRecord record = records.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> TIME_FORMAT.format(record.occurredAt());
        case 1 -> record.source().name();
        case 2 -> record.username();
        case 3 -> record.activityType().name();
        case 4 -> formatDetails(record.details());
        default -> "";
      };
    }

    private static String formatDetails(java.util.Map<String, Object> details) {
      if (details == null || details.isEmpty()) {
        return "";
      }
      return details.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
          .collect(java.util.stream.Collectors.joining(", "));
    }
  }

  /** Highlights the (always-first, per {@link SplitPeriodTableModel}) open/current period's row. */
  private static class SplitPeriodCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column) {

      JLabel label =
          (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      boolean isCurrentRow = row == 0;
      label.setOpaque(true);
      label.setBackground(ColorTheme.get().bgList);
      label.setForeground(isCurrentRow ? ColorTheme.get().accentGold : ColorTheme.get().textPrimary);
      label.setFont(table.getFont().deriveFont(isCurrentRow ? Font.BOLD : Font.PLAIN));
      label.setBorder(new EmptyBorder(2, 8, 2, 8));
      return label;
    }
  }

  /** Backs the Financial Ledger dialog's table -- current period (if any) sorted first. */
  private static class SplitPeriodTableModel extends javax.swing.table.AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS =
        {"Period", "Cash", "Card", "Mobile", "Total", "Owner Due", "Operator Due"};

    private static final java.time.format.DateTimeFormatter DATE_FORMAT =
        java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")
            .withZone(java.time.ZoneId.systemDefault());

    private List<JukeboxSplitPeriodDto> periods = new ArrayList<>();

    void setPeriods(List<JukeboxSplitPeriodDto> newPeriods) {
      this.periods = new ArrayList<>(newPeriods);
      // Current (open) period first, then most-recently-finalized next, etc.
      Collections.reverse(this.periods);
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return periods.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

      JukeboxSplitPeriodDto period = periods.get(rowIndex);
      boolean isOpen = period.endDate() == null;

      return switch (columnIndex) {
        case 0 -> isOpen
            ? "Current (since " + DATE_FORMAT.format(period.startDate()) + ")"
            : DATE_FORMAT.format(period.startDate()) + " – " + DATE_FORMAT.format(period.endDate());
        case 1 -> formatDollars(period.cashTotal());
        case 2 -> formatDollars(period.cardTotal());
        case 3 -> formatDollars(period.mobileTotal());
        case 4 -> formatDollars(period.totalEarned());
        case 5 -> formatDollars(period.amountDueOwner());
        case 6 -> formatDollars(period.amountDueOperator());
        default -> "";
      };
    }

    private static String formatDollars(java.math.BigDecimal amount) {
      return amount == null ? "—" : "$" + amount.toPlainString();
    }
  }

  /**
   * Called by {@link JukeANatorFrame#setLicenseService} once the license service is known.
   *
   * @param showRegistration opens the frame's registration card
   */
  public void setLicenseService(LicenseService licenseService, Runnable showRegistration) {
    this.licenseService = licenseService;
    this.showRegistration = showRegistration;
  }

  /**
   * Shows the license status (after a fresh subscription check for the subscription tiers) and
   * offers to open the registration screen.
   */
  private void doLicense() {

    if (licenseService == null) {
      showOverlayMessage("License", "Licensing is not available on this instance.",
          ColorTheme.get().accentGold);
      return;
    }
    SwingSecurityUtil.runAsync(() -> {
      LicenseStatus status;
      try {
        status = licenseService.getStatus().licensedTier().isSubscription()
            ? licenseService.validateSubscription()
            : licenseService.refreshLocal();
      } catch (Exception ex) {
        status = licenseService.getStatus();
      }
      LicenseStatus s = status;
      SwingUtilities.invokeLater(() -> {
        StringBuilder text = new StringBuilder();
        text.append("License: ").append(s.describe()).append(". ");
        text.append("Commercial operation (bill acceptor and card reader): ")
            .append(s.commercialEnabled() ? "enabled" : "disabled").append(". ");
        text.append("Machine ID: ").append(s.machineId() == null ? "unknown" : s.machineId())
            .append(". ");
        if (s.subscriptionQuarter() != null) {
          text.append("Subscription paid through ").append(s.subscriptionQuarter())
              .append(" (").append(s.subscriptionHoursRemaining()).append(" hours left). ");
        }
        if (s.lastServerCheckAt() != null) {
          text.append("Last confirmed by the license server: ")
              .append(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                  .withZone(java.time.ZoneId.systemDefault()).format(s.lastServerCheckAt()))
              .append(". ");
        }
        if (s.message() != null) {
          text.append(s.message()).append(' ');
        }
        if (s.latestEngineVersion() != null) {
          text.append("Latest JukeANator version: ").append(s.latestEngineVersion())
              .append(". ");
        }
        text.append("Open the registration screen?");
        showOverlayConfirm("License", text.toString(),
            s.commercialEnabled() ? ColorTheme.get().accentGreen : ColorTheme.get().accentOrange,
            () -> {
              if (showRegistration != null) {
                showRegistration.run();
              }
            });
      });
    });
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LOCATION (LocationService)
  // ─────────────────────────────────────────────────────────────────────────
  private void doLocation() {

    SwingSecurityUtil.runAsync(() -> {
      try {
        LocationEntity current = locationService.getOrCreateOwnLocation(null);
        SwingUtilities.invokeLater(() -> new LocationForm(current).show());
      } catch (Exception ex) {
        ex.printStackTrace();
        SwingUtilities.invokeLater(() -> showOverlayMessage("Error",
            "Could not load location info: "
                + (ex.getMessage() != null ? ex.getMessage() : ex.toString()),
            ColorTheme.get().accentRed));
      }
    });
  }

  /**
   * Custom (non-{@code JOptionPane}) modal prompt for this standalone/slave instance's single own
   * {@link LocationEntity}: its name, coordinates, logo and geofencing, plus what provisioning it
   * on master needs. Replaces the former separate Add Location and Edit Location Info forms; Add
   * Location created a second, unrelated location record just to produce an API key, which left
   * the instance's own location (the one it actually runs as) unprovisioned.
   *
   * <p>There is deliberately no location id field: master picks the id when the SQL below is run,
   * and tells the slave its id on every {@code /ws-slave} connection, since it identifies a slave
   * by its API key alone (see {@link LocationService#reconcileOwnLocationId}).
   *
   * <ul>
   * <li><b>Generate API Key</b> -- {@link LocationService#generateOwnLocationApiKey}; the
   * plaintext key is shown once, for {@code app.location-api-key}. As this is the provisioning
   * step, it also purges the financial ledger and discards unsent user activity ({@link
   * FinancialLedgerService#purgeLocalLedger}, {@link UserActivityService#discardPendingMasterSync}),
   * so nothing recorded while testing is ever pushed to master.</li>
   * <li><b>Copy SQL</b> -- saves, then builds the insert for master's {@code location} table from
   * the saved record (including the key's hash and lookup value), copies it to the clipboard and
   * writes it to {@code <dataDir>/location.sql}.</li>
   * <li><b>Logo</b> -- a file name under {@code <dataDir>/images}; Choose copies a picked image
   * there.</li>
   * </ul>
   *
   * Shown via {@link #showOverlayForm} rather than a separate {@code JDialog} window -- see
   * {@link #overlayCard}.
   */
  private class LocationForm {

    private static final int LOGO_PREVIEW_SIZE = 64;
    private static final String SQL_FILE_NAME = "location.sql";

    private final JTextField nameField = new JTextField(18);
    private final JTextField latitudeField = new JTextField(18);
    private final JTextField longitudeField = new JTextField(18);
    private final JTextField logoNameField = new JTextField(12);
    private final JLabel logoPreview = new JLabel();
    private final JCheckBox geoFencedCheckBox = new JCheckBox("Geo-fenced");
    private final JLabel apiKeyLabel = new JLabel("API Key:");
    private final JTextField apiKeyField = new JTextField(24);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton generateKeyBtn = overlayButton("Generate API Key");
    private final JPanel content = new JPanel(new BorderLayout(0, 16));

    // The own location itself: the service methods called below mutate this same instance in
    // place, so it always reflects what was last saved.
    private final LocationEntity location;

    // The name the song-library file is currently stored under, so a save can rename it from this
    // to the new name -- location.getName() can't be used, since the save mutates it first.
    private String savedName;

    // Generate API Key asks for a second click before replacing the key.
    private boolean confirmingKeyGeneration;

    LocationForm(LocationEntity current) {

      this.location = current;
      this.savedName = current.getName();

      nameField.setName(UiComponentNames.ADMIN_LOCATION_NAME_FIELD);
      content.setOpaque(false);

      JLabel title = new JLabel("Location");
      title.setForeground(ColorTheme.get().accentGold);
      title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
      content.add(title, BorderLayout.NORTH);

      nameField.setText(current.getName());
      latitudeField
          .setText(current.getLatitude() != null ? String.valueOf(current.getLatitude()) : "");
      longitudeField
          .setText(current.getLongitude() != null ? String.valueOf(current.getLongitude()) : "");
      logoNameField.setText(current.getLogoName() != null ? current.getLogoName() : "");
      geoFencedCheckBox.setSelected(current.isGeoFenced());

      content.add(buildFieldsPanel(), BorderLayout.CENTER);
      content.add(buildButtonRow(), BorderLayout.SOUTH);

      content.registerKeyboardAction(e -> hideOverlay(),
          javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_IN_FOCUSED_WINDOW);

      // Physical keyboard: Enter in any field saves, like the Save Changes button.
      for (JTextField field : new JTextField[] {nameField, latitudeField, longitudeField,
          logoNameField}) {
        field.addActionListener(e -> attemptSave(this::closeAfterSave));
      }
      logoNameField.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override
        public void focusLost(java.awt.event.FocusEvent e) {
          refreshLogoPreview();
        }
      });

      refreshLogoPreview();
    }

    void show() {
      showOverlayForm(ColorTheme.get().accentGold, content);
      SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }

    private JPanel buildFieldsPanel() {

      JPanel fields = new JPanel(new GridBagLayout());
      fields.setOpaque(false);

      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(6, 6, 6, 6);
      c.anchor = GridBagConstraints.WEST;

      int row = 0;
      addFieldRow(fields, c, row++, "Name:", nameField);
      addFieldRow(fields, c, row++, "Latitude:", latitudeField);
      addFieldRow(fields, c, row++, "Longitude:", longitudeField);

      styleField(logoNameField);
      JButton chooseLogoBtn = overlayButton("Choose...");
      chooseLogoBtn.addActionListener(e -> chooseLogo());
      JPanel logoRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
      logoRow.setOpaque(false);
      logoRow.add(logoNameField);
      logoRow.add(chooseLogoBtn);
      addComponentRow(fields, c, row++, "Logo:", logoRow);

      logoPreview.setForeground(ColorTheme.get().textSecondary);
      logoPreview.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
      addComponentRow(fields, c, row++, "", logoPreview);

      geoFencedCheckBox.setOpaque(false);
      geoFencedCheckBox.setForeground(ColorTheme.get().textPrimary);
      geoFencedCheckBox.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist));
      c.gridx = 0;
      c.gridy = row++;
      c.gridwidth = 2;
      fields.add(geoFencedCheckBox, c);

      // Shown only once a key has been generated -- it is never recoverable afterward.
      apiKeyField.setEditable(false);
      styleField(apiKeyField);
      addComponentRow(fields, c, row++, "API Key:", apiKeyField, apiKeyLabel);
      apiKeyLabel.setVisible(false);
      apiKeyField.setVisible(false);

      c.gridx = 0;
      c.gridy = row;
      c.gridwidth = 2;
      c.insets = new Insets(2, 6, 0, 6);
      statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));
      fields.add(statusLabel, c);

      return fields;
    }

    private void addFieldRow(JPanel fields, GridBagConstraints c, int row, String labelText,
        JTextField field) {

      styleField(field);
      addComponentRow(fields, c, row, labelText, field);
    }

    private void addComponentRow(JPanel fields, GridBagConstraints c, int row, String labelText,
        JComponent component) {

      addComponentRow(fields, c, row, labelText, component, new JLabel(labelText));
    }

    private void addComponentRow(JPanel fields, GridBagConstraints c, int row, String labelText,
        JComponent component, JLabel label) {

      label.setText(labelText);
      label.setForeground(ColorTheme.get().textSecondary);
      label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist));

      c.gridx = 0;
      c.gridy = row;
      c.gridwidth = 1;
      c.insets = new Insets(6, 6, 6, 6);
      fields.add(label, c);

      c.gridx = 1;
      fields.add(component, c);
    }

    private void styleField(JTextField field) {

      field.setForeground(ColorTheme.get().textPrimary);
      field.setBackground(ColorTheme.get().bgFieldDark);
      field.setCaretColor(ColorTheme.get().accentBlue);
      field.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1),
          new EmptyBorder(4, 6, 4, 6)));
    }

    private JPanel buildButtonRow() {

      JButton saveBtn = overlayButton("Save Changes");
      saveBtn.setName(UiComponentNames.ADMIN_LOCATION_SAVE_BUTTON);
      saveBtn.addActionListener(e -> attemptSave(this::closeAfterSave));

      generateKeyBtn.addActionListener(e -> attemptGenerateKey());

      JButton copySqlBtn = overlayButton("Copy SQL");
      copySqlBtn.addActionListener(e -> attemptCopySql());

      JButton cancelBtn = overlayButton("Cancel");
      cancelBtn.setName(UiComponentNames.ADMIN_FORM_CANCEL_BUTTON);
      cancelBtn.addActionListener(e -> hideOverlay());

      JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
      row.setOpaque(false);
      row.add(saveBtn);
      row.add(generateKeyBtn);
      row.add(copySqlBtn);
      row.add(cancelBtn);
      return row;
    }

    // ── Save ──────────────────────────────────────────────────────────────

    /**
     * Validates and saves the form, then runs {@code afterSave} on the EDT once the save succeeded.
     * On failure, shows the error and leaves the form open.
     */
    private void attemptSave(Runnable afterSave) {

      String name = nameField.getText().trim();
      String latitudeText = latitudeField.getText().trim();
      String longitudeText = longitudeField.getText().trim();
      String logoName = logoNameField.getText().trim();

      if (name.isEmpty() || latitudeText.isEmpty() || longitudeText.isEmpty()) {
        showError("Name, latitude, and longitude are required.");
        return;
      }

      final double latitude;
      final double longitude;
      try {
        latitude = Double.parseDouble(latitudeText);
        longitude = Double.parseDouble(longitudeText);
      } catch (NumberFormatException nfe) {
        showError("Latitude and longitude must be numbers.");
        return;
      }

      if (latitude < -90 || latitude > 90) {
        showError("Latitude must be between -90 and 90.");
        return;
      }
      if (longitude < -180 || longitude > 180) {
        showError("Longitude must be between -180 and 180.");
        return;
      }

      UpdateLocationInfoRequest request = new UpdateLocationInfoRequest(name,
          Double.valueOf(latitude), Double.valueOf(longitude), logoName.isEmpty() ? null : logoName,
          geoFencedCheckBox.isSelected());
      String previousName = savedName;

      SwingSecurityUtil.runAsync(() -> {
        try {
          locationService.updateOwnLocationInfo(request);
          try {
            // Best-effort: the name update itself already succeeded above, and a startup-time
            // fallback (SongLibraryRepositoryFileSystemImpl.loadAggregateRoot) reconciles a
            // stale filename on next restart regardless -- so a rename failure here shouldn't
            // undo/block the location info that was just saved.
            songLibraryService.renameOwnLocationLibraryFileIfNameChanged(previousName);
          } catch (Exception renameEx) {
            renameEx.printStackTrace();
          }
          SwingUtilities.invokeLater(() -> {
            savedName = name;
            refreshLogoPreview();
            afterSave.run();
          });
        } catch (Exception ex) {
          SwingUtilities.invokeLater(() -> showError(
              ex.getMessage() != null ? ex.getMessage() : "Could not update location info."));
        }
      });
    }

    private void closeAfterSave() {

      hideOverlay();
      showOverlayMessage("Location Updated",
          "Location info saved. A changed logo appears on this screen after a restart.",
          ColorTheme.get().accentGreen);
    }

    // ── API key ───────────────────────────────────────────────────────────

    private void attemptGenerateKey() {

      if (!confirmingKeyGeneration) {
        confirmingKeyGeneration = true;
        generateKeyBtn.setText("Confirm New Key");
        showWarning("A new key replaces this location's current one, and provisions this "
            + "instance as a new slave: its financial ledger (every split period and cash and "
            + "card transaction) is deleted, and user activity not yet sent to master is "
            + "discarded. Master rejects this instance until its location row has the new key's "
            + "hash (see Copy SQL). Click Confirm New Key to continue.");
        return;
      }

      confirmingKeyGeneration = false;
      generateKeyBtn.setText("Generate API Key");

      SwingSecurityUtil.runAsync(() -> {
        try {
          ProvisionedLocationDto provisioned = locationService.generateOwnLocationApiKey();
          // Provisioning: anything recorded so far (testing while standalone) is not revenue
          // and must never be pushed to master once this instance runs as a slave.
          financialLedgerService.purgeLocalLedger();
          userActivityService.discardPendingMasterSync();
          SwingUtilities.invokeLater(() -> {
            apiKeyField.setText(provisioned.apiKey());
            apiKeyField.setCaretPosition(0);
            apiKeyLabel.setVisible(true);
            apiKeyField.setVisible(true);
            showWarning("Copy this API key into app.location-api-key now. It will not be shown "
                + "again; only its hash is saved.");
            content.revalidate();
            content.repaint();
          });
        } catch (Exception ex) {
          SwingUtilities.invokeLater(() -> showError(
              ex.getMessage() != null ? ex.getMessage() : "Could not generate an API key."));
        }
      });
    }

    // ── SQL for master ────────────────────────────────────────────────────

    private void attemptCopySql() {

      attemptSave(() -> {

        if (location.getStatus() == null
            || location.getStatus() == LocationStatus.PENDING) {
          showError("Generate an API key first. The SQL needs the key's hash.");
          return;
        }

        String sql = buildMasterSql();
        StringBuilder outcome = new StringBuilder("Saved.");

        try {
          java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
              .setContents(new java.awt.datatransfer.StringSelection(sql), null);
          outcome.append(" SQL copied to the clipboard");
        } catch (Exception ex) {
          outcome.append(" Could not copy the SQL to the clipboard");
        }

        java.nio.file.Path sqlFile = dataDirPath().resolve(SQL_FILE_NAME);
        try {
          java.nio.file.Files.writeString(sqlFile, sql);
          outcome.append(" and saved to ").append(sqlFile).append('.');
        } catch (Exception ex) {
          outcome.append(", and could not save it to ").append(sqlFile).append('.');
        }

        showSuccess(outcome.toString());
      });
    }

    /**
     * The insert for master's {@code location} table, built from the saved own location. Master
     * picks the id itself (one past the highest existing id), and the slave learns it on its first
     * connection. Also carries the sequence safeguard (see docs/master-slave-local-trial.md) and,
     * commented out, the update to use instead when the row already exists on master and only the
     * key changed. That update matches by this instance's id, which is master's id once this
     * instance has connected at least once.
     */
    private String buildMasterSql() {

      String hash = sqlString(location.getApiKeyHash());
      String lookup = sqlString(location.getApiKeyLookup());

      return "-- Run in master's database. Master assigns the id; the slave learns it when it "
          + "connects.\n"
          + "INSERT INTO location (id, name, latitude, longitude, logo_name, api_key_hash, "
          + "api_key_lookup, status, is_geo_fenced)\n"
          + "SELECT COALESCE(MAX(id), 0) + 1, " + sqlString(location.getName()) + ", "
          + sqlNumber(location.getLatitude()) + ", " + sqlNumber(location.getLongitude()) + ", "
          + sqlString(location.getLogoName()) + ", " + hash + ", " + lookup + ", 'PROVISIONED', "
          + (location.isGeoFenced() ? 1 : 0) + " FROM location;\n"
          + "UPDATE persistent_identity_seq SET next_val = (SELECT MAX(id) + 1 FROM location) "
          + "WHERE next_val <= (SELECT MAX(id) FROM location);\n"
          + "\n"
          + "-- If master already has this location's row and only the API key changed, run this "
          + "instead of the statements above (check the id against master's location table):\n"
          + "-- UPDATE location SET api_key_hash = " + hash + ", api_key_lookup = " + lookup
          + " WHERE id = " + location.getPersistentIdentity() + ";\n";
    }

    private String sqlString(String value) {
      return value == null ? "NULL"
          : "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private String sqlNumber(Double value) {
      return value == null ? "NULL" : java.math.BigDecimal.valueOf(value).toPlainString();
    }

    // ── Logo ──────────────────────────────────────────────────────────────

    private java.nio.file.Path dataDirPath() {
      return java.nio.file.Path.of(dataDir == null || dataDir.isBlank() ? "." : dataDir);
    }

    private java.nio.file.Path imagesDir() {
      return dataDirPath().resolve("images");
    }

    /**
     * Shows an image chooser in place of the form (embedded in the overlay, like the playlist
     * chooser -- see {@link #showPlaylistChooser}), then returns to the form either way.
     */
    private void chooseLogo() {

      JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
      chooser.setDialogType(JFileChooser.OPEN_DIALOG);
      chooser.setDialogTitle("Choose Location Logo");
      chooser.setApproveButtonText("Use as Logo");
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.setFileFilter(new FileNameExtensionFilter("Images (*.jpg, *.jpeg, *.png, *.gif)",
          "jpg", "jpeg", "png", "gif"));
      chooser.setPreferredSize(new Dimension(Math.max(640, getWidth() * 3 / 5),
          Math.max(420, getHeight() * 3 / 5)));

      chooser.addActionListener(e -> {
        if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
          File selected = chooser.getSelectedFile();
          show();
          if (selected != null) {
            importLogo(selected);
          }
        } else if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) {
          show();
        }
      });

      JLabel titleLabel = new JLabel("Choose Location Logo");
      titleLabel.setForeground(ColorTheme.get().accentGold);
      titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
      titleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

      JPanel chooserContent = new JPanel(new BorderLayout(0, 12));
      chooserContent.setOpaque(false);
      chooserContent.add(titleLabel, BorderLayout.NORTH);
      chooserContent.add(chooser, BorderLayout.CENTER);

      // Escape returns to the Location form rather than closing everything.
      chooserContent.registerKeyboardAction(e -> show(),
          javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_IN_FOCUSED_WINDOW);

      showOverlayForm(ColorTheme.get().accentGold, chooserContent);
    }

    /**
     * Copies {@code source} into {@code <dataDir>/images} (where the kiosk, and the logo upload to
     * master, look for it) and puts its file name in the Logo field. Saving applies it.
     */
    private void importLogo(File source) {

      java.nio.file.Path target = imagesDir().resolve(source.getName());
      try {
        java.nio.file.Files.createDirectories(imagesDir());
        if (!java.nio.file.Files.exists(target)
            || !java.nio.file.Files.isSameFile(source.toPath(), target)) {
          java.nio.file.Files.copy(source.toPath(), target,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (Exception ex) {
        showError("Could not copy the logo to " + imagesDir() + ": "
            + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        return;
      }

      logoNameField.setText(source.getName());
      refreshLogoPreview();
      showSuccess("Logo copied to " + target + ". Save Changes to use it.");
    }

    /**
     * Shows a thumbnail of the logo named in the Logo field. Read straight from disk with
     * {@link ImageIO} rather than through {@code ImageIcon(URL)}, which caches by URL and would
     * keep showing the old image after a same-named file is replaced.
     */
    private void refreshLogoPreview() {

      logoPreview.setIcon(null);
      String logoName = logoNameField.getText().trim();
      if (logoName.isEmpty()) {
        logoPreview.setText("(no logo: the default is shown)");
        return;
      }

      File file = imagesDir().resolve(logoName).toFile();
      if (!file.isFile()) {
        logoPreview.setText("(not found in " + imagesDir() + ")");
        return;
      }

      try {
        java.awt.image.BufferedImage image = ImageIO.read(file);
        if (image == null) {
          logoPreview.setText("(not a readable image)");
          return;
        }
        double scale = Math.min((double) LOGO_PREVIEW_SIZE / image.getWidth(),
            (double) LOGO_PREVIEW_SIZE / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        logoPreview.setText("");
        logoPreview.setIcon(new javax.swing.ImageIcon(
            image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH)));
      } catch (Exception ex) {
        logoPreview.setText("(not a readable image)");
      }
    }

    // ── Status line ───────────────────────────────────────────────────────

    private void showError(String message) {
      showStatus(message, ColorTheme.get().accentRed);
    }

    private void showWarning(String message) {
      showStatus(message, ColorTheme.get().accentOrange);
    }

    private void showSuccess(String message) {
      showStatus(message, ColorTheme.get().accentGreen);
    }

    private void showStatus(String message, Color color) {

      String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
      statusLabel.setForeground(color);
      statusLabel.setText("<html><body style='width: 420px;'>" + escaped + "</body></html>");
      content.revalidate();
      content.repaint();
    }
  }

  private void doMinimize() {

    SwingUtilities.invokeLater(() -> {
      GraphicsDevice gd =
          GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      gd.setFullScreenWindow(null);
      if (ownerFrame instanceof JukeANatorFrame jukeFrame) {
        jukeFrame.setAdminMinimizeRequested(true);
      }
      ownerFrame.setState(JFrame.ICONIFIED);
    });
  }

  private void doExit() {

    showOverlayConfirm("Confirm Exit", "Exit JukeANator?", ColorTheme.get().accentRed,
        () -> System.exit(0));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // QUEUE ACTIONS (SongQueueService / SongPlayerService)
  // ─────────────────────────────────────────────────────────────────────────
  private void doPlayNextTrack() {

    try {
      songPlayerService.playNextTrack(songLibraryService.getOwnLocationId());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doPause() {

    try {
      songPlayerService.pause(songLibraryService.getOwnLocationId());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doPlaySelected() {

    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null) {
      showOverlayMessage("No Selection", "Please select a song in the queue first.",
          ColorTheme.get().accentOrange);
      return;
    }
    try {
      int idx = queueList.getSelectedIndex();
      for (int i = 0; i < idx; i++) {
        songQueueService.moveSongUpInQueue(songQueueService.getOwnLocationId(),
            new ChangeSongQueueRequest(selected.song().albumId(),
                selected.song().songId()));
      }
      songPlayerService.playNextTrack(songLibraryService.getOwnLocationId());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doMoveUp() {

    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null)
      return;
    int idx = queueList.getSelectedIndex();
    if (idx <= 0)
      return;
    try {
      songQueueService.moveSongUpInQueue(songQueueService.getOwnLocationId(),
          new ChangeSongQueueRequest(selected.song().albumId(), selected.song().songId(),
              idx));
      SongQueueEntryDto above = queueListModel.get(idx - 1);
      queueListModel.set(idx - 1, selected);
      queueListModel.set(idx, above);
      queueList.setSelectedIndex(idx - 1);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doMoveDown() {

    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null)
      return;
    int idx = queueList.getSelectedIndex();
    if (idx >= queueListModel.getSize() - 1)
      return;
    try {
      songQueueService.moveSongDownInQueue(songQueueService.getOwnLocationId(),
          new ChangeSongQueueRequest(selected.song().albumId(), selected.song().songId(),
              idx));
      SongQueueEntryDto below = queueListModel.get(idx + 1);
      queueListModel.set(idx + 1, selected);
      queueListModel.set(idx, below);
      queueList.setSelectedIndex(idx + 1);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doRemoveSong() {

    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null) {
      showOverlayMessage("No Selection", "Please select a song in the queue first.",
          ColorTheme.get().accentOrange);
      return;
    }
    try {
      songQueueService.removeSongDownFromQueue(songQueueService.getOwnLocationId(),
          new ChangeSongQueueRequest(selected.song().albumId(),
              selected.song().songId()));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doFlushQueue() {

    showOverlayConfirm("Clear Queue", "Clear the entire song queue?", ColorTheme.get().accentRed,
        () -> SwingSecurityUtil.runAsync(() -> {
          try {
            songQueueService.flushQueue(songQueueService.getOwnLocationId());
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }));
  }

  private void doRandomizeQueue() {

    showOverlayConfirm("Shuffle Queue", "Shuffle the entire song queue?",
        ColorTheme.get().accentViolet, () -> SwingSecurityUtil.runAsync(() -> {
          try {
            songQueueService.randomizeQueue(songQueueService.getOwnLocationId());
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }));
  }

  private void doIncrementCredits() {
    creditManager.addDollar();
  }

  private void doDecrementCredits() {
    creditManager.deductCredits(1);
  }

  /**
   * The folder playlists are loaded from and saved to by default ({@code <dataDir>/playlists}, e.g.
   * {@code C:\kiosk\data\playlists}), created if missing so the operator can copy playlists into it.
   */
  private File playlistsDir() {

    File dir = new File(dataDir == null || dataDir.isBlank() ? "." : dataDir, "playlists");
    if (!dir.isDirectory() && !dir.mkdirs()) {
      System.err.println("Could not create playlists directory: " + dir.getAbsolutePath());
    }
    return dir;
  }

  /**
   * Shows a playlist file chooser inside the in-window overlay rather than as a separate dialog
   * window. While the frame holds full-screen exclusive mode, Windows does not reliably give a new
   * top-level window the foreground (see the overlay comment above), and the first JFileChooser is
   * slow to build, so a dialog could open behind another window. Embedding the chooser avoids that.
   *
   * @param onApprove receives the chosen file once the overlay has closed
   */
  private void showPlaylistChooser(String title, boolean save, Color accent,
      java.util.function.Consumer<File> onApprove) {

    JFileChooser chooser = new JFileChooser(playlistsDir());
    chooser.setDialogType(save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
    chooser.setDialogTitle(title);
    chooser.setApproveButtonText(save ? "Save" : "Load");
    chooser.setFileFilter(
        new FileNameExtensionFilter("Playlist files (*.pl, *.txt)", "pl", "txt"));
    chooser.setPreferredSize(new Dimension(Math.max(640, getWidth() * 3 / 5),
        Math.max(420, getHeight() * 3 / 5)));

    chooser.addActionListener(e -> {
      if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
        File selected = chooser.getSelectedFile();
        hideOverlay();
        if (selected != null) {
          onApprove.accept(selected);
        }
      } else if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) {
        hideOverlay();
      }
    });

    JLabel titleLabel = new JLabel(title);
    titleLabel.setForeground(accent);
    titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
    titleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

    JPanel content = new JPanel(new BorderLayout(0, 12));
    content.setOpaque(false);
    content.add(titleLabel, BorderLayout.NORTH);
    content.add(chooser, BorderLayout.CENTER);

    // Escape cancels, like the other admin overlay forms (instead of leaving the Admin panel).
    content.registerKeyboardAction(e -> hideOverlay(),
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);

    showOverlayForm(accent, content);
    SwingUtilities.invokeLater(() -> {
      JTextField fileNameField = findFirstTextField(chooser);
      if (fileNameField != null) {
        fileNameField.requestFocusInWindow();
      } else {
        chooser.requestFocusInWindow();
      }
    });
  }

  private static JTextField findFirstTextField(java.awt.Container container) {

    for (Component child : container.getComponents()) {
      if (child instanceof JTextField textField) {
        return textField;
      }
      if (child instanceof java.awt.Container nested) {
        JTextField found = findFirstTextField(nested);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private void doLoadPlaylist() {
    showPlaylistChooser("Load Playlist", false, ColorTheme.get().accentGreen, this::loadPlaylist);
  }

  private void loadPlaylist(File file) {

    String filename = file.getAbsolutePath();
    SwingSecurityUtil.runAsync(() -> {
      try {

        LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest =
            new LoadPlaylistIntoQueueRequest(SongQueueService.LOCAL_USERNAME, filename, 1);
        Integer numSongs = this.songQueueService.loadPlaylistIntoQueue(
            songQueueService.getOwnLocationId(), loadPlaylistIntoQueueRequest);

        SwingUtilities.invokeLater(() -> showOverlayMessage("Playlist Loaded",
            "Queued " + numSongs + " songs from " + file.getName()
                + " (songs not found in the library are listed in the log)",
            ColorTheme.get().accentGreen));

      } catch (Exception ex) {
        ex.printStackTrace();
        SwingUtilities.invokeLater(() -> showOverlayMessage("Error",
            "Failed to load playlist: " + ex.getMessage(), ColorTheme.get().accentRed));
      }
    });
  }

  private void doSavePlaylist() {
    showPlaylistChooser("Save Playlist", true, ColorTheme.get().accentBlue, this::savePlaylist);
  }

  private void savePlaylist(File file) {

    String lowerName = file.getName().toLowerCase(java.util.Locale.ROOT);
    boolean hasPlaylistExtension = lowerName.endsWith(".pl") || lowerName.endsWith(".txt");
    String filename = file.getAbsolutePath() + (hasPlaylistExtension ? "" : ".pl");
    SwingSecurityUtil.runAsync(() -> {
      try {
        this.songQueueService.saveQueueAsPlaylist(songQueueService.getOwnLocationId(), filename);

        SwingUtilities.invokeLater(() -> showOverlayMessage("Playlist Saved", "Saved " + filename,
            ColorTheme.get().accentGreen));

      } catch (Exception ex) {
        ex.printStackTrace();
        SwingUtilities.invokeLater(() -> showOverlayMessage("Error",
            "Failed to save playlist: " + ex.getMessage(), ColorTheme.get().accentRed));
      }
    });
  }

  /**
   * Synchronously loads the full album list from the song library service into the list model.
   * Called once from the constructor and again from {@link #refreshAlbumList()} after a rescan.
   * Runs on the EDT; the security context is already present via
   * {@code LocalAuthenticatedEventQueue}.
   */
  private void loadAlbumList() {
    try {
      List<AlbumDto> albums = songLibraryService.getAlbums(songLibraryService.getOwnLocationId());
      albumListModel.clear();
      albumsWithInvalidMetadata.clear();
      if (albums != null) {
        for (AlbumDto album : albums) {
          albumListModel.addElement(album);
          if (isMetadataInvalid(album)) {
            albumsWithInvalidMetadata.add(album);
          }
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /** Triggers a fresh reload of the album list (used after a library rescan). */
  public void refreshAlbumList() {
    loadAlbumList();
  }

  private boolean isMetadataInvalid(AlbumDto album) {

    // Check missing, blank, or fallback release dates (1950)
    if (album.releaseDate() == null || album.releaseDate().isBlank()
        || "1950".equals(album.releaseDate().trim())) {
      return true;
    }

    // Check missing, blank, or fallback record label designations (Unknown)
    if (album.recordLabel() == null || album.recordLabel().isBlank()
        || "Unknown".equalsIgnoreCase(album.recordLabel().trim())) {
      return true;
    }

    // Check physical sizing dimensions on tracking image path assets (At least 250x250)
    String coverArtPath = album.coverArtPath();
    if (coverArtPath == null || coverArtPath.isBlank()) {
      return true;
    }

    try {
      File imgFile = new File(coverArtPath);
      if (!imgFile.exists()) {
        return true;
      }

      java.awt.image.BufferedImage bufImage = ImageIO.read(imgFile);
      if (bufImage == null) {
        return true;
      }

      if (bufImage.getWidth() < 250 || bufImage.getHeight() < 250) {
        return true;
      }
    } catch (Exception e) {
      // Inability to successfully process or stream structural dimensions flags item as invalid
      return true;
    }
    return false;
  }

  public void setQueue(List<SongQueueEntryDto> queue) {

    refreshQueueList(songQueueService.getQueuedSongs(songQueueService.getOwnLocationId()));
  }

  private void refreshQueueList(List<SongQueueEntryDto> queue) {

    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> refreshQueueList(queue));
      return;
    }
    try {
      int sel = queueList.getSelectedIndex();
      queueListModel.clear();
      if (queue != null) {
        // Appending a fully materialized snapshot
        queue.forEach(queueListModel::addElement);
      }
      if (sel >= 0 && sel < queueListModel.getSize()) {
        queueList.setSelectedIndex(sel);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CELL RENDERERS
  // ─────────────────────────────────────────────────────────────────────────
  /**
   * Single-line album cell renderer. Displays artist, album name, and genre on one row with no
   * thumbnail — fast to render regardless of library size.
   *
   * <p>
   * Format: {@code ArtistName — AlbumName  [Genre]}
   */
  private static class AlbumCellRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
        boolean isSelected, boolean cellHasFocus) {

      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof AlbumDto album) {
        String artist = album.artistName() != null ? album.artistName() : "";
        String display =
            AlbumGridPanel.albumDisplayName(album.albumName(), album.genreName());
        setText(artist + " \u2014 " + display);
      }

      setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminAlbum));

      if (isSelected) {
        setBackground(ColorTheme.get().bgListSelected);
        setForeground(ColorTheme.get().accentBlue);
      } else {
        setBackground(index % 2 == 0 ? ColorTheme.get().bgList : ColorTheme.get().bgListRowAlt);
        setForeground(ColorTheme.get().textPrimary);
      }
      setOpaque(true);
      setBorder(new EmptyBorder(3, 8, 3, 8));
      return this;
    }
  }

  // QueueCellRenderer removed — replaced by shared SongTrackCellRenderer.

  // ─────────────────────────────────────────────────────────────────────────
  // WIDGET HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds the "SONG QUEUE" section header with a priority-colour legend panel aligned to the
   * right/EAST — identical in chrome to {@link #buildAlbumSectionHeader()} but without a filter
   * field and with the legend in its place.
   */
  private JPanel buildQueueSectionHeader() {
    Color accent = ColorTheme.get().accentGreen;
    JPanel header = new JPanel(new BorderLayout(8, 0)) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(ColorTheme.get().bgAdminHeader);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(accent);
        g2.fillRect(0, getHeight() - 2, getWidth(), 2);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    header.setOpaque(false);
    header.setBorder(new EmptyBorder(6, 10, 6, 10));

    JLabel lbl = new JLabel("Queue:");
    lbl.setForeground(accent);
    lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));

    JPanel westWrap = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    westWrap.setOpaque(false);
    westWrap.add(lbl);
    westWrap.add(queueLockedLabel);

    header.add(westWrap, BorderLayout.WEST);
    header.add(SongTrackCellRenderer.buildPriorityLegend(), BorderLayout.EAST);
    return header;
  }

  /**
   * Builds the "JUKEBOX LIST" section header that includes a compact filter text field on the
   * right. Typing in the field scrolls the album list to the first entry whose display name
   * contains the entered text (case-insensitive). Touching (releasing on) the field opens an
   * on-screen keyboard overlay, since touchscreen deployments have no physical keyboard.
   */
  private JPanel buildAlbumSectionHeader() {
    JPanel header = new JPanel(new BorderLayout(8, 0)) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(ColorTheme.get().bgAdminHeader);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(ColorTheme.get().accentBlue);
        g2.fillRect(0, getHeight() - 2, getWidth(), 2);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    header.setOpaque(false);
    header.setBorder(new EmptyBorder(6, 10, 6, 10));

    JLabel lbl = new JLabel("Albums:");
    lbl.setForeground(ColorTheme.get().accentBlue);
    lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
    header.add(lbl, BorderLayout.WEST);

    // ── Filter field ─────────────────────────────────────────────────────
    javax.swing.JTextField filterField = new javax.swing.JTextField();
    filterField.setName(UiComponentNames.ADMIN_FILTER_FIELD);
    filterField
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminSection - 1));
    filterField.setForeground(ColorTheme.get().textPrimary);
    filterField.setBackground(ColorTheme.get().adminFilterFieldBg);
    filterField.setCaretColor(ColorTheme.get().accentBlue);
    filterField.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1),
        new EmptyBorder(2, 6, 2, 6)));
    filterField.setPreferredSize(
        new Dimension(LayoutTheme.get().adminFilterFieldW, LayoutTheme.get().adminFilterFieldH));
    filterField.setMaximumSize(
        new Dimension(LayoutTheme.get().adminFilterFieldW, LayoutTheme.get().adminFilterFieldH));
    filterField.setToolTipText("Filter — jumps to first album containing the text");

    // Touchscreen deployments have no physical keyboard, so a touch on the field opens the
    // on-screen keyboard overlay. Triggered on release, like every other touch-driven control.
    filterField.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && !overlayScrim.isVisible()) {
          showAlbumSearchKeyboard(filterField);
        }
      }
    });

    // Jump to the first album whose display name contains the filter text
    filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      private void jumpToMatch() {
        String filter = filterField.getText().trim().toLowerCase();
        if (filter.isEmpty())
          return;
        for (int i = 0; i < albumListModel.getSize(); i++) {
          AlbumDto album = albumListModel.getElementAt(i);
          String display = AlbumGridPanel
              .albumDisplayName(album.albumName(), album.genreName()).toLowerCase();
          if (display.contains(filter)) {
            albumList.setSelectedIndex(i);
            albumList.ensureIndexIsVisible(i);
            return;
          }
        }
      }

      @Override
      public void insertUpdate(javax.swing.event.DocumentEvent e) {
        jumpToMatch();
      }

      @Override
      public void removeUpdate(javax.swing.event.DocumentEvent e) {
        jumpToMatch();
      }

      @Override
      public void changedUpdate(javax.swing.event.DocumentEvent e) {
        jumpToMatch();
      }
    });

    JPanel filterWrapper = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
    filterWrapper.setOpaque(false);
    JLabel filterLbl = new JLabel("Filter: ");
    filterLbl.setForeground(ColorTheme.get().textMuted);
    filterLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminArtist));
    filterWrapper.add(filterLbl);
    filterWrapper.add(filterField);
    header.add(filterWrapper, BorderLayout.EAST);

    return header;
  }

  /**
   * Shows the album-search pseudo dialog in the in-window overlay: an entry field seeded with the
   * current filter text, the shared on-screen {@link KeyboardPanel}, and Search / Cancel buttons.
   * Search copies the entry into {@code filterField} (whose document listener jumps the album list
   * to the first match); Cancel leaves the filter unchanged. The entry field also accepts a physical
   * keyboard, with Enter acting as Search.
   */
  private void showAlbumSearchKeyboard(javax.swing.JTextField filterField) {

    JLabel title = new JLabel("Search Albums");
    title.setForeground(ColorTheme.get().accentBlue);
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSection));
    title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

    javax.swing.JTextField entry = new javax.swing.JTextField(filterField.getText());
    entry.setName(UiComponentNames.ADMIN_ALBUM_SEARCH_FIELD);
    entry.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminSection));
    entry.setForeground(ColorTheme.get().textPrimary);
    entry.setBackground(ColorTheme.get().adminFilterFieldBg);
    entry.setCaretColor(ColorTheme.get().accentBlue);
    entry.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1),
        new EmptyBorder(6, 10, 6, 10)));

    Runnable search = () -> {
      hideOverlay();
      filterField.setText(entry.getText());
    };
    entry.addActionListener(e -> search.run());

    KeyboardPanel keyboard = new KeyboardPanel(new KeyboardPanel.KeyboardListener() {
      @Override
      public void onCharacter(String ch) {
        setEntry(entry.getText() + ch);
      }

      @Override
      public void onBackspace() {
        String text = entry.getText();
        if (!text.isEmpty()) {
          setEntry(text.substring(0, text.length() - 1));
        }
      }

      @Override
      public void onClear() {
        setEntry("");
      }

      @Override
      public void onSpace() {
        setEntry(entry.getText() + " ");
      }

      /** Updates the entry and hands focus back so a physical keyboard keeps working too. */
      private void setEntry(String text) {
        entry.setText(text);
        entry.requestFocusInWindow();
      }
    });
    keyboard.useNaturalWidth();

    JButton searchBtn = overlayButton("Search");
    searchBtn.setName(UiComponentNames.ADMIN_ALBUM_SEARCH_BUTTON);
    searchBtn.addActionListener(e -> search.run());

    JButton cancelBtn = overlayButton("Cancel");
    cancelBtn.setName(UiComponentNames.ADMIN_ALBUM_SEARCH_CANCEL_BUTTON);
    cancelBtn.addActionListener(e -> hideOverlay());

    JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
    buttonRow.setOpaque(false);
    buttonRow.add(searchBtn);
    buttonRow.add(cancelBtn);

    JPanel top = new JPanel(new BorderLayout(0, 10));
    top.setOpaque(false);
    top.add(title, BorderLayout.NORTH);
    top.add(entry, BorderLayout.CENTER);

    JPanel content = new JPanel(new BorderLayout(0, 12));
    content.setOpaque(false);
    content.add(top, BorderLayout.NORTH);
    content.add(keyboard, BorderLayout.CENTER);
    content.add(buttonRow, BorderLayout.SOUTH);

    // Escape cancels, like the other admin overlay forms (instead of leaving the Admin panel).
    content.registerKeyboardAction(e -> hideOverlay(),
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);

    showOverlayForm(ColorTheme.get().accentBlue, content);
    SwingUtilities.invokeLater(() -> {
      entry.requestFocusInWindow();
      entry.setCaretPosition(entry.getText().length());
    });
  }

  /**
   * Vertical BoxLayout strip with uniform top padding — the structural container for both the WEST
   * and EAST button columns.
   */
  private static JPanel buildButtonStrip() {
    JPanel strip = new JPanel();
    strip.setOpaque(false);
    strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
    return strip;
  }

  /** Thin vertical spacer for visual grouping inside a button strip. */
  private static javax.swing.Box.Filler verticalSpacer(int height) {
    return (javax.swing.Box.Filler) Box.createRigidArea(new Dimension(0, height));
  }

  /**
   * Fixed-size side-panel button with the same AMI 3-D gradient style.
   *
   * <p>
   * The {@code label} string may contain a {@code \n} to split across two lines; the first line is
   * rendered in a slightly larger font as an icon/symbol row and the second as the text label —
   * matching the reference screenshot's compact two-line button style.
   *
   * @param name Component name (see {@link UiComponentNames}), used to locate the button in tests.
   * @param label Button text; use {@code \n} for a two-line layout.
   * @param accent Border/gradient accent colour.
   * @param action {@code ActionListener} fired on click.
   */
  private static JButton sideButton(String name, String label, Color accent,
      java.awt.event.ActionListener action) {

    // Split into icon line + text line if a newline is present
    final String[] parts = label.split("\n", 2);
    final String line1 = parts[0];
    final String line2 = parts.length > 1 ? parts[1] : null;

    JButton btn = new JButton() {
      private static final long serialVersionUID = 1L;
      private boolean hovered = false;
      {
        addMouseListener(new java.awt.event.MouseAdapter() {
          public void mouseEntered(java.awt.event.MouseEvent e) {
            hovered = true;
            repaint();
          }

          public void mouseExited(java.awt.event.MouseEvent e) {
            hovered = false;
            repaint();
          }
        });
      }

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int arc = 8;
        int shadowH = 3;
        int visH = h - shadowH;
        int shelfH = Math.round(visH * 0.22f);
        int faceH = visH - shelfH;

        boolean enabled = isEnabled();
        // Shared pressed motif (see PressedEffect): the body sinks onto its drop shadow, the face
        // and border darken, the specular edge disappears and an inset shadow appears.
        boolean pressed = enabled && PressedEffect.isPressed(this);
        boolean hoverLook = enabled && hovered && !pressed;
        Color gradTop = enabled ? accent.darker() : ColorTheme.get().btnGreyFace.brighter();
        Color gradBottom = enabled ? accent.darker().darker() : ColorTheme.get().btnGreyFace;
        Color borderColor = enabled ? accent : ColorTheme.get().btnGreyBorder;

        // A pressed button sinks toward its shadow; the shadow's bottom edge stays put.
        int drop = pressed ? SIDE_BTN_PRESS_DROP : 0;
        g2.translate(0, drop);

        // Drop-shadow
        g2.setColor(ColorTheme.get().adminSideBtnShadow);
        g2.fillRoundRect(1, shadowH - drop, w - 2, visH, arc, arc);

        // Shelf
        g2.setColor(ColorTheme.get().adminSideBtnShelf);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // Face gradient
        Color top = pressed ? PressedEffect.darken(gradTop)
            : hoverLook ? gradTop.brighter() : gradTop;
        Color bot = pressed ? PressedEffect.darken(gradBottom)
            : hoverLook ? gradBottom.brighter() : gradBottom;
        g2.setPaint(new GradientPaint(0, 0, top, 0, faceH, bot));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // Specular edge — a sunken button doesn't catch the light
        if (!pressed) {
          if (enabled) {
            g2.setColor(new Color(Math.min(255, accent.getRed() + 80),
                Math.min(255, accent.getGreen() + 80), Math.min(255, accent.getBlue() + 80), 160));
          } else {
            g2.setColor(new Color(255, 255, 255, 30));
          }
          g2.setStroke(new java.awt.BasicStroke(1f));
          g2.drawLine(arc, 1, w - arc - 1, 1);
        }

        // Inset shadow — the pressed face now sits below the surrounding surface
        if (pressed) {
          PressedEffect.paintInsetShadow(g2, 1, 0, w - 2, visH, arc);
        }

        // Border
        g2.setColor(pressed ? PressedEffect.darken(accent)
            : hoverLook ? accent.brighter() : borderColor);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        // Text — one or two lines centred on the face
        g2.setColor(enabled ? ColorTheme.get().textPrimary : ColorTheme.get().btnGreyText);
        if (line2 == null) {
          // Single line
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSideBtn1));
          java.awt.FontMetrics fm = g2.getFontMetrics();
          g2.drawString(line1, (w - fm.stringWidth(line1)) / 2,
              (faceH - fm.getHeight()) / 2 + fm.getAscent());
        } else {
          // Two lines: symbol on top, text label below
          Font f1 = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSideBtn1);
          Font f2 = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSideBtn2);
          java.awt.FontMetrics fm1 = g2.getFontMetrics(f1);
          java.awt.FontMetrics fm2 = g2.getFontMetrics(f2);
          int totalH = fm1.getHeight() + fm2.getHeight() - 2;
          int startY = (faceH - totalH) / 2 + fm1.getAscent();
          g2.setFont(f1);
          g2.drawString(line1, (w - fm1.stringWidth(line1)) / 2, startY);
          g2.setFont(f2);
          g2.drawString(line2, (w - fm2.stringWidth(line2)) / 2,
              startY + fm1.getDescent() + fm2.getAscent());
        }
        g2.dispose();
      }
    };

    btn.setName(name);
    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminSideBtn1));
    btn.setForeground(ColorTheme.get().textPrimary);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setOpaque(false);
    btn.setMargin(new Insets(0, 0, 0, 0));
    // Fixed size — both preferred and maximum are clamped so BoxLayout doesn't stretch them
    btn.setPreferredSize(BTN_SIZE);
    btn.setMaximumSize(BTN_SIZE);
    btn.setMinimumSize(BTN_SIZE);
    btn.setCursor(KioskCursors.hand());
    btn.addActionListener(action);
    return btn;
  }

  /**
   * Scroll pane for the admin lists and tables. Uses the same big touch-friendly scroll bar as the
   * Legacy view's CD panels unless {@code user-interface.use-big-scrollbars} is off, in which case
   * it keeps the look and feel's normal bar on the admin background colour.
   */
  private static JScrollPane darkScrollPane(java.awt.Component view) {
    JScrollPane sp = new JScrollPane(view);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setBorder(BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1));
    sp.getVerticalScrollBar().setBackground(ColorTheme.get().adminScrollBarBg);
    ScrollBars.apply(sp.getVerticalScrollBar());
    sp.getHorizontalScrollBar().setVisible(false);
    return sp;
  }
}
