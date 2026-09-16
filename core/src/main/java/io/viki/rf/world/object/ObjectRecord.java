/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.momentum.util.Identifier;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;

import java.util.Objects;
import java.util.Set;

/** Canonical immutable instance record retained independently of loaded chunks. */
public record ObjectRecord(long objectId, Identifier definitionId, BlockPos rootPosition,
                           boolean mirrorX, int revision, ObjectLifecycle lifecycle,
                           Set<ChunkPos> involvedChunks, int variant) {
  public ObjectRecord {
    Objects.requireNonNull(definitionId, "definitionId");
    Objects.requireNonNull(rootPosition, "rootPosition");
    Objects.requireNonNull(lifecycle, "lifecycle");
    Objects.requireNonNull(involvedChunks, "involvedChunks");
    involvedChunks = Set.copyOf(involvedChunks);
    if (objectId <= 0L) {
      throw new IllegalArgumentException("Object id must be positive: " + objectId);
    }
    if (revision <= 0) {
      throw new IllegalArgumentException("Object revision must be positive: " + revision);
    }
    if (variant < 0) {
      throw new IllegalArgumentException("Object variant must be non-negative: " + variant);
    }
    if (involvedChunks.isEmpty()) {
      throw new IllegalArgumentException("Object must involve at least one chunk");
    }
  }

  public ObjectRecord withLifecycle(ObjectLifecycle newLifecycle) {
    return new ObjectRecord(objectId, definitionId, rootPosition, mirrorX, revision,
        newLifecycle, involvedChunks, variant);
  }
}
