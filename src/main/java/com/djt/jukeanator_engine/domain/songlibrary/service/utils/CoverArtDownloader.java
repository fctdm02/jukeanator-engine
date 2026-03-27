package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;

/**
 * @author tmyers
 */
public final class CoverArtDownloader {

  private final RestClient restClient;

  public CoverArtDownloader() {

    this.restClient = RestClient.create();
  }

  public void downloadCoverArt(String coverArtPath, String coverArtUrl) {

    String errorMessage = null;
    try {

      errorMessage = "Could not fetch image from: " + coverArtUrl;
      byte[] imageBytes = restClient.get().uri(coverArtUrl).retrieve().body(byte[].class);


      errorMessage = "Could not write image to: " + coverArtPath;
      Path path = Path.of(coverArtPath);
      Files.write(path, imageBytes);

      
      BufferedImage image = ImageIO.read(new File(coverArtPath));
      int width = image.getWidth();
      int height = image.getHeight();
      if (width > 500 || height > 500) {
        
        errorMessage = "Could not resize image to 500x500px: " + coverArtPath;
        BufferedImage original = ImageIO.read(new File(coverArtPath));
        BufferedImage resized = resizeHighQuality(original, 500, 500);
        ImageIO.write(resized, "jpg", new File(coverArtPath));        
      }      

    } catch (IOException ioe) {
      throw new SongLibraryException(errorMessage + ", error: " + ioe.getMessage(), ioe);
    }
  }

  public static BufferedImage resizeHighQuality(BufferedImage original, int targetWidth, int targetHeight) {

    BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

    Graphics2D g2d = output.createGraphics();

    // High-quality rendering hints
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
    g2d.dispose();

    return output;
  }
}