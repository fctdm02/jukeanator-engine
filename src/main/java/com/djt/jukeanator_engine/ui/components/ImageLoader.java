package com.djt.jukeanator_engine.ui.components;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.ImageIcon;

public class ImageLoader {

  private static final int MAX_CACHE_SIZE = 1000;

  public final Map<CacheKey, ImageIcon> cache = new LinkedHashMap<>(16, 0.75f, true) {
    private static final long serialVersionUID = -2034534390673299070L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<CacheKey, ImageIcon> eldest) {
      return size() > MAX_CACHE_SIZE;
    }
  };

  public ImageIcon loadImage(String resourceName, int width, int height) {

    ImageIcon imageIcon = loadFilesystemImage(resourceName, width, height);
    if (imageIcon == null) {
      imageIcon = loadClasspathImage(resourceName, width, height);
    }
    return imageIcon;
  }

  public ImageIcon loadFilesystemImage(String resourceName, int width, int height) {

    if (resourceName == null || resourceName.isBlank()) {
      return null;
    }
    Path path = Paths.get(resourceName);
    if (!path.toFile().exists()) {
      return null;
    }
    URL imageUrl;
    try {
      imageUrl = path.toUri().toURL();
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
    return loadImage(imageUrl, width, height, Image.SCALE_SMOOTH);
  }

  public ImageIcon loadClasspathImage(String resourceName, int width, int height) {

    return this.loadClasspathImage(resourceName, width, height, Image.SCALE_SMOOTH);
  }

  public ImageIcon loadClasspathImage(String resourceName, int width, int height, int scaling) {

    if (resourceName == null || resourceName.isBlank()) {
      return null;
    }
    URL imageUrl = getClass().getResource(resourceName);
    return loadImage(imageUrl, width, height, scaling);
  }

  public ImageIcon loadImage(URL imageUrl, int width, int height, int scaling) {

    if (imageUrl == null) {
      return null;
    }

    CacheKey key = new CacheKey(imageUrl, width, height);

    synchronized (cache) {
      ImageIcon cached = cache.get(key);
      if (cached != null) {
        return cached;
      }

      try {
        ImageIcon icon = new ImageIcon(imageUrl);
        Image image = icon.getImage();
        Image scaled = image.getScaledInstance(width, height, scaling);
        ImageIcon result = new ImageIcon(scaled);

        cache.put(key, result);
        return result;

      } catch (Exception e) {
        return null;
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ANIMATED GIF LOADER
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Loads an animated GIF from the classpath without passing it through
   * {@link Image#getScaledInstance}, which strips all frames past the first and silently kills the
   * animation.
   *
   * <p>
   * The returned icon is constructed directly from the resource URL so that the AWT toolkit retains
   * the full frame sequence and fires image-update callbacks correctly. The icon is <em>not</em>
   * placed in the shared cache because animated icons must be kept as independent instances —
   * sharing a single {@link ImageIcon} between multiple {@link javax.swing.JLabel}s causes the
   * image-observer chain to diverge once any label is hidden, which can cause the animation to
   * paint at stale component coordinates after a visibility change (the root cause of the
   * portrait-mode overlay bleed described in Issue 3).
   *
   * <p>
   * If the resource cannot be found, {@code null} is returned so callers can fall back gracefully.
   *
   * @param resourceName classpath-relative resource name, e.g. {@code "music_playing.gif"}
   * @return a freshly constructed {@link ImageIcon} that preserves the animation, or {@code null}
   *         if the resource was not found
   */
  public ImageIcon loadAnimatedGif(String resourceName) {

    if (resourceName == null || resourceName.isBlank()) {
      return null;
    }

    URL imageUrl = getClass().getResource(resourceName);
    if (imageUrl == null) {
      return null;
    }

    // Construct from URL — this is the only ImageIcon constructor that correctly
    // initialises the MediaTracker animation thread for animated GIFs.
    return new ImageIcon(imageUrl);
  }

  // ─────────────────────────────────────────────────────────────────────────

  public static Image createTransparentImage(Image srcImage, boolean filterAbove, int threshold) {

    RGBImageFilter filter = new RGBImageFilter() {
      @Override
      public final int filterRGB(int x, int y, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        if (filterAbove) {
          if (r >= threshold && g >= threshold && b >= threshold) {
            return 0x00FFFFFF & rgb;
          }
        } else {
          if (r <= threshold && g <= threshold && b <= threshold) {
            return 0x00FFFFFF & rgb;
          }
        }
        return rgb;
      }
    };

    ImageProducer ip = new FilteredImageSource(srcImage.getSource(), filter);
    return Toolkit.getDefaultToolkit().createImage(ip);
  }

  private record CacheKey(URL url, int width, int height) {
    CacheKey {
      Objects.requireNonNull(url);
    }
  }
}
