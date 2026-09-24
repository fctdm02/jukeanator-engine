package com.djt.jukeanator_engine.domain.user.event;

import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;

/**
 * Published by {@code UserServiceImpl} right after a location-attributed mobile/web song-credit
 * spend is recorded. Harmless to publish on any instance -- only master's {@code
 * MobileCreditUsageSlaveNotifier} listens, mirroring the spend down to {@code
 * usage.locationId()}'s slave.
 */
public record LocationSongCreditUsageRecordedEvent(UserSongCreditUsageDto usage) {
}
