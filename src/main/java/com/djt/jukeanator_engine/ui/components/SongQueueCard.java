package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

// ─────────────────────────────────────────────────────────────────────────
// CONSTRUCTOR
// ─────────────────────────────────────────────────────────────────────────
public class SongQueueCard extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Timeout / layout — sourced from LayoutTheme ───────────────────────────
  // Previously: private static final int TIMEOUT_SECONDS = 120;
  // private static final int MAX_QUEUE_VISIBLE = 5;

  /**
   * Three distinct visual states for the action buttons. GREY = no selection (or positional lock) —
   * fully dimmed, no sub-label. WARN = selected but not enough credits — red border, "ADD N
   * CREDITS". NORMAL = selected and affordable — accent border, "Ncr" cost label.
   */
  private enum ButtonState {
    GREY, WARN, NORMAL
  }

  // ── Colours ───────────────────────────────────────────────────────────────
  private static final Color BG_PANEL = new Color(22, 22, 28);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_GOLD = new Color(255, 200, 0);
  private static final Color ACCENT_GREEN = new Color(60, 210, 80);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);
  private static final Color AM_WARN_BORDER = new Color(220, 40, 40);
  private static final Color LIST_BG = new Color(10, 12, 18);
  private static final Color SEPARATOR = new Color(40, 44, 60);

  // ── 3-D button palette (mirrors AddSongToQueueCard) ─────────────────────
  private static final Color BTN3D_FACE_TOP = new Color(28, 45, 72);
  private static final Color BTN3D_FACE_MID = new Color(18, 32, 54);
  private static final Color BTN3D_FACE_BOTTOM = new Color(10, 18, 34);
  private static final Color BTN3D_SHELF = new Color(6, 10, 20);
  private static final Color BTN3D_SHADOW = new Color(2, 4, 10);
  private static final Color BTN3D_HIGHLIGHT = new Color(80, 140, 210, 200);
  private static final Color BTN3D_SIDE = new Color(40, 80, 130, 90);
  private static final Color BTN3D_WARN_TOP = new Color(55, 10, 10);
  private static final Color BTN3D_WARN_MID = new Color(38, 6, 6);
  private static final Color BTN3D_WARN_BOTTOM = new Color(22, 3, 3);
  private static final Color BTN3D_WARN_SHELF = new Color(12, 2, 2);

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongPlayerService songPlayerService;
  private List<SongQueueEntryDto> queue;
  private final SongQueueService songQueueService;
  private final CreditManager creditManager;
  private final Runnable onDismiss;
  private final ImageLoader imageLoader;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;

  // ── Queue list ────────────────────────────────────────────────────────────
  private final DefaultListModel<SongQueueEntryDto> queueListModel = new DefaultListModel<>();
  private final JList<SongQueueEntryDto> queueList = new JList<>(queueListModel);

  // ── Now-playing dynamic area ───────────────────────────────────────────────
  /** The CENTER slot of the now-playing section — replaced on every {@link #onShown()} call. */
  private JPanel nowPlayingSection;

  // ── Action buttons ────────────────────────────────────────────────────────
  private JButton moveUpButton;
  private JButton moveDownButton;
  private JButton removeButton;
  private Runnable creditListener;

  // ── Timeout ───────────────────────────────────────────────────────────────
  private final Timer countdownTimer;
  private int secondsRemaining = LayoutTheme.get().overlayTimeoutSeconds;
  private final JLabel timeoutLabel = new JProgressLabel();
  private final JProgressBar timeoutBar =
      new JProgressBar(0, LayoutTheme.get().overlayTimeoutSeconds);

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public SongQueueCard(SongPlayerService songPlayerService, List<SongQueueEntryDto> queue,
      SongQueueService songQueueService, CreditManager creditManager, ImageLoader imageLoader,
      int popularityT1, int popularityT2, int popularityT3, Runnable onDismiss) {

    this.songPlayerService = songPlayerService;
    this.queue = queue;
    this.songQueueService = songQueueService;
    this.creditManager = creditManager;
    this.imageLoader = imageLoader;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.onDismiss = onDismiss;

    setOpaque(false);
    setLayout(new java.awt.GridBagLayout());
    JPanel sized = buildBorderPanel();
    sized.setPreferredSize(
        new Dimension(LayoutTheme.get().songQueueCardW, LayoutTheme.get().songQueueCardH));
    add(sized);

    countdownTimer = new Timer(1000, e -> {
      secondsRemaining--;
      updateTimeout();
      if (secondsRemaining <= 0)
        dismiss();
    });
    countdownTimer.start();

  }

  /** Updates the live queue reference — call before {@link #onShown()} if it may have changed. */
  public void setQueue(List<SongQueueEntryDto> queue) {
    this.queue = queue;
    SwingUtilities.invokeLater(() -> {
      refreshQueueListModel();
      refreshNowPlaying();
    });
  }

  /** Called whenever this card is shown — rebuilds the queue list, restarts the countdown. */
  public void onShown() {
    secondsRemaining = LayoutTheme.get().overlayTimeoutSeconds;
    updateTimeout();
    if (!countdownTimer.isRunning()) {
      countdownTimer.start();
    }
    refreshNowPlaying();
    refreshQueueListModel();
    requestFocusInWindow();
  }

  /**
   * Replaces the now-playing content area with a fresh snapshot from
   * {@link SongPlayerService#getNowPlayingSong()}. Safe to call on the EDT at any time.
   */
  private void refreshNowPlaying() {
    if (nowPlayingSection == null)
      return;

    // Remove whatever is currently in the CENTER slot
    nowPlayingSection.removeAll();

    SongDto nowPlayingSong = songPlayerService.getNowPlayingSong();
    if (nowPlayingSong != null) {
      nowPlayingSection.add(buildNowPlayingCard(nowPlayingSong), BorderLayout.CENTER);
    } else {
      JLabel none = new JLabel("Nothing is currently playing.");
      none.setForeground(TEXT_SECONDARY);
      none.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
      nowPlayingSection.add(none, BorderLayout.CENTER);
    }

    nowPlayingSection.revalidate();
    nowPlayingSection.repaint();
  }

  // Background is painted by overlayRoot in JukeANatorFrame — no paintComponent override needed.

  // ── Layout ────────────────────────────────────────────────────────────────

  private JPanel buildBorderPanel() {
    JPanel border = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ACCENT_BLUE);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
        g2.dispose();
      }
    };
    border.setOpaque(false);
    border.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    border.add(buildMainPanel());
    return border;
  }

  private JPanel buildMainPanel() {
    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setBackground(BG_PANEL);
    main.setBorder(BorderFactory.createEmptyBorder(16, 28, 14, 28));

    main.add(buildNowPlayingSection(), BorderLayout.NORTH);
    main.add(buildDivider(), BorderLayout.CENTER);
    main.add(buildQueueSection(), BorderLayout.SOUTH);

    return main;
  }

  // ── Now-Playing section ───────────────────────────────────────────────────

  private JPanel buildNowPlayingSection() {
    nowPlayingSection = new JPanel(new BorderLayout(0, 8));
    nowPlayingSection.setOpaque(false);

    // Header
    JLabel header = new JLabel("NOW PLAYING");
    header.setForeground(ACCENT_BLUE);
    header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
    header.setBorder(new EmptyBorder(0, 0, 6, 0));
    nowPlayingSection.add(header, BorderLayout.NORTH);

    // Content is populated dynamically; refreshNowPlaying() fills the CENTER slot.
    // We call it once here so the section is populated at construction time.
    refreshNowPlaying();

    return nowPlayingSection;
  }

  private JPanel buildNowPlayingCard(SongDto song) {
    JPanel card = new JPanel(new BorderLayout(20, 0));
    card.setOpaque(false);
    card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(SEPARATOR, 1),
        new EmptyBorder(12, 14, 12, 14)));

    // Cover art
    JLabel cover = new JLabel();
    cover.setPreferredSize(new Dimension(96, 96));
    cover.setHorizontalAlignment(SwingConstants.CENTER);
    cover.setVerticalAlignment(SwingConstants.CENTER);
    cover.setOpaque(true);
    cover.setBackground(new Color(30, 30, 40));
    cover.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 75), 1));

    if (song.getCoverArtPath() != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(song.getCoverArtPath(), 96, 96);
        if (icon != null)
          cover.setIcon(icon);
      } catch (Exception ignored) {
      }
    }
    if (cover.getIcon() == null) {
      cover.setText("♫");
      cover.setForeground(TEXT_SECONDARY);
      cover.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
    }

    // Text info
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

    JLabel songName = label(song.getSongName(), Font.BOLD, 22, TEXT_PRIMARY);
    JLabel artist = label(song.getArtistName(), Font.PLAIN, 17, TEXT_PRIMARY);
    JLabel album = label(song.getAlbumName(), Font.PLAIN, 15, TEXT_SECONDARY);

    text.add(Box.createVerticalGlue());
    text.add(songName);
    text.add(Box.createVerticalStrut(4));
    text.add(artist);
    text.add(Box.createVerticalStrut(4));
    text.add(album);
    text.add(Box.createVerticalGlue());

    card.add(cover, BorderLayout.WEST);
    card.add(text, BorderLayout.CENTER);
    return card;
  }

  // ── Divider ────────────────────────────────────────────────────────────────

  private JPanel buildDivider() {
    JPanel divider = new JPanel() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(new Color(80, 80, 100));
        g.fillRect(0, getHeight() / 2, getWidth(), 1);
      }
    };
    divider.setOpaque(false);
    divider.setPreferredSize(new Dimension(0, 8));
    return divider;
  }

  // ── Queue section ─────────────────────────────────────────────────────────

  private JPanel buildQueueSection() {
    JPanel section = new JPanel(new BorderLayout(0, 8));
    section.setOpaque(false);
    // Extra top margin separates this section visually from Now Playing
    section.setBorder(new EmptyBorder(15, 0, 0, 0));

    // Header row: label on the left, priority legend aligned to the right
    JPanel headerRow = new JPanel(new BorderLayout(8, 0));
    headerRow.setOpaque(false);
    headerRow.setBorder(new EmptyBorder(0, 0, 4, 0));

    JLabel header = new JLabel("QUEUED SONGS");
    header.setForeground(ACCENT_GREEN);
    header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
    headerRow.add(header, BorderLayout.WEST);
    headerRow.add(SongTrackCellRenderer.buildPriorityLegend(), BorderLayout.EAST);

    section.add(headerRow, BorderLayout.NORTH);

    // Populate model (up to MAX_QUEUE_VISIBLE entries)
    refreshQueueListModel();

    SongTrackCellRenderer.installWithPriority(queueList, popularityT1, popularityT2, popularityT3);
    queueList.setOpaque(true);
    queueList.setBackground(LIST_BG);
    queueList.setForeground(TEXT_PRIMARY);
    queueList.setSelectionBackground(new Color(0, 60, 80));
    queueList.setSelectionForeground(Color.WHITE);
    queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    queueList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting())
        updateButtonStates();
    });

    // Fix the list height to exactly songQueueMaxVisible rows — no scroll bar needed.
    int listH = SongTrackCellRenderer.CELL_HEIGHT * LayoutTheme.get().songQueueMaxVisible;
    queueList.setPreferredSize(new Dimension(Integer.MAX_VALUE, listH));
    queueList.setMinimumSize(new Dimension(0, listH));
    queueList.setMaximumSize(new Dimension(Integer.MAX_VALUE, listH));

    // Plain bordered wrapper — no JScrollPane so no scroll bar can appear.
    JPanel listWrapper = new JPanel(new BorderLayout());
    listWrapper.setOpaque(false);
    listWrapper.setBorder(BorderFactory.createLineBorder(SEPARATOR, 1));
    listWrapper.add(queueList, BorderLayout.CENTER);

    section.add(listWrapper, BorderLayout.CENTER);
    section.add(buildActionArea(), BorderLayout.SOUTH);

    return section;
  }

  // ── Action area (buttons + timeout row) ──────────────────────────────────

  private JPanel buildActionArea() {
    JPanel area = new JPanel(new BorderLayout(0, 10));
    area.setOpaque(false);
    area.setBorder(new EmptyBorder(10, 0, 0, 0));

    // Three management buttons
    JPanel buttons = new JPanel(new GridLayout(1, 3, 16, 0));
    buttons.setOpaque(false);

    moveUpButton = createActionButton("Move Song Up", 0, ACCENT_BLUE, e -> doMoveUp());
    moveDownButton = createActionButton("Move Song Down", 0, ACCENT_BLUE, e -> doMoveDown());
    removeButton = createActionButton("Remove Song", 0, ACCENT_BLUE, e -> doRemove());

    buttons.add(moveUpButton);
    buttons.add(moveDownButton);
    buttons.add(removeButton);

    // Cancel button — narrower and shorter than the action buttons, centred
    JButton cancel = createCancelButton("Cancel");
    cancel.setPreferredSize(new Dimension(LayoutTheme.get().songQueueCancelBtnW,
        LayoutTheme.get().songQueueCancelBtnH));
    cancel.setMinimumSize(new Dimension(LayoutTheme.get().songQueueCancelBtnW,
        LayoutTheme.get().songQueueCancelBtnH));
    cancel.setMaximumSize(new Dimension(LayoutTheme.get().songQueueCancelBtnW,
        LayoutTheme.get().songQueueCancelBtnH));
    JPanel cancelRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    cancelRow.setOpaque(false);
    cancelRow.add(cancel);

    // Timeout bar
    JComponent timeoutRow = buildTimeoutRow();

    area.add(buttons, BorderLayout.NORTH);
    area.add(cancelRow, BorderLayout.CENTER);
    area.add(timeoutRow, BorderLayout.SOUTH);

    // Hook credit listener so buttons update when credits change
    creditListener = this::updateButtonStates;
    creditManager.addListener(creditListener);

    updateButtonStates();

    return area;
  }

  // ── Queue operations ──────────────────────────────────────────────────────

  private void doMoveUp() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null || !deductCostFor(selected))
      return;
    int idx = queueList.getSelectedIndex();
    CompletableFuture.runAsync(() -> {
      try {
        songQueueService.moveSongUpInQueue(new ChangeSongQueueRequest(
            selected.getSong().getAlbumId(), selected.getSong().getSongId()));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    // Update local model optimistically
    SwingUtilities.invokeLater(() -> {
      if (idx > 0) {
        SongQueueEntryDto above = queueListModel.get(idx - 1);
        queueListModel.set(idx - 1, selected);
        queueListModel.set(idx, above);
        queueList.setSelectedIndex(idx - 1);
      }
    });
  }

  private void doMoveDown() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null || !deductCostFor(selected))
      return;
    int idx = queueList.getSelectedIndex();
    CompletableFuture.runAsync(() -> {
      try {
        songQueueService.moveSongDownInQueue(new ChangeSongQueueRequest(
            selected.getSong().getAlbumId(), selected.getSong().getSongId()));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    SwingUtilities.invokeLater(() -> {
      if (idx < queueListModel.getSize() - 1) {
        SongQueueEntryDto below = queueListModel.get(idx + 1);
        queueListModel.set(idx + 1, selected);
        queueListModel.set(idx, below);
        queueList.setSelectedIndex(idx + 1);
      }
    });
  }

  private void doRemove() {
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null || !deductCostFor(selected))
      return;
    int idx = queueList.getSelectedIndex();
    CompletableFuture.runAsync(() -> {
      try {
        songQueueService.removeSongDownFromQueue(new ChangeSongQueueRequest(
            selected.getSong().getAlbumId(), selected.getSong().getSongId()));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    SwingUtilities.invokeLater(() -> {
      queueListModel.remove(idx);
      if (queueListModel.getSize() > 0) {
        queueList.setSelectedIndex(Math.min(idx, queueListModel.getSize() - 1));
      }
    });
  }

  /** Deducts the credit cost for the given entry and returns {@code true} on success. */
  private boolean deductCostFor(SongQueueEntryDto entry) {
    int cost = computeCost(entry);
    return creditManager.deductCredits(cost);
  }

  /** Cost = 3 × entry's priority (minimum 1). */
  private static int computeCost(SongQueueEntryDto entry) {
    int priority = entry.getPriority() == null ? 0 : entry.getPriority();
    return Math.max(1, priority * 3);
  }

  // ── Button state / label refresh ─────────────────────────────────────────

  private void updateButtonStates() {
    if (moveUpButton == null || moveDownButton == null || removeButton == null)
      return; // buttons not yet constructed (initial population during buildQueueSection)
    SongQueueEntryDto selected = queueList.getSelectedValue();
    int currentCredits = creditManager.getCredits();
    int idx = queueList.getSelectedIndex();
    int size = queueListModel.getSize();

    if (selected == null) {
      // No selection — all buttons go fully grey
      applyState(moveUpButton, ButtonState.GREY, 0);
      applyState(moveDownButton, ButtonState.GREY, 0);
      applyState(removeButton, ButtonState.GREY, 0);
    } else {
      int cost = computeCost(selected);
      boolean afford = currentCredits >= cost;

      // Move Up: grey when first item, warn/normal otherwise
      if (idx == 0) {
        applyState(moveUpButton, ButtonState.GREY, cost);
      } else {
        applyState(moveUpButton, afford ? ButtonState.NORMAL : ButtonState.WARN, cost);
      }

      // Move Down: grey when last item, warn/normal otherwise
      if (idx >= size - 1) {
        applyState(moveDownButton, ButtonState.GREY, cost);
      } else {
        applyState(moveDownButton, afford ? ButtonState.NORMAL : ButtonState.WARN, cost);
      }

      // Remove: always warn/normal when something is selected
      applyState(removeButton, afford ? ButtonState.NORMAL : ButtonState.WARN, cost);
    }

    moveUpButton.repaint();
    moveDownButton.repaint();
    removeButton.repaint();
  }

  /**
   * Stores the {@link ButtonState} and cost in client properties and updates the Swing enabled flag
   * so the action listener is gated correctly.
   */
  private static void applyState(JButton button, ButtonState state, int cost) {
    button.putClientProperty("buttonState", state);
    button.putClientProperty("cost", cost);
    // Only truly enable the button when it can fire a meaningful action
    button.setEnabled(state == ButtonState.NORMAL);
  }

  // ── Timeout row ──────────────────────────────────────────────────────────

  private JComponent buildTimeoutRow() {
    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(6, 0, 0, 0));

    timeoutLabel.setForeground(TEXT_SECONDARY);
    timeoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    timeoutLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    updateTimeout();

    timeoutBar.setValue(LayoutTheme.get().overlayTimeoutSeconds);
    timeoutBar.setForeground(ACCENT_BLUE);
    timeoutBar.setBackground(new Color(40, 40, 55));
    timeoutBar.setBorderPainted(false);
    timeoutBar.setStringPainted(false);
    timeoutBar.setPreferredSize(new Dimension(0, 4));

    row.add(timeoutBar, BorderLayout.CENTER);
    row.add(timeoutLabel, BorderLayout.EAST);

    return row;
  }

  private void updateTimeout() {
    timeoutBar.setValue(secondsRemaining);
    timeoutLabel.setText("Closes in " + secondsRemaining + "s");
  }

  /**
   * Rebuilds the queue list model from the (live) queue reference — used at construction and
   * onShown().
   */
  private void refreshQueueListModel() {
    queueListModel.clear();
    if (queue != null) {
      int limit = Math.min(queue.size(), LayoutTheme.get().songQueueMaxVisible);
      for (int i = 0; i < limit; i++) {
        queueListModel.addElement(queue.get(i));
      }
    }
    updateButtonStates();
  }

  private void dismiss() {
    countdownTimer.stop();
    if (onDismiss != null) {
      SwingUtilities.invokeLater(onDismiss);
    }
  }

  // ── Button factories ──────────────────────────────────────────────────────

  /**
   * Creates an action button with three distinct visual states driven by the {@code "buttonState"}
   * client property ({@link ButtonState}):
   *
   * <ul>
   * <li>{@code GREY} — fully dimmed flat surface, no sub-label. Used when no song is selected or a
   * positional constraint applies (Move Up on first item).</li>
   * <li>{@code WARN} — dark-red face, red border, "ADD N CREDITS" sub-label.</li>
   * <li>{@code NORMAL} — standard 3-D face, accent border, "Ncr" cost sub-label.</li>
   * </ul>
   */
  private JButton createActionButton(String actionText, int initialCost, Color accentColor,
      java.awt.event.ActionListener onClick) {

    // Grey palette constants (fully dimmed — no depth or color)
    final Color BTN_GREY_FACE = new Color(35, 35, 42);
    final Color BTN_GREY_BORDER = new Color(65, 65, 75);
    final Color BTN_GREY_TEXT = new Color(90, 90, 100);

    JButton button = new JButton() {
      private static final long serialVersionUID = 1L;
      private boolean hovered = false;
      {
        addMouseListener(new java.awt.event.MouseAdapter() {
          public void mouseEntered(java.awt.event.MouseEvent e) {
            if (isEnabled()) {
              hovered = true;
              repaint();
            }
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

        Object stateProp = getClientProperty("buttonState");
        ButtonState state =
            (stateProp instanceof ButtonState) ? (ButtonState) stateProp : ButtonState.GREY;
        Object costProp = getClientProperty("cost");
        int cost = (costProp instanceof Integer) ? (Integer) costProp : initialCost;

        int w = getWidth(), h = getHeight(), arc = 12;
        int shadowH = 5, visH = h - shadowH;
        int shelfH = Math.round(visH * 0.22f);
        int faceH = visH - shelfH;

        if (state == ButtonState.GREY) {
          // ── Flat dimmed surface — no shadow, no depth ──────────────────
          g2.setColor(BTN_GREY_FACE);
          g2.fillRoundRect(1, 0, w - 2, visH, arc, arc);
          g2.setColor(BTN_GREY_BORDER);
          g2.setStroke(new java.awt.BasicStroke(1.5f));
          g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

          // Action text only — muted, no sub-label
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
          FontMetrics fm = g2.getFontMetrics();
          g2.setColor(BTN_GREY_TEXT);
          g2.drawString(actionText, (w - fm.stringWidth(actionText)) / 2,
              (visH - fm.getHeight()) / 2 + fm.getAscent());

        } else {
          // ── 3-D face (WARN or NORMAL) ──────────────────────────────────
          boolean warn = (state == ButtonState.WARN);

          // 1. Drop-shadow
          g2.setColor(BTN3D_SHADOW);
          g2.fillRoundRect(2, shadowH, w - 4, visH, arc, arc);

          // 2. Shelf
          g2.setColor(warn ? BTN3D_WARN_SHELF : BTN3D_SHELF);
          g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

          // 3. Face gradient
          Color fTop, fMid, fBot;
          if (warn) {
            fTop = BTN3D_WARN_TOP;
            fMid = BTN3D_WARN_MID;
            fBot = BTN3D_WARN_BOTTOM;
          } else if (hovered) {
            fTop = new Color(40, 65, 105);
            fMid = new Color(28, 50, 84);
            fBot = new Color(16, 30, 56);
          } else {
            fTop = BTN3D_FACE_TOP;
            fMid = BTN3D_FACE_MID;
            fBot = BTN3D_FACE_BOTTOM;
          }
          g2.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.5f, 1f},
              new Color[] {fTop, fMid, fBot}));
          g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

          // 4. Specular highlight
          g2.setColor(warn ? new Color(200, 60, 60, 160) : BTN3D_HIGHLIGHT);
          g2.setStroke(new java.awt.BasicStroke(1.2f));
          g2.drawLine(arc, 1, w - arc - 1, 1);

          // 5. Side sheen
          g2.setColor(warn ? new Color(160, 30, 30, 70) : BTN3D_SIDE);
          g2.setStroke(new java.awt.BasicStroke(1f));
          g2.drawLine(1, 3, 1, faceH - 3);
          g2.drawLine(w - 2, 3, w - 2, faceH - 3);

          // 6. Border
          g2.setColor(warn ? AM_WARN_BORDER : (hovered ? accentColor.brighter() : accentColor));
          g2.setStroke(new java.awt.BasicStroke(2f));
          g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

          // 7. Two-line centred label
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
          FontMetrics fm1 = g2.getFontMetrics();
          int totalTextH = fm1.getHeight() + 4 + fm1.getHeight();
          int blockY = (faceH - totalTextH) / 2 + fm1.getAscent();

          g2.setColor(TEXT_PRIMARY);
          g2.drawString(actionText, (w - fm1.stringWidth(actionText)) / 2, blockY);

          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
          FontMetrics fm2 = g2.getFontMetrics();
          int line2Y = blockY + fm1.getHeight() - fm1.getDescent() + 4 + fm2.getAscent();

          if (!warn) {
            String costText = cost + "cr";
            g2.setColor(ACCENT_GOLD);
            g2.drawString(costText, (w - fm2.stringWidth(costText)) / 2, line2Y);
          } else {
            int needed = Math.max(0, cost - creditManager.getCredits());
            String warnText = "ADD " + needed + (needed == 1 ? " CREDIT" : " CREDITS");
            g2.setColor(AM_WARN_BORDER);
            g2.drawString(warnText, (w - fm2.stringWidth(warnText)) / 2, line2Y);
          }
        }

        g2.dispose();
      }
    };

    button.putClientProperty("buttonState", ButtonState.GREY);
    button.putClientProperty("cost", initialCost);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setEnabled(false);
    button.setPreferredSize(new Dimension(LayoutTheme.get().songQueueActionBtnW,
        LayoutTheme.get().songQueueActionBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(onClick);

    return button;
  }

  private JButton createCancelButton(String text) {
    JButton button = new JButton() {
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

        int w = getWidth(), h = getHeight(), arc = 12;
        int shadowH = 4, visH = h - shadowH;
        int shelfH = Math.round(visH * 0.22f);
        int faceH = visH - shelfH;

        g2.setColor(BTN3D_SHADOW);
        g2.fillRoundRect(2, shadowH, w - 4, visH, arc, arc);

        g2.setColor(BTN3D_SHELF);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        Color fTop = hovered ? new Color(40, 65, 105) : BTN3D_FACE_TOP;
        Color fMid = hovered ? new Color(28, 50, 84) : BTN3D_FACE_MID;
        Color fBot = hovered ? new Color(16, 30, 56) : BTN3D_FACE_BOTTOM;
        g2.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.5f, 1f},
            new Color[] {fTop, fMid, fBot}));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        g2.setColor(BTN3D_HIGHLIGHT);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        g2.setColor(BTN3D_SIDE);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 3, 1, faceH - 3);
        g2.drawLine(w - 2, 3, w - 2, faceH - 3);

        g2.setColor(hovered ? ACCENT_BLUE.brighter() : ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(TEXT_PRIMARY);
        g2.drawString(text, (w - fm.stringWidth(text)) / 2,
            (faceH - fm.getHeight()) / 2 + fm.getAscent());

        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(LayoutTheme.get().songQueueCancelBtnW,
        LayoutTheme.get().songQueueCancelBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> dismiss());

    return button;
  }

  // ── Misc helpers ──────────────────────────────────────────────────────────

  private static JLabel label(String text, int style, int size, Color color) {
    JLabel l = new JLabel(text != null ? text : "");
    l.setForeground(color);
    l.setFont(new Font(Font.SANS_SERIF, style, size));
    return l;
  }

  /** Tiny inner class so we can use JLabel as the timeout text without a name conflict. */
  private static class JProgressLabel extends JLabel {
    private static final long serialVersionUID = 1L;
  }

  /** Must be called when this card is permanently discarded so listeners don't leak. */
  public void teardown() {
    countdownTimer.stop();
    if (creditListener != null) {
      creditManager.removeListener(creditListener);
    }
  }
}
