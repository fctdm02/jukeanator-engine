package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

// ─────────────────────────────────────────────────────────────────────────
// CONSTRUCTOR
// ─────────────────────────────────────────────────────────────────────────
public class AddSongToQueueCard extends JPanel {

  private static final long serialVersionUID = 1L;

  /** Client property holding the text a queue button shows when disabled for a non-credit reason. */
  private static final String UNAVAILABLE_TEXT_PROPERTY = "jukeanator.unavailableText";
  private static final String PRIORITY_PLAY_UNAVAILABLE_TEXT = Button3D.NOT_AVAILABLE_TEXT;

  private final ImageLoader imageLoader;
  private final SongDto song;
  private final int priorityCostMultiplier;
  private final SongQueueService songQueueService;

  private final CreditManager creditManager;
  private final Runnable onDismiss;
  private final int normalPlayCost = 1;
  private JButton normalButton;
  private JButton priorityButton;
  private Runnable creditListener;

  /**
   * Auto-dismisses the card after {@link LayoutTheme#overlayTimeoutSeconds} of it sitting open
   * with no Play/Priority Play/Cancel tap -- otherwise it floats indefinitely above the tab strip
   * (it isn't part of any tab's own {@code resetToDefaultView()} state, so the frame's 10-minute
   * idle-reset never reaches it).
   */
  private final Timer timeoutTimer;

  /** Card name for the normal play-selection view. */
  private static final String INNER_CARD_MAIN = "MAIN";

  /** Card name for the song-constraint / ineligible view. */
  private static final String INNER_CARD_CONSTRAINT = "CONSTRAINT";
  private JLabel constraintLabel;
  private String constraintLabelText =
      "<html><div style='text-align:center;'>This song cannot be played at this time because<br>[REASON].</div></html>";

  /** Inner card layout that switches between the main panel and the constraint panel. */
  private final CardLayout innerCardLayout = new CardLayout();

  /** Container that holds both inner cards. */
  private JPanel innerCardRoot; // assigned in buildBorderPanel()

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AddSongToQueueCard(SongDto song, ImageLoader imageLoader, int priorityCostMultiplier,
      SongQueueService songQueueService, CreditManager creditManager, Runnable onDismiss) {

    this.imageLoader = imageLoader;
    this.song = song;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.songQueueService = songQueueService;
    this.creditManager = creditManager;
    this.onDismiss = onDismiss;

    this.timeoutTimer = new Timer(LayoutTheme.get().overlayTimeoutSeconds * 1000, e -> dismiss());
    this.timeoutTimer.setRepeats(false);

    setOpaque(false);
    setLayout(new java.awt.GridBagLayout());
    JPanel sized = buildBorderPanel();
    sized.setPreferredSize(
        new Dimension(LayoutTheme.get().addSongCardW, LayoutTheme.get().addSongCardH));
    add(sized);
  }

  // Background is painted by overlayRoot in JukeANatorFrame — no paintComponent override needed.

  /** Called whenever this card is shown — resets focus and (re)starts the auto-dismiss timeout. */
  public void onShown() {
    timeoutTimer.restart();
    requestFocusInWindow();
  }

  private JPanel buildBorderPanel() {
    JPanel border = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = -6872340517984425563L;

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ColorTheme.get().accentBlue);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
        g2.dispose();
      }
    };
    border.setOpaque(false);
    border.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    // ── Inner card root ───────────────────────────────────────────────────
    innerCardRoot = new JPanel(innerCardLayout);
    innerCardRoot.setOpaque(false);
    innerCardRoot.add(buildMainPanel(), INNER_CARD_MAIN);
    innerCardRoot.add(buildConstraintPanel(), INNER_CARD_CONSTRAINT);
    // Default: show the normal play panel
    innerCardLayout.show(innerCardRoot, INNER_CARD_MAIN);

    border.add(innerCardRoot);
    return border;
  }

  /**
   * Builds the "song cannot be played at this time" panel.
   *
   * <p>
   * Layout mirrors the main panel dimensions so the card swap is seamless: same background, same
   * rounded border managed by the outer border panel, same 900 × 420 preferred size. The OK button
   * is produced by {@link #createCancelButton(String)} so it is visually identical to Cancel.
   */
  private JPanel buildConstraintPanel() {

    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.setBackground(ColorTheme.get().bgOverlayCard);
    panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

    // ── Icon row (top) ────────────────────────────────────────────────────
    JLabel iconLabel = new JLabel("\u26A0", SwingConstants.CENTER); // ⚠ warning sign
    iconLabel.setForeground(ColorTheme.get().accentGold);
    iconLabel
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().addSongCoverSize / 2)); // proportional
    iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

    // ── Message (centre) ─────────────────────────────────────────────────
    JPanel messagePanel = new JPanel();
    messagePanel.setOpaque(false);
    messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));

    JLabel headingLabel = new JLabel("Song Unavailable to Play", SwingConstants.CENTER);
    headingLabel.setAlignmentX(CENTER_ALIGNMENT);
    headingLabel.setForeground(ColorTheme.get().textPrimary);
    headingLabel
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongTitle));

    JLabel songNameLabel = new JLabel(song.songName() != null
        ? "\u201C" + song.songName() + "\u201D by " + song.artistName() + ""
        : "", SwingConstants.CENTER);
    songNameLabel.setAlignmentX(CENTER_ALIGNMENT);
    songNameLabel.setForeground(ColorTheme.get().accentGold);
    songNameLabel
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongArtist));

    constraintLabel = new JLabel(constraintLabelText, SwingConstants.CENTER);
    constraintLabel.setName(UiComponentNames.ADD_SONG_CONSTRAINT_LABEL);
    constraintLabel.setAlignmentX(CENTER_ALIGNMENT);
    constraintLabel.setForeground(ColorTheme.get().textSecondary);
    constraintLabel
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeNavBtn));

    messagePanel.add(Box.createVerticalGlue());
    messagePanel.add(headingLabel);
    messagePanel.add(Box.createVerticalStrut(6));
    messagePanel.add(songNameLabel);
    messagePanel.add(Box.createVerticalStrut(14));
    messagePanel.add(constraintLabel);
    messagePanel.add(Box.createVerticalGlue());

    // ── OK button (bottom) ───────────────────────────────────────────────
    // createCancelButton() renders identically to the Cancel button; the
    // action still calls dismiss() so the overlay is closed on tap.
    JButton okButton = createCancelButton("OK");
    okButton.setName(UiComponentNames.ADD_SONG_OK_BUTTON);

    JPanel okRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    okRow.setOpaque(false);
    okRow.add(okButton);

    JPanel bottomSection = new JPanel(new BorderLayout(0, 8));
    bottomSection.setOpaque(false);
    bottomSection.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
    bottomSection.add(okRow, BorderLayout.CENTER);

    panel.add(iconLabel, BorderLayout.NORTH);
    panel.add(messagePanel, BorderLayout.CENTER);
    panel.add(bottomSection, BorderLayout.SOUTH);

    return panel;
  }

  /**
   * Flips the inner card to the song-constraint view. Safe to call before or after the component is
   * added to the hierarchy.
   * 
   * @param reason
   */
  public void showConstraintPanel(String reason) {

    // Reasons such as "is already in the queue" describe the song itself: "...because it is...".
    String phrased = reason.matches("^(has|is|was) .*") ? "it " + reason : reason;
    this.constraintLabelText = this.constraintLabelText.replace("[REASON]", phrased);
    this.constraintLabel.setText(this.constraintLabelText);
    innerCardLayout.show(innerCardRoot, INNER_CARD_CONSTRAINT);
  }

  private JPanel buildMainPanel() {
    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setBackground(ColorTheme.get().bgOverlayCard);
    main.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

    main.add(buildInfoRow(song, imageLoader), BorderLayout.NORTH);
    main.add(buildDivider(), BorderLayout.CENTER);
    main.add(buildBottomSection(), BorderLayout.SOUTH);

    return main;
  }

  private JPanel buildInfoRow(SongDto song, ImageLoader imageLoader) {
    JPanel row = new JPanel(new BorderLayout(24, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(0, 0, 14, 0));

    JLabel cover = new JLabel();
    cover.setPreferredSize(
        new Dimension(LayoutTheme.get().addSongCoverSize, LayoutTheme.get().addSongCoverSize));
    cover.setHorizontalAlignment(SwingConstants.CENTER);
    cover.setVerticalAlignment(SwingConstants.CENTER);
    cover.setOpaque(true);
    cover.setBackground(ColorTheme.get().bgCoverArtPlaceholder);
    cover.setBorder(BorderFactory.createLineBorder(ColorTheme.get().coverArtBorder, 1));

    if (song.coverArtPath() != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(song.coverArtPath(), 160, 160);
        if (icon != null) {
          cover.setIcon(icon);
        }
      } catch (Exception ignored) {
      }
    }

    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

    JLabel songName = new JLabel(song.songName() != null ? song.songName() : "");
    songName.setForeground(ColorTheme.get().textPrimary);
    songName.setFont(fitTitleFont(songName, songName.getText(), infoTextWidth()));

    JLabel artistName = new JLabel(song.artistName() != null ? song.artistName() : "");
    artistName.setForeground(ColorTheme.get().textPrimary);
    artistName
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongArtist));

    JLabel albumName =
        new JLabel(AlbumGridPanel.albumDisplayName(song.albumName(), song.genreName()));
    albumName.setForeground(ColorTheme.get().textPrimary);
    albumName
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongArtist));

    text.add(Box.createVerticalGlue());
    text.add(songName);
    text.add(Box.createVerticalStrut(8));
    text.add(artistName);
    text.add(Box.createVerticalStrut(4));
    text.add(albumName);
    text.add(Box.createVerticalGlue());

    row.add(cover, BorderLayout.WEST);
    row.add(text, BorderLayout.CENTER);

    return row;
  }

  /**
   * Width available to the info row's text column: the card width minus the outer border panel's
   * 2px inset, the main panel's 28px side padding, the cover art, and the 24px cover/text gap.
   */
  private static int infoTextWidth() {
    LayoutTheme lt = LayoutTheme.get();
    return lt.addSongCardW - 2 * 2 - 2 * 28 - lt.addSongCoverSize - 24;
  }

  /**
   * Returns the largest bold title font, from {@link LayoutTheme#fontSizeAddSongTitle} down to
   * {@link LayoutTheme#fontSizeAddSongTitleMin}, at which {@code text} fits within
   * {@code maxWidth}. Titles still too long at the minimum size fall back to the label's own "..."
   * truncation rather than shrinking further.
   */
  private static Font fitTitleFont(JLabel label, String text, int maxWidth) {
    LayoutTheme lt = LayoutTheme.get();
    for (int size = lt.fontSizeAddSongTitle; size > lt.fontSizeAddSongTitleMin; size--) {
      Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
      if (label.getFontMetrics(font).stringWidth(text) <= maxWidth) {
        return font;
      }
    }
    return new Font(Font.SANS_SERIF, Font.BOLD, lt.fontSizeAddSongTitleMin);
  }

  private JPanel buildDivider() {
    JPanel divider = new JPanel() {
      private static final long serialVersionUID = -3555442413130451178L;

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(ColorTheme.get().dividerLine);
        g.fillRect(0, getHeight() / 2, getWidth(), 1);
      }
    };
    divider.setOpaque(false);
    divider.setPreferredSize(new Dimension(0, 12));
    return divider;
  }

  private JPanel buildBottomSection() {
    JPanel bottom = new JPanel(new BorderLayout(0, 12));
    bottom.setOpaque(false);
    bottom.setBorder(new EmptyBorder(12, 0, 0, 0));

    JPanel buttons = new JPanel(new GridLayout(1, 2, 16, 0));
    buttons.setOpaque(false);

    int priorityLevel = priorityPlayLevel();
    int priorityCost = priorityPlayCost(priorityLevel);

    this.normalButton =
        createQueueButton("Play Song", normalPlayCost, ColorTheme.get().accentBlue, e -> {
          if (creditManager.deductCredits(normalPlayCost)) {

            creditManager.recordSongQueued(song.albumId(), song.songId());
            SwingSecurityUtil.runAsync(() -> songQueueService.addSongToQueue(
                songQueueService.getOwnLocationId(), new AddSongToQueueRequest(
                    SongQueueService.LOCAL_USERNAME, song.albumId(), song.songId(), 1, false)));

            dismiss();
          }
        });

    this.priorityButton =
        createQueueButton("Priority Play Song", priorityCost, ColorTheme.get().accentGold, e -> {
          // Re-checked at the tap: the queue may have changed since the card was opened.
          if (!isPriorityPlayAllowed()) {
            updateButtonStates();
            return;
          }
          if (creditManager.deductCredits(priorityCost)) {

            creditManager.recordPriorityPlayLevel(priorityLevel);
            creditManager.recordSongQueued(song.albumId(), song.songId());
            SwingSecurityUtil.runAsync(() -> songQueueService.addSongToQueue(
                songQueueService.getOwnLocationId(),
                new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME, song.albumId(),
                    song.songId(), priorityLevel, true)));

            dismiss();
          }
        });

    this.normalButton.setName(UiComponentNames.ADD_SONG_PLAY_BUTTON);
    this.priorityButton.setName(UiComponentNames.ADD_SONG_PRIORITY_PLAY_BUTTON);
    buttons.add(this.normalButton);
    buttons.add(this.priorityButton);

    this.creditListener = this::updateButtonStates;
    this.creditManager.addListener(creditListener);

    updateButtonStates();

    JButton cancel = createCancelButton("Cancel");
    cancel.setName(UiComponentNames.ADD_SONG_CANCEL_BUTTON);
    JPanel cancelRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    cancelRow.setOpaque(false);
    cancelRow.add(cancel);

    bottom.add(buttons, BorderLayout.NORTH);
    bottom.add(cancelRow, BorderLayout.CENTER);

    return bottom;
  }

  private void dismiss() {
    timeoutTimer.stop();
    if (onDismiss != null) {
      SwingUtilities.invokeLater(onDismiss);
    }
  }

  /**
   * The level a priority play queues at (and is priced from): once the current user has made a
   * priority play this session, every later one reuses that level so it costs the same and lines up
   * behind the earlier ones; otherwise it is the queue's next available level.
   */
  private int priorityPlayLevel() {
    Integer sessionLevel = creditManager.getSessionPriorityLevel();
    if (sessionLevel != null) {
      return sessionLevel;
    }
    return songQueueService.getHighestPriority(songQueueService.getOwnLocationId());
  }

  /**
   * A priority play is priced by the level it jumps, not the level it queues at: it queues one
   * above the song it jumps (level), so it costs {@code (level - 1) * priorityCostMultiplier} --
   * e.g. 2 credits to jump a normal play (queued at 2), 4 to jump a priority-2 play (queued at 3).
   * Mirrored by {@code CreditCostCalculator.webQueueAddCost} and app.js's {@code queueAddCost}.
   */
  private int priorityPlayCost(int level) {
    return Math.max(1, level - 1) * priorityCostMultiplier;
  }

  /**
   * See {@link SongQueueService#isPriorityPlayAllowed}. Every jukebox song is queued as
   * {@code LOCAL}, so the current user's own songs are the {@code LOCAL} ones queued during this
   * credit session; earlier jukebox users' and web users' songs count as someone else's.
   */
  private boolean isPriorityPlayAllowed() {
    return SongQueueService.isPriorityPlayAllowed(
        songQueueService.getQueuedSongs(songQueueService.getOwnLocationId()),
        entry -> SongQueueService.LOCAL_USERNAME.equals(entry.username())
            && creditManager.isSongQueuedThisSession(entry.song().albumId(),
                entry.song().songId()));
  }

  private void updateButtonStates() {

    int currentCredits = creditManager.getCredits();

    int priorityCost = priorityPlayCost(priorityPlayLevel());
    boolean priorityPlayAllowed = isPriorityPlayAllowed();

    normalButton.setEnabled(currentCredits >= normalPlayCost);
    priorityButton.setEnabled(priorityPlayAllowed && currentCredits >= priorityCost);
    priorityButton.putClientProperty(UNAVAILABLE_TEXT_PROPERTY,
        priorityPlayAllowed ? null : PRIORITY_PLAY_UNAVAILABLE_TEXT);

    normalButton.repaint();
    priorityButton.repaint();
  }

  private JButton createQueueButton(String actionText, int cost, Color accentColor,
      java.awt.event.ActionListener onClick) {

    JButton button = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = Button3D.createGraphics(g);

        boolean enabled = isEnabled();
        Button3D.Face face = Button3D.paintBody(g2, this, !enabled, accentColor);
        int w = face.width();
        int faceH = face.faceH();

        // Two-line centred label
        // Line 1: action text
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueCancelBtn));
        FontMetrics fm1 = g2.getFontMetrics();
        int totalTextH = fm1.getHeight() + 4 + fm1.getHeight(); // rough two-line block height
        int blockY = (faceH - totalTextH) / 2 + fm1.getAscent();

        g2.setColor(ColorTheme.get().textPrimary);
        Button3D.drawCentered(g2, actionText, w, blockY);

        // Line 2: cost or warning
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeNavBtn));
        FontMetrics fm2 = g2.getFontMetrics();
        int line2Y = blockY + fm1.getHeight() - fm1.getDescent() + 4 + fm2.getAscent();

        if (enabled) {
          // AMI-style short format: "2cr" (or "$0.50" when display-currency-for-cost is enabled)
          g2.setColor(ColorTheme.get().accentGold);
          Button3D.drawCentered(g2, creditManager.formatCredits(cost), w, line2Y);
        } else {
          // Disabled for a reason other than credits (e.g. priority play with only background
          // music queued) -- say so rather than asking for more money.
          Object unavailableText = getClientProperty(UNAVAILABLE_TEXT_PROPERTY);
          String warnText = unavailableText != null ? unavailableText.toString()
              : creditManager.formatShortfall(cost - creditManager.getCredits());
          g2.setColor(ColorTheme.get().btn3dWarnBorder);
          Button3D.drawCentered(g2, warnText, w, line2Y);
        }

        g2.dispose();
      }
    };

    Button3D.configure(button,
        new Dimension(LayoutTheme.get().addSongQueueBtnW, LayoutTheme.get().addSongQueueBtnH));
    button.addActionListener(onClick);

    return button;
  }

  private JButton createCancelButton(String text) {

    JButton button = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = Button3D.createGraphics(g);
        Color accent = ColorTheme.get().accentBlue;
        Button3D.Face face = Button3D.paintBody(g2, this, false, accent);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueCancelBtn));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(ColorTheme.get().textPrimary);
        Button3D.drawCentered(g2, text, face.width(),
            (face.faceH() - fm.getHeight()) / 2 + fm.getAscent());

        g2.dispose();
      }
    };

    Button3D.configure(button,
        new Dimension(LayoutTheme.get().addSongCancelBtnW, LayoutTheme.get().addSongCancelBtnH));
    button.addActionListener(e -> dismiss());

    return button;
  }

  /** Must be called when this card is permanently discarded so listeners don't leak. */
  public void teardown() {
    timeoutTimer.stop();
    if (creditListener != null) {
      creditManager.removeListener(creditListener);
    }
  }
}
