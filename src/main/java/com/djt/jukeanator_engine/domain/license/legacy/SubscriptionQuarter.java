package com.djt.jukeanator_engine.domain.license.legacy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A calendar quarter in the legacy {@code nQyyyy} notation (e.g. {@code 4Q2027}), used for the
 * subscription "paid through" quarter on the server and in {@code ExtendCode.CTL}.
 *
 * <p>Expiry follows the legacy client exactly: a quarter expires at local midnight at the
 * <em>start</em> of its last day (Mar 31, Jun 30, Sep 30, Dec 31), so existing files expire on the
 * same day under both applications.
 */
public final class SubscriptionQuarter implements Comparable<SubscriptionQuarter> {

  /** Legacy client constant: hours added per extra quarter paid in advance. */
  public static final int LEGACY_HOURS_PER_QUARTER = 2192;

  private static final Pattern FORMAT = Pattern.compile("^([1-4])Q(\\d{4})$");

  private final int quarter;
  private final int year;

  public SubscriptionQuarter(int quarter, int year) {
    if (quarter < 1 || quarter > 4) {
      throw new IllegalArgumentException("Quarter must be 1-4: " + quarter);
    }
    this.quarter = quarter;
    this.year = year;
  }

  public static SubscriptionQuarter parse(String text) {
    Matcher m = FORMAT.matcher(text == null ? "" : text.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException("Invalid quarter: " + text);
    }
    return new SubscriptionQuarter(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
  }

  public static boolean isValid(String text) {
    return text != null && FORMAT.matcher(text.trim()).matches();
  }

  public static SubscriptionQuarter of(LocalDate date) {
    return new SubscriptionQuarter((date.getMonthValue() - 1) / 3 + 1, date.getYear());
  }

  public static SubscriptionQuarter current(ZoneId zone) {
    return of(LocalDate.now(zone));
  }

  /**
   * Legacy server {@code DatabaseUtil.getNextYearQuarter()}: the current quarter of next year.
   * This is the "paid through" value stamped on a machine when a license is provisioned.
   */
  public static SubscriptionQuarter nextYearQuarter(ZoneId zone) {
    return current(zone).plus(4);
  }

  public SubscriptionQuarter plus(int quarters) {
    int index = year * 4 + (quarter - 1) + quarters;
    return new SubscriptionQuarter(Math.floorMod(index, 4) + 1, Math.floorDiv(index, 4));
  }

  public int getQuarter() {
    return quarter;
  }

  public int getYear() {
    return year;
  }

  /** First day of the quarter. */
  public LocalDate firstDay() {
    return LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
  }

  /** Last day of the quarter (Mar 31, Jun 30, Sep 30, Dec 31). */
  public LocalDate lastDay() {
    return firstDay().plusMonths(3).minusDays(1);
  }

  /** Legacy expiry instant: local midnight at the start of the quarter's last day. */
  public ZonedDateTime expiry(ZoneId zone) {
    return lastDay().atStartOfDay(zone);
  }

  /**
   * Whole hours from {@code now} until this quarter's expiry, never negative. Used to compute the
   * {@code hoursRemaining} written to a new extend code (the legacy client approximated this with
   * a fixed 29-day February and 2192 hours per quarter; the new application uses the calendar).
   */
  public int hoursUntilExpiry(ZonedDateTime now) {
    long hours = Duration.between(now, expiry(now.getZone())).toHours();
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, hours));
  }

  public boolean isExpired(ZonedDateTime now) {
    return !now.isBefore(expiry(now.getZone()));
  }

  /** Legacy warning window: within 7 days of expiry. */
  public boolean isInWarningWindow(ZonedDateTime now) {
    return !now.isBefore(expiry(now.getZone()).minusDays(7));
  }

  public LocalDateTime expiryLocal() {
    return lastDay().atStartOfDay();
  }

  @Override
  public int compareTo(SubscriptionQuarter o) {
    return Integer.compare(year * 4 + quarter, o.year * 4 + o.quarter);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SubscriptionQuarter that && quarter == that.quarter && year == that.year;
  }

  @Override
  public int hashCode() {
    return Objects.hash(quarter, year);
  }

  @Override
  public String toString() {
    return quarter + "Q" + year;
  }
}
