/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.rf.world.physics.Collision;
import io.viki.rf.world.util.BlockPos;

import java.util.List;
import java.util.Objects;

/** Immutable local data for one occupied cell of a multi-block definition. */
public record MultiBlockPart(BlockPos offset, Collision collisionShape,
                             List<ObjectPartRender> renderPieces, boolean selectable,
                             boolean blocksFluid, boolean blocksLight, float friction) {
  public MultiBlockPart {
    Objects.requireNonNull(offset, "offset");
    Objects.requireNonNull(collisionShape, "collisionShape");
    Objects.requireNonNull(renderPieces, "renderPieces");
    renderPieces = List.copyOf(renderPieces);
    if (!Float.isFinite(friction) || friction < 0.0F) {
      throw new IllegalArgumentException("Object part friction must be finite and non-negative: " + friction);
    }
  }

  public static MultiBlockPart solid(BlockPos offset, Collision collisionShape,
                                     List<ObjectPartRender> renderPieces) {
    return new MultiBlockPart(offset, collisionShape, renderPieces, true, true, true,
        PhysicsConstants.STANDARD_FRICTION_COEFFICIENT);
  }

  public boolean hasCollision() {
    return !collisionShape.shape().isEmpty();
  }
}
