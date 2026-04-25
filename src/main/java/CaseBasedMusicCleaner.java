import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CaseBasedMusicCleaner {

    private static final Path MUSIC_DIR = Paths.get("/home/tmyers/Music/CompressedMusic");

    // Words that should normally be lowercase in titles
    private static final Set<String> LOWERCASE_WORDS = Set.of(
            "of", "the", "and", "in", "on", "at", "to", "for"
    );

    public static void main(String[] args) throws IOException {
        Map<String, List<Path>> grouped = new HashMap<>();

        // Step 1: Group files by case-insensitive name
        try (var stream = Files.walk(MUSIC_DIR)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String key = normalizeKey(path.getFileName().toString());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
            });
        }

        // Step 2: Process groups with duplicates
        for (var entry : grouped.entrySet()) {
            List<Path> files = entry.getValue();

            if (files.size() > 1) {
                //System.out.println("\nFound duplicates:");
                files.forEach(System.out::println);

                // Step 3: Choose best filename
                Path best = chooseBest(files);

                //System.out.println("Keeping: " + best);

                // Step 4: Delete others
                for (Path p : files) {
                    if (!p.equals(best)) {
                        System.out.println("Deleting: " + p);
                        Files.delete(p);
                    }
                }
            }
        }
    }

    // Normalize for grouping (case-insensitive only)
    private static String normalizeKey(String name) {
        return name.toLowerCase();
    }

    // Choose the best filename based on capitalization rules
    private static Path chooseBest(List<Path> files) {
        return files.stream()
                .max(Comparator.comparingInt(CaseBasedMusicCleaner::scoreFilename))
                .orElse(files.get(0));
    }

    // Score filenames: higher = better capitalization
    private static int scoreFilename(Path path) {
        String name = path.getFileName().toString();
        String base = name.replaceAll("\\.[^.]+$", ""); // remove extension

        String[] words = base.split("[_\\- ]+");

        int score = 0;

        for (String word : words) {
            if (word.isEmpty()) continue;

            if (LOWERCASE_WORDS.contains(word.toLowerCase())) {
                // prefer lowercase for words like "of"
                if (word.equals(word.toLowerCase())) {
                    score += 2;
                } else {
                    score -= 1;
                }
            } else {
                // prefer capitalized words (Title Case)
                if (Character.isUpperCase(word.charAt(0))) {
                    score += 2;
                } else {
                    score -= 1;
                }
            }
        }

        return score;
    }
}