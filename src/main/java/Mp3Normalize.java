import java.io.File;
import java.io.IOException;

public class Mp3Normalize {

    private static final String SOURCE_DIR ="/home/tmyers/Music/AllMusic";
    private static final String DEST_DIR = "/home/tmyers/Music/CompressedMusic";

    public static void main(String[] args) throws IOException {

    	File sourceDir = new File(SOURCE_DIR);
    	File destDir = new File(DEST_DIR);

        File[] files = sourceDir.listFiles();
        
        for (int i=0; i < files.length; i++) {
        	
        	File file = files[i];        	
        	if (file.getAbsolutePath().endsWith(".mp3")) {
        		
        		System.out.println("Processing " + i + " of " + files.length + ": " + file.getName());
        		
        		processFile(destDir, file);
        	}
        }
    }

    private static void processFile(File destDir, File file) {
    	
        try {
        	
            String inputFile = file.getAbsolutePath();
            String outputFile = destDir.getAbsolutePath() + File.separator + file.getName();

            // Single-pass loudness normalization (simpler)
            String filter = "loudnorm=I=-14:LRA=11:TP=-1.5";
            
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", inputFile,
                    "-af", filter,
                    "-c:a", "libmp3lame",
                    "-q:a", "4",
                    outputFile
            );
            
            pb.inheritIO();

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("Failed: " + file);
            }

        } catch (Exception e) {
            System.err.println("Error processing: " + file);
            e.printStackTrace();
        }
    }
}









/*



import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class Mp3Normalize {

    private static final Path SOURCE_DIR =
            Paths.get("/home/tmyers/Music/AllMusic");

    private static final Path DEST_DIR =
            Paths.get("/home/tmyers/Music/NormalizedMusic");

    public static void main(String[] args) throws IOException {

        if (!Files.isDirectory(SOURCE_DIR)) {
            System.err.println("Invalid source directory: " + SOURCE_DIR);
            return;
        }

        Files.createDirectories(DEST_DIR);

        try (Stream<Path> files = Files.list(SOURCE_DIR)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
                 .forEach(Mp3Normalize::processFile);
        }
    }

    private static void processFile(Path file) {
        try {
            String input = file.toAbsolutePath().toString();

            String outputName = file.getFileName()
                    .toString()
                    .replaceAll("(?i)\\.mp3$", "_normalized.mp3");

            Path outputPath = DEST_DIR.resolve(outputName);

            // Single-pass loudness normalization (simpler)
            String filter = "loudnorm=I=-14:LRA=11:TP=-1.5";

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", input,
                    "-af", filter,
                    "-c:a", "libmp3lame",
                    "-q:a", "4",
                    outputPath.toString()
            );

            pb.inheritIO();

            System.out.println("Normalizing: " + input);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("Failed: " + input);
            }

        } catch (Exception e) {
            System.err.println("Error processing: " + file);
            e.printStackTrace();
        }
    }
}*/