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
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongPlayHistoryEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

/**
 * The QUEUE tab panel.
 *
 * <p>
 * Renders the same diagonal screen gradient as the main frame so it looks like a seamless "window"
 * into the underlying background. Hosts a {@link DetailHeaderPanel} (matching Home / Hot Here /
 * Genre Details) followed by the queue list and the move-up/move-down/remove controls — no nested
 * card or border around the content, and fills the tab edge-to-edge like the other tabs.
 *
 * <p>
 * There is no now-playing display here: the currently-playing song is always visible in the main
 * frame's top panel, so repeating it on this tab would be redundant.
 */
public class QueuePanel extends JPanel {

  private static final long serialVersionUID = 1L;

  /**
   * Visual states for the action buttons. GREY = no selection — fully dimmed, no sub-label.
   * UNAVAILABLE = positional lock (Move Up on the first song, Move Down on the last) — red border,
   * "NOT AVAILABLE". WARN = selected but not enough credits — red border, "ADD N CREDITS". NORMAL =
   * selected and affordable — accent border, "Ncr" cost label.
   */
  private enum ButtonState {
    GREY, UNAVAILABLE, WARN, NORMAL
  }

  // ── Colours ───────────────────────────────────────────────────────────────
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_GOLD = new Color(255, 200, 0);

  // ── Track-row palette — identical to AlbumViewCard, so the queue list renders exactly
  // like the album track listing ────────────────────────────────────────────
  private static final Color BG_ROW_HOVER = new Color(255, 255, 255, 25);
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);
  private static final Color SEPARATOR = new Color(50, 50, 65);

  // ── Dependencies ──────────────────────────────────────────────────────────
  private List<SongQueueEntryDto> queue;
  private final SongQueueService songQueueService;
  private final CreditManager creditManager;
  private final ImageLoader imageLoader;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;
  /** Whether the "Genre" column is shown (the song library's {@code use-genre} setting). */
  private final boolean useGenre;

  // ── Text columns ──────────────────────────────────────────────────────────
  /*
   * Relative widths of the text columns. Album is the baseline (1.0); Artist is 30% narrower, and
   * the space taken from Artist and Genre goes to Song. When Genre is hidden the other three share
   * its space in the same proportions.
   */
  private static final double GENRE_COL_WEIGHT = 0.5;
  private static final double ARTIST_COL_WEIGHT = 0.7;
  private static final double ALBUM_COL_WEIGHT = 1.0;
  private static final double SONG_COL_WEIGHT = 1.4;
  /** Horizontal gap between a column's text and the divider on its right. */
  private static final int TEXT_COL_GAP = 8;
  /** Padding between a column divider and the text to its right. */
  private static final int TEXT_COL_PAD_LEFT = 8;

  // ── Queue list ────────────────────────────────────────────────────────────
  // Mutable snapshot of the visible (capped) queue rows, rendered as AlbumViewCard-style
  // track rows rather than a JList — decoupled from `queue` so move/remove actions can be
  // applied optimistically before the authoritative server round-trip arrives.
  private final List<SongQueueEntryDto> displayedQueue = new ArrayList<>();
  private JPanel queueRowsPanel;
  private JScrollPane queueRowsScroll;
  /** Holds the column-name header row, which is rebuilt when switching queue/history views. */
  private JPanel columnHeaderHolder;
  /**
   * Viewport height the queue rows were last sized for, or -1 before the first queue-view rebuild.
   * Lets the viewport resize listener skip rebuilds when the available height has not changed.
   */
  private int lastQueueViewportH = -1;

  /** Top + bottom {@code EmptyBorder} padding on {@link #queueRowsPanel}. */
  private static final int ROWS_PANEL_PAD_V = 4;
  /** Height of each separator between rows (see {@link #buildSeparator()}). */
  private static final int ROW_SEPARATOR_H = 1;

  // ── "Show History" view ───────────────────────────────────────────────────
  /** Start times in the history view, e.g. "9:30PM". */
  private static final DateTimeFormatter HISTORY_TIME_FORMAT =
      DateTimeFormatter.ofPattern("h:mma", Locale.US).withZone(ZoneId.systemDefault());

  private ToggleSwitch historyToggle;
  /** {@code true} while the table shows play history instead of the queue. Defaults off. */
  private volatile boolean showingHistory = false; // read by setQueue() off the EDT
  /** Latest play-history snapshot (newest first), fetched while {@link #showingHistory} is on. */
  private List<SongPlayHistoryEntryDto> history = List.of();

  // ── Action buttons ────────────────────────────────────────────────────────
  /** Move/Remove button area — hidden in the history view, where it does not apply. */
  private JPanel actionArea;
  private JButton moveUpButton;
  private JButton moveDownButton;
  private JButton removeButton;
  private Runnable creditListener;

  /** Tracks the selected song across queue rebuilds so the same song stays selected. -1 = none. */
  private int selectedIndex = -1;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public QueuePanel(List<SongQueueEntryDto> initialQueue, SongQueueService songQueueService,
      CreditManager creditManager, ImageLoader imageLoader, int popularityT1, int popularityT2,
      int popularityT3, boolean useGenre) {

    this.queue = initialQueue;
    this.useGenre = useGenre;
    this.songQueueService = songQueueService;
    this.creditManager = creditManager;
    this.imageLoader = imageLoader;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;

    // Transparent so the frame's own diagonal gradient shows through, matching Home / Hot
    // Here / Genre Details, instead of duplicating and then covering it with an opaque panel.
    setOpaque(false);
    // Fills the entire tab, edge-to-edge, matching the other tabs' layout
    setLayout(new BorderLayout());
    add(buildMainPanel(), BorderLayout.CENTER);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PUBLIC API
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Push a fresh queue snapshot. Safe to call from any thread; internally dispatched to the EDT.
   */
  public void setQueue(List<SongQueueEntryDto> queue) {
    this.queue = queue;
    if (showingHistory) {
      // A dequeue publishes SongQueueChangedEvent too, so this is also when history grows.
      refreshHistory(true);
    } else {
      SwingUtilities.invokeLater(() -> rebuildRows(true));
    }
  }

  /** Called by the tab {@code ChangeListener} whenever this tab is selected. Refreshes the list. */
  public void onShown() {
    if (showingHistory) {
      refreshHistory(true);
    } else {
      rebuildRows(true);
    }
    requestFocusInWindow();
  }

  /**
   * Resets the Queue tab to its default view: turns "Show History" off, clears the current row
   * selection and refreshes the queue list. Called after 10 minutes of inactivity, as part of
   * resetting every tab to its default state.
   */
  public void resetToDefaultView() {
    if (historyToggle != null) {
      historyToggle.setOn(false);
    }
    applyHistoryMode(false);
    requestFocusInWindow();
  }

  // ── Queue / history view switching ─────────────────────────────────────────

  /** "Show History" toggle listener (and reset path): swaps the table between queue and history. */
  private void applyHistoryMode(boolean on) {
    showingHistory = on;
    selectedIndex = -1;

    columnHeaderHolder.removeAll();
    columnHeaderHolder.add(buildColumnHeaderPanel(on), BorderLayout.CENTER);
    columnHeaderHolder.revalidate();
    columnHeaderHolder.repaint();

    actionArea.setVisible(!on);

    // Only the history view scrolls; the queue view shows just the rows that fit.
    queueRowsScroll.setVerticalScrollBarPolicy(on ? JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        : JScrollPane.VERTICAL_SCROLLBAR_NEVER);

    if (on) {
      history = List.of();
      rebuildRows(false); // clear the queue rows right away; history fills in when fetched
      refreshHistory(false);
    } else {
      rebuildRows(false);
    }
  }

  /**
   * Fetches the play history off the EDT, then rebuilds the rows with it.
   *
   * @param keepScroll keep the current scroll position (a live refresh) rather than jumping to the
   *        top (a fresh switch into the history view)
   */
  private void refreshHistory(boolean keepScroll) {
    SwingSecurityUtil.runAsync(() -> {
      List<SongPlayHistoryEntryDto> fetched;
      try {
        fetched = songQueueService.getSongPlayHistory();
      } catch (Exception ex) {
        ex.printStackTrace();
        return;
      }
      SwingUtilities.invokeLater(() -> {
        // Toggled back off while the fetch was in flight — the queue view owns the rows now.
        if (!showingHistory) {
          return;
        }
        history = fetched;
        rebuildRows(keepScroll);
      });
    });
  }

  // ── Layout ────────────────────────────────────────────────────────────────

  private JPanel buildMainPanel() {
    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setOpaque(false);
    main.setBorder(
        BorderFactory.createEmptyBorder(LayoutTheme.get().songQueueCardPadTop, 0, 14, 0));

    main.add(buildHeaderPanel(), BorderLayout.NORTH);
    main.add(buildQueueBody(), BorderLayout.CENTER);

    return main;
  }

  // ── Header ─────────────────────────────────────────────────────────────────

  /**
   * Builds the "Song Queue" header — same {@link DetailHeaderPanel} style as Home / Hot Here /
   * Genre Details, with the "Show History" toggle and the priority legend (formerly its own
   * "Queued Songs:" row) in the header's east slot. No back button and no artist/album/song
   * subtitle — there's nothing to navigate back from and nothing meaningful to count here.
   */
  private DetailHeaderPanel buildHeaderPanel() {
    int iconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon queueIcon = imageLoader.loadImage("Queue_Header_Image.png", iconSize, iconSize);

    // "Show History:" label + toggle, styled exactly like the Home tab's "Legacy:" toggle,
    // followed by the priority legend on the same row.
    JPanel eastRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    eastRow.setOpaque(false);

    JLabel historyLabel = new JLabel("Show History: ");
    historyLabel.setForeground(ColorTheme.get().textPrimary);
    historyLabel
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSortLabel));
    eastRow.add(historyLabel);

    historyToggle = new ToggleSwitch(false);
    historyToggle.setName(UiComponentNames.QUEUE_HISTORY_TOGGLE);
    historyToggle.setToggleListener(this::applyHistoryMode);
    eastRow.add(historyToggle);

    eastRow.add(Box.createHorizontalStrut(14));
    eastRow.add(SongTrackCellRenderer.buildPriorityLegend());

    // BorderLayout.EAST stretches the east panel to the header's full height, and the row's
    // own FlowLayout then lays it out starting from the top — pinning it level with the title
    // instead of the (taller) image. Vertical glue above pushes the row down to sit level with
    // the bottom of the header instead.
    JPanel legendWrapper = new JPanel();
    legendWrapper.setOpaque(false);
    legendWrapper.setLayout(new BoxLayout(legendWrapper, BoxLayout.Y_AXIS));
    legendWrapper.add(Box.createVerticalGlue());
    legendWrapper.add(eastRow);

    DetailHeaderPanel header = new DetailHeaderPanel(null, null, queueIcon, "♫", "Song Queue", null,
        legendWrapper, ColorTheme.get().frameTabAccentQueue);
    header.setOpaque(false);
    int hbH = LayoutTheme.get().homeHeaderBorderH;
    header.setBorder(new EmptyBorder(4, hbH, 4, hbH));
    return header;
  }

  /**
   * Builds the queue list and action area.
   *
   * <p>
   * The list itself is rendered exactly like {@link AlbumViewCard}'s track listing — same column
   * header row, same row layout/height/padding, same gradient card backdrop — rather than a JList
   * with a custom cell renderer. Unlike {@code AlbumViewCard} there is no footer nav panel and no
   * scroll bar: the queue view shows only as many rows as fit in the available height (see
   * {@link #fitRowCount()}), capped at {@link LayoutTheme#songQueueMaxVisible}.
   */
  private JPanel buildQueueBody() {
    JPanel section = new JPanel(new BorderLayout(0, 8));
    section.setOpaque(false);

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    columnHeaderHolder = new JPanel(new BorderLayout());
    columnHeaderHolder.setOpaque(false);
    columnHeaderHolder.add(buildColumnHeaderPanel(false), BorderLayout.CENTER);
    wrapper.add(columnHeaderHolder, BorderLayout.NORTH);

    // Rounded gradient card backdrop — identical paint to AlbumViewCard's track-list body,
    // so the queue list reads exactly like the album track listing.
    JPanel body = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0),
            new Point2D.Float(0, getHeight()), new float[] {0.0f, 1.0f},
            new Color[] {new Color(24, 38, 60, 225), new Color(12, 18, 30, 245)}));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    body.setOpaque(false);

    queueRowsPanel = new RowsPanel();
    queueRowsPanel.setOpaque(false);
    queueRowsPanel.setLayout(new BoxLayout(queueRowsPanel, BoxLayout.Y_AXIS));
    queueRowsPanel.setBorder(new EmptyBorder(ROWS_PANEL_PAD_V, 0, ROWS_PANEL_PAD_V, 0));

    // Scrolls only in the history view (applyHistoryMode switches the policy). The queue view
    // never shows a scroll bar: it renders only the rows that fit. Transparent so the gradient
    // card above shows through.
    queueRowsScroll = new JScrollPane(queueRowsPanel, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    // Re-fit (row count and row height) whenever the available height changes (first layout,
    // resize, the action buttons reappearing after the history view).
    queueRowsScroll.getViewport().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (!showingHistory
            && queueRowsScroll.getViewport().getHeight() != lastQueueViewportH) {
          rebuildRows(true);
        }
      }
    });
    queueRowsScroll.setOpaque(false);
    queueRowsScroll.getViewport().setOpaque(false);
    queueRowsScroll.setBorder(null);
    queueRowsScroll.getVerticalScrollBar().setUnitIncrement(LayoutTheme.get().albumViewRowH);
    // Big touch-friendly scroll bar unless user-interface.use-big-scrollbars is off (same as
    // LegacyAlbumGridPanel's track list).
    ScrollBars.apply(queueRowsScroll.getVerticalScrollBar());
    body.add(queueRowsScroll, BorderLayout.CENTER);

    wrapper.add(body, BorderLayout.CENTER);

    // Populate rows (up to songQueueMaxVisible until the first layout, then however many fit)
    rebuildRows(false);

    section.add(wrapper, BorderLayout.CENTER);
    actionArea = buildActionArea();
    section.add(actionArea, BorderLayout.SOUTH);

    return section;
  }

  /**
   * The rows container inside {@link #queueRowsScroll}. Rows ask for {@code Integer.MAX_VALUE}
   * width, so it must track the viewport's width (or it would scroll sideways), and it fills the
   * viewport's height when the rows are shorter than it, as it did before the scroll pane existed.
   */
  private static final class RowsPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return LayoutTheme.get().albumViewRowH;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return Math.max(visibleRect.height - LayoutTheme.get().albumViewRowH,
          LayoutTheme.get().albumViewRowH);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return getParent() instanceof JViewport viewport
          && viewport.getHeight() > getPreferredSize().height;
    }
  }

  /**
   * Builds the column-name header row above the queue rows — same style as {@code AlbumViewCard}'s
   * track-list header, with "Plays" followed by the "Genre" (only when genres are in use) /
   * "Artist" / "Album" / "Song" text columns (see {@link #buildTextColumns}). The history view adds
   * a "Time" column to the left of "Plays".
   */
  private JPanel buildColumnHeaderPanel(boolean history) {
    JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
    headerPanel.setBackground(Color.BLACK);
    headerPanel.setBorder(new EmptyBorder(8, 16, 8, 16));
    headerPanel.setPreferredSize(new Dimension(headerPanel.getPreferredSize().width, 45));

    Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist);

    JLabel popHeaderLabel = new JLabel("Plays");
    popHeaderLabel.setForeground(TEXT_SECONDARY);
    popHeaderLabel.setFont(headerFont);
    popHeaderLabel.setPreferredSize(new Dimension(LayoutTheme.get().albumViewPlaysColW, 30));
    popHeaderLabel.setHorizontalAlignment(SwingConstants.LEFT);

    JPanel headerLeftCluster = new JPanel(new BorderLayout(6, 0));
    headerLeftCluster.setOpaque(false);
    headerLeftCluster.add(popHeaderLabel, BorderLayout.WEST);

    JPanel headerCenterCluster =
        buildTextColumns("Genre", "Artist", "Album", "Song", TEXT_SECONDARY, headerFont);

    if (history) {
      JLabel timeHeaderLabel = new JLabel("Time");
      timeHeaderLabel.setForeground(TEXT_SECONDARY);
      timeHeaderLabel
          .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
      timeHeaderLabel
          .setPreferredSize(new Dimension(LayoutTheme.get().songQueueHistoryTimeColW, 30));
      timeHeaderLabel.setHorizontalAlignment(SwingConstants.LEFT);

      JPanel timeAndLeft = new JPanel(new BorderLayout(6, 0));
      timeAndLeft.setOpaque(false);
      timeAndLeft.add(timeHeaderLabel, BorderLayout.WEST);
      timeAndLeft.add(headerLeftCluster, BorderLayout.CENTER);
      headerPanel.add(timeAndLeft, BorderLayout.WEST);
    } else {
      headerPanel.add(headerLeftCluster, BorderLayout.WEST);
    }
    headerPanel.add(headerCenterCluster, BorderLayout.CENTER);

    return headerPanel;
  }

  /**
   * Builds a single queue or history row — same sizing and popularity-bar widget as
   * {@code AlbumViewCard.buildTrackRow} ([Time] | Plays | [Genre] | Artist | Album | Song). The
   * text columns share one font and are all tinted by the entry's queue priority (matching the
   * legend), switching to the accent colour when the row is the current selection for the
   * move/remove buttons.
   *
   * @param timeText start time shown in the leading "Time" column (history view), or
   *        {@code null} for a queue row, which has no such column
   * @param selectable whether the row can be clicked to select it for the move/remove buttons
   * @param rowHeight row height in pixels: {@link LayoutTheme#albumViewRowH} in the history view,
   *        stretched in the queue view so the visible rows fill the list
   */
  private JPanel buildRow(SongQueueEntryDto entry, int index, String timeText,
      boolean selectable, int rowHeight) {
    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setName(selectable ? UiComponentNames.QUEUE_ROW : UiComponentNames.QUEUE_HISTORY_ROW);
    row.setOpaque(false);
    int padV = LayoutTheme.get().albumViewRowPadV;
    row.setBorder(new EmptyBorder(padV, 16, padV, 16));

    Dimension rowSize = new Dimension(Integer.MAX_VALUE, rowHeight);
    row.setPreferredSize(rowSize);
    row.setMinimumSize(new Dimension(0, rowHeight));
    row.setMaximumSize(rowSize);
    if (selectable) {
      row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    boolean selected = selectable && index == selectedIndex;
    Color textColor = selected ? ACCENT_BLUE : priorityColor(entry);
    Font textFont = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackSong);

    // ── Popularity bars (WEST) ────────────────────────────────────────────
    int plays = entry.song().numPlays() == null ? 0 : entry.song().numPlays();
    int bars = SongTrackCellRenderer.barsForPlays(plays, popularityT1, popularityT2, popularityT3);
    JPanel barsPanel = new SongTrackCellRenderer.PopularityBarsPanel(bars);
    barsPanel.setOpaque(false);
    int barW = SongTrackCellRenderer.BAR_WIDTH;
    int barGap = SongTrackCellRenderer.BAR_GAP;
    int barMaxH = SongTrackCellRenderer.BAR_MAX_H;
    barsPanel.setPreferredSize(new Dimension(3 * (barW + barGap) + 6, barMaxH + 4));

    JPanel left = new JPanel(new BorderLayout(6, 0));
    left.setOpaque(false);

    JPanel barsAlignmentPanel = new JPanel(new BorderLayout());
    barsAlignmentPanel.setOpaque(false);
    barsAlignmentPanel.setPreferredSize(new Dimension(LayoutTheme.get().albumViewPlaysColW, 30));
    barsAlignmentPanel.add(barsPanel, BorderLayout.WEST);

    left.add(barsAlignmentPanel, BorderLayout.WEST);

    if (timeText != null) {
      JLabel timeLabel = new JLabel(timeText);
      timeLabel.setForeground(textColor);
      timeLabel.setFont(textFont);
      timeLabel.setPreferredSize(new Dimension(LayoutTheme.get().songQueueHistoryTimeColW, 30));
      timeLabel.setHorizontalAlignment(SwingConstants.LEFT);

      JPanel timeAndLeft = new JPanel(new BorderLayout(6, 0));
      timeAndLeft.setOpaque(false);
      timeAndLeft.add(timeLabel, BorderLayout.WEST);
      timeAndLeft.add(left, BorderLayout.CENTER);
      row.add(timeAndLeft, BorderLayout.WEST);
    } else {
      row.add(left, BorderLayout.WEST);
    }

    // ── Genre / Artist / Album / Song columns (CENTER) ────────────────────
    row.add(buildTextColumns(entry.song().genreName(), entry.song().artistName(),
        entry.song().albumName(), entry.song().songName(), textColor, textFont),
        BorderLayout.CENTER);

    if (selected) {
      row.setOpaque(true);
      row.setBackground(ColorTheme.get().bgListSelected);
    }

    if (!selectable) {
      return row; // history rows: no hover highlight or click-to-select
    }

    // ── Hover + click-to-select ────────────────────────────────────────────
    row.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        if (index != selectedIndex) {
          row.setOpaque(true);
          row.setBackground(BG_ROW_HOVER);
          queueRowsPanel.repaint();
        }
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        if (index != selectedIndex) {
          row.setOpaque(false);
          row.setBackground(null);
          queueRowsPanel.repaint();
        }
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        if (!TouchInput.isTap(e))
          return;
        selectedIndex = index;
        rebuildRows(true);
      }
    });

    return row;
  }

  /**
   * Builds the Genre / Artist / Album / Song text columns shared by the header row and every queue
   * or history row, so the header and rows always line up. The columns fill the available width by
   * weight alone (every label's preferred width is 0), using the {@code *_COL_WEIGHT} constants.
   * The Genre column is omitted entirely when genres are not in use. Every column after the first
   * has a thin vertical divider on its left edge, and text too long for its column is truncated
   * with an ellipsis before it reaches the next divider.
   */
  private JPanel buildTextColumns(String genre, String artist, String album, String song,
      Color color, Font font) {
    JPanel panel = new JPanel(new java.awt.GridBagLayout());
    panel.setOpaque(false);

    List<String> texts = new ArrayList<>();
    List<Double> weights = new ArrayList<>();
    if (useGenre) {
      texts.add(genre);
      weights.add(GENRE_COL_WEIGHT);
    }
    texts.add(artist);
    weights.add(ARTIST_COL_WEIGHT);
    texts.add(album);
    weights.add(ALBUM_COL_WEIGHT);
    texts.add(song);
    weights.add(SONG_COL_WEIGHT);

    for (int i = 0; i < texts.size(); i++) {
      JLabel label = new JLabel(texts.get(i) == null ? "" : texts.get(i));
      label.setForeground(color);
      label.setFont(font);
      label.setHorizontalAlignment(SwingConstants.LEFT);
      label.setPreferredSize(new Dimension(0, 30));
      label.setMinimumSize(new Dimension(0, 30));
      if (i > 0) {
        // Divider on the left edge, with padding so the text does not touch it
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, SEPARATOR),
            new EmptyBorder(0, TEXT_COL_PAD_LEFT, 0, 0)));
      }

      java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
      c.gridx = i;
      c.gridy = 0;
      c.weightx = weights.get(i);
      c.weighty = 1.0;
      c.fill = java.awt.GridBagConstraints.BOTH; // full height, so the dividers span the row
      c.insets = new java.awt.Insets(0, 0, 0, i < texts.size() - 1 ? TEXT_COL_GAP : 0);
      panel.add(label, c);
    }
    return panel;
  }

  /** The legend colour for the entry's queue priority (5 and above share the last colour). */
  private static Color priorityColor(SongQueueEntryDto entry) {
    int priority = entry.priority() == null ? 0 : entry.priority();
    int slot = Math.max(0, Math.min(priority, SongTrackCellRenderer.PRIORITY_COLORS.length - 1));
    return SongTrackCellRenderer.PRIORITY_COLORS[slot];
  }

  // ── Action area (buttons + timeout row) ──────────────────────────────────

  private JPanel buildActionArea() {
    int gap = LayoutTheme.get().songQueueActionAreaGap;
    JPanel area = new JPanel(new BorderLayout(0, gap));
    area.setOpaque(false);
    area.setBorder(new EmptyBorder(gap, 0, 0, 0));

    // Three management buttons
    JPanel buttons = new JPanel(new GridLayout(1, 3, gap * 2, 0));
    buttons.setOpaque(false);

    moveUpButton = createActionButton("Move Song Up", 0, ACCENT_BLUE, e -> doMoveUp());
    moveDownButton = createActionButton("Move Song Down", 0, ACCENT_BLUE, e -> doMoveDown());
    removeButton = createActionButton("Remove Song", 0, ACCENT_BLUE, e -> doRemove());
    moveUpButton.setName(UiComponentNames.QUEUE_MOVE_UP_BUTTON);
    moveDownButton.setName(UiComponentNames.QUEUE_MOVE_DOWN_BUTTON);
    removeButton.setName(UiComponentNames.QUEUE_REMOVE_BUTTON);

    buttons.add(moveUpButton);
    buttons.add(moveDownButton);
    buttons.add(removeButton);

    area.add(buttons, BorderLayout.NORTH);

    // Hook credit listener so buttons update when credits change
    creditListener = this::updateButtonStates;
    creditManager.addListener(creditListener);

    updateButtonStates();

    return area;
  }

  // ── Queue operations ──────────────────────────────────────────────────────

  private void doMoveUp() {
    if (selectedIndex < 0 || selectedIndex >= displayedQueue.size())
      return;
    SongQueueEntryDto selected = displayedQueue.get(selectedIndex);
    if (!deductCostFor(selected))
      return;
    int idx = selectedIndex;
    SwingSecurityUtil.runAsync(() -> {
      try {
        songQueueService.moveSongUpInQueue(songQueueService.getOwnLocationId(),
            new ChangeSongQueueRequest(selected.song().albumId(),
                selected.song().songId()));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    // Update local model optimistically
    SwingUtilities.invokeLater(() -> {
      if (idx > 0) {
        SongQueueEntryDto above = displayedQueue.get(idx - 1);
        displayedQueue.set(idx - 1, selected);
        displayedQueue.set(idx, above);
        selectedIndex = idx - 1;
        rebuildRows(true);
      }
    });
  }

  private void doMoveDown() {
    if (selectedIndex < 0 || selectedIndex >= displayedQueue.size())
      return;
    SongQueueEntryDto selected = displayedQueue.get(selectedIndex);
    if (!deductCostFor(selected))
      return;
    int idx = selectedIndex;
    SwingSecurityUtil.runAsync(() -> {
      try {
        songQueueService.moveSongDownInQueue(songQueueService.getOwnLocationId(),
            new ChangeSongQueueRequest(selected.song().albumId(),
                selected.song().songId()));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    SwingUtilities.invokeLater(() -> {
      if (idx < displayedQueue.size() - 1) {
        SongQueueEntryDto below = displayedQueue.get(idx + 1);
        displayedQueue.set(idx + 1, selected);
        displayedQueue.set(idx, below);
        selectedIndex = idx + 1;
        rebuildRows(true);
      }
    });
  }

  private void doRemove() {
    if (selectedIndex < 0 || selectedIndex >= displayedQueue.size())
      return;
    SongQueueEntryDto selected = displayedQueue.get(selectedIndex);
    if (!deductCostFor(selected))
      return;
    int idx = selectedIndex;
    SwingSecurityUtil.runAsync(() -> {
      try {
        songQueueService.removeSongDownFromQueue(songQueueService.getOwnLocationId(),
            new ChangeSongQueueRequest(selected.song().albumId(),
                selected.song().songId()));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    SwingUtilities.invokeLater(() -> {
      displayedQueue.remove(idx);
      selectedIndex = displayedQueue.isEmpty() ? -1 : Math.min(idx, displayedQueue.size() - 1);
      rebuildRows(true);
    });
  }

  /** Deducts the credit cost for the given entry and returns {@code true} on success. */
  private boolean deductCostFor(SongQueueEntryDto entry) {
    int cost = computeCost(entry);
    return creditManager.deductCredits(cost);
  }

  /** Cost = 3 × entry's priority (minimum 1). */
  private static int computeCost(SongQueueEntryDto entry) {
    int priority = entry.priority() == null ? 0 : entry.priority();
    return Math.max(1, priority * 3);
  }

  // ── Button state / label refresh ─────────────────────────────────────────

  private void updateButtonStates() {
    if (moveUpButton == null || moveDownButton == null || removeButton == null)
      return; // buttons not yet constructed (initial population during buildQueueBody)
    SongQueueEntryDto selected =
        (selectedIndex >= 0 && selectedIndex < displayedQueue.size()) ? displayedQueue.get(selectedIndex)
            : null;
    int currentCredits = creditManager.getCredits();
    int idx = selectedIndex;
    int size = displayedQueue.size();

    if (selected == null) {
      // No selection — all buttons go fully grey
      applyState(moveUpButton, ButtonState.GREY, 0);
      applyState(moveDownButton, ButtonState.GREY, 0);
      applyState(removeButton, ButtonState.GREY, 0);
    } else {
      int cost = computeCost(selected);
      boolean afford = currentCredits >= cost;

      // Move Up: not available when first item, warn/normal otherwise
      if (idx == 0) {
        applyState(moveUpButton, ButtonState.UNAVAILABLE, cost);
      } else {
        applyState(moveUpButton, afford ? ButtonState.NORMAL : ButtonState.WARN, cost);
      }

      // Move Down: not available when last item, warn/normal otherwise
      if (idx >= size - 1) {
        applyState(moveDownButton, ButtonState.UNAVAILABLE, cost);
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

  /**
   * Rebuilds the row panels for whichever view is showing.
   *
   * <p>
   * Queue view: rebuilds {@link #displayedQueue} from the (live) queue reference — used at
   * construction, onShown(), and every {@link #setQueue(List)} call (including the round trip after
   * a move/remove action, or an externally-triggered {@code SongQueueChangedEvent}). Restores the
   * previously-selected row at {@link #selectedIndex} (clamped to the rebuilt list's bounds) so the
   * same song stays selected across the rebuild.
   *
   * <p>
   * History view: one non-selectable row per {@link #history} entry, newest first, with its start
   * time in the leading "Time" column.
   *
   * @param keepScroll keep the current scroll position; otherwise scroll back to the top
   */
  private void rebuildRows(boolean keepScroll) {
    Point scrollPosition =
        keepScroll && queueRowsScroll != null ? queueRowsScroll.getViewport().getViewPosition()
            : new Point(0, 0);

    queueRowsPanel.removeAll();

    if (showingHistory) {
      displayedQueue.clear();
      if (history.isEmpty()) {
        JLabel empty = new JLabel("No songs have played yet.", SwingConstants.CENTER);
        empty.setForeground(TEXT_SECONDARY);
        empty.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));
        empty.setAlignmentX(CENTER_ALIGNMENT);
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, LayoutTheme.get().albumViewRowH));
        empty.setPreferredSize(new Dimension(0, LayoutTheme.get().albumViewRowH));
        queueRowsPanel.add(empty);
      }
      for (int i = 0; i < history.size(); i++) {
        SongPlayHistoryEntryDto played = history.get(i);
        queueRowsPanel.add(buildRow(played.entry(), i,
            HISTORY_TIME_FORMAT.format(played.startedAt()), false, LayoutTheme.get().albumViewRowH));
        if (i < history.size() - 1) {
          queueRowsPanel.add(buildSeparator());
        }
      }
    } else {
      displayedQueue.clear();
      int viewportH = queueRowsScroll == null ? 0 : queueRowsScroll.getViewport().getHeight();
      lastQueueViewportH = viewportH;
      int fitRows = fitRowCount();
      if (queue != null) {
        int limit = Math.min(queue.size(), fitRows);
        for (int i = 0; i < limit; i++) {
          displayedQueue.add(queue.get(i));
        }
      }
      if (selectedIndex >= displayedQueue.size()) {
        selectedIndex = displayedQueue.isEmpty() ? -1 : displayedQueue.size() - 1;
      }
      // Select the first song by default when the queue is non-empty, so the move/remove buttons
      // show straight away what can be done and what it costs (or how many credits to add).
      if (selectedIndex < 0 && !displayedQueue.isEmpty()) {
        selectedIndex = 0;
      }

      // Stretch the rows so that fitRows of them exactly fill the list, leaving no gap at the
      // bottom that would suggest the queue ends there. The heights are the same whether or not
      // the queue is full, so a short queue keeps its rows the same size and its gap is real.
      // Leftover pixels go one each to the first rows.
      int rowH = LayoutTheme.get().albumViewRowH;
      int extraPx = 0;
      if (viewportH > 0) {
        int avail = viewportH - 2 * ROWS_PANEL_PAD_V - (fitRows - 1) * ROW_SEPARATOR_H;
        rowH = Math.max(rowH, avail / fitRows);
        extraPx = rowH * fitRows < avail ? avail - rowH * fitRows : 0;
      }
      for (int i = 0; i < displayedQueue.size(); i++) {
        int h = rowH + (i < extraPx ? 1 : 0);
        queueRowsPanel.add(buildRow(displayedQueue.get(i), i, null, true, h));
        if (i < displayedQueue.size() - 1) {
          queueRowsPanel.add(buildSeparator());
        }
      }
    }

    queueRowsPanel.revalidate();
    queueRowsPanel.repaint();

    // Restore the scroll position once the new rows have been laid out.
    if (queueRowsScroll != null) {
      SwingUtilities.invokeLater(() -> {
        JViewport viewport = queueRowsScroll.getViewport();
        int maxY = Math.max(0, queueRowsPanel.getHeight() - viewport.getHeight());
        viewport.setViewPosition(new Point(0, Math.min(scrollPosition.y, maxY)));
      });
    }

    updateButtonStates();
  }

  /**
   * Number of whole queue rows (at the minimum {@link LayoutTheme#albumViewRowH}) that fit in the
   * viewport's current height: n rows take {@code n * rowH + (n - 1) * separator + top/bottom
   * padding}. {@link #rebuildRows(boolean)} then stretches those rows to fill the list. Capped at
   * {@link LayoutTheme#songQueueMaxVisible}, and falls back to that cap before the first layout
   * (viewport height 0). Always at least 1 so a queued song is never hidden entirely.
   */
  private int fitRowCount() {
    int max = LayoutTheme.get().songQueueMaxVisible;
    if (queueRowsScroll == null) {
      return max;
    }
    int viewportH = queueRowsScroll.getViewport().getHeight();
    if (viewportH <= 0) {
      return max;
    }
    int rowH = LayoutTheme.get().albumViewRowH;
    int fit = (viewportH - 2 * ROWS_PANEL_PAD_V + ROW_SEPARATOR_H) / (rowH + ROW_SEPARATOR_H);
    return Math.max(1, Math.min(max, fit));
  }

  private static JSeparator buildSeparator() {
    JSeparator sep = new JSeparator();
    sep.setForeground(SEPARATOR);
    sep.setBackground(SEPARATOR);
    sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_SEPARATOR_H));
    return sep;
  }

  // ── Button factories ──────────────────────────────────────────────────────

  /**
   * Creates an action button with four distinct visual states driven by the {@code "buttonState"}
   * client property ({@link ButtonState}):
   *
   * <ul>
   * <li>{@code GREY} — fully dimmed flat surface, no sub-label. Used when no song is selected.</li>
   * <li>{@code UNAVAILABLE} — dark-red 3-D body, red border, "NOT AVAILABLE" sub-label (same as
   * {@link AddSongToQueueCard}'s unavailable Priority Play). Used for Move Up on the first song and
   * Move Down on the last.</li>
   * <li>{@code WARN} — dark-red 3-D body, red border, "ADD N CREDITS" sub-label.</li>
   * <li>{@code NORMAL} — standard 3-D body, accent border, "Ncr" cost sub-label.</li>
   * </ul>
   *
   * The 3-D body (including hover and pressed looks) is painted by {@link Button3D}.
   */
  private JButton createActionButton(String actionText, int initialCost, Color accentColor,
      java.awt.event.ActionListener onClick) {

    JButton button = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = Button3D.createGraphics(g);

        Object stateProp = getClientProperty("buttonState");
        ButtonState state =
            (stateProp instanceof ButtonState) ? (ButtonState) stateProp : ButtonState.GREY;
        Object costProp = getClientProperty("cost");
        int cost = (costProp instanceof Integer) ? (Integer) costProp : initialCost;

        if (state == ButtonState.GREY) {
          // ── Flat dimmed surface — no shadow, no depth ──────────────────
          int w = getWidth(), visH = getHeight() - 5, arc = Button3D.ARC;
          g2.setColor(ColorTheme.get().btnGreyFace);
          g2.fillRoundRect(1, 0, w - 2, visH, arc, arc);
          g2.setColor(ColorTheme.get().btnGreyBorder);
          g2.setStroke(new java.awt.BasicStroke(1.5f));
          g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

          // Action text only — muted, no sub-label
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueBtn));
          FontMetrics fm = g2.getFontMetrics();
          g2.setColor(ColorTheme.get().btnGreyText);
          Button3D.drawCentered(g2, actionText, w, (visH - fm.getHeight()) / 2 + fm.getAscent());

        } else {
          // ── 3-D body (shared with AddSongToQueueCard) ─────────────────
          boolean normal = (state == ButtonState.NORMAL);
          Button3D.Face face = Button3D.paintBody(g2, this, !normal, accentColor);
          int w = face.width();
          int visH = face.visH();

          // Two-line centred label. Both lines are measured by their actual
          // ascent+descent (not getHeight(), which also includes font leading) so the
          // tight 4px gap between them is the only space between the lines — then the
          // whole two-line block (as a single tight unit) is centred against the full
          // visible button height (visH). Using getHeight() here previously inflated
          // the apparent line spacing and pushed line 1 toward the top and line 2
          // toward the bottom instead of keeping them together as a centred pair.
          Font font1 = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueBtn);
          Font font2 = new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist);
          FontMetrics fm1 = g2.getFontMetrics(font1);
          FontMetrics fm2 = g2.getFontMetrics(font2);
          int lineGap = 4;
          int line1H = fm1.getAscent() + fm1.getDescent();
          int line2H = fm2.getAscent() + fm2.getDescent();
          int blockTop = (visH - (line1H + lineGap + line2H)) / 2;
          int line1Y = blockTop + fm1.getAscent();
          int line2Y = blockTop + line1H + lineGap + fm2.getAscent();

          g2.setFont(font1);
          g2.setColor(ColorTheme.get().textPrimary);
          Button3D.drawCentered(g2, actionText, w, line1Y);

          g2.setFont(font2);
          String line2;
          if (normal) {
            line2 = creditManager.formatCredits(cost);
            g2.setColor(ACCENT_GOLD);
          } else if (state == ButtonState.UNAVAILABLE) {
            line2 = Button3D.NOT_AVAILABLE_TEXT;
            g2.setColor(ColorTheme.get().btn3dWarnBorder);
          } else {
            line2 = creditManager.formatShortfall(Math.max(0, cost - creditManager.getCredits()));
            g2.setColor(ColorTheme.get().btn3dWarnBorder);
          }
          Button3D.drawCentered(g2, line2, w, line2Y);
        }

        g2.dispose();
      }
    };

    button.putClientProperty("buttonState", ButtonState.GREY);
    button.putClientProperty("cost", initialCost);
    button.setEnabled(false);
    Button3D.configure(button, new Dimension(LayoutTheme.get().songQueueActionBtnW,
        LayoutTheme.get().songQueueActionBtnH));
    button.addActionListener(onClick);

    return button;
  }

}
