/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */

package io.viki.rf.world.level;

import io.viki.rf.world.util.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Coordinates chunk streaming from server-owned interests. */
public final class ChunkManager {
  public static final int MAX_INTEREST_RADIUS = 32;
  public static final int DEFAULT_PUBLISH_BUDGET = 8;
  private static final int RETENTION_MARGIN = 1;

  private final Level level;
  private final Map<UUID, Interest> interests = new HashMap<>();

  ChunkManager(Level level) {
    this.level = level;
  }

  public Interest interest(UUID owner) {
    Objects.requireNonNull(owner, "owner");
    return interests.computeIfAbsent(owner, ignored -> new Interest());
  }

  public void removeInterest(UUID owner) {
    interests.remove(Objects.requireNonNull(owner, "owner"));
  }

  /** Schedules changed windows, publishes prepared chunks, then evicts stale chunks. */
  public void tick() {
    for (Interest interest : interests.values()) {
      interest.scheduleIfChanged();
    }
    level.pumpChunks(DEFAULT_PUBLISH_BUDGET);
    if (!interests.isEmpty()) {
      level.unloadChunksOutside(this);
    }
  }

  boolean retains(ChunkPos pos) {
    for (Interest interest : interests.values()) {
      if (interest.contains(pos, RETENTION_MARGIN)) {
        return true;
      }
    }
    return false;
  }

  public boolean isInterested(UUID owner, ChunkPos pos) {
    Interest interest = interests.get(Objects.requireNonNull(owner, "owner"));
    return interest != null && interest.contains(pos, 0);
  }

  public List<ChunkPos> desiredChunks(UUID owner) {
    Interest interest = interests.get(Objects.requireNonNull(owner, "owner"));
    if (interest == null) {
      return List.of();
    }
    var result = new ArrayList<ChunkPos>((interest.radius * 2 + 1) * (interest.radius * 2 + 1));
    interest.visit(result::add);
    return result;
  }

  /** Mutable server-owned chunk window. It is not derived by Entity itself. */
  public final class Interest {
    private ChunkPos center = new ChunkPos(0, 0);
    private int radius;
    private boolean changed = true;

    private Interest() {
    }

    public void update(double worldX, double worldY, int radius) {
      if (radius < 0 || radius > MAX_INTEREST_RADIUS) {
        throw new IllegalArgumentException(
            "chunk interest radius must be between 0 and " + MAX_INTEREST_RADIUS + ": " + radius);
      }
      var nextCenter = new ChunkPos(
          Math.floorDiv((int) Math.floor(worldX), ChunkPos.SIZE),
          Math.floorDiv((int) Math.floor(worldY), ChunkPos.SIZE));
      if (!nextCenter.equals(center) || this.radius != radius) {
        center = nextCenter;
        this.radius = radius;
        changed = true;
      }
    }

    /** Loads the complete window synchronously, intended for initial spawn preparation. */
    public void loadNow() {
      visit(level::requestChunk);
      changed = false;
    }

    private void scheduleIfChanged() {
      if (!changed) {
        return;
      }
      visit(level::requestChunkAsync);
      changed = false;
    }

    private void visit(java.util.function.Consumer<ChunkPos> action) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
          action.accept(center.offset(dx, dy));
        }
      }
    }

    private boolean contains(ChunkPos pos, int margin) {
      return Math.abs(pos.x() - center.x()) <= radius + margin
          && Math.abs(pos.y() - center.y()) <= radius + margin;
    }
  }
}
