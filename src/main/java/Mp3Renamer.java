import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;

public class Mp3Renamer {

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("/home/tmyers/Music/AllMusic");

        Files.walk(root)
                .filter(p -> Files.isRegularFile(p))
                .filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
                .forEach(Mp3Renamer::processFile);
    }

    private static void processFile(Path file) {
        try {
            String originalName = file.getFileName().toString();

            String cleanedName = sanitizeFilename(originalName);

            if (originalName.equals(cleanedName)) {
                return; // nothing to change
            }

            Path target = file.resolveSibling(cleanedName);

            // Avoid overwriting existing files
            int counter = 1;
            while (Files.exists(target)) {
                String base = cleanedName.replaceFirst("\\.mp3$", "");
                target = file.resolveSibling(base + "_" + counter + ".mp3");
                counter++;
            }

            System.out.println("Renaming: " + file + " -> " + target);

            Files.move(file, target);

        } catch (Exception e) {
            System.err.println("Error processing: " + file);
            e.printStackTrace();
        }
    }

    private static String sanitizeFilename(String name) {
        // Normalize unicode (e.g., é → e)
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Remove non-printable characters
        normalized = normalized.replaceAll("[^\\x20-\\x7E]", "");

        // Replace spaces with underscore
        normalized = normalized.replace(" ", "_");

        // Keep only safe characters
        normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "");

        // Avoid empty names
        if (normalized.isBlank()) {
            normalized = "file.mp3";
        }

        return normalized;
    }
}