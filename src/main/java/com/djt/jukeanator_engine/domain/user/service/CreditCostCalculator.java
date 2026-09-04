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
   * Cost to add a song to the queue. {@code priorityPlay} mirrors which button the caller
   * pressed -- normal play uses AddSongToQueueCard's fixed {@code normalPlayCost}; priority play
   * uses {@code priority * priorityCostMultiplier}. This can't be inferred from {@code priority}
   * alone: {@code getHighestPriority()} returns {@code 1} whenever the queue's current top entry
   * is a priority-0 background song, so a genuine priority play can submit the exact same
   * {@code priority == 1} a normal play always does.
   */
  public static int webQueueAddCost(PricingConfig config, int priority, boolean priorityPlay) {
    int swingCost =
        priorityPlay ? priority * config.priorityCostMultiplier() : SWING_NORMAL_PLAY_COST;
    return swingCost * config.webCostMultiplier();
  }

  /** Cost to reorder/remove an already-queued song, mirroring QueuePanel.computeCost. */
  public static int webQueueActionCost(PricingConfig config, int priority) {
    int swingCost = Math.max(1, priority * SWING_QUEUE_ACTION_MULTIPLIER);
    return swingCost * config.webCostMultiplier();
  }
}
