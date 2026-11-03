package com.djt.jukeanator_engine.domain.user.service;

/**
 * Computes Web/Mobile UI credit costs as the equivalent JFC/Swing cost multiplied by
 * {@link PricingConfig#webCostMultiplier()} -- at a multiplier of 1, Web/Mobile costs exactly
 * match JFC/Swing. The Swing-side formulas themselves live in the JFC UI's own classes
 * ({@code AddSongToQueueCard}, {@code QueuePanel}) and can't be called directly from here (master
 * runs no Swing code at all), so they're mirrored below -- keep both sides in sync if either
 * changes.
 *
 * <p>The priority level fed in here is not always the queue's next available level: once a user has
 * made a priority play, their later ones reuse its level (same cost, queued behind it). That rule
 * lives in {@code AddSongToQueueCard}/{@code CreditManager} (JFC/Swing, per credit session) and
 * {@code SongQueueController} (web, while the user's priority play is still queued).
 */
public final class CreditCostCalculator {

  /** AddSongToQueueCard.normalPlayCost -- a fixed cost, not affected by priorityCostMultiplier. */
  private static final int SWING_NORMAL_PLAY_COST = 1;

  /** QueuePanel.computeCost's fixed per-priority-level multiplier (distinct from priorityCostMultiplier). */
  private static final int SWING_QUEUE_ACTION_MULTIPLIER = 3;

  private CreditCostCalculator() {}

  /**
   * Cost to add a song to the queue. {@code priorityPlay} mirrors which button the caller
   * pressed -- normal play uses AddSongToQueueCard's fixed {@code normalPlayCost}; priority play
   * is priced by the level it jumps, not the level it queues at: it queues one above the song it
   * jumps, so it costs {@code (priority - 1) * priorityCostMultiplier} -- mirroring
   * AddSongToQueueCard.priorityPlayCost. A priority play is only allowed over another user's
   * priority-1-or-higher song (see {@code SongQueueService.isPriorityPlayAllowed}), so it always
   * queues at priority 2 or higher.
   */
  public static int webQueueAddCost(PricingConfig config, int priority, boolean priorityPlay) {
    int swingCost = priorityPlay ? Math.max(1, priority - 1) * config.priorityCostMultiplier()
        : SWING_NORMAL_PLAY_COST;
    return swingCost * config.webCostMultiplier();
  }

  /** Cost to reorder/remove an already-queued song, mirroring QueuePanel.computeCost. */
  public static int webQueueActionCost(PricingConfig config, int priority) {
    int swingCost = Math.max(1, priority * SWING_QUEUE_ACTION_MULTIPLIER);
    return swingCost * config.webCostMultiplier();
  }
}
