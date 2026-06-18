package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Full-screen black overlay displayed during hibernation hours.
 *
 * <p>
 * The window is completely non-interactive: it consumes all mouse/touch input and renders nothing
 * but a dim "HIBERNATION MODE" label that repositions itself every {@value #MOVE_INTERVAL_MS} ms to
 * avoid pixel burn-in.
 *
 * <p>
 * Lifecycle is managed by {@code JukeANatorFrame}, which calls {@link #setVisible(boolean)} based
 * on a periodic time check.
 *
 * <p>
 * <b>Escape-key dismiss:</b> Pressing the physical {@code Esc} key while the window is visible
 * invokes the {@code onDismiss} callback supplied at construction time. This allows a service
 * technician who has opened the jukebox enclosure to bypass hibernation mode and access the
 * AdminPanel without waiting for the scheduled end of the hibernation window. The
 * {@code KeyEventDispatcher} used for this purpose is registered with {@link KeyboardFocusManager}
 * only while the window is visible, so it never interferes with normal application key handling at
 * any other time.
 */
public class HibernationWindow extends JWindow {

  private static final long serialVersionUID = 1L;

  /** How often (ms) the floating label moves to a new random position. */
  private static final int MOVE_INTERVAL_MS = 30_000;

  private final int screenWidth;
  private final int screenHeight;

  /** Panel that holds the label — repositioned on every timer tick. */
  private final JPanel floatingPanel;

  /** Timer that moves the label periodically to prevent burn-in. */
  private final Timer moveTimer;

  /**
   * Intercepts the Esc key system-wide while the window is visible. Stored as a field so the same
   * instance can be cleanly deregistered.
   */
  private final KeyEventDispatcher escDispatcher;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  public HibernationWindow(javax.swing.JFrame owner, int screenWidth, int screenHeight,
      int hibernateEnd, Runnable onDismiss) {

    // Anchoring to the owner JFrame keeps the window on the same graphics
    // device as the fullscreen frame, so setBounds lands correctly.
    super(owner);

    this.screenWidth = screenWidth;
    this.screenHeight = screenHeight;

    // ── Esc-key dispatcher ────────────────────────────────────────────────
    // JWindow does not participate in the JRootPane input-map mechanism that
    // JFrame uses, so a KeyEventDispatcher on the KeyboardFocusManager is the
    // most reliable way to catch a global keystroke regardless of which
    // component (if any) currently holds focus.
    this.escDispatcher = (KeyEvent e) -> {
      if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        onDismiss.run();
        return true; // consume the event — nothing else should act on it
      }
      return false;
    };

    setAlwaysOnTop(true);

    // Cover every pixel of the screen, respecting the device's actual bounds
    // (mirrors the same pattern used in ScreenSaverWindow).
    java.awt.Rectangle screenBounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    setBounds(screenBounds);

    // ── Solid-black, non-opaque background panel ──────────────────────────
    JPanel background = new JPanel(null) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        // Pure black — no gradient, no transparency.
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
      }
    };
    background.setOpaque(true);
    background.setBackground(Color.BLACK);
    setContentPane(background);

    // Block all mouse events so the screen cannot be dismissed by touch or click.
    background.addMouseListener(new java.awt.event.MouseAdapter() {});
    background.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {});

    // ── Floating label panel ──────────────────────────────────────────────
    floatingPanel = new JPanel();
    floatingPanel.setLayout(new BoxLayout(floatingPanel, BoxLayout.Y_AXIS));
    floatingPanel.setOpaque(false);

    // ── Wake-up time string ───────────────────────────────────────────────
    // Convert the 24-hour hibernateEnd integer to a 12-hour AM/PM string.
    // e.g. hibernateEnd=10 → "10:00 AM", hibernateEnd=13 → "1:00 PM"
    String wakeUpTime =
        LocalTime.of(hibernateEnd, 0).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));

    JLabel line1 = makeLabel("HIBERNATION", 28);
    JLabel line2 = makeLabel("MODE", 28);
    JLabel line3 = makeLabel("(wakes up at " + wakeUpTime + ")", 20);

    floatingPanel.add(line1);
    floatingPanel.add(Box.createVerticalStrut(8));
    floatingPanel.add(line2);
    floatingPanel.add(Box.createVerticalStrut(14));
    floatingPanel.add(line3);

    // Size the panel to wrap its contents
    floatingPanel.setSize(floatingPanel.getPreferredSize());

    background.add(floatingPanel);

    // Place it once immediately so it is not stuck at (0, 0) on first show.
    moveFloatingPanel();

    // ── Burn-in prevention timer ──────────────────────────────────────────
    moveTimer = new Timer(MOVE_INTERVAL_MS, e -> moveFloatingPanel());
    moveTimer.setInitialDelay(MOVE_INTERVAL_MS);
    moveTimer.start();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // VISIBILITY — register / deregister the Esc dispatcher
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Registers the Esc {@link KeyEventDispatcher} when becoming visible and deregisters it when
   * being hidden, so the dispatcher is active for exactly as long as the hibernation overlay is on
   * screen.
   */
  @Override
  public void setVisible(boolean visible) {

    KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    if (visible) {
      kfm.addKeyEventDispatcher(escDispatcher);
    } else {
      kfm.removeKeyEventDispatcher(escDispatcher);
    }

    super.setVisible(visible);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PRIVATE HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  /** Creates one line of the floating label with a consistent style. */
  private static JLabel makeLabel(String text, int fontSize) {

    JLabel label = new JLabel(text, SwingConstants.CENTER);
    label.setAlignmentX(Component.CENTER_ALIGNMENT);
    // Light gray — visible but unobtrusive on a black screen.
    label.setForeground(new Color(180, 180, 180));
    label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
    return label;
  }

  /** Repositions the floating panel to a uniformly random location on screen. */
  private void moveFloatingPanel() {

    // Re-measure each time in case the panel was never laid out before the
    // first call (preferred size requires a valid UI delegate).
    floatingPanel.setSize(floatingPanel.getPreferredSize());

    int maxX = Math.max(1, screenWidth - floatingPanel.getWidth());
    int maxY = Math.max(1, screenHeight - floatingPanel.getHeight());

    Random rng = new Random();
    floatingPanel.setLocation(rng.nextInt(maxX), rng.nextInt(maxY));

    // Repaint the background so the old position is erased cleanly.
    floatingPanel.getParent().repaint();
  }
}
