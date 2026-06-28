package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFromSongEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.FolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
public final class SongScanner {

  private static final String IGNORE_MARKER_FILENAME = "ignore.me";

  private RootFolderEntity rootFolder;
  private boolean requiresMetadata;
  private boolean useGenre;
  private boolean useTopFolderForGenre;
  private Set<String> acceptedSongFileExtensions;

  private DiscogsClientWrapper discogsClientWrapper;
  private MusicBrainzClientWrapper musicBrainzClientWrapper;
  private JAudioTaggerClient jAudioTaggerClient;
  private CoverArtDownloader coverArtDownloader;

  public SongScanner(DiscogsClientWrapper discogsClientWrapper,
      MusicBrainzClientWrapper musicBrainzClientWrapper, JAudioTaggerClient jAudioTaggerClient,
      CoverArtDownloader coverArtDownloader, boolean requiresMetadata, boolean useGenre,
      boolean useTopFolderForGenre, Set<String> acceptedSongFileExtensions) {
    requireNonNull(discogsClientWrapper, "discogsClientWrapper cannot be null");
    requireNonNull(musicBrainzClientWrapper, "musicBrainzClientWrapper cannot be null");
    requireNonNull(jAudioTaggerClient, "jAudioTaggerClient cannot be null");
    requireNonNull(coverArtDownloader, "coverArtDownloader cannot be null");
    requireNonNull(acceptedSongFileExtensions, "acceptedSongFileExtensions cannot be null");
    this.discogsClientWrapper = discogsClientWrapper;
    this.musicBrainzClientWrapper = musicBrainzClientWrapper;
    this.jAudioTaggerClient = jAudioTaggerClient;
    this.coverArtDownloader = coverArtDownloader;
    this.requiresMetadata = requiresMetadata;
    this.useGenre = useGenre;
    this.useTopFolderForGenre = useTopFolderForGenre;
    this.acceptedSongFileExtensions = acceptedSongFileExtensions;
    if (this.acceptedSongFileExtensions.isEmpty()) {
      throw new IllegalStateException("Accepted File Extensions cannot be empty.");
    }
  }

  public Set<String> getAcceptedSongFileExtensions() {
    return this.acceptedSongFileExtensions;
  }

  public RootFolderEntity scanFileSystemForSongs(String rootPath) throws IOException {

    // FIX: Clean up invisible whitespace trailing paths to prevent Linux mounting discrepancies
    if (rootPath != null) {
      rootPath = rootPath.strip();
    }
    File file = new File(rootPath).getCanonicalFile();

    String rootName = file.getAbsolutePath();

    rootFolder = new RootFolderEntity(rootName);

    process(rootFolder);

    // Prune all children that do not contain AlbumFolders
    rootFolder.pruneNonAlbumContainingChildFolders();

    List<AlbumFolderEntity> albums = rootFolder.getAllAlbums();
    for (AlbumFolderEntity album : albums) {

      // A historical quirk is that for "Soundtracks", there is no Artist level, as it
      // was originally assumed all albums would be compilations
      FolderEntity parentFolder = album.getParentFolder();
      if (!parentFolder.getName().equalsIgnoreCase("Soundtracks")
          && parentFolder instanceof ArtistFolderEntity == false) {

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
    int genreIndex = 0;
    int artistIndex = 0;
    for (int i = 0; i < albums.size(); i++) {

      AlbumFolderEntity album = albums.get(i);
      album.setPersistentIdentity(i);

      GenreFolderEntity genre = album.getParentGenre();
      if (genre != null && genre.getPersistentIdentity() == null) {
        genre.setPersistentIdentity(genreIndex++);
      }

      ArtistFolderEntity artist = album.getParentArtist();
      if (artist != null && artist.getPersistentIdentity() == null) {
        artist.setPersistentIdentity(artistIndex++);
      }

      String albumPath = album.getNaturalIdentity();
      String coverArtPath = albumPath + File.separator + AlbumFolderEntity.COVER_ART_FILENAME;

      boolean hasValidCoverArt = album.hasValidCoverArt();
      boolean hasValidMetadata = album.hasValidMetadata();

      // First, see if we can retrieve any of this information from tags embedded in the song file
      List<SongFileEntity> songList = album.getChildSongs();
      for (int j = 0; j < songList.size(); j++) {

        SongFileEntity song = songList.get(j);

        String songPathname = song.getNaturalIdentity();
        String songFilename = song.getName();
        song.setPersistentIdentity(j);

        String songArtistName = SongFileEntity.extractArtistName(songFilename);
        if (songArtistName != null && !songArtistName.trim().isBlank()) {
          song.setArtistName(stripNonPrintableCharacters(songArtistName));
        }

        ArtistFromSongEntity artistFromSong = rootFolder.getArtistFromSong(songArtistName);
        if (artistFromSong == null) {
          artistFromSong = new ArtistFromSongEntity(rootFolder, songArtistName);
          artistFromSong.setPersistentIdentity(artistIndex++);
          artistFromSong.addAlbum(album);
          rootFolder.addArtistFromSong(artistFromSong);
        } else {
          artistFromSong.addAlbum(album);
        }

        String songName = SongFileEntity.extractSongName(songFilename);
        if (songName != null && !songName.trim().isBlank()) {
          song.setSongName(stripNonPrintableCharacters(songName));
        }

        Integer trackNumber = SongFileEntity.extractTrackNumber(songFilename);
        if (trackNumber != null && trackNumber.intValue() > 0) {
          song.setTrackNumber(trackNumber);
        } else {
          song.setTrackNumber(Integer.valueOf(j + 1));
        }

        if (!hasValidCoverArt) {

          this.jAudioTaggerClient.extractCoverArt(coverArtPath, songPathname);
          hasValidCoverArt = album.hasValidCoverArt();
        }

        if (requiresMetadata && !hasValidMetadata) {

          Map<String, String> tags = this.jAudioTaggerClient.getTags(songPathname);
          if (tags != null && !tags.isEmpty()) {

            String recordLabel = tags.get(JAudioTaggerClient.RECORD_LABEL);
            String releaseDate = tags.get(JAudioTaggerClient.RELEASE_DATE);

            AlbumMetadataDto metadata =
                new AlbumMetadataDto("", "", recordLabel, releaseDate, "", "", false);

            if (!metadata.isEmpty()) {

              album.getMetaData().writeMetadataToFileSystem(metadata);
              hasValidMetadata = album.hasValidMetadata();
            }

            songArtistName = tags.get(JAudioTaggerClient.ARTIST_NAME);
            if (songArtistName != null && !songArtistName.trim().isBlank()) {
              song.setArtistName(stripNonPrintableCharacters(songArtistName));
            }

            songName = tags.get(JAudioTaggerClient.SONG_NAME);
            if (songName != null && !songName.trim().isBlank()) {
              song.setSongName(stripNonPrintableCharacters(songName));
            }
          }
        }
      }

      // Execute the post-processing track correction and compilation analysis
      album.postProcessSongs();

      if (!hasValidCoverArt || (requiresMetadata && !hasValidMetadata)) {

        List<AlbumMetadataDto> albumMetadataResults = searchInternetForAlbumMetadata(album);

        if (!hasValidCoverArt && !albumMetadataResults.isEmpty()) {

          AlbumMetadataDto albumMetadataResult = albumMetadataResults.get(0);
          String coverArtUrl = albumMetadataResult.getCoverArtUrl();
          downloadCoverArt(coverArtPath, coverArtUrl);
        }

        if (!hasValidMetadata && !albumMetadataResults.isEmpty()) {

          AlbumMetadataDto albumMetadataResult = albumMetadataResults.get(0);
          album.getMetaData().writeMetadataToFileSystem(albumMetadataResult);
        }
      }

    }

    return rootFolder;
  }

  public void downloadCoverArt(String coverArtPath, String coverArtUrl) {
    this.coverArtDownloader.downloadCoverArt(coverArtPath, coverArtUrl);
  }

  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(AlbumFolderEntity album) {
    return searchInternetForAlbumMetadata(album.getParentFolder().getName(), album.getName(), 3);
  }

  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(String artistName, String albumName,
      int limit) {

    List<AlbumMetadataDto> albumMetadataResults = this.musicBrainzClientWrapper
        .searchForAlbumMetadata(artistName, albumName, this.useGenre, limit);

    if ((albumMetadataResults == null || albumMetadataResults.isEmpty())
        && this.discogsClientWrapper.hasValidApiKey()) {

      albumMetadataResults =
          this.discogsClientWrapper.searchForAlbumMetadata(artistName, albumName, limit);
    }

    return albumMetadataResults;
  }

  private void process(FolderEntity parentFolder) {

    List<String> songFilenames = new ArrayList<>();
    File parentFile = new File(parentFolder.getNaturalIdentity());

    // IGNORE ENTIRE SUBTREE IF ignore.me EXISTS
    File ignoreMarker = new File(parentFile, IGNORE_MARKER_FILENAME);
    if (ignoreMarker.exists() && ignoreMarker.isFile()) {
      System.out
          .println("Ignoring folder subtree due to ignore.me: " + parentFile.getAbsolutePath());
      return;
    }

    File[] children = parentFile.listFiles();

    if (children != null) {
      for (File child : children) {
        boolean isHidden = child.isHidden();

        if (!isHidden && child.isDirectory()) {
          try {
            // SKIP CHILD DIRECTORY IF IT CONTAINS ignore.me
            File childIgnoreMarker = new File(child, IGNORE_MARKER_FILENAME);
            if (childIgnoreMarker.exists() && childIgnoreMarker.isFile()) {
              System.out
                  .println("Ignoring folder subtree due to ignore.me: " + child.getAbsolutePath());
              continue;
            }

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

  // FIX: Using lastIndexOf('.') isolates ".mp3" from multi-dotted names like "Y.M.C.A..mp3"
  private String getFileExtension(File file) {
    if (file == null) {
      return "";
    }
    String extension = "";
    String filename = file.getName().toLowerCase();
    int index = filename.lastIndexOf('.');
    if (index > 0) {
      extension = filename.substring(index);
    }
    return extension;
  }

  // FIX: Targets actual Unicode system control blocks while preserving international character sets
  // safely
  private String stripNonPrintableCharacters(String input) {
    if (input == null) {
      return "";
    }
    return input.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", "").strip();
  }
}
