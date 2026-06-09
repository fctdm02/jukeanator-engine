package com.djt.jukeanator_engine.domain.songlibrary.model;

import static java.util.Objects.requireNonNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

public class RootFolderEntity extends FolderEntity {
  private static final long serialVersionUID = 1L;

  private static final String CD_STATS_FILE = "/mnt/readyNAS/Rock_On_Third/CDStats_linux.txt";

  private String rootPrefix;

  private transient Map<Integer, GenreFolderEntity> genresMap;
  private transient Map<GenreFolderEntity, Set<AlbumFolderEntity>> albumsByGenreMap;
  private transient Map<Integer, ArtistFolderEntity> artistsMap;
  private transient Map<Integer, AlbumFolderEntity> albumsMap;
  private transient Map<String, SongFileEntity> songsMap;

  // Used only by SongQueueService.loadPlaylistIntoQueue()
  private transient Map<String, SongFileEntity> songsByPathMap;

  public RootFolderEntity() {}

  /**
   * Windows: C:\Users\Admin\Music rootPrefix is: C:\
   * 
   * Linux: /Users/Admin/Music rootPrefix is: /
   * 
   * @param rootPrefix
   * @param name
   */
  public RootFolderEntity(String rootPrefix, String name) {
    super(null, name);
    requireNonNull(rootPrefix, "rootPrefix cannot be null");
    this.rootPrefix = rootPrefix;
  }

  public String getRootPrefix() {
    return this.rootPrefix;
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

    StringBuilder sb = new StringBuilder();
    if (this.rootPrefix != null) {
      sb.append(this.rootPrefix);
    } else {
      sb.append(File.separatorChar);
    }
    sb.append(getName());
    return sb.toString();
  }

  public Collection<GenreFolderEntity> getGenres() {
    return genresMap.values();
  }

  public Collection<AlbumFolderEntity> getAlbumsForGenre(Integer genreId) {
    GenreFolderEntity genre = genresMap.get(genreId);
    return albumsByGenreMap.get(genre);
  }

  public Collection<ArtistFolderEntity> getArtists() {
    return artistsMap.values();
  }

  public Collection<AlbumFolderEntity> getAlbums() {
    return albumsMap.values();
  }

  public Collection<SongFileEntity> getSongs() {
    return songsMap.values();
  }

  public void initialize() {

    this.genresMap = new HashMap<>();
    this.albumsByGenreMap = new HashMap<>();
    this.artistsMap = new HashMap<>();
    this.albumsMap = new HashMap<>();
    this.songsMap = new HashMap<>();

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

      ArtistFolderEntity artist = album.getParentArtist();
      Integer artistId = artist.getPersistentIdentity();
      if (!this.artistsMap.containsKey(artistId)) {
        this.artistsMap.put(artistId, artist);
      }

      Integer albumId = album.getPersistentIdentity();
      if (!this.albumsMap.containsKey(albumId)) {
        this.albumsMap.put(albumId, album);
      }

      for (SongFileEntity song : album.getChildSongs()) {
        String songKey = albumId.toString() + song.getPersistentIdentity().toString();
        if (!this.songsMap.containsKey(songKey)) {
          this.songsMap.put(songKey, song);
        }
      }
    }
  }

  public void restoreSongNumPlays() {

    if (this.songsMap == null) {
      initialize();
    }

    Path statsFile = Path.of(CD_STATS_FILE);

    if (!Files.exists(statsFile)) {
      System.err.println("CD stats file does not exist: " + CD_STATS_FILE);
      return;
    }

    Map<String, SongFileEntity> songsByPath = new HashMap<>();
    for (SongFileEntity song : this.songsMap.values()) {

      String naturalIdentity = normalizeSongPath(song.getNaturalIdentity());
      songsByPath.put(naturalIdentity, song);
    }

    int restoredCount = 0;
    try (BufferedReader reader = Files.newBufferedReader(statsFile, StandardCharsets.UTF_8)) {

      String line;
      while ((line = reader.readLine()) != null) {

        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        // Split into at most 4 parts:
        // plays, ignored, ignored, full song path
        String[] parts = line.split("\\s+", 4);

        if (parts.length < 4) {
          System.err.println("Skipping malformed CD stats line: " + line);
          continue;
        }

        try {

          int numPlays = Integer.parseInt(parts[0]);
          if (numPlays > 0) {
            String songPath = normalizeSongPath(parts[3]);
            SongFileEntity song = songsByPath.get(songPath);
            if (song != null) {
              song.setNumPlays(numPlays);
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
      System.out.println("Restored num plays for " + restoredCount + " songs.");

    } catch (IOException e) {

      System.err.println("Failed to restore song num plays from: " + CD_STATS_FILE);
      e.printStackTrace();
    }
  }

  public void resetSongStatistics() {

    if (this.songsMap == null) {
      initialize();
    }

    for (SongFileEntity song : this.songsMap.values()) {

      song.setNumPlays(0);
    }
  }

  private String normalizeSongPath(String path) {

    if (path == null) {
      return "";
    }

    return path.replace('\\', '/').trim().toLowerCase();
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

    String songKey = albumId.toString() + songId.toString();
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

    SongFileEntity entity = songsMap.get(songPathname);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Song with path: [" + songPathname + "] not found.");
  }

  private void initializeSongsByPathMap() {

    songsByPathMap = new HashMap<>();

    for (SongFileEntity song : this.songsMap.values()) {
      String songPathname = song.getNaturalIdentity();
      if (!this.songsMap.containsKey(songPathname)) {
        this.songsMap.put(songPathname, song);
      }
    }
  }
}
