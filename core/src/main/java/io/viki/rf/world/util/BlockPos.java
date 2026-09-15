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

package io.viki.rf.world.util;

import io.viki.momentum.math.Direction2D;

/**
 * Immutable integer position in the block grid.
 *
 * <p>A block occupies the unit square {@code [x, x+1) × [y, y+1)} in world
 * space. Use {@link #toCenter()} to obtain the center of the block.
 *
 * @param x the X block coordinate
 * @param y the Y block coordinate
 * @see PrecisePos
 * @see ChunkPos
 */
public record BlockPos(int x, int y) implements Locatable {
  /** Origin block. */
  public static final BlockPos ZERO = new BlockPos(0, 0);

  /**
   * Unpacks a {@code long} produced by {@link #asLong()}.
   *
   * @param packed the packed position
   * @return the block position
   */
  public static BlockPos fromLong(long packed) {
    return new BlockPos((int) (packed >> 32), (int) packed);
  }

  /**
   * Returns an offset block position.
   *
   * @param dx the X offset in blocks
   * @param dy the Y offset in blocks
   * @return {@code (x + dx, y + dy)}
   */
  public BlockPos offset(int dx, int dy) {
    return new BlockPos(x + dx, y + dy);
  }

  /**
   * Returns the block offset by one step in the given direction.
   *
   * @param dir the direction to offset
   * @return {@code (x + dir.dx, y + dir.dy)}
   */
  public BlockPos offset(Direction2D dir) {
    return new BlockPos(x + dir.offset[0], y + dir.offset[1]);
  }

  /**
   * Returns the block offset by {@code n} steps in the given direction.
   *
   * @param dir the direction to offset
   * @param n   the number of steps
   * @return {@code (x + dir.dx * n, y + dir.dy * n)}
   */
  public BlockPos offset(Direction2D dir, int n) {
    return new BlockPos(x + dir.offset[0] * n, y + dir.offset[1] * n);
  }

  /**
   * Returns the block above this one ({@code +y}).
   *
   * @return {@code (x, y + 1)}
   */
  public BlockPos above() {
    return new BlockPos(x, y + 1);
  }

  /**
   * Returns the block above this one by the given distance.
   *
   * @param n the distance in blocks
   * @return {@code (x, y + n)}
   */
  public BlockPos above(int n) {
    return new BlockPos(x, y + n);
  }

  /**
   * Returns the block below this one ({@code -y}).
   *
   * @return {@code (x, y - 1)}
   */
  public BlockPos below() {
    return new BlockPos(x, y - 1);
  }

  /**
   * Returns the block below this one by the given distance.
   *
   * @param n the distance in blocks
   * @return {@code (x, y - n)}
   */
  public BlockPos below(int n) {
    return new BlockPos(x, y - n);
  }

  /**
   * Returns the block to the left ({@code -x}).
   *
   * @return {@code (x - 1, y)}
   */
  public BlockPos left() {
    return new BlockPos(x - 1, y);
  }

  /**
   * Returns the block to the left by the given distance.
   *
   * @param n the distance in blocks
   * @return {@code (x - n, y)}
   */
  public BlockPos left(int n) {
    return new BlockPos(x - n, y);
  }

  /**
   * Returns the block to the right ({@code +x}).
   *
   * @return {@code (x + 1, y)}
   */
  public BlockPos right() {
    return new BlockPos(x + 1, y);
  }

  /**
   * Returns the block to the right by the given distance.
   *
   * @param n the distance in blocks
   * @return {@code (x + n, y)}
   */
  public BlockPos right(int n) {
    return new BlockPos(x + n, y);
  }

  /**
   * Returns the world position at the center of this block.
   *
   * @return {@code (x + 0.5, y + 0.5)}
   */
  public PrecisePos toCenter() {
    return new PrecisePos(x + 0.5, y + 0.5);
  }

  /**
   * Returns the world position at the minimum corner of this block.
   *
   * @return {@code (x, y)}
   */
  public PrecisePos toCorner() {
    return new PrecisePos(x, y);
  }

  /**
   * Returns the chunk position containing this block.
   *
   * @return the chunk containing this block
   */
  public ChunkPos toChunkPos() {
    return toChunkPos(ChunkPos.SIZE);
  }

  /**
   * Returns the chunk position containing this block for the given chunk
   * size.
   *
   * <p>Uses floor division so that chunks straddling the origin are
   * positioned correctly for negative block coordinates.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return the chunk containing this block
   */
  public ChunkPos toChunkPos(int chunkSize) {
    return new ChunkPos(Math.floorDiv(x, chunkSize), Math.floorDiv(y, chunkSize));
  }

  /**
   * Returns this block's X coordinate within its chunk.
   *
   * @return {@code x mod SIZE}, always in {@code [0, SIZE)}
   */
  public int chunkLocalX() {
    return chunkLocalX(ChunkPos.SIZE);
  }

  /**
   * Returns this block's X coordinate within its chunk.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return {@code x mod chunkSize}, always in {@code [0, chunkSize)}
   */
  public int chunkLocalX(int chunkSize) {
    return Math.floorMod(x, chunkSize);
  }

  /**
   * Returns this block's Y coordinate within its chunk.
   *
   * @return {@code y mod SIZE}, always in {@code [0, SIZE)}
   */
  public int chunkLocalY() {
    return chunkLocalY(ChunkPos.SIZE);
  }

  /**
   * Returns this block's Y coordinate within its chunk.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return {@code y mod chunkSize}, always in {@code [0, chunkSize)}
   */
  public int chunkLocalY(int chunkSize) {
    return Math.floorMod(y, chunkSize);
  }

  /**
   * Packs this position into a {@code long}. The upper 32 bits hold
   * {@code x}, the lower 32 bits hold {@code y}.
   *
   * @return the packed position
   */
  public long asLong() {
    return ((long) x << 32) | (y & 0xFFFFFFFFL);
  }

  @Override
  public double getX() {
    return x + 0.5;
  }

  @Override
  public double getY() {
    return y + 0.5;
  }
}
