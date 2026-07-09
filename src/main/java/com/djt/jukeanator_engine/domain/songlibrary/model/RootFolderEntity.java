package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.io.File;
import java.io.IOException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.utils.FileSystemHelper;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;

public class RootFolderEntity extends FolderEntity {

  private static final Logger log = LoggerFactory.getLogger(RootFolderEntity.class);

  private static final long serialVersionUID = 2L;


  // Used to read/write CDStats file
  private static final String CD_STATS = "CDStats.TXT";
  private static final String CD_STATS_BACKUP = "CDStats_backup.TXT";
  private static final FileSystemHelper fileSystemHelper = new FileSystemHelper();

  private Set<ArtistFromSongEntity> artistsFromSongs = new TreeSet<ArtistFromSongEntity>();

  private transient Map<Integer, GenreFolderEntity> genresMap;
  private transient Map<GenreFolderEntity, Set<AlbumFolderEntity>> albumsByGenreMap;
  private transient Map<String, ArtistFolderEntity> artistsMap;
  private transient Map<String, ArtistFromSongEntity> artistsFromSongsMap;
  private transient Map<Integer, AlbumFolderEntity> albumsMap;
  private transient Map<String, SongFileEntity> songsMap;

  // Used to load playlists into the queue
  private transient Map<String, SongFileEntity> songsByPath;

  public RootFolderEntity(String rootPath) {
    super(null, rootPath);
  }

  public void initialize() {

    this.genresMap = new TreeMap<>();
    this.albumsByGenreMap = new TreeMap<>();
    this.artistsMap = new TreeMap<>();
    this.albumsMap = new TreeMap<>();
    this.songsMap = new TreeMap<>();

    // Iterate over artistsFromSongs and add to artistsMap
    for (ArtistFromSongEntity artistFromSong : this.artistsFromSongs) {

      String artistName = artistFromSong.getName();
      if (!this.artistsMap.containsKey(artistName)) {
        this.artistsMap.put(artistName, artistFromSong);
      }
    }

    List<AlbumFolderEntity> allAlbums = getAllAlbums();
    for (AlbumFolderEntity album : allAlbums) {

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
      String artistName = artist.getName();
      if (!artistName.equals("Compilations")) {

        if (!this.artistsMap.containsKey(artistName)) {
          this.artistsMap.put(artistName, artist);
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

  public String getRootPath() {
    return getName();
  }

  public void setRootPath(String rootPath) {
    this.setName(rootPath);
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

  public ArtistFolderEntity getArtistByName(String artistName) throws EntityDoesNotExistException {
    ArtistFolderEntity entity = artistsMap.get(artistName);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Artist with name: [" + artistName + "] not found.");
  }

  public ArtistFolderEntity getArtistById(Integer artistId) throws EntityDoesNotExistException {
    for (ArtistFolderEntity artist : artistsMap.values()) {
      if (artistId.equals(artist.getPersistentIdentity())) {
        return artist;
      }
    }
    throw new EntityDoesNotExistException("Artist with id: [" + artistId + "] not found.");
  }

  public ArtistFolderEntity getArtistByAlbumId(Integer albumId) throws EntityDoesNotExistException {
    AlbumFolderEntity album = getAlbumById(albumId);
    String artistName = album.getParentArtist().getName();
    if ("Compilations".equals(artistName)) {
      throw new EntityDoesNotExistException(
          "Album " + albumId + " belongs to Compilations; use song artist lookup instead.");
    }
    ArtistFolderEntity canonical = artistsMap.get(artistName);
    if (canonical != null) {
      return canonical;
    }
    throw new EntityDoesNotExistException(
        "Artist with name: [" + artistName + "] not found in artistsMap.");
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

  private String buildSongKey(Integer albumId, Integer songId) {

    return albumId.toString() + "__" + songId.toString();
  }

  public SongFileEntity getSongByPath(String songPathName) throws EntityDoesNotExistException {

    if (songsByPath == null) {
      initializeSongsByPath();
    }

    SongFileEntity entity = songsByPath.get(songPathName.replace(":\\\\", ":\\"));
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Song with path: [" + songPathName + "] not found.");
  }

  private void initializeSongsByPath() {

    this.songsByPath = new HashMap<>();
    for (SongFileEntity song : this.songsMap.values()) {
      String songPathName = song.getNaturalIdentity().replace(":\\\\", ":\\");
      if (songPathName != null && !this.songsByPath.containsKey(songPathName)) {
        this.songsByPath.put(songPathName, song);
      }
    }
  }

  // CD STATS RELATED
  public int restoreSongStatisticsForRootPath(String rootPath, String rootPathWindows,
      String rootPathUnix) {

    String cdStatsPathName = rootPath + File.separator + RootFolderEntity.CD_STATS;
    if (!fileSystemHelper.exists(cdStatsPathName)) {

      System.err.println("CD stats file does not exist: " + cdStatsPathName);
      return 0;
    }

    int numRestored =
        restoreSongStatisticsForFile(rootPath, rootPathWindows, rootPathUnix, cdStatsPathName);
    if (numRestored == 0) {

      numRestored = restoreSongStatisticsForFile(rootPath, rootPathWindows, rootPathUnix,
          rootPath + File.separator + RootFolderEntity.CD_STATS_BACKUP);
    }
    return numRestored;
  }

  public int restoreSongStatisticsForFile(String rootPath, String rootPathWindows,
      String rootPathUnix, String filename) {

    // Normalize rootPathWindows in case the config value was loaded with a double backslash
    // after the drive letter (e.g. "R:\\Rock_On_Third" instead of "R:\Rock_On_Third").
    // CDStats.TXT always stores single-backslash Windows paths, so we must match that form.
    String normalizedRootPathWindows = rootPathWindows.replace(":\\\\", ":\\");

    OSType osType = OperatingSystemDetector.getOperatingSystem();

    if (!fileSystemHelper.exists(filename)) {
      System.err.println("CD stats file does not exist: " + filename);
      return 0;
    }

    if (this.songsMap == null) {
      initialize();
    }

    Map<String, SongFileEntity> songsByPath = new HashMap<>();
    for (SongFileEntity song : this.songsMap.values()) {

      String songPathName = song.getNaturalIdentity().toLowerCase();
      songsByPath.put(songPathName.replace(":\\\\", ":\\"), song);
    }

    // NEW FORMAT
    // <numPlays> <songPath>
    //
    // OLD FORMAT
    // <numPlays> <ignored> <ignored> <songPath>
    int restoredCount = 0;
    try {
      List<String> lines = fileSystemHelper.readNonBlankLines(filename);

      for (String rawLine : lines) {

        String line = rawLine.trim();
        if (line.isEmpty()) {
          continue;
        }

        try {

          // See if we need to fix up song pathnames that we read in.
          boolean switchToUnixFormat = false;
          boolean switchToWindowsFormat = false;
          if (osType == OSType.WINDOWS && !line.contains(normalizedRootPathWindows)) {
            switchToWindowsFormat = true;
          } else if ((osType == OSType.LINUX || osType == OSType.MACOS)
              && !line.contains(rootPathUnix)) {
            switchToUnixFormat = true;
          }

          if (switchToUnixFormat) {
            line = line.replace(normalizedRootPathWindows, rootPathUnix).replace("\\", "/");
          } else if (switchToWindowsFormat) {
            line = line.replace(":\\", ":\\\\").replace(rootPathUnix, normalizedRootPathWindows)
                .replace("/", "\\");
          }

          int rootPathIndex = line.indexOf(rootPath);
          if (rootPathIndex < 0) {
            System.err.println("rootPath: [" + rootPath + "] does not exist in: [" + line + "]");
            continue;
          }

          String prefix = line.substring(0, rootPathIndex);
          String[] parts = prefix.split(" ");

          int numPlays = Integer.parseInt(parts[0].trim());
          if (numPlays > 0) {

            String songPath = line.substring(rootPathIndex).trim();
            SongFileEntity song = songsByPath.get(songPath.toLowerCase());
            if (song != null) {
              song.setNumPlays(numPlays);
              restoredCount++;
            } else {
              System.err.println("Could not find song: " + songPath);
            }
          }

        } catch (NumberFormatException e) {
          log.warn("Could not parse num plays from line: {}", line);
        } catch (Exception e) {
          log.warn("Could not restore song stats from line: {}", line, e);
        }
      }
      log.info("Restored num plays processing completed for {} log lines.", restoredCount);

    } catch (IOException e) {
      throw new SongLibraryServiceException(
          "Failed to restore song num plays from CD Stats file: " + filename, e);
    }

    return restoredCount;
  }

  public void storeSongStatistics(String rootPath) {

    String cdStatsPathName = rootPath + RootFolderEntity.CD_STATS;

    List<SongFileEntity> songs = new ArrayList<>(this.songsMap.values());

    songs.sort(
        Comparator.comparing(SongFileEntity::getNaturalIdentity, String.CASE_INSENSITIVE_ORDER));

    List<String> lines = new ArrayList<>();
    for (SongFileEntity song : songs) {

      String songPath = song.getNaturalIdentity();
      if (songPath == null || songPath.isBlank()) {
        continue;
      }

      lines.add(song.getNumPlays() + " " + songPath);
    }

    try {
      fileSystemHelper.writeLines(cdStatsPathName, lines);
      log.info("Stored num plays for {} songs to {}", songs.size(), cdStatsPathName);
    } catch (IOException e) {
      throw new SongLibraryServiceException(
          "Failed to store song num plays to CD Stats file: " + cdStatsPathName, e);
    }
  }
}
