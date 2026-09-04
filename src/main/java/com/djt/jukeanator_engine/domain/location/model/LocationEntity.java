package com.djt.jukeanator_engine.domain.location.model;

import java.time.Instant;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * A physical jukebox ("slave") location known to the master instance.
 *
 * <p>{@code @AttributeOverride} maps {@code persistentIdentity} to the {@code id} column for this
 * table specifically, per this refactor's convention of naming the {@code location}/{@code
 * song_library} PK columns {@code id} -- every other {@link AbstractPersistentEntity} subclass
 * (users, song queue entries, background music, ...) is untouched and keeps the default {@code
 * persistent_identity} column name.
 *
 * @author tmyers
 */
@Entity
@Table(name = "location")
@AttributeOverride(name = "persistentIdentity", column = @Column(name = "id"))
public class LocationEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(nullable = false, unique = true)
  private String name = "Location Name";

  private Double latitude = 42.3314;
  private Double longitude = -83.0458;

  @Column(name = "logo_name")
  private String logoName = "LocationLogo.jpg";

  @Column(name = "is_geo_fenced")
  private boolean isGeoFenced = true;

  // bcrypt hash of the location's API secret; plaintext is never stored
  @Column(name = "api_key_hash", nullable = false)
  private String apiKeyHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LocationStatus status = LocationStatus.PENDING;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "library_last_synced_at")
  private Instant libraryLastSyncedAt;

  // Pricing config, pushed by the slave itself over the /ws-slave STOMP connection (see
  // SlaveConnectionManager) every time it (re)connects -- the slave's own application.yml
  // (JukeANatorUserInterfaceProperties) remains the single source of truth; these columns are
  // just master's read-through cache of it, used to price this location's Web/Mobile UI the same
  // way credit charging for that UI already happens on master. Null until the slave's first sync
  // (or on standalone, where these are never populated at all -- UserServiceImpl reads
  // JukeANatorUserInterfaceProperties directly instead).
  @Column(name = "priority_cost_multiplier")
  private Integer priorityCostMultiplier;

  @Column(name = "credits_per_dollar")
  private Integer creditsPerDollar;

  @Column(name = "five_dollar_bonus_credits")
  private Integer fiveDollarBonusCredits;

  @Column(name = "ten_dollar_bonus_credits")
  private Integer tenDollarBonusCredits;

  @Column(name = "web_cost_multiplier")
  private Integer webCostMultiplier;

  @Column(name = "display_currency_for_cost")
  private Boolean displayCurrencyForCost;

  // Not persisted -- reconstructed uniformly by SongLibraryServiceImpl right after any
  // SongLibraryRepository load. See RootFolderEntity's symmetric transient parentLocation field.
  private transient RootFolderEntity locationSongLibraryRoot;

  public LocationEntity() {}

  public LocationEntity(Integer persistentIdentity, String name, Double latitude,
      Double longitude, String apiKeyHash) {
    super(persistentIdentity);
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
    this.apiKeyHash = apiKeyHash;
  }

  @Override
  public String getNaturalIdentity() {
    return name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public String getLogoName() {
    return logoName;
  }

  public void setLogoName(String logoName) {
    this.logoName = logoName;
  }

  public boolean isGeoFenced() {
    return isGeoFenced;
  }

  public void setGeoFenced(boolean isGeoFenced) {
    this.isGeoFenced = isGeoFenced;
  }

  public String getApiKeyHash() {
    return apiKeyHash;
  }

  public void setApiKeyHash(String apiKeyHash) {
    this.apiKeyHash = apiKeyHash;
  }

  public LocationStatus getStatus() {
    return status;
  }

  public void setStatus(LocationStatus status) {
    this.status = status;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public Instant getLibraryLastSyncedAt() {
    return libraryLastSyncedAt;
  }

  public void setLibraryLastSyncedAt(Instant libraryLastSyncedAt) {
    this.libraryLastSyncedAt = libraryLastSyncedAt;
  }

  public Integer getPriorityCostMultiplier() {
    return priorityCostMultiplier;
  }

  public void setPriorityCostMultiplier(Integer priorityCostMultiplier) {
    this.priorityCostMultiplier = priorityCostMultiplier;
  }

  public Integer getCreditsPerDollar() {
    return creditsPerDollar;
  }

  public void setCreditsPerDollar(Integer creditsPerDollar) {
    this.creditsPerDollar = creditsPerDollar;
  }

  public Integer getFiveDollarBonusCredits() {
    return fiveDollarBonusCredits;
  }

  public void setFiveDollarBonusCredits(Integer fiveDollarBonusCredits) {
    this.fiveDollarBonusCredits = fiveDollarBonusCredits;
  }

  public Integer getTenDollarBonusCredits() {
    return tenDollarBonusCredits;
  }

  public void setTenDollarBonusCredits(Integer tenDollarBonusCredits) {
    this.tenDollarBonusCredits = tenDollarBonusCredits;
  }

  public Integer getWebCostMultiplier() {
    return webCostMultiplier;
  }

  public void setWebCostMultiplier(Integer webCostMultiplier) {
    this.webCostMultiplier = webCostMultiplier;
  }

  public Boolean getDisplayCurrencyForCost() {
    return displayCurrencyForCost;
  }

  public void setDisplayCurrencyForCost(Boolean displayCurrencyForCost) {
    this.displayCurrencyForCost = displayCurrencyForCost;
  }

  public RootFolderEntity getLocationSongLibraryRoot() {
    return locationSongLibraryRoot;
  }

  public void setLocationSongLibraryRoot(RootFolderEntity locationSongLibraryRoot) {
    this.locationSongLibraryRoot = locationSongLibraryRoot;
  }
}
