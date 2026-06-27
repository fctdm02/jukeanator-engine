package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
  private final Timer countdownTimer;
  private int secondsRemaining = LayoutTheme.get().overlayTimeoutSeconds;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar =
      new JProgressBar(0, LayoutTheme.get().overlayTimeoutSeconds);

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

    setOpaque(false);
    setLayout(new java.awt.GridBagLayout());
    JPanel sized = buildBorderPanel();
    sized.setPreferredSize(
        new Dimension(LayoutTheme.get().addSongCardW, LayoutTheme.get().addSongCardH));
    add(sized);

    countdownTimer = new Timer(1000, e -> {
      secondsRemaining--;
      updateTimeout();
      if (secondsRemaining <= 0) {
        dismiss();
      }
    });
    countdownTimer.start();
  }

  // Background is painted by overlayRoot in JukeANatorFrame — no paintComponent override needed.

  /** Called whenever this card is shown — restarts the countdown and resets focus. */
  public void onShown() {
    secondsRemaining = LayoutTheme.get().overlayTimeoutSeconds;
    updateTimeout();
    if (!countdownTimer.isRunning()) {
      countdownTimer.start();
    }
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

    JLabel songNameLabel = new JLabel(song.getSongName() != null
        ? "\u201C" + song.getSongName() + "\u201D by " + song.getArtistName() + ""
        : "", SwingConstants.CENTER);
    songNameLabel.setAlignmentX(CENTER_ALIGNMENT);
    songNameLabel.setForeground(ColorTheme.get().accentGold);
    songNameLabel
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongArtist));

    constraintLabel = new JLabel(constraintLabelText, SwingConstants.CENTER);
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

    JPanel okRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    okRow.setOpaque(false);
    okRow.add(okButton);

    // ── Timeout row ───────────────────────────────────────────────────────
    // Re-use the shared timeout widgets so the countdown runs on the
    // constraint panel exactly as it does on the main panel.
    JComponent timeoutRow = buildTimeoutRow();

    JPanel bottomSection = new JPanel(new BorderLayout(0, 8));
    bottomSection.setOpaque(false);
    bottomSection.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
    bottomSection.add(okRow, BorderLayout.CENTER);
    bottomSection.add(timeoutRow, BorderLayout.SOUTH);

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

    this.constraintLabelText = this.constraintLabelText.replace("[REASON]", reason);
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

    if (song.getCoverArtPath() != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(song.getCoverArtPath(), 160, 160);
        if (icon != null) {
          cover.setIcon(icon);
        }
      } catch (Exception ignored) {
      }
    }

    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

    JLabel songName = new JLabel(song.getSongName() != null ? song.getSongName() : "");
    songName.setForeground(ColorTheme.get().textPrimary);
    songName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongTitle));

    JLabel artistName = new JLabel(song.getArtistName() != null ? song.getArtistName() : "");
    artistName.setForeground(ColorTheme.get().textPrimary);
    artistName
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAddSongArtist));

    JLabel albumName =
        new JLabel(AlbumGridPanel.albumDisplayName(song.getAlbumName(), song.getGenreName()));
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

    int highestPriority = songQueueService.getHighestPriority();
    int priorityCost = highestPriority * priorityCostMultiplier;

    this.normalButton =
        createQueueButton("Play Song", normalPlayCost, ColorTheme.get().accentBlue, e -> {
          if (creditManager.deductCredits(normalPlayCost)) {

            SwingSecurityUtil
                .runAsync(() -> songQueueService.addSongToQueue(new AddSongToQueueRequest(
                    SongQueueService.LOCAL_USERNAME, song.getAlbumId(), song.getSongId(), 1)));

            dismiss();
          }
        });

    this.priorityButton =
        createQueueButton("Priority Play Song", priorityCost, ColorTheme.get().accentGold, e -> {
          if (creditManager.deductCredits(priorityCost)) {

            SwingSecurityUtil.runAsync(() -> songQueueService
                .addSongToQueue(new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME,
                    song.getAlbumId(), song.getSongId(), highestPriority)));

            dismiss();
          }
        });

    buttons.add(this.normalButton);
    buttons.add(this.priorityButton);

    this.creditListener = this::updateButtonStates;
    this.creditManager.addListener(creditListener);

    updateButtonStates();

    JButton cancel = createCancelButton("Cancel");
    JPanel cancelRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    cancelRow.setOpaque(false);
    cancelRow.add(cancel);

    JComponent timeoutRow = buildTimeoutRow();

    bottom.add(buttons, BorderLayout.NORTH);
    bottom.add(cancelRow, BorderLayout.CENTER);
    bottom.add(timeoutRow, BorderLayout.SOUTH);

    return bottom;
  }

  private JComponent buildTimeoutRow() {
    // Identical layout preservation from original source
    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(6, 0, 0, 0));

    timeoutLabel.setForeground(ColorTheme.get().textSecondary);
    timeoutLabel
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTimeoutLabel));
    timeoutLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    updateTimeout();

    timeoutBar.setValue(LayoutTheme.get().overlayTimeoutSeconds);
    timeoutBar.setForeground(ColorTheme.get().accentBlue);
    timeoutBar.setBackground(ColorTheme.get().timeoutBarTrack);
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

  private void dismiss() {
    countdownTimer.stop();
    if (onDismiss != null) {
      SwingUtilities.invokeLater(onDismiss);
    }
  }

  private void updateButtonStates() {

    int currentCredits = creditManager.getCredits();

    int highestPriority = songQueueService.getHighestPriority();
    int priorityCost = highestPriority * priorityCostMultiplier;

    normalButton.setEnabled(currentCredits >= normalPlayCost);
    priorityButton.setEnabled(currentCredits >= priorityCost);

    normalButton.repaint();
    priorityButton.repaint();
  }

  private JButton createQueueButton(String actionText, int cost, Color accentColor,
      java.awt.event.ActionListener onClick) {

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

        boolean enabled = isEnabled();
        boolean warn = !enabled;
        int w = getWidth();
        int h = getHeight();
        int arc = 12;

        // Reserve bottom pixels for the shadow slab; the visible button lives
        // within [0, visH) so it looks like it floats above the surface.
        int shadowH = 5;
        int visH = h - shadowH;

        // ── 1. Drop-shadow slab ────────────────────────────────────────────
        g2.setColor(ColorTheme.get().btn3dShadow);
        g2.fillRoundRect(2, shadowH, w - 4, visH, arc, arc);

        // ── 2. Shelf band (bottom ~22 % of visible face) ──────────────────
        int shelfH = Math.round(visH * 0.22f);
        int faceH = visH - shelfH;

        Color shelfColor = warn ? ColorTheme.get().btn3dWarnShelf : ColorTheme.get().btn3dShelf;
        g2.setColor(shelfColor);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // ── 3. Face gradient ──────────────────────────────────────────────
        Color fTop, fMid, fBot;
        if (warn) {
          fTop = ColorTheme.get().btn3dWarnTop;
          fMid = ColorTheme.get().btn3dWarnMid;
          fBot = ColorTheme.get().btn3dWarnBottom;
        } else if (hovered) {
          // Brighten face slightly on hover
          fTop = ColorTheme.get().btn3dHoverTop;
          fMid = ColorTheme.get().btn3dHoverMid;
          fBot = ColorTheme.get().btn3dHoverBottom;
        } else {
          fTop = ColorTheme.get().btn3dFaceTop;
          fMid = ColorTheme.get().btn3dFaceMid;
          fBot = ColorTheme.get().btn3dFaceBottom;
        }
        g2.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.5f, 1f},
            new Color[] {fTop, fMid, fBot}));
        // Extend slightly into the shelf zone so the join is seamless
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // ── 4. Specular top-edge highlight ────────────────────────────────
        g2.setColor(warn ? ColorTheme.get().btn3dWarnSpecular : ColorTheme.get().btn3dHighlight);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // ── 5. Side-edge sheen (subtle vertical lines) ────────────────────
        g2.setColor(warn ? ColorTheme.get().btn3dWarnSide : ColorTheme.get().btn3dSide);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 3, 1, faceH - 3);
        g2.drawLine(w - 2, 3, w - 2, faceH - 3);

        // ── 6. Glowing border ─────────────────────────────────────────────
        Color borderColor = warn ? ColorTheme.get().btn3dWarnBorder
            : (hovered ? accentColor.brighter() : accentColor);
        g2.setColor(borderColor);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        // ── 7. Two-line centred label ─────────────────────────────────────
        // Line 1: action text
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueCancelBtn));
        FontMetrics fm1 = g2.getFontMetrics();
        int totalTextH = fm1.getHeight() + 4 + fm1.getHeight(); // rough two-line block height
        int blockY = (faceH - totalTextH) / 2 + fm1.getAscent();

        g2.setColor(ColorTheme.get().textPrimary);
        g2.drawString(actionText, (w - fm1.stringWidth(actionText)) / 2, blockY);

        // Line 2: cost or warning
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeNavBtn));
        FontMetrics fm2 = g2.getFontMetrics();
        int line2Y = blockY + fm1.getHeight() - fm1.getDescent() + 4 + fm2.getAscent();

        if (enabled) {
          // AMI-style short format: "2cr"
          String costText = cost + "cr";
          g2.setColor(ColorTheme.get().accentGold);
          g2.drawString(costText, (w - fm2.stringWidth(costText)) / 2, line2Y);
        } else {
          int needed = cost - creditManager.getCredits();
          String warnText = "ADD " + needed + (needed == 1 ? " CREDIT" : " CREDITS");
          g2.setColor(ColorTheme.get().btn3dWarnBorder);
          g2.drawString(warnText, (w - fm2.stringWidth(warnText)) / 2, line2Y);
        }

        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(
        new Dimension(LayoutTheme.get().addSongQueueBtnW, LayoutTheme.get().addSongQueueBtnH));
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

        int w = getWidth();
        int h = getHeight();
        int arc = 12;
        int shadowH = 4;
        int visH = h - shadowH;
        int shelfH = Math.round(visH * 0.22f);
        int faceH = visH - shelfH;

        // Drop-shadow
        g2.setColor(ColorTheme.get().btn3dShadow);
        g2.fillRoundRect(2, shadowH, w - 4, visH, arc, arc);

        // Shelf
        g2.setColor(ColorTheme.get().btn3dShelf);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // Face gradient
        Color fTop = hovered ? ColorTheme.get().btn3dHoverTop : ColorTheme.get().btn3dFaceTop;
        Color fMid = hovered ? ColorTheme.get().btn3dHoverMid : ColorTheme.get().btn3dFaceMid;
        Color fBot = hovered ? ColorTheme.get().btn3dHoverBottom : ColorTheme.get().btn3dFaceBottom;
        g2.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.5f, 1f},
            new Color[] {fTop, fMid, fBot}));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // Specular top-edge highlight
        g2.setColor(ColorTheme.get().btn3dHighlight);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // Side sheen
        g2.setColor(ColorTheme.get().btn3dSide);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 3, 1, faceH - 3);
        g2.drawLine(w - 2, 3, w - 2, faceH - 3);

        // Border
        g2.setColor(hovered ? ColorTheme.get().accentBlue.brighter() : ColorTheme.get().accentBlue);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueCancelBtn));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(ColorTheme.get().textPrimary);
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (faceH - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, tx, ty);

        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(
        new Dimension(LayoutTheme.get().addSongCancelBtnW, LayoutTheme.get().addSongCancelBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> dismiss());

    return button;
  }

  /** Must be called when this card is permanently discarded so listeners don't leak. */
  public void teardown() {
    countdownTimer.stop();
    if (creditListener != null) {
      creditManager.removeListener(creditListener);
    }
  }
}
