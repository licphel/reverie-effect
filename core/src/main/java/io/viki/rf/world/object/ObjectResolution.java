/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Explicit result of a non-loading object lookup. */
public record ObjectResolution(Status status, @Nullable ObjectPartRef part,
                               @Nullable MultiBlockDefinition definition,
                               @Nullable ObjectRecord record,
                               @Nullable MultiBlockPart partData) {
  public ObjectResolution {
    Objects.requireNonNull(status, "status");
    if (status == Status.RESOLVED
        && (part == null || definition == null || record == null || partData == null)) {
      throw new IllegalArgumentException("Resolved object lookup must contain all object data");
    }
  }

  public enum Status {
    NONE,
    CHUNK_UNLOADED,
    RESOLVED,
    ROOT_UNLOADED,
    TOMBSTONED,
    STALE_REVISION,
    ORPHANED,
    CORRUPT
  }

  public boolean found() {
    return status == Status.RESOLVED;
  }
}
