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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
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

public class AddSongToQueueDialog extends JDialog {

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

  // Jukebox Warning Colors
  private static final Color AM_WARN_BG = new Color(25, 10, 10);
  private static final Color AM_WARN_BORDER = new Color(220, 40, 40);

  // ── Timeout ───────────────────────────────────────────────────────────────
  private static final int TIMEOUT_SECONDS = 120;

  private final ImageLoader imageLoader;
  private final SongDto song;
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

  public AddSongToQueueDialog(Frame owner, SongDto song, ImageLoader imageLoader,
      int priorityCostMultiplier, SongQueueService songQueueService, CreditManager creditManager) {

    super(owner, "Add Song to Queue", true /* modal */);

    this.imageLoader = imageLoader;
    this.song = song;
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
    cover.setPreferredSize(new Dimension(160, 160));
    cover.setHorizontalAlignment(SwingConstants.CENTER);
    cover.setVerticalAlignment(SwingConstants.CENTER);
    cover.setOpaque(true);
    cover.setBackground(new Color(30, 30, 40));
    cover.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 90), 1));

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
    songName.setForeground(TEXT_PRIMARY);
    songName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));

    JLabel artistName = new JLabel(song.getArtistName() != null ? song.getArtistName() : "");
    artistName.setForeground(TEXT_PRIMARY);
    artistName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));

    JLabel albumName = new JLabel(song.getAlbumName() != null ? song.getAlbumName() : "");
    albumName.setForeground(TEXT_PRIMARY);
    albumName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));

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

    int highestPriority = songQueueService.getHighestPriority();
    int priorityCost = highestPriority * priorityCostMultiplier;

    this.normalButton = createQueueButton("Play Song", normalPlayCost, ACCENT_BLUE, e -> {
      if (creditManager.deductCredits(normalPlayCost)) {
        songQueueService
            .addSongToQueue(new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 1));
        dismiss();
      }
    });

    this.priorityButton = createQueueButton("Priority Play Song", priorityCost, ACCENT_GOLD, e -> {
      if (creditManager.deductCredits(priorityCost)) {
        songQueueService.addSongToQueue(
            new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), highestPriority));
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

        boolean enabled = isEnabled();
        int w = getWidth();
        int h = getHeight();

        // 1. Background Fill Logic
        if (enabled) {
          g2.setColor(hovered ? BTN_HOVER : BTN_NORMAL);
        } else {
          g2.setColor(AM_WARN_BG);
        }
        g2.fillRoundRect(0, 0, w, h, 10, 10);

        // 2. Structural Border Paint
        g2.setColor(enabled ? accentColor : AM_WARN_BORDER);
        g2.setStroke(new java.awt.BasicStroke(2.0f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);

        // 3. Dual-Line Typography Rendering Engines
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        int y1 = h / 2 - 2;

        if (enabled) {
          // Label Line
          g2.setColor(TEXT_PRIMARY);
          g2.drawString(actionText, (w - fm.stringWidth(actionText)) / 2, y1);

          // Credit Cost Line
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
          g2.setColor(accentColor);
          FontMetrics fmCost = g2.getFontMetrics();
          String costText = cost + (cost == 1 ? " credit" : " credits");
          g2.drawString(costText, (w - fmCost.stringWidth(costText)) / 2,
              h / 2 + fmCost.getAscent());
        } else {
          // Warning Label Line 1
          g2.setColor(TEXT_PRIMARY);
          g2.drawString(actionText, (w - fm.stringWidth(actionText)) / 2, y1);

          // Hardware Spec Needed Line 2
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
          g2.setColor(AM_WARN_BORDER);
          FontMetrics fmSmall = g2.getFontMetrics();
          int needed = cost - creditManager.getCredits();
          String warningText = "ADD " + needed + " " + (needed == 1 ? "CREDIT" : "CREDITS");
          g2.drawString(warningText, (w - fmSmall.stringWidth(warningText)) / 2,
              h / 2 + fmSmall.getAscent());
        }
        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(200, 80));
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

        int w = getWidth();
        int h = getHeight();

        g2.setColor(hovered ? BTN_HOVER : BTN_NORMAL);
        g2.fillRoundRect(0, 0, w, h, 10, 10);

        g2.setColor(ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(2.0f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(TEXT_PRIMARY);
        g2.drawString(text, (w - fm.stringWidth(text)) / 2,
            (h - fm.getHeight()) / 2 + fm.getAscent() - 2);

        g2.dispose();
      }
    };

    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(200, 56));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> dismiss());

    return button;
  }

  public static void show(Frame owner, SongDto song, ImageLoader imageLoader,
      int priorityCostMultiplier, SongQueueService songQueueService, CreditManager creditManager) {
    AddSongToQueueDialog dialog = new AddSongToQueueDialog(owner, song, imageLoader,
        priorityCostMultiplier, songQueueService, creditManager);
    dialog.setVisible(true);
  }
}
