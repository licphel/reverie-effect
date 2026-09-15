/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.rf.world.fluid;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import io.viki.rf.world.block.Shape;
import io.viki.rf.GameConstants;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Random;

/**
 * Classic Starbound liquid cellular automaton, run on the main thread from
 * {@link Level#tick(double)}.
 *
 * <p>Each tile holds a discrete liquid level ({@code 255} = full,
 * the minimum amount is 1). Every simulation tick, a tile first
 * falls into the tile below (filling it up to full), then equalizes
 * sideways by moving half the integer level difference into a lower
 * neighbor — a single unit that cannot split further evaporates — and
 * finally squeezes any overfill (level &gt; 255) upward, so water falls
 * quickly, seeks its own level and rises under pressure, all from purely
 * local rules.
 *
 * <p>Only tiles that hold liquid are simulated. A tile joins the active
 * list when liquid is written into it ({@link #join(int, int)}, called by
 * {@code Level.setLiquid} and by every flow) and is removed when its chunk
 * unloads ({@link #delChunk(ChunkPos)}), so the engine never scans the
 * whole world. The tick processes the list sorted bottom-up; flowing liquid
 * re-joins its destination, so it falls at most one tile per tick and every
 * tile is updated at most once. Solid tiles and unloaded chunks are
 * impassable. Different liquids touching each other react (lava + water →
 * stone) or the thinner one converts into the thicker one, so they never
 * just sit layered next to each other.
 *
 * <p>All tile access goes through the {@link Level} chunk map (allocation-
 * free via {@link ChunkPos#packBlockPosAsLong(int, int)}) and the
 * {@link Chunk} proxy methods.
 */
public final class FluidEngine {
  /** The level of a full tile. */
  public static final int FULL = 255;
  /** Simulation cadence in seconds. */
  public static final double TICK_INTERVAL = 1.0 / GameConstants.TICKS_PER_SECOND;
  /**
   * The minimum amount of liquid that exists: a single unit that cannot
   * split further during equalization evaporates.
   */
  public static final int MINIMUM_LIQUID_LEVEL = 1;

  private final Level level;
  private final Random random = new Random(42);
  // Reused scratch cells so the hot loops allocate nothing.
  private final Cell cellA = new Cell();
  private final Cell cellB = new Cell();
  // Alternate the horizontal sweep while keeping the vertical sweep bottom-up.
  private boolean reverseX;
  private double tickAcc;
  // Active cells: tiles that hold liquid and need an update. The list is
  // unsorted and may contain duplicates; each tick sorts and dedupes it,
  // then updates the tiles bottom-up. Cells join via join() when liquid is
  // written and leave via delChunk() when a chunk unloads.
  private long[] active = new long[256];
  private int activeCount;
  private final LongOpenHashSet simulatedChunks = new LongOpenHashSet();

  public FluidEngine(Level level) {
    this.level = level;
  }

  public void tick(double delta) {
    tickAcc += delta;
    while (tickAcc >= TICK_INTERVAL) {
      tickAcc -= TICK_INTERVAL;
      tickOnce();
    }
  }

  /**
   * Marks a tile as holding liquid so it is updated on the next tick.
   * Called by {@code Level.setLiquid} and by every flow.
   */
  public void join(int x, int y) {
    Chunk chunk = level.getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk != null) {
      chunk.liquidPanic = true;
    }
    addActive(x, y);
  }

  public void activateChunk(Chunk chunk) {
    chunk.liquidPanic = true;
    int startX = chunk.chunkPos.x() * ChunkPos.SIZE;
    int startY = chunk.chunkPos.y() * ChunkPos.SIZE;
    for (int localY = 0; localY < ChunkPos.SIZE; localY++) {
      for (int localX = 0; localX < ChunkPos.SIZE; localX++) {
        int x = startX + localX;
        int y = startY + localY;
        if (chunk.getLiquidLevel(x, y) > 0) {
          addActive(x, y);
        }
      }
    }
  }

  private void addActive(int x, int y) {
    if (activeCount == active.length) {
      active = Arrays.copyOf(active, active.length * 2);
    }
    // Flip the sign bit so Arrays.sort(long[]) orders signed X coordinates
    // numerically, including rows that cross x = 0.
    active[activeCount++] = ((long) y << 32)
        | ((x ^ Integer.MIN_VALUE) & 0xFFFFFFFFL);
  }

  /** Removes all active cells of the given chunk (call when it unloads). */
  public void delChunk(ChunkPos pos) {
    int minX = pos.x() * ChunkPos.SIZE;
    int minY = pos.y() * ChunkPos.SIZE;
    int maxX = minX + ChunkPos.SIZE;
    int maxY = minY + ChunkPos.SIZE;
    int n = 0;
    for (int i = 0; i < activeCount; i++) {
      long key = active[i];
      int x = (int) key ^ Integer.MIN_VALUE;
      int y = (int) (key >>> 32);
      if (x < minX || x >= maxX || y < minY || y >= maxY) {
        active[n++] = key;
      }
    }
    activeCount = n;
  }

  private void tickOnce() {
    collectSimulatedChunks();
    if (simulatedChunks.isEmpty()) {
      return;
    }
    // sort + dedupe the pending cells (join() may have added duplicates)
    Arrays.sort(active, 0, activeCount);
    int n = 0;
    for (int i = 0; i < activeCount; i++) {
      if (n == 0 || active[i] != active[n - 1]) {
        active[n++] = active[i];
      }
    }
    activeCount = n;
    updateCells();
    findInteractions();
  }

  private void collectSimulatedChunks() {
    simulatedChunks.clear();
    for (int i = 0; i < activeCount; i++) {
      int x = (int) active[i] ^ Integer.MIN_VALUE;
      int y = (int) (active[i] >>> 32);
      long chunkKey = ChunkPos.packBlockPosAsLong(x, y);
      Chunk chunk = level.getChunkByKey(chunkKey);
      if (chunk != null && chunk.liquidPanic) {
        simulatedChunks.add(chunkKey);
      }
    }
    for (var iterator = simulatedChunks.iterator(); iterator.hasNext();) {
      Chunk chunk = level.getChunkByKey(iterator.nextLong());
      if (chunk != null) {
        // Any actual write during this pass sets the flag again. If no write
        // occurs, the chunk stays asleep until liquid enters or is placed.
        chunk.liquidPanic = false;
      }
    }
  }

  /** The cellular automaton step: fall, then equalize sideways. */
  private void updateCells() {
    boolean leftFirst = random.nextBoolean();
    boolean sweepReverseX = reverseX;
    reverseX = !reverseX;
    // bottom-up, like Starbound (Y-up world: lowest y first); the count is
    // fixed so cells joined mid-tick are processed on the next tick
    int count = activeCount;
    int rowStart = 0;
    while (rowStart < count) {
      int rowY = (int) (active[rowStart] >>> 32);
      int rowEnd = rowStart + 1;
      while (rowEnd < count && (int) (active[rowEnd] >>> 32) == rowY) {
        rowEnd++;
      }
      if (sweepReverseX) {
        for (int i = rowEnd - 1; i >= rowStart; i--) {
          updateCell(i, leftFirst);
        }
      } else {
        for (int i = rowStart; i < rowEnd; i++) {
          updateCell(i, leftFirst);
        }
      }
      rowStart = rowEnd;
    }
  }

  private void updateCell(int i, boolean leftFirst) {
    Cell self = cellAt(i);
    if (self == null || !simulatedChunks.contains(self.chunk.chunkPos.asLong())
        || self.type == 0 || self.level <= 0) {
      return;
    }

    // 1) fall: drop into the tile below (Y-up: y - 1), filling it up
    Cell below = cellOf(self.x, self.y - 1, cellB);
    if (below != null) {
      transferLevel(self, self.x, self.y - 1, Math.min(self.level, FULL - below.level));
    }

    // 2) equalize: move half the level difference into a lower neighbour
    if (leftFirst) {
      equalizeSide(self, self.x - 1, self.y);
      equalizeSide(self, self.x + 1, self.y);
    } else {
      equalizeSide(self, self.x + 1, self.y);
      equalizeSide(self, self.x - 1, self.y);
    }

  }

  private void equalizeSide(Cell self, int nx, int ny) {
    Cell dst = cellOf(nx, ny, cellB);
    if (dst == null) {
      return;
    }
    int flow = (self.level - dst.level) / 2;
    if (flow <= 0) {
      // a single unit cannot split further: it evaporates instead
      if (self.level == MINIMUM_LIQUID_LEVEL) {
        self.chunk.setLiquidLevel(self.x, self.y, 0);
      }
      return;
    }
    transferLevel(self, nx, ny, flow);
  }

  /**
   * Different liquids touching each other: lava reacts against water (→
   * stone); otherwise the thinner liquid converts into the thicker one, so
   * different liquids never just sit layered next to each other.
   */
  private void findInteractions() {
    int count = activeCount; // the snapshot, as in updateCells
    for (int i = count - 1; i >= 0; i--) {
      Cell self = cellAt(i);
      if (self == null || !simulatedChunks.contains(self.chunk.chunkPos.asLong())
          || self.type == 0 || self.level <= 0) {
        continue;
      }
      interactSide(self, self.x - 1, self.y);
      interactSide(self, self.x + 1, self.y);
      interactSide(self, self.x, self.y - 1);
      interactSide(self, self.x, self.y + 1);
    }
  }

  private void interactSide(Cell self, int nx, int ny) {
    Cell dst = cellOf(nx, ny, cellB);
    if (dst == null || dst.type == 0 || dst.type == self.type) {
      return;
    }

    boolean selfLava = self.type == Liquids.LAVA.id();
    if (selfLava || dst.type == Liquids.LAVA.id()) {
      // the reaction fires from the lava tile's perspective and is
      // idempotent: it either solidifies (clearing the tiles) or no-ops
      Liquids.LAVA.onTouch(Liquid.CATEGORY.emptyStack(), Liquid.CATEGORY.emptyStack(), level,
          selfLava ? self.x : nx, selfLava ? self.y : ny, nx, ny);
      dst = cellOf(nx, ny, cellB);
      if (dst == null || dst.type == 0) {
        return;
      }
      self = cellOf(self.x, self.y, cellA);
      if (self == null || self.type == 0) {
        return;
      }
      if (dst.type == self.type) {
        return;
      }
    }

    // conversion: the thinner liquid becomes the thicker one
    if (self.level > dst.level) {
      dst.chunk.setLiquidType(nx, ny, self.type);
      join(nx, ny);
    } else {
      self.chunk.setLiquidType(self.x, self.y, dst.type);
      join(self.x, self.y);
    }
  }

  // -- transfers ------------------------------------------------------------

  /**
   * Moves liquid from the source cell into the tile at {@code (dx, dy)},
   * re-joining both tiles for the next tick.
   */
  private void transferLevel(Cell src, int dx, int dy, int amount) {
    if (amount <= 0 || src.level <= 0) {
      return;
    }
    Cell dst = cellOf(dx, dy, cellB);
    if (dst == null) {
      return;
    }
    if (dst.type != 0 && dst.type != src.type) {
      return;
    }
    amount = Math.min(amount, src.level);
    src.level -= amount;
    dst.level += amount;
    src.chunk.setLiquidLevel(src.x, src.y, src.level);
    dst.chunk.setLiquidLevel(dx, dy, dst.level);
    dst.chunk.setLiquidType(dx, dy, src.type);
    join(src.x, src.y);
    join(dx, dy);
  }

  // -- cells ----------------------------------------------------------------

  private @Nullable Cell cellAt(int i) {
    return cellOf((int) active[i] ^ Integer.MIN_VALUE,
        (int) (active[i] >>> 32), cellA);
  }

  /**
   * Fills {@code out} with the tile's data, or {@code null} when the tile
   * is impassable (solid block or unloaded chunk).
   */
  private @Nullable Cell cellOf(int wx, int wy, Cell out) {
    Chunk chunk = level.getChunkByKey(ChunkPos.packBlockPosAsLong(wx, wy));
    if (chunk == null || chunk.getEffectiveShape(wx, wy) == Shape.SOLID) {
      return null;
    }
    out.chunk = chunk;
    out.x = wx;
    out.y = wy;
    out.level = chunk.getLiquidLevel(wx, wy);
    out.type = chunk.getLiquidType(wx, wy);
    return out;
  }

  @SuppressWarnings("all")
  private static final class Cell {
    Chunk chunk; // Nonnull
    int x;
    int y;
    int level;
    byte type;
  }
}
