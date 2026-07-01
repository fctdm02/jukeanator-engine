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
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
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

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;
  private final CreditManager creditManager;
  private final Frame ownerFrame;
  private final ImageLoader imageLoader;

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

  // ── Invalid Metadata Tracking Cache ──────────────────────────────────────
  private final List<AlbumDto> albumsWithInvalidMetadata = new ArrayList<>();

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
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

    add(buildLibraryButtons(), BorderLayout.WEST);
    add(buildListsCenter(), BorderLayout.CENTER);
    add(buildQueueButtons(), BorderLayout.EAST);

    loadAlbumList();
    setQueue(songQueueService.getQueuedSongs());

    requestFocusInWindow();
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
    queueList.setOpaque(true);
    queueList.setBackground(ColorTheme.get().bgList);
    queueList.setForeground(ColorTheme.get().textPrimary);
    queueList.setSelectionBackground(ColorTheme.get().bgListSelected);
    queueList.setSelectionForeground(ColorTheme.get().textPrimary);
    queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    SongTrackCellRenderer.installWithPriority(queueList, popularityT1, popularityT2, popularityT3,
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

    strip.add(Box.createVerticalGlue());
    strip.add(sideButton("Queue\nAlbum", ColorTheme.get().accentGreen, e -> doAddAlbumToQueue()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton("Edit\nAlbum", ColorTheme.get().accentGold, e -> doEditAlbum()));
    strip.add(sideButton("Reset\nStats", ColorTheme.get().accentOrange, e -> doResetStats()));
    strip.add(sideButton("Rescan\nLibrary", ColorTheme.get().accentViolet, e -> doRescan()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton("⊟ Minimize", ColorTheme.get().accentBlue, e -> doMinimize()));
    strip.add(sideButton("✕ Exit", ColorTheme.get().accentRed, e -> doExit()));

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
    strip.add(sideButton("▶▶\nNext", ColorTheme.get().accentGreen, e -> doPlayNextTrack()));
    strip.add(sideButton("❚❚\nPause", ColorTheme.get().accentBlue, e -> doPause()));
    strip.add(sideButton("▶\nPlay", ColorTheme.get().accentGreen, e -> doPlaySelected()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton("▲\nMove Up", ColorTheme.get().accentBlue, e -> doMoveUp()));
    strip.add(sideButton("▼\nMove Dn", ColorTheme.get().accentBlue, e -> doMoveDown()));
    strip.add(sideButton("✕\nRemove", ColorTheme.get().accentRed, e -> doRemoveSong()));
    strip.add(verticalSpacer(20));
    strip.add(sideButton("🗑\nFlush", ColorTheme.get().accentRed, e -> doFlushQueue()));
    strip.add(sideButton("🔀\nShuffle", ColorTheme.get().accentViolet, e -> doRandomizeQueue()));
    strip.add(verticalSpacer(20));
    strip.add(sideButton("📂\nLoad Playlist", ColorTheme.get().accentGold, e -> doLoadPlaylist()));
    strip.add(sideButton("💾\nSave Playlist", ColorTheme.get().accentGold, e -> doSavePlaylist()));
    strip.add(Box.createVerticalGlue());
    strip.add(sideButton("➕\nCredits", ColorTheme.get().accentGreen, e -> doIncrementCredits()));
    strip.add(sideButton("➖\nCredits", ColorTheme.get().accentOrange, e -> doDecrementCredits()));

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
      JOptionPane.showMessageDialog(this, "Please select an album first.", "No Selection",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    // Capture the ID safely
    final Integer albumId = selected.getAlbumId();

    SwingSecurityUtil.runAsync(() -> {
      try {
        // 1. Fetch full album entity context in the background
        AlbumDto full = songLibraryService.getAlbumById(albumId);

        // 2. Submit to the queue engine (Fires events, updates data models)
        songQueueService.addAlbumToQueue(
            new AddAlbumToQueueRequest(SongQueueService.LOCAL_USERNAME, full.getAlbumId(), 1));

        // 3. Explicitly request the fresh queue list from the service layer
        // WHILE STILL on the background thread.
        var freshQueue = songQueueService.getQueuedSongs();

        // 4. Safely push the isolated DTO snapshot to the Swing EDT
        SwingUtilities.invokeLater(() -> refreshQueueList(freshQueue));

      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
  }

  private void doEditAlbum() {

    AlbumDto selected = albumList.getSelectedValue();
    if (selected == null) {
      if (!albumsWithInvalidMetadata.isEmpty()) {
        selected = albumsWithInvalidMetadata.getFirst();
      } else {
        JOptionPane.showMessageDialog(this, "Please select an album first.", "No Selection",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
    }

    if (ownerFrame instanceof JukeANatorFrame frame) {
      frame.showEditAlbumCard(selected, albumsWithInvalidMetadata);
    }
  }

  private void doResetStats() {

    int confirm = JOptionPane.showConfirmDialog(this, "Reset all song play statistics?",
        "Reset Statistics", JOptionPane.YES_NO_OPTION);
    if (confirm == JOptionPane.YES_OPTION) {
      SwingSecurityUtil.runAsync(() -> {
        try {
          songLibraryService.resetSongStatistics();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
    }
  }

  private void doRescan() {

    int confirm =
        JOptionPane.showConfirmDialog(this, "Rescan the song library? This may take a moment.",
            "Rescan Library", JOptionPane.YES_NO_OPTION);
    if (confirm == JOptionPane.YES_OPTION) {
      SwingSecurityUtil.runAsync(() -> {
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
      if (ownerFrame instanceof JukeANatorFrame jukeFrame) {
        jukeFrame.setAdminMinimizeRequested(true);
      }
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
      songQueueService.moveSongUpInQueue(new ChangeSongQueueRequest(
          selected.getSong().getAlbumId(), selected.getSong().getSongId(), idx));
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
      songQueueService.moveSongDownInQueue(new ChangeSongQueueRequest(
          selected.getSong().getAlbumId(), selected.getSong().getSongId(), idx));
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
      JOptionPane.showMessageDialog(this, "Please select a song in the queue first.",
          "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }
    try {
      songQueueService.removeSongDownFromQueue(new ChangeSongQueueRequest(
          selected.getSong().getAlbumId(), selected.getSong().getSongId()));
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
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  private void doRandomizeQueue() {

    try {
      songQueueService.randomizeQueue();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void doIncrementCredits() {
    creditManager.addDollar();
  }

  private void doDecrementCredits() {
    creditManager.deductCredits(1);
  }

  private void doLoadPlaylist() {

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Load Playlist");
    chooser.setFileFilter(new FileNameExtensionFilter("Playlist files (*.txt)", "txt"));
    chooser.setCurrentDirectory(new File(""));

    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
      return;

    String filename = chooser.getSelectedFile().getAbsolutePath();
    SwingSecurityUtil.runAsync(() -> {
      try {

        LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest =
            new LoadPlaylistIntoQueueRequest(SongQueueService.LOCAL_USERNAME, filename);
        this.songQueueService.loadPlaylistIntoQueue(loadPlaylistIntoQueueRequest);

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
    SwingSecurityUtil.runAsync(() -> {
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

  /**
   * Synchronously loads the full album list from the song library service into the list model.
   * Called once from the constructor and again from {@link #refreshAlbumList()} after a rescan.
   * Runs on the EDT; the security context is already present via
   * {@code LocalAuthenticatedEventQueue}.
   */
  private void loadAlbumList() {
    try {
      List<AlbumDto> albums = songLibraryService.getAlbums();
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

  public void setQueue(List<SongQueueEntryDto> queue) {

    refreshQueueList(songQueueService.getQueuedSongs());
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
        String artist = album.getArtistName() != null ? album.getArtistName() : "";
        String display =
            AlbumGridPanel.albumDisplayName(album.getAlbumName(), album.getGenreName());
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
    header.add(lbl, BorderLayout.WEST);
    header.add(SongTrackCellRenderer.buildPriorityLegend(), BorderLayout.EAST);
    return header;
  }

  /**
   * Builds the "JUKEBOX LIST" section header that includes a compact filter text field on the
   * right. Typing in the field scrolls the album list to the first entry whose display name starts
   * with the entered text (case-insensitive).
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
    filterField.setToolTipText("Filter — jumps to first match");

    // Jump to the first album whose display name starts with the filter text
    filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      private void jumpToMatch() {
        String filter = filterField.getText().trim().toLowerCase();
        if (filter.isEmpty())
          return;
        for (int i = 0; i < albumListModel.getSize(); i++) {
          AlbumDto album = albumListModel.getElementAt(i);
          String display = AlbumGridPanel
              .albumDisplayName(album.getAlbumName(), album.getGenreName()).toLowerCase();
          if (display.startsWith(filter)) {
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
        g2.setColor(ColorTheme.get().adminSideBtnShadow);
        g2.fillRoundRect(1, shadowH, w - 2, visH, arc, arc);

        // Shelf
        g2.setColor(ColorTheme.get().adminSideBtnShelf);
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
        g2.setColor(ColorTheme.get().textPrimary);
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
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(action);
    return btn;
  }

  private static JScrollPane darkScrollPane(java.awt.Component view) {
    JScrollPane sp = new JScrollPane(view);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setBorder(BorderFactory.createLineBorder(ColorTheme.get().colorAdminSeparator, 1));
    sp.getVerticalScrollBar().setBackground(ColorTheme.get().adminScrollBarBg);
    sp.getHorizontalScrollBar().setVisible(false);
    return sp;
  }
}
