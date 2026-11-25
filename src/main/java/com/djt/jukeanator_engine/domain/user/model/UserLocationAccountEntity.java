package com.djt.jukeanator_engine.domain.user.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * A patron's account at one location: the (user, location) association that owns everything a
 * mobile/web patron has that is only meaningful at that location -- the song-credit balance, the
 * song-play history, the playlists (including that location's My Favorites), the credit spends
 * and the Add Funds purchases.
 *
 * <p>JukeANator has no central song library: every location plays its own licensed copies, and a
 * song is identified only by {@code (locationId, albumId, songId)}. Credits bought for a location
 * are likewise usable only there, and the purchase is recorded in full in that location's
 * financial ledger (see {@code LocalMobileTransactionEntity}).
 *
 * <p>Every {@link SongIdentifier} held here carries this account's own {@code locationId} -- the
 * column is redundant inside the account but keeps the queue code, which passes
 * {@code SongIdentifier}s between locations, unchanged.
 */
@Entity
@Table(name = "user_location_account")
public class UserLocationAccountEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  // Persistence-only back-reference -- UserEntity.getOrCreateLocationAccount()/
  // restoreLocationAccount() keep it in sync.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "location_id", nullable = false)
  private Integer locationId;

  @Column(name = "num_credits", nullable = false)
  private int numCredits;

  // fetch = EAGER throughout for the same reason as on UserEntity: the whole user root is loaded
  // once and held in memory for the app's lifetime.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_song_play_history",
      joinColumns = @JoinColumn(name = "user_location_account_id"))
  @OrderColumn(name = "play_order")
  private List<SongIdentifier> songPlayHistory = new ArrayList<>();

  @OneToMany(mappedBy = "locationAccount", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("persistentIdentity ASC")
  private List<PlaylistEntity> playlists = new ArrayList<>();

  // Two separate ledgers, never conflated: songCreditUsages records spending already-owned
  // credits here; addFundsTransactions records buying those credits with real money.
  @OneToMany(mappedBy = "locationAccount", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("timestamp ASC")
  private Set<UserSongCreditUsageEntity> songCreditUsages = new HashSet<>();

  @OneToMany(mappedBy = "locationAccount", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("timestamp ASC")
  private Set<UserAddFundsTransactionEntity> addFundsTransactions = new HashSet<>();

  protected UserLocationAccountEntity() {} // for JPA

  /** A brand-new account, seeded with its own My Favorites playlist. */
  public UserLocationAccountEntity(Integer persistentIdentity, Integer locationId) {
    this(persistentIdentity, locationId, 0);
    try {
      createPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    } catch (EntityAlreadyExistsException e) {
      throw new IllegalStateException(e); // cannot happen on an empty account
    }
  }

  /** An account rehydrated from persisted state: no playlists are created. */
  public UserLocationAccountEntity(Integer persistentIdentity, Integer locationId,
      int numCredits) {
    super(persistentIdentity);
    this.locationId = Objects.requireNonNull(locationId, "locationId cannot be null");
    this.numCredits = numCredits;
  }

  void setUser(UserEntity user) {
    this.user = user;
  }

  public UserEntity getUser() {
    return user;
  }

  public String getUserEmail() {
    return user != null ? user.getEmailAddress() : null;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public int getNumCredits() {
    return numCredits;
  }

  public void setNumCredits(int numCredits) {
    this.numCredits = numCredits;
  }

  // ─── Song-play history ─────────────────────────────────────────────────────────────────────

  public List<SongIdentifier> getSongPlayHistory() {
    if (songPlayHistory == null) {
      songPlayHistory = new ArrayList<>();
    }
    return songPlayHistory;
  }

  public void setSongPlayHistory(List<SongIdentifier> songPlayHistory) {
    this.songPlayHistory = songPlayHistory == null ? new ArrayList<>() : songPlayHistory;
  }

  /** Moves (or adds) the song to the newest end of the history. */
  public boolean addSongToSongPlayHistory(SongIdentifier songIdentifier) {
    requireOwnLocation(songIdentifier);
    getSongPlayHistory().remove(songIdentifier);
    return getSongPlayHistory().add(songIdentifier);
  }

  // ─── Playlists ─────────────────────────────────────────────────────────────────────────────

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
    throw new EntityDoesNotExistException("Cannot find playlist: [" + playlistName
        + "] for user: [" + getUserEmail() + "] at location: [" + locationId + "].");
  }

  public PlaylistEntity getPlaylistByNameNullIfNotExists(String playlistName) {

    for (PlaylistEntity playlist : getPlaylists()) {
      if (playlist.getName().equals(playlistName)) {
        return playlist;
      }
    }
    return null;
  }

  /**
   * Creates My Favorites if it is missing and drops duplicate songs from it (My Favorites is a
   * set).
   *
   * @return true if anything changed (the caller must then store the user)
   */
  public boolean ensureMyFavoritesPlaylist() {

    PlaylistEntity favorites =
        getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    if (favorites == null) {
      try {
        createPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
      } catch (EntityAlreadyExistsException e) {
        throw new IllegalStateException(e); // just checked it is missing
      }
      return true;
    }
    List<SongIdentifier> songs = favorites.getSongs();
    if (songs == null || songs.size() == new HashSet<>(songs).size()) {
      return false;
    }
    Set<SongIdentifier> seen = new HashSet<>();
    songs.removeIf(song -> !seen.add(song));
    return true;
  }

  public PlaylistEntity createPlaylist(String playlistName) throws EntityAlreadyExistsException {

    if (getPlaylistByNameNullIfNotExists(playlistName) != null) {
      throw new EntityAlreadyExistsException("Cannot create playlist: [" + playlistName
          + "] for user: [" + getUserEmail() + "] at location: [" + locationId
          + "] because it already exists.");
    }
    PlaylistEntity playlist =
        new PlaylistEntity(UserEntity.nextIdentity(getPlaylists(), 0), playlistName);
    playlist.setLocationAccount(this);
    getPlaylists().add(playlist);
    return playlist;
  }

  /** Re-attaches a fully-formed, already-persisted playlist (rehydration only). */
  public PlaylistEntity restorePlaylist(PlaylistEntity playlist) {

    playlist.setLocationAccount(this);
    getPlaylists().add(playlist);
    return playlist;
  }

  public boolean deletePlaylist(String playlistName) throws EntityDoesNotExistException {

    if (PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME.equals(playlistName)) {
      throw new IllegalArgumentException("Cannot delete the My Favorites playlist.");
    }
    return getPlaylists().remove(getPlaylistByName(playlistName));
  }

  public boolean renamePlaylist(String playlistName, String newPlaylistName)
      throws EntityDoesNotExistException, EntityAlreadyExistsException {

    if (PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME.equals(playlistName)
        || PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME.equals(newPlaylistName)) {
      throw new IllegalArgumentException("Cannot rename to or from the My Favorites playlist.");
    }
    PlaylistEntity playlist = getPlaylistByName(playlistName);
    if (playlistName.equals(newPlaylistName)) {
      return false;
    }
    if (getPlaylistByNameNullIfNotExists(newPlaylistName) != null) {
      throw new EntityAlreadyExistsException("Cannot rename playlist: [" + playlistName
          + "] to: [" + newPlaylistName + "] for user: [" + getUserEmail()
          + "] because it already exists.");
    }
    playlist.setName(newPlaylistName);
    return true;
  }

  public boolean addSongToPlaylist(String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    SongIdentifier songIdentifier = songIdentifierOf(song);

    // My Favorites is a set -- a song is a favorite or it is not -- while any other playlist may
    // hold a song more than once, as a music playlist can.
    if (isMyFavorites(playlist) && playlist.getSongs().contains(songIdentifier)) {
      return false;
    }
    return playlist.addSong(songIdentifier);
  }

  public boolean removeSongFromPlaylist(String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    SongIdentifier songIdentifier = songIdentifierOf(song);

    if (isMyFavorites(playlist)) {
      return playlist.getSongs().removeIf(songIdentifier::equals);
    }
    return playlist.removeSong(songIdentifier);
  }

  private SongIdentifier songIdentifierOf(SongFileEntity song) {
    return new SongIdentifier(locationId, song.getAlbum().getId(), song.getId());
  }

  private static boolean isMyFavorites(PlaylistEntity playlist) {
    return PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME.equals(playlist.getName());
  }

  // ─── Credit ledgers ────────────────────────────────────────────────────────────────────────

  public Set<UserSongCreditUsageEntity> getSongCreditUsages() {
    if (songCreditUsages == null) {
      songCreditUsages = new HashSet<>();
    }
    return songCreditUsages;
  }

  public UserSongCreditUsageEntity addSongCreditUsage(UserSongCreditUsageEntity usage) {

    if (!Objects.equals(usage.getLocationId(), locationId)) {
      throw new IllegalArgumentException("Credit usage for location " + usage.getLocationId()
          + " cannot be recorded on the account for location " + locationId);
    }
    usage.setLocationAccount(this);
    UserEntity.requireAdded(getSongCreditUsages().add(usage), usage);
    return usage;
  }

  public Integer nextSongCreditUsageIdentity() {
    return UserEntity.nextIdentity(getSongCreditUsages(), 1);
  }

  public Set<UserAddFundsTransactionEntity> getAddFundsTransactions() {
    if (addFundsTransactions == null) {
      addFundsTransactions = new HashSet<>();
    }
    return addFundsTransactions;
  }

  public UserAddFundsTransactionEntity addAddFundsTransaction(
      UserAddFundsTransactionEntity transaction) {

    if (!Objects.equals(transaction.getLocationId(), locationId)) {
      throw new IllegalArgumentException("Add Funds for location " + transaction.getLocationId()
          + " cannot be recorded on the account for location " + locationId);
    }
    transaction.setLocationAccount(this);
    UserEntity.requireAdded(getAddFundsTransactions().add(transaction), transaction);
    return transaction;
  }

  public Integer nextAddFundsTransactionIdentity() {
    return UserEntity.nextIdentity(getAddFundsTransactions(), 1);
  }

  /**
   * Withdraws an Add Funds transaction that was never stored -- matched by identity, not by id,
   * since a failed store may already have replaced its placeholder id in place.
   */
  public boolean removeUnstoredAddFundsTransaction(UserAddFundsTransactionEntity transaction) {
    return getAddFundsTransactions().removeIf(t -> t == transaction);
  }

  // ─── Persistence support ───────────────────────────────────────────────────────────────────

  /** See {@link UserEntity#rehashChildCollections()}. */
  void rehashChildCollections() {
    UserEntity.rehash(getSongCreditUsages());
    UserEntity.rehash(getAddFundsTransactions());
  }

  /** See {@link UserEntity#replaceJpaCollectionsWithPlainCopies()}. */
  void replaceJpaCollectionsWithPlainCopies() {

    this.playlists = new ArrayList<>(getPlaylists());
    for (PlaylistEntity playlist : this.playlists) {
      playlist.setSongs(
          playlist.getSongs() != null ? new ArrayList<>(playlist.getSongs()) : new ArrayList<>());
    }
    this.songPlayHistory = new ArrayList<>(getSongPlayHistory());
  }

  /** Clears the personal (non-financial) state when the owning account is closed. */
  void anonymize() {
    // Fresh collections rather than clear() -- see replaceJpaCollectionsWithPlainCopies().
    this.songPlayHistory = new ArrayList<>();
    this.playlists = new ArrayList<>();
  }

  // Package-private: only UserEntity.changeLocationId re-tags an account, when this instance's
  // own location id is corrected post-handshake.
  void changeLocationId(Integer oldLocationId, Integer newLocationId) {

    if (!Objects.equals(oldLocationId, this.locationId)) {
      return;
    }
    this.locationId = newLocationId;
    getSongPlayHistory()
        .replaceAll(s -> s.withLocationIdChanged(oldLocationId, newLocationId));
    for (PlaylistEntity playlist : getPlaylists()) {
      playlist.changeLocationId(oldLocationId, newLocationId);
    }
    for (UserSongCreditUsageEntity usage : getSongCreditUsages()) {
      usage.changeLocationId(oldLocationId, newLocationId);
    }
    for (UserAddFundsTransactionEntity transaction : getAddFundsTransactions()) {
      transaction.changeLocationId(oldLocationId, newLocationId);
    }
  }

  private void requireOwnLocation(SongIdentifier songIdentifier) {
    if (!Objects.equals(songIdentifier.getLocationId(), locationId)) {
      throw new IllegalArgumentException("Song " + songIdentifier + " is not from location "
          + locationId);
    }
  }

  @Override
  public String getNaturalIdentity() {
    return getUserEmail() + "@" + locationId;
  }
}
