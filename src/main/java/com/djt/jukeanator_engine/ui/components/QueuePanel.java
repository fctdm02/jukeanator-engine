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
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
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
   * Three distinct visual states for the action buttons. GREY = no selection (or positional lock) —
   * fully dimmed, no sub-label. WARN = selected but not enough credits — red border, "ADD N
   * CREDITS". NORMAL = selected and affordable — accent border, "Ncr" cost label.
   */
  private enum ButtonState {
    GREY, WARN, NORMAL
  }

  // ── Colours ───────────────────────────────────────────────────────────────
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_GOLD = new Color(255, 200, 0);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color AM_WARN_BORDER = new Color(220, 40, 40);

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
  private List<SongQueueEntryDto> queue;
  private final SongQueueService songQueueService;
  private final CreditManager creditManager;
  private final ImageLoader imageLoader;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;

  // ── Queue list ────────────────────────────────────────────────────────────
  private final DefaultListModel<SongQueueEntryDto> queueListModel = new DefaultListModel<>();
  private final JList<SongQueueEntryDto> queueList = new JList<>(queueListModel);

  // ── Action buttons ────────────────────────────────────────────────────────
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
      int popularityT3) {

    this.queue = initialQueue;
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
    SwingUtilities.invokeLater(this::refreshQueueListModel);
  }

  /** Called by the tab {@code ChangeListener} whenever this tab is selected. Refreshes the queue list. */
  public void onShown() {
    refreshQueueListModel();
    requestFocusInWindow();
  }

  /**
   * Resets the Queue tab to its default view: clears the current row selection and refreshes the
   * queue list, matching the {@code resetToDefaultView()} contract used by Home / Search / Hot
   * Here / Genres when the tab is switched to.
   */
  public void resetToDefaultView() {
    selectedIndex = -1;
    queueList.clearSelection();
    refreshQueueListModel();
    requestFocusInWindow();
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
   * Genre Details, with the priority legend (formerly its own "Queued Songs:" row) moved into the
   * header's east slot. No back button and no artist/album/song subtitle — there's nothing to
   * navigate back from and nothing meaningful to count here.
   */
  private DetailHeaderPanel buildHeaderPanel() {
    int iconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon queueIcon = imageLoader.loadImage("Queue_Header_Image.png", iconSize, iconSize);

    // BorderLayout.EAST stretches the legend panel to the header's full height, and its
    // own FlowLayout then lays the row out starting from the top — pinning it level with
    // the title instead of the (taller) image. Vertical glue above pushes the row down to
    // sit level with the bottom of the header instead.
    JPanel legendWrapper = new JPanel();
    legendWrapper.setOpaque(false);
    legendWrapper.setLayout(new BoxLayout(legendWrapper, BoxLayout.Y_AXIS));
    legendWrapper.add(Box.createVerticalGlue());
    legendWrapper.add(SongTrackCellRenderer.buildPriorityLegend());

    DetailHeaderPanel header = new DetailHeaderPanel(null, null, queueIcon, "♫", "Song Queue", null,
        legendWrapper, ColorTheme.get().frameTabAccentQueue);
    header.setOpaque(false);
    int hbH = LayoutTheme.get().homeHeaderBorderH;
    header.setBorder(new EmptyBorder(4, hbH, 4, hbH));
    return header;
  }

  /**
   * Builds the queue list and action area.
   */
  private JPanel buildQueueBody() {
    JPanel section = new JPanel(new BorderLayout(0, 8));
    section.setOpaque(false);

    // Populate model (up to MAX_QUEUE_VISIBLE entries)
    refreshQueueListModel();

    SongTrackCellRenderer.installWithPriority(queueList, popularityT1, popularityT2, popularityT3,
        imageLoader);
    // Transparent so the underlying screen gradient shows through, matching the Search / Hot
    // Here / Genre result lists — no opaque black list/row backgrounds.
    queueList.setOpaque(false);
    queueList.setForeground(TEXT_PRIMARY);
    queueList.setSelectionBackground(ColorTheme.get().bgListSelected);
    queueList.setSelectionForeground(Color.WHITE);
    queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    queueList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        selectedIndex = queueList.getSelectedIndex();
        updateButtonStates();
      }
    });

    // Fix the list height to exactly songQueueMaxVisible rows — no scroll bar needed.
    int listH = SongTrackCellRenderer.CELL_HEIGHT * LayoutTheme.get().songQueueMaxVisible;
    queueList.setPreferredSize(new Dimension(Integer.MAX_VALUE, listH));
    queueList.setMinimumSize(new Dimension(0, listH));
    queueList.setMaximumSize(new Dimension(Integer.MAX_VALUE, listH));

    // Rounded gradient card backdrop — identical paint to ResultsColumnPanel's
    // innerColumnBody, so the queue list reads exactly like a Hot Here / Search /
    // Genre result column. No JScrollPane, so no scroll bar can appear.
    JPanel listWrapper = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0),
            new Point2D.Float(0, getHeight()), new float[] {0.0f, 1.0f},
            new Color[] {ColorTheme.get().columnGradTop, ColorTheme.get().columnGradBottom}));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    listWrapper.setOpaque(false);
    listWrapper.setBorder(new EmptyBorder(4, 0, 4, 0));
    listWrapper.add(queueList, BorderLayout.CENTER);

    section.add(listWrapper, BorderLayout.CENTER);
    section.add(buildActionArea(), BorderLayout.SOUTH);

    return section;
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
    SongQueueEntryDto selected = queueList.getSelectedValue();
    if (selected == null || !deductCostFor(selected))
      return;
    int idx = queueList.getSelectedIndex();
    SwingSecurityUtil.runAsync(() -> {
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
    SwingSecurityUtil.runAsync(() -> {
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
    SwingSecurityUtil.runAsync(() -> {
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
      } else {
        selectedIndex = -1;
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
      return; // buttons not yet constructed (initial population during buildQueueBody)
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

  /**
   * Rebuilds the queue list model from the (live) queue reference — used at construction,
   * onShown(), and every {@link #setQueue(List)} call (including the round trip after a
   * move/remove action, or an externally-triggered {@code SongQueueChangedEvent}). Restores the
   * previously-selected row at {@link #selectedIndex} (clamped to the rebuilt model's bounds) so
   * the same song stays selected across the rebuild.
   *
   * <p>
   * The desired index is snapshotted into a local before {@code clear()} runs, because clearing the
   * model fires a selection-changed event that — via the list's own selection listener — resets
   * {@link #selectedIndex} to {@code -1} before this method would otherwise get a chance to use it.
   */
  private void refreshQueueListModel() {
    int desiredIndex = selectedIndex;
    queueListModel.clear();
    if (queue != null) {
      int limit = Math.min(queue.size(), LayoutTheme.get().songQueueMaxVisible);
      for (int i = 0; i < limit; i++) {
        queueListModel.addElement(queue.get(i));
      }
    }
    if (desiredIndex >= 0 && queueListModel.getSize() > 0) {
      queueList.setSelectedIndex(Math.min(desiredIndex, queueListModel.getSize() - 1));
    } else {
      selectedIndex = -1;
    }
    updateButtonStates();
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
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueBtn));
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

          // 7. Two-line centred label. Both lines are measured by their actual
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
          g2.setColor(TEXT_PRIMARY);
          g2.drawString(actionText, (w - fm1.stringWidth(actionText)) / 2, line1Y);

          g2.setFont(font2);
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

}
