package com.djt.jukeanator_engine.domain.location.model;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/**
 * One Add Funds credit package sold for a location through the Web/Mobile UI. Each slave pushes
 * its own {@code add-funds.packages} list to master with its pricing config (see
 * {@code LocationPricingConfigDto.creditPackages}); master keeps one row per package per location
 * and replaces a location's rows whenever its slave pushes a new list. Held in memory on
 * {@link LocationEntity#getCreditPackages()} as {@link CreditPackageDto}s; this entity is only the
 * persisted shape (see {@code LocationRepositoryJpaImpl.storeCreditPackages}).
 */
@Entity
@Table(name = "location_credit_package")
public class LocationCreditPackageEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "location_id", nullable = false)
  private Integer locationId;

  @Column(name = "package_code", nullable = false, length = 64)
  private String packageCode;

  @Column(name = "price_usd", precision = 12, scale = 2, nullable = false)
  private BigDecimal priceUsd;

  @Column(nullable = false)
  private int credits;

  @Column(name = "bonus_credits", nullable = false)
  private int bonusCredits;

  @Column(length = 64)
  private String badge;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  protected LocationCreditPackageEntity() {} // for JPA

  public LocationCreditPackageEntity(Integer locationId, CreditPackageDto pkg, int displayOrder) {
    super(null);
    this.locationId = locationId;
    this.packageCode = pkg.id();
    this.priceUsd = pkg.priceUsd();
    this.credits = pkg.credits();
    this.bonusCredits = pkg.bonusCredits();
    this.badge = pkg.badge();
    this.displayOrder = displayOrder;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public CreditPackageDto toDto() {
    return new CreditPackageDto(packageCode, credits, bonusCredits, priceUsd, badge);
  }

  @Override
  public String getNaturalIdentity() {
    return locationId + "/" + packageCode;
  }
}
