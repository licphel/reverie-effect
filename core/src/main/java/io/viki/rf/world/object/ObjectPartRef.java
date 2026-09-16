/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.momentum.util.Identifier;
import io.viki.rf.world.util.BlockPos;

import java.util.Objects;

/** Durable local reference stored in every occupied chunk cell. */
public record ObjectPartRef(long objectId, BlockPos rootPosition, Identifier definitionId,
                            boolean mirrorX, int partIndex, int revision,
                            int variant, int physicalState) {
  public ObjectPartRef {
    Objects.requireNonNull(rootPosition, "rootPosition");
    Objects.requireNonNull(definitionId, "definitionId");
    if (objectId <= 0L) {
      throw new IllegalArgumentException("Object id must be positive: " + objectId);
    }
    if (partIndex < 0) {
      throw new IllegalArgumentException("Object part index must be non-negative: " + partIndex);
    }
    if (revision <= 0) {
      throw new IllegalArgumentException("Object revision must be positive: " + revision);
    }
    if (variant < 0) {
      throw new IllegalArgumentException("Object variant must be non-negative: " + variant);
    }
  }
}
