/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import io.viki.momentum.math.shape.Poly;
import java.util.Objects;

/** Immutable world collision data produced by the voxel query. */
record WorldCollider(Poly shape, double x, double y, boolean oneWay, boolean stepable,
    float friction, double sortDistanceSquared, boolean initiallyOverlapping) {
  WorldCollider {
    Objects.requireNonNull(shape, "shape");
    if (shape.isEmpty()) {
      throw new IllegalArgumentException("A world collider cannot be empty");
    }
    if (!Double.isFinite(x) || !Double.isFinite(y)
        || !Float.isFinite(friction) || friction < 0.0F
        || !Double.isFinite(sortDistanceSquared) || sortDistanceSquared < 0.0) {
      throw new IllegalArgumentException("World collider coordinates must be finite");
    }
  }
}
