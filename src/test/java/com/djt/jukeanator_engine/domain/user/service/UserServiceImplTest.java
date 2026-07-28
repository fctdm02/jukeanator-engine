package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.repository.CreditLedgerRepository;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * Plain Mockito unit test for {@link UserServiceImpl#deleteAccount(String)} -- unlike
 * {@link UserServiceTest}, this mocks every collaborator instead of exercising a real
 * datastore.
 */
public class UserServiceImplTest {

  private static final String REGISTERED_EMAIL = "jane.doe@example.com";

  private UserRepository userRepository;
  private CreditLedgerRepository creditLedgerRepository;
  private UserRootEntity userRoot;
  private UserServiceImpl userService;

  @BeforeEach
  void setUp() throws EntityDoesNotExistException {

    userRepository = mock(UserRepository.class);
    creditLedgerRepository = mock(CreditLedgerRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    JwtUtil jwtUtil = mock(JwtUtil.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    SongLibraryService songLibraryService = mock(SongLibraryService.class);

    userRoot = new UserRootEntity();
    userRoot.addUser(new UserEntity(Integer.valueOf(1), "Jane", "Doe", REGISTERED_EMAIL,
        "hashed-password", Integer.valueOf(6), "ROLE_USER"));

    when(userRepository.loadAggregateRoot(anyString())).thenReturn(userRoot);
    when(creditLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger for test"));

    userService = new UserServiceImpl("test-root", userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, creditLedgerRepository, false);
  }

  @Test
  void deleteAccountRemovesRegisteredUserAndPersistsRoot() {

    userService.deleteAccount(REGISTERED_EMAIL);

    assertNull(userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL),
        "user should no longer be present in the user root");
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void deleteAccountIsIdempotentlyRejectedOnSecondCall() {

    userService.deleteAccount(REGISTERED_EMAIL);

    assertThrows(InvalidPrincipalException.class,
        () -> userService.deleteAccount(REGISTERED_EMAIL));
  }

  @Test
  void deleteAccountThrowsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class,
        () -> userService.deleteAccount("unknown@example.com"));

    verify(userRepository, never()).storeAggregateRoot(userRoot);
  }
}
