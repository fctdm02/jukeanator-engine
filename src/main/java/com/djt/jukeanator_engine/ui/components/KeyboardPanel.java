package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Reusable on-screen keyboard panel.
 *
 * <p>
 * Extracted from {@link SearchPanel} so that other UI components (e.g.
 * {@link LoginToAdminPanelCard}) can embed the keyboard without duplicating code.
 *
 * <p>
 * Callers supply a {@link KeyboardListener} that receives each character appended, a backspace
 * event, a clear event, and a space event. The keyboard manages its own ABC / 123 mode toggle
 * internally.
 */
public class KeyboardPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Colours — sourced from ColorTheme.get() ──────────────────────────────

  // ── Layout constants — sourced from LayoutTheme ───────────────────────────
  // Previously declared as public static final int fields here (KEYBOARD_HEIGHT,
  // SCREEN_PADDING_HORIZONTAL). All callers should now use LayoutTheme.get() directly.

  // ── Keyboard mode ─────────────────────────────────────────────────────────
  private enum KeyboardMode {
    ABC, NUMERIC
  }

  private KeyboardMode keyboardMode = KeyboardMode.ABC;

  // ── Callback ──────────────────────────────────────────────────────────────

  /**
   * Listener interface supplied by the parent component.
   *
   * <p>
   * All methods are called on the EDT immediately when the corresponding key is pressed.
   */
  public interface KeyboardListener {
    /** A printable character (letter, digit, symbol) was pressed. */
    void onCharacter(String ch);

    /** The BACKSPACE key was pressed. */
    void onBackspace();

    /** The CLEAR key was pressed. */
    void onClear();

    /** The SPACE key was pressed. */
    void onSpace();
  }

  private final KeyboardListener listener;

  // ── Panel references rebuilt on mode toggle ────────────────────────────────
  /** The outer wrapper returned by {@link #buildKeyboardWrapper()}. */
  private final JPanel outerWrapper;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Creates a self-contained keyboard wrapper panel sized at {@link LayoutTheme#keyboardHeight}
   * pixels tall.
   *
   * @param listener receives key events
   */
  public KeyboardPanel(KeyboardListener listener) {
    this.listener = listener;

    setLayout(new java.awt.BorderLayout());
    setOpaque(false);
    int kh = LayoutTheme.get().keyboardHeight;
    setPreferredSize(new Dimension(100, kh));
    setMinimumSize(new Dimension(100, kh));
    setMaximumSize(new Dimension(Integer.MAX_VALUE, kh));

    outerWrapper = buildKeyboardWrapper();
    add(outerWrapper, java.awt.BorderLayout.CENTER);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KEYBOARD WRAPPER
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildKeyboardWrapper() {
    JPanel wrapper = new JPanel(new java.awt.BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(new EmptyBorder(0, LayoutTheme.get().keyboardPaddingHorizontal, 0,
        LayoutTheme.get().keyboardPaddingHorizontal));

    JPanel frostedBodyPanel = new JPanel(new java.awt.BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ColorTheme.get().bgFrostedGlassHover);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
      }
    };
    frostedBodyPanel.setOpaque(false);

    JPanel centreShell = new JPanel(new GridBagLayout());
    centreShell.setOpaque(false);
    centreShell.add(buildKeyboardPanel());

    frostedBodyPanel.add(centreShell, java.awt.BorderLayout.CENTER);
    wrapper.add(frostedBodyPanel, java.awt.BorderLayout.CENTER);

    return wrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KEYBOARD LAYOUT
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildKeyboardPanel() {
    JPanel p =
        new JPanel(new GridLayout(3, 1, LayoutTheme.get().keyRowGap, LayoutTheme.get().keyRowGap));
    p.setOpaque(false);
    int pad = LayoutTheme.get().keyboardInnerPad;
    p.setBorder(new EmptyBorder(pad, pad, pad, pad));

    if (keyboardMode == KeyboardMode.ABC) {
      p.add(buildAbcRow1());
      p.add(buildAbcRow2());
      p.add(buildAbcRow3());
    } else {
      p.add(buildNumRow1());
      p.add(buildNumRow2());
      p.add(buildNumRow3());
    }
    return p;
  }

  private JPanel buildAbcRow1() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, LayoutTheme.get().keyColGap, 0));
    row.setOpaque(false);
    for (char c : "QWERTYUIOP".toCharArray())
      row.add(letterKey(String.valueOf(c)));

    JButton clear = styledKey("CLEAR",
        new Dimension(LayoutTheme.get().keyClearW, LayoutTheme.get().keyLetterH));
    clear.addActionListener(e -> listener.onClear());
    row.add(clear);

    row.add(buildBackspaceButton());
    return row;
  }

  private JPanel buildAbcRow2() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, LayoutTheme.get().keyColGap, 0));
    row.setOpaque(false);
    for (char c : "ASDFGHJKL".toCharArray())
      row.add(letterKey(String.valueOf(c)));

    row.add(letterKey("'"));
    row.add(buildModeToggleButton("123@", KeyboardMode.NUMERIC));
    row.add(buildModeToggleButton("ABC", KeyboardMode.ABC));
    return row;
  }

  private JPanel buildAbcRow3() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, LayoutTheme.get().keyColGap, 0));
    row.setOpaque(false);
    for (char c : "ZXCVBNM,.".toCharArray())
      row.add(letterKey(String.valueOf(c)));

    JButton space = styledKey("SPACE",
        new Dimension(LayoutTheme.get().keySpaceW, LayoutTheme.get().keyLetterH));
    space.addActionListener(e -> listener.onSpace());
    row.add(space);
    return row;
  }

  private JPanel buildNumRow1() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, LayoutTheme.get().keyColGap, 0));
    row.setOpaque(false);
    for (String s : new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"})
      row.add(letterKey(s));

    JButton clear = styledKey("CLEAR",
        new Dimension(LayoutTheme.get().keyClearW, LayoutTheme.get().keyLetterH));
    clear.addActionListener(e -> listener.onClear());
    row.add(clear);

    row.add(buildBackspaceButton());
    return row;
  }

  private JPanel buildNumRow2() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, LayoutTheme.get().keyColGap, 0));
    row.setOpaque(false);
    for (String s : new String[] {"!", "@", "#", "$", "%", "^", "&", "*", "\"", "'"})
      row.add(letterKey(s));

    row.add(buildModeToggleButton("123@", KeyboardMode.NUMERIC));
    row.add(buildModeToggleButton("ABC", KeyboardMode.ABC));
    return row;
  }

  private JPanel buildNumRow3() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, LayoutTheme.get().keyColGap, 0));
    row.setOpaque(false);
    for (String s : new String[] {"(", ")", "[", "]", "/", "\\", "?", ":", ";"})
      row.add(letterKey(s));

    JButton space = styledKey("SPACE",
        new Dimension(LayoutTheme.get().keySpaceW, LayoutTheme.get().keyLetterH));
    space.addActionListener(e -> listener.onSpace());
    row.add(space);
    return row;
  }

  // ── Keyboard mode toggle helper ───────────────────────────────────────────

  private JButton buildModeToggleButton(String label, KeyboardMode targetMode) {
    boolean isActiveMode = (targetMode == keyboardMode);

    // Pass the active state flag straight into our unified styledKey generator
    JButton btn = styledKey(label,
        new Dimension(LayoutTheme.get().keyModeToggleW, LayoutTheme.get().keyLetterH),
        isActiveMode);

    if (isActiveMode) {
      btn.setBackground(ColorTheme.get().keyActiveModeBackground);
    }

    btn.addActionListener(e -> {
      if (keyboardMode != targetMode) {
        keyboardMode = targetMode;
        refreshKeyboard();
      }
    });
    return btn;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KEY HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  private JButton buildBackspaceButton() {
    JButton back = styledKey("⌫",
        new Dimension(LayoutTheme.get().keyBackspaceW, LayoutTheme.get().keyLetterH));
    back.addActionListener(e -> listener.onBackspace());
    return back;
  }

  private void refreshKeyboard() {
    JPanel frostedBody = (JPanel) outerWrapper.getComponent(0);
    frostedBody.removeAll();

    JPanel centreShell = new JPanel(new GridBagLayout());
    centreShell.setOpaque(false);
    centreShell.add(buildKeyboardPanel());
    frostedBody.add(centreShell, java.awt.BorderLayout.CENTER);

    frostedBody.repaint();
    frostedBody.revalidate();
  }

  private JButton letterKey(String text) {
    JButton btn =
        styledKey(text, new Dimension(LayoutTheme.get().keyLetterW, LayoutTheme.get().keyLetterH));
    btn.addActionListener(e -> listener.onCharacter(text));
    return btn;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STYLED KEY (AMI 3D)
  // ─────────────────────────────────────────────────────────────────────────

  private JButton styledKey(String text, Dimension size) {
    return styledKey(text, size, false);
  }

  private JButton styledKey(String text, Dimension size, boolean drawHighlight) {
    JButton btn = new JButton(text) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 7;

        // Reserve bottom pixels for the shadow; the visible key sits in [0, visH)
        int shadowH = 4;
        int visH = h - shadowH;

        // ── Drop-shadow slab ──────────────────────────────────────────────
        g2.setColor(ColorTheme.get().keyShadow);
        g2.fillRoundRect(1, shadowH, w - 2, visH, arc, arc);

        // ── Shelf band (bottom ~25 % of visible key) ──────────────────────
        int shelfH = Math.round(visH * 0.25f);
        int faceH = visH - shelfH;

        g2.setColor(ColorTheme.get().keyShelf);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // ── Face gradient ─────────────────────────────────────────────────
        boolean pressed = getModel().isArmed();
        Color fTop = pressed ? ColorTheme.get().keyFaceBottom : ColorTheme.get().keyFaceTop;
        Color fMid = ColorTheme.get().keyFaceMid;
        Color fBot = pressed ? ColorTheme.get().keyFaceTop : ColorTheme.get().keyFaceBottom;
        g2.setPaint(new LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.55f, 1f},
            new Color[] {fTop, fMid, fBot}));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // ── Specular top-edge highlight ────────────────────────────────────
        g2.setColor(ColorTheme.get().keyHighlight);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // ── Side-edge sheens ──────────────────────────────────────────────
        g2.setColor(ColorTheme.get().keySide);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 2, 1, faceH - 2);
        g2.drawLine(w - 2, 2, w - 2, faceH - 2);

        // ── Label — vertically centred in faceH ───────────────────────────
        g2.setFont(getFont());
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (faceH - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(pressed ? ColorTheme.get().accentBlue : ColorTheme.get().textPrimary);
        g2.drawString(getText(), tx, ty);

        // ── Active Mode Neon Border Overlay ───────────────────────────────
        if (drawHighlight) {
          g2.setColor(ColorTheme.get().accentBlue);
          g2.setStroke(new java.awt.BasicStroke(2.0f));
          // Draws right around the 3D button bounds seamlessly
          g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
        }

        g2.dispose();
      }

      @Override
      protected void paintBorder(Graphics g) {}
    };

    btn.setPreferredSize(size);
    btn.setFocusPainted(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setOpaque(false);
    btn.setForeground(ColorTheme.get().textPrimary);
    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeKeyLabel));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return btn;
  }
}
