package com.djt.jukeanator_engine.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches for every {@code app.mode} value except {@code master} (i.e. the default {@code
 * standalone} mode, unset, and {@code slave}).
 *
 * <p>A headless master is location-agnostic: there is no local song library on disk, no local
 * queue tied to one physical room, and no local audio hardware to play through (VLC/Winamp are
 * never installed on a master host by design). Beans that own that local, hardware-backed state
 * — the real {@code SongPlayerService}, its audio-device dependencies, {@code SongQueueService},
 * and {@code BackgroundMusicService} — must therefore never be constructed on master. Master-side
 * per-location control instead goes through {@code LocationServiceRegistry} and the {@code
 * *LocationProxy} classes (see docs/multi-tenant-mode.md).
 *
 * <p>{@code @ConditionalOnProperty}'s {@code havingValue} only supports equality, so a plain
 * negation needs this small custom {@link Condition} rather than the annotation directly.
 */
public class NotMasterModeCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String mode = context.getEnvironment().getProperty("app.mode", "standalone");
    return !"master".equalsIgnoreCase(mode);
  }
}
