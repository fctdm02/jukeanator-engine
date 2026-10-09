package com.djt.jukeanator_engine.domain.user.model;

/**
 * Whether an account may log in. A Web/Mobile UI registration starts PENDING and becomes ACTIVE
 * once its owner opens the verification link emailed to them.
 */
public enum UserStatus {
  PENDING,
  ACTIVE
}
