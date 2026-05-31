package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;

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

  private final Timer countdownTimer;
  private int secondsRemaining = TIMEOUT_SECONDS;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

  // ── Callbacks ─────────────────────────────────────────────────────────────
  /** Called when the user confirms normal play. */
  public interface PlayAction {
    void execute();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * @param owner The parent Frame (pass JukeANatorFrame).
   * @param album The album to display.
   * @param imageLoader Shared ImageLoader instance.
   * @param normalPlayCost Credits required for a normal play.
   * @param priorityCost Credits required for a priority play.
   * @param onNormalPlay Action to execute on "Play Song".
   * @param onPriorityPlay Action to execute on "Priority Play".
   */
  public AddAlbumToQueueDialog(Frame owner, AlbumDto album, ImageLoader imageLoader,
      int normalPlayCost, int priorityCost, PlayAction onNormalPlay, PlayAction onPriorityPlay) {

    super(owner, "Add Album to Queue", true /* modal */);

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
    getContentPane().add(buildBorderPanel(album, imageLoader, normalPlayCost, priorityCost,
        onNormalPlay, onPriorityPlay));

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
  private JPanel buildBorderPanel(AlbumDto album, ImageLoader imageLoader, int normalPlayCost,
      int priorityCost, PlayAction onNormalPlay, PlayAction onPriorityPlay) {

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
    border.add(buildMainPanel(album, imageLoader, normalPlayCost, priorityCost, onNormalPlay,
        onPriorityPlay));
    return border;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // MAIN CONTENT PANEL
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildMainPanel(AlbumDto album, ImageLoader imageLoader, int normalPlayCost,
      int priorityCost, PlayAction onNormalPlay, PlayAction onPriorityPlay) {

    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setBackground(BG_PANEL);
    main.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

    main.add(buildInfoRow(album, imageLoader), BorderLayout.NORTH);
    main.add(buildDivider(), BorderLayout.CENTER);
    main.add(buildBottomSection(normalPlayCost, priorityCost, onNormalPlay, onPriorityPlay),
        BorderLayout.SOUTH);

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
  private JPanel buildBottomSection(int normalPlayCost, int priorityCost, PlayAction onNormalPlay,
      PlayAction onPriorityPlay) {

    JPanel bottom = new JPanel(new BorderLayout(0, 12));
    bottom.setOpaque(false);
    bottom.setBorder(new EmptyBorder(12, 0, 0, 0));

    // ── Action buttons ────────────────────────────────────────────────────
    JPanel buttons = new JPanel(new GridLayout(1, 2, 16, 0));
    buttons.setOpaque(false);

    buttons.add(buildActionButton("Play Album", normalPlayCost + "cr", ACCENT_BLUE, () -> {
      dismiss();
      onNormalPlay.execute();
    }));

    buttons.add(buildActionButton("Priority Album Play", priorityCost + "cr", ACCENT_GOLD, () -> {
      dismiss();
      onPriorityPlay.execute();
    }));

    // ── Cancel button ─────────────────────────────────────────────────────
    JButton cancel = new JButton("CANCEL");
    cancel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    cancel.setForeground(TEXT_PRIMARY);
    cancel.setBackground(new Color(60, 60, 75));
    cancel.setFocusPainted(false);
    cancel.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 120), 1));
    cancel.setPreferredSize(new Dimension(200, 48));
    cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    cancel.addActionListener(e -> dismiss());

    // Hover
    cancel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        cancel.setBackground(new Color(90, 90, 110));
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        cancel.setBackground(new Color(60, 60, 75));
      }
    });

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

  // ─────────────────────────────────────────────────────────────────────────
  // STATIC FACTORY — convenience method for call sites
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds and shows the dialog. Blocks until the user dismisses it (modal) or the 2-minute timeout
   * elapses.
   *
   * @param owner Parent frame.
   * @param album Album to display.
   * @param imageLoader Shared loader.
   * @param normalPlayCost Credits for a normal play.
   * @param priorityCost Credits for a priority play.
   * @param onNormalPlay Callback for normal play.
   * @param onPriorityPlay Callback for priority play.
   */
  public static void show(Frame owner, AlbumDto album, ImageLoader imageLoader, int normalPlayCost,
      int priorityCost, PlayAction onNormalPlay, PlayAction onPriorityPlay) {

    AddAlbumToQueueDialog dialog = new AddAlbumToQueueDialog(owner, album, imageLoader, normalPlayCost,
        priorityCost, onNormalPlay, onPriorityPlay);

    dialog.setVisible(true); // blocks here (modal)
  }
}
