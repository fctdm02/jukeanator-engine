package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;

public class FolderEntity extends AbstractLibraryEntity {
  private static final long serialVersionUID = 1L;

  private Set<FolderEntity> childFolders = new TreeSet<FolderEntity>();

  public FolderEntity() {}

  public FolderEntity(FolderEntity parentFolder, String name) {
    this(parentFolder, name, null);
  }

  public FolderEntity(FolderEntity parentFolder, String name, Set<FolderEntity> childFolders) {
    super(parentFolder, name);

    if (childFolders != null) {
      this.childFolders = childFolders;
    }
  }

  public boolean addChildFolder(FolderEntity childFolder) throws EntityAlreadyExistsException {
    return addChild(childFolders, childFolder, this);
  }

  public Set<FolderEntity> getChildFolders() {
    return childFolders;
  }

  public FolderEntity getChildFolder(Integer persistentIdentity)
      throws EntityDoesNotExistException {

    for (FolderEntity childFolder : childFolders) {
      if (childFolder.getPersistentIdentity().equals(persistentIdentity)) {
        return childFolder;
      }
    }
    throw new EntityDoesNotExistException("Child Folder with persistentIdentity: ["
        + persistentIdentity + "] not found in [" + this.getNaturalIdentity() + "].");
  }

  public FolderEntity getChildFolderByName(String name) throws EntityDoesNotExistException {

    for (FolderEntity childFolder : childFolders) {
      if (childFolder.getName().equals(name)) {
        return childFolder;
      }
    }
    throw new EntityDoesNotExistException(
        "Child Folder with name: [" + name + "] not found in [" + this.getNaturalIdentity() + "].");
  }

  public boolean removeChild(FolderEntity childFolder) {

    return this.childFolders.remove(childFolder);
  }

  public AlbumFolderEntity convertChildFolderToAlbumFolder(FolderEntity childFolder,
      List<String> songFilenames, boolean useGenre, boolean useTopFolderForGenre) {

    try {

      this.removeChild(this.childFolders, childFolder);

      AlbumFolderEntity albumFolder = new AlbumFolderEntity(this, childFolder.getName());

      // System.out.println(" Found album: " + albumFolder.getName());

      FolderEntity parentFolder = albumFolder.getParentFolder();
      this.addChildFolder(albumFolder);

      for (String songFilename : songFilenames) {
        albumFolder.addChildSong(new SongFileEntity(albumFolder, songFilename));
      }

      albumFolder.createCoverArtEntity();
      albumFolder.createMetadataEntity();

      // A historical quirk is that for "Soundtracks", there is no Artist level, as it was
      // originally assumed all albums would be compilations
      if (!parentFolder.getName().equalsIgnoreCase("Soundtracks")
          && parentFolder instanceof ArtistFolderEntity == false) {

        parentFolder.getParentFolder().convertChildFolderToArtistFolder(parentFolder, useGenre,
            useTopFolderForGenre);

      } else if (this instanceof GenreFolderEntity == false) {

        if (useGenre) {

          RootFolderEntity rootFolder = null;
          FolderEntity folderToConvertToGenreFolder = null;

          if (useTopFolderForGenre) {

            while (parentFolder instanceof RootFolderEntity == false) {

              folderToConvertToGenreFolder = parentFolder;
              parentFolder = parentFolder.getParentFolder();
              rootFolder = (RootFolderEntity) parentFolder;
            }
            rootFolder.convertChildFolderToGenreFolder(folderToConvertToGenreFolder);

          } else {

            throw new SongLibraryException("useTopFolderForGenre=false not implemented yet!");

          }
        }
      }

      return albumFolder;

    } catch (EntityDoesNotExistException | EntityAlreadyExistsException e) {
      throw new SongLibraryException("Could not convert child folder to album folder, this: " + this + ", childFolder: " + childFolder + ", error:" + e.getMessage(), e);
    }
  }

  public RootFolderEntity getRootFolder() {

    FolderEntity parentFolder = this.getParentFolder();
    while (parentFolder instanceof RootFolderEntity == false) {
      parentFolder = parentFolder.getParentFolder();
    }
    return (RootFolderEntity) parentFolder;
  }

  public ArtistFolderEntity convertChildFolderToArtistFolder(FolderEntity childFolder,
      boolean useGenre, boolean useTopFolderForGenre) {

    try {

      FolderEntity f = this.getChildFolderByName(childFolder.getName());
      if (f instanceof ArtistFolderEntity) {
        return (ArtistFolderEntity) f;
      }

      this.removeChild(this.childFolders, childFolder);

      ArtistFolderEntity artistFolder =
          new ArtistFolderEntity(this, childFolder.getName(), childFolder.getChildFolders());

      // System.out.println(" Found artist: " + artistFolder.getName());

      FolderEntity parentFolder = artistFolder.getParentFolder();
      parentFolder.addChildFolder(artistFolder);

      for (FolderEntity cf : childFolder.getChildFolders()) {
        cf.setParentFolder(artistFolder);
      }

      if (useGenre) {

        RootFolderEntity rootFolder = null;
        FolderEntity folderToConvertToGenreFolder = null;

        if (useTopFolderForGenre) {

          while (parentFolder instanceof RootFolderEntity == false) {

            folderToConvertToGenreFolder = parentFolder;
            parentFolder = parentFolder.getParentFolder();

            if (parentFolder instanceof RootFolderEntity) {
              rootFolder = (RootFolderEntity) parentFolder;
            }
          }
          rootFolder.convertChildFolderToGenreFolder(folderToConvertToGenreFolder);

        } else {

          throw new SongLibraryException("useTopFolderForGenre=false not implemented yet!");

        }
      }

      return artistFolder;

    } catch (EntityDoesNotExistException | EntityAlreadyExistsException e) {
      throw new SongLibraryException(e.getMessage(), e);
    }
  }

  public GenreFolderEntity convertChildFolderToGenreFolder(FolderEntity childFolder) {

    try {

      FolderEntity f = this.getChildFolderByName(childFolder.getName());
      if (f instanceof GenreFolderEntity) {
        return (GenreFolderEntity) f;
      }

      this.removeChild(this.childFolders, childFolder);

      GenreFolderEntity genreFolder =
          new GenreFolderEntity(this, childFolder.getName(), childFolder.getChildFolders());

      // TODO: Convert to log4j
      System.out.println("Found genre: " + genreFolder.getName());

      this.addChildFolder(genreFolder);

      for (FolderEntity cf : childFolder.getChildFolders()) {
        cf.setParentFolder(genreFolder);
      }

      return genreFolder;

    } catch (EntityDoesNotExistException | EntityAlreadyExistsException e) {
      throw new SongLibraryException("Could not convert child folder to genre folder, this: " + this + ", childFolder: " + childFolder + ", error:" + e.getMessage(), e);
    }
  }

  public void pruneNonAlbumContainingChildFolders(Set<FolderEntity> foldersToPrune) {

    for (FolderEntity childFolder : getChildFolders()) {

      if (childFolder.getChildFolders().isEmpty()
          && childFolder instanceof AlbumFolderEntity == false) {

        // System.out.println("Pruning candidate: " + childFolder.getName());
        foldersToPrune.add(childFolder);

      } else {

        childFolder.pruneNonAlbumContainingChildFolders(foldersToPrune);

      }
    }
  }

  public void getAllGenres(Set<GenreFolderEntity> allGenres) {

    for (FolderEntity childFolder : getChildFolders()) {

      if (childFolder instanceof GenreFolderEntity) {

        allGenres.add((GenreFolderEntity) childFolder);

      } else {

        childFolder.getAllGenres(allGenres);

      }
    }
  }

  public void getAllArtists(Set<ArtistFolderEntity> allArtists) {

    for (FolderEntity childFolder : getChildFolders()) {

      if (childFolder instanceof ArtistFolderEntity) {

        allArtists.add((ArtistFolderEntity) childFolder);

      } else {

        childFolder.getAllArtists(allArtists);

      }
    }
  }

  public void getAllAlbums(Set<AlbumFolderEntity> allAlbums) {

    for (FolderEntity childFolder : getChildFolders()) {

      if (childFolder instanceof AlbumFolderEntity) {

        allAlbums.add((AlbumFolderEntity) childFolder);

      } else {

        childFolder.getAllAlbums(allAlbums);

      }
    }
  }
}
