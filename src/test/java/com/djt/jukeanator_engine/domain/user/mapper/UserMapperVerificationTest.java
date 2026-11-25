package com.djt.jukeanator_engine.domain.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.user.dto.UserDto;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserLocationAccountEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;
import com.djt.jukeanator_engine.domain.user.model.UserStatus;

class UserMapperVerificationTest {

  @Test
  void userFileWrittenBeforeVerificationExistedReadsAsActive() {

    UserDto legacy = new UserDto(1, "Jane", "Doe", "jane@example.com", "hash", List.of(),
        List.of(), "ROLE_USER", null, null, null);

    UserEntity user = UserMapper.toEntity(legacy);

    assertEquals(UserStatus.ACTIVE, user.getStatus());
    assertFalse(user.isPending());
  }

  @Test
  void pendingStatusAndTokenRoundTrip() {

    UserEntity user = new UserEntity(1, "Jane", "Doe", "jane@example.com", "hash",
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

  @Test
  void locationAccountsRoundTrip_withBalancePlaysPlaylistsAndLedgers() throws Exception {

    UserEntity user = new UserEntity(1, "Jane", "Doe", "jane@example.com", "hash",
        UserRole.ROLE_USER);
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(7);
    account.setNumCredits(12);
    account.addSongToSongPlayHistory(new SongIdentifier(7, 1, 2));
    account.createPlaylist("Party Mix").addSong(new SongIdentifier(7, 3, 4));
    account.addSongCreditUsage(new UserSongCreditUsageEntity(account.nextSongCreditUsageIdentity(),
        7, -2, UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 12, "sync-1"));
    account.addAddFundsTransaction(new UserAddFundsTransactionEntity(
        account.nextAddFundsTransactionIdentity(), 7, "pkg-7", 12, 2, new BigDecimal("7.00"),
        "Visa", "tx-1", Instant.now(), 14));
    user.getOrCreateLocationAccount(9);

    UserEntity restored = UserMapper.toEntity(UserMapper.toDto(user));

    assertEquals(2, restored.getLocationAccounts().size());
    UserLocationAccountEntity restoredAccount = restored.getLocationAccountNullIfNotExists(7);
    assertNotNull(restoredAccount);
    assertEquals(12, restoredAccount.getNumCredits());
    assertEquals(List.of(new SongIdentifier(7, 1, 2)), restoredAccount.getSongPlayHistory());
    assertEquals(List.of(new SongIdentifier(7, 3, 4)),
        restoredAccount.getPlaylistByName("Party Mix").getSongs());
    assertNotNull(restoredAccount
        .getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME));
    assertEquals(1, restoredAccount.getSongCreditUsages().size());
    assertEquals(1, restoredAccount.getAddFundsTransactions().size());
    assertEquals(Integer.valueOf(7),
        restoredAccount.getAddFundsTransactions().iterator().next().getLocationId());
    assertEquals(2, restoredAccount.getPlaylists().size(),
        "rehydration restores the stored playlists without seeding another My Favorites");
  }
}
