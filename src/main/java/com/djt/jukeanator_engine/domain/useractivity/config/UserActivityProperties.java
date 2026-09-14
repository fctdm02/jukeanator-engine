package com.djt.jukeanator_engine.domain.useractivity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user-activity")
public class UserActivityProperties {

  // How long a recorded activity event is kept before UserActivityPurgeScheduler's daily job
  // permanently deletes it, keeping the activity log/table bounded to a rolling window.
  private int retentionDays = 30;

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }
}
