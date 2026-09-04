package com.djt.jukeanator_engine.domain.user.service;

/**
 * Computes Web/Mobile UI credit costs as the equivalent JFC/Swing cost multiplied by
 * {@link PricingConfig#webCostMultiplier()} -- at a multiplier of 1, Web/Mobile costs exactly
 * match JFC/Swing. The Swing-side formulas themselves live in the JFC UI's own classes
 * ({@code AddSongToQueueCard}, {@code QueuePanel}) and can't be called directly from here (master
 * runs no Swing code at all), so they're mirrored below -- keep both sides in sync if either
 * changes.
 */
public final class CreditCostCalculator {

  /** AddSongToQueueCard.normalPlayCost -- a fixed cost, not affected by priorityCostMultiplier. */
  private static final int SWING_NORMAL_PLAY_COST = 1;

  /** QueuePanel.computeCost's fixed per-priority-level multiplier (distinct from priorityCostMultiplier). */
  private static final int SWING_QUEUE_ACTION_MULTIPLIER = 3;

  private CreditCostCalculator() {}

  /**
   * Cost to add a song to the queue. {@code priority == 1} is a normal play (mirrors
   * AddSongToQueueCard's fixed {@code normalPlayCost}); {@code priority > 1} is a priority play
   * (mirrors {@code highestPriority * priorityCostMultiplier}).
   */
  public static int webQueueAddCost(PricingConfig config, int priority) {
    int swingCost =
        priority <= 1 ? SWING_NORMAL_PLAY_COST : priority * config.priorityCostMultiplier();
    return swingCost * config.webCostMultiplier();
  }

  /** Cost to reorder/remove an already-queued song, mirroring QueuePanel.computeCost. */
  public static int webQueueActionCost(PricingConfig config, int priority) {
    int swingCost = Math.max(1, priority * SWING_QUEUE_ACTION_MULTIPLIER);
    return swingCost * config.webCostMultiplier();
  }
}
