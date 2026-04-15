package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

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
	private MusicBrainzClientWrapper musicBrainzClientWrapper;
	private JAudioTaggerClient jAudioTaggerClient;
	private CoverArtDownloader coverArtDownloader;

	public SongScanner(
			DiscogsClientWrapper discogsClientWrapper, 
			MusicBrainzClientWrapper musicBrainzClientWrapper,
			JAudioTaggerClient jAudioTaggerClient,
			CoverArtDownloader coverArtDownloader, 
			boolean requiresMetadata, 
			boolean useGenre,
			boolean useTopFolderForGenre) {
		requireNonNull(discogsClientWrapper, "discogsClientWrapper cannot be null");
		requireNonNull(musicBrainzClientWrapper, "musicBrainzClientWrapper cannot be null");
		requireNonNull(jAudioTaggerClient, "jAudioTaggerClient cannot be null");
		requireNonNull(coverArtDownloader, "coverArtDownloader cannot be null");
		this.discogsClientWrapper = discogsClientWrapper;
		this.musicBrainzClientWrapper = musicBrainzClientWrapper;
		this.jAudioTaggerClient = jAudioTaggerClient;
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
	public RootFolderEntity scanFileSystemForSongs(String scanPath, Set<String> acceptedSongFileExtensions)
			throws IOException {

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
		for (AlbumFolderEntity album : albums) {

			String albumPath = album.getNaturalIdentity();
			String coverArtPath = albumPath + File.separator + AlbumFolderEntity.COVER_ART_FILENAME;
			
			boolean hasValidCoverArt = album.hasValidCoverArt();
			boolean hasValidMetadata = album.hasValidMetadata();

			// First, see if we can retrieve any of this information from tags embedded in the song file
			Map<String, String> albumMetadataResults = new HashMap<>();
			Set<SongFileEntity> songs = album.getChildSongs();
			List<SongFileEntity> songList = new ArrayList<>();
			songList.addAll(songs);
			for (int i=0; i < songList.size(); i++) {
				
				SongFileEntity song = songList.get(i);
				
				String songFile = song.getNaturalIdentity();
				if (!hasValidCoverArt) {
					
					this.jAudioTaggerClient.extractCoverArt(coverArtPath, songFile);					
					hasValidCoverArt = album.hasValidCoverArt();						
				}
				
				if (requiresMetadata && !hasValidMetadata) {
					
					Map<String, String> tags = this.jAudioTaggerClient.getTags(songFile);
					if (tags != null && !tags.isEmpty()) {
						
						String recordLabel = tags.get(JAudioTaggerClient.RECORD_LABEL);						
						if (recordLabel != null && !recordLabel.trim().isBlank()) {
							
							albumMetadataResults.put(AlbumMetaDataFileEntity.RecordLabel, recordLabel);	
						}
						
						String releaseDate = tags.get(JAudioTaggerClient.RELEASE_DATE);
						if (releaseDate != null && releaseDate.trim().length() == 4) {
							
							albumMetadataResults.put(AlbumMetaDataFileEntity.ReleaseDate, releaseDate);
					    }
						
						if (!albumMetadataResults.isEmpty()) {

							album.getMetaData().writeMetadataToFileSystem(albumMetadataResults);						
							hasValidMetadata = album.hasValidMetadata();
						}
						
						String artistName = tags.get(JAudioTaggerClient.ARTIST_NAME);
						if (artistName != null && !artistName.trim().isBlank()) {
							song.setArtistName(artistName);
						} else {
							artistName = SongFileEntity.extractArtistName(songFile);
							if (artistName != null && !artistName.trim().isBlank()) {
								song.setArtistName(artistName);	
							}
						}
						
						String songName = tags.get(JAudioTaggerClient.SONG_NAME);
						if (songName != null && !songName.trim().isBlank()) {
							song.setSongName(songName);
						} else {
							songName = SongFileEntity.extractSongName(songFile);
							if (songName != null && !songName.trim().isBlank()) {
								song.setSongName(songName);
							}
						}

						Integer trackNumber = null;
						try {
							
							String strTrackNumber = tags.get(JAudioTaggerClient.TRACK_NUMBER);
							if (strTrackNumber != null && !strTrackNumber.trim().isBlank()) {
								
								trackNumber = Integer.parseInt(strTrackNumber);
								if (trackNumber.intValue() > 0) {
									song.setTrackNumber(trackNumber);	
								}					
							}
							
						} catch (NumberFormatException nfe) {
							
							trackNumber = SongFileEntity.extractTrackNumber(songFile);
							if (trackNumber != null && trackNumber.intValue() > 0) {
								song.setTrackNumber(trackNumber);
							} else {
								song.setTrackNumber(Integer.valueOf(i));	
							}							
						}					
					}					
				}
			}
			
			if (!hasValidCoverArt || (requiresMetadata && !hasValidMetadata)) {

				albumMetadataResults = this.musicBrainzClientWrapper
						.searchForAlbumMetadata(album.getParentFolder().getName(), album.getName());
				
				if ((albumMetadataResults == null || albumMetadataResults.isEmpty()) && this.discogsClientWrapper.hasValidApiKey()) {

					albumMetadataResults = this.discogsClientWrapper
							.searchForAlbumMetadata(album.getParentFolder().getName(), album.getName());
				}

				if (!hasValidCoverArt) {
					
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
