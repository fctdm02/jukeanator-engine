import java.io.File;
import java.io.IOException;

public class Mp3ToVbr128 {

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

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", inputFile,
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