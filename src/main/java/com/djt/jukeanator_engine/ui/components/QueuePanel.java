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
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
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

  // ── Row text ──────────────────────────────────────────────────────────────
  /** Header text of the priority column; the column is sized to fit it. */
  private static final String PRI_HEADER_TEXT = "Priority";
  /** Extra width added to the "Priority" and "Time" columns beyond their measured text width. */
  private static final int LEAD_COL_PAD = 8;
  /** Header text of the history view's start-time column. */
  private static final String TIME_HEADER_TEXT = "Time";
  /**
   * Widest possible start time in {@link #HISTORY_TIME_FORMAT} ("h:mma"): two-digit hour, using
   * the widest digit, so the Time column always fits without wasting space.
   */
  private static final String[] WIDEST_TIME_SAMPLES = {"88:88AM", "88:88PM"};
  /** Separator between genre, artist and album on a row's second line. */
  private static final String DETAILS_SEPARATOR = "  ·  ";

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
   * Genre Details, with the "Show History" toggle in the header's east slot (each row shows its
   * priority number in the "Priority" column, so there is no priority legend). No back button and no
   * artist/album/song
   * subtitle — there's nothing to navigate back from and nothing meaningful to count here.
   */
  private DetailHeaderPanel buildHeaderPanel() {
    int iconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon queueIcon = imageLoader.loadImage("Queue_Header_Image.png", iconSize, iconSize);

    // "Show History:" label + toggle, styled exactly like the Home tab's "Legacy:" toggle.
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
    queueRowsScroll.getVerticalScrollBar().setUnitIncrement(minRowH());
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
      return minRowH();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return Math.max(visibleRect.height - minRowH(), minRowH());
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

  /** Font of the column-name header row ("Time" / "Priority" / "Song"). */
  private static Font headerFont() {
    return new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist);
  }

  /** Font of the song name (row line 1) — same as the Hot Here song rows. */
  private static Font songNameFont() {
    return new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAlbumLabel);
  }

  /**
   * Width of the "Priority" column: just wide enough for its header label (or a two-digit priority
   * in the row font, whichever is wider) plus a little padding, since priorities stay in single
   * digits in practice.
   */
  private int priColW() {
    int header = getFontMetrics(headerFont()).stringWidth(PRI_HEADER_TEXT);
    int value = getFontMetrics(songNameFont()).stringWidth("88");
    return Math.max(header, value) + LEAD_COL_PAD;
  }

  /**
   * Width of the history view's "Time" column: just wide enough for the widest possible start time
   * in the row font (or the header label, whichever is wider) plus a little padding. The time
   * format is fixed, so the column never needs more.
   */
  private int timeColW() {
    int width = getFontMetrics(headerFont()).stringWidth(TIME_HEADER_TEXT);
    FontMetrics fm = getFontMetrics(songNameFont());
    for (String sample : WIDEST_TIME_SAMPLES) {
      width = Math.max(width, fm.stringWidth(sample));
    }
    return width + LEAD_COL_PAD;
  }

  /**
   * Minimum height of a queue/history row: tall enough for the album art thumbnail plus the row's
   * top/bottom padding, and never less than the shared {@link LayoutTheme#albumViewRowH}. The queue
   * view fits as many rows of this height as it can, then stretches them to fill the list.
   */
  private static int minRowH() {
    LayoutTheme lt = LayoutTheme.get();
    return Math.max(lt.albumViewRowH, lt.resultThumbSize + 2 * lt.albumViewRowPadV);
  }

  /**
   * Builds the leading fixed-width cluster shared by the header and every row, so they line up:
   * [Time] (history view only) then Priority.
   */
  private JPanel buildLeadCluster(JLabel timeLabel, JLabel priLabel) {
    priLabel.setPreferredSize(new Dimension(priColW(), 30));
    priLabel.setHorizontalAlignment(SwingConstants.CENTER);

    JPanel cluster = new JPanel(new BorderLayout(6, 0));
    cluster.setOpaque(false);
    if (timeLabel != null) {
      timeLabel.setPreferredSize(new Dimension(timeColW(), 30));
      timeLabel.setHorizontalAlignment(SwingConstants.LEFT);
      cluster.add(timeLabel, BorderLayout.WEST);
    }
    cluster.add(priLabel, BorderLayout.CENTER);
    return cluster;
  }

  /**
   * Builds the column-name header row above the queue rows: "Priority" over the priority column and
   * "Song" over the play bars, album art and song text. The history view adds a "Time" column to
   * the left of "Priority".
   */
  private JPanel buildColumnHeaderPanel(boolean history) {
    JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
    headerPanel.setBackground(Color.BLACK);
    headerPanel.setBorder(new EmptyBorder(8, 16, 8, 16));
    headerPanel.setPreferredSize(new Dimension(headerPanel.getPreferredSize().width, 45));

    Font font = headerFont();
    JLabel timeHeaderLabel = history ? headerLabel(TIME_HEADER_TEXT, font) : null;
    JLabel priHeaderLabel = headerLabel(PRI_HEADER_TEXT, font);
    JLabel songHeaderLabel = headerLabel("Song", font);

    headerPanel.add(buildLeadCluster(timeHeaderLabel, priHeaderLabel), BorderLayout.WEST);
    headerPanel.add(songHeaderLabel, BorderLayout.CENTER);
    return headerPanel;
  }

  private static JLabel headerLabel(String text, Font font) {
    JLabel label = new JLabel(text);
    label.setForeground(TEXT_SECONDARY);
    label.setFont(font);
    return label;
  }

  /**
   * Second line of a row, e.g. "Rock  ·  Moby  ·  Play": the genre (only when genres are in use),
   * artist and album, skipping any that are blank.
   */
  private String songDetailsText(SongDto song) {
    List<String> parts = new ArrayList<>();
    if (useGenre) {
      addIfNotBlank(parts, song.genreName());
    }
    addIfNotBlank(parts, song.artistName());
    addIfNotBlank(parts, song.albumName());
    return String.join(DETAILS_SEPARATOR, parts);
  }

  private static void addIfNotBlank(List<String> parts, String text) {
    if (text != null && !text.isBlank()) {
      parts.add(text);
    }
  }

  /**
   * Zeroes a label's preferred/minimum width while keeping its font-driven height, so long text is
   * truncated with an ellipsis instead of widening the row (same as {@code
   * ResultsColumnPanel.capTextWidth}).
   */
  private static void capTextWidth(JLabel label) {
    Dimension d = new Dimension(0, label.getPreferredSize().height);
    label.setMinimumSize(d);
    label.setPreferredSize(d);
  }

  /** Wraps a component so it keeps its preferred size and sits vertically centred in the row. */
  private static JPanel centered(java.awt.Component c) {
    JPanel wrapper = new JPanel(new java.awt.GridBagLayout());
    wrapper.setOpaque(false);
    wrapper.add(c);
    return wrapper;
  }

  /**
   * Builds a single queue or history row, styled like the Hot Here Songs rows ({@code
   * ResultsColumnPanel.buildItemRow}) but without a rank number: [Time] | Priority | play bars | album
   * art | two-line text (song name over "Genre · Artist · Album"). The text uses the Hot Here
   * colours; a selected row keeps them and only changes its background.
   *
   * @param timeText start time shown in the leading "Time" column (history view), or
   *        {@code null} for a queue row, which has no such column
   * @param selectable whether the row can be clicked to select it for the move/remove buttons
   * @param rowHeight row height in pixels: {@link #minRowH()} in the history view, stretched in
   *        the queue view so the visible rows fill the list
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
    LayoutTheme lt = LayoutTheme.get();
    SongDto song = entry.song();
    Font songFont = songNameFont();

    // ── [Time] | Priority (WEST) ──────────────────────────────────────────────
    JLabel timeLabel = null;
    if (timeText != null) {
      timeLabel = new JLabel(timeText);
      timeLabel.setForeground(ColorTheme.get().textPrimary);
      timeLabel.setFont(songFont);
    }
    int priority = entry.priority() == null ? 0 : entry.priority();
    JLabel priLabel = new JLabel(String.valueOf(priority));
    priLabel.setForeground(ColorTheme.get().textPrimary);
    priLabel.setFont(songFont);
    row.add(buildLeadCluster(timeLabel, priLabel), BorderLayout.WEST);

    // ── Popularity bars — the small Hot Here variant ──────────────────────
    int plays = song.numPlays() == null ? 0 : song.numPlays();
    int active = SongTrackCellRenderer.barsForPlays(plays, popularityT1, popularityT2, popularityT3);
    SongTrackCellRenderer.PopularityBarsPanel bars = new SongTrackCellRenderer.PopularityBarsPanel(
        active, lt.popularityBarWidthSmall, lt.popularityBarGapSmall,
        SongTrackCellRenderer.computeBarHeights(lt.popularityBarMaxHSmall));
    bars.setOpaque(false);
    bars.setPreferredSize(new Dimension(3 * (lt.popularityBarWidthSmall + lt.popularityBarGapSmall),
        lt.popularityBarMaxHSmall + 4));

    // ── Album art ─────────────────────────────────────────────────────────
    JLabel thumb = new JLabel();
    thumb.setPreferredSize(new Dimension(lt.resultThumbSize, lt.resultThumbSize));
    thumb.setHorizontalAlignment(SwingConstants.CENTER);
    thumb.setOpaque(true);
    thumb.setBackground(ColorTheme.get().bgThumb);
    if (song.coverArtPath() != null && imageLoader != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(song.coverArtPath(), lt.resultThumbSize,
            lt.resultThumbSize);
        if (icon != null) {
          thumb.setIcon(icon);
        }
      } catch (Exception ignored) {
        // no art: leave the placeholder thumbnail background showing
      }
    }

    JPanel barsAndThumb = new JPanel(new BorderLayout(6, 0));
    barsAndThumb.setOpaque(false);
    barsAndThumb.add(centered(bars), BorderLayout.WEST);
    barsAndThumb.add(centered(thumb), BorderLayout.CENTER);

    // ── Two-line text: song name over "Genre · Artist · Album" ──────────
    JLabel line1 = new JLabel(song.songName());
    line1.setFont(songFont);
    line1.setForeground(ColorTheme.get().textPrimary);
    JLabel line2 = new JLabel(songDetailsText(song));
    line2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, lt.fontSizeResultLine2));
    line2.setForeground(ColorTheme.get().textResultsSecondary);
    capTextWidth(line1);
    capTextWidth(line2);
    line1.setAlignmentX(LEFT_ALIGNMENT);
    line2.setAlignmentX(LEFT_ALIGNMENT);
    line1.setMaximumSize(new Dimension(Integer.MAX_VALUE, line1.getPreferredSize().height));
    line2.setMaximumSize(new Dimension(Integer.MAX_VALUE, line2.getPreferredSize().height));

    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
    textPanel.add(Box.createVerticalGlue());
    textPanel.add(line1);
    textPanel.add(Box.createVerticalStrut(3));
    textPanel.add(line2);
    textPanel.add(Box.createVerticalGlue());

    JPanel songCluster = new JPanel(new BorderLayout(10, 0));
    songCluster.setOpaque(false);
    songCluster.add(barsAndThumb, BorderLayout.WEST);
    songCluster.add(textPanel, BorderLayout.CENTER);
    row.add(songCluster, BorderLayout.CENTER);

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
      // Otherwise this is the last displayed row with more songs queued behind it: the selected
      // song moves out of view, and selectedIndex stays on this row so that, once the server
      // round trip (setQueue) rebuilds the rows, the song that was moved up is selected.
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
    // The full queue, not just the rows that fit on screen: the last displayed song can still be
    // moved down when more songs are queued behind it.
    int size = Math.max(displayedQueue.size(), queue == null ? 0 : queue.size());

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

      // Move Down: not available when last item in the whole queue, warn/normal otherwise
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
            HISTORY_TIME_FORMAT.format(played.startedAt()), false, minRowH()));
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
      int rowH = minRowH();
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
   * Number of whole queue rows (at the minimum {@link #minRowH()}) that fit in the
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
    int rowH = minRowH();
    int fit =(viewportH - 2 * ROWS_PANEL_PAD_V + ROW_SEPARATOR_H) / (rowH + ROW_SEPARATOR_H);
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
