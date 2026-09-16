/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

/** Immutable result of a convex-polygon separation query. */
record SatResult(boolean intersects, float x, float y, float distanceSquared) {
  SatResult {
    if (!Float.isFinite(x) || !Float.isFinite(y)
        || !Float.isFinite(distanceSquared) || distanceSquared < 0.0F) {
      throw new IllegalArgumentException("SAT result must contain finite values");
    }
  }

  static SatResult none() {
    return new SatResult(false, 0.0F, 0.0F, 0.0F);
  }
}
