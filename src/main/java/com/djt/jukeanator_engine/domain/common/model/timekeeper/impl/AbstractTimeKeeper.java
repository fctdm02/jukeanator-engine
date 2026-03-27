package com.djt.jukeanator_engine.domain.common.model.timekeeper.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.djt.jukeanator_engine.domain.common.model.timekeeper.TimeKeeper;

public abstract class AbstractTimeKeeper implements TimeKeeper {

  public static final ThreadLocal<DateTimeFormatter> LOCAL_DATE_FORMATTER =
      new ThreadLocal<DateTimeFormatter>() {

        @Override
        protected DateTimeFormatter initialValue() {
          return DateTimeFormatter.ofPattern("yyyy-MM-dd");
        }
      };

  public static final ThreadLocal<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTER =
      new ThreadLocal<DateTimeFormatter>() {

        @Override
        protected DateTimeFormatter initialValue() {
          return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        }
      };

  public AbstractTimeKeeper() {}

  @Override
  public Instant getCurrentInstant() {

    return Instant.ofEpochMilli(this.getCurrentTimeInMillis());
  }

  @Override
  public Timestamp getCurrentTimestamp() {

    return new Timestamp(Instant.ofEpochMilli(this.getCurrentTimeInMillis()).toEpochMilli());
  }

  @Override
  public Timestamp getTimestampForDaysFromCurrent(int offSetDays) {

    return new Timestamp(
        Instant.ofEpochMilli(this.getCurrentTimeInMillis() + (NUM_MILLIS_IN_DAY * offSetDays))
            .toEpochMilli());
  }

  @Override
  public LocalDateTime getCurrentLocalDateTime() {

    return getCurrentTimestamp().toLocalDateTime();
  }

  @Override
  public LocalDate getCurrentLocalDate() {

    return getCurrentTimestamp().toLocalDateTime().toLocalDate();
  }

  @Override
  public String toString() {

    return new StringBuilder().append("TimeKeeper [type=").append(getClass().getSimpleName())
        .append("], current time: [")
        .append(LOCAL_DATE_TIME_FORMATTER.get().format(getCurrentLocalDateTime())).toString();
  }
}
