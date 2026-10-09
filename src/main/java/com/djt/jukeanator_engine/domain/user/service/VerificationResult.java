package com.djt.jukeanator_engine.domain.user.service;

/** The outcome of opening an emailed account-verification link. */
public enum VerificationResult {
  VERIFIED,
  EXPIRED,
  INVALID
}
