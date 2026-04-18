import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class DeleteVbr128Files {

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get("/home/tmyers/Music/AllMusic");

        if (!Files.isDirectory(dir)) {
            System.err.println("Invalid directory: " + dir);
            return;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase().contains("vbr128"))
                 .forEach(DeleteVbr128Files::deleteFile);
        }
    }

    private static void deleteFile(Path file) {
        try {
            System.out.println("Deleting: " + file);
            Files.delete(file);
        } catch (IOException e) {
            System.err.println("Failed to delete: " + file);
            e.printStackTrace();
        }
    }
}