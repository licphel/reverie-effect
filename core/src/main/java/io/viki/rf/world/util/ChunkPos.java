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

/**
 * Immutable integer position identifying a chunk in the world grid.
 *
 * <p>A chunk spans {@code [x * SIZE, (x+1) * SIZE) × [y * SIZE,
 * (y+1) * SIZE)} in block coordinates. The default chunk size is
 * {@value #SIZE} blocks per edge.
 *
 * @param x the X chunk coordinate
 * @param y the Y chunk coordinate
 * @see BlockPos
 * @see PrecisePos
 */
public record ChunkPos(int x, int y) {
  /** Default number of blocks along each chunk edge. */
  public static final int SIZE = 16;
  /** Chunk at the origin. */
  public static final ChunkPos ZERO = new ChunkPos(0, 0);

  /**
   * Unpacks a {@code long} produced by {@link #asLong()}.
   *
   * @param packed the packed position
   * @return the chunk position
   */
  public static ChunkPos fromLong(long packed) {
    return new ChunkPos((int) (packed >> 32), (int) packed);
  }

  /**
   * Packs a block position into the chunk map key of its containing chunk,
   * without allocating a {@link ChunkPos} or {@link BlockPos}.
   *
   * @param x the block X coordinate
   * @param y the block Y coordinate
   * @return the same key as {@code new ChunkPos(floorDiv(x, SIZE), floorDiv(y, SIZE)).asLong()}
   */
  public static long packBlockPosAsLong(int x, int y) {
    return ((long) Math.floorDiv(x, SIZE) << 32) | (Math.floorDiv(y, SIZE) & 0xFFFFFFFFL);
  }

  /**
   * Returns an offset chunk position.
   *
   * @param dx the X offset in chunks
   * @param dy the Y offset in chunks
   * @return {@code (x + dx, y + dy)}
   */
  public ChunkPos offset(int dx, int dy) {
    return new ChunkPos(x + dx, y + dy);
  }

  /**
   * Returns the minimum block position in this chunk.
   *
   * @return the block at the chunk's minimum corner
   */
  public BlockPos min() {
    return new BlockPos(x * SIZE, y * SIZE);
  }

  /**
   * Returns the maximum inclusive block position in this chunk.
   *
   * @return the block at the chunk's far corner (exclusive bound minus one)
   */
  public BlockPos max() {
    return new BlockPos((x + 1) * SIZE - 1, (y + 1) * SIZE - 1);
  }

  /**
   * Returns the block position at the given local coordinates within this
   * chunk.
   *
   * @param localX the X offset within the chunk (Take the modulo of {@code SIZE})
   * @param localY the Y offset within the chunk (Take the modulo of {@code SIZE})
   * @return the world block position
   */
  public BlockPos blockAt(int localX, int localY) {
    return new BlockPos(x * SIZE + localX % SIZE, y * SIZE + localY % SIZE);
  }

  /**
   * Returns whether the given block position lies within this chunk.
   *
   * @param loc the position to test
   * @return {@code true} if the block is inside this chunk
   */
  public boolean contains(Locatable loc) {
    int minX = x * SIZE;
    int minY = y * SIZE;
    return loc.getX() >= minX && loc.getX() < minX + SIZE
        && loc.getY() >= minY && loc.getY() < minY + SIZE;
  }

  /**
   * Returns the squared Euclidean distance to another chunk position.
   *
   * @param o the other chunk position
   * @return {@code dx² + dy²}
   */
  public int distanceSquared(ChunkPos o) {
    int dx = x - o.x;
    int dy = y - o.y;
    return dx * dx + dy * dy;
  }

  /**
   * Packs this chunk position into a {@code long}. The upper 32 bits hold
   * {@code x}, the lower 32 bits hold {@code y}.
   *
   * @return the packed position
   */
  public long asLong() {
    return ((long) x << 32) | (y & 0xFFFFFFFFL);
  }
}
