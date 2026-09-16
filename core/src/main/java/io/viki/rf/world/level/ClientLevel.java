/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */

package io.viki.rf.world.level;

import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.rf.client.InterpolationTracker;
import io.viki.rf.network.packet.EntityPhysicsPacket;
import io.viki.rf.world.util.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;

/** Client-side world mirror populated by authoritative server snapshots. */
@SideOnly(dist = Dist.CLIENT)
public final class ClientLevel extends Level {
  private @org.jspecify.annotations.Nullable InterpolationTracker interpolationTracker;
  private final Set<Long> initializedChunks = new HashSet<>();
  private final Map<UUID, PendingPhysics> pendingPhysics = new HashMap<>();
  private int expectedChunks = Integer.MAX_VALUE;
  private boolean initializing;
  private boolean ready;

  public ClientLevel(long seed) {
    // Empty chunks are storage targets for snapshot application; this side
    // never runs terrain generators.
    super((chunk, ignoredSeed) -> { }, seed);
  }

  public void bindNetworkState(InterpolationTracker interpolationTracker) {
    this.interpolationTracker = java.util.Objects.requireNonNull(interpolationTracker, "interpolationTracker");
  }

  public void clearNetworkState() {
    interpolationTracker = null;
    initializedChunks.clear();
    pendingPhysics.clear();
    initializing = false;
    ready = false;
  }

  public InterpolationTracker interpolationTracker() {
    InterpolationTracker current = interpolationTracker;
    if (current == null) throw new IllegalStateException("Client network state is not bound");
    return current;
  }

  public Set<Long> initializedChunks() { return initializedChunks; }
  public Map<UUID, PendingPhysics> pendingPhysics() { return pendingPhysics; }
  public int expectedChunks() { return expectedChunks; }
  public boolean initializing() { return initializing; }
  public boolean ready() { return ready; }

  public void beginInitialization(int expectedChunks) {
    if (expectedChunks < 0) throw new IllegalArgumentException("Expected chunk count must not be negative");
    this.expectedChunks = expectedChunks;
    initializedChunks.clear();
    initializing = true;
    ready = false;
  }

  public void markReady() {
    initializing = false;
    ready = true;
  }

  public record PendingPhysics(double serverTime, EntityPhysicsPacket.State state) {
    public PendingPhysics {
      if (!Double.isFinite(serverTime)) throw new IllegalArgumentException("Physics server time must be finite");
      java.util.Objects.requireNonNull(state, "state");
    }
  }

  /** Installs authoritative serialized data without requesting or generating a chunk. */
  public void deserializeChunk(CompoundNBT nbt) {
    var pos = new ChunkPos(nbt.getInt("x", 0), nbt.getInt("y", 0));
    Chunk existing = getChunk(pos);
    if (existing == null) {
      existing = Chunk.deserialize(this, nbt);
      installChunk(existing);
    } else {
      existing.deserialize(nbt);
    }
    fluidEngine().activateChunk(existing);
  }

  @Override
  public Chunk requestChunk(ChunkPos pos) {
    Chunk chunk = getChunk(pos);
    if (chunk == null) {
      throw new IllegalStateException("Client cannot generate unloaded chunk " + pos);
    }
    return chunk;
  }

  @Override
  public CompletableFuture<Chunk> requestChunkAsync(ChunkPos pos) {
    Chunk chunk = getChunk(pos);
    return chunk == null
        ? CompletableFuture.failedFuture(
            new IllegalStateException("Client cannot request unloaded chunk " + pos))
        : CompletableFuture.completedFuture(chunk);
  }

  @Override
  public Chunk getOrLoadChunk(ChunkPos pos) {
    return requestChunk(pos);
  }

  @Override
  public Chunk getOrLoadChunkByKey(long key) {
    return requestChunk(ChunkPos.fromLong(key));
  }

  /**
   * Runs predictive client simulation. Chunk creation remains packet-only;
   * authoritative server updates reconcile any divergence.
   */
  @Override
  public void tick(double delta) {
    tickEntities(delta, true);
  }
}
