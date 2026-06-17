package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;

/**
 * Centralized color palette for all JukeANator UI components.
 *
 * <p>
 * This class is the single source of truth for every {@link Color} constant used across the UI
 * layer. It is designed as a singleton so that it can eventually be populated at startup from a
 * properties file (e.g. {@code application.yml}), enabling full support for multiple themes,
 * screen-resolution profiles, and a future Portrait Mode.
 *
 * <p>
 * <b>Usage:</b>
 *
 * <pre>
 * Color c = ColorTheme.get().accentBlue;
 * </pre>
 *
 * <p>
 * <b>Design notes:</b>
 * <ul>
 * <li>All fields are {@code public final} so they read like named constants at call-sites while
 * still being instance members that can be swapped by loading a different {@code ColorTheme}
 * instance at startup.</li>
 * <li>Color groups are delimited by section comments that mirror the original per-class
 * declarations, making it straightforward to trace every constant back to its origin.</li>
 * <li>When YML support is added, replace the default field initialisers below with values injected
 * by Spring (or read from a {@code Properties} object) inside {@link #ColorTheme()} or a static
 * factory method.</li>
 * </ul>
 *
 * @see LayoutTheme
 */
public final class ColorTheme {

  // ── Singleton ──────────────────────────────────────────────────────────────

  private static volatile ColorTheme instance;

  /** Returns the singleton instance, creating it on first call. */
  public static ColorTheme get() {
    if (instance == null) {
      synchronized (ColorTheme.class) {
        if (instance == null) {
          instance = new ColorTheme();
        }
      }
    }
    return instance;
  }

  /**
   * Replaces the singleton with a pre-built instance.
   *
   * <p>
   * Intended for startup configuration — call this once from {@code main} (or a Spring
   * {@code @PostConstruct} method) before any UI component is constructed.
   *
   * @param theme the fully configured {@link ColorTheme} to install
   */
  public static void install(ColorTheme theme) {
    synchronized (ColorTheme.class) {
      instance = theme;
    }
  }

  // ── Constructor ────────────────────────────────────────────────────────────

  /**
   * Creates a {@code ColorTheme} with the default 1920×1080 landscape-mode palette.
   *
   * <p>
   * To support a different theme, sub-class or compose this class and pass the result to
   * {@link #install(ColorTheme)}.
   */
  public ColorTheme() {
    // Default values are set inline on every field below.
    // A future YML-driven constructor would read these from a config map instead.
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CORE ACCENT PALETTE
  // Used across nearly every component as primary interactive and branding colors.
  // Origin: AlbumGridPanel, AlbumDetailCard, AlbumViewCard, ButtonFactory,
  // AddSongToQueueCard, SongQueueCard, SearchPanel, KeyboardPanel,
  // LoginToAdminPanelCard, AdminPanel, ResultsColumnPanel, DetailHeaderPanel,
  // GenrePanel, JukeANatorFrame
  // ═══════════════════════════════════════════════════════════════════════════

  /** Primary cyan accent — borders, hover highlights, active states, progress bars. */
  public final Color accentBlue = new Color(0, 210, 255);

  /** Gold accent — credit displays, cost labels, admin header, priority play buttons. */
  public final Color accentGold = new Color(255, 200, 0);

  /** Green accent — popularity bars, queue section header, queue/add action buttons. */
  public final Color accentGreen = new Color(60, 210, 80);

  /** Red accent — warning/error states, remove buttons, admin exit. */
  public final Color accentRed = new Color(220, 60, 60);

  /** Orange accent — admin reset-stats button. */
  public final Color accentOrange = new Color(255, 140, 0);

  /** Violet accent — admin randomize/rescan buttons. */
  public final Color accentViolet = new Color(180, 80, 255);

  /** Red accent for explicit-content badges and AMI warning borders. */
  public final Color accentExplicit = new Color(220, 60, 60);

  // ═══════════════════════════════════════════════════════════════════════════
  // TEXT
  // Origin: virtually all components
  // ═══════════════════════════════════════════════════════════════════════════

  /** Primary foreground — song names, artist names, main labels. */
  public final Color textPrimary = Color.WHITE;

  /** Secondary / muted foreground — sub-labels, metadata, artist sub-text. */
  public final Color textSecondary = new Color(180, 180, 180);

  /** Muted text variant used in AdminPanel list sub-labels. */
  public final Color textMuted = new Color(160, 165, 180);

  // ═══════════════════════════════════════════════════════════════════════════
  // BACKGROUNDS
  // Origin: AddSongToQueueCard, SongQueueCard, AdminPanel, EditAlbumCard
  // ═══════════════════════════════════════════════════════════════════════════

  /** Dark panel background for overlay cards (AddSongToQueueCard, SongQueueCard). */
  public final Color bgOverlayCard = new Color(22, 22, 28);

  /** List background — very dark near-black. */
  public final Color bgList = new Color(10, 12, 18);

  /** Selected row background in lists — dark teal. */
  public final Color bgListSelected = new Color(0, 60, 80);

  /** Alternating row tint for unselected list rows. */
  public final Color bgListRowAlt = new Color(18, 20, 28);

  /** Row hover overlay in ResultsColumnPanel / AlbumViewCard track rows. */
  public final Color bgRowHover = new Color(255, 255, 255, 25);

  /**
   * Thumbnail background for cover-art placeholders in result rows. Origin: ResultsColumnPanel
   */
  public final Color bgThumb = new Color(40, 40, 55);

  /**
   * Dark background used for the EditAlbumCard outer panel. Origin: EditAlbumCard
   */
  public final Color bgEditCardDark = new Color(26, 26, 36);

  /**
   * Card background inside the EditAlbumCard panels. Origin: EditAlbumCard
   */
  public final Color bgEditCardPanel = new Color(36, 36, 50);

  /** Cover-art label background in AddSongToQueueCard. */
  public final Color bgCoverArtPlaceholder = new Color(30, 30, 40);

  /**
   * Header bar background in AdminPanel. Origin: AdminPanel#buildHeaderBar
   */
  public final Color bgAdminHeader = new Color(8, 8, 14);

  /**
   * Opaque dark fill for login/admin field backgrounds. Origin: LoginToAdminPanelCard, SearchPanel
   * search bar.
   */
  public final Color bgFieldDark = Color.BLACK;

  /**
   * Frosted-glass resting overlay — tile / hero backgrounds. Origin: AlbumGridPanel tiles,
   * GenrePanel tiles, SearchPanel hero, KeyboardPanel wrapper
   */
  public final Color bgFrostedGlassRest = new Color(255, 255, 255, 15);

  /**
   * Frosted-glass hover overlay — tile hover state. Origin: AlbumGridPanel tiles, GenrePanel tiles
   */
  public final Color bgFrostedGlassHover = new Color(255, 255, 255, 30);

  /**
   * Frosted-glass perimeter highlight ring (resting). Origin: AlbumGridPanel tiles, GenrePanel
   * tiles, DetailHeaderPanel
   */
  public final Color bgFrostedGlassRing = new Color(255, 255, 255, 35);

  // ═══════════════════════════════════════════════════════════════════════════
  // SEPARATORS / BORDERS
  // Origin: AlbumViewCard, AdminPanel, DetailHeaderPanel, SongQueueCard
  // ═══════════════════════════════════════════════════════════════════════════

  /** General separator / border line. */
  public final Color colorBorder = new Color(60, 60, 80);

  /** AlbumViewCard track-row separator. */
  public final Color colorSeparator = new Color(50, 50, 65);

  /**
   * AdminPanel separator between columns and button strips. Also used for scroll-pane borders and
   * list borders.
   */
  public final Color colorAdminSeparator = new Color(40, 44, 60);

  /** Row separator line inside ResultsColumnPanel columns. */
  public final Color colorColumnSeparator = new Color(255, 255, 255, 25);

  // ═══════════════════════════════════════════════════════════════════════════
  // NAVIGATION BUTTON GRADIENT (ButtonFactory / AlbumDetailCard / DetailHeaderPanel)
  // A consistent set used for the standard "← BACK" and page-nav buttons.
  // ═══════════════════════════════════════════════════════════════════════════

  /** Top of the navigation button gradient (idle). */
  public final Color navBtnGradTop = new Color(0, 160, 210);

  /** Bottom of the navigation button gradient (idle). */
  public final Color navBtnGradBottom = new Color(0, 80, 130);

  /** Top of the navigation button gradient (hover). */
  public final Color navBtnHoverTop = new Color(0, 190, 240);

  /** Bottom of the navigation button gradient (hover). */
  public final Color navBtnHoverBottom = new Color(0, 100, 160);

  // ═══════════════════════════════════════════════════════════════════════════
  // SORT BUTTON PALETTE (GenreDetailPanel)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Sort button gradient top when active. */
  public final Color sortBtnActiveTop = new Color(0, 160, 210);

  /** Sort button gradient bottom when active. */
  public final Color sortBtnActiveBottom = new Color(0, 80, 130);

  /** Sort button flat background when idle. */
  public final Color sortBtnIdleBg = new Color(28, 28, 42);

  /** Sort button border color when active. */
  public final Color sortBtnBorderActive = new Color(0, 210, 255); // == accentBlue

  /** Sort button border color when idle. */
  public final Color sortBtnBorderIdle = new Color(60, 60, 80);

  /** Sort button text color when active. */
  public final Color sortTextActive = Color.WHITE;

  /** Sort button text color when idle. */
  public final Color sortTextIdle = new Color(160, 165, 180);

  // ═══════════════════════════════════════════════════════════════════════════
  // AMI 3D BUTTON PALETTE
  // The deep-blue 3-D extruded button style used by AddSongToQueueCard,
  // SongQueueCard, AdminPanel side buttons, and EditAlbumCard.
  // ═══════════════════════════════════════════════════════════════════════════

  /** 3-D button face gradient — top. */
  public final Color btn3dFaceTop = new Color(28, 45, 72);

  /** 3-D button face gradient — mid-point. */
  public final Color btn3dFaceMid = new Color(18, 32, 54);

  /** 3-D button face gradient — bottom. */
  public final Color btn3dFaceBottom = new Color(10, 18, 34);

  /** 3-D button shelf band (physical-depth illusion). */
  public final Color btn3dShelf = new Color(6, 10, 20);

  /** 3-D button drop-shadow layer. */
  public final Color btn3dShadow = new Color(2, 4, 10);

  /** 3-D button specular top-edge highlight. */
  public final Color btn3dHighlight = new Color(80, 140, 210, 200);

  /** 3-D button side-edge sheen. */
  public final Color btn3dSide = new Color(40, 80, 130, 90);

  // Hover state face gradient (brightened face when cursor is over an enabled button)
  /** 3-D button hover face gradient — top. */
  public final Color btn3dHoverTop = new Color(40, 65, 105);

  /** 3-D button hover face gradient — mid-point. */
  public final Color btn3dHoverMid = new Color(28, 50, 84);

  /** 3-D button hover face gradient — bottom. */
  public final Color btn3dHoverBottom = new Color(16, 30, 56);

  // Warning state (insufficient credits / error)
  /** 3-D warning button face — top. */
  public final Color btn3dWarnTop = new Color(55, 10, 10);

  /** 3-D warning button face — mid. */
  public final Color btn3dWarnMid = new Color(38, 6, 6);

  /** 3-D warning button face — bottom. */
  public final Color btn3dWarnBottom = new Color(22, 3, 3);

  /** 3-D warning button shelf. */
  public final Color btn3dWarnShelf = new Color(12, 2, 2);

  /** 3-D warning button border. */
  public final Color btn3dWarnBorder = new Color(220, 40, 40);

  /**
   * Warning-state specular top-edge highlight (semi-transparent red). Origin: AddSongToQueueCard
   * queue button paintComponent — warn specular line.
   */
  public final Color btn3dWarnSpecular = new Color(200, 60, 60, 160);

  /**
   * Warning-state side-edge sheen (dim transparent red). Origin: AddSongToQueueCard queue button
   * paintComponent — warn side sheen lines.
   */
  public final Color btn3dWarnSide = new Color(160, 30, 30, 70);

  // Grey / disabled state (SongQueueCard action buttons when nothing is selected)
  /** Disabled / greyed action button face. */
  public final Color btnGreyFace = new Color(35, 35, 42);

  /** Disabled / greyed action button border. */
  public final Color btnGreyBorder = new Color(65, 65, 75);

  /** Disabled / greyed action button text. */
  public final Color btnGreyText = new Color(90, 90, 100);

  // ═══════════════════════════════════════════════════════════════════════════
  // KEYBOARD KEY PALETTE (KeyboardPanel)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Keyboard key face gradient — top. */
  public final Color keyFaceTop = new Color(72, 76, 88);

  /** Keyboard key face gradient — mid. */
  public final Color keyFaceMid = new Color(52, 55, 65);

  /** Keyboard key face gradient — bottom. */
  public final Color keyFaceBottom = new Color(35, 37, 45);

  /** Keyboard key shelf band. */
  public final Color keyShelf = new Color(18, 18, 22);

  /** Keyboard key drop-shadow. */
  public final Color keyShadow = new Color(6, 6, 8);

  /** Keyboard key specular top-edge highlight. */
  public final Color keyHighlight = new Color(160, 162, 175, 200);

  /** Keyboard key side-edge sheen. */
  public final Color keySide = new Color(100, 102, 115, 80);

  /** Active keyboard mode button background (ABC / 123 toggle). */
  public final Color keyActiveModeBackground = new Color(0, 160, 200);

  // ═══════════════════════════════════════════════════════════════════════════
  // EDIT ALBUM CARD PALETTE (EditAlbumCard)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Text colour for EditAlbumCard labels. */
  public final Color editTextLight = new Color(230, 230, 240);

  /** Accent blue used in EditAlbumCard (slightly different shade — Material design influence). */
  public final Color editAccentBlue = new Color(52, 152, 219);

  /** EditAlbumCard button gradient — top. */
  public final Color editGradTop = new Color(44, 62, 80);

  /** EditAlbumCard button gradient — bottom. */
  public final Color editGradBottom = new Color(22, 32, 43);

  // Inline status label colours
  /** Status label colour — informational. */
  public final Color statusInfo = new Color(120, 200, 255);

  /** Status label colour — warning. */
  public final Color statusWarn = new Color(255, 190, 60);

  /** Status label colour — error. */
  public final Color statusError = new Color(230, 90, 90);

  /** Status label colour — success. */
  public final Color statusSuccess = new Color(110, 220, 130);

  // ═══════════════════════════════════════════════════════════════════════════
  // LOGIN CARD PALETTE (LoginToAdminPanelCard)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Error / validation message colour in the login card. */
  public final Color loginError = new Color(220, 60, 60);

  /** Admin login title colour (gold-yellow). */
  public final Color loginTitleColor = new Color(255, 220, 0);

  // ═══════════════════════════════════════════════════════════════════════════
  // FRAME / APP-LEVEL BACKGROUND GRADIENT (JukeANatorFrame, overlayRoot)
  // The six-stop diagonal rainbow gradient shared by the content pane,
  // the overlay card root, and the tab content border.
  // ═══════════════════════════════════════════════════════════════════════════

  /** App background diagonal gradient — base dark fill. */
  public final Color appBgBase = new Color(10, 10, 10);

  /** App background gradient stop 0 — deep red, top-left. */
  public final Color appGradStop0 = new Color(140, 50, 50, 90);

  /** App background gradient stop 1 — amber. */
  public final Color appGradStop1 = new Color(140, 90, 30, 80);

  /** App background gradient stop 2 — olive. */
  public final Color appGradStop2 = new Color(80, 110, 40, 70);

  /** App background gradient stop 3 — teal. */
  public final Color appGradStop3 = new Color(30, 100, 110, 70);

  /** App background gradient stop 4 — blue. */
  public final Color appGradStop4 = new Color(40, 60, 140, 80);

  /** App background gradient stop 5 — violet, bottom-right. */
  public final Color appGradStop5 = new Color(100, 30, 140, 90);

  /**
   * Convenience array of the six gradient color stops in order. Matches the {@code float[]}
   * fractions {@code {0.0f, 0.20f, 0.42f, 0.62f, 0.82f, 1.0f}}.
   */
  public Color[] appGradColors() {
    return new Color[] {appGradStop0, appGradStop1, appGradStop2, appGradStop3, appGradStop4,
        appGradStop5};
  }

  /** The six fraction stops for the app background gradient. */
  public float[] appGradFractions() {
    return new float[] {0.0f, 0.20f, 0.42f, 0.62f, 0.82f, 1.0f};
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // RESULT COLUMN BODY GRADIENT (ResultsColumnPanel, AlbumViewCard body)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Column body gradient — top stop (dark blue-tinted). */
  public final Color columnGradTop = new Color(24, 38, 60, 225);

  /** Column body gradient — bottom stop (very dark blue). */
  public final Color columnGradBottom = new Color(12, 18, 30, 245);

  // ═══════════════════════════════════════════════════════════════════════════
  // MISC / ONE-OFF
  // ═══════════════════════════════════════════════════════════════════════════

  /** Divider line color in AddSongToQueueCard and SongQueueCard. */
  public final Color dividerLine = new Color(80, 80, 100);

  /** Cover-art placeholder border in AddSongToQueueCard. */
  public final Color coverArtBorder = new Color(80, 80, 90);

  /** Cover-art placeholder border inside SongQueueCard now-playing card. */
  public final Color coverArtBorderDim = new Color(60, 60, 75);

  /** Timeout progress bar track background (AddSongToQueueCard, SongQueueCard). */
  public final Color timeoutBarTrack = new Color(40, 40, 55);

  /** AdminPanel album cell thumbnail background. */
  public final Color adminThumbBg = new Color(24, 26, 38);

  /** AdminPanel filter text-field background. */
  public final Color adminFilterFieldBg = new Color(18, 20, 30);

  /**
   * AdminPanel scroll bar background. Origin: AdminPanel#darkScrollPane —
   * sp.getVerticalScrollBar().setBackground
   */
  public final Color adminScrollBarBg = new Color(20, 20, 30);

  /**
   * AdminPanel side-button drop-shadow slab color. Origin: AdminPanel#sideButton paintComponent —
   * drop-shadow step.
   */
  public final Color adminSideBtnShadow = new Color(2, 2, 6);

  /**
   * AdminPanel side-button shelf band color. Origin: AdminPanel#sideButton paintComponent — shelf
   * step.
   */
  public final Color adminSideBtnShelf = new Color(6, 6, 12);

  /**
   * Semi-transparent white tint painted over a disabled track-nav button caret. Origin:
   * AlbumViewCard#trackNavButton paintComponent — disabled state color.
   */
  public final Color trackNavDisabledTint = new Color(255, 255, 255, 40);

  // ═══════════════════════════════════════════════════════════════════════════
  // DETAIL HEADER PANEL PALETTE (DetailHeaderPanel)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Matte border line drawn below DetailHeaderPanel and used as the idle/rest border on sort
   * buttons in GenreDetailPanel. Origin: DetailHeaderPanel COLOR_BORDER; GenreDetailPanel
   * SORT_BTN_BORDER_IDLE
   */
  public final Color detailHeaderBorder = new Color(60, 60, 80);

  /**
   * Background fill of the image/icon label inside DetailHeaderPanel when no image has been loaded
   * yet. Origin: DetailHeaderPanel — imageLabel.setBackground
   */
  public final Color detailHeaderImageBg = new Color(20, 20, 32);

  /**
   * Foreground color of the fallback text label shown inside the image slot of DetailHeaderPanel
   * when no icon is available. Origin: DetailHeaderPanel — imageLabel.setForeground (fallback
   * branch)
   */
  public final Color detailHeaderFallbackFg = new Color(100, 100, 120);

  // ═══════════════════════════════════════════════════════════════════════════
  // SORT BUTTON PALETTE (GenreDetailPanel)
  // ═══════════════════════════════════════════════════════════════════════════

  // ═══════════════════════════════════════════════════════════════════════════
  // EDIT ALBUM CARD PALETTE (EditAlbumCard)
  // Colors used exclusively by the admin-facing album metadata editing overlay.
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Main panel background — darkest layer of the edit card. Origin: EditAlbumCard BG_DARK
   */
  public final Color editAlbumBgDark = new Color(26, 26, 36);

  /**
   * Left / right sub-panel background — slightly lighter than the main bg. Origin: EditAlbumCard
   * CARD_BG
   */
  public final Color editAlbumCardBg = new Color(36, 36, 50);

  /**
   * Primary label / form text color inside the edit card. Origin: EditAlbumCard TEXT_LIGHT
   */
  public final Color editAlbumTextLight = new Color(230, 230, 240);

  /**
   * Accent blue used for button borders and titled-border outlines inside the edit card. NOTE:
   * intentionally different from the main UI accentBlue (0,210,255) — this is a softer, more muted
   * blue suited to the dense admin form layout. Origin: EditAlbumCard ACCENT_BLUE = new Color(52,
   * 152, 219)
   */
  public final Color editAlbumAccentBlue = new Color(52, 152, 219);

  /**
   * Top color of the styled button gradient inside the edit card (idle state). Origin:
   * EditAlbumCard GRAD_TOP
   */
  public final Color editAlbumGradTop = new Color(44, 62, 80);

  /**
   * Bottom color of the styled button gradient inside the edit card (idle state). Origin:
   * EditAlbumCard GRAD_BOTTOM
   */
  public final Color editAlbumGradBottom = new Color(22, 32, 43);

  /**
   * Semi-transparent black overlay painted by EditAlbumCard over the underlying tab content to
   * produce a modal-dimming effect. Origin: EditAlbumCard paintComponent — new Color(0, 0, 0, 160)
   */
  public final Color editAlbumModalDim = new Color(0, 0, 0, 160);

  /**
   * Status banner color — informational message. Origin: EditAlbumCard STATUS_INFO
   */
  public final Color editAlbumStatusInfo = new Color(120, 200, 255);

  /**
   * Status banner color — warning message. Origin: EditAlbumCard STATUS_WARN
   */
  public final Color editAlbumStatusWarn = new Color(255, 190, 60);

  /**
   * Status banner color — error message. Origin: EditAlbumCard STATUS_ERROR
   */
  public final Color editAlbumStatusError = new Color(230, 90, 90);

  /**
   * Status banner color — success message. Origin: EditAlbumCard STATUS_SUCCESS
   */
  public final Color editAlbumStatusSuccess = new Color(110, 220, 130);

  /**
   * Border color of text fields inside the edit card (maps to Color.GRAY). Origin: EditAlbumCard
   * setupTextField — createLineBorder(Color.GRAY, 1)
   */
  public final Color editAlbumFieldBorder = Color.GRAY;

  /**
   * Foreground of the "No search performed" / result count label (maps to Color.LIGHT_GRAY).
   * Origin: EditAlbumCard lblSearchStatus.setForeground(Color.LIGHT_GRAY)
   */
  public final Color editAlbumSearchStatusFg = Color.LIGHT_GRAY;

  /**
   * Border stroke color of a disabled styled button (maps to Color.DARK_GRAY). Origin:
   * EditAlbumCard createStyledButton paintComponent — disabled branch border
   */
  public final Color editAlbumBtnDisabledBorder = Color.DARK_GRAY;

  /**
   * Text color of a disabled styled button (maps to Color.GRAY). Origin: EditAlbumCard
   * createStyledButton paintComponent — disabled branch text
   */
  public final Color editAlbumBtnDisabledText = Color.GRAY;

  /** DetailHeaderPanel image placeholder background. */
  public final Color headerImagePlaceholderBg = new Color(20, 20, 32);

  /** AlbumViewCard — cover-art area / sidebar placeholder. */
  public final Color sidebarPlaceholderFg = new Color(80, 80, 100);

  // ═══════════════════════════════════════════════════════════════════════════
  // FRAME-LEVEL PALETTE (JukeANatorFrame)
  // Colors that are specific to the top-level application frame and its tab
  // bar — not shared with any card or panel component.
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Background of the location-logo label in the credits panel (top-left corner). Origin:
   * JukeANatorFrame#buildTopPanel — locationLogo.setBackground
   */
  public final Color frameLocationLogoBg = new Color(20, 20, 28);

  /**
   * Foreground color of the "CREDITS: N" title label. Origin: JukeANatorFrame#buildTopPanel —
   * creditsTitle.setForeground
   */
  public final Color frameCreditsTitleColor = Color.YELLOW;

  /**
   * Fully-transparent black used for the JTabbedPane background and the two UIManager transparency
   * keys so the frame gradient shows through the tab area. Origin:
   * JukeANatorFrame#buildContentPanelTabs — tabs.setBackground and UIManager puts
   */
  public final Color frameTabsTransparent = new Color(0, 0, 0, 0);

  /**
   * Semi-transparent black fill painted behind unselected tab headers. Origin:
   * JukeANatorFrame.JukeboxTabComponent#paintTabBackground (isSelected == false)
   */
  public final Color frameTabBgUnselected = new Color(0, 0, 0, 120);

  /**
   * Accent color for the HOME tab icon and label. Origin: JukeANatorFrame#buildContentPanelTabs —
   * JukeboxTabComponent("HOME", ...)
   */
  public final Color frameTabAccentHome = new Color(255, 120, 120);

  /**
   * Accent color for the SEARCH tab icon and label. Origin: JukeANatorFrame#buildContentPanelTabs —
   * JukeboxTabComponent("SEARCH", ...)
   */
  public final Color frameTabAccentSearch = new Color(0, 220, 255);

  /**
   * Accent color for the HOT HERE tab icon and label. Origin: JukeANatorFrame#buildContentPanelTabs
   * — JukeboxTabComponent("HOT HERE", ...)
   */
  public final Color frameTabAccentHotHere = new Color(255, 80, 120);

  /**
   * Accent color for the QUEUE tab icon and label. Origin: JukeANatorFrame#buildContentPanelTabs —
   * JukeboxTabComponent("QUEUE", ...)
   */
  public final Color frameTabAccentQueue = new Color(140, 255, 140);

  /**
   * Foreground of the fallback text label in DetailHeaderPanel when no image is loaded. Origin:
   * DetailHeaderPanel — imageLabel.setForeground (fallback text)
   */
  public final Color detailHeaderImageFg = new Color(100, 100, 120);
  
  /**
   * Gray border used on cover art preview labels in EditAlbumCard.
   * Origin: EditAlbumCard — lblCurrentCoverArt and lblCoverArtCanvas BorderFactory.createLineBorder(Color.GRAY)
   */
  public final Color editAlbumBorderGray = Color.GRAY;  
}
