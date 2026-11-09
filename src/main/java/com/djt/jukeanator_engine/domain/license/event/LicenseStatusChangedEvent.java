package com.djt.jukeanator_engine.domain.license.event;

import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;

/** Published whenever this instance's {@link LicenseStatus} changes. */
public record LicenseStatusChangedEvent(LicenseStatus previous, LicenseStatus current) {

  public boolean commercialChanged() {
    return previous == null || previous.commercialEnabled() != current.commercialEnabled();
  }
}
