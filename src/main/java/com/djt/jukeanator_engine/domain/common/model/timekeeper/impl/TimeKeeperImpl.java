package com.djt.jukeanator_engine.domain.common.model.timekeeper.impl;

import java.sql.Timestamp;
import java.time.LocalDate;

public final class TimeKeeperImpl extends AbstractTimeKeeper {

  @Override
  public long getCurrentTimeInMillis() {
    return System.currentTimeMillis();
  }

  @Override
  public void setCurrentDate(String t) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void setCurrentTime(long t) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void setCurrentTime(Timestamp t) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void setCurrentTime(LocalDate t) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void forwardTimeInHours(int i) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void forwardTimeInDays(int i) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void forwardTimeInWeeks(int i) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void forwardTimeInMonths(int i) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }

  @Override
  public void forwardTimeInYears(int i) {
    throw new UnsupportedOperationException(
        "Cannot be invoked for: " + this.getClass().getSimpleName());
  }
}
