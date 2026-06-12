package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

public class AlbumFolderEntity extends FolderEntity implements LibraryItem {
  private static final long serialVersionUID = 1L;

  public static final String METADATA_FILENAME = "metadata.txt";
  public static final String COVER_ART_FILENAME = "cover.jpg";
  public static final String JPG_EXTENSION = ".jpg";

  private AlbumCoverArtFileEntity coverArt;
  private AlbumMetaDataFileEntity metaData;
  private Set<SongFileEntity> childSongs = new TreeSet<SongFileEntity>();

  private transient GenreFolderEntity parentGenre;
  private transient Year releaseDate;
  private transient Boolean isCompilation; // Returns true if there are at least 2 songs where the
                                           // song artist (that is embedded in the song filename)
                                           // are different from one another

  public AlbumFolderEntity() {}

  public AlbumFolderEntity(FolderEntity parentFolder, String name) {
    super(parentFolder, name);
  }

  public AlbumCoverArtFileEntity getCoverArt() {
    return this.coverArt;
  }

  public AlbumMetaDataFileEntity getMetaData() {
    return this.metaData;
  }

  @Override
  public Integer getNumPlays() {

    int numPlays = 0;
    for (SongFileEntity childSong : childSongs) {

      numPlays = numPlays + childSong.getNumPlays();
    }
    return Integer.valueOf(numPlays);
  }

  public boolean addChildSong(SongFileEntity childSong) throws EntityAlreadyExistsException {
    return addChild(childSongs, childSong, this);
  }

  public List<SongFileEntity> getChildSongs() {

    return childSongs.stream().sorted(Comparator.comparing(SongFileEntity::getTrackNumber,
        Comparator.nullsLast(Integer::compareTo))).toList();
  }

  public SongFileEntity getChildSong(Integer persistentIdentity)
      throws EntityDoesNotExistException {

    for (SongFileEntity childSong : childSongs) {
      if (childSong.getPersistentIdentity().equals(persistentIdentity)) {
        return childSong;
      }
    }
    throw new EntityDoesNotExistException("Child Song with persistentIdentity: ["
        + persistentIdentity + "] not found in [" + this.getNaturalIdentity() + "].");
  }

  public SongFileEntity getChildSongByName(String name) throws EntityDoesNotExistException {

    for (SongFileEntity childSong : childSongs) {
      if (childSong.getName().equals(name)) {
        return childSong;
      }
    }
    throw new EntityDoesNotExistException(
        "Child Song with name: [" + name + "] not found in [" + this.getNaturalIdentity() + "].");
  }

  public ArtistFolderEntity getParentArtist() {

    FolderEntity parentFolder = this.getParentFolder();
    while (parentFolder instanceof RootFolderEntity == false) {

      if (parentFolder instanceof ArtistFolderEntity) {
        return (ArtistFolderEntity) parentFolder;
      } else {
        parentFolder = parentFolder.getParentFolder();
      }
    }
    ArtistFolderEntity dummyArtist = new ArtistFolderEntity(parentFolder, "Compilations");
    dummyArtist.setPersistentIdentity(999999);
    return dummyArtist;
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

  public boolean hasValidCoverArt() {
    return this.coverArt.isValid();
  }

  public boolean hasValidMetadata() {
    return this.metaData.isValid();
  }

  public void createCoverArtEntity() {
    this.coverArt = new AlbumCoverArtFileEntity(this, COVER_ART_FILENAME);
  }

  public void createMetadataEntity() {
    this.metaData = new AlbumMetaDataFileEntity(this, METADATA_FILENAME);
  }

  public boolean hasExplicit() {
    return this.metaData.hasExplicit();
  }

  public String getRecordLabel() {
    return this.metaData.getRecordLabel();
  }

  @Override
  public Year getReleaseDate() {

    if (releaseDate == null) {

      String strReleaseDate = this.metaData.getReleaseDate();
      if (strReleaseDate == null || strReleaseDate.isEmpty()) {
        releaseDate = Year.parse("1950");
      } else {
        try {
          releaseDate = Year.parse(strReleaseDate);
        } catch (Exception e) {
          System.err.println(
              "Could not parse release date: " + strReleaseDate + " into Year: " + e.getMessage());
          releaseDate = Year.parse("1950");
        }
      }
    }
    return releaseDate;
  }

  public void setReleaseDate(Year year) {

    if (year != null) {
      this.metaData.setReleaseDate(year.toString());
      this.getMetaData().writeMetadataToFileSystem();
    }
  }

  @Override
  public String getTitle() {
    return this.getName();
  }

  public String getCoverArtPath() {
    return this.coverArt.getNaturalIdentity();
  }

  public Boolean isCompilation() {
    return this.isCompilation;
  }

  /**
   * Post-processes child songs to guarantee sequential track numbers if anomalies are found, and
   * detects whether this album functions as a compilation.
   */
  public void postProcessSongs() {
    List<SongFileEntity> songs = getChildSongs();
    if (songs.isEmpty()) {
      this.isCompilation = Boolean.FALSE;
      return;
    }

    boolean reassignTracks = false;
    Set<Integer> seenTracks = new java.util.HashSet<>();
    Set<String> uniqueArtists = new java.util.HashSet<>();

    int expectedTrack = 1;

    for (SongFileEntity song : songs) {
      Integer track = song.getTrackNumber();

      // Condition check for Item #1: duplicate or non-consecutive tracking
      if (track == null || seenTracks.contains(track) || track.intValue() != expectedTrack) {
        reassignTracks = true;
      }
      if (track != null) {
        seenTracks.add(track);
      }
      expectedTrack++;

      // Condition check for Item #2: capture artist names safely
      String artist = song.getArtistName();
      if (artist != null && !artist.trim().isBlank() && !artist.equalsIgnoreCase("Unknown")) {
        uniqueArtists.add(artist.trim().toLowerCase());
      }
    }

    // Item #1 Execution: Re-assign consecutive track numbers based on current sort index
    if (reassignTracks) {
      for (int i = 0; i < songs.size(); i++) {
        songs.get(i).setTrackNumber(Integer.valueOf(i + 1));
      }
    }

    // Item #2 Execution: Flag compilation if at least 2 distinct artists are found
    if (uniqueArtists.size() >= 2) {
      this.isCompilation = Boolean.TRUE;
    } else {
      this.isCompilation = Boolean.FALSE;
    }
  }
}