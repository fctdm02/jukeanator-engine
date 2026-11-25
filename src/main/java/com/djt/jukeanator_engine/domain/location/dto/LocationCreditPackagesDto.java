package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/**
 * On-disk shape of every location's Add Funds credit packages for the filesystem-backed
 * {@code LocationRepository} -- a separate file from the location list itself, so adding packages
 * never changes (and never invalidates) the existing location file.
 */
public record LocationCreditPackagesDto(Map<Integer, List<CreditPackageDto>> packagesByLocation)
    implements Serializable {

  public Map<Integer, List<CreditPackageDto>> packagesByLocation() {
    return packagesByLocation == null ? Map.of() : packagesByLocation;
  }
}
