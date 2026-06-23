package com.djt.jukeanator_engine.domain.songplayer.audio;

/**
 * Thin adapter so {@link JukeboxAudioCoordinator} doesn't need a compile-time dependency on your
 * actual SongQueueServiceImpl.
 *
 * Easiest integration: have your existing SongQueueServiceImpl implement this interface directly
 * (it's just two getters), e.g.:
 *
 * <pre>
 * public class SongQueueServiceImpl implements SongQueueService, SongQueueStateProvider {
 *     ...
 *     {@literal @}Override
 *     public boolean isQueueEmpty() { return queue.isEmpty(); }
 *
 *     {@literal @}Override
 *     public boolean isBackgroundMusicEnabled() { return enableBackgroundMusic; }
 * }
 * </pre>
 *
 * Spring will then autowire it into JukeboxAudioCoordinator automatically since it's the only bean
 * of this type. If you'd rather not touch that class, write a tiny {@code @Component} adapter that
 * wraps it instead.
 */
public interface SongQueueStateProvider {

  boolean isQueueEmpty();

  boolean isBackgroundMusicEnabled();
}
