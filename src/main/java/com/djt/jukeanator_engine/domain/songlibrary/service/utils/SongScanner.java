package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.FolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public final class SongScanner {

  private RootFolderEntity rootFolder;
  private Set<String> acceptedSongFileExtensions;
  private boolean requiresMetadata;
  private boolean useGenre;
  private boolean useTopFolderForGenre;

  private DiscogsClientWrapper discogsClientWrapper;
  private CoverArtDownloader coverArtDownloader;

  public SongScanner(
      DiscogsClientWrapper discogsClientWrapper,
      CoverArtDownloader coverArtDownloader, 
      boolean requiresMetadata, 
      boolean useGenre,
      boolean useTopFolderForGenre) {
    requireNonNull(discogsClientWrapper, "discogsClientWrapper cannot be null");
    requireNonNull(coverArtDownloader, "coverArtDownloader cannot be null");
    this.discogsClientWrapper = discogsClientWrapper;
    this.coverArtDownloader = coverArtDownloader;
    this.requiresMetadata = requiresMetadata;
    this.useGenre = useGenre;
    this.useTopFolderForGenre = useTopFolderForGenre;
  }

  /**
   * 
   * @param scanPath
   * @param acceptedSongFileExtensions
   * @param useGenre
   * @param useTopFolderForGenre
   * @return
   * @throws IOException
   */
  public RootFolderEntity scanFileSystemForSongs(
      String scanPath,
      Set<String> acceptedSongFileExtensions) throws IOException {

    if (acceptedSongFileExtensions == null || acceptedSongFileExtensions.isEmpty()) {
      throw new IllegalStateException("acceptedSongFileExtensions cannot be null/empty");
    }
    this.acceptedSongFileExtensions = acceptedSongFileExtensions;



    File file = new File(scanPath);
    if (file == null || !file.exists() || !file.isDirectory()) {
      throw new IllegalStateException(
          "scanPath must specify a root directory that holds songs with file extensions: "
              + acceptedSongFileExtensions);
    }

    String rootPrefix = "";
    String filePath = file.getAbsolutePath();
    String name = null;
    if (filePath.contains(":")) {
      rootPrefix = filePath.substring(0, 2);
      name = filePath.substring(2);
    } else {
      name = filePath;
    }

    rootFolder = new RootFolderEntity(rootPrefix, name);

    process(rootFolder);

    // Prune all children that do not contain AlbumFolders
    rootFolder.pruneNonAlbumContainingChildFolders();
    
    List<AlbumFolderEntity> albums = rootFolder.getAllAlbums();
    for (AlbumFolderEntity album : albums) {

      // A historical quirk is that for "Soundtracks", there is no Artist level, as it was
      // originally assumed all albums would be compilations
      FolderEntity parentFolder = album.getParentFolder();
      if (!parentFolder.getName().equalsIgnoreCase("Soundtracks") && parentFolder instanceof ArtistFolderEntity == false) {
                
        parentFolder.getParentFolder().convertChildFolderToArtistFolder(parentFolder);
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
          
          if (folderToConvertToGenreFolder instanceof GenreFolderEntity == false) {
            rootFolder.convertChildFolderToGenreFolder(folderToConvertToGenreFolder);  
          }          

        } else {

          throw new SongLibraryException("useTopFolderForGenre=false not implemented yet!");

        }
      }
    }
    
    // See if any album needs to have cover art, record label, release
    // date or explicit lyrics metadata retrieved from Discogs    
    for (AlbumFolderEntity album : albums) {

      String albumPath = album.getNaturalIdentity();
      boolean hasValidCoverArt = album.hasValidCoverArt();
      boolean hasValidMetadata = album.hasValidMetadata();

      if (!hasValidCoverArt || (requiresMetadata && !hasValidMetadata)) {

        Map<String, String> albumMetadataResults = this.discogsClientWrapper
            .searchForAlbumMetadata(
                album.getParentFolder().getName(), 
                album.getName());

        if (!hasValidCoverArt) {

          String coverArtPath = albumPath + File.separator + AlbumFolderEntity.COVER_ART_FILENAME;
          String coverArtUrl = albumMetadataResults.get(AlbumMetaDataFileEntity.CoverArtURL);
          this.coverArtDownloader.downloadCoverArt(coverArtPath, coverArtUrl);
        }
        
        if (!hasValidMetadata) {
          
          album.getMetaData().writeMetadataToFileSystem(albumMetadataResults);  
        }        
      }
    }

    return rootFolder;
  }

  private void process(FolderEntity parentFolder) {

    List<String> songFilenames = new ArrayList<>();

    File parentFile = new File(parentFolder.getNaturalIdentity());
    File[] children = parentFile.listFiles();
    if (children != null) {

      for (File child : children) {

        boolean isHidden = child.isHidden();
        if (!isHidden && child.isDirectory()) {
          try {

            FolderEntity childFolder = new FolderEntity(parentFolder, child.getName());
            parentFolder.addChildFolder(childFolder);
            process(childFolder);

          } catch (EntityAlreadyExistsException eaee) {
            throw new SongLibraryException(eaee.getMessage(), eaee);
          }
        } else if (parentFolder instanceof RootFolderEntity == false && !isHidden && child.isFile()
            && this.acceptedSongFileExtensions.contains(getFileExtension(child))) {

          songFilenames.add(child.getName());

        }
      }
    } else {
      System.err.println("parentFile.listFiles() was null for: " + parentFile.getAbsolutePath());
    }

    if (!songFilenames.isEmpty()) {

      parentFolder.getParentFolder().convertChildFolderToAlbumFolder(parentFolder, songFilenames);
    }
  }

  private String getFileExtension(File file) {

    String extension = "";
    String filename = file.getName().toLowerCase();
    int index = filename.indexOf('.');
    if (index > 0) {
      extension = filename.substring(index);
    }
    return extension;
  }
}
