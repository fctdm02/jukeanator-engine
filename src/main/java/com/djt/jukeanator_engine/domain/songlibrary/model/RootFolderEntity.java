package com.djt.jukeanator_engine.domain.songlibrary.model;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

public class RootFolderEntity extends FolderEntity {
  private static final long serialVersionUID = 1L;

  private String rootPrefix;

  private transient Map<Integer, GenreFolderEntity> genres;
  private transient Map<Integer, ArtistFolderEntity> artists;
  private transient Map<Integer, AlbumFolderEntity> albums;
  private transient Map<String, SongFileEntity> songs;

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
    return genres.values();
  }

  public Collection<ArtistFolderEntity> getArtists() {
    return artists.values();
  }

  public Collection<AlbumFolderEntity> getAlbums() {
    return albums.values();
  }

  public Collection<SongFileEntity> getSongs() {
    return songs.values();
  }

  public void initialize() {

    this.genres = new HashMap<>();
    this.artists = new HashMap<>();
    this.albums = new HashMap<>();
    this.songs = new HashMap<>();

    for (AlbumFolderEntity album : getAllAlbums()) {

      GenreFolderEntity genre = album.getParentGenre();
      Integer genreId = genre.getPersistentIdentity();
      if (!this.genres.containsKey(genreId)) {
        this.genres.put(genreId, genre);
      }

      ArtistFolderEntity artist = album.getParentArtist();
      Integer artistId = artist.getPersistentIdentity();
      if (!this.artists.containsKey(artistId)) {
        this.artists.put(artistId, artist);
      }

      Integer albumId = album.getPersistentIdentity();
      if (!this.albums.containsKey(albumId)) {
        this.albums.put(albumId, album);
      }

      for (SongFileEntity song : album.getChildSongs()) {
        String songKey = albumId.toString() + song.getPersistentIdentity().toString();
        if (!this.songs.containsKey(songKey)) {
          this.songs.put(songKey, song);
        }
      }
    }
  }

  public GenreFolderEntity getGenreById(Integer id) throws EntityDoesNotExistException {

    GenreFolderEntity entity = genres.get(id);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Genre with id: [" + id + "] not found.");
  }

  public ArtistFolderEntity getArtistById(Integer id) throws EntityDoesNotExistException {

    ArtistFolderEntity entity = artists.get(id);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Artist with id: [" + id + "] not found.");
  }

  public AlbumFolderEntity getAlbumById(Integer id) throws EntityDoesNotExistException {

    AlbumFolderEntity entity = albums.get(id);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException("Album with id: [" + id + "] not found.");
  }

  public SongFileEntity getSongById(Integer albumId, Integer songId)
      throws EntityDoesNotExistException {

    String songKey = albumId.toString() + songId.toString();
    SongFileEntity entity = songs.get(songKey);
    if (entity != null) {
      return entity;
    }
    throw new EntityDoesNotExistException(
        "Song with songId: [" + songId + "] and albumId: [" + albumId + "] not found.");
  }
}
