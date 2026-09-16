/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

/** Immutable result of sweeping one convex polygon against another. */
record SweepResult(boolean hit, float time, float normalX, float normalY, float penetration) {
  SweepResult {
    if (!Float.isFinite(time) || !Float.isFinite(normalX) || !Float.isFinite(normalY)
        || !Float.isFinite(penetration) || time < 0.0F || time > 1.0F
        || penetration < 0.0F) {
      throw new IllegalArgumentException("Sweep result must contain finite values in range");
    }
    if (hit && normalX * normalX + normalY * normalY <= 0.0F) {
      throw new IllegalArgumentException("A sweep hit must contain a non-zero normal");
    }
  }

  static SweepResult none() {
    return new SweepResult(false, 1.0F, 0.0F, 0.0F, 0.0F);
  }

  static SweepResult penetration(float normalX, float normalY, float penetration) {
    return new SweepResult(true, 0.0F, normalX, normalY, penetration);
  }
}
