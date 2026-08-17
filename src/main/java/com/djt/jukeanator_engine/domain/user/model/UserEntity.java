package com.djt.jukeanator_engine.domain.user.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

@Entity
@Table(name = "users")
public class UserEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(UserEntity.class);

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "email_address", nullable = false, unique = true)
  private String emailAddress;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "num_credits", nullable = false)
  private Integer numCredits = 0;

  // fetch = EAGER throughout this aggregate because UserServiceImpl loads the whole user root
  // once and holds it in memory indefinitely, well outside the scope of any one transaction --
  // the same "fully materialized" shape that UserRepositoryFileSystemImpl already assumes when it
  // deserializes the entire file in one shot.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_song_play_history", joinColumns = @JoinColumn(name = "user_id"))
  @OrderColumn(name = "play_order")
  private List<SongIdentifier> songPlayHistory = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_search_history", joinColumns = @JoinColumn(name = "user_id"))
  @OrderColumn(name = "search_order")
  @Column(name = "search_query", length = 500)
  private List<String> searchHistory = new ArrayList<>();

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("persistentIdentity ASC")
  private List<PlaylistEntity> playlists = new ArrayList<>();

  // Same "load once, hold in memory" rationale as the other collections above -- see
  // UserServiceImpl, which loads the whole user root once and holds it for the app's lifetime.
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("timestamp ASC")
  private Set<CreditTransactionEntity> transactions = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role = UserRole.ROLE_USER;

  protected UserEntity() {} // for JPA

  public UserEntity(Integer persistentIdentity, String firstName, String lastName,
      String emailAddress, String passwordHash, Integer numCredits, UserRole role) {
    super(persistentIdentity);
    this.firstName = firstName;
    this.lastName = lastName;
    this.emailAddress = emailAddress;
    this.passwordHash = passwordHash;
    this.numCredits = numCredits;
    this.role = role;

    try {
      createPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    } catch (EntityAlreadyExistsException eaee) {
      log.error("My favorites playlist somehow already exists for new user: {" + this.emailAddress
          + "].");
    }
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Integer getNumCredits() {
    return numCredits;
  }

  public void setNumCredits(Integer numCredits) {
    this.numCredits = numCredits;
  }

  public List<SongIdentifier> getSongPlayHistory() {
    return songPlayHistory;
  }

  public void setSongPlayHistory(List<SongIdentifier> songPlayHistory) {
    this.songPlayHistory = songPlayHistory;
  }

  public boolean addSongToSongPlayHistory(SongIdentifier songIdentifier) {
    this.songPlayHistory.remove(songIdentifier);
    return this.songPlayHistory.add(songIdentifier);
  }

  public List<String> getSearchHistory() {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    return searchHistory;
  }

  public void setSearchHistory(List<String> searchHistory) {
    this.searchHistory = searchHistory;
  }

  public void addToSearchHistory(String query, int maxSize) {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    searchHistory.remove(query);
    searchHistory.add(0, query);
    if (searchHistory.size() > maxSize) {
      searchHistory = new ArrayList<>(searchHistory.subList(0, maxSize));
    }
  }

  public void removeFromSearchHistory(int index) {
    if (searchHistory == null)
      return;
    if (index >= 0 && index < searchHistory.size()) {
      searchHistory.remove(index);
    }
  }

  public List<PlaylistEntity> getPlaylists() {

    if (playlists == null) {
      playlists = new ArrayList<>();
    }

    return playlists;
  }

  public PlaylistEntity getPlaylistByName(String playlistName) throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByNameNullIfNotExists(playlistName);
    if (playlist != null) {
      return playlist;
    }

    createMyFavoritesPlaylist();

    throw new EntityDoesNotExistException(
        "Cannot find playlist: [" + playlistName + "] for user: [" + this.emailAddress + "].");
  }

  public PlaylistEntity getPlaylistByNameNullIfNotExists(String playlistName) {

    for (PlaylistEntity playlist : this.playlists) {

      if (playlist.getName().equals(playlistName)) {
        return playlist;
      }
    }
    return null;
  }

  public PlaylistEntity createMyFavoritesPlaylist() {

    // Every user should have a "My favorites" playlist
    try {
      return createPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    } catch (EntityAlreadyExistsException eaee) {
      log.error("My favorites playlist somehow already exists for new user: {" + this.emailAddress
          + "].");
    }

    // This should never occur.
    return this.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
  }

  public PlaylistEntity createPlaylist(String playlistName) throws EntityAlreadyExistsException {

    PlaylistEntity check = getPlaylistByNameNullIfNotExists(playlistName);
    if (check != null) {
      throw new EntityAlreadyExistsException("Cannot create playlist: [" + playlistName
          + "] for user: [" + this.emailAddress + "] because it already exists.");
    }

    if (this.playlists == null) {
      this.playlists = new ArrayList<>();
    }

    int index = this.playlists.size();
    PlaylistEntity playlist = new PlaylistEntity(index, this.emailAddress, playlistName);
    playlist.setUser(this);
    this.playlists.add(playlist);
    return playlist;
  }

  /**
   * Re-attaches a fully-formed {@link PlaylistEntity} (persisted identity, songs, and all)
   * without going through {@link #createPlaylist(String)}, which mints a brand new playlist.
   * Used when rehydrating a user from persisted state, where the playlist already exists.
   */
  public PlaylistEntity restorePlaylist(PlaylistEntity playlist) {

    playlist.setUser(this);
    this.getPlaylists().add(playlist);
    return playlist;
  }

  public boolean deletePlaylist(String playlistName) throws EntityDoesNotExistException {

    if (PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME.equals(playlistName)) {
      throw new IllegalArgumentException("Cannot delete the My Favorites playlist.");
    }

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    return this.playlists.remove(playlist);
  }

  public boolean addSongToPlaylist(String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    SongIdentifier songIdentifier =
        new SongIdentifier(song.getAlbum().getPersistentIdentity(), song.getPersistentIdentity());

    return playlist.addSong(songIdentifier);
  }

  public boolean removeSongFromPlaylist(String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    SongIdentifier songIdentifier =
        new SongIdentifier(song.getAlbum().getPersistentIdentity(), song.getPersistentIdentity());

    return playlist.removeSong(songIdentifier);
  }

  public Set<CreditTransactionEntity> getTransactions() {

    if (transactions == null) {
      transactions = new HashSet<>();
    }

    return transactions;
  }

  public CreditTransactionEntity addTransaction(CreditTransactionEntity transaction) {

    transaction.setUser(this);
    this.transactions.add(transaction);
    return transaction;
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  @Override
  public String getNaturalIdentity() {
    return this.emailAddress;
  }
}
