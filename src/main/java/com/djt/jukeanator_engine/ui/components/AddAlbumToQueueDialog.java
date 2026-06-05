package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
  private static final Color BTN_NORMAL = new Color(40, 40, 55);
  private static final Color BTN_HOVER = new Color(0, 140, 180);

  // ── Timeout ───────────────────────────────────────────────────────────────
  private static final int TIMEOUT_SECONDS = 120;

  private final ImageLoader imageLoader;
  private final AlbumDto album;
  private final int priorityCostMultiplier;
  private final SongQueueService songQueueService;

  private final CreditManager creditManager;
  private final int normalPlayCost = 1;
  private JButton normalButton;
  private JButton priorityButton;
  private Runnable creditListener;

  private final Timer countdownTimer;
  private int secondsRemaining = TIMEOUT_SECONDS;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

  public AddAlbumToQueueDialog(Frame owner, AlbumDto album, ImageLoader imageLoader,
      int priorityCostMultiplier, SongQueueService songQueueService, CreditManager creditManager) {

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

    // ── Countdown timer ───────────────────────────────────────────────────
    countdownTimer = new Timer(1000, e -> {
      secondsRemaining--;
      updateTimeout();
      if (secondsRemaining <= 0) {
        dismiss();
      }
    });
    countdownTimer.start();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ROOT PANEL WITH ACCENT BORDER
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildBorderPanel() {

    // Outer glowing border panel
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

  // ─────────────────────────────────────────────────────────────────────────
  // MAIN CONTENT PANEL
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildMainPanel() {

    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setBackground(BG_PANEL);
    main.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

    main.add(buildInfoRow(album, imageLoader), BorderLayout.NORTH);
    main.add(buildDivider(), BorderLayout.CENTER);
    main.add(buildBottomSection(), BorderLayout.SOUTH);

    return main;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // INFO ROW (cover art | song name / artist / album)
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildInfoRow(AlbumDto album, ImageLoader imageLoader) {

    JPanel row = new JPanel(new BorderLayout(24, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(0, 0, 14, 0));

    // ── Cover art ────────────────────────────────────────────────────────
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

    // ── Text block ────────────────────────────────────────────────────────
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

    JLabel albumName = new JLabel(album.getAlbumName() != null ? album.getAlbumName() : "");
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

  // ─────────────────────────────────────────────────────────────────────────
  // DIVIDER
  // ─────────────────────────────────────────────────────────────────────────
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

  // ─────────────────────────────────────────────────────────────────────────
  // BOTTOM SECTION (action buttons + cancel + timeout)
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildBottomSection() {

    JPanel bottom = new JPanel(new BorderLayout(0, 12));
    bottom.setOpaque(false);
    bottom.setBorder(new EmptyBorder(12, 0, 0, 0));

    // ── Action buttons ────────────────────────────────────────────────────
    JPanel buttons = new JPanel(new GridLayout(1, 2, 16, 0));
    buttons.setOpaque(false);

    int numSongs = album.getSongs().size();
    int normalPlayCost = numSongs;
    int highestPriority = songQueueService.getHighestPriority();
    int priorityCost = numSongs * (highestPriority * priorityCostMultiplier);



    this.normalButton = createQueueButton("Play Album", normalPlayCost, e -> {
      songQueueService.addAlbumToQueue(new AddAlbumToQueueRequest(album.getAlbumId(), 1));
    });

    this.priorityButton = createQueueButton("Priority Album Play", priorityCost, e -> {
      songQueueService
          .addAlbumToQueue(new AddAlbumToQueueRequest(album.getAlbumId(), highestPriority));
    });



    buttons.add(buildActionButton("Play Album", normalPlayCost + "cr", ACCENT_BLUE, () -> {
      dismiss();
      songQueueService.addAlbumToQueue(new AddAlbumToQueueRequest(album.getAlbumId(), 1));
    }));

    buttons.add(buildActionButton("Priority Album Play", priorityCost + "cr", ACCENT_GOLD, () -> {
      dismiss();
      songQueueService
          .addAlbumToQueue(new AddAlbumToQueueRequest(album.getAlbumId(), highestPriority));
    }));



    // Add selection buttons to the button panel
    buttons.add(this.normalButton);
    buttons.add(this.priorityButton);

    // Attach reactive listeners
    this.creditListener = this::updateButtonStates;
    this.creditManager.addListener(creditListener);

    // Run initialization configuration tick
    updateButtonStates();

    // IMPORTANT: Clean up memory leaks when window is closed
    this.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent e) {
        creditManager.removeListener(creditListener);
      }
    });

    // ── Cancel button ─────────────────────────────────────────────────────
    JButton cancel = createCancelButton("CANCEL");

    JPanel cancelRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    cancelRow.setOpaque(false);
    cancelRow.add(cancel);

    // ── Timeout bar ───────────────────────────────────────────────────────
    JPanel timeoutRow = buildTimeoutRow();

    bottom.add(buttons, BorderLayout.NORTH);
    bottom.add(cancelRow, BorderLayout.CENTER);
    bottom.add(timeoutRow, BorderLayout.SOUTH);

    return bottom;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ACTION BUTTON (two-line: label on top, cost in accent colour below)
  // ─────────────────────────────────────────────────────────────────────────
  private JButton buildActionButton(String label, String cost, Color accentColor,
      Runnable onClick) {

    // Custom two-line button painted manually
    JButton button = new JButton() {
      private static final long serialVersionUID = 373087729838840160L;

      @Override
      protected void paintComponent(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

        // Border
        g2.setColor(accentColor);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);

        // Label (top line)
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g2.setColor(TEXT_PRIMARY);
        java.awt.FontMetrics fm1 = g2.getFontMetrics();
        int labelX = (getWidth() - fm1.stringWidth(label)) / 2;
        g2.drawString(label, labelX, getHeight() / 2 - 2);

        // Cost (bottom line, accent colour)
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g2.setColor(accentColor);
        java.awt.FontMetrics fm2 = g2.getFontMetrics();
        int costX = (getWidth() - fm2.stringWidth(cost)) / 2;
        g2.drawString(cost, costX, getHeight() / 2 + fm2.getAscent());

        g2.dispose();
      }
    };

    button.setBackground(BTN_NORMAL);
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setPreferredSize(new Dimension(200, 80));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> onClick.run());

    // Hover
    button.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        button.setBackground(BTN_HOVER);
        button.repaint();
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        button.setBackground(BTN_NORMAL);
        button.repaint();
      }
    });

    return button;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // TIMEOUT ROW (label + progress bar)
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildTimeoutRow() {

    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(6, 0, 0, 0));

    timeoutLabel.setForeground(TEXT_SECONDARY);
    timeoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    timeoutLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    updateTimeout(); // set initial text

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

  // ─────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  private void updateTimeout() {

    timeoutBar.setValue(secondsRemaining);
    timeoutLabel.setText("Closes in " + secondsRemaining + "s");
  }

  private void dismiss() {

    countdownTimer.stop();
    SwingUtilities.invokeLater(this::dispose);
  }

  private JButton createCancelButton(String text) {

    // Idle and hover fill colours mirror the sort button's active gradient
    final Color GRAD_TOP = new Color(0, 160, 210);
    final Color GRAD_BOTTOM = new Color(0, 80, 130);
    final Color HOVER_TOP = new Color(0, 190, 240);
    final Color HOVER_BOTTOM = new Color(0, 100, 160);

    JButton button = new JButton(text) {

      private static final long serialVersionUID = 1L;
      private boolean hovered = false;

      {
        addMouseListener(new java.awt.event.MouseAdapter() {
          @Override
          public void mouseEntered(java.awt.event.MouseEvent e) {
            hovered = true;
            repaint();
          }

          @Override
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

        // Gradient fill — brighter on hover
        Color top = hovered ? HOVER_TOP : GRAD_TOP;
        Color bottom = hovered ? HOVER_BOTTOM : GRAD_BOTTOM;
        g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

        // Accent border
        g2.setColor(ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
      }
    };

    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    button.setForeground(Color.WHITE);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(140, 52));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> dismiss());

    return button;
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

  private JButton createQueueButton(String actionText, int cost,
      java.awt.event.ActionListener onSuccess) {

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

        boolean enabled = isEnabled();
        int w = getWidth();
        int h = getHeight();

        if (enabled) {
          // Normal UI Theme Linear Flow
          Color top = hovered ? new Color(0, 240, 255) : new Color(0, 170, 255);
          Color bottom = hovered ? new Color(0, 150, 200) : new Color(0, 100, 150);
          g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
          g2.fillRoundRect(0, 0, w, h, 8, 8);
          g2.setColor(new Color(0, 210, 255)); // ACCENT_BLUE
        } else {
          // Disabled AMI Hardware Interface Spec
          g2.setColor(new Color(25, 10, 10)); // Deep dark red backdrop tint
          g2.fillRoundRect(0, 0, w, h, 8, 8);
          g2.setColor(new Color(220, 40, 40)); // Solid Warning Red Border
        }

        g2.setStroke(new java.awt.BasicStroke(2.0f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);

        // Text Engine Formatting
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        FontMetrics fm = g2.getFontMetrics();

        if (enabled) {
          g2.setColor(Color.WHITE);
          int textX = (w - fm.stringWidth(actionText)) / 2;
          int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
          g2.drawString(actionText, textX, textY);
        } else {
          // Multi-line warning layout text rendering
          g2.setColor(new Color(220, 40, 40));
          String line1 = actionText;
          int needed = cost - creditManager.getCredits();
          String line2 = "ADD " + needed + " " + (needed == 1 ? "CREDIT" : "CREDITS");

          int y1 = (h / 2) - 2;
          int y2 = (h / 2) + fm.getAscent() - 2;

          g2.drawString(line1, (w - fm.stringWidth(line1)) / 2, y1);
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
          FontMetrics fmSmall = g2.getFontMetrics();
          g2.drawString(line2, (w - fmSmall.stringWidth(line2)) / 2, y2);
        }
        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(150, 55));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    button.addActionListener(e -> {
      if (creditManager.deductCredits(cost)) {
        onSuccess.actionPerformed(e);
        dismiss();
      }
    });

    return button;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STATIC FACTORY — convenience method for call sites
  // ─────────────────────────────────────────────────────────────────────────
  public static void show(Frame owner, AlbumDto album, ImageLoader imageLoader,
      int priorityCostMultiplier, SongQueueService songQueueService, CreditManager creditManager) {

    AddAlbumToQueueDialog dialog = new AddAlbumToQueueDialog(owner, album, imageLoader,
        priorityCostMultiplier, songQueueService, creditManager);

    dialog.setVisible(true); // blocks here (modal)
  }
}
