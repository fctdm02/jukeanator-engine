package com.djt.jukeanator_engine.domain.common.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.model.timekeeper.TimeKeeper;
import com.djt.jukeanator_engine.domain.common.model.timekeeper.impl.TimeKeeperImpl;
import com.djt.jukeanator_engine.domain.common.model.utils.CalendarAligners;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.common.model.utils.TimezoneUtils;
import com.djt.jukeanator_engine.domain.common.model.validation.ValidationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public abstract class AbstractEntity implements Comparable<AbstractEntity>, Serializable {
  private static final long serialVersionUID = 1L;
  
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEntity.class);

  public static final String NATURAL_IDENTITY_DELIMITER = "/";
  
  public static final String UTC_TIMEZONE_LABEL = "UTC";
  
  public static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone(UTC_TIMEZONE_LABEL);
  
  public static final ZoneId UTC_ZONE_ID = UTC_TIME_ZONE.toZoneId();
  
  public static final String ETC_UTC_TIMEZONE = "Etc/UTC";
  
  public static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
  public static final ThreadLocal<DateTimeFormatter> DATE_TIME_FORMATTER =
      new ThreadLocal<DateTimeFormatter>() {
        @Override
        protected DateTimeFormatter initialValue() {
          return DateTimeFormatter
              .ofPattern(TIME_FORMAT)
              .withZone(ZoneId.of(UTC_TIMEZONE_LABEL));
        }
      };  

  public static String toFormattedZonedTime(Timestamp timestamp, String timezone) {
    
    if (timestamp == null) {
      return "";
    }
    return AbstractEntity.toFormattedZonedTime(
        timestamp.getTime(), 
        timezone);
  }  

  public static String toFormattedZonedTime(long epochMillis, String timezone) {
    
    return AbstractEntity.DATE_TIME_FORMATTER
        .get()
        .format(Instant
            .ofEpochMilli(epochMillis)
            .atZone(ZoneId
                .of(TimezoneUtils
                    .getTimezone(timezone)
                    .getID())));
  }  
  
  public static final String DISPLAY_DATE_TIME_FORMAT = "M/d/yyyy, h:mm:ss a";
  public static final ThreadLocal<DateTimeFormatter> DISPLAY_DATE_TIME_FORMATTER =
      new ThreadLocal<DateTimeFormatter>() {
        @Override
        protected DateTimeFormatter initialValue() {
          return DateTimeFormatter
              .ofPattern(DISPLAY_DATE_TIME_FORMAT);
        }
      };  

  public static String toDisplayFormattedZonedTime(Timestamp timestamp, String timezone) {
    
    if (timestamp == null) {
      return "";
    }
    return AbstractEntity.toDisplayFormattedZonedTime(
        timestamp.getTime(), 
        timezone);
  }  

  public static String toDisplayFormattedZonedTime(long epochMillis, String timezone) {
    
    return AbstractEntity.DISPLAY_DATE_TIME_FORMATTER
        .get()
        .format(Instant
            .ofEpochMilli(epochMillis)
            .atZone(ZoneId
                .of(TimezoneUtils
                    .getTimezone(timezone)
                    .getID())));
  }  
  
  public static final ThreadLocal<DateTimeFormatter> LOCAL_DATE_FORMATTER = new ThreadLocal<DateTimeFormatter>() {

    @Override
    protected DateTimeFormatter initialValue() {
      return DateTimeFormatter.ofPattern("yyyy-MM-dd");
    }
  };

  public static final ThreadLocal<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTER = new ThreadLocal<DateTimeFormatter>() {

    @Override
    protected DateTimeFormatter initialValue() {
      return DateTimeFormatter.ofPattern(TIME_FORMAT);
    }
  };
  
  public static final ThreadLocal<ObjectMapper> OBJECT_MAPPER = new ThreadLocal<ObjectMapper>() {

    @Override
    protected ObjectMapper initialValue() {
      return ObjectMappers.create();
    }
  };
  
  public static final ThreadLocal<ObjectWriter> OBJECT_WRITER = new ThreadLocal<ObjectWriter>() {
    
    @Override
    protected ObjectWriter initialValue() {
      return ObjectMappers.create().writerWithDefaultPrettyPrinter();
    }
  };
  
  /*
   * Used to manage the current time.  In a test context, the "current time" may be set to be a
   * static point in time, either in the past or in the future.  This is to facilitate testing
   * operations/events that are time-dependent.
   *
   * In a non-local/unit test context, the time keeper will always delegate to
   * <code>System.getCurrentTimeMillis()</code> when asked what the current date is. 
   */
  private static TimeKeeper TIME_KEEPER = new TimeKeeperImpl();
                  
  /*
   * Used for retrieving the time and time related functionality.  In PROD, time is retrieved from the 
   * application container.  In lower environments, a test timekeeper can be employed that has static
   * time and can allow for "time travel" to test events that are initiated via the passage of time.
   */
  public static TimeKeeper getTimeKeeper() {
      return TIME_KEEPER;
  }
  
  public static final void setTimeKeeper(TimeKeeper timeKeeper) {
    
      LOGGER.debug("Setting timeKeeper to: {} with current timestamp: {}", 
          timeKeeper.getClass().getSimpleName(), 
          timeKeeper.getCurrentTimestamp());
      TIME_KEEPER = timeKeeper;
  }
  
  
  public static final LocalDateTime convertTimestampToLocalDateTime(Timestamp timestamp) {
    
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp.getTime()), AbstractEntity.UTC_ZONE_ID).truncatedTo(ChronoUnit.SECONDS);
  }
  
  public static final Timestamp convertLocalDateTimeToTimestamp(LocalDateTime localDateTime) {
    
    return Timestamp.valueOf(localDateTime.truncatedTo(ChronoUnit.SECONDS));
  }
  
  public static final TemporalAdjuster FIFTEEN_MINUTE_FLOOR_TEMPORAL_ADJUSTER = CalendarAligners.floor(15, ChronoField.MINUTE_OF_HOUR);
  
  public static LocalDateTime adjustCurrentTimeIntoFifteenMinuteFloor() {
    
    return adjustTimeIntoFifteenMinuteFloor(AbstractEntity
        .getTimeKeeper()
        .getCurrentLocalDateTime());
  }
  
  public static LocalDateTime adjustTimeIntoFifteenMinuteFloor(LocalDateTime localDateTime) {
    
    return localDateTime.with(FIFTEEN_MINUTE_FLOOR_TEMPORAL_ADJUSTER);
  }

  public static LocalDateTime adjustCurrentTimeIntoDailyFloor() {
    
    return adjustTimeIntoDailyFloor(AbstractEntity
        .getTimeKeeper()
        .getCurrentLocalDateTime());
  }

  public static LocalDateTime adjustTimeIntoDailyFloor(LocalDateTime localDateTime) {
    
    return localDateTime
        .toLocalDate()
        .atStartOfDay();
  }

  public static LocalDateTime adjustCurrentTimeIntoMonthlyFloor() {
    
    return adjustTimeIntoMonthlyFloor(AbstractEntity
        .getTimeKeeper()
        .getCurrentLocalDateTime());
  }
  
  public static LocalDateTime adjustTimeIntoMonthlyFloor(LocalDateTime localDateTime) {

    return localDateTime
        .toLocalDate()
        .withDayOfMonth(1)
        .atStartOfDay()
        .truncatedTo(ChronoUnit.DAYS);
  }
  
  public static final Set<String> EMPTY_MODIFIED_ATTRIBUTES = new HashSet<>();
  
  private boolean isDeleted = false;
  private Set<String> modifiedAttributeNames = null; // This is instantiated when needed
  
  public AbstractEntity() {
    super();
  }
  
  protected transient String _naturalIdentity;
  protected void resetTransientAttributes() {
    _naturalIdentity = null;
  }
  
  public abstract String getNaturalIdentity();
  
  public boolean getIsDeleted() {
    return isDeleted;
  }

  public void setIsDeleted() {
    
    if (getIsModified()) {
      setNotModified();
    }
    isDeleted = true;
  }
  
  public boolean getIsModified() {
    
    if (modifiedAttributeNames != null && !modifiedAttributeNames.isEmpty()) {
      return true;
    }
    return false;
  }
  
  public Set<String> getModifiedAttributes() {

    if (modifiedAttributeNames != null) {
      return modifiedAttributeNames;
    }
    return EMPTY_MODIFIED_ATTRIBUTES;
  }

  public void setIsModified(String modifiedAttributeName) {
    
    if (modifiedAttributeNames == null) {
      modifiedAttributeNames = new TreeSet<>();
    }
    modifiedAttributeNames.add(modifiedAttributeName);
  }

  public void setNotModified() {
    modifiedAttributeNames = null;
  }
  
  /**
   * This method can be overridden by sub-classes that need to do non-trivial state evaluation. 
   * A state change may occur in several ways:
   * <ol>
   *   <li> An explicit operation call initiated by the end-user</li>
   *   <li> Implicitly via the state of the aggregate (e.g. child entities have been created/updated
   *        that would automatically "trigger" a state transition)</li>
   *   <li> Implicitly via the passage of time (e.g. The 'expiration date' has been reached)      
   * </ol>
   */
  public void evaluateState() {
  }

  /**
   * To be implemented by those subclasses that want to do validation,
   * but not validation with the option for remediation.
   * For example dictionary entities such as AD Function Templates...
   * </p> 
   * In addition, this method can act as a guard to ensure that any explicit/enumerated state/status 
   * attributes do indeed match the implicit state of the aggregate (e.g. If some 'active' state
   * requires the existence of some child entity in state X, then this method should identify if
   * there is any discrepancy between the two.
   *  
   * @param validationMessages A list of validation messages identified.
   */
  public void validate(List<ValidationMessage> validationMessages) {
	  
  }
  
  /**
   * 
   * @return
   */
  public List<ValidationMessage> validate() {

    List<ValidationMessage> validationMessages = new ArrayList<>();
    validate(validationMessages);
    Collections.sort(validationMessages);
    return validationMessages;
  }  

  @Override
  public int compareTo(AbstractEntity that) {

    return this.getNaturalIdentity().compareTo(that.getNaturalIdentity());
  }

  @Override
  public int hashCode() {

    return getNaturalIdentity().hashCode();
  }

  @Override
  public boolean equals(Object that) {

    if (that == null) {
      return false;
    }

    if (that == this) {
      return true;
    }

    if (!this.getClass().equals(that.getClass())) {
      return false;
    }

    return this.getNaturalIdentity().equals(((AbstractEntity) that).getNaturalIdentity());
  }

  public String getClassAndNaturalIdentity() {

    return new StringBuilder()
        .append(getClass().getSimpleName())
        .append("[")
        .append(getNaturalIdentity())
        .append("]")
        .toString();
  }
  
  @Override
  public String toString() {
    return getNaturalIdentity();
  }
  
  protected <T> boolean addChild(
      Set<T> set, 
      T t,
      AbstractEntity parent) throws EntityAlreadyExistsException {
    
    if (set.contains(t)) {
      throw new EntityAlreadyExistsException(
          parent.getClassAndNaturalIdentity()
          + " already has " 
          + t.getClass().getSimpleName()
          + ": [" 
          + t 
          + "]." );
    }
    return set.add(t);
  }  

  public <T extends AbstractEntity> T getChild(
      Class<T> childClass,
      Set<T> set,
      String naturalIdentity,
      AbstractEntity parent) throws EntityDoesNotExistException {

    return set
        .stream()
        .filter(child -> child.getNaturalIdentity().equals(naturalIdentity))
        .findAny()
        .orElseThrow(() -> new EntityDoesNotExistException(
            childClass.getSimpleName()
            + " with natural id: [" 
            + naturalIdentity 
            + "] not found in "
            + parent.getClassAndNaturalIdentity()
            ));
  } 

  public <T extends AbstractAssociativeEntity> T getChild(
      Class<T> childClass,
      Set<T> set,
      Map<String, Integer> parentIdentities,
      AbstractPersistentEntity parent) throws EntityDoesNotExistException {

    return set
        .stream()
        .filter(associativeEntity -> associativeEntity.getParentIdentities().equals(parentIdentities))
        .findAny()
        .orElseThrow(() -> new EntityDoesNotExistException(
            childClass.getSimpleName()
            + " with compound id: [" 
            + parentIdentities 
            + "] not found in: ["
            + parent.getClassAndPersistentIdentity()
            + "] with collection: "
            + set
            ));
  }
  
  /**
   * <pre>
   * TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
   * 
   * Format from DBeaver DB export to JSON:
   * 2017-06-20T22:34:01Z
   * 
   * Java format to string:
   * 2019-09-20 17:50:36.372801
   * 
   * @param strTimestamp The timestamp value as a string
   * 
   * @return the parsed timestamp
   */
  public static Timestamp parseTimestamp(String strTimestamp) {
    
    if (strTimestamp == null || strTimestamp.isEmpty()) {
      return null;
    }
    
    if (strTimestamp.contains("/")) {

      return Timestamp.valueOf(LocalDateTime.parse(
          strTimestamp, 
          DISPLAY_DATE_TIME_FORMATTER
          .get()));
    }
    
    // EXPECTED FORMAT: yyyy-MM-dd HH:mm:ss
    // DB JSON:         2017-06-20T22:34:01Z
    // Java String:     2019-09-20 17:50:36.372801
    String massagedTimestamp = strTimestamp
        .replace("T", " ")
        .replace("Z", "");
    
    // See if the timestamp came from a LocalDateTime with zero seconds
    // 2019-11-01 00:00
    int firstColonIndex = strTimestamp.indexOf(':');
    int secondColonIndex = strTimestamp.indexOf(':', firstColonIndex+1);
    if (secondColonIndex <= 0) {
      massagedTimestamp = massagedTimestamp + ":00";
    }
    
    int periodIndex = strTimestamp.indexOf('.');
    if (periodIndex > 0 ) {
      massagedTimestamp = massagedTimestamp.substring(0, periodIndex);
    }
    
    return Timestamp.valueOf(LocalDateTime.parse(
        massagedTimestamp, 
        DATE_TIME_FORMATTER
        .get()));
  }
  
  /**
   * 
   * @return the current time in yyyy-MM-dd HH:mm:ss format
   */
  public static String getCurrentTimestampAsFormattedString() {
    return formatTimestamp(AbstractEntity
        .getTimeKeeper()
        .getCurrentTimestamp());
  }

  /**
   * 
   * @param timestamp
   * 
   * @return the given timestamp in yyyy-MM-dd HH:mm:ss format
   */
  public static String formatTimestamp(Timestamp timestamp) {
    
    if (timestamp == null) {
      return null;
    }
    return AbstractEntity.toFormattedZonedTime(timestamp, "UTC");
  }

  /**
   * 
   * @param temporalAdjuster The temporal adjuster to normalize the timestamp into
   * 
   * @return The normalized timestamp
   */
  public static long normalizeTimestampEpochSeconds(TemporalAdjuster temporalAdjuster) {

    LocalDateTime localDateTime = AbstractEntity
        .getTimeKeeper()
        .getCurrentLocalDateTime()
        .with(temporalAdjuster);
    
    long normalizedTimestampEpochSeconds = localDateTime
        .atZone(UTC_ZONE_ID)
        .toInstant()
        .getEpochSecond();
    
    return normalizedTimestampEpochSeconds;
  }   
  /**
   * 
   * @param timestampEpochSeconds The timestamp, in epoch seconds, to normalize
   * @param temporalAdjuster The temporal adjuster to normalize the timestamp into
   * 
   * @return The normalized timestamp
   */
  public static long normalizeTimestampEpochSeconds(
      long timestampEpochSeconds, 
      TemporalAdjuster temporalAdjuster) {

    if (Long.toString(timestampEpochSeconds).length() == 13) {
      throw new IllegalStateException("Expected timestamp in epoch seconds, but got epoch millis instead: " + timestampEpochSeconds);
    }
    
    LocalDateTime localDateTime = Instant
        .ofEpochSecond(timestampEpochSeconds)
        .atZone(UTC_ZONE_ID)
        .toLocalDateTime()
        .with(temporalAdjuster);
    
    long normalizedTimestampEpochSeconds = localDateTime
        .atZone(UTC_ZONE_ID)
        .toInstant()
        .getEpochSecond();
    
    return normalizedTimestampEpochSeconds;
  }  
}
