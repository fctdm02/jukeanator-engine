package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.djt.jukeanator_engine.domain.license.service.LicenseService;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

/**
 * Full-screen overlay "popup" asking the operator for one licensing detail the license server could
 * not supply: the customer name ({@code license.customer-name}) or the location name
 * ({@code license.location-name}). Save writes it to the external {@code application.yml}; Skip
 * leaves it unset, and the prompt appears again at the next start.
 *
 * <p>Like the registration card it takes the physical keyboard (setup time) as well as the
 * on-screen keyboard, and it closes only through its own buttons.
 */
public class LicenseDetailPromptCard extends JPanel {

  private static final long serialVersionUID = 1L;

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(LicenseDetailPromptCard.class);

  private static final int MAX_LENGTH = 128;

  /** The detail being asked for. */
  public enum Detail {
    CUSTOMER_NAME("Customer Name", "NAME:",
        "The license server has no name for this customer. Please enter the business or owner"
            + " name the jukebox is registered to.",
        "license.customer-name"),
    LOCATION_NAME("Location Name", "LOCATION:",
        "Please enter the name of the location where this jukebox is installed (for example,"
            + " the bar or venue name).",
        "license.location-name");

    private final String title;
    private final String caption;
    private final String explanation;
    private final String property;

    Detail(String title, String caption, String explanation, String property) {
      this.title = title;
      this.caption = caption;
      this.explanation = explanation;
      this.property = property;
    }
  }

  private final LicenseService licenseService;
  private final Detail detail;
  private final Runnable onDone;
  private final StringBuilder value = new StringBuilder();

  private JLabel valueLabel;
  private JLabel messageLabel;
  private JButton saveButton;
  private boolean saving;

  private final PhysicalKeyboardInput physicalKeyboard;

  /**
   * @param licenseService saves the value
   * @param detail what to ask for
   * @param onDone invoked when the card closes (saved or skipped)
   */
  public LicenseDetailPromptCard(LicenseService licenseService, Detail detail, Runnable onDone) {
    this.licenseService = licenseService;
    this.detail = detail;
    this.onDone = onDone;

    setOpaque(false);
    setLayout(new BorderLayout());

    JPanel center = new JPanel(new GridBagLayout());
    center.setOpaque(false);
    center.add(buildFormPanel());
    add(center, BorderLayout.CENTER);

    add(new KeyboardPanel(new KeyboardPanel.KeyboardListener() {
      @Override
      public void onCharacter(String ch) {
        append(ch);
      }

      @Override
      public void onBackspace() {
        backspace();
      }

      @Override
      public void onClear() {
        value.setLength(0);
        syncLabel();
      }

      @Override
      public void onSpace() {
        append(" ");
      }
    }), BorderLayout.SOUTH);

    physicalKeyboard = new PhysicalKeyboardInput(this, () -> true,
        new PhysicalKeyboardInput.Target() {
          @Override
          public void type(String text) {
            append(text);
          }

          @Override
          public void backspace() {
            LicenseDetailPromptCard.this.backspace();
          }

          @Override
          public void enter() {
            save();
          }
        });

    syncLabel();
  }

  /** Releases the physical keyboard; call when the card is removed. */
  public void dismiss() {
    physicalKeyboard.uninstall();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    physicalKeyboard.install();
  }

  @Override
  public void removeNotify() {
    physicalKeyboard.uninstall();
    super.removeNotify();
  }

  // ---------------------------------------------------------------- layout

  private JPanel buildFormPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 16)) {
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
    panel.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));
    panel.setPreferredSize(new Dimension(Math.round(LayoutTheme.get().loginPanelW * 1.25f),
        Math.round(LayoutTheme.get().loginPanelH * 1.1f)));

    JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
    header.setOpaque(false);
    JLabel title = new JLabel(detail.title, SwingConstants.CENTER);
    title.setForeground(ColorTheme.get().loginTitleColor);
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeLoginTitle));
    header.add(title);
    JLabel info = new JLabel("<html><div style='text-align:center'>" + detail.explanation
        + "</div></html>", SwingConstants.CENTER);
    info.setForeground(ColorTheme.get().textSecondary);
    info.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeLoginCaption));
    header.add(info);
    panel.add(header, BorderLayout.NORTH);

    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);
    JLabel caption = new JLabel(detail.caption);
    caption.setForeground(ColorTheme.get().textSecondary);
    caption.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeLoginCaption));
    caption.setPreferredSize(new Dimension(LayoutTheme.get().loginCaptionW,
        LayoutTheme.get().loginFieldH));
    caption.setHorizontalAlignment(SwingConstants.RIGHT);
    valueLabel = new JLabel(" ");
    valueLabel.setName(UiComponentNames.LICENSE_DETAIL_FIELD);
    valueLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,
        LayoutTheme.get().fontSizeLoginField));
    valueLabel.setForeground(ColorTheme.get().textPrimary);
    valueLabel.setBackground(ColorTheme.get().bgFieldDark);
    valueLabel.setOpaque(true);
    valueLabel.setPreferredSize(new Dimension(100, LayoutTheme.get().loginFieldH));
    valueLabel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(2, 1, 1, 1, ColorTheme.get().accentBlue),
        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    row.add(caption, BorderLayout.WEST);
    row.add(valueLabel, BorderLayout.CENTER);
    JPanel fieldWrap = new JPanel(new GridBagLayout());
    fieldWrap.setOpaque(false);
    row.setPreferredSize(new Dimension(Math.round(LayoutTheme.get().loginPanelW * 1.1f),
        LayoutTheme.get().loginFieldH));
    fieldWrap.add(row);
    panel.add(fieldWrap, BorderLayout.CENTER);

    messageLabel = new JLabel(" ", SwingConstants.CENTER);
    messageLabel.setName(UiComponentNames.LICENSE_DETAIL_MESSAGE_LABEL);
    messageLabel.setForeground(ColorTheme.get().loginError);
    messageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
        LayoutTheme.get().fontSizeAdminSection));

    saveButton = actionButton("Save", this::save);
    saveButton.setName(UiComponentNames.LICENSE_DETAIL_SAVE_BUTTON);
    JButton skipButton = actionButton("Skip", () -> finish("skipped"));
    skipButton.setName(UiComponentNames.LICENSE_DETAIL_SKIP_BUTTON);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
    buttons.setOpaque(false);
    buttons.add(saveButton);
    buttons.add(skipButton);

    JPanel south = new JPanel(new BorderLayout(0, 8));
    south.setOpaque(false);
    south.add(messageLabel, BorderLayout.NORTH);
    south.add(buttons, BorderLayout.CENTER);
    panel.add(south, BorderLayout.SOUTH);
    return panel;
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
    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    button.addActionListener(e -> action.run());
    return button;
  }

  // ---------------------------------------------------------------- actions

  private void save() {
    String text = value.toString().trim();
    if (saving) {
      return;
    }
    if (text.isEmpty()) {
      showError("Please enter the " + detail.title.toLowerCase() + ".");
      return;
    }
    saving = true;
    saveButton.setEnabled(false);
    SwingSecurityUtil.runAsync(() -> {
      try {
        if (detail == Detail.CUSTOMER_NAME) {
          licenseService.updateRegistrationDetails(null, text, null);
        } else {
          licenseService.updateRegistrationDetails(null, null, text);
        }
        SwingUtilities.invokeLater(() -> {
          log.info("{} saved as {}", detail.property, text);
          finish("saved");
        });
      } catch (RuntimeException ex) {
        SwingUtilities.invokeLater(() -> {
          saving = false;
          saveButton.setEnabled(true);
          showError("Could not save: " + ex.getMessage());
        });
      }
    });
  }

  private void finish(String reason) {
    log.info("{} prompt closed: {}", detail.title, reason);
    physicalKeyboard.uninstall();
    onDone.run();
  }

  // ---------------------------------------------------------------- helpers

  private void append(String text) {
    int room = MAX_LENGTH - value.length();
    if (room > 0) {
      value.append(text, 0, Math.min(room, text.length()));
    }
    syncLabel();
  }

  private void backspace() {
    if (value.length() > 0) {
      value.deleteCharAt(value.length() - 1);
    }
    syncLabel();
  }

  private void syncLabel() {
    valueLabel.setText(value.length() == 0 ? " " : value.toString());
  }

  private void showError(String text) {
    messageLabel.setText("<html><div style='text-align:center'>"
        + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></html>");
  }
}
