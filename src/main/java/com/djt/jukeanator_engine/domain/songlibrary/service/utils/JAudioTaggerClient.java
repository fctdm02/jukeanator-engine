package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.springframework.context.annotation.Bean;

import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;

/**
 * @author tmyers
 */
public final class JAudioTaggerClient {

	public static final String GENRE_NAME = "GenreName";
	public static final String ALBUM_NAME = "AlbumName";
	public static final String ARTIST_NAME = "ArtistName";
	public static final String TRACK_NUMBER = "TrackNumber";
	public static final String SONG_NAME = "SongName";
	public static final String RECORD_LABEL = "RecordLabel";
	public static final String RELEASE_DATE = "ReleaseDate";

	@Bean
	public JAudioTaggerClient jAudioTaggerClient() {
		return new JAudioTaggerClient();
	}
	
	public JAudioTaggerClient() {
		
	}
	
	public Map<String, String> getTags(String songFile) {

		try {

			Map<String, String> tags = new TreeMap<>();

			File file = new File(songFile);
			if (file.getName().startsWith("._")) {
			    return tags;
			}
			AudioFile audioFile = AudioFileIO.read(file);
			Tag tag = audioFile.getTag();
			if (tag != null) {

				tags.put(GENRE_NAME, tag.getFirst(FieldKey.GENRE));
				
				String album = tag.getFirst(FieldKey.ALBUM);
				int index = album.lastIndexOf('(');
				if (index != -1) {
					album = album.substring(0, index);
				}
				tags.put(ALBUM_NAME, album);
				tags.put(ARTIST_NAME, tag.getFirst(FieldKey.ARTIST));
				tags.put(TRACK_NUMBER, tag.getFirst(FieldKey.TRACK));
				tags.put(SONG_NAME, tag.getFirst(FieldKey.TITLE));

				String recordLabel = tag.getFirst(FieldKey.RECORD_LABEL);
				if (recordLabel == null || recordLabel.trim().isBlank()) {
					recordLabel = tag.getFirst(FieldKey.COPYRIGHT);
				}
				tags.put(RECORD_LABEL, recordLabel);

				String releaseDate = tag.getFirst(FieldKey.YEAR);
				if (releaseDate == null || releaseDate.trim().isBlank()) {
					releaseDate = tag.getFirst(FieldKey.ORIGINAL_YEAR);
					if (releaseDate == null || releaseDate.trim().isBlank()) {
						releaseDate = tag.getFirst(FieldKey.ORIGINALRELEASEDATE);
					}
				}
				if (releaseDate != null && releaseDate.indexOf('-') > 0) {
					releaseDate = releaseDate.substring(0, releaseDate.indexOf('-'));
				}
				tags.put(RELEASE_DATE, releaseDate);
			}
			return tags;

		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);			
		} catch (Exception e) {
			throw new SongLibraryException("Could not read audio tag for: " + songFile + ", error: " + e.getMessage(), e);
		}
		return null;
	}

	public boolean hasGenre(String songFile) {

		try {

			File file = new File(songFile);
			if (file.getName().startsWith("._")) {
			    return false;
			}
			AudioFile audioFile = AudioFileIO.read(file);
			Tag tag = audioFile.getTag();
			if (tag != null) {

				String genre = tag.getFirst(FieldKey.GENRE);
				if (genre != null && !genre.trim().isBlank()) {
					
					return true;
				}
			}
			return false;

		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);			
		} catch (Exception e) {
			throw new SongLibraryException("Could not determine if cover art tag exists from: " + songFile + ", error: " + e.getMessage(), e);
		}
		return false;
	}
	
	public boolean embedGenre(String genre, String songFile) {
		
		try {

	        File mp3File = new File(songFile);

	        AudioFile audioFile = AudioFileIO.read(mp3File);
	        Tag tag = audioFile.getTagOrCreateAndSetDefault();

	        // Set genre (replaces existing value)
	        tag.setField(FieldKey.GENRE, genre);

	        // Save changes
	        audioFile.commit();

	        System.out.println("Genre updated to: " + genre + " for: " + songFile);	        
			return true;
			
		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);			
		} catch (Exception e) {
			throw new SongLibraryException("Could not extract/write cover art image from: " + songFile + ", error: " + e.getMessage(), e);
		}
		return false;
	}	
	
	public boolean hasCoverArt(String songFile) {

		try {

			File file = new File(songFile);
			if (file.getName().startsWith("._")) {
			    return false;
			}
			AudioFile audioFile = AudioFileIO.read(file);
			Tag tag = audioFile.getTag();
			if (tag != null) {

				Artwork artwork = tag.getFirstArtwork();

				if (artwork != null) {

					/*
		            byte[] data = artwork.getBinaryData();
		            int sizeBytes = data.length;

		            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
		            int width = img.getWidth();
		            int height = img.getHeight();
		            */
		            
					return true;
				}
			}
			return false;

		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);			
		} catch (Exception e) {
			throw new SongLibraryException("Could not determine if cover art tag exists from: " + songFile + ", error: " + e.getMessage(), e);
		}
		return false;
	}
	
	public boolean extractCoverArt(String coverArtPath, String songFile) {

		try {

			/*
			 * TODO: TDM: If the image is PNG, then we need to convert it to JPG String
			 * mimeType = artwork.getMimeType(); String extension =
			 * mimeType.equals("image/png") ? ".png" : ".jpg";
			 */

			File file = new File(songFile);
			if (file.getName().startsWith("._")) {
			    return false;
			}
			AudioFile audioFile = AudioFileIO.read(file);
			Tag tag = audioFile.getTag();
			if (tag != null) {

				Artwork artwork = tag.getFirstArtwork();

				if (artwork != null) {

					byte[] imageData = artwork.getBinaryData();

					try (FileOutputStream fos = new FileOutputStream(coverArtPath)) {
						fos.write(imageData);
					}

					BufferedImage image = ImageIO.read(new File(coverArtPath));
					int width = image.getWidth();
					int height = image.getHeight();
					if (width > 500 || height > 500) {

						BufferedImage original = ImageIO.read(new File(coverArtPath));
						BufferedImage resized = resizeHighQuality(original, 500, 500);
						ImageIO.write(resized, "jpg", new File(coverArtPath));
					}
					
					return true;
				}
			}
			return false;

		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);			
		} catch (Exception e) {
			throw new SongLibraryException("Could not extract/write cover art image from: " + songFile + ", error: " + e.getMessage(), e);
		}
		return false;
	}
	
	public boolean embedCoverArt(String coverArtPath, String songFile) {
		
		try {

	        File mp3File = new File(songFile);
	        File coverFile = new File(coverArtPath);

	        AudioFile audioFile = AudioFileIO.read(mp3File);
	        Tag tag = audioFile.getTagOrCreateAndSetDefault();

	        // Create artwork from file
	        Artwork artwork = ArtworkFactory.createArtworkFromFile(coverFile);

	        // Remove existing artwork (optional but recommended)
	        tag.deleteArtworkField();

	        // Set new artwork
	        tag.setField(artwork);

	        // Save changes
	        audioFile.commit();

	        System.out.println("Album artwork added successfully for: " + songFile);
			return true;
			
		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);			
		} catch (Exception e) {
			throw new SongLibraryException("Could not extract/write cover art image from: " + songFile + ", error: " + e.getMessage(), e);
		}
		return false;
	}
	
	public Path renameSongFromTag(String songFile) {
		return renameSongFromTag(songFile, true);
	}	
	
	public Path renameSongFromTag(String songFile, boolean includeTrackNumber) {

		try {
			File file = new File(songFile);
			if (file.getName().startsWith("._")) {
			    return null;
			}			
			AudioFile audioFile = AudioFileIO.read(file);
			Tag tag = audioFile.getTag();
			if (tag != null) {

				String artistName = tag.getFirst(FieldKey.ARTIST);
				String trackNumber =  tag.getFirst(FieldKey.TRACK);
				if (trackNumber.length() == 1) {
					trackNumber = "0" + trackNumber;
				}
				String songName =  tag.getFirst(FieldKey.TITLE);
				
				String parentPath = file.getParent();
				String extension = file.getName().substring(file.getName().lastIndexOf("."));
				
		        Path source = Paths.get(file.getAbsolutePath());
		        
		        String renamedSongFile = null;
		        
		        if (includeTrackNumber) {
		        	renamedSongFile = parentPath + File.separator + artistName + "-" + trackNumber + "-" + songName + extension;
		        } else {
		        	renamedSongFile = parentPath + File.separator + artistName + "-" + songName + extension;
		        }
		        		
		        renamedSongFile = stripExtraneousPhrases(renamedSongFile);
		        		
		        Path target = Paths.get(renamedSongFile);
				Path path = Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				
				return path;
			}			
		} catch (InvalidAudioFrameException | CannotReadException e) {
			System.out.println("Skipping invalid audio file: " + songFile);
		} catch (Exception e) {
			throw new SongLibraryException("Could not read audio tag for: " + songFile + ", error: " + e.getMessage(), e);
		}
		return null;
	}

	public static String stripExtraneousPhrases(String filename) {
		
	    return filename
	    		.replaceAll(" - Remastered \\d{4}(?=\\.mp3$)", "")
	    		.replaceAll(" - Remastered Version \\d{4}(?=\\.mp3$)", "")
	    		.replaceAll(" - \\d{4} Remaster(?=\\.mp3$)", "")
	    		.replaceAll("\\s*\\(\\d{4}\\s*Remaster\\)", "")
	    		.replaceAll("\\(\\d{4}-\\sRemaster\\)", "")
	    		.replaceAll(" - Remastered Version", "")
	    		.replaceAll(" - Remastered", "")
	    		.replaceAll(" - Radio Edit", "")
	    		.replaceAll(" - Edit", "")
	    		.replaceAll(" - Radio Version", "")
	    		.replaceAll(" - Single Version", "");
	}
    
	private static BufferedImage resizeHighQuality(BufferedImage original, int targetWidth, int targetHeight) {

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