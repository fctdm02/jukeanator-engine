package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
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
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * Modal dialog that wraps {@link AlbumViewPanel} and adds:
 * <ul>
 *   <li>A CLOSE button in the footer.</li>
 *   <li>A 2-minute auto-dismiss countdown bar.</li>
 *   <li>Wires song-row clicks directly to {@link AddSongToQueueDialog}.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   AlbumDetailDialog.show(
 *       parentFrame, album, imageLoader, songQueueService,
 *       normalPlayCost, priorityCost,
 *       threshold1, threshold2, threshold3);
 * </pre>
 */
public class AlbumDetailDialog extends JDialog {

  private static final long serialVersionUID = 1L;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_DARK       = new Color(10,  10,  10);
  private static final Color BG_FOOTER     = new Color(18,  18,  26);
  private static final Color ACCENT_BLUE   = new Color(0,  210, 255);
  private static final Color TEXT_SECONDARY= new Color(180, 180, 180);

  // ── Timeout ───────────────────────────────────────────────────────────────
  private static final int TIMEOUT_SECONDS = 120;

  private int            secondsRemaining = TIMEOUT_SECONDS;
  private final Timer    countdownTimer;
  private final JLabel   timeoutLabel  = new JLabel();
  private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AlbumDetailDialog(
      Frame           owner,
      AlbumDto        album,
      ImageLoader     imageLoader,
      SongQueueService songQueueService,
      int             normalPlayCost,
      int             priorityCost,
      int             threshold1,
      int             threshold2,
      int             threshold3,
      boolean         enableBigScrollBars) {

    super(owner, album.getAlbumName(), true /* modal */);

    setUndecorated(true);
    setBackground(BG_DARK);
    setSize(960, 640);
    setLocationRelativeTo(owner);
    setResizable(false);

    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) { dismiss(); }
    });

    // ── Song-click listener: resets timeout then opens AddSongToQueueDialog
    AlbumViewPanel.SongClickListener songClick = song -> {

      // Reset the album dialog timeout when user starts interacting with a song
      secondsRemaining = TIMEOUT_SECONDS;
      updateTimeout();

      AddSongToQueueDialog.show(
          owner,
          song,
          imageLoader,
          normalPlayCost,
          priorityCost,
          () -> songQueueService.addSongToQueue(
              new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 0)),
          () -> songQueueService.addSongToQueue(
              new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 1)));
    };

    // ── Album-click listener: resets timeout then opens AddAlbumToQueueDialog
    AlbumViewPanel.AlbumClickListener albumClick = clickedAlbum -> {

      secondsRemaining = TIMEOUT_SECONDS;
      updateTimeout();

      AddAlbumToQueueDialog.show(owner, clickedAlbum, imageLoader, normalPlayCost, priorityCost,

          () -> songQueueService
              .addAlbumToQueue(new AddAlbumToQueueRequest(clickedAlbum.getAlbumId(), 0)),

          () -> songQueueService
              .addAlbumToQueue(new AddAlbumToQueueRequest(clickedAlbum.getAlbumId(), 1)));
    };
    
    AlbumViewPanel albumView = new AlbumViewPanel(
        album, imageLoader,
        threshold1, threshold2, threshold3,
        enableBigScrollBars,
        songClick,
        albumClick);

    getContentPane().setBackground(BG_DARK);
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(buildBorderPanel(albumView), BorderLayout.CENTER);

    // ── Countdown ─────────────────────────────────────────────────────────
    countdownTimer = new Timer(1000, e -> {
      secondsRemaining--;
      updateTimeout();
      if (secondsRemaining <= 0) dismiss();
    });
    countdownTimer.start();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LAYOUT
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildBorderPanel(AlbumViewPanel albumView) {

    // Outer accent border
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
    border.setBackground(BG_DARK);
    border.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    JPanel inner = new JPanel(new BorderLayout(0, 0));
    inner.setBackground(BG_DARK);

    inner.add(albumView,       BorderLayout.CENTER);
    inner.add(buildFooter(),   BorderLayout.SOUTH);

    border.add(inner);
    return border;
  }

  private JPanel buildFooter() {

    JPanel footer = new JPanel(new BorderLayout(12, 0));
    footer.setBackground(BG_FOOTER);
    footer.setBorder(new EmptyBorder(10, 20, 10, 20));

    // ── Close button ──────────────────────────────────────────────────────
    JButton close = new JButton("CLOSE");
    close.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    close.setForeground(Color.WHITE);
    close.setBackground(new Color(60, 60, 75));
    close.setFocusPainted(false);
    close.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 120), 1));
    close.setPreferredSize(new Dimension(160, 44));
    close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    close.addActionListener(e -> dismiss());

    close.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override public void mouseEntered(java.awt.event.MouseEvent e) {
        close.setBackground(new Color(90, 90, 110));
      }
      @Override public void mouseExited(java.awt.event.MouseEvent e) {
        close.setBackground(new Color(60, 60, 75));
      }
    });

    // ── Timeout bar + label ───────────────────────────────────────────────
    JPanel timeoutSection = new JPanel(new BorderLayout(8, 0));
    timeoutSection.setOpaque(false);

    timeoutBar.setValue(TIMEOUT_SECONDS);
    timeoutBar.setForeground(ACCENT_BLUE);
    timeoutBar.setBackground(new Color(40, 40, 55));
    timeoutBar.setBorderPainted(false);
    timeoutBar.setStringPainted(false);
    timeoutBar.setPreferredSize(new Dimension(0, 4));

    timeoutLabel.setForeground(TEXT_SECONDARY);
    timeoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    timeoutLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    timeoutLabel.setPreferredSize(new Dimension(110, 20));
    updateTimeout();

    timeoutSection.add(timeoutBar,   BorderLayout.CENTER);
    timeoutSection.add(timeoutLabel, BorderLayout.EAST);

    footer.add(close,          BorderLayout.WEST);
    footer.add(timeoutSection, BorderLayout.CENTER);

    return footer;
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
  // STATIC FACTORY
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Convenience factory — builds, shows, and blocks until dismissed.
   */
  public static void show(
      Frame            owner,
      AlbumDto         album,
      ImageLoader      imageLoader,
      SongQueueService songQueueService,
      int              normalPlayCost,
      int              priorityCost,
      int              threshold1,
      int              threshold2,
      int              threshold3,
      boolean          enableBigScrollBars) {

    AlbumDetailDialog dialog = new AlbumDetailDialog(
        owner, album, imageLoader, songQueueService,
        normalPlayCost, priorityCost,
        threshold1, threshold2, threshold3,
        enableBigScrollBars);

    dialog.setVisible(true); // blocks (modal)
  }
}