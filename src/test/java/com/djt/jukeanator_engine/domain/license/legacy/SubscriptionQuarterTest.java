package com.djt.jukeanator_engine.domain.license.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class SubscriptionQuarterTest {

  private static final ZoneId ZONE = ZoneId.of("America/Detroit");

  @Test
  void parseAndFormat() {
    SubscriptionQuarter q = SubscriptionQuarter.parse("4Q2027");
    assertEquals(4, q.getQuarter());
    assertEquals(2027, q.getYear());
    assertEquals("4Q2027", q.toString());
    assertThrows(IllegalArgumentException.class, () -> SubscriptionQuarter.parse(""));
    assertThrows(IllegalArgumentException.class, () -> SubscriptionQuarter.parse("5Q2027"));
    assertFalse(SubscriptionQuarter.isValid("Q2027"));
  }

  @Test
  void ofDate_and_plusWrapsYears() {
    assertEquals("4Q2026", SubscriptionQuarter.of(LocalDate.of(2026, 11, 8)).toString());
    assertEquals("1Q2026", SubscriptionQuarter.of(LocalDate.of(2026, 1, 1)).toString());
    SubscriptionQuarter q = SubscriptionQuarter.parse("3Q2026");
    assertEquals("4Q2026", q.plus(1).toString());
    assertEquals("1Q2027", q.plus(2).toString());
    assertEquals("3Q2027", q.plus(4).toString());
    assertEquals("4Q2025", q.plus(-3).toString());
  }

  @Test
  void expiry_isMidnightStartingLastDayOfQuarter() {
    assertEquals(LocalDate.of(2027, 3, 31), SubscriptionQuarter.parse("1Q2027").lastDay());
    assertEquals(LocalDate.of(2027, 6, 30), SubscriptionQuarter.parse("2Q2027").lastDay());
    assertEquals(LocalDate.of(2027, 9, 30), SubscriptionQuarter.parse("3Q2027").lastDay());
    assertEquals(LocalDate.of(2027, 12, 31), SubscriptionQuarter.parse("4Q2027").lastDay());

    SubscriptionQuarter q = SubscriptionQuarter.parse("4Q2026");
    assertFalse(q.isExpired(ZonedDateTime.of(2026, 12, 30, 23, 59, 0, 0, ZONE)));
    assertTrue(q.isExpired(ZonedDateTime.of(2026, 12, 31, 0, 0, 0, 0, ZONE)));
  }

  @Test
  void warningWindow_isSevenDaysBeforeExpiry() {
    SubscriptionQuarter q = SubscriptionQuarter.parse("4Q2026");
    assertFalse(q.isInWarningWindow(ZonedDateTime.of(2026, 12, 23, 23, 0, 0, 0, ZONE)));
    assertTrue(q.isInWarningWindow(ZonedDateTime.of(2026, 12, 24, 0, 0, 0, 0, ZONE)));
  }

  @Test
  void hoursUntilExpiry() {
    SubscriptionQuarter q = SubscriptionQuarter.parse("4Q2026");
    assertEquals(24, q.hoursUntilExpiry(ZonedDateTime.of(2026, 12, 30, 0, 0, 0, 0, ZONE)));
    assertEquals(0, q.hoursUntilExpiry(ZonedDateTime.of(2027, 1, 5, 0, 0, 0, 0, ZONE)));
  }
}
