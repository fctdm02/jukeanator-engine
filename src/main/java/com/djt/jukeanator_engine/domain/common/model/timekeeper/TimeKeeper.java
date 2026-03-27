package com.djt.jukeanator_engine.domain.common.model.timekeeper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * Used to manage the current time. In a test context, the "current time" may be set to be a static
 * point in time, either in the past or in the future. This is to facilitate testing
 * operations/events that are time-dependent.
 * <p>
 * In a non-local/unit test context, the time keeper will always delegate to
 * <code>System.getCurrentTimeMillis()</code> when asked what the current date is.
 *
 * Used for retrieving the time and time related functionality. In PROD, time is retrieved from the
 * application container. In lower environments, a test timekeeper can be employed that has static
 * time and can allow for "time travel" to test events that are initiated via the passage of time.
 * 
 * @author tmyers
 *
 */
public interface TimeKeeper {

  /** Used to limit usage of the test timekeeper implementation. */
  String ENV_PROPERTY_KEY = "com.djt.hvac.domain.model.common.timekeeper.env";

  /** Used to limit usage of the test timekeeper implementation. */
  String DEV = "dev";

  /** Used to limit usage of the test timekeeper implementation. */
  String UNIT_TEST = "test";

  /** Used to limit usage of the test timekeeper implementation. */
  String LOCAL = "local";


  /** */
  long NUM_MILLIS_IN_HOUR = 3600000;

  /** */
  long NUM_MILLIS_IN_DAY = NUM_MILLIS_IN_HOUR * 24;


  /**
   * In epoch millis (UTC)
   * 
   * @return current time in millis
   */
  long getCurrentTimeInMillis();

  /**
   * 
   * In epoch millis (UTC)
   * 
   * @return current instant
   */
  Instant getCurrentInstant();

  /**
   *
   * In epoch millis (UTC)
   *
   * @return current timestamp
   */
  Timestamp getCurrentTimestamp();

  /**
   * 
   * @return current local date time
   */
  LocalDateTime getCurrentLocalDateTime();

  /**
   * 
   * @return current local date
   */
  LocalDate getCurrentLocalDate();

  /**
   * 
   * In epoch millis (UTC)
   * 
   * @param offSetDays
   * 
   * @return timestamp
   */
  Timestamp getTimestampForDaysFromCurrent(int offSetDays);


  // The methods below are strictly for testing and are not available in higher environments.
  void setCurrentDate(String currentDate);

  void setCurrentTime(long millis);

  void setCurrentTime(Timestamp timestamp);

  void setCurrentTime(LocalDate localDate);

  void forwardTimeInHours(int hours);

  void forwardTimeInDays(int days);

  void forwardTimeInWeeks(int weeks);

  void forwardTimeInMonths(int months);

  void forwardTimeInYears(int years);
}
