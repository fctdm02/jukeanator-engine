package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown by {@link com.djt.jukeanator_engine.domain.user.service.UserService#login} when the
 * credentials are correct but the account is still PENDING email verification. A subclass of
 * {@link InvalidCredentialsException} so existing login callers need no change, but mapped to its
 * own status (403) so the Web/Mobile UI can offer to resend the verification email.
 */
public class AccountNotVerifiedException extends InvalidCredentialsException {

  private static final long serialVersionUID = 1L;

  public AccountNotVerifiedException(String message) {
    super(message);
  }
}
