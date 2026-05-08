package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongFileEntity extends AbstractFileEntity {
	private static final long serialVersionUID = 1L;

	private Integer numPlays = Integer.valueOf(0);
	private String artistName;
	private String songName;
	private Integer trackNumber;

	public SongFileEntity() {
	}

	public SongFileEntity(AlbumFolderEntity parentAlbum, String name) {
		super(parentAlbum, name);
	}

	public Integer getNumPlays() {
		return numPlays;
	}

	public void setNumPlays(Integer numPlays) {
		this.numPlays = numPlays;
	}
	
	public AlbumFolderEntity getAlbum() {
	  return (AlbumFolderEntity)this.getParentFolder();
	}

	public String getArtistName() {

		if (this.artistName != null) {
			return this.artistName;
		} else if (this.artistName == null) {
			this.artistName = extractArtistName(getName());
		}
		if (this.artistName != null) {
			return this.artistName;
		}		
		return getParentFolder().getName();
	}

	public void setArtistName(String artistName) {
		this.artistName = artistName;
	}

	public String getSongName() {

		if (this.songName != null) {
			return this.songName;
		} else if (this.songName == null) {
			this.songName = extractSongName(getName());
		}
		if (this.songName != null) {
			return this.songName;
		}		
		return getName();
	}

	public void setSongName(String songName) {
		this.songName = songName;
	}

	public void setTrackNumber(Integer trackNumber) {
		this.trackNumber = trackNumber;
	}

	public Integer getTrackNumber() {

		if (this.trackNumber != null && this.trackNumber.intValue() > 0) {
			return this.trackNumber;
		}
		return extractTrackNumber(getName());
	}
	
	public static String extractArtistName(String filename) {

		String artist = "";
		
		Pattern pattern = Pattern.compile("^(.*?)\\s*-\\s*(\\d{2})\\s*-\\s*(.*?)\\.mp3$");
        Matcher m = pattern.matcher(filename);
        if (m.matches()) {
        	
            artist = m.group(1);
        }
        
        return artist;
	}

	public static String extractSongName(String filename) {

		String song = "";
		
		Pattern pattern = Pattern.compile("^(.*?)\\s*-\\s*(\\d{2})\\s*-\\s*(.*?)\\.mp3$");
        Matcher m = pattern.matcher(filename);
        if (m.matches()) {
        	
            song = m.group(3);
        }
        
        return song;
	}
	
	public static Integer extractTrackNumber(String filename) {

		Pattern pattern = Pattern.compile("^[^-]+-(\\d{2})-.*\\.mp3$");
		Matcher matcher = pattern.matcher(filename);

		if (matcher.matches()) {
			return Integer.parseInt(matcher.group(1));
		}
		return null;
	}
}