/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import java.util.Objects;

import io.viki.momentum.math.Vector2;

/** Immutable support contact returned by the motion solver. */
record MotionContact(Vector2 normal, float friction) {
  MotionContact {
    Objects.requireNonNull(normal, "normal");
    if (!Float.isFinite(normal.x()) || !Float.isFinite(normal.y())
        || !Float.isFinite(friction) || friction < 0.0F) {
      throw new IllegalArgumentException(
          "Motion contact must contain a finite normal and non-negative friction");
    }
    float lengthSquared = normal.x() * normal.x() + normal.y() * normal.y();
    if (Math.abs(lengthSquared - 1.0F) > 0.001F) {
      throw new IllegalArgumentException("Motion contact normal must be normalized");
    }
  }

  boolean slope() {
    return normal.y() < 1.0F - (float) PhysicsConstants.CONTACT_EPSILON;
  }
}
