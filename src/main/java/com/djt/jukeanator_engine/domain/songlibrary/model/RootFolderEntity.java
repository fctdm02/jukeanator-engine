package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;

public class RootFolderEntity extends FolderEntity {
  private static final long serialVersionUID = 2L;

  private static final String CD_STATS_FILE_PREFIX = "CDStats";
  private static final String CD_STATS_FILE_SUFFIX = ".TXT";

  private Set<ArtistFromSongEntity> artistsFromSongs = new TreeSet<ArtistFromSongEntity>();

  private transient Map<Integer, GenreFolderEntity> genresMap;
  private transient Map<GenreFolderEntity, Set<AlbumFolderEntity>> albumsByGenreMap;
  private transient Map<Integer, ArtistFolderEntity> artistsMap;
  private transient Map<String, ArtistFromSongEntity> artistsFromSongsMap;
  private transient Map<Integer, AlbumFolderEntity> albumsMap;
  private transient Map<String, SongFileEntity> songsMap;

  // Used only by SongQueueService.loadPlaylistIntoQueue()
  private transient Map<String, SongFileEntity> songsByPathMap;

  public RootFolderEntity(String scanPath) {

    super(null, scanPath);
  }

  public String getRootPrefix() {
    return getName();
  }

  public ArtistFromSongEntity getArtistFromSong(String songArtistName) {

    if (this.artistsFromSongsMap == null) {
      this.artistsFromSongsMap = new TreeMap<>();
    }

    return this.artistsFromSongsMap.get(songArtistName);
  }

  public ArtistFromSongEntity addArtistFromSong(ArtistFromSongEntity artistFromSong) {

    this.artistsFromSongs.add(artistFromSong);
    return this.artistsFromSongsMap.put(artistFromSong.getName(), artistFromSong);
  }

  public Set<FolderEntity> pruneNonAlbumContainingChildFolders() {

    Set<FolderEntity> foldersToPrune = new TreeSet<>();

    for (FolderEntity childFolder : getChildFolders()) {
      if (childFolder.getChildFolders().isEmpty()
          && childFolder instanceof AlbumFolderEntity == false) {

        System.out.println("Pruning candidate: " + childFolder.getName());
        foldersToPrune.add(childFolder);
      } else {
        childFolder.pruneNonAlbumContainingChildFolders(foldersToPrune);
      }
    }

    if (!foldersToPrune.isEmpty()) {
      for (FolderEntity folderToPrune : foldersToPrune) {

        System.out.println("Pruning: " + folderToPrune.getNaturalIdentity());
        FolderEntity parentFolder = folderToPrune.getParentFolder();
        while (parentFolder != null && folderToPrune.getChildFolders().isEmpty()) {

          boolean removed = parentFolder.removeChild(folderToPrune);
          if (!removed) {
            System.err.println("Could not remove: " + folderToPrune.getName());
          }

          folderToPrune = parentFolder;
          if (parentFolder instanceof RootFolderEntity == false) {
            parentFolder = parentFolder.getParentFolder();
          } else {
            parentFolder = null;
          }
        }
      }
    }

    return foldersToPrune;
  }

  public List<AlbumFolderEntity> getAllAlbums() {

    Set<AlbumFolderEntity> allAlbums = new TreeSet<>();

    for (FolderEntity childFolder : getChildFolders()) {
      if (childFolder instanceof AlbumFolderEntity) {
        allAlbums.add((AlbumFolderEntity) childFolder);
      } else {
        childFolder.getAllAlbums(allAlbums);
      }
    }

    ArrayList<AlbumFolderEntity> list = new ArrayList<>();
    list.addAll(allAlbums);
    return list;
  }

  @Override
  public FolderEntity getParentFolder() {
    throw new IllegalStateException("getParentFolder() cannot be called on the Root");
  }

  @Override
  public String getNaturalIdentity() {
    return getName();
  }

  public Collection<GenreFolderEntity> getGenres() {
    return genresMap.values();
  }

  public Collection<AlbumFolderEntity> getAlbumsForGenre(Integer genreId) {
    GenreFolderEntity genre = genresMap.get(genreId);
    return albumsByGenreMap.get(genre);
  }

  /*
   * public Collection<ArtistFolderEntity> getArtists() { return artistsMap.values(); }
   */
  public Collection<ArtistFolderEntity> getArtists() {

    Map<String, ArtistFolderEntity> uniqueArtists = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    for (ArtistFolderEntity artist : artistsMap.values()) {

      String artistName = artist.getName();
      ArtistFolderEntity existing = uniqueArtists.get(artistName);

      if (existing == null) {
        uniqueArtists.put(artistName, artist);
        continue;
      }

      // Prefer ArtistFromSongEntity over ArtistFolderEntity
      if (existing instanceof ArtistFromSongEntity == false
          && artist instanceof ArtistFromSongEntity) {

        uniqueArtists.put(artistName, artist);
      }
    }

    return uniqueArtists.values();
  }

  public Collection<AlbumFolderEntity> getAlbums() {
    return albumsMap.values();
  }

  public Collection<SongFileEntity> getSongs() {
    return songsMap.values();
  }

  public void initialize() {

    this.genresMap = new TreeMap<>();
    this.albumsByGenreMap = new TreeMap<>();
    this.artistsMap = new TreeMap<>();
    this.albumsMap = new TreeMap<>();
    this.songsMap = new TreeMap<>();

    // Iterate over artistsFromSongs and add to artistsMap
    for (ArtistFromSongEntity artistFromSong : this.artistsFromSongs) {

      Integer artistId = artistFromSong.getPersistentIdentity();
      if (!this.artistsMap.containsKey(artistId)) {
        this.artistsMap.put(artistId, artistFromSong);
      }
    }

    for (AlbumFolderEntity album : getAllAlbums()) {

      GenreFolderEntity genre = album.getParentGenre();
      Integer genreId = genre.getPersistentIdentity();
      if (!this.genresMap.containsKey(genreId)) {
        this.genresMap.put(genreId, genre);
      }

      Set<AlbumFolderEntity> genreAlbums = null;
      if (!this.albumsByGenreMap.containsKey(genre)) {
        genreAlbums = new HashSet<>();
        genreAlbums.add(album);
        this.albumsByGenreMap.put(genre, genreAlbums);
      } else {
        genreAlbums = this.albumsByGenreMap.get(genre);
        if (!genreAlbums.contains(album)) {
          genreAlbums.add(album);
        }
      }

      // For compilation albums, the song artist's will
      // be in this collection, so there's no need to
      // add the "Compilations" artist itself, as any of these
      // albums will be retrievable via the song artist.
      ArtistFolderEntity artist = album.getParentArtist();
      if (!artist.getName().equals("Compilations")) {

        Integer artistId = artist.getPersistentIdentity();
        if (!this.artistsMap.containsKey(artistId)) {
          this.artistsMap.put(artistId, artist);
        }
      }

      Integer albumId = album.getPersistentIdentity();
      if (!this.albumsMap.containsKey(albumId)) {
        this.albumsMap.put(albumId, album);
      }

      for (SongFileEntity song : album.getChildSongs()) {
        this.songsMap.put(buildSongKey(albumId, song.getPersistentIdentity()), song);
      }
    }
  }

  public void restoreSongNumPlays(String scanPath) {

    String cdStatsPathName = buildCdStatsPathname(scanPath);
    Path statsFile = Path.of(cdStatsPathName);
    if (!Files.exists(statsFile)) {
      cdStatsPathName = scanPath + File.separator + CD_STATS_FILE_PREFIX + CD_STATS_FILE_SUFFIX;
      statsFile = Path.of(cdStatsPathName);
      if (!Files.exists(statsFile)) {
        System.err.println("CD stats file does not exist: " + cdStatsPathName);
        return;
      }
    }

    if (this.songsMap == null) {
      initialize();
    }

    Map<String, SongFileEntity> songsByPath = new HashMap<>();
    for (SongFileEntity song : this.songsMap.values()) {

      String songPathname = song.getNaturalIdentity().toLowerCase();
      songsByPath.put(songPathname, song);
    }

    // NEW FORMAT
    // <numPlays> <songPath>
    //
    // OLD FORMAT
    // <numPlays> <ignored> <ignored> <songPath>
    int restoredCount = 0;
    try (BufferedReader reader = Files.newBufferedReader(statsFile, StandardCharsets.UTF_8)) {

      String line;
      while ((line = reader.readLine()) != null) {

        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        try {

          int scanPathIndex = line.indexOf(scanPath);
          if (scanPathIndex < 0) {
            System.err.println("scanPath: [" + scanPath + "] does not exist in: [" + line + "]");
            continue;
          }

          String prefix = line.substring(0, scanPathIndex);
          String[] parts = prefix.split(" ");

          int numPlays = Integer.parseInt(parts[0].trim());
          if (numPlays > 0) {

            String songPath = line.substring(scanPathIndex).trim();
            SongFileEntity song = songsByPath.get(songPath.toLowerCase());
            if (song != null) {
              song.setNumPlays(numPlays);
            } else {
              System.err.println("Could not find song: " + songPath);
            }
          }
          restoredCount++;

        } catch (NumberFormatException e) {
          System.err.println("Could not parse num plays from line: " + line);
        } catch (Exception e) {
          System.err.println("Could not restore song stats from line: " + line);
          e.printStackTrace();
        }
      }
      System.out
          .println("Restored num plays processing completed for " + restoredCount + " log lines.");

    } catch (IOException e) {
      System.err.println("Failed to restore song num plays from CD Stats file: " + cdStatsPathName);
      e.printStackTrace();
    }
  }

  public void storeSongNumPlays(String scanPath) {

    String cdStatsPathName = buildCdStatsPathname(scanPath);

    List<SongFileEntity> songs = new ArrayList<>(this.songsMap.values());

    songs.sort(
        Comparator.comparing(SongFileEntity::getNaturalIdentity, String.CASE_INSENSITIVE_ORDER));

    Path statsFile = Path.of(cdStatsPathName);

    try (BufferedWriter writer = Files.newBufferedWriter(statsFile, StandardCharsets.UTF_8)) {

      for (SongFileEntity song : songs) {

        String songPath = song.getNaturalIdentity();
        if (songPath == null || songPath.isBlank()) {
          continue;
        }

        writer.write(song.getNumPlays() + " " + songPath);
        writer.newLine();
      }

      System.out.println("Stored num plays for " + songs.size() + " songs to " + cdStatsPathName);

    } catch (IOException e) {
      System.err.println("Failed to store song num plays to CD Stats file: " + cdStatsPathName);
      e.printStackTrace();
    }
  }

  private String buildCdStatsPathname(String scanPath) {

    String cdStatsPathName = null;
    OSType osType = OperatingSystemDetector.getOperatingSystem();
    if (osType == OSType.WINDOWS) {
      cdStatsPathName = scanPath + File.separator + CD_STATS_FILE_PREFIX + CD_STATS_FILE_SUFFIX;
    } else if (osType == OSType.MACOS) {
      cdStatsPathName =
          scanPath + File.separator + CD_STATS_FILE_PREFIX + "_mac" + CD_STATS_FILE_SUFFIX;
    } else {
      cdStatsPathName =
          scanPath + File.separator + CD_STATS_FILE_PREFIX + "_linux" + CD_STATS_FILE_SUFFIX;
    }
    return cdStatsPathName;
  }

  public void resetSongStatistics() {
    if (this.songsMap == null) {
      initialize();
    }
    for (SongFileEntity song : this.songsMap.values()) {
      song.setNumPlays(0);
    }
  }

  public GenreFolderEntity getGenreById(Integer id) throws EntityDoesNotExistException {
    GenreFolderEntity entity = genresMap.get(id);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Genre with id: [" + id + "] not found.");
  }

  public ArtistFolderEntity getArtistById(Integer id) throws EntityDoesNotExistException {
    ArtistFolderEntity entity = artistsMap.get(id);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Artist with id: [" + id + "] not found.");
  }

  public AlbumFolderEntity getAlbumById(Integer id) throws EntityDoesNotExistException {
    AlbumFolderEntity entity = albumsMap.get(id);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Album with id: [" + id + "] not found.");
  }

  public SongFileEntity getSongById(Integer albumId, Integer songId)
      throws EntityDoesNotExistException {

    String songKey = buildSongKey(albumId, songId);
    SongFileEntity entity = songsMap.get(songKey);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException(
        "Song with songId: [" + songId + "] and albumId: [" + albumId + "] not found.");
  }

  public SongFileEntity getSongByPath(String songPathname) throws EntityDoesNotExistException {

    if (songsByPathMap == null) {
      initializeSongsByPathMap();
    }

    SongFileEntity entity = songsByPathMap.get(songPathname);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Song with path: [" + songPathname + "] not found.");
  }

  private void initializeSongsByPathMap() {
    this.songsByPathMap = new HashMap<>();
    for (SongFileEntity song : this.songsMap.values()) {
      String songPathname = song.getNaturalIdentity();
      if (songPathname != null && !this.songsByPathMap.containsKey(songPathname)) {
        this.songsByPathMap.put(songPathname, song);
      }
    }
  }

  private String buildSongKey(Integer albumId, Integer songId) {

    return albumId.toString() + "__" + songId.toString();
  }
}
