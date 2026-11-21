package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.djt.jukeanator_engine.domain.license.model.LicenseAgreement;
import com.djt.jukeanator_engine.domain.license.service.LicenseService;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

/**
 * Full-screen overlay "popup" with the JukeANator End User License Agreement, which the operator
 * must accept before the jukebox is used.
 *
 * <ul>
 * <li>{@link Mode#GENERAL}: first start (or a new agreement version). Accepting records the
 * agreement, and also its Commercial Use section when a commercial tier is licensed. Declining
 * exits the application.</li>
 * <li>{@link Mode#COMMERCIAL}: the agreement was accepted earlier, but a Commercial or Commercial
 * Subscription license is now active. The operator must acknowledge that they, not Digital Jukebox
 * Technologies, LLC, are responsible for public performance licenses. Declining keeps the jukebox
 * playing with payment inputs disabled, and the card appears again at the next start.</li>
 * </ul>
 *
 * <p>It closes only through its own buttons.
 */
public class LicenseAgreementCard extends JPanel {

  private static final long serialVersionUID = 1L;

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(LicenseAgreementCard.class);

  /** Why the agreement is shown. */
  public enum Mode {
    GENERAL, COMMERCIAL
  }

  private final LicenseService licenseService;
  private final Mode mode;
  private final boolean commercialLicensed;
  private final Runnable onAccepted;
  private final Runnable onDeclined;

  private JEditorPane textPane;
  private JLabel messageLabel;
  private JButton acceptButton;
  private boolean saving;

  /**
   * @param licenseService records the acceptance
   * @param mode why the agreement is shown
   * @param onAccepted invoked on the EDT once the acceptance is saved
   * @param onDeclined invoked on the EDT when the operator declines
   */
  public LicenseAgreementCard(LicenseService licenseService, Mode mode, Runnable onAccepted,
      Runnable onDeclined) {
    this.licenseService = licenseService;
    this.mode = mode;
    this.commercialLicensed = mode == Mode.COMMERCIAL
        || licenseService.getStatus().licensedTier().isCommercial();
    this.onAccepted = onAccepted;
    this.onDeclined = onDeclined;

    setOpaque(false);
    setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1;
    c.weighty = 1;
    c.insets = new Insets(30, 60, 30, 60);
    add(buildPanel(), c);
  }

  // ---------------------------------------------------------------- layout

  private JPanel buildPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 12)) {
      private static final long serialVersionUID = 1L;

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
    panel.setBackground(ColorTheme.get().bgOverlayCard);
    panel.setOpaque(true);
    panel.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

    JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
    header.setOpaque(false);
    JLabel title = new JLabel(mode == Mode.COMMERCIAL ? "Commercial License Acknowledgement"
        : "License Agreement", SwingConstants.CENTER);
    title.setForeground(ColorTheme.get().loginTitleColor);
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeLoginTitle));
    header.add(title);
    JLabel info = new JLabel("<html><div style='text-align:center'>" + explanation()
        + "</div></html>", SwingConstants.CENTER);
    info.setForeground(ColorTheme.get().textSecondary);
    info.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeLoginCaption));
    header.add(info);
    panel.add(header, BorderLayout.NORTH);

    textPane = new JEditorPane();
    textPane.setName(UiComponentNames.LICENSE_AGREEMENT_TEXT);
    textPane.setContentType("text/html");
    textPane.setEditable(false);
    textPane.setFocusable(false);
    textPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    textPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,
        LayoutTheme.get().fontSizeLoginCaption));
    textPane.setBackground(java.awt.Color.WHITE);
    textPane.setForeground(java.awt.Color.BLACK);
    textPane.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    String html;
    try {
      html = LicenseAgreement.loadHtml();
    } catch (RuntimeException ex) {
      log.error("Could not load the license agreement", ex);
      html = "<html><body><p>The license agreement could not be loaded. It is available at"
          + " https://www.jukeanator.com/license_agreement.html</p></body></html>";
    }
    textPane.setText(html);
    JScrollPane scroll = new JScrollPane(textPane,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createLineBorder(ColorTheme.get().accentBlue));
    scroll.getVerticalScrollBar().setUnitIncrement(24);
    ScrollBars.apply(scroll, ColorTheme.get().accentBlue);
    scroll.setPreferredSize(new Dimension(LayoutTheme.get().loginPanelW,
        LayoutTheme.get().loginPanelH));
    panel.add(scroll, BorderLayout.CENTER);

    messageLabel = new JLabel(" ", SwingConstants.CENTER);
    messageLabel.setName(UiComponentNames.LICENSE_AGREEMENT_MESSAGE_LABEL);
    messageLabel.setForeground(ColorTheme.get().loginError);
    messageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
        LayoutTheme.get().fontSizeAdminSection));

    acceptButton = actionButton("I Accept", this::accept);
    acceptButton.setName(UiComponentNames.LICENSE_AGREEMENT_ACCEPT_BUTTON);
    JButton declineButton = actionButton("Decline", this::decline);
    declineButton.setName(UiComponentNames.LICENSE_AGREEMENT_DECLINE_BUTTON);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
    buttons.setOpaque(false);
    buttons.add(acceptButton);
    buttons.add(declineButton);

    JPanel south = new JPanel(new BorderLayout(0, 8));
    south.setOpaque(false);
    south.add(messageLabel, BorderLayout.NORTH);
    south.add(buttons, BorderLayout.CENTER);
    panel.add(south, BorderLayout.SOUTH);

    if (mode == Mode.COMMERCIAL) {
      SwingUtilities.invokeLater(() -> textPane.scrollToReference(
          LicenseAgreement.COMMERCIAL_ANCHOR));
    } else {
      SwingUtilities.invokeLater(() -> textPane.setCaretPosition(0));
    }
    return panel;
  }

  private String explanation() {
    if (mode == Mode.COMMERCIAL) {
      return "A Commercial license is active. Before the bill acceptor and card reader are enabled,"
          + " please read and accept Section 3 (Commercial Use): you, not Digital Jukebox"
          + " Technologies, LLC, are responsible for obtaining all public performance licenses for"
          + " the music played.";
    }
    if (commercialLicensed) {
      return "Please read the agreement, including Section 3 (Commercial Use): you, not Digital"
          + " Jukebox Technologies, LLC, are responsible for obtaining all public performance"
          + " licenses for the music played.";
    }
    return "Please read and accept the license agreement to use JukeANator. Sections 1 and 2 apply"
        + " to home / non-commercial use.";
  }

  private JButton actionButton(String text, Runnable action) {
    JButton button = new JButton(text) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ButtonFactory.paintNavButtonBackground(g2, this, getModel().isRollover());
        g2.dispose();
        ButtonFactory.paintLabelShifted(g, this, super::paintComponent);
      }
    };
    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeLoginBtn));
    button.setForeground(ColorTheme.get().textPrimary);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setFocusable(false);
    button.setRolloverEnabled(true);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(LayoutTheme.get().loginActionBtnW,
        LayoutTheme.get().loginActionBtnH));
    button.setCursor(KioskCursors.hand());
    button.addActionListener(e -> action.run());
    return button;
  }

  // ---------------------------------------------------------------- actions

  private void accept() {
    if (saving) {
      return;
    }
    saving = true;
    acceptButton.setEnabled(false);
    SwingSecurityUtil.runAsync(() -> {
      try {
        licenseService.acceptAgreement(commercialLicensed);
        SwingUtilities.invokeLater(() -> {
          log.info("License agreement accepted ({} mode, commercial section {})", mode,
              commercialLicensed ? "included" : "not applicable");
          onAccepted.run();
        });
      } catch (RuntimeException ex) {
        log.warn("Could not save the license agreement acceptance", ex);
        SwingUtilities.invokeLater(() -> {
          saving = false;
          acceptButton.setEnabled(true);
          messageLabel.setText("Could not save: " + ex.getMessage());
        });
      }
    });
  }

  private void decline() {
    if (saving) {
      return;
    }
    log.info("License agreement declined ({} mode)", mode);
    onDeclined.run();
  }
}
