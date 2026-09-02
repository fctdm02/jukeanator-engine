package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;

public class ScreenSaverWindow extends JWindow {

  private static final long serialVersionUID = 1L;

  private static final int MOVE_INTERVAL_MS = 30000;

  private final ImageLoader imageLoader;
  private final int screenWidth;
  private final int screenHeight;

  private final JPanel floatingPanel;
  private final JLabel logoLabel;
  private final JLabel coverArtLabel;
  private final JLabel touchLabel;

  private final Timer moveTimer;

  private final SongPlayerService songPlayerService;
  private final SongLibraryService songLibraryService;
  private List<AlbumDto> albums; // lazy-initialised on first updateContent() call
  private final ImageIcon logo;
  private final ImageIcon textImage;

  public ScreenSaverWindow(javax.swing.JFrame owner, ImageLoader imageLoader, int screenWidth,
      int screenHeight, SongPlayerService songPlayerService,
      SongLibraryService songLibraryService) {

    // Passing the owner JFrame ensures the JWindow is anchored to the same
    // graphics device as the fullscreen JFrame, so setBounds lands correctly.
    super(owner);

    this.imageLoader = imageLoader;
    this.screenWidth = screenWidth;
    this.screenHeight = screenHeight;
    this.songPlayerService = songPlayerService;
    this.songLibraryService = songLibraryService;

    ImageIcon icon = imageLoader.loadImage("JukeANatorLogo.png", (int) (screenWidth * 0.30), 120);
    Image transparentStrippedImage = ImageLoader.createTransparentImage(icon.getImage(), false, 15);
    this.logo = new ImageIcon(transparentStrippedImage);

    // Scale "ScreenSaverText.png" to fit within the same box as the logo
    // (screenWidth * 0.30 wide, 120 tall) while preserving its native aspect
    // ratio. Stretching it to exactly that box (as before) squished it
    // horizontally on narrower screens — e.g. at 1024px wide the box is
    // 307x120 (2.56:1) but the source image is 2172x724 (3:1), so forcing it
    // into the box compressed the width.
    java.awt.Dimension textNativeSize = nativeImageSize("ScreenSaverText.png");
    int textMaxWidth = (int) (screenWidth * 0.30);
    int textMaxHeight = 120;
    int textTargetWidth = textMaxWidth;
    int textTargetHeight = textMaxHeight;
    if (textNativeSize != null && textNativeSize.width > 0 && textNativeSize.height > 0) {
      double scale = Math.min((double) textMaxWidth / textNativeSize.width,
          (double) textMaxHeight / textNativeSize.height);
      textTargetWidth = Math.max(1, (int) Math.round(textNativeSize.width * scale));
      textTargetHeight = Math.max(1, (int) Math.round(textNativeSize.height * scale));
    }
    // Widen the fitted image by 20% so it doesn't look squished, independent
    // of screen resolution.
    textTargetWidth = (int) Math.round(textTargetWidth * 1.2);
    ImageIcon textIcon =
        imageLoader.loadImage("ScreenSaverText.png", textTargetWidth, textTargetHeight);
    Image transparentStrippedText =
        ImageLoader.createTransparentImage(textIcon.getImage(), false, 15);
    this.textImage = new ImageIcon(transparentStrippedText);

    setAlwaysOnTop(true);

    // Use the GraphicsDevice bounds so the window covers every pixel of the
    // screen, including any area the OS may reserve for taskbars. This is the
    // same device the JFrame occupies in exclusive fullscreen mode.
    java.awt.Rectangle screenBounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    setBounds(screenBounds);

    JPanel background = new JPanel(null) {

      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(10, 10, 10));
        g2.fillRect(0, 0, w, h);

        float[] fractions = {0.0f, 0.20f, 0.42f, 0.62f, 0.82f, 1.0f};

        Color[] colors = {new Color(140, 50, 50, 90), new Color(140, 90, 30, 80),
            new Color(80, 110, 40, 70), new Color(30, 100, 110, 70), new Color(40, 60, 140, 80),
            new Color(100, 30, 140, 90)};

        g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0), new Point2D.Float(w, h),
            fractions, colors));

        g2.fillRect(0, 0, w, h);

        g2.dispose();
      }
    };

    setContentPane(background);

    int panelWidth = (int) (screenWidth * 0.35);

    // Height must fit every stacked child at its actual rendered size — logo
    // (120) + strut (20) + cover art (350, fixed regardless of screen size) +
    // strut (20) + touch-to-start text (120) — or BoxLayout will lay the
    // trailing components out below the panel's bottom edge, where Swing
    // clips them during paint. A screenWidth-derived fraction (e.g. 0.35 *
    // 1.4) undershoots this on smaller screens (e.g. 1024px wide), which is
    // why the touch label was invisible.
    int logoTextHeight = 120;
    int coverArtSize = 350;
    int strutHeight = 20;
    int panelHeight =
        logoTextHeight + strutHeight + coverArtSize + strutHeight + logoTextHeight;

    floatingPanel = new JPanel();
    floatingPanel.setLayout(new BoxLayout(floatingPanel, BoxLayout.Y_AXIS));
    floatingPanel.setOpaque(false);
    floatingPanel.setSize(panelWidth, panelHeight);

    logoLabel = new JLabel();
    logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    coverArtLabel = new JLabel();
    coverArtLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    // Changed to an empty label slated to hold the new text image icon
    touchLabel = new JLabel();
    touchLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    floatingPanel.add(logoLabel);
    floatingPanel.add(Box.createVerticalStrut(20));
    floatingPanel.add(coverArtLabel);
    floatingPanel.add(Box.createVerticalStrut(20));
    floatingPanel.add(touchLabel);

    background.add(floatingPanel);

    moveFloatingPanel();

    moveTimer = new Timer(MOVE_INTERVAL_MS, e -> {
      // Only do work while actually showing — in particular, while hibernation
      // is active this window is hidden and must stay fully idle rather than
      // continuing to refresh content in the background (screensaver and
      // hibernation are mutually exclusive).
      if (!isVisible()) {
        return;
      }
      // Always reposition the panel to prevent burn-in.
      // When no song is playing, also pick a fresh random cover art so the
      // screensaver shows variety instead of the same album every 30 seconds.
      if (this.songPlayerService.getNowPlayingSong(this.songLibraryService.getOwnLocationId())
          == null) {
        updateContent();
      }
      moveFloatingPanel();
    });
    moveTimer.start();
  }

  // Reads the unscaled pixel dimensions of a classpath image, so callers can
  // compute a scale that fits a target box without distorting the aspect
  // ratio. ImageIcon(URL) loads synchronously, so the dimensions are valid
  // immediately after construction.
  private java.awt.Dimension nativeImageSize(String classpathResourceName) {

    java.net.URL url = getClass().getResource(classpathResourceName);
    if (url == null) {
      return null;
    }
    ImageIcon probe = new ImageIcon(url);
    return new java.awt.Dimension(probe.getIconWidth(), probe.getIconHeight());
  }

  private void moveFloatingPanel() {

    Random random = new Random();

    int x = random.nextInt(Math.max(1, screenWidth - floatingPanel.getWidth()));

    int y = random.nextInt(Math.max(1, screenHeight - floatingPanel.getHeight()));

    floatingPanel.setLocation(x, y);
  }

  public void updateContent() {

    logoLabel.setIcon(logo);
    touchLabel.setIcon(textImage);

    ImageIcon coverArt = null;
    SongDto currentSong =
        this.songPlayerService.getNowPlayingSong(this.songLibraryService.getOwnLocationId());
    if (currentSong != null && currentSong.getCoverArtPath() != null) {

      coverArt = imageLoader.loadFilesystemImage(currentSong.getCoverArtPath(), 350, 350);

    } else {

      if (albums == null) {
        albums = this.songLibraryService.getAlbums(this.songLibraryService.getOwnLocationId());
      }
      if (!albums.isEmpty()) {
        AlbumDto album = albums.get(new Random().nextInt(albums.size()));
        coverArt = imageLoader.loadFilesystemImage(album.getCoverArtPath(), 350, 350);
      }

    }

    coverArtLabel.setIcon(coverArt);
  }
}
