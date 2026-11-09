package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;
import com.djt.jukeanator_engine.domain.license.service.LicenseException;
import com.djt.jukeanator_engine.domain.license.service.LicenseService;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;

/**
 * Full-screen overlay card for licensing, the counterpart of the legacy {@code RegistrationDialog}:
 *
 * <ul>
 * <li><b>Register</b>: the customer's registered email and passcode retrieve this computer's
 * license key from the license server (automatic provisioning);</li>
 * <li><b>Import license file</b>: install a {@code JukeANator.CTL} the customer was sent (manual
 * provisioning), chosen in an embedded file chooser (separate windows lose focus in fullscreen
 * exclusive mode);</li>
 * <li><b>Try</b>: keep running unlicensed (trial / non-commercial).</li>
 * </ul>
 *
 * <p>When a license is already installed but the subscription needs the registered email or
 * location, the passcode field is hidden and <b>Save</b> stores the details instead (<b>Later</b>
 * closes the card).
 *
 * <p>The card is used while the PC and jukebox are being set up, so a physical keyboard is assumed:
 * while the card is showing, every keystroke goes to the active field (Tab / Shift+Tab / arrows
 * move between fields, Enter submits, Ctrl+V pastes) and never reaches the frame's own key
 * bindings (Escape, the bill-acceptor and card-reader keys). The on-screen keyboard works too.
 *
 * <p>The card never closes by itself: only the Try (or Later) button closes it.
 */
public class RegistrationCard extends JPanel {

  private static final long serialVersionUID = 1L;

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RegistrationCard.class);

  /** Longest value accepted in a field (typed or pasted). */
  private static final int MAX_FIELD_LENGTH = 128;

  private enum Field {
    EMAIL, PASSCODE, LOCATION
  }

  private final LicenseService licenseService;
  private final Runnable onDone;
  private final boolean detailsOnly;

  private final StringBuilder email = new StringBuilder();
  private final StringBuilder passcode = new StringBuilder();
  private final StringBuilder location = new StringBuilder();
  private Field activeField = Field.EMAIL;

  private JLabel emailLabel;
  private JLabel passcodeLabel;
  private JLabel locationLabel;
  private JLabel messageLabel;
  private JButton submitButton;
  private final JPanel center = new JPanel(new GridBagLayout());
  private JPanel formPanel;

  // Only while the form (not the embedded file chooser, which takes keys itself) is showing.
  private final PhysicalKeyboardInput physicalKeyboard = new PhysicalKeyboardInput(this,
      () -> formPanel != null && formPanel.getParent() == center,
      new PhysicalKeyboardInput.Target() {
        @Override
        public void type(String text) {
          append(text);
        }

        @Override
        public void backspace() {
          RegistrationCard.this.backspace();
        }

        @Override
        public void enter() {
          if (submitButton.isEnabled()) {
            submit();
          }
        }

        @Override
        public void moveField(int delta) {
          RegistrationCard.this.moveField(delta);
        }
      });

  /**
   * @param licenseService licensing operations
   * @param initialEmail prefilled email (may be null)
   * @param initialLocation prefilled location name (may be null)
   * @param onDone invoked when the operator closes the card with Try (or Later)
   */
  public RegistrationCard(LicenseService licenseService, String initialEmail,
      String initialLocation, Runnable onDone) {

    this.licenseService = licenseService;
    this.onDone = onDone;
    LicenseStatus status = licenseService.getStatus();
    this.detailsOnly = !status.isTrial();
    if (initialEmail != null) {
      email.append(initialEmail);
    }
    if (initialLocation != null) {
      location.append(initialLocation);
    }

    setOpaque(false);
    setLayout(new BorderLayout());

    center.setOpaque(false);
    formPanel = buildFormPanel(status);
    center.add(formPanel);
    add(center, BorderLayout.CENTER);

    KeyboardPanel keyboard = new KeyboardPanel(new KeyboardPanel.KeyboardListener() {
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
        activeBuffer().setLength(0);
        syncLabels();
      }

      @Override
      public void onSpace() {
        append(" ");
      }
    });
    add(keyboard, BorderLayout.SOUTH);

    syncLabels();
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

  private JPanel buildFormPanel(LicenseStatus status) {
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
    panel.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));
    panel.setPreferredSize(new Dimension(Math.round(LayoutTheme.get().loginPanelW * 1.25f),
        Math.round(LayoutTheme.get().loginPanelH * (detailsOnly ? 1.55f : 1.85f))));

    JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
    header.setOpaque(false);
    JLabel title = new JLabel(detailsOnly ? "Registration Details" : "Register JukeANator",
        SwingConstants.CENTER);
    title.setForeground(ColorTheme.get().loginTitleColor);
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeLoginTitle));
    header.add(title);
    header.add(infoLabel(detailsOnly
        ? "Your subscription needs your registered email address and location."
        : "Enter the email address and passcode you registered with, or import your license"
            + " file. Unlicensed, the bill acceptor and card reader stay disabled."));
    header.add(machineIdRow(status.machineId()));
    panel.add(header, BorderLayout.NORTH);

    JPanel fields = new JPanel(new GridLayout(0, 1, 0, 8));
    fields.setOpaque(false);
    emailLabel = fieldRow(fields, "EMAIL:", Field.EMAIL,
        UiComponentNames.REGISTRATION_EMAIL_FIELD);
    if (!detailsOnly) {
      passcodeLabel = fieldRow(fields, "PASSCODE:", Field.PASSCODE,
          UiComponentNames.REGISTRATION_PASSCODE_FIELD);
    }
    locationLabel = fieldRow(fields, "LOCATION:", Field.LOCATION,
        UiComponentNames.REGISTRATION_LOCATION_FIELD);
    panel.add(fields, BorderLayout.CENTER);

    messageLabel = new JLabel(" ", SwingConstants.CENTER);
    messageLabel.setName(UiComponentNames.REGISTRATION_MESSAGE_LABEL);
    messageLabel.setForeground(ColorTheme.get().loginError);
    messageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
        LayoutTheme.get().fontSizeAdminSection));
    if (status.message() != null) {
      showMessage(status.message(), status.warning());
    }

    submitButton = actionButton(detailsOnly ? "Save" : "Register", this::submit);
    submitButton.setName(UiComponentNames.REGISTRATION_SUBMIT_BUTTON);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
    buttons.setOpaque(false);
    buttons.add(submitButton);
    if (!detailsOnly) {
      JButton importButton = actionButton("Import File", this::showFileChooser);
      importButton.setName(UiComponentNames.REGISTRATION_IMPORT_BUTTON);
      buttons.add(importButton);
    }
    JButton tryButton = actionButton(detailsOnly ? "Later" : "Try",
        () -> finish(detailsOnly ? "operator chose Later" : "operator chose Try"));
    tryButton.setName(UiComponentNames.REGISTRATION_TRY_BUTTON);
    buttons.add(tryButton);

    JPanel south = new JPanel(new BorderLayout(0, 8));
    south.setOpaque(false);
    south.add(messageLabel, BorderLayout.NORTH);
    south.add(buttons, BorderLayout.CENTER);
    panel.add(south, BorderLayout.SOUTH);
    return panel;
  }

  private JLabel infoLabel(String text) {
    JLabel label = new JLabel("<html><div style='text-align:center'>" + escape(text)
        + "</div></html>", SwingConstants.CENTER);
    label.setForeground(ColorTheme.get().textSecondary);
    label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeLoginCaption));
    return label;
  }

  private JPanel machineIdRow(String machineId) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    row.setOpaque(false);
    JLabel label = new JLabel("Machine ID: " + (machineId == null ? "(unknown)" : machineId));
    label.setForeground(ColorTheme.get().textPrimary);
    label.setFont(new Font(Font.MONOSPACED, Font.BOLD, LayoutTheme.get().fontSizeLoginCaption));
    row.add(label);
    if (machineId != null) {
      JButton copy = actionButton("Copy", () -> {
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(machineId), null);
        showMessage("Machine ID copied to the clipboard.", true);
      });
      copy.setPreferredSize(new Dimension(LayoutTheme.get().loginActionBtnW / 2,
          LayoutTheme.get().loginActionBtnH * 2 / 3));
      row.add(copy);
    }
    return row;
  }

  private JLabel fieldRow(JPanel parent, String caption, Field field, String name) {
    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);

    JLabel captionLabel = new JLabel(caption);
    captionLabel.setForeground(ColorTheme.get().textSecondary);
    captionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
        LayoutTheme.get().fontSizeLoginCaption));
    captionLabel.setPreferredSize(new Dimension(LayoutTheme.get().loginCaptionW,
        LayoutTheme.get().loginFieldH));
    captionLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    JLabel value = new JLabel(" ");
    value.setName(name);
    value.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeLoginField));
    value.setForeground(ColorTheme.get().textPrimary);
    value.setBackground(ColorTheme.get().bgFieldDark);
    value.setOpaque(true);
    value.setPreferredSize(new Dimension(100, LayoutTheme.get().loginFieldH));
    value.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
    value.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        if (!TouchInput.isTap(e)) {
          return;
        }
        activeField = field;
        syncLabels();
      }
    });

    row.add(captionLabel, BorderLayout.WEST);
    row.add(value, BorderLayout.CENTER);
    parent.add(row);
    return value;
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
    // Not focusable, so a physical Space / Enter never "clicks" whichever button was clicked last.
    button.setFocusable(false);
    button.setRolloverEnabled(true);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(LayoutTheme.get().loginActionBtnW,
        LayoutTheme.get().loginActionBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> action.run());
    return button;
  }

  // ---------------------------------------------------------------- actions

  private void submit() {
    String e = email.toString().trim();
    String p = passcode.toString();
    String l = location.toString().trim();
    if (e.isEmpty() || (!detailsOnly && p.isEmpty())) {
      showMessage(detailsOnly ? "Please enter your registered email address."
          : "Please enter your registered email address and passcode.", false);
      return;
    }
    submitButton.setEnabled(false);
    showMessage(detailsOnly ? "Saving..." : "Contacting the license server...", true);
    SwingSecurityUtil.runAsync(() -> {
      try {
        LicenseStatus status = detailsOnly
            ? licenseService.updateRegistrationDetails(e, null, l)
            : licenseService.provision(e, p, l);
        SwingUtilities.invokeLater(() -> afterOperation(status));
      } catch (LicenseException ex) {
        SwingUtilities.invokeLater(() -> {
          submitButton.setEnabled(true);
          showMessage(ex.getMessage(), false);
        });
      } catch (RuntimeException ex) {
        SwingUtilities.invokeLater(() -> {
          submitButton.setEnabled(true);
          showMessage("Registration failed: " + ex.getMessage(), false);
        });
      }
    });
  }

  private void afterOperation(LicenseStatus status) {
    submitButton.setEnabled(true);
    if (!status.isTrial() && !status.registrationNeeded()) {
      // The card stays up until the operator closes it, so the result can be read.
      log.info("Registration screen: licensed ({})", status.describe());
      showMessage("Licensed: " + status.describe() + ". Click "
          + (detailsOnly ? "Later" : "Try") + " to start the jukebox.", true);
    } else {
      showMessage(status.message() != null ? status.message()
          : "The license could not be activated.", status.warning());
    }
  }

  private void showFileChooser() {
    JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home")));
    chooser.setDialogType(JFileChooser.OPEN_DIALOG);
    chooser.setFileFilter(new FileNameExtensionFilter("JukeANator license (*.ctl)", "ctl", "CTL"));
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.setPreferredSize(formPanel.getPreferredSize());
    chooser.addActionListener(e -> {
      File selected = chooser.getSelectedFile();
      showForm();
      if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand()) && selected != null) {
        showMessage("Installing " + selected.getName() + "...", true);
        // Keep what the operator typed on the form (the import itself carries no details).
        String typedEmail = email.toString().trim();
        String typedLocation = location.toString().trim();
        SwingSecurityUtil.runAsync(() -> {
          try {
            LicenseStatus imported = licenseService.importLicenseFile(selected.toPath());
            LicenseStatus status = typedEmail.isEmpty() && typedLocation.isEmpty() ? imported
                : licenseService.updateRegistrationDetails(typedEmail, null, typedLocation);
            SwingUtilities.invokeLater(() -> afterOperation(status));
          } catch (RuntimeException ex) {
            SwingUtilities.invokeLater(() -> showMessage(ex.getMessage(), false));
          }
        });
      }
    });
    center.removeAll();
    center.add(chooser);
    center.revalidate();
    center.repaint();
    // The chooser's own file-name field takes physical keystrokes while it is showing.
    SwingUtilities.invokeLater(chooser::requestFocusInWindow);
  }

  private void showForm() {
    center.removeAll();
    center.add(formPanel);
    center.revalidate();
    center.repaint();
  }

  private void finish(String reason) {
    log.info("Registration screen closed: {}", reason);
    physicalKeyboard.uninstall();
    onDone.run();
  }

  private void moveField(int delta) {
    Field[] order = detailsOnly ? new Field[] {Field.EMAIL, Field.LOCATION} : Field.values();
    int index = 0;
    for (int i = 0; i < order.length; i++) {
      if (order[i] == activeField) {
        index = i;
      }
    }
    activeField = order[Math.floorMod(index + delta, order.length)];
    syncLabels();
  }

  // ---------------------------------------------------------------- helpers

  private StringBuilder activeBuffer() {
    return switch (activeField) {
      case EMAIL -> email;
      case PASSCODE -> passcode;
      case LOCATION -> location;
    };
  }

  private void append(String text) {
    StringBuilder buf = activeBuffer();
    int room = MAX_FIELD_LENGTH - buf.length();
    if (room > 0) {
      buf.append(text, 0, Math.min(room, text.length()));
    }
    syncLabels();
  }

  private void backspace() {
    StringBuilder buf = activeBuffer();
    if (buf.length() > 0) {
      buf.deleteCharAt(buf.length() - 1);
    }
    syncLabels();
  }

  private void syncLabels() {
    setField(emailLabel, email.toString(), activeField == Field.EMAIL);
    if (passcodeLabel != null) {
      setField(passcodeLabel, "•".repeat(passcode.length()), activeField == Field.PASSCODE);
    }
    setField(locationLabel, location.toString(), activeField == Field.LOCATION);
  }

  private static void setField(JLabel label, String text, boolean active) {
    label.setText(text.isEmpty() ? " " : text);
    Color border = active ? ColorTheme.get().accentBlue : ColorTheme.get().textPrimary;
    label.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(2, 1, 1, 1, border),
        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
  }

  private void showMessage(String text, boolean informational) {
    messageLabel.setForeground(informational ? ColorTheme.get().textSecondary
        : ColorTheme.get().loginError);
    messageLabel.setText("<html><div style='text-align:center'>" + escape(text)
        + "</div></html>");
  }

  private static String escape(String s) {
    return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
