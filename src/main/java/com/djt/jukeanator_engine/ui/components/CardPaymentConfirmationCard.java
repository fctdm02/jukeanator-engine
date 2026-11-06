package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * Brief "thank you" message shown after a card purchase at the jukebox's card reader finishes
 * sending its credit pulses. It reports the amount charged and the credits added, then dismisses
 * itself after {@link #AUTO_DISMISS_MS}, or sooner when tapped.
 *
 * <p>
 * The card is purely informational: credits are already on the balance by the time it appears.
 */
public class CardPaymentConfirmationCard extends JPanel {

  private static final long serialVersionUID = 1L;

  /** How long the message stays up without a tap. */
  static final int AUTO_DISMISS_MS = 5_000;

  private static final int ARC = 20;

  private final Runnable onDismiss;
  private final Timer dismissTimer;
  private boolean dismissed = false;

  public CardPaymentConfirmationCard(int dollars, int creditsAdded, Runnable onDismiss) {

    this.onDismiss = onDismiss;
    this.dismissTimer = new Timer(AUTO_DISMISS_MS, e -> dismiss());
    this.dismissTimer.setRepeats(false);

    setOpaque(false);
    setLayout(new GridBagLayout());
    add(buildMessagePanel(dollars, creditsAdded));

    // A tap anywhere on the dimmed backdrop dismisses the message too.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        dismiss();
      }
    });
  }

  /** Starts the auto-dismiss countdown; call once the card is on screen. */
  public void onShown() {
    dismissTimer.restart();
  }

  /** Hides the card (once), stopping the countdown. */
  public void dismiss() {
    if (dismissed) {
      return;
    }
    dismissed = true;
    dismissTimer.stop();
    onDismiss.run();
  }

  @Override
  protected void paintComponent(Graphics g) {
    // Dim whatever is underneath so the message stands out.
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setColor(new java.awt.Color(0, 0, 0, 160));
    g2.fillRect(0, 0, getWidth(), getHeight());
    g2.dispose();
    super.paintComponent(g);
  }

  private JPanel buildMessagePanel(int dollars, int creditsAdded) {

    ColorTheme theme = ColorTheme.get();

    JPanel panel = new JPanel() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(theme.bgOverlayCard);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.setColor(theme.accentGreen);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, ARC, ARC);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    panel.setOpaque(false);
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(new EmptyBorder(30, 60, 30, 60));

    JLabel thankYou = centeredLabel("THANK YOU!", 40, theme.accentGreen);

    JLabel charged =
        centeredLabel(String.format("$%d.00 CHARGED TO YOUR CARD", dollars), 26, theme.textPrimary);
    charged.setName(UiComponentNames.CARD_PAYMENT_CHARGED_LABEL);

    JLabel credits = centeredLabel(
        creditsAdded + (creditsAdded == 1 ? " CREDIT ADDED" : " CREDITS ADDED"), 34,
        theme.accentGold);
    credits.setName(UiComponentNames.CARD_PAYMENT_CREDITS_LABEL);

    JLabel hint = centeredLabel("Pick your songs!", 20, theme.textSecondary);

    JButton ok = createOkButton();
    ok.setAlignmentX(Component.CENTER_ALIGNMENT);

    panel.add(thankYou);
    panel.add(Box.createVerticalStrut(16));
    panel.add(charged);
    panel.add(Box.createVerticalStrut(10));
    panel.add(credits);
    panel.add(Box.createVerticalStrut(10));
    panel.add(hint);
    panel.add(Box.createVerticalStrut(24));
    panel.add(ok);

    return panel;
  }

  private static JLabel centeredLabel(String text, int fontSize, java.awt.Color color) {
    JLabel label = new JLabel(text, SwingConstants.CENTER);
    label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
    label.setForeground(color);
    label.setAlignmentX(Component.CENTER_ALIGNMENT);
    return label;
  }

  private JButton createOkButton() {

    JButton button = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = Button3D.createGraphics(g);
        Button3D.Face face = Button3D.paintBody(g2, this, false, ColorTheme.get().accentBlue);

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeQueueCancelBtn));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(ColorTheme.get().textPrimary);
        Button3D.drawCentered(g2, "OK", face.width(),
            (face.faceH() - fm.getHeight()) / 2 + fm.getAscent());

        g2.dispose();
      }
    };

    Button3D.configure(button,
        new Dimension(LayoutTheme.get().addSongCancelBtnW, LayoutTheme.get().addSongCancelBtnH));
    button.setMaximumSize(button.getPreferredSize());
    button.setName(UiComponentNames.CARD_PAYMENT_OK_BUTTON);
    button.addActionListener(e -> dismiss());
    return button;
  }
}
