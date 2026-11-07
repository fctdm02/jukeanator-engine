package com.djt.jukeanator_engine.domain.user.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.service.CreditPackageCatalog;

/**
 * Properties bound to the {@code add-funds:} YAML prefix: the credit packages the Web/Mobile UI
 * sells. Set them on the instance that takes web payments (master, or standalone). When no
 * packages are listed, {@link CreditPackageCatalog#DEFAULT_PACKAGES} apply. See
 * {@code docs/pricing.md}.
 */
@Validated
@ConfigurationProperties(prefix = "add-funds")
public class AddFundsProperties {

  private List<CreditPackage> packages = new ArrayList<>();

  public List<CreditPackage> getPackages() {
    return packages;
  }

  public void setPackages(List<CreditPackage> packages) {
    this.packages = packages;
  }

  /** The configured packages, or the built-in defaults when none are configured. */
  public List<CreditPackageDto> toCreditPackages() {
    if (packages == null || packages.isEmpty()) {
      return CreditPackageCatalog.DEFAULT_PACKAGES;
    }
    return packages.stream()
        .map(p -> new CreditPackageDto(p.getId(), p.getCredits(), p.getBonusCredits(),
            p.getPriceUsd(), p.getBadge() == null || p.getBadge().isBlank() ? null : p.getBadge()))
        .toList();
  }

  /** One {@code add-funds.packages} entry. */
  public static class CreditPackage {

    // Recorded on every purchase (with the credits, bonus and price at the time), so it may stay
    // the same when the package's contents change.
    private String id;
    private BigDecimal priceUsd;
    private int credits;
    private int bonusCredits;
    private String badge; // optional label, e.g. "Best Value"

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public BigDecimal getPriceUsd() {
      return priceUsd;
    }

    public void setPriceUsd(BigDecimal priceUsd) {
      this.priceUsd = priceUsd;
    }

    public int getCredits() {
      return credits;
    }

    public void setCredits(int credits) {
      this.credits = credits;
    }

    public int getBonusCredits() {
      return bonusCredits;
    }

    public void setBonusCredits(int bonusCredits) {
      this.bonusCredits = bonusCredits;
    }

    public String getBadge() {
      return badge;
    }

    public void setBadge(String badge) {
      this.badge = badge;
    }
  }
}
