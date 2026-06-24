package com.djt.jukeanator_engine.ui.components;

import java.awt.Dimension;

/**
 * Centralized layout, sizing, and pagination constants for all JukeANator UI components.
 *
 * <p>
 * This class is the single source of truth for every pixel size, grid dimension, page count, and
 * spacing value used across the UI layer. It is designed as a singleton so that it can eventually
 * be populated at startup from a properties file (e.g. {@code application.yml}), enabling full
 * support for multiple screen resolutions and a future <em>Portrait Mode</em> (height &gt; width)
 * in addition to the current <em>Landscape Mode</em> (1920 × 1080).
 *
 * <p>
 * <b>Usage:</b>
 *
 * <pre>
 * int cols = LayoutTheme.get().homeGridCols;
 * Dimension sz = LayoutTheme.get().navBtnSize;
 *
 * // Resolution- and orientation-aware grid dimensions for the Home screen:
 * LayoutTheme.GridProfile gp = LayoutTheme.get().homeGridProfile(screenW, screenH);
 * int cols = gp.cols();
 * int rows = gp.rows();
 * int artW = gp.artW();
 * int artH = gp.artH();
 * </pre>
 *
 * <p>
 * <b>Design notes:</b>
 * <ul>
 * <li>All fields are {@code public final} so they read like named constants at call-sites while
 * still being instance members replaceable at startup.</li>
 * <li>Derived {@link Dimension} fields are computed once from the primitive fields during
 * construction to avoid repeated allocation at every paint cycle.</li>
 * <li>Sections correspond to the original per-class constant declarations; each one notes its
 * source files so the mapping is auditable.</li>
 * <li>When YML support is added, replace the default field initialisers with values injected by
 * Spring (or read from a {@code Properties} object) inside {@link #LayoutTheme()} or a static
 * factory method.</li>
 * </ul>
 *
 * @see ColorTheme
 */
public final class LayoutTheme {

  // ── Singleton ──────────────────────────────────────────────────────────────

  private static volatile LayoutTheme instance;

  /** Returns the singleton instance, creating it on first call. */
  public static LayoutTheme get() {
    if (instance == null) {
      synchronized (LayoutTheme.class) {
        if (instance == null) {
          instance = new LayoutTheme();
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
   * @param theme the fully configured {@link LayoutTheme} to install
   */
  public static void install(LayoutTheme theme) {
    synchronized (LayoutTheme.class) {
      instance = theme;
    }
  }

  // ── Constructor ────────────────────────────────────────────────────────────

  /**
   * Creates a {@code LayoutTheme} with the default 1920×1080 landscape-mode sizes.
   *
   * <p>
   * To support a different resolution or orientation (e.g. 1080×1920 portrait), create a sub-class
   * or extend this class, override the relevant fields, and pass the result to
   * {@link #install(LayoutTheme)}.
   */
  public LayoutTheme() {
    // Derived Dimension fields must be initialised after the primitives they depend on.
    navBtnSize = new Dimension(navBtnW, navBtnH);
    adminBtnSize = new Dimension(adminBtnW, adminBtnH);
    detailHeaderImageSize = new Dimension(detailHeaderImageW, detailHeaderImageH);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CANONICAL RESOLUTION
  // The grid-profile calculations below are expressed in terms of this
  // reference resolution so that all derived values stay consistent even as
  // the design evolves.
  // ═══════════════════════════════════════════════════════════════════════════

  /** Canonical screen width on which all base layout constants were designed. */
  public static final int CANONICAL_W = 1920;

  /** Canonical screen height on which all base layout constants were designed. */
  public static final int CANONICAL_H = 1080;

  // ═══════════════════════════════════════════════════════════════════════════
  // GRID PROFILE — resolution- and orientation-aware album grid parameters
  //
  // A GridProfile captures the four values that control the album grid
  // (cols, rows, artW, artH) for a specific (screenW, screenH) combination.
  // Call LayoutTheme.get().homeGridProfile(screenW, screenH) once at startup
  // (or whenever the display configuration changes) and pass the result to
  // HomePanel / AlbumGridPanel instead of the static constants.
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Immutable value-object that bundles the four album-grid dimensions.
   *
   * <p>
   * Obtain instances via {@link LayoutTheme#homeGridProfile(int, int)} rather than constructing
   * directly.
   */
  public record GridProfile(int cols, int rows, int artW, int artH) {

    /** Total albums visible on one page ({@code cols × rows}). */
    public int pageSize() {
      return cols * rows;
    }
  }

  /**
   * Computes a {@link GridProfile} for the given screen dimensions, scaling relative to the
   * canonical 1920 × 1080 landscape resolution.
   *
   * <h3>Algorithm</h3>
   * <ol>
   * <li><b>Orientation check</b> — portrait mode is detected when {@code screenH > screenW}.</li>
   * <li><b>Scale factor</b> — {@code scale = min(screenW / 1920.0, screenH / 1080.0)}, clamped to
   * [{@value #GRID_SCALE_MIN}, {@value #GRID_SCALE_MAX}] to prevent comically small or large tiles
   * on extreme displays.</li>
   * <li><b>Art size</b> — the base art dimensions ({@value #homeArtW} × {@value #homeArtH} px) are
   * multiplied by {@code scale} and rounded to the nearest even integer so images scale
   * cleanly.</li>
   * <li><b>Grid dimensions</b> — the available content area (screen minus the fixed top panel, tab
   * bar, letter-nav strip, and album-text area below each tile) is divided by the scaled tile size
   * to derive cols/rows. In portrait mode the tile size is further reduced so that more rows fit
   * vertically.</li>
   * </ol>
   *
   * <h3>Portrait-mode layout strategy</h3> In portrait mode ({@code screenH > screenW}):
   * <ul>
   * <li>The art size is scaled against the portrait "short axis" (the width), so tiles are always
   * smaller than in landscape — this is correct because the short-axis constraint is tighter in
   * portrait.</li>
   * <li>Columns are reduced (default 2) so wide album names still fit horizontally.</li>
   * <li>Rows are increased (default 5) to fill the tall content area.</li>
   * </ul>
   *
   * @param screenW current screen width in pixels
   * @param screenH current screen height in pixels
   * @return a {@link GridProfile} ready to pass to {@link HomePanel} / {@link AlbumGridPanel}
   */
  public GridProfile homeGridProfile(int screenW, int screenH) {

    boolean portrait = screenH > screenW;

    if (portrait) {
      return computePortraitProfile(screenW, screenH);
    } else {
      return computeLandscapeProfile(screenW, screenH);
    }
  }

  // ── Landscape ─────────────────────────────────────────────────────────────

  private GridProfile computeLandscapeProfile(int screenW, int screenH) {

    // Scale relative to the canonical 1920 × 1080 resolution.
    // Use the smaller of the two axes so the grid always fits without clipping.
    double scaleX = (double) screenW / CANONICAL_W;
    double scaleY = (double) screenH / CANONICAL_H;
    double scale = Math.min(scaleX, scaleY);
    scale = Math.max(GRID_SCALE_MIN, Math.min(scale, GRID_SCALE_MAX));

    // Scale the canonical art dimensions and round to an even number of pixels.
    int artW = roundEven((int) Math.round(homeArtW * scale));
    int artH = roundEven((int) Math.round(homeArtH * scale));

    // Available content height after fixed chrome is removed.
    // topPanelHeight — credits / now-playing strip
    // tabHeight — bottom JTabbedPane tab bar
    // LETTER_NAV_H — letter-navigation strip in AlbumGridPanel (36px + 8px padding)
    // TILE_TEXT_H — album + artist labels below each tile (≈ 40px)
    // GRID_PADDING_V — top + bottom padding of the grid panel (≈ 16px)
    int availH = screenH - topPanelHeight - tabHeight - LETTER_NAV_H - TILE_TEXT_H - GRID_PADDING_V;

    // Available content width after left + right grid panel padding.
    int availW = screenW - GRID_PADDING_H;

    // Derive cols and rows from available space ÷ tile size (plus inter-tile gap).
    int tileW = artW + albumGridGapH;
    int tileH = artH + albumGridGapV;

    int cols = Math.max(1, availW / tileW);
    int rows = Math.max(1, availH / tileH);

    // Cap at the canonical maximums so very large screens don't produce huge pages.
    cols = Math.min(cols, GRID_MAX_COLS_LANDSCAPE);
    rows = Math.min(rows, GRID_MAX_ROWS_LANDSCAPE);

    return new GridProfile(cols, rows, artW, artH);
  }

  // ── Portrait ──────────────────────────────────────────────────────────────

  private GridProfile computePortraitProfile(int screenW, int screenH) {

    // In portrait mode the short axis is screenW; scale the art relative to that.
    // We still compare against CANONICAL_W so "the same 1920-wide monitor rotated
    // to 1080-wide portrait" produces a tile roughly the same absolute pixel size
    // as the landscape version, just fewer cols and more rows.
    double scale = (double) screenW / CANONICAL_W;
    scale = Math.max(GRID_SCALE_MIN, Math.min(scale, GRID_SCALE_MAX));

    // In portrait the art is a bit smaller than in landscape so more rows fit.
    int artW = roundEven((int) Math.round(homeArtW * scale * PORTRAIT_ART_REDUCTION));
    int artH = artW; // keep square thumbnails

    // Available area — same fixed-chrome deductions as landscape.
    // In portrait mode the top panel and tab bar heights may eventually be
    // different, but for now we reuse the same constants; override in a
    // portrait-specific LayoutTheme subclass if they differ.
    int availH = screenH - topPanelHeight - tabHeight - LETTER_NAV_H - TILE_TEXT_H - GRID_PADDING_V;
    int availW = screenW - GRID_PADDING_H;

    int tileW = artW + albumGridGapH;
    int tileH = artH + albumGridGapV;

    int cols = Math.max(1, availW / tileW);
    int rows = Math.max(1, availH / tileH);

    cols = Math.min(cols, GRID_MAX_COLS_PORTRAIT);
    rows = Math.min(rows, GRID_MAX_ROWS_PORTRAIT);

    return new GridProfile(cols, rows, artW, artH);
  }

  // ── Grid-profile tuning constants ─────────────────────────────────────────
  // Adjust these if the fixed-chrome heights change or the aesthetics need tuning.

  /** Minimum allowed scale factor — prevents tiles becoming unusably tiny. */
  private static final double GRID_SCALE_MIN = 0.40;

  /** Maximum allowed scale factor — prevents tiles becoming comically large. */
  private static final double GRID_SCALE_MAX = 1.50;

  /**
   * Height consumed by the AlbumGridPanel letter-navigation strip (nav buttons + padding). Matches
   * the 36px button height + 4px top + 4px bottom padding in AlbumGridPanel.
   */
  private static final int LETTER_NAV_H = 44;

  /**
   * Approximate height of the album-name + artist-name text panel below each tile. Two lines ×
   * ~16px each, plus 6px top/bottom padding = ~44px.
   */
  private static final int TILE_TEXT_H = 44;

  /**
   * Total vertical padding of the grid panel ({@code EmptyBorder(8,12,4,12)} = 12px top+bottom).
   */
  private static final int GRID_PADDING_V = 12;

  /**
   * Total horizontal padding of the grid panel ({@code EmptyBorder(8,12,4,12)} = 24px left+right).
   */
  private static final int GRID_PADDING_H = 24;

  /**
   * Additional art-size reduction factor applied in portrait mode so that tiles are proportionally
   * narrower to match the shorter portrait width.
   */
  private static final double PORTRAIT_ART_REDUCTION = 0.85;

  // Maximum grid dimensions (prevents absurdly large pages on 4 K+ or portrait 4 K displays)
  private static final int GRID_MAX_COLS_LANDSCAPE = 8;
  private static final int GRID_MAX_ROWS_LANDSCAPE = 5;
  private static final int GRID_MAX_COLS_PORTRAIT = 4;
  private static final int GRID_MAX_ROWS_PORTRAIT = 8;

  // ── Utility ───────────────────────────────────────────────────────────────

  /** Rounds {@code v} to the nearest even integer (keeps image scaling clean). */
  private static int roundEven(int v) {
    return (v % 2 == 0) ? v : v + 1;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GENRE GRID PROFILE — resolution- and orientation-aware genre-tile grid
  //
  // Mirrors the homeGridProfile() design. The genre grid is structurally
  // different from the album grid (no letter-nav strip, no per-tile text
  // panel, fixed square image tiles) so it gets its own profile type and
  // its own scaling method.
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Immutable value-object that bundles the genre-tile grid dimensions.
   *
   * <p>
   * Obtain instances via {@link LayoutTheme#genreGridProfile(int, int)}.
   *
   * @param cols number of genre tile columns per page
   * @param rows number of genre tile rows per page
   * @param imageSize pixel size (square) of each genre tile image
   */
  public record GenreGridProfile(int cols, int rows, int imageSize) {

    /** Total tiles visible on one page ({@code cols × rows}). */
    public int tilesPerPage() {
      return cols * rows;
    }
  }

  /**
   * Computes a {@link GenreGridProfile} for the given screen dimensions.
   *
   * <h3>Algorithm</h3>
   * <ol>
   * <li><b>Orientation check</b> — portrait when {@code screenH > screenW}.</li>
   * <li><b>Scale factor</b> — {@code min(screenW/1920, screenH/1080)}, clamped to
   * [{@value #GENRE_SCALE_MIN}, {@value #GENRE_SCALE_MAX}].</li>
   * <li><b>Image size</b> — the canonical {@value #genreImageSize}px image is scaled and rounded to
   * an even integer.</li>
   * <li><b>Grid dimensions</b> — available content area (screen minus fixed top-panel, tab bar,
   * genre page padding, and label height below each tile) divided by the scaled tile size.</li>
   * </ol>
   *
   * <h3>Portrait mode</h3> Fewer columns (default cap 2), more rows (default cap 6), with a further
   * {@value #GENRE_PORTRAIT_IMG_REDUCTION}× image reduction to keep tiles comfortable on the
   * narrower short axis.
   *
   * @param screenW current screen width in pixels
   * @param screenH current screen height in pixels
   * @return a {@link GenreGridProfile} ready to pass to {@link GenrePanel}
   */
  public GenreGridProfile genreGridProfile(int screenW, int screenH) {

    boolean portrait = screenH > screenW;
    return portrait ? computePortraitGenreProfile(screenW, screenH)
        : computeLandscapeGenreProfile(screenW, screenH);
  }

  // ── Landscape ─────────────────────────────────────────────────────────────

  private GenreGridProfile computeLandscapeGenreProfile(int screenW, int screenH) {

    double scaleX = (double) screenW / CANONICAL_W;
    double scaleY = (double) screenH / CANONICAL_H;
    double scale = Math.min(scaleX, scaleY);
    scale = Math.max(GENRE_SCALE_MIN, Math.min(scale, GENRE_SCALE_MAX));

    int imageSize = roundEven((int) Math.round(genreImageSize * scale));

    // Available height: subtract top panel, tab bar, vertical page padding (top+bottom),
    // and the genre label height below each tile (~40px incl. EmptyBorder(10,0,10,0)).
    int availH = screenH - topPanelHeight - tabHeight - (genrePagePadV * 2) - GENRE_LABEL_H;

    // Available width: subtract horizontal page padding (left+right).
    int availW = screenW - (genrePagePadH * 2);

    // Tile size includes the inter-tile gap.
    int tileW = imageSize + genreGridGapH;
    int tileH = imageSize + genreGridGapV + GENRE_LABEL_H;

    int cols = Math.max(1, availW / tileW);
    int rows = Math.max(1, availH / tileH);

    cols = Math.min(cols, GENRE_MAX_COLS_LANDSCAPE);
    rows = Math.min(rows, GENRE_MAX_ROWS_LANDSCAPE);

    return new GenreGridProfile(cols, rows, imageSize);
  }

  // ── Portrait ──────────────────────────────────────────────────────────────

  private GenreGridProfile computePortraitGenreProfile(int screenW, int screenH) {

    double scale = (double) screenW / CANONICAL_W;
    scale = Math.max(GENRE_SCALE_MIN, Math.min(scale, GENRE_SCALE_MAX));

    int imageSize =
        roundEven((int) Math.round(genreImageSize * scale * GENRE_PORTRAIT_IMG_REDUCTION));

    int availH = screenH - topPanelHeight - tabHeight - (genrePagePadV * 2) - GENRE_LABEL_H;
    int availW = screenW - (genrePagePadH * 2);

    int tileW = imageSize + genreGridGapH;
    int tileH = imageSize + genreGridGapV + GENRE_LABEL_H;

    int cols = Math.max(1, availW / tileW);
    int rows = Math.max(1, availH / tileH);

    cols = Math.min(cols, GENRE_MAX_COLS_PORTRAIT);
    rows = Math.min(rows, GENRE_MAX_ROWS_PORTRAIT);

    return new GenreGridProfile(cols, rows, imageSize);
  }

  // ── Genre grid-profile tuning constants ───────────────────────────────────

  /** Minimum allowed scale factor for the genre grid. */
  private static final double GENRE_SCALE_MIN = 0.40;

  /** Maximum allowed scale factor for the genre grid. */
  private static final double GENRE_SCALE_MAX = 1.50;

  /**
   * Approximate height of the genre text label below each tile image (font 24px +
   * EmptyBorder(10,0,10,0) top+bottom = ~44px).
   */
  private static final int GENRE_LABEL_H = 44;

  /** Extra art reduction applied in portrait mode. */
  private static final double GENRE_PORTRAIT_IMG_REDUCTION = 0.80;

  // Maximum genre grid dimensions
  private static final int GENRE_MAX_COLS_LANDSCAPE = 8;
  private static final int GENRE_MAX_ROWS_LANDSCAPE = 4;
  private static final int GENRE_MAX_COLS_PORTRAIT = 2;
  private static final int GENRE_MAX_ROWS_PORTRAIT = 6;

  // ═══════════════════════════════════════════════════════════════════════════
  // TOP-PANEL PROFILE — percentage-based, resolution-aware top-bar sizing
  //
  // Strategy: allocate a fixed percentage of the usable panel width to each
  // side panel (WEST = credits, EAST = now-playing wrapper). The centre
  // banner takes the remainder. This keeps both panels full-size and readable
  // at every orientation without leaving wasteful empty space.
  //
  // Usable width = screenW minus the top panel's left+right EmptyBorder (40 px).
  //
  // Default percentages (override in a subclass for custom layouts):
  // WEST / EAST : TOP_PANEL_SIDE_PCT (30 %)
  // The border height is 90 px (topPanelHeight 110 - 10 top - 10 bottom padding).
  //
  // Icon size is computed so it fits within the panel height with a small margin,
  // clamped to a sensible range so it never becomes illegibly tiny or oversized.
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Immutable value-object that bundles the widths and heights needed to lay out the top panel.
   *
   * <p>
   * Obtain instances via {@link LayoutTheme#topPanelProfile(int, int)}.
   *
   * @param creditsPanelW width of the credits panel (WEST)
   * @param creditsPanelH height of the credits panel
   * @param nowPlayingPanelW width of the now-playing panel (inner, fits inside wrapper)
   * @param nowPlayingPanelH height of the now-playing panel
   * @param nowPlayingWrapperW width of the now-playing wrapper (EAST, equals creditsPanelW for
   *        symmetry)
   * @param nowPlayingWrapperH height of the now-playing wrapper
   * @param iconSize square pixel size for the location-logo and cover-art icons
   */
  public record TopPanelProfile(int creditsPanelW, int creditsPanelH, int nowPlayingPanelW,
      int nowPlayingPanelH, int nowPlayingWrapperW, int nowPlayingWrapperH, int iconSize) {
  }

  /**
   * Computes a {@link TopPanelProfile} for the given screen dimensions using a
   * <em>percentage-of-usable-width</em> strategy.
   *
   * <h3>Algorithm</h3>
   * <ol>
   * <li><b>Usable width</b> — {@code screenW} minus the top panel's fixed left+right
   * {@code EmptyBorder} padding ({@value #TOP_PANEL_H_PADDING} px total).</li>
   * <li><b>Side-panel width</b> — {@code usableW × TOP_PANEL_SIDE_PCT}
   * ({@value #TOP_PANEL_SIDE_PCT} of usable width), the same value for both WEST (credits) and EAST
   * (now-playing wrapper) so the layout is symmetric.</li>
   * <li><b>Panel height</b> — {@code topPanelHeight} minus top+bottom {@code EmptyBorder} padding
   * ({@value #TOP_PANEL_V_PADDING} px total).</li>
   * <li><b>Icon size</b> — panel height minus a small vertical margin
   * ({@value #TOP_PANEL_ICON_V_MARGIN} px), clamped to [{@value #TOP_PANEL_ICON_MIN},
   * {@value #TOP_PANEL_ICON_MAX}].</li>
   * <li>The inner {@code nowPlayingPanelW} is set equal to {@code nowPlayingWrapperW} so it fills
   * the wrapper exactly.</li>
   * </ol>
   *
   * <p>
   * Because the widths are derived from the actual {@code screenW}, this method produces correct
   * results for both landscape (1920 × 1080) and portrait (1080 × 1920) screens without any
   * orientation-specific branches.
   *
   * @param screenW current screen width in pixels
   * @param screenH current screen height in pixels (used for future orientation-specific overrides)
   * @return a {@link TopPanelProfile} ready to pass to {@link JukeANatorFrame#buildTopPanel}
   */
  public TopPanelProfile topPanelProfile(int screenW, int screenH) {

    // Usable width after the panel's own left+right EmptyBorder is removed.
    int usableW = screenW - TOP_PANEL_H_PADDING;

    // Each side panel occupies the configured percentage of the usable width.
    int sideW = (int) Math.round(usableW * TOP_PANEL_SIDE_PCT);

    // Panel height = topPanelHeight minus top+bottom border padding.
    int panelH = topPanelHeight - TOP_PANEL_V_PADDING;

    // Icon fills the panel height minus a small top+bottom margin.
    int icon = panelH - TOP_PANEL_ICON_V_MARGIN;
    icon = Math.max(TOP_PANEL_ICON_MIN, Math.min(icon, TOP_PANEL_ICON_MAX));

    // The inner now-playing panel fills its wrapper exactly.
    return new TopPanelProfile(sideW, panelH, // credits panel (WEST)
        sideW, panelH, // now-playing panel (inner) — same dims as wrapper
        sideW, panelH, // now-playing wrapper (EAST)
        icon);
  }

  /**
   * Fraction of the usable top-panel width allocated to each side panel (WEST credits and EAST
   * now-playing wrapper). The centre banner receives the remainder ({@code 1 - 2 × side_pct}).
   * Default 0.30 → 30 % each side, 40 % centre.
   */
  private static final double TOP_PANEL_SIDE_PCT = 0.30;

  /**
   * Total left+right {@code EmptyBorder} padding on the top panel (px). Matches
   * {@code new EmptyBorder(10, 20, 10, 20)} → left 20 + right 20 = 40.
   */
  private static final int TOP_PANEL_H_PADDING = 40;

  /**
   * Total top+bottom {@code EmptyBorder} padding on the top panel (px). Matches
   * {@code new EmptyBorder(10, 20, 10, 20)} → top 10 + bottom 10 = 20.
   */
  private static final int TOP_PANEL_V_PADDING = 20;

  /** Top+bottom margin subtracted from panel height to derive the icon size. */
  private static final int TOP_PANEL_ICON_V_MARGIN = 10;

  /** Minimum icon size — prevents icons becoming illegibly tiny on very small screens. */
  private static final int TOP_PANEL_ICON_MIN = 40;

  /** Maximum icon size — prevents icons overflowing the panel on very large screens. */
  private static final int TOP_PANEL_ICON_MAX = 120;

  // ═══════════════════════════════════════════════════════════════════════════
  // FRAME / TOP-PANEL
  // Origin: JukeANatorFrame#buildTopPanel
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred height of the top panel (credits + banner + now-playing). */
  public final int topPanelHeight = 110;

  /** Fixed width of the credits panel (left side of the top panel). Canonical 1920 px value. */
  public final int creditsPanelW = 485;

  /** Fixed height of the credits panel (left side of the top panel). Canonical 1920 px value. */
  public final int creditsPanelH = 100;

  /**
   * Fixed width of the now-playing panel (right side of the top panel). Canonical 1920 px value.
   */
  public final int nowPlayingPanelW = 450;

  /** Fixed height of the now-playing panel. Canonical 1920 px value. */
  public final int nowPlayingPanelH = 100;

  /**
   * Width reserved for the now-playing wrapper (matches credits panel for symmetric layout).
   * Canonical 1920 px value. Origin: JukeANatorFrame — {@code nowPlayingWrapper}
   */
  public final int nowPlayingWrapperW = 485;

  /** Height of the now-playing wrapper. Canonical 1920 px value. */
  public final int nowPlayingWrapperH = 100;

  /**
   * Size of the location logo / cover-art icon in the top panel (square). Canonical 1920 px value.
   */
  public final int topPanelIconSize = 96;

  // ═══════════════════════════════════════════════════════════════════════════
  // TAB BAR (JukeANatorFrame — custom BasicTabbedPaneUI)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Width of each tab header button. */
  public final int tabWidth = 200;

  /** Height of each tab header. */
  public final int tabHeight = 96;

  /** Height of the painted separator line between the tab bar and content area. */
  public final int tabSeparatorHeight = 2;

  /** Icon font size inside each JukeboxTabComponent. */
  public final int tabIconFontSize = 34;

  /** Text font size inside each JukeboxTabComponent. */
  public final int tabTextFontSize = 20;

  // ═══════════════════════════════════════════════════════════════════════════
  // COUNTDOWN TIMEOUT (AlbumDetailCard, AddSongToQueueCard, SongQueueCard,
  // LoginToAdminPanelCard)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Seconds before an overlay card or detail card auto-dismisses. */
  public final int overlayTimeoutSeconds = 120;

  // ═══════════════════════════════════════════════════════════════════════════
  // HOME / ALBUM GRID (JukeANatorFrame → HomePanel → AlbumGridPanel)
  //
  // NOTE: These are the CANONICAL (1920 × 1080 landscape) base values.
  // Use homeGridProfile(screenW, screenH) to get the actual values for
  // the current display; do NOT pass these directly to HomePanel any more.
  // ═══════════════════════════════════════════════════════════════════════════

  /** Canonical number of columns in the main album grid (1920 × 1080 landscape). */
  public final int homeGridCols = 4;

  /** Canonical number of rows in the main album grid (1920 × 1080 landscape). */
  public final int homeGridRows = 3;

  /** Canonical width of each album cover-art thumbnail in the main grid. */
  public final int homeArtW = 190;

  /** Canonical height of each album cover-art thumbnail in the main grid. */
  public final int homeArtH = 190;

  // ═══════════════════════════════════════════════════════════════════════════
  // ALBUM GRID PANEL (AlbumGridPanel)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Horizontal gap between tiles in AlbumGridPanel. */
  public final int albumGridGapH = 10;

  /** Vertical gap between tiles in AlbumGridPanel. */
  public final int albumGridGapV = 10;

  /** Inner padding (all sides) inside each album tile border. */
  public final int albumTileInnerPad = 1;

  // ═══════════════════════════════════════════════════════════════════════════
  // ALBUM VIEW CARD — left sidebar and track list
  // Origin: AlbumViewCard
  // ═══════════════════════════════════════════════════════════════════════════

  /** Width of the left sidebar (cover art + metadata) in AlbumViewCard. */
  public final int albumViewSidebarW = 320;

  /** Pixel size (square) of the cover-art image displayed in the sidebar. */
  public final int albumViewCoverSize = 320;

  /** Number of track rows shown per page in the paginated track listing. */
  public final int albumViewTracksPerPage = 15;

  /** Width allocated for the "# Plays" (popularity bars) column header and cells. */
  public final int albumViewPlaysColW = 64;

  /** Width allocated for the track-number column. */
  public final int albumViewTrkNumColW = 48;

  /**
   * Width of the artist column when displaying a compilation album (column is split: artist |
   * song).
   */
  public final int albumViewCompilationArtistW = 260;

  /**
   * Width of the song column when displaying a compilation album.
   */
  public final int albumViewCompilationSongW = 520;

  // ═══════════════════════════════════════════════════════════════════════════
  // NAVIGATION / PAGE BUTTONS (AlbumGridPanel, GenrePanel, ButtonFactory)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred width of the standard navigation button (❮ / ❯). */
  public final int navBtnW = 140;

  /** Preferred height of the standard navigation button. */
  public final int navBtnH = 36;

  /** Preferred {@link Dimension} for the standard navigation button (derived). */
  public final Dimension navBtnSize;

  // ═══════════════════════════════════════════════════════════════════════════
  // DETAIL HEADER PANEL (DetailHeaderPanel)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Width of the cover-art / icon image in the detail header. */
  public final int detailHeaderImageW = 72;

  /** Height of the cover-art / icon image in the detail header. */
  public final int detailHeaderImageH = 72;

  /** Preferred size of the detail header image label (derived). */
  public final Dimension detailHeaderImageSize;

  /**
   * Preferred width of the back button inside DetailHeaderPanel (and AlbumDetailCard footer,
   * LoginToAdminPanelCard).
   */
  public final int detailBackBtnW = 140;

  /** Preferred height of the back button inside DetailHeaderPanel / AlbumDetailCard footer. */
  public final int detailBackBtnH = 52;

  // ═══════════════════════════════════════════════════════════════════════════
  // GENRE PANEL (GenrePanel)
  // Origin: GenrePanel
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Number of genre tiles per page in the genre grid. Default: 12 (2 rows × 6 columns).
   */
  public final int genresPerPage = 12;

  /** Number of columns in the genre grid. */
  public final int genreGridCols = 6;

  /** Number of rows in the genre grid. */
  public final int genreGridRows = 2;

  /** Horizontal gap between genre tiles. */
  public final int genreGridGapH = 20;

  /** Vertical gap between genre tiles. */
  public final int genreGridGapV = 20;

  /** Outer horizontal padding for the genre grid page wrapper. */
  public final int genrePagePadH = 60;

  /** Outer vertical (top) padding for the genre grid page wrapper. */
  public final int genrePagePadV = 30;

  /** Inner padding (all sides) inside each genre tile. */
  public final int genreTileInnerPad = 16;

  /** Pixel size (square) of the genre image loaded and displayed in each tile. */
  public final int genreImageSize = 240;

  /**
   * Width and height for the prev/next wrapper panels in the genre pagination row. Matches
   * {@link #navBtnW} × {@link #navBtnH}.
   */
  public final int genrePaginationBtnW = 140;

  /** Height of the genre pagination nav wrapper. */
  public final int genrePaginationBtnH = 36;

  // ═══════════════════════════════════════════════════════════════════════════
  // GENRE DETAIL PANEL (GenreDetailPanel)
  // Origin: GenreDetailPanel
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Number of result rows visible per column page in the genre detail (artists / albums / songs).
   * Tune this value if the screen resolution changes the visible row count.
   */
  public final int genreDetailPreviewCount = 9;

  /** Preferred width of each sort button in the genre detail header. */
  public final int sortBtnW = 170;

  /** Preferred height of each sort button in the genre detail header. */
  public final int sortBtnH = 42;

  // ═══════════════════════════════════════════════════════════════════════════
  // HOT HERE PANEL (HotHerePanel)
  // Origin: HotHerePanel
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Number of result rows visible per column page in the Hot Here tab. Tune this value if the
   * screen resolution changes the visible row count.
   */
  public final int hotHerePreviewCount = 10;

  // ═══════════════════════════════════════════════════════════════════════════
  // SEARCH PANEL (SearchPanel)
  // Origin: SearchPanel
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred height of the search bar panel (query display + optional search button). */
  public final int searchBarHeight = 90;

  /**
   * Number of result rows visible per column page in search results. Tune this value if the screen
   * resolution changes the visible row count.
   */
  public final int searchPreviewCount = 5;

  /**
   * Outer horizontal screen margin padding (left + right) applied to the search bar wrapper,
   * keyboard wrapper, and hero panel. Exposing background gradient on both sides.
   */
  public final int screenPaddingHorizontal = 60;

  /**
   * Internal edge gap inside each result column (left + right, each side). Must match the value
   * used in {@code ResultsColumnPanel}. Used in SearchPanel to compute the unified column padding:
   * {@code screenPaddingHorizontal - columnInternalEdgeGap}.
   */
  public final int columnInternalEdgeGap = 10;

  /** Preferred height of the hero / entry-state panel in the Search tab. */
  public final int searchHeroHeight = 300;

  /** Preferred width of the search button (manual search mode). */
  public final int searchBtnW = 180;

  /** Preferred height of the search button. */
  public final int searchBtnH = 60;

  // ═══════════════════════════════════════════════════════════════════════════
  // KEYBOARD PANEL (KeyboardPanel)
  // Origin: KeyboardPanel
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred height of the keyboard wrapper panel. */
  public final int keyboardHeight = 260;

  /**
   * Horizontal margin (left + right) applied to the keyboard wrapper. Exposes the background
   * gradient on both sides.
   */
  public final int keyboardPaddingHorizontal = 60;

  /** Standard letter key preferred size — width. */
  public final int keyLetterW = 70;

  /** Standard letter key preferred size — height. */
  public final int keyLetterH = 60;

  /** CLEAR key width. */
  public final int keyClearW = 140;

  /** Backspace key width. */
  public final int keyBackspaceW = 100;

  /** Mode toggle button (ABC / 123) width. */
  public final int keyModeToggleW = 140;

  /** SPACE key width. */
  public final int keySpaceW = 420;

  /** Key grid row gap. */
  public final int keyRowGap = 10;

  /** Key grid column gap. */
  public final int keyColGap = 8;

  /** Inner padding around the full keyboard layout. */
  public final int keyboardInnerPad = 20;

  // ═══════════════════════════════════════════════════════════════════════════
  // RESULT ROW / RESULTS COLUMN PANEL (ResultsColumnPanel)
  // Origin: ResultsColumnPanel
  // ═══════════════════════════════════════════════════════════════════════════

  /** Maximum row height for items in a results column. */
  public final int resultRowMaxH = 72;

  /** Thumbnail image size (square) displayed in each result row. */
  public final int resultThumbSize = 56;

  /** Width reserved for the row number label. */
  public final int resultNumLabelW = 36;

  /** Result column outer left/right padding. */
  public final int resultColumnPadH = 10;

  /** Nav button preferred size — width (up/down arrows in result columns). */
  public final int resultNavBtnW = 75;

  /** Nav button preferred size — height. */
  public final int resultNavBtnH = 45;

  // ═══════════════════════════════════════════════════════════════════════════
  // SONG TRACK CELL RENDERER (SongTrackCellRenderer)
  // Origin: SongTrackCellRenderer
  // ═══════════════════════════════════════════════════════════════════════════

  /** Width of each popularity bar in pixels. */
  public final int popularityBarWidth = 5;

  /** Gap between adjacent popularity bars in pixels. */
  public final int popularityBarGap = 3;

  /** Maximum height of the tallest (3rd) popularity bar. */
  public final int popularityBarMaxH = 18;

  /** Fixed cell height for the queue/song-track list renderer. */
  public final int songTrackCellHeight = 44;

  // ═══════════════════════════════════════════════════════════════════════════
  // ADD SONG TO QUEUE CARD (AddSongToQueueCard)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred width of the AddSongToQueueCard panel. */
  public final int addSongCardW = 900;

  /** Preferred height of the AddSongToQueueCard panel. */
  public final int addSongCardH = 420;

  /** Size (square) of the song cover-art image in the info row. */
  public final int addSongCoverSize = 160;

  /** Preferred width of each queue action button (Play / Priority Play). */
  public final int addSongQueueBtnW = 200;

  /** Preferred height of each queue action button. */
  public final int addSongQueueBtnH = 88;

  /** Preferred width of the Cancel button in AddSongToQueueCard. */
  public final int addSongCancelBtnW = 200;

  /** Preferred height of the Cancel button. */
  public final int addSongCancelBtnH = 62;

  // ═══════════════════════════════════════════════════════════════════════════
  // SONG QUEUE CARD (SongQueueCard)
  // Origin: SongQueueCard
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred width of the SongQueueCard panel. */
  public final int songQueueCardW = 900;

  /** Preferred height of the SongQueueCard panel. */
  public final int songQueueCardH = 660;

  /**
   * Maximum number of queued song entries visible at once in the SongQueueCard list. Determines the
   * fixed list height (no scroll bar).
   */
  public final int songQueueMaxVisible = 5;

  /** Size (square) of the now-playing cover-art icon in SongQueueCard. */
  public final int songQueueCoverSize = 96;

  /** Preferred width of the move-up/move-down/remove action buttons in SongQueueCard. */
  public final int songQueueActionBtnW = 200;

  /** Preferred height of the action buttons. */
  public final int songQueueActionBtnH = 80;

  /** Preferred width of the Cancel button in SongQueueCard. */
  public final int songQueueCancelBtnW = 200;

  /** Preferred height of the Cancel button. */
  public final int songQueueCancelBtnH = 52;

  // ═══════════════════════════════════════════════════════════════════════════
  // ADMIN PANEL (AdminPanel)
  // Origin: AdminPanel
  // ═══════════════════════════════════════════════════════════════════════════

  /** Fixed width of every sidebar button in AdminPanel. */
  public final int adminBtnW = 84;

  /** Fixed height of every sidebar button in AdminPanel. */
  public final int adminBtnH = 42;

  /** Fixed preferred and maximum {@link Dimension} for AdminPanel sidebar buttons (derived). */
  public final Dimension adminBtnSize;

  /** Fixed cell height for the album list in AdminPanel. */
  public final int adminAlbumCellH = 36;

  /** Preferred width of the filter text field in AdminPanel. */
  public final int adminFilterFieldW = 160;

  /** Preferred height of the filter text field. */
  public final int adminFilterFieldH = 24;

  /** Thumbnail size (square) for album cover-art in the AdminPanel album list. */
  public final int adminThumbSize = 30;

  // ═══════════════════════════════════════════════════════════════════════════
  // LOGIN TO ADMIN PANEL CARD (LoginToAdminPanelCard)
  // Origin: LoginToAdminPanelCard
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred width of the credential panel. */
  public final int loginPanelW = 700;

  /** Preferred height of the credential panel. */
  public final int loginPanelH = 340;

  /** Preferred width of each action button (LOGIN / CANCEL) in the login card. */
  public final int loginActionBtnW = 160;

  /** Preferred height of each action button. */
  public final int loginActionBtnH = 52;

  /** Width of the field-label caption in the login card. */
  public final int loginCaptionW = 140;

  /** Height of each field row (Username / Password) in the login card. */
  public final int loginFieldH = 48;

  // ═══════════════════════════════════════════════════════════════════════════
  // EDIT ALBUM CARD (EditAlbumCard)
  // Origin: EditAlbumCard
  // ═══════════════════════════════════════════════════════════════════════════

  /** Preferred width of the EditAlbumCard main panel. */
  public final int editAlbumCardW = 860;

  /** Preferred height of the EditAlbumCard main panel. */
  public final int editAlbumCardH = 660;

  /** Square size of the current cover-art and search-result cover-art labels. */
  public final int editAlbumCoverSize = 250;

  // ═══════════════════════════════════════════════════════════════════════════
  // POPULARITY THRESHOLDS (JukeANatorFrame → all panels)
  // These drive the SongTrackCellRenderer bar count.
  // Origin: JukeANatorFrame constants, propagated through constructors
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Minimum play count to display 1 popularity bar. Origin: JukeANatorFrame#POPULARITY_THRESHOLD_1
   */
  public final int popularityThreshold1 = 10;

  /**
   * Minimum play count to display 2 popularity bars. Origin: JukeANatorFrame#POPULARITY_THRESHOLD_2
   */
  public final int popularityThreshold2 = 25;

  /**
   * Minimum play count to display 3 popularity bars (fully popular). Origin:
   * JukeANatorFrame#POPULARITY_THRESHOLD_3
   */
  public final int popularityThreshold3 = 50;

  // ═══════════════════════════════════════════════════════════════════════════
  // FONT SIZES
  // Consolidated here so a portrait or small-screen theme can scale all text
  // from a single place rather than hunting through paintComponent overrides.
  // ═══════════════════════════════════════════════════════════════════════════

  // Navigation & header
  public final int fontSizeNavBtn = 18; // ButtonFactory, AlbumDetailCard back button
  public final int fontSizeDetailTitle = 26; // DetailHeaderPanel title label
  public final int fontSizeDetailSubtitle = 14; // DetailHeaderPanel subtitle label
  public final int fontSizeAdminHeader = 22; // AdminPanel header title
  public final int fontSizeAdminSection = 14; // AdminPanel section header labels
  public final int fontSizeAlbumLabel = 14; // AlbumGridPanel tile album name
  public final int fontSizeArtistLabel = 12; // AlbumGridPanel tile artist name
  public final int fontSizePageLabel = 15; // Pagination page labels
  public final int fontSizeSortBtn = 18; // GenreDetailPanel sort buttons

  // Search
  public final int fontSizeSearchHero = 42; // SearchPanel hero label
  public final int fontSizeSearchBar = 32; // Search bar query display
  public final int fontSizeSearchBtn = 22; // Manual search button

  // Track list / result rows
  public final int fontSizeTrackSong = 17; // AlbumViewCard song label
  public final int fontSizeTrackArtist = 14; // AlbumViewCard artist/header label
  public final int fontSizeResultLine1 = 17; // ResultsColumnPanel primary line
  public final int fontSizeResultLine2 = 13; // ResultsColumnPanel secondary line
  public final int fontSizeResultNum = 16; // ResultsColumnPanel row number label
  public final int fontSizeResultHeader = 22; // ResultsColumnPanel column header

  // Queue / overlay cards
  public final int fontSizeAddSongTitle = 32; // AddSongToQueueCard song title
  public final int fontSizeAddSongArtist = 22; // AddSongToQueueCard artist / album
  public final int fontSizeQueueBtn = 17; // SongQueueCard action buttons
  public final int fontSizeQueueCancelBtn = 20; // SongQueueCard cancel button
  public final int fontSizeTimeoutLabel = 13; // Countdown "Closes in Xs" label

  // Keyboard
  public final int fontSizeKeyLabel = 22; // KeyboardPanel key labels

  // Admin
  public final int fontSizeAdminAlbum = 15; // AdminPanel album list cell
  public final int fontSizeAdminArtist = 12; // AdminPanel album list sub-label
  public final int fontSizeAdminSideBtn1 = 13; // AdminPanel side button line 1 (symbol)
  public final int fontSizeAdminSideBtn2 = 10; // AdminPanel side button line 2 (text)
  public final int fontSizeCreditTitle = 18; // JukeANatorFrame credits title
  public final int fontSizeCreditDesc = 15; // JukeANatorFrame credits description

  // Login
  public final int fontSizeLoginTitle = 26; // LoginToAdminPanelCard title
  public final int fontSizeLoginCaption = 16; // LoginToAdminPanelCard field captions
  public final int fontSizeLoginField = 22; // LoginToAdminPanelCard field text
  public final int fontSizeLoginBtn = 18; // LoginToAdminPanelCard buttons

  // Genre
  public final int fontSizeGenreTileLabel = 24; // GenrePanel tile text label
}
