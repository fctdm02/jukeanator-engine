package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongFileEntity extends AbstractFileEntity implements LibraryItem {
  private static final long serialVersionUID = 1L;

  // Matches: ArtistName-TrackNum-SongName.ext
  // Artist may contain hyphens (e.g. "A-ha"), so the track number field — a 1-to-3 digit
  // token bounded by literal hyphens — acts as the unambiguous anchor. The non-greedy
  // artist group (.+?) combined with the greedy song-name group (.+) ensures that any
  // extra hyphens are consumed by the song name rather than the artist, which is the
  // safer failure mode.
  private static final Pattern FILENAME_PATTERN =
      Pattern.compile("^(.+?)\\s*-\\s*(\\d{1,3})\\s*-\\s*(.+)\\.[a-zA-Z0-9]+$");

  private Integer numPlays = Integer.valueOf(0);
  private String artistName;
  private String songName;
  private Integer trackNumber;

  private transient GenreFolderEntity parentGenre;
  private transient Year releaseDate;

  public SongFileEntity() {}

  public SongFileEntity(AlbumFolderEntity parentAlbum, String name) {
    super(parentAlbum, name);
  }

  @Override
  public Integer getNumPlays() {
    return numPlays;
  }

  @Override
  public GenreFolderEntity getParentGenre() {

    if (parentGenre == null) {

      FolderEntity parentFolder = this.getParentFolder();
      while (parentFolder instanceof RootFolderEntity == false) {

        if (parentFolder instanceof GenreFolderEntity) {
          parentGenre = (GenreFolderEntity) parentFolder;
          break;
        } else {
          parentFolder = parentFolder.getParentFolder();
        }
      }
      if (parentGenre == null) {
        parentGenre = new GenreFolderEntity(parentFolder, "None");
      }
    }
    return parentGenre;
  }

  public boolean hasExplicit() {
    return getAlbum().hasExplicit();
  }

  @Override
  public String getTitle() {
    return getSongName();
  }

  @Override
  public Year getReleaseDate() {

    if (releaseDate == null) {
      releaseDate = ((AlbumFolderEntity) this.getParentFolder()).getReleaseDate();
    }
    return releaseDate;
  }

  public void setNumPlays(Integer numPlays) {
    this.numPlays = numPlays;
  }

  public Integer incrementNumPlays() {
    this.numPlays = Integer.valueOf(this.numPlays.intValue() + 1);
    return this.numPlays;
  }

  public AlbumFolderEntity getAlbum() {
    return (AlbumFolderEntity) this.getParentFolder();
  }

  public String getArtistName() {
    return this.artistName;
  }

  public void setArtistName(String artistName) {
    this.artistName = artistName;
  }

  public String getSongName() {
    return this.songName;
  }

  public void setSongName(String songName) {
    this.songName = songName;
  }

  public void setTrackNumber(Integer trackNumber) {
    this.trackNumber = trackNumber;
  }

  public Integer getTrackNumber() {
    return this.trackNumber;
  }

  public static Integer extractTrackNumber(String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }

    Matcher m = FILENAME_PATTERN.matcher(filename.trim());
    if (m.matches()) {
      try {
        return Integer.valueOf(m.group(2));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  public static String extractArtistName(String filename) {
    if (filename == null || filename.isBlank())
      return "Unknown";
    Matcher m = FILENAME_PATTERN.matcher(filename.trim());
    return (m.matches()) ? m.group(1).trim() : "Unknown";
  }

  public static String extractSongName(String filename) {
    if (filename == null || filename.isBlank())
      return "Unknown";
    Matcher m = FILENAME_PATTERN.matcher(filename.trim());
    return (m.matches()) ? m.group(3).trim() : "Unknown";
  }
}
