package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class AddAlbumToQueueDialog extends JDialog {

  private static final long serialVersionUID = 1L;

  // ── Colours (match JukeANatorFrame palette) ──────────────────────────────
  private static final Color BG_DARK = new Color(10, 10, 10);
  private static final Color BG_PANEL = new Color(22, 22, 28);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_GOLD = new Color(255, 200, 0);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  // Jukebox Warning Colors
  private static final Color AM_WARN_BORDER = new Color(220, 40, 40);

  // ── 3D button palette ─────────────────────────────────────────────────
  // Face gradient: deep blue-slate body matching the AMI dark-teal look
  private static final Color BTN3D_FACE_TOP = new Color(28, 45, 72);
  private static final Color BTN3D_FACE_MID = new Color(18, 32, 54);
  private static final Color BTN3D_FACE_BOTTOM = new Color(10, 18, 34);
  // Bottom "shelf" band — very dark, sells the physical depth illusion
  private static final Color BTN3D_SHELF = new Color(6, 10, 20);
  // Drop-shadow layer rendered one pixel below the whole button
  private static final Color BTN3D_SHADOW = new Color(2, 4, 10);
  // Specular top-edge highlight and side sheen
  private static final Color BTN3D_HIGHLIGHT = new Color(80, 140, 210, 200);
  private static final Color BTN3D_SIDE = new Color(40, 80, 130, 90);
  // Warning state face gradient (dark red)
  private static final Color BTN3D_WARN_TOP = new Color(55, 10, 10);
  private static final Color BTN3D_WARN_MID = new Color(38, 6, 6);
  private static final Color BTN3D_WARN_BOTTOM = new Color(22, 3, 3);
  private static final Color BTN3D_WARN_SHELF = new Color(12, 2, 2);

  // ── Timeout ───────────────────────────────────────────────────────────────
  private static final int TIMEOUT_SECONDS = 120;

  private final ImageLoader imageLoader;
  private final AlbumDto album;
  private final int priorityCostMultiplier;
  private final SongQueueService songQueueService;

  private final CreditManager creditManager;
  private JButton normalButton;
  private JButton priorityButton;
  private Runnable creditListener;

  private final Timer countdownTimer;
  private int secondsRemaining = TIMEOUT_SECONDS;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

  public AddAlbumToQueueDialog(Frame owner, AlbumDto album, ImageLoader imageLoader,
      int priorityCostMultiplier, SongQueueService songQueueService, CreditManager creditManager,
      char incrementCreditsKey) {

    super(owner, "Add Album to Queue", true /* modal */);

    this.imageLoader = imageLoader;
    this.album = album;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.songQueueService = songQueueService;
    this.creditManager = creditManager;

    setUndecorated(true);
    setBackground(BG_DARK);
    setSize(900, 420);
    setLocationRelativeTo(owner);
    setResizable(false);

    // Close on Escape / window-close
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        dismiss();
      }
    });

    getContentPane().setBackground(BG_DARK);
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(buildBorderPanel());

    countdownTimer = new Timer(1000, e -> {
      secondsRemaining--;
      updateTimeout();
      if (secondsRemaining <= 0) {
        dismiss();
      }
    });
    countdownTimer.start();

    // Hardware Bill Acceptor Key Bindings
    this.setFocusable(true);
    this.addKeyListener(new java.awt.event.KeyAdapter() {
      @Override
      public void keyTyped(java.awt.event.KeyEvent e) {
        if (e.getKeyChar() == incrementCreditsKey) {
          creditManager.addDollar();
        }
      }
    });

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
        g2.setColor(ACCENT_BLUE);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
        g2.dispose();
      }
    };
    border.setBackground(BG_DARK);
    border.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    border.add(buildMainPanel());
    return border;
  }

  private JPanel buildMainPanel() {
    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setBackground(BG_PANEL);
    main.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

    main.add(buildInfoRow(album, imageLoader), BorderLayout.NORTH);
    main.add(buildDivider(), BorderLayout.CENTER);
    main.add(buildBottomSection(), BorderLayout.SOUTH);

    return main;
  }

  private JPanel buildInfoRow(AlbumDto album, ImageLoader imageLoader) {
    JPanel row = new JPanel(new BorderLayout(24, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(0, 0, 14, 0));

    JLabel cover = new JLabel();
    cover.setPreferredSize(new Dimension(160, 160));
    cover.setHorizontalAlignment(SwingConstants.CENTER);
    cover.setVerticalAlignment(SwingConstants.CENTER);
    cover.setOpaque(true);
    cover.setBackground(new Color(30, 30, 40));
    cover.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 90), 1));

    if (album.getCoverArtPath() != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(album.getCoverArtPath(), 160, 160);
        if (icon != null) {
          cover.setIcon(icon);
        }
      } catch (Exception ignored) {
      }
    }

    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

    JLabel albumName =
        new JLabel(AlbumGridPanel.albumDisplayName(album.getAlbumName(), album.getGenreName()));
    albumName.setForeground(TEXT_PRIMARY);
    albumName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));

    JLabel artistName = new JLabel(album.getArtistName() != null ? album.getArtistName() : "");
    artistName.setForeground(TEXT_PRIMARY);
    artistName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));

    text.add(Box.createVerticalGlue());
    text.add(albumName);
    text.add(Box.createVerticalStrut(8));
    text.add(artistName);
    text.add(Box.createVerticalStrut(4));

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
        g.setColor(new Color(80, 80, 100));
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

    int numSongs = album.getSongs().size();
    int normalPlayCost = numSongs;
    int highestPriority = songQueueService.getHighestPriority();
    int priorityCost = numSongs * (highestPriority * priorityCostMultiplier);

    this.normalButton = createQueueButton("Play Album", normalPlayCost, ACCENT_BLUE, e -> {
      if (creditManager.deductCredits(normalPlayCost)) {

        CompletableFuture.runAsync(() -> songQueueService
            .addAlbumToQueue(new AddAlbumToQueueRequest(album.getAlbumId(), 1)));

        dismiss();
      }
    });

    this.priorityButton = createQueueButton("Priority Album Play", priorityCost, ACCENT_GOLD, e -> {
      if (creditManager.deductCredits(priorityCost)) {

        CompletableFuture.runAsync(() -> songQueueService
            .addAlbumToQueue(new AddAlbumToQueueRequest(album.getAlbumId(), highestPriority)));

        dismiss();
      }
    });

    buttons.add(this.normalButton);
    buttons.add(this.priorityButton);

    this.creditListener = this::updateButtonStates;
    this.creditManager.addListener(creditListener);

    updateButtonStates();

    this.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent e) {
        creditManager.removeListener(creditListener);
      }
    });

    JButton cancel = createCancelButton("CANCEL");
    JPanel cancelRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    cancelRow.setOpaque(false);
    cancelRow.add(cancel);

    JPanel timeoutRow = buildTimeoutRow();

    bottom.add(buttons, BorderLayout.NORTH);
    bottom.add(cancelRow, BorderLayout.CENTER);
    bottom.add(timeoutRow, BorderLayout.SOUTH);

    return bottom;
  }

  private JPanel buildTimeoutRow() {
    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(6, 0, 0, 0));

    timeoutLabel.setForeground(TEXT_SECONDARY);
    timeoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    timeoutLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    updateTimeout();

    timeoutBar.setValue(TIMEOUT_SECONDS);
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

  private void dismiss() {
    countdownTimer.stop();
    SwingUtilities.invokeLater(this::dispose);
  }

  private void updateButtonStates() {

    int currentCredits = creditManager.getCredits();

    int numSongs = album.getSongs().size();
    int normalPlayCost = numSongs;
    int highestPriority = songQueueService.getHighestPriority();
    int priorityCost = numSongs * (highestPriority * priorityCostMultiplier);

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
        g2.setColor(BTN3D_SHADOW);
        g2.fillRoundRect(2, shadowH, w - 4, visH, arc, arc);

        // ── 2. Shelf band (bottom ~22 % of visible face) ──────────────────
        int shelfH = Math.round(visH * 0.22f);
        int faceH = visH - shelfH;

        Color shelfColor = warn ? BTN3D_WARN_SHELF : BTN3D_SHELF;
        g2.setColor(shelfColor);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // ── 3. Face gradient ──────────────────────────────────────────────
        Color fTop, fMid, fBot;
        if (warn) {
          fTop = BTN3D_WARN_TOP;
          fMid = BTN3D_WARN_MID;
          fBot = BTN3D_WARN_BOTTOM;
        } else if (hovered) {
          // Brighten face slightly on hover
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
        // Extend slightly into the shelf zone so the join is seamless
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // ── 4. Specular top-edge highlight ────────────────────────────────
        g2.setColor(warn ? new Color(200, 60, 60, 160) : BTN3D_HIGHLIGHT);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // ── 5. Side-edge sheen (subtle vertical lines) ────────────────────
        g2.setColor(warn ? new Color(160, 30, 30, 70) : BTN3D_SIDE);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 3, 1, faceH - 3);
        g2.drawLine(w - 2, 3, w - 2, faceH - 3);

        // ── 6. Glowing border ─────────────────────────────────────────────
        Color borderColor =
            warn ? AM_WARN_BORDER : (hovered ? accentColor.brighter() : accentColor);
        g2.setColor(borderColor);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        // ── 7. Two-line centred label ─────────────────────────────────────
        // Line 1: action text — white, bold 20 pt
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics fm1 = g2.getFontMetrics();
        int totalTextH = fm1.getHeight() + 4 + fm1.getHeight(); // rough two-line block height
        int blockY = (faceH - totalTextH) / 2 + fm1.getAscent();

        g2.setColor(TEXT_PRIMARY);
        g2.drawString(actionText, (w - fm1.stringWidth(actionText)) / 2, blockY);

        // Line 2: cost or warning — gold / red, bold 18 pt
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        FontMetrics fm2 = g2.getFontMetrics();
        int line2Y = blockY + fm1.getHeight() - fm1.getDescent() + 4 + fm2.getAscent();

        if (enabled) {
          // AMI-style short format: "2cr"
          String costText = cost + "cr";
          g2.setColor(ACCENT_GOLD);
          g2.drawString(costText, (w - fm2.stringWidth(costText)) / 2, line2Y);
        } else {
          int needed = cost - creditManager.getCredits();
          String warnText = "ADD " + needed + (needed == 1 ? " CREDIT" : " CREDITS");
          g2.setColor(AM_WARN_BORDER);
          g2.drawString(warnText, (w - fm2.stringWidth(warnText)) / 2, line2Y);
        }

        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(200, 88));
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
        g2.setColor(BTN3D_SHADOW);
        g2.fillRoundRect(2, shadowH, w - 4, visH, arc, arc);

        // Shelf
        g2.setColor(BTN3D_SHELF);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // Face gradient
        Color fTop = hovered ? new Color(40, 65, 105) : BTN3D_FACE_TOP;
        Color fMid = hovered ? new Color(28, 50, 84) : BTN3D_FACE_MID;
        Color fBot = hovered ? new Color(16, 30, 56) : BTN3D_FACE_BOTTOM;
        g2.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.5f, 1f},
            new Color[] {fTop, fMid, fBot}));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // Specular top-edge highlight
        g2.setColor(BTN3D_HIGHLIGHT);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // Side sheen
        g2.setColor(BTN3D_SIDE);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 3, 1, faceH - 3);
        g2.drawLine(w - 2, 3, w - 2, faceH - 3);

        // Border
        g2.setColor(hovered ? ACCENT_BLUE.brighter() : ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(1, 1, w - 3, visH - 2, arc, arc);

        // Label — vertically centred in faceH
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(TEXT_PRIMARY);
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
    button.setPreferredSize(new Dimension(200, 62));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> dismiss());

    return button;
  }


  public static void show(Frame owner, AlbumDto album, ImageLoader imageLoader,
      int priorityCostMultiplier, SongQueueService songQueueService, CreditManager creditManager,
      char incrementCreditsKey) {
    AddAlbumToQueueDialog dialog = new AddAlbumToQueueDialog(owner, album, imageLoader,
        priorityCostMultiplier, songQueueService, creditManager, incrementCreditsKey);
    dialog.setVisible(true);
  }
}
