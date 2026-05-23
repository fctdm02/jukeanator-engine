package com.djt.jukeanator_engine.ui.utils;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ImageLoader {

  private static final int MAX_CACHE_SIZE = 1000;

  public final Map<CacheKey, ImageIcon> cache = new LinkedHashMap<>(16, 0.75f, true) {
    private static final long serialVersionUID = -2034534390673299070L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<CacheKey, ImageIcon> eldest) {
      return size() > MAX_CACHE_SIZE;
    }
  };

  public ImageIcon loadImage(URL imageUrl, int width, int height) {

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
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        ImageIcon result = new ImageIcon(scaled);

        cache.put(key, result);
        return result;

      } catch (Exception e) {
        return null;
      }
    }
  }

  private record CacheKey(URL url, int width, int height) {
    CacheKey {
      Objects.requireNonNull(url);
    }
  }
}
