package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class AdminPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Button icons ──────────────────────────────────────────────────────────
  private static final String SHUFFLE_ICON = new String(Character.toChars(0x1F500));
  // Plus sign: ➕ U+271A

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_GOLD = new Color(255, 200, 0);
  private static final Color ACCENT_GREEN = new Color(60, 210, 80);
  private static final Color ACCENT_RED = new Color(220, 60, 60);
  private static final Color ACCENT_ORANGE = new Color(255, 140, 0);
  private static final Color ACCENT_VIOLET = new Color(180, 80, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_MUTED = new Color(160, 165, 180);
  private static final Color LIST_BG = new Color(10, 12, 18);
  private static final Color LIST_SEL_BG = new Color(0, 60, 80);
  private static final Color ROW_ALT = new Color(18, 20, 28);
  private static final Color SEPARATOR = new Color(40, 44, 60);

  /**
   * Fixed size for every sidebar button — narrow enough to leave the lists dominant, tall enough to
   * be touch-friendly and readable. The width is intentionally capped rather than Integer.MAX_VALUE
   * so the sidebar columns stay anchored.
   */
  private static final Dimension BTN_SIZE = new Dimension(84, 42);

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;
  private final CreditManager creditManager;
  private final ImageLoader imageLoader;
  private final Frame ownerFrame;

  // ── Album list ────────────────────────────────────────────────────────────
  private final DefaultListModel<AlbumDto> albumListModel = new DefaultListModel<>();
  private final JList<AlbumDto> albumList = new JList<>(albumListModel);

  // ── Queue list ────────────────────────────────────────────────────────────
  private final DefaultListModel<SongQueueEntryDto> queueListModel = new DefaultListModel<>();
  private final JList<SongQueueEntryDto> queueList = new JList<>(queueListModel);

  // ── Header credit label (refreshed on change) ─────────────────────────────
  private JLabel creditCountLabel;

  // ── Popularity thresholds (passed through to the queue cell renderer) ─────
  private int popularityT1 = 1;
  private int popularityT2 = 5;
  private int popularityT3 = 15;

  // ── Invalid Metadata Tracking Cache (Item #1) ─────────────────────────────
  private final List<AlbumDto> albumsWithInvalidMetadata = new ArrayList<>();

  public AdminPanel(Frame ownerFrame, SongLibraryService songLibraryService,
      SongQueueService songQueueService, SongPlayerService songPlayerService,
      CreditManager creditManager, ImageLoader imageLoader) {

    this.ownerFrame = ownerFrame;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    this.creditManager = creditManager;
    this.imageLoader = imageLoader;

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    add(buildHeaderBar(), BorderLayout.NORTH);
    add(buildLibraryButtons(), BorderLayout.WEST);
    add(buildListsCenter(), BorderLayout.CENTER);
    add(buildQueueButtons(), BorderLayout.EAST);

    // Keep the header credit counter live
    creditManager.addListener(() -> SwingUtilities.invokeLater(() -> {
      if (creditCountLabel != null) {
        creditCountLabel.setText(String.valueOf(creditManager.getCredits()));
      }
    }));

    refreshAlbumList();
    refreshQueueList();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HEADER BAR
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildHeaderBar() {

    JPanel bar = new JPanel(new BorderLayout(16, 0)) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(8, 8, 14));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(SEPARATOR);
        g2.fillRect(0, getHeight() - 1, getWidth(), 1);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    bar.setOpaque(false);
    bar.setBorder(new EmptyBorder(10, 14, 10, 14));

    JLabel title = new JLabel("⚙  ADMIN PANEL");
    title.setForeground(ACCENT_GOLD);
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));

    JPanel creditBadge = new JPanel(new BorderLayout(6, 0));
    creditBadge.setOpaque(false);
    JLabel crLabel = new JLabel("CREDITS:");
    crLabel.setForeground(TEXT_MUTED);
    crLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
    creditCountLabel = new JLabel(String.valueOf(creditManager.getCredits()));
    creditCountLabel.setForeground(ACCENT_GOLD);
    creditCountLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    creditBadge.add(crLabel, BorderLayout.WEST);
    creditBadge.add(creditCountLabel, BorderLayout.CENTER);

    bar.add(title, BorderLayout.WEST);
    bar.add(creditBadge, BorderLayout.EAST);
    return bar;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CENTER — side-by-side lists
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildListsCenter() {

    JPanel center = new JPanel(new GridLayout(1, 2, 6, 0));
    center.setOpaque(false);
    center.setBorder(new EmptyBorder(6, 0, 6, 0));

    // ── Album list ────────────────────────────────────────────────────────
    albumList.setOpaque(true);
    albumList.setBackground(LIST_BG);
    albumList.setForeground(TEXT_PRIMARY);
    albumList.setSelectionBackground(LIST_SEL_BG);
    albumList.setSelectionForeground(Color.WHITE);
    albumList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    albumList.setFixedCellHeight(36);
    albumList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    albumList.setCellRenderer(new AlbumCellRenderer());

    JPanel albumPane = new JPanel(new BorderLayout(0, 4));
    albumPane.setOpaque(false);
    albumPane.add(sectionHeader("JUKEBOX LIST", ACCENT_BLUE), BorderLayout.NORTH);
    albumPane.add(darkScrollPane(albumList), BorderLayout.CENTER);

    // ── Queue list ────────────────────────────────────────────────────────
    queueList.setOpaque(true);
    queueList.setBackground(LIST_BG);
    queueList.setForeground(TEXT_PRIMARY);
    queueList.setSelectionBackground(LIST_SEL_BG);
    queueList.setSelectionForeground(Color.WHITE);
    queueList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
    queueList.setFixedCellHeight(44);
    queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    queueList.setCellRenderer(new QueueCellRenderer());

    JPanel queuePane = new JPanel(new BorderLayout(0, 4));
    queuePane.setOpaque(false);
    queuePane.add(sectionHeader("SONG QUEUE", ACCENT_GREEN), BorderLayout.NORTH);
    queuePane.add(darkScrollPane(queueList), BorderLayout.CENTER);

    center.add(albumPane);
    center.add(queuePane);
    return center;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // WEST — library action buttons (operate on selected album)
  // ─────────────────────────────────────────────────────────────────────────
  /**
   * Returns a narrow vertical strip of fixed-size buttons anchored to the WEST edge. Every button
   * acts on the currently selected album and delegates to {@link SongLibraryService} or
   * system-level operations.
   *
   * <p>
   * Button order (top → bottom), matching the reference screenshot left column:
   * <ol>
   * <li>Report / Dupes / Add — skipped (not in scope; placeholders left commented)</li>
   * <li><b>Add to Queue</b> — {@code SongQueueService.addAlbumToQueue}</li>
   * <li><b>Edit Album</b> — {@code EditAlbumDialog}</li>
   * <li><b>Reset Stats</b> — {@code SongLibraryService.resetSongStatistics}</li>
   * <li><b>Rescan</b> — {@code SongLibraryService.scanFileSystemForSongs}</li>
   * <li><b>Minimize</b> — iconify the owner frame</li>
   * <li><b>Exit</b> — {@code System.exit(0)}</li>
   * </ol>
   */
  private JPanel buildLibraryButtons() {

    JPanel strip = buildButtonStrip();

    strip.add(sideButton("Queue\nAlbum", ACCENT_GREEN, e -> doAddAlbumToQueue()));
    strip.add(sideButton("Edit\nAlbum", ACCENT_GOLD, e -> doEditAlbum()));
    strip.add(sideButton("Reset\nStats", ACCENT_ORANGE, e -> doResetStats()));
    strip.add(sideButton("Rescan\nLibrary", ACCENT_VIOLET, e -> doRescan()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton("⊟ Minimize", ACCENT_BLUE, e -> doMinimize()));
    strip.add(sideButton("✕ Exit", ACCENT_RED, e -> doExit()));

    // Wrap so the strip itself is opaque-background-free but has a right border separator
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 0, 1, SEPARATOR), new EmptyBorder(6, 6, 6, 6)));
    wrapper.add(strip, BorderLayout.CENTER);
    return wrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // EAST — queue action buttons (operate on selected queue entry)
  // ─────────────────────────────────────────────────────────────────────────
  /**
   * Returns a narrow vertical strip of fixed-size buttons anchored to the EAST edge. Every button
   * acts on the currently selected queue entry or the queue as a whole and delegates to
   * {@link SongQueueService} / {@link SongPlayerService}.
   *
   * <p>
   * Button order (top → bottom), matching the reference screenshot right column:
   * <ol>
   * <li><b>Next</b> — advance to next track</li>
   * <li><b>Pause</b> — pause playback</li>
   * <li><b>Play</b> — play selected queue entry immediately</li>
   * <li><b>Move ▲</b> — {@code SongQueueService.moveSongUpInQueue}</li>
   * <li><b>Move ▼</b> — {@code SongQueueService.moveSongDownInQueue}</li>
   * <li><b>Remove</b> — {@code SongQueueService.removeSongDownFromQueue}</li>
   * <li><b>Flush</b> — {@code SongQueueService.flushQueue}</li>
   * <li><b>Rndm</b> — {@code SongQueueService.randomizeQueue}</li>
   * <li><b>+ Credit</b> — {@code CreditManager.addDollar}</li>
   * <li><b>- Credit</b> — {@code CreditManager.deductCredits}</li>
   * <li><b>Load PL</b> — load playlist from file</li>
   * <li><b>Save PL</b> — save playlist to file</li>
   * </ol>
   */
  private JPanel buildQueueButtons() {

    JPanel strip = buildButtonStrip();

    // ── Playback ──────────────────────────────────────────────────────────
    strip.add(sideButton("▶▶\nNext", ACCENT_GREEN, e -> doPlayNextTrack()));
    strip.add(sideButton("❚❚\nPause", ACCENT_BLUE, e -> doPause()));
    strip.add(sideButton("▶\nPlay", ACCENT_GREEN, e -> doPlaySelected()));

    strip.add(verticalSpacer(8));

    // ── Position ──────────────────────────────────────────────────────────
    strip.add(sideButton("▲\nMove Up", ACCENT_BLUE, e -> doMoveUp()));
    strip.add(sideButton("▼\nMove Dn", ACCENT_BLUE, e -> doMoveDown()));
    strip.add(sideButton("✕\nRemove", ACCENT_RED, e -> doRemoveSong()));

    strip.add(verticalSpacer(8));

    // ── Queue management ──────────────────────────────────────────────────
    strip.add(sideButton("🗑\nFlush", ACCENT_RED, e -> doFlushQueue()));
    strip.add(sideButton(SHUFFLE_ICON + "\nShuffle", ACCENT_VIOLET, e -> doRandomizeQueue()));

    strip.add(verticalSpacer(8));

    // ── Credits ───────────────────────────────────────────────────────────
    strip.add(sideButton("＋\nCredits", ACCENT_GREEN, e -> doIncrementCredits()));
    strip.add(sideButton("－\nCredits", ACCENT_ORANGE, e -> doDecrementCredits()));

    strip.add(Box.createVerticalGlue());

    // ── Playlist I/O ──────────────────────────────────────────────────────
    strip.add(sideButton("📂\nLoad Playlist", ACCENT_GOLD, e -> doLoadPlaylist()));
    strip.add(sideButton("💾\nSave Playlist", ACCENT_GOLD, e -> doSavePlaylist()));

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 1, 0, 0, SEPARATOR), new EmptyBorder(6, 6, 6, 6)));
    wrapper.add(strip, BorderLayout.CENTER);
    return wrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ALBUM ACTIONS (SongLibraryService)
  // ─────────────────────────────────────────────────────────────────────────

  private void doAddAlbumToQueue() {
    AlbumDto selected = albumList.getSelectedValue();
    if (selected == null) {
      JOptionPane.showMessageDialog(this, "Please select an album first.", "No Selection",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    CompletableFuture.runAsync(() -> {
      try {
        AlbumDto full = songLibraryService.getAlbumById(selected.getAlbumId());
        songQueueService.addAlbumToQueue(new AddAlbumToQueueRequest(full.getAlbumId(), 1));
        SwingUtilities.invokeLater(this::refreshQueueList);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
  }

  /**
   * Refactored to pass the selected album and the compiled invalid metadata master list to
   * EditAlbumDialog matching Item #1 specifications.
   */
  private void doEditAlbum() {
    AlbumDto selected = albumList.getSelectedValue();
    if (selected == null) {
      JOptionPane.showMessageDialog(this, "Please select an album first.", "No Selection",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    // Instantiates matching the parameters designed in Item #1
    EditAlbumDialog dialog =
        new EditAlbumDialog(ownerFrame, songLibraryService, selected, albumsWithInvalidMetadata);
    dialog.setVisible(true);
    albumList.repaint();
  }

  private void doResetStats() {
    int confirm = JOptionPane.showConfirmDialog(this, "Reset all song play statistics?",
        "Reset Statistics", JOptionPane.YES_NO_OPTION);
    if (confirm == JOptionPane.YES_OPTION) {
      CompletableFuture.runAsync(() -> {
        try {
          songLibraryService.resetSongStatistics();
          SwingUtilities.invokeLater(this::refreshQueueList);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
    }
  }

  private void doRescan() {
    int confirm =
        JOptionPane.showConfirmDialog(this, "Rescan the music library? This may take a moment.",
            "Rescan Library", JOptionPane.YES_NO_OPTION);
    if (confirm == JOptionPane.YES_OPTION) {
      CompletableFuture.runAsync(() -> {
        try {
          songLibraryService.scanFileSystemForSongs();
          SwingUtilities.invokeLater(this::refreshAlbumList);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
    }
  }

  private void doMinimize() {
    SwingUtilities.invokeLater(() -> {
      GraphicsDevice gd =
          GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      gd.setFullScreenWindow(null);
      ownerFrame.setState(JFrame.ICONIFIED);
    });
  }

  private void doExit() {
    int confirm = JOptionPane.showConfirmDialog(this, "Exit JukeANator?", "Confirm Exit",
        JOptionPane.YES_NO_OPTION);
    if (confirm == JOptionPane.YES_OPTION) {
      System.exit(0);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // QUEUE ACTIONS (SongQueueService / SongPlayerService)
  // ─────────────────────────────────────────────────────────────────────────

  private void doPlayNextTrack() {
    try {
      songPlayerService.playNextTrack();
      refreshQueueList();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doPause() {
    try {
      songPlayerService.pause();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doPlaySelected() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null) {
      JOptionPane.showMessageDialog(this, "Please select a song in the queue first.",
          "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }
    try {
      int idx = queueList.getSelectedIndex();
      for (int i = 0; i < idx; i++) {
        songQueueService.moveSongUpInQueue(new ChangeSongQueueRequest(
            selected.getSong().getAlbumId(), selected.getSong().getSongId()));
      }
      songPlayerService.playNextTrack();
      refreshQueueList();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doMoveUp() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null)
      return;
    int idx = queueList.getSelectedIndex();
    try {
      songQueueService.moveSongUpInQueue(new ChangeSongQueueRequest(selected.getSong().getAlbumId(),
          selected.getSong().getSongId()));
      refreshQueueList();
      queueList.setSelectedIndex(Math.max(0, idx - 1));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doMoveDown() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null)
      return;
    int idx = queueList.getSelectedIndex();
    try {
      songQueueService.moveSongDownInQueue(new ChangeSongQueueRequest(
          selected.getSong().getAlbumId(), selected.getSong().getSongId()));
      refreshQueueList();
      queueList.setSelectedIndex(Math.min(queueListModel.getSize() - 1, idx + 1));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doRemoveSong() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null) {
      JOptionPane.showMessageDialog(this, "Please select a song in the queue first.",
          "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }
    try {
      songQueueService.removeSongDownFromQueue(new ChangeSongQueueRequest(
          selected.getSong().getAlbumId(), selected.getSong().getSongId()));
      refreshQueueList();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doFlushQueue() {
    int confirm = JOptionPane.showConfirmDialog(this, "Clear the entire song queue?", "Flush Queue",
        JOptionPane.YES_NO_OPTION);
    if (confirm == JOptionPane.YES_OPTION) {
      try {
        songQueueService.flushQueue();
        refreshQueueList();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  private void doRandomizeQueue() {
    try {
      songQueueService.randomizeQueue();
      refreshQueueList();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  // ── Credits ───────────────────────────────────────────────────────────────

  private void doIncrementCredits() {
    creditManager.addDollar();
  }

  private void doDecrementCredits() {
    creditManager.deductCredits(1);
  }

  // ── Playlist ──────────────────────────────────────────────────────────────

  private void doLoadPlaylist() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Load Playlist");
    chooser.setFileFilter(new FileNameExtensionFilter("Playlist files (*.txt)", "txt"));
    chooser.setCurrentDirectory(new File(""));

    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
      return;

    String filename = chooser.getSelectedFile().getAbsolutePath();
    CompletableFuture.runAsync(() -> {
      try {

        this.songQueueService.loadPlaylistIntoQueue(filename);

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Loaded " + filename,
            " playlist", JOptionPane.INFORMATION_MESSAGE));

      } catch (Exception ex) {
        ex.printStackTrace();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
            "Failed to load playlist: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
      }
    });

  }

  private void doSavePlaylist() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Save Playlist");
    chooser.setFileFilter(new FileNameExtensionFilter("Playlist files (*.txt)", "txt"));

    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
      return;

    String filename = chooser.getSelectedFile().getAbsolutePath();
    CompletableFuture.runAsync(() -> {
      try {
        this.songQueueService.saveQueueAsPlaylist(filename);

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Saved " + filename,
            " playlist", JOptionPane.INFORMATION_MESSAGE));

      } catch (Exception ex) {
        ex.printStackTrace();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
            "Failed to save playlist: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
      }
    });

  }

  // ─────────────────────────────────────────────────────────────────────────
  // DATA REFRESH & INVALID METADATA SCANNING
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * * Re-populates the album list from the library service and filters the items into a separate
   * list tracking entries with missing or incorrect data (Item #1).
   */
  public void refreshAlbumList() {
    CompletableFuture.runAsync(() -> {
      try {
        List<AlbumDto> albums = songLibraryService.getAlbums();
        SwingUtilities.invokeLater(() -> {
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
        });
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
  }

  /**
   * Evaluates validation requirements matching specified constraint structures from Item #1.
   * Isolates records missing critical values or carrying default/low-resolution assets.
   */
  private boolean isMetadataInvalid(AlbumDto album) {
    // Check missing, blank, or fallback release dates (1950)
    if (album.getReleaseDate() == null || album.getReleaseDate().isBlank()
        || "1950".equals(album.getReleaseDate().trim())) {
      return true;
    }

    // Check missing, blank, or fallback record label designations (Unknown)
    if (album.getRecordLabel() == null || album.getRecordLabel().isBlank()
        || "Unknown".equalsIgnoreCase(album.getRecordLabel().trim())) {
      return true;
    }

    // Check physical sizing dimensions on tracking image path assets (At least 250x250)
    String coverArtPath = album.getCoverArtPath();
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

  /** Re-populates the queue list from the queue service. */
  public void refreshQueueList() {
    try {
      List<SongQueueEntryDto> queue = songQueueService.getQueuedSongs();
      SwingUtilities.invokeLater(() -> {
        int sel = queueList.getSelectedIndex();
        queueListModel.clear();
        if (queue != null)
          queue.forEach(queueListModel::addElement);
        if (sel >= 0 && sel < queueListModel.getSize()) {
          queueList.setSelectedIndex(sel);
        }
      });
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CELL RENDERERS
  // ─────────────────────────────────────────────────────────────────────────

  private class AlbumCellRenderer extends JPanel implements javax.swing.ListCellRenderer<AlbumDto> {

    private static final long serialVersionUID = 1L;
    private final JLabel thumb = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel artist = new JLabel();

    AlbumCellRenderer() {
      setLayout(new BorderLayout(8, 0));
      setBorder(new EmptyBorder(3, 8, 3, 8));

      thumb.setPreferredSize(new Dimension(30, 30));
      thumb.setHorizontalAlignment(SwingConstants.CENTER);
      thumb.setOpaque(true);
      thumb.setBackground(new Color(24, 26, 38));

      JPanel text = new JPanel(new BorderLayout(0, 0));
      text.setOpaque(false);
      name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
      artist.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
      name.setForeground(TEXT_PRIMARY);
      artist.setForeground(TEXT_MUTED);
      text.add(name, BorderLayout.CENTER);
      text.add(artist, BorderLayout.SOUTH);

      add(thumb, BorderLayout.WEST);
      add(text, BorderLayout.CENTER);
    }

    @Override
    public java.awt.Component getListCellRendererComponent(JList<? extends AlbumDto> list,
        AlbumDto album, int index, boolean isSelected, boolean cellHasFocus) {

      name.setText(AlbumGridPanel.albumDisplayName(album.getAlbumName(), album.getGenreName()));
      artist.setText(album.getArtistName() != null ? album.getArtistName() : "");

      try {
        if (album.getCoverArtPath() != null) {
          ImageIcon icon = imageLoader.loadFilesystemImage(album.getCoverArtPath(), 30, 30);
          thumb.setIcon(icon);
        } else {
          thumb.setIcon(null);
          thumb.setText("♫");
          thumb.setForeground(TEXT_MUTED);
          thumb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        }
      } catch (Exception ignored) {
        thumb.setIcon(null);
      }

      if (isSelected) {
        setBackground(LIST_SEL_BG);
        name.setForeground(ACCENT_BLUE);
      } else {
        setBackground(index % 2 == 0 ? LIST_BG : ROW_ALT);
        name.setForeground(TEXT_PRIMARY);
      }
      setOpaque(true);
      return this;
    }
  }

  /**
   * Queue row renderer. Mirrors the track-row style of {@link AlbumViewPanel}: the same three-bar
   * popularity widget appears on the WEST side of each row, followed by a queue-position badge,
   * then the song name and artist/album sub-label.
   *
   * <p>
   * The {@link PopularityBarsPanel} inner class is identical to the one in {@link AlbumViewPanel} —
   * if a shared utility class is introduced in the future both can be migrated to it.
   */
  private class QueueCellRenderer extends JPanel
      implements javax.swing.ListCellRenderer<SongQueueEntryDto> {

    private static final long serialVersionUID = 1L;

    // ── Popularity bar constants (match AlbumViewPanel exactly) ───────────
    private static final int BAR_WIDTH = 5;
    private static final int BAR_GAP = 3;
    private static final int BAR_MAX_H = 18;
    private static final int[] BAR_HEIGHTS = {8, 13, 18};

    // ── Sub-widgets ───────────────────────────────────────────────────────
    private final PopularityBarsPanel barsPanel = new PopularityBarsPanel(0);
    private final JLabel position = new JLabel();
    private final JLabel song = new JLabel();
    private final JLabel sub = new JLabel();

    QueueCellRenderer() {
      setLayout(new BorderLayout(6, 0));
      setBorder(new EmptyBorder(4, 8, 4, 8));

      // Popularity bars — fixed size matches AlbumViewPanel track rows
      barsPanel.setPreferredSize(new Dimension(3 * (BAR_WIDTH + BAR_GAP) + 6, BAR_MAX_H + 4));
      barsPanel.setOpaque(false);

      // Position / priority badge
      position.setPreferredSize(new Dimension(28, 36));
      position.setHorizontalAlignment(SwingConstants.CENTER);
      position.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

      // Text cluster
      JPanel text = new JPanel(new BorderLayout(0, 1));
      text.setOpaque(false);
      song.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
      sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
      text.add(song, BorderLayout.CENTER);
      text.add(sub, BorderLayout.SOUTH);

      // Left cluster: bars + badge
      JPanel left = new JPanel(new BorderLayout(4, 0));
      left.setOpaque(false);
      left.add(barsPanel, BorderLayout.WEST);
      left.add(position, BorderLayout.CENTER);

      add(left, BorderLayout.WEST);
      add(text, BorderLayout.CENTER);
    }

    @Override
    public java.awt.Component getListCellRendererComponent(JList<? extends SongQueueEntryDto> list,
        SongQueueEntryDto entry, int index, boolean isSelected, boolean cellHasFocus) {

      // ── Popularity bars ────────────────────────────────────────────────
      int plays = entry.getSong().getNumPlays() == null ? 0 : entry.getSong().getNumPlays();
      int active = barsForPlays(plays, popularityT1, popularityT2, popularityT3);
      barsPanel.setActiveBars(active);

      // ── Position / priority badge ──────────────────────────────────────
      position.setText(String.valueOf(index + 1));
      int p = entry.getPriority() == null ? 0 : entry.getPriority();
      position.setForeground(p >= 8 ? ACCENT_RED : p >= 4 ? ACCENT_ORANGE : TEXT_MUTED);

      // ── Song / sub text ────────────────────────────────────────────────
      song.setText(entry.getSong().getSongName());
      sub.setText(entry.getSong().getArtistName() + "  •  " + entry.getSong().getAlbumName());

      if (isSelected) {
        setBackground(LIST_SEL_BG);
        song.setForeground(ACCENT_BLUE);
        sub.setForeground(Color.WHITE);
      } else {
        setBackground(index % 2 == 0 ? LIST_BG : ROW_ALT);
        song.setForeground(TEXT_PRIMARY);
        sub.setForeground(TEXT_MUTED);
      }
      setOpaque(true);
      return this;
    }

    // ── Shared popularity-bar helpers (mirrors AlbumViewPanel.barsForPlays) ─
    private int barsForPlays(int plays, int t1, int t2, int t3) {
      if (plays >= t3)
        return 3;
      if (plays >= t2)
        return 2;
      if (plays >= t1)
        return 1;
      return 0;
    }

    // ── Inner popularity-bars widget ──────────────────────────────────────
    /**
     * Identical painting logic to {@code AlbumViewPanel.PopularityBarsPanel}. Extracted here so the
     * queue renderer is self-contained. If a shared utility class is created later, both can
     * delegate to it.
     */
    private class PopularityBarsPanel extends JPanel {
      private static final long serialVersionUID = 1L;
      private int activeBars;

      PopularityBarsPanel(int activeBars) {
        this.activeBars = activeBars;
        setOpaque(false);
      }

      void setActiveBars(int n) {
        this.activeBars = n;
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int baseline = getHeight() - 2;
        for (int i = 0; i < 3; i++) {
          int barH = BAR_HEIGHTS[i];
          int x = i * (BAR_WIDTH + BAR_GAP);
          int y = baseline - barH;

          if (i < activeBars) {
            int alpha = Math.min(255, 180 + (i * 25));
            g2.setColor(new Color(ACCENT_GREEN.getRed(), ACCENT_GREEN.getGreen(),
                ACCENT_GREEN.getBlue(), alpha));
          } else {
            g2.setColor(new Color(60, 60, 70, 120));
          }
          g2.fillRoundRect(x, y, BAR_WIDTH, barH, 2, 2);
        }
        g2.dispose();
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // WIDGET HELPERS
  // ─────────────────────────────────────────────────────────────────────────

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

  private static JPanel sectionHeader(String text, Color accent) {
    JPanel header = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(8, 8, 14));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(accent);
        g2.fillRect(0, getHeight() - 2, getWidth(), 2);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    header.setOpaque(false);
    header.setBorder(new EmptyBorder(6, 10, 6, 10));
    JLabel lbl = new JLabel(text);
    lbl.setForeground(accent);
    lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
    header.add(lbl, BorderLayout.WEST);
    return header;
  }

  /**
   * Fixed-size side-panel button with the same AMI 3-D gradient style.
   *
   * <p>
   * The {@code label} string may contain a {@code \n} to split across two lines; the first line is
   * rendered in a slightly larger font as an icon/symbol row and the second as the text label —
   * matching the reference screenshot's compact two-line button style.
   *
   * @param label Button text; use {@code \n} for a two-line layout.
   * @param accent Border/gradient accent colour.
   * @param action {@code ActionListener} fired on click.
   */
  private static JButton sideButton(String label, Color accent,
      java.awt.event.ActionListener action) {

    final Color GRAD_TOP = accent.darker();
    final Color GRAD_BOTTOM = accent.darker().darker();

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

        // Drop-shadow
        g2.setColor(new Color(2, 2, 6));
        g2.fillRoundRect(1, shadowH, w - 2, visH, arc, arc);

        // Shelf
        g2.setColor(new Color(6, 6, 12));
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // Face gradient
        Color top = hovered ? GRAD_TOP.brighter() : GRAD_TOP;
        Color bot = hovered ? GRAD_BOTTOM.brighter() : GRAD_BOTTOM;
        g2.setPaint(new GradientPaint(0, 0, top, 0, faceH, bot));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // Specular edge
        g2.setColor(new Color(Math.min(255, accent.getRed() + 80),
            Math.min(255, accent.getGreen() + 80), Math.min(255, accent.getBlue() + 80), 160));
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // Border
        g2.setColor(hovered ? accent.brighter() : accent);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        // Text — one or two lines centred on the face
        g2.setColor(Color.WHITE);
        if (line2 == null) {
          // Single line
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
          java.awt.FontMetrics fm = g2.getFontMetrics();
          g2.drawString(line1, (w - fm.stringWidth(line1)) / 2,
              (faceH - fm.getHeight()) / 2 + fm.getAscent());
        } else {
          // Two lines: symbol on top, text label below
          Font f1 = new Font(Font.SANS_SERIF, Font.BOLD, 13);
          Font f2 = new Font(Font.SANS_SERIF, Font.BOLD, 10);
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

    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
    btn.setForeground(Color.WHITE);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setOpaque(false);
    btn.setMargin(new Insets(0, 0, 0, 0));
    // Fixed size — both preferred and maximum are clamped so BoxLayout doesn't stretch them
    btn.setPreferredSize(BTN_SIZE);
    btn.setMaximumSize(BTN_SIZE);
    btn.setMinimumSize(BTN_SIZE);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(action);
    return btn;
  }

  private static JScrollPane darkScrollPane(java.awt.Component view) {
    JScrollPane sp = new JScrollPane(view);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setBorder(BorderFactory.createLineBorder(SEPARATOR, 1));
    sp.getVerticalScrollBar().setBackground(new Color(20, 20, 30));
    sp.getHorizontalScrollBar().setVisible(false);
    return sp;
  }
}
