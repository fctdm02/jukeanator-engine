package com.djt.jukeanator_engine.domain.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.user.dto.UserDto;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserStatus;

class UserMapperVerificationTest {

  @Test
  void userFileWrittenBeforeVerificationExistedReadsAsActive() {

    UserDto legacy = new UserDto(1, "Jane", "Doe", "jane@example.com", "hash", 0, List.of(),
        List.of(), List.of(), List.of(), List.of(), "ROLE_USER", null, null, null);

    UserEntity user = UserMapper.toEntity(legacy);

    assertEquals(UserStatus.ACTIVE, user.getStatus());
    assertFalse(user.isPending());
  }

  @Test
  void pendingStatusAndTokenRoundTrip() {

    UserEntity user = new UserEntity(1, "Jane", "Doe", "jane@example.com", "hash", 0,
        UserRole.ROLE_USER);
    Instant expiresAt = Instant.parse("2026-10-10T12:00:00Z");
    user.issueVerificationToken("abc123", expiresAt);

    UserEntity restored = UserMapper.toEntity(UserMapper.toDto(user));

    assertTrue(restored.isPending());
    assertEquals("abc123", restored.getVerificationTokenHash());
    assertEquals(expiresAt, restored.getVerificationTokenExpiresAt());

    restored.markVerified();
    UserEntity verified = UserMapper.toEntity(UserMapper.toDto(restored));
    assertEquals(UserStatus.ACTIVE, verified.getStatus());
    assertNull(verified.getVerificationTokenHash());
  }
}
