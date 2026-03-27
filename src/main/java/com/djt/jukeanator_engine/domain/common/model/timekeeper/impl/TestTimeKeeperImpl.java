package com.djt.jukeanator_engine.domain.common.model.timekeeper.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestTimeKeeperImpl extends AbstractTimeKeeper {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestTimeKeeperImpl.class);

  /** Default to 01/01/2020 00:00:00 */
  public static final long DEFAULT_TEST_EPOCH_MILLIS_01_01_2020 = 1577854800000L;

  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private long epochMillis;

  public TestTimeKeeperImpl() {
    this(DEFAULT_TEST_EPOCH_MILLIS_01_01_2020);
  }

  /**
   * @param date The date to set the static current time to. Time doesn't advance with this
   *        implementation and can only be used in the dev-wks and dev-int environments.
   */
  public TestTimeKeeperImpl(String date) {
    this(Timestamp.valueOf(LocalDate.parse(date, DATE_TIME_FORMATTER).atStartOfDay()).getTime());
  }

  /**
   * @param epochMillis The number of milliseconds since midnight, January 1, 1970 UTC to set the
   *        "current" time to. Time doesn't advance with this implementation and can only be used in
   *        the dev-wks and dev-int environments.
   */
  public TestTimeKeeperImpl(long epochMillis) {

    // See if the environment property has been set via Operating System environment variable.
    Map<String, String> osEnvVarsMap = System.getenv();
    String env = osEnvVarsMap.get(ENV_PROPERTY_KEY);
    if (env == null) {
      env = osEnvVarsMap.get(ENV_PROPERTY_KEY.toLowerCase());
    }

    // See if the environment property has been set via JRE System Property.
    if (env == null) {
      env = System.getProperty(ENV_PROPERTY_KEY);
      if (env == null) {
        env = System.getProperty(ENV_PROPERTY_KEY.toLowerCase());
      }
    }

    // Ensure that we are running in a LOCAL or TEST environment
    if (env != null && !env.equalsIgnoreCase(LOCAL) && !env.equalsIgnoreCase(UNIT_TEST)) {
      throw new IllegalStateException("Cannot use this instance:[" + this.getClass().getSimpleName()
          + "] in non-local/non-test environment: [" + env + "].");
    }

    this.epochMillis = epochMillis;
  }

  public long getCurrentTimeInMillis() {

    return this.epochMillis;
  }

  public void setCurrentTime(long millis) {

    if (millis < this.epochMillis) {

      Timestamp ts = new Timestamp(Instant.ofEpochMilli(millis).toEpochMilli());
      throw new IllegalArgumentException("Cannot set static time to the past, current static time: "
          + getCurrentTimestamp() + "], new static time: [" + ts + "].");
    }

    this.epochMillis = millis;
  }

  public void setCurrentTime(long millis, boolean print) {

    if (millis < this.epochMillis) {

      Timestamp ts = new Timestamp(Instant.ofEpochMilli(millis).toEpochMilli());
      throw new IllegalArgumentException("Cannot set static time to the past, current static time: "
          + getCurrentTimestamp() + "], new static time: [" + ts + "].");
    }

    Timestamp oldTimestamp = getCurrentTimestamp();

    this.epochMillis = millis;

    if (print) {
      LOGGER.info("###### FAST FORWARD: {} --> {}", oldTimestamp, getCurrentTimestamp());
    }
  }

  public void forwardTimeInHours(int hours) {

    setCurrentTime(this.epochMillis + NUM_MILLIS_IN_HOUR);
  }

  public void setCurrentDate(String currentDate) {

    LocalDate localDate = LocalDate.parse(currentDate, DATE_TIME_FORMATTER);

    setCurrentTime(localDate);
  }

  public void setCurrentDate(String currentDate, boolean print) {

    LocalDate localDate = LocalDate.parse(currentDate, DATE_TIME_FORMATTER);

    setCurrentTime(localDate, print);
  }

  public void setCurrentTime(Timestamp timestamp) {

    setCurrentTime(timestamp.getTime());
  }

  public void setCurrentTime(LocalDate localDate) {

    Timestamp timestamp = Timestamp.valueOf(localDate.atStartOfDay());
    setCurrentTime(timestamp.getTime());
  }

  public void setCurrentTime(LocalDate localDate, boolean print) {

    Timestamp timestamp = Timestamp.valueOf(localDate.atStartOfDay());
    setCurrentTime(timestamp.getTime(), print);
  }

  public void forwardTimeInDays(int days) {

    Timestamp currentTimestamp = getCurrentTimestamp();

    LocalDate currentLocalDate = currentTimestamp.toLocalDateTime().toLocalDate();

    LocalDate forwardedLocalDate = currentLocalDate.plusDays(days);

    setCurrentTime(forwardedLocalDate);
  }

  public void forwardTimeInWeeks(int weeks) {

    Timestamp currentTimestamp = getCurrentTimestamp();

    LocalDate currentLocalDate = currentTimestamp.toLocalDateTime().toLocalDate();

    LocalDate forwardedLocalDate = currentLocalDate.plusWeeks(weeks);

    setCurrentTime(forwardedLocalDate);
  }

  public void forwardTimeInMonths(int months) {

    Timestamp currentTimestamp = getCurrentTimestamp();

    LocalDate currentLocalDate = currentTimestamp.toLocalDateTime().toLocalDate();

    LocalDate forwardedLocalDate = currentLocalDate.plusMonths(months);

    setCurrentTime(forwardedLocalDate);
  }

  public void forwardTimeInYears(int years) {

    Timestamp currentTimestamp = getCurrentTimestamp();

    LocalDate currentLocalDate = currentTimestamp.toLocalDateTime().toLocalDate();

    LocalDate forwardedLocalDate = currentLocalDate.plusYears(years);

    setCurrentTime(forwardedLocalDate);
  }

  public List<LocalDate> getDatesBetween(LocalDate endDate) {

    return getDatesBetween(getCurrentLocalDate(), endDate);
  }

  public static List<LocalDate> getDatesBetween(LocalDate startDate, LocalDate endDate) {

    long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate);
    return IntStream.iterate(0, i -> i + 1).limit(numOfDaysBetween)
        .mapToObj(i -> startDate.plusDays(i)).collect(Collectors.toList());
  }

}
