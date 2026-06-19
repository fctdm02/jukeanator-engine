package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.DownloadAlbumCoverArtRequest;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

// ─────────────────────────────────────────────────────────────────────────
// CONSTRUCTOR
// ─────────────────────────────────────────────────────────────────────────
public class EditAlbumCard extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Palette — sourced from ColorTheme.get() ──────────────────────────────

  private final SongLibraryService songLibraryService;
  private List<AlbumDto> invalidAlbumsList;
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
  private JTextField tfCoverArtUrl;

  // Navigation Buttons (Invalid Metadata Albums)
  private JButton btnPrevAlbum;
  private JButton btnNextAlbum;

  // Navigation Buttons (Internet Results)
  private JButton btnPrevResult;
  private JButton btnNextResult;

  // Global Actions Buttons
  private JButton btnUpdateMeta;
  private JButton btnDownloadArt;

  // Inline status banner (replaces JOptionPane popups)
  private JLabel lblGlobalStatus;

  // Called when the Cancel button is pressed — pops back to the AdminPanel.
  private final Runnable onDismiss;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public EditAlbumCard(SongLibraryService songLibraryService, AlbumDto selectedAlbum,
      List<AlbumDto> invalidAlbumsList, Runnable onDismiss) {
    this.songLibraryService = songLibraryService;
    this.invalidAlbumsList = invalidAlbumsList;
    this.currentAlbum = selectedAlbum;
    this.onDismiss = onDismiss;

    if (invalidAlbumsList != null && selectedAlbum != null) {
      this.currentAlbumIndex = invalidAlbumsList.indexOf(selectedAlbum);
    }

    setOpaque(false);
    setLayout(new java.awt.GridBagLayout());
    initLayout();
    populateAlbumData();
  }

  /**
   * Re-targets this card at a (possibly different) selected album, e.g. when re-shown from the
   * Admin panel. Resets all transient search state and refreshes the displayed fields.
   */
  public void editAlbum(AlbumDto selectedAlbum, List<AlbumDto> invalidAlbumsList) {
    this.invalidAlbumsList = invalidAlbumsList;
    this.currentAlbum = selectedAlbum;
    if (invalidAlbumsList != null && selectedAlbum != null) {
      this.currentAlbumIndex = invalidAlbumsList.indexOf(selectedAlbum);
    } else {
      this.currentAlbumIndex = -1;
    }
    setStatus(null, ColorTheme.get().editAlbumStatusInfo);
    populateAlbumData();
  }

  @Override
  protected void paintComponent(Graphics g) {
    // Dim the underlying tab content so this overlay reads as modal
    g.setColor(ColorTheme.get().editAlbumModalDim);
    g.fillRect(0, 0, getWidth(), getHeight());
    super.paintComponent(g);
  }

  private void setStatus(String message, Color color) {
    if (lblGlobalStatus == null)
      return;
    if (message == null || message.isBlank()) {
      lblGlobalStatus.setText(" ");
    } else {
      lblGlobalStatus.setText(message);
    }
    lblGlobalStatus.setForeground(color);
  }

  private void evaluateUpdateMetadataButtonState() {
    String yearVal = tfResultReleaseDate.getText().trim();
    String labelVal = tfResultRecordLabel.getText().trim();
    boolean fieldsNotEmpty = !yearVal.isEmpty() || !labelVal.isEmpty();
    btnUpdateMeta.setEnabled(fieldsNotEmpty);
  }

  private void evaluateDownloadArtButtonState() {
    if (btnDownloadArt == null || tfCoverArtUrl == null)
      return;
    btnDownloadArt.setEnabled(!tfCoverArtUrl.getText().trim().isEmpty());
  }

  private void initLayout() {
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBackground(ColorTheme.get().editAlbumBgDark);
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    // 1. Header & Album Master Navigation Block
    JPanel topContainer = new JPanel(new BorderLayout());
    topContainer.setOpaque(false);
    topContainer.setBorder(new EmptyBorder(0, 0, 10, 0));

    lblTopHeader = new JLabel("Editing Album Metadata", SwingConstants.CENTER);
    lblTopHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeNavBtn));
    lblTopHeader.setForeground(ColorTheme.get().editAlbumTextLight);
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
    leftPanel.setBackground(ColorTheme.get().editAlbumCardBg);
    leftPanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(ColorTheme.get().editAlbumAccentBlue), "Current Properties",
        0, 0, null, ColorTheme.get().editAlbumTextLight));

    // Symmetric alignment offset padding
    leftPanel.add(Box.createVerticalStrut(51));

    // Cover Art Box
    lblCurrentCoverArt = new JLabel();
    lblCurrentCoverArt.setPreferredSize(
        new Dimension(LayoutTheme.get().editAlbumCoverSize, LayoutTheme.get().editAlbumCoverSize));
    lblCurrentCoverArt.setMinimumSize(
        new Dimension(LayoutTheme.get().editAlbumCoverSize, LayoutTheme.get().editAlbumCoverSize));
    lblCurrentCoverArt.setMaximumSize(
        new Dimension(LayoutTheme.get().editAlbumCoverSize, LayoutTheme.get().editAlbumCoverSize));
    lblCurrentCoverArt.setHorizontalAlignment(SwingConstants.CENTER);
    lblCurrentCoverArt
        .setBorder(BorderFactory.createLineBorder(ColorTheme.get().editAlbumBorderGray, 1));
    lblCurrentCoverArt.setBackground(ColorTheme.get().bgFieldDark);
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
    rightPanel.setBackground(ColorTheme.get().editAlbumCardBg);
    rightPanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(ColorTheme.get().editAlbumAccentBlue), "Internet Search", 0,
        0, null, ColorTheme.get().editAlbumTextLight));

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
    lblCoverArtCanvas.setPreferredSize(
        new Dimension(LayoutTheme.get().editAlbumCoverSize, LayoutTheme.get().editAlbumCoverSize));
    lblCoverArtCanvas.setMinimumSize(
        new Dimension(LayoutTheme.get().editAlbumCoverSize, LayoutTheme.get().editAlbumCoverSize));
    lblCoverArtCanvas.setMaximumSize(
        new Dimension(LayoutTheme.get().editAlbumCoverSize, LayoutTheme.get().editAlbumCoverSize));
    lblCoverArtCanvas.setHorizontalAlignment(SwingConstants.CENTER);
    lblCoverArtCanvas
        .setBorder(BorderFactory.createLineBorder(ColorTheme.get().editAlbumBorderGray, 1));
    lblCoverArtCanvas.setBackground(ColorTheme.get().bgFieldDark);
    lblCoverArtCanvas.setOpaque(true);
    lblCoverArtCanvas.setAlignmentX(CENTER_ALIGNMENT);
    rightCenterContainer.add(lblCoverArtCanvas);
    rightCenterContainer.add(Box.createVerticalStrut(8));

    // ── Cover Art URL strip (below canvas, always editable) ──────────────────
    // Uses GridBagLayout so the text field stretches and the two buttons stay fixed-width.
    JPanel coverUrlStrip = new JPanel(new GridBagLayout());
    coverUrlStrip.setOpaque(false);
    coverUrlStrip.setAlignmentX(CENTER_ALIGNMENT);
    coverUrlStrip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    GridBagConstraints gbcUrl = new GridBagConstraints();
    gbcUrl.insets = new Insets(0, 3, 0, 3);
    gbcUrl.fill = GridBagConstraints.HORIZONTAL;
    gbcUrl.gridy = 0;

    gbcUrl.gridx = 0;
    gbcUrl.weightx = 0.0;
    coverUrlStrip.add(createLabel("Cover Art URL:"), gbcUrl);

    gbcUrl.gridx = 1;
    gbcUrl.weightx = 1.0;
    tfCoverArtUrl = new JTextField();
    setupTextField(tfCoverArtUrl);
    tfCoverArtUrl
        .setToolTipText("Auto-populated from search results — or paste / type any image URL");
    tfCoverArtUrl.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        evaluateDownloadArtButtonState();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        evaluateDownloadArtButtonState();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        evaluateDownloadArtButtonState();
      }
    });
    coverUrlStrip.add(tfCoverArtUrl, gbcUrl);

    gbcUrl.gridx = 2;
    gbcUrl.weightx = 0.0;
    JButton btnBrowseArt = createStyledButton("Browse...", e -> doBrowseCoverArt());
    coverUrlStrip.add(btnBrowseArt, gbcUrl);

    gbcUrl.gridx = 3;
    JButton btnGoogle = createStyledButton("Google", e -> doGoogleSearch());
    coverUrlStrip.add(btnGoogle, gbcUrl);

    rightCenterContainer.add(coverUrlStrip);
    rightCenterContainer.add(Box.createVerticalStrut(8));

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
    resultsFormPanel.add(tfResultReleaseDate, gbcR);

    gbcR.gridx = 0;
    gbcR.gridy = 1;
    resultsFormPanel.add(createLabel("Record Label:"), gbcR);
    gbcR.gridx = 1;
    tfResultRecordLabel = new JTextField(15);
    setupTextField(tfResultRecordLabel);
    resultsFormPanel.add(tfResultRecordLabel, gbcR);

    DocumentListener fieldChangeListener = new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        evaluateUpdateMetadataButtonState();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        evaluateUpdateMetadataButtonState();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        evaluateUpdateMetadataButtonState();
      }
    };
    tfResultReleaseDate.getDocument().addDocumentListener(fieldChangeListener);
    tfResultRecordLabel.getDocument().addDocumentListener(fieldChangeListener);

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
    lblSearchStatus.setForeground(ColorTheme.get().editAlbumSearchStatusFg);
    lblSearchStatus
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminArtist));

    searchControlPanel.add(btnPrevResult);
    searchControlPanel.add(lblSearchStatus);
    searchControlPanel.add(btnNextResult);

    rightPanel.add(searchControlPanel, BorderLayout.SOUTH);
    centerSplitPanel.add(rightPanel);
    mainPanel.add(centerSplitPanel, BorderLayout.CENTER);

    // 3. Global action footer control panel
    JPanel footerOuter = new JPanel(new BorderLayout(0, 6));
    footerOuter.setOpaque(false);

    JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
    footerPanel.setOpaque(false);

    btnUpdateMeta = createStyledButton("Update Metadata", e -> doMetadataUpdate());
    btnDownloadArt = createStyledButton("Download Cover Art", e -> doCoverArtDownload());
    JButton btnCancel = createStyledButton("Cancel", e -> {
      if (onDismiss != null)
        onDismiss.run();
    });

    btnUpdateMeta.setEnabled(false);
    btnDownloadArt.setEnabled(false);

    footerPanel.add(btnUpdateMeta);
    footerPanel.add(btnDownloadArt);
    footerPanel.add(btnCancel);

    lblGlobalStatus = new JLabel(" ", SwingConstants.CENTER);
    lblGlobalStatus
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminSection - 1));
    lblGlobalStatus.setForeground(ColorTheme.get().editAlbumStatusInfo);

    footerOuter.add(footerPanel, BorderLayout.CENTER);
    footerOuter.add(lblGlobalStatus, BorderLayout.SOUTH);
    mainPanel.add(footerOuter, BorderLayout.SOUTH);

    mainPanel.setPreferredSize(
        new Dimension(LayoutTheme.get().editAlbumCardW, LayoutTheme.get().editAlbumCardH));
    add(mainPanel);
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

    // Use "Various Artists" instead of "Compilations" for better internet search results
    String searchArtistName = currentAlbum.getArtistName();
    if ("Compilations".equalsIgnoreCase(searchArtistName)) {
      searchArtistName = "Various Artists";
    }
    tfSearchArtist.setText(searchArtistName);
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

      evaluateUpdateMetadataButtonState();
      btnDownloadArt.setEnabled(false);
      tfCoverArtUrl.setText("");
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

    evaluateUpdateMetadataButtonState();
    boolean hasValidMetadata =
        !yearVal.isEmpty() && !labelVal.isEmpty() && urlStr != null && !urlStr.isBlank();
    btnDownloadArt.setEnabled(hasValidMetadata);

    // Populate the editable Cover Art URL field from the search result (Item #2)
    tfCoverArtUrl.setText(urlStr != null ? urlStr : "");
    evaluateDownloadArtButtonState();

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
      setStatus("Artist and Album text fields are required fields to query.",
          ColorTheme.get().editAlbumStatusWarn);
      return;
    }

    setStatus(null, ColorTheme.get().editAlbumStatusInfo);
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
            setStatus("No matches found on the web.", ColorTheme.get().editAlbumStatusInfo);
          }
          updateSearchResultUI();
        });
      } catch (Exception ex) {
        SwingUtilities.invokeLater(() -> {
          lblSearchStatus.setText("Search failed.");
          setStatus("Error executing lookup: " + ex.getMessage(),
              ColorTheme.get().editAlbumStatusError);
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

      String messageDetails = String.format("Updated — Year: %s | Label: %s | Explicit: %b",
          updatedYear, updatedLabel, updatedExplicit);

      setStatus(messageDetails, ColorTheme.get().editAlbumStatusSuccess);

    } catch (Exception e) {
      setStatus("Failed updating record metadata: " + e.getMessage(),
          ColorTheme.get().editAlbumStatusError);
    }
  }

  private void doCoverArtDownload() {

    if (currentAlbum == null)
      return;

    // Use the (possibly user-edited) Cover Art URL field rather than the raw search result (Item
    // #2)
    String liveArtUrlStr = tfCoverArtUrl.getText().trim();
    if (liveArtUrlStr.isEmpty()) {
      setStatus("No Cover Art URL specified. Enter or paste a URL in the Cover Art URL field.",
          ColorTheme.get().editAlbumStatusWarn);
      return;
    }

    try {

      // Constructs operational scan paths safely linked to the active track record context
      DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest =
          new DownloadAlbumCoverArtRequest(currentAlbum.getAlbumId(), liveArtUrlStr);

      songLibraryService.downloadAlbumCoverArt(downloadAlbumCoverArtRequest);

      setStatus("Cover art download requested via: " + liveArtUrlStr,
          ColorTheme.get().editAlbumStatusSuccess);

    } catch (Exception e) {
      setStatus("Failed downloading art asset payload: " + e.getMessage(),
          ColorTheme.get().editAlbumStatusError);
    }
  }

  private void doGoogleSearch() {

    String artistQuery = tfSearchArtist.getText().trim();
    String albumQuery = tfSearchAlbum.getText().trim();

    if (artistQuery.isEmpty() && albumQuery.isEmpty()) {
      setStatus("Enter an Artist and/or Album name before opening Google.",
          ColorTheme.get().editAlbumStatusWarn);
      return;
    }

    try {
      String searchTerms = (artistQuery + " " + albumQuery).trim();
      String encodedQuery =
          java.net.URLEncoder.encode(searchTerms, java.nio.charset.StandardCharsets.UTF_8);
      URI googleUri = URI.create("https://www.google.com/search?q=" + encodedQuery);

      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(googleUri);
        setStatus("Opened Google search for: " + searchTerms, ColorTheme.get().editAlbumStatusInfo);
      } else {
        setStatus("System browser launch is not supported on this platform.",
            ColorTheme.get().editAlbumStatusWarn);
      }

    } catch (Exception ex) {
      setStatus("Failed to open browser: " + ex.getMessage(),
          ColorTheme.get().editAlbumStatusError);
    }
  }

  private void doBrowseCoverArt() {

    if (currentAlbum == null) {
      setStatus("No album selected.", ColorTheme.get().editAlbumStatusWarn);
      return;
    }

    String coverArtPath = currentAlbum.getCoverArtPath();
    if (coverArtPath == null || coverArtPath.isBlank()) {
      setStatus("Current album has no cover art path defined — cannot write cover.jpg.",
          ColorTheme.get().editAlbumStatusWarn);
      return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select Cover Art Image");
    chooser
        .setFileFilter(new FileNameExtensionFilter("Image files (JPG, PNG)", "jpg", "jpeg", "png"));
    chooser.setAcceptAllFileFilterUsed(false);

    int result = chooser.showOpenDialog(this);
    if (result != JFileChooser.APPROVE_OPTION)
      return;

    File selectedFile = chooser.getSelectedFile();

    new Thread(() -> {
      try {

        BufferedImage sourceImage = ImageIO.read(selectedFile);
        if (sourceImage == null) {
          SwingUtilities.invokeLater(() -> setStatus("Could not read selected image file.",
              ColorTheme.get().editAlbumStatusError));
          return;
        }

        // Resize to 500×500
        BufferedImage resized = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(sourceImage, 0, 0, 500, 500, null);
        g2d.dispose();

        // Derive destination: same directory as the album's current coverArtPath, named "cover.jpg"
        File coverArtFile = new File(coverArtPath);
        File destFile = new File(coverArtFile.getParentFile(), "cover.jpg");

        ImageIO.write(resized, "jpg", destFile);

        // Refresh the left-panel preview with the newly written image
        Image scaled = resized.getScaledInstance(250, 250, Image.SCALE_SMOOTH);
        ImageIcon icon = new ImageIcon(scaled);
        SwingUtilities.invokeLater(() -> {
          lblCurrentCoverArt.setIcon(icon);
          lblCurrentCoverArt.setText("");
          setStatus("Cover art saved to: " + destFile.getAbsolutePath(),
              ColorTheme.get().editAlbumStatusSuccess);
        });

      } catch (Exception ex) {
        SwingUtilities.invokeLater(() -> setStatus("Failed writing cover art: " + ex.getMessage(),
            ColorTheme.get().editAlbumStatusError));
      }
    }).start();
  }

  // ── Helper UI Styling Methods ──────────────────────────────────────────────
  private JLabel createLabel(String text) {
    JLabel lbl = new JLabel(text);
    lbl.setForeground(ColorTheme.get().editAlbumTextLight);
    lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminArtist));
    return lbl;
  }

  private void setupTextField(JTextField tf) {
    tf.setBackground(ColorTheme.get().editAlbumBgDark);
    tf.setForeground(ColorTheme.get().textPrimary);
    tf.setCaretColor(ColorTheme.get().textPrimary);
    tf.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ColorTheme.get().editAlbumBorderGray, 1),
        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
  }

  private void setupCheckBox(JCheckBox cb) {
    cb.setOpaque(false);
    cb.setForeground(ColorTheme.get().editAlbumTextLight);
    cb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeAdminArtist));
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
          g2.setColor(ColorTheme.get().editAlbumCardBg.brighter());
          g2.fillRoundRect(0, 0, w, h, 8, 8);
          g2.setColor(ColorTheme.get().editAlbumBtnDisabledBorder);
          g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
          g2.setFont(getFont());
          g2.setColor(ColorTheme.get().editAlbumBorderGray);
          java.awt.FontMetrics fm = g2.getFontMetrics();
          g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
              (h - fm.getHeight()) / 2 + fm.getAscent());
          g2.dispose();
          return;
        }

        Color top = hovered ? ColorTheme.get().editAlbumGradTop.brighter()
            : ColorTheme.get().editAlbumGradTop;
        Color bot = hovered ? ColorTheme.get().editAlbumGradBottom.brighter()
            : ColorTheme.get().editAlbumGradBottom;
        g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
        g2.fillRoundRect(0, 0, w, h, 8, 8);
        g2.setColor(ColorTheme.get().editAlbumAccentBlue);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        g2.setFont(getFont());
        g2.setColor(ColorTheme.get().textPrimary);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
            (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
      }
    };
    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAdminArtist));
    btn.setForeground(ColorTheme.get().textPrimary);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setOpaque(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(action);
    return btn;
  }
}
