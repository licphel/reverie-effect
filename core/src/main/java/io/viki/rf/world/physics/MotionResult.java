/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import io.viki.momentum.math.Vector2;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable result of one bounded movement step. */
record MotionResult(Vector2 movement, Vector2 correction, @Nullable MotionContact contact,
    @Nullable MotionContact collision, boolean stuck) {
  MotionResult {
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(correction, "correction");
  }

  boolean onGround() {
    return contact != null;
  }
}
