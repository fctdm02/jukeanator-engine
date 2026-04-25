import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class Mp3Pruner {

    private static final Path ALL_MUSIC =
            Paths.get("/home/tmyers/Music/AllMusic");

    private static final Path COMPRESSED_MUSIC =
            Paths.get("/home/tmyers/Music/CompressedMusic");

    public static void main(String[] args) throws IOException {

        // Step 1: Build a set of all MP3 paths in CompressedMusic
        Set<String> compressedSet = new HashSet<>();

        try (var stream = Files.walk(COMPRESSED_MUSIC)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
                  .forEach(p -> {
                      String relative = COMPRESSED_MUSIC.relativize(p)
                              .toString()
                              .toLowerCase();
                      compressedSet.add(relative);
                  });
        }

        // Step 2: Scan AllMusic and delete missing ones
        try (var stream = Files.walk(ALL_MUSIC)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
                  .forEach(p -> {
                      String relative = ALL_MUSIC.relativize(p)
                              .toString()
                              .toLowerCase();

                      if (!compressedSet.contains(relative)) {
                          System.out.println("Deleting: " + p);
                          try {
                              Files.delete(p);
                          } catch (IOException e) {
                              System.err.println("Failed to delete: " + p);
                              e.printStackTrace();
                          }
                      }
                  });
        }
    }
}