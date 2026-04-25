import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BPMFilter {

    private static final String MUSIC_DIR = "/home/tmyers/Music/CompressedMusic";

    private static final Pattern TIME_PATTERN = Pattern.compile("pts_time:([0-9\\.]+)");

    public static void main(String[] args) throws IOException {
    
    	File musicDir = new File(MUSIC_DIR);
    	File[] files = musicDir.listFiles();
    	for (int i=0; i< files.length; i++) {

    		File file = files[i];
    		if (file.toString().toLowerCase().endsWith(".mp3")) {

    			double bpm = BPMFilter.processFile(file);
    			System.out.println("Processing " + i + " of " + files.length + ": " + file.getAbsolutePath() + " BPM: " + bpm);
    		}
    	}
    }

    private static double processFile(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", file.getAbsolutePath(),
                    "-filter_complex", "beatdetect",
                    "-f", "null",
                    "-"
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<Double> beatTimes = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = TIME_PATTERN.matcher(line);
                    if (m.find()) {
                        beatTimes.add(Double.parseDouble(m.group(1)));
                    }
                }
            }

            process.waitFor();

            return estimateBPM(beatTimes);

        } catch (Exception e) {
            System.err.println("Error processing: " + file);
            e.printStackTrace();
        }
        
        return 0d;
    }

    private static double estimateBPM(List<Double> beats) {
        if (beats.size() < 2) return 0;

        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < beats.size(); i++) {
            intervals.add(beats.get(i) - beats.get(i - 1));
        }

        double avgInterval = intervals.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        if (avgInterval == 0) return 0;

        return 60.0 / avgInterval;
    }
}