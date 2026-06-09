package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.DownloadAlbumCoverArtRequest;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * Modal dialog for inspecting and fixing album metadata. Features navigation through problematic
 * albums and integrated internet lookups.
 */
public class EditAlbumDialog extends JDialog {

  private static final long serialVersionUID = 1L;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_DARK = new Color(26, 26, 36);
  private static final Color CARD_BG = new Color(36, 36, 50);
  private static final Color TEXT_LIGHT = new Color(230, 230, 240);
  private static final Color ACCENT_BLUE = new Color(52, 152, 219);
  private static final Color GRAD_TOP = new Color(44, 62, 80);
  private static final Color GRAD_BOTTOM = new Color(22, 32, 43);

  private final SongLibraryService songLibraryService;
  private final List<AlbumDto> invalidAlbumsList;
  private int currentAlbumIndex = -1;
  private AlbumDto currentAlbum;

  // Internet Search State
  private List<AlbumMetadataDto> searchResults = new ArrayList<>();
  private int currentResultIndex = -1;

  // ── UI Components ────────────────────────────────────────────────────────
  private JLabel lblTopHeader;

  // Left Panel: Current Properties Components
  private JLabel lblCurrentCoverArt;
  private JTextField tfReleaseDate;
  private JTextField tfRecordLabel;
  private JCheckBox chbHasExplicit;

  // Right Panel: Internet Search Inputs & Results Components
  private JTextField tfSearchArtist;
  private JTextField tfSearchAlbum;
  private JLabel lblCoverArtCanvas;
  private JLabel lblSearchStatus;

  // Internet Search Result Metadata Readouts
  private JTextField tfResultReleaseDate;
  private JTextField tfResultRecordLabel;
  private JCheckBox chbResultHasExplicit;

  // Navigation Buttons (Invalid Metadata Albums)
  private JButton btnPrevAlbum;
  private JButton btnNextAlbum;

  // Navigation Buttons (Internet Results)
  private JButton btnPrevResult;
  private JButton btnNextResult;

  // Global Actions Buttons
  private JButton btnUpdateMeta;
  private JButton btnDownloadArt;

  public EditAlbumDialog(Frame owner, SongLibraryService songLibraryService, AlbumDto selectedAlbum,
      List<AlbumDto> invalidAlbumsList) {
    super(owner, "Edit Album Metadata", true);
    this.songLibraryService = songLibraryService;
    this.invalidAlbumsList = invalidAlbumsList;
    this.currentAlbum = selectedAlbum;

    if (invalidAlbumsList != null && selectedAlbum != null) {
      this.currentAlbumIndex = invalidAlbumsList.indexOf(selectedAlbum);
    }

    setResizable(false);
    initLayout();
    populateAlbumData();
  }

  private void initLayout() {
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBackground(BG_DARK);
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    // 1. Header & Album Master Navigation Block
    JPanel topContainer = new JPanel(new BorderLayout());
    topContainer.setOpaque(false);
    topContainer.setBorder(new EmptyBorder(0, 0, 10, 0));

    lblTopHeader = new JLabel("Editing Album Metadata", SwingConstants.CENTER);
    lblTopHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    lblTopHeader.setForeground(TEXT_LIGHT);
    topContainer.add(lblTopHeader, BorderLayout.CENTER);

    JPanel albumNavPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    albumNavPanel.setOpaque(false);
    btnPrevAlbum = createStyledButton("< Prev Album", e -> navigateAlbum(-1));
    btnNextAlbum = createStyledButton("Next Album >", e -> navigateAlbum(1));
    albumNavPanel.add(btnPrevAlbum);
    albumNavPanel.add(btnNextAlbum);
    topContainer.add(albumNavPanel, BorderLayout.SOUTH);

    mainPanel.add(topContainer, BorderLayout.NORTH);

    // 2. Central Content splitting Base Data and Internet Lookup
    JPanel centerSplitPanel = new JPanel(new GridLayout(1, 2, 15, 0));
    centerSplitPanel.setOpaque(false);

    // ==========================================
    // LEFT PANEL: CURRENT PROPERTIES (Read-Only)
    // ==========================================
    JPanel leftPanel = new JPanel();
    leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
    leftPanel.setBackground(CARD_BG);
    leftPanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(ACCENT_BLUE), "Current Properties", 0, 0, null, TEXT_LIGHT));

    // Symmetric alignment offset padding
    leftPanel.add(Box.createVerticalStrut(51));

    // Cover Art Box
    lblCurrentCoverArt = new JLabel();
    lblCurrentCoverArt.setPreferredSize(new Dimension(250, 250));
    lblCurrentCoverArt.setMinimumSize(new Dimension(250, 250));
    lblCurrentCoverArt.setMaximumSize(new Dimension(250, 250));
    lblCurrentCoverArt.setHorizontalAlignment(SwingConstants.CENTER);
    lblCurrentCoverArt.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
    lblCurrentCoverArt.setBackground(Color.BLACK);
    lblCurrentCoverArt.setOpaque(true);
    lblCurrentCoverArt.setAlignmentX(CENTER_ALIGNMENT);
    leftPanel.add(lblCurrentCoverArt);
    leftPanel.add(Box.createVerticalStrut(15));

    // Form Grid
    JPanel fieldsForm = new JPanel(new GridBagLayout());
    fieldsForm.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(6, 8, 6, 8);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.gridx = 0;
    gbc.gridy = 0;
    fieldsForm.add(createLabel("Release Year:"), gbc);
    gbc.gridx = 1;
    tfReleaseDate = new JTextField(15);
    setupTextField(tfReleaseDate);
    tfReleaseDate.setEditable(false);
    fieldsForm.add(tfReleaseDate, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    fieldsForm.add(createLabel("Record Label:"), gbc);
    gbc.gridx = 1;
    tfRecordLabel = new JTextField(15);
    setupTextField(tfRecordLabel);
    tfRecordLabel.setEditable(false);
    fieldsForm.add(tfRecordLabel, gbc);

    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    chbHasExplicit = new JCheckBox("Has Explicit Lyrics");
    setupCheckBox(chbHasExplicit);
    chbHasExplicit.setEnabled(false);
    fieldsForm.add(chbHasExplicit, gbc);

    leftPanel.add(fieldsForm);

    // Item #2: Padding balance buffer tracking against internet panel pagination height
    leftPanel.add(Box.createVerticalStrut(50));
    centerSplitPanel.add(leftPanel);

    // ==========================================
    // RIGHT PANEL: INTERNET SEARCH ENGINE (Editable)
    // ==========================================
    JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
    rightPanel.setBackground(CARD_BG);
    rightPanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(ACCENT_BLUE), "Internet Search", 0, 0, null, TEXT_LIGHT));

    // Input row parameters
    JPanel searchInputsPanel = new JPanel(new GridBagLayout());
    searchInputsPanel.setOpaque(false);
    GridBagConstraints gbcS = new GridBagConstraints();
    gbcS.insets = new Insets(6, 4, 6, 4);
    gbcS.fill = GridBagConstraints.HORIZONTAL;

    gbcS.gridx = 0;
    gbcS.gridy = 0;
    gbcS.weightx = 0.0;
    searchInputsPanel.add(createLabel("Artist:"), gbcS);
    gbcS.gridx = 1;
    gbcS.weightx = 0.5;
    tfSearchArtist = new JTextField(10);
    setupTextField(tfSearchArtist);
    searchInputsPanel.add(tfSearchArtist, gbcS);

    gbcS.gridx = 2;
    gbcS.weightx = 0.0;
    searchInputsPanel.add(createLabel(" Album:"), gbcS);
    gbcS.gridx = 3;
    gbcS.weightx = 0.5;
    tfSearchAlbum = new JTextField(10);
    setupTextField(tfSearchAlbum);
    searchInputsPanel.add(tfSearchAlbum, gbcS);

    gbcS.gridx = 4;
    gbcS.gridy = 0;
    gbcS.weightx = 0.0;
    gbcS.gridwidth = 1;
    JButton btnExecuteSearch = createStyledButton("Search", e -> doInternetSearch());
    searchInputsPanel.add(btnExecuteSearch, gbcS);

    rightPanel.add(searchInputsPanel, BorderLayout.NORTH);

    // Central search data content cluster
    JPanel rightCenterContainer = new JPanel();
    rightCenterContainer.setLayout(new BoxLayout(rightCenterContainer, BoxLayout.Y_AXIS));
    rightCenterContainer.setOpaque(false);

    // Found Cover Artwork Container
    lblCoverArtCanvas = new JLabel();
    lblCoverArtCanvas.setPreferredSize(new Dimension(250, 250));
    lblCoverArtCanvas.setMinimumSize(new Dimension(250, 250));
    lblCoverArtCanvas.setMaximumSize(new Dimension(250, 250));
    lblCoverArtCanvas.setHorizontalAlignment(SwingConstants.CENTER);
    lblCoverArtCanvas.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
    lblCoverArtCanvas.setBackground(Color.BLACK);
    lblCoverArtCanvas.setOpaque(true);
    lblCoverArtCanvas.setAlignmentX(CENTER_ALIGNMENT);
    rightCenterContainer.add(lblCoverArtCanvas);
    rightCenterContainer.add(Box.createVerticalStrut(15));

    // Item #1: Mirrored forms tracking interactive fields
    JPanel resultsFormPanel = new JPanel(new GridBagLayout());
    resultsFormPanel.setOpaque(false);
    GridBagConstraints gbcR = new GridBagConstraints();
    gbcR.insets = new Insets(6, 8, 6, 8);
    gbcR.fill = GridBagConstraints.HORIZONTAL;

    gbcR.gridx = 0;
    gbcR.gridy = 0;
    resultsFormPanel.add(createLabel("Release Year:"), gbcR);
    gbcR.gridx = 1;
    tfResultReleaseDate = new JTextField(15);
    setupTextField(tfResultReleaseDate);
    tfResultReleaseDate.setEditable(true);
    resultsFormPanel.add(tfResultReleaseDate, gbcR);

    gbcR.gridx = 0;
    gbcR.gridy = 1;
    resultsFormPanel.add(createLabel("Record Label:"), gbcR);
    gbcR.gridx = 1;
    tfResultRecordLabel = new JTextField(15);
    setupTextField(tfResultRecordLabel);
    tfResultRecordLabel.setEditable(true);
    resultsFormPanel.add(tfResultRecordLabel, gbcR);

    gbcR.gridx = 0;
    gbcR.gridy = 2;
    gbcR.gridwidth = 2;
    chbResultHasExplicit = new JCheckBox("Has Explicit Lyrics");
    setupCheckBox(chbResultHasExplicit);
    chbResultHasExplicit.setEnabled(true);
    resultsFormPanel.add(chbResultHasExplicit, gbcR);

    rightCenterContainer.add(resultsFormPanel);
    rightPanel.add(rightCenterContainer, BorderLayout.CENTER);

    // Pagination row
    JPanel searchControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
    searchControlPanel.setOpaque(false);

    btnPrevResult = createStyledButton("< Prev Result", e -> navigateSearchResult(-1));
    btnNextResult = createStyledButton("Next Result >", e -> navigateSearchResult(1));

    lblSearchStatus = new JLabel("No search performed", SwingConstants.CENTER);
    lblSearchStatus.setForeground(Color.LIGHT_GRAY);
    lblSearchStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    searchControlPanel.add(btnPrevResult);
    searchControlPanel.add(lblSearchStatus);
    searchControlPanel.add(btnNextResult);

    rightPanel.add(searchControlPanel, BorderLayout.SOUTH);
    centerSplitPanel.add(rightPanel);
    mainPanel.add(centerSplitPanel, BorderLayout.CENTER);

    // 3. Global action footer control panel
    JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
    footerPanel.setOpaque(false);

    btnUpdateMeta = createStyledButton("Update Metadata", e -> doMetadataUpdate());
    btnDownloadArt = createStyledButton("Download Cover Art", e -> doCoverArtDownload());
    JButton btnCancel = createStyledButton("Cancel", e -> dispose());

    btnUpdateMeta.setEnabled(false);
    btnDownloadArt.setEnabled(false);

    footerPanel.add(btnUpdateMeta);
    footerPanel.add(btnDownloadArt);
    footerPanel.add(btnCancel);
    mainPanel.add(footerPanel, BorderLayout.SOUTH);

    setContentPane(mainPanel);
    pack();

    // Item #2: Higher dialog parameters definition context
    setSize(new Dimension(860, 660));
    setLocationRelativeTo(getOwner());
  }

  private void populateAlbumData() {
    if (currentAlbum == null)
      return;

    lblTopHeader.setText(
        "Editing: " + currentAlbum.getAlbumName() + " (" + currentAlbum.getArtistName() + ")");
    tfReleaseDate
        .setText(currentAlbum.getReleaseDate() == null ? "" : currentAlbum.getReleaseDate());
    tfRecordLabel
        .setText(currentAlbum.getRecordLabel() == null ? "" : currentAlbum.getRecordLabel());
    chbHasExplicit
        .setSelected(currentAlbum.getHasExplicit() != null && currentAlbum.getHasExplicit());

    tfSearchArtist.setText(currentAlbum.getArtistName());
    tfSearchAlbum.setText(currentAlbum.getAlbumName());

    // Local file system cover art asset rendering
    lblCurrentCoverArt.setIcon(null);
    lblCurrentCoverArt.setText("");
    String coverArtPath = currentAlbum.getCoverArtPath();
    if (coverArtPath != null && !coverArtPath.isBlank()) {
      File file = new File(coverArtPath);
      if (file.exists()) {
        try {
          Image img = ImageIO.read(file);
          if (img != null) {
            Image scaled = img.getScaledInstance(250, 250, Image.SCALE_SMOOTH);
            lblCurrentCoverArt.setIcon(new ImageIcon(scaled));
          }
        } catch (Exception e) {
          lblCurrentCoverArt.setText("Error loading artwork asset file.");
        }
      } else {
        lblCurrentCoverArt.setText("Cover art path file not found.");
      }
    } else {
      lblCurrentCoverArt.setText("No local artwork path specified.");
    }

    if (currentAlbumIndex == -1 || invalidAlbumsList == null) {
      btnPrevAlbum.setEnabled(false);
      btnNextAlbum.setEnabled(false);
    } else {
      btnPrevAlbum.setEnabled(currentAlbumIndex > 0);
      btnNextAlbum.setEnabled(currentAlbumIndex < invalidAlbumsList.size() - 1);
    }

    searchResults.clear();
    currentResultIndex = -1;
    updateSearchResultUI();
  }

  private void navigateAlbum(int offset) {
    if (invalidAlbumsList == null || currentAlbumIndex == -1)
      return;
    int target = currentAlbumIndex + offset;
    if (target >= 0 && target < invalidAlbumsList.size()) {
      currentAlbumIndex = target;
      currentAlbum = invalidAlbumsList.get(currentAlbumIndex);
      populateAlbumData();
    }
  }

  private void navigateSearchResult(int offset) {
    if (searchResults.isEmpty())
      return;
    int target = currentResultIndex + offset;
    if (target >= 0 && target < searchResults.size()) {
      currentResultIndex = target;
      updateSearchResultUI();
    }
  }

  private void updateSearchResultUI() {
    if (currentResultIndex == -1 || searchResults.isEmpty()) {
      btnPrevResult.setEnabled(false);
      btnNextResult.setEnabled(false);
      lblSearchStatus.setText("No results loaded.");
      lblCoverArtCanvas.setIcon(null);
      tfResultReleaseDate.setText("");
      tfResultRecordLabel.setText("");
      chbResultHasExplicit.setSelected(false);

      btnUpdateMeta.setEnabled(false);
      btnDownloadArt.setEnabled(false);
      return;
    }

    btnPrevResult.setEnabled(currentResultIndex > 0);
    btnNextResult.setEnabled(currentResultIndex < searchResults.size() - 1);
    lblSearchStatus
        .setText(String.format("Result %d of %d", (currentResultIndex + 1), searchResults.size()));

    AlbumMetadataDto selectedMeta = searchResults.get(currentResultIndex);

    tfResultReleaseDate
        .setText(selectedMeta.getReleaseDate() == null ? "" : selectedMeta.getReleaseDate());
    tfResultRecordLabel
        .setText(selectedMeta.getRecordLabel() == null ? "" : selectedMeta.getRecordLabel());
    chbResultHasExplicit.setSelected(selectedMeta.hasExplicit());

    String yearVal = tfResultReleaseDate.getText().trim();
    String labelVal = tfResultRecordLabel.getText().trim();
    String urlStr = selectedMeta.getCoverArtUrl();

    boolean hasValidMetadata =
        !yearVal.isEmpty() && !labelVal.isEmpty() && urlStr != null && !urlStr.isBlank();
    btnUpdateMeta.setEnabled(hasValidMetadata);
    btnDownloadArt.setEnabled(hasValidMetadata);

    lblCoverArtCanvas.setIcon(null);
    lblCoverArtCanvas.setText("");
    if (urlStr != null && !urlStr.isBlank()) {
      new Thread(() -> {
        try {
          URL url = URI.create(urlStr).toURL();
          Image img = ImageIO.read(url);
          if (img != null) {
            Image scaled = img.getScaledInstance(250, 250, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            SwingUtilities.invokeLater(() -> lblCoverArtCanvas.setIcon(icon));
          }
        } catch (Exception e) {
          SwingUtilities
              .invokeLater(() -> lblCoverArtCanvas.setText("Failed to render art asset."));
        }
      }).start();
    } else {
      lblCoverArtCanvas.setText("No cover URL defined.");
    }
  }

  private void doInternetSearch() {
    
    String artistQuery = tfSearchArtist.getText().trim();
    String albumQuery = tfSearchAlbum.getText().trim();

    if (artistQuery.isEmpty() || albumQuery.isEmpty()) {
      JOptionPane.showMessageDialog(this,
          "Artist and Album text fields are required fields to query.", "Warning",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    lblSearchStatus.setText("Searching...");

    new Thread(() -> {
      try {
        
        List<AlbumMetadataDto> results =
            songLibraryService.searchInternetForAlbumMetadata(artistQuery, albumQuery, 5);
        
        SwingUtilities.invokeLater(() -> {
          this.searchResults = (results != null) ? results : new ArrayList<>();
          if (!searchResults.isEmpty()) {
            this.currentResultIndex = 0;
          } else {
            this.currentResultIndex = -1;
            JOptionPane.showMessageDialog(this, "No matches found on the web.", "Information",
                JOptionPane.INFORMATION_MESSAGE);
          }
          updateSearchResultUI();
        });
      } catch (Exception ex) {
        SwingUtilities.invokeLater(() -> {
          lblSearchStatus.setText("Search failed.");
          JOptionPane.showMessageDialog(this, "Error executing lookup: " + ex.getMessage(), "Error",
              JOptionPane.ERROR_MESSAGE);
        });
      }
    }).start();
  }
  
  private void doMetadataUpdate() {

    if (currentAlbum == null)
      return;

    String updatedYear = tfResultReleaseDate.getText().trim();
    String updatedLabel = tfResultRecordLabel.getText().trim();
    boolean updatedExplicit = chbResultHasExplicit.isSelected();

    try {

      AlbumMetadataDto metadata =
          new AlbumMetadataDto("", "", updatedLabel, updatedYear, "", "", updatedExplicit);

      songLibraryService.updateAlbumMetadata(currentAlbum.getAlbumId(), metadata);

      String messageDetails =
          String.format("Successfully staged values for update:\nYear: %s\nLabel: %s\nExplicit: %b",
              updatedYear, updatedLabel, updatedExplicit);

      JOptionPane.showMessageDialog(this, messageDetails, "Success",
          JOptionPane.INFORMATION_MESSAGE);

    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Failed updating record metadata: " + e.getMessage(),
          "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void doCoverArtDownload() {

    if (currentAlbum == null || currentResultIndex == -1 || searchResults.isEmpty())
      return;

    AlbumMetadataDto targetMeta = searchResults.get(currentResultIndex);
    String liveArtUrlStr = targetMeta.getCoverArtUrl();

    try {

      // Constructs operational scan paths safely linked to the active track record context
      DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest =
          new DownloadAlbumCoverArtRequest(currentAlbum.getAlbumId(), liveArtUrlStr);

      songLibraryService.downloadAlbumCoverArt(downloadAlbumCoverArtRequest);

      JOptionPane.showMessageDialog(this,
          "Cover art download request staged via asset URL:\n" + liveArtUrlStr, "Asset Download",
          JOptionPane.INFORMATION_MESSAGE);

    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Failed downloading art asset payload: " + e.getMessage(),
          "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  // ── Helper UI Styling Methods ──────────────────────────────────────────────
  private JLabel createLabel(String text) {
    JLabel lbl = new JLabel(text);
    lbl.setForeground(TEXT_LIGHT);
    lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    return lbl;
  }

  private void setupTextField(JTextField tf) {
    tf.setBackground(BG_DARK);
    tf.setForeground(Color.WHITE);
    tf.setCaretColor(Color.WHITE);
    tf.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY, 1),
        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
  }

  private void setupCheckBox(JCheckBox cb) {
    cb.setOpaque(false);
    cb.setForeground(TEXT_LIGHT);
    cb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
  }

  private JButton createStyledButton(String text, java.awt.event.ActionListener action) {
    JButton btn = new JButton(text) {
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
        int w = getWidth(), h = getHeight();

        if (!isEnabled()) {
          g2.setColor(CARD_BG.brighter());
          g2.fillRoundRect(0, 0, w, h, 8, 8);
          g2.setColor(Color.DARK_GRAY);
          g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
          g2.setFont(getFont());
          g2.setColor(Color.GRAY);
          java.awt.FontMetrics fm = g2.getFontMetrics();
          g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
              (h - fm.getHeight()) / 2 + fm.getAscent());
          g2.dispose();
          return;
        }

        Color top = hovered ? GRAD_TOP.brighter() : GRAD_TOP;
        Color bot = hovered ? GRAD_BOTTOM.brighter() : GRAD_BOTTOM;
        g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
        g2.fillRoundRect(0, 0, w, h, 8, 8);
        g2.setColor(ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        g2.setFont(getFont());
        g2.setColor(Color.WHITE);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
            (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
      }
    };
    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
    btn.setForeground(Color.WHITE);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setOpaque(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(action);
    return btn;
  }
}
