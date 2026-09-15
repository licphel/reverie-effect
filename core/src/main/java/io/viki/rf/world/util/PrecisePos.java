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

import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.Direction2D;

/**
 * Immutable double-precision world position.
 *
 * <p>Stored as {@code double} for precision at world scale; float accessors
 * {@link #xf()} / {@link #yf()} are provided for rendering and GPU upload.
 *
 * @param x the X coordinate
 * @param y the Y coordinate
 * @see BlockPos
 * @see ChunkPos
 */
public record PrecisePos(double x, double y) implements Locatable {
  /** Origin. */
  public static final PrecisePos ZERO = new PrecisePos(0.0, 0.0);

  /**
   * Linearly interpolates between two positions.
   *
   * @param a the start position
   * @param b the end position
   * @param t the interpolation factor ({@code 0.0} = a, {@code 1.0} = b)
   * @return the interpolated position
   */
  public static PrecisePos lerp(PrecisePos a, PrecisePos b, double t) {
    double it = 1.0 - t;
    return new PrecisePos(a.x * it + b.x * t, a.y * it + b.y * t);
  }

  /**
   * Returns the X coordinate as a float.
   *
   * @return {@code (float) x}
   */
  public float xf() {
    return (float) x;
  }

  /**
   * Returns the Y coordinate as a float.
   *
   * @return {@code (float) y}
   */
  public float yf() {
    return (float) y;
  }

  /**
   * Returns the sum of this position and another.
   *
   * @param o the other position
   * @return {@code this + o}
   */
  public PrecisePos add(PrecisePos o) {
    return new PrecisePos(x + o.x, y + o.y);
  }

  /**
   * Returns the sum of this position and the given offsets.
   *
   * @param dx the X offset
   * @param dy the Y offset
   * @return {@code this + (dx, dy)}
   */
  public PrecisePos add(double dx, double dy) {
    return new PrecisePos(x + dx, y + dy);
  }

  /**
   * Returns the position offset by one block in the given direction.
   *
   * @param dir the direction to offset
   * @return {@code (x + dir.dx, y + dir.dy)}
   */
  public PrecisePos offset(Direction2D dir) {
    return new PrecisePos(x + dir.offset[0], y + dir.offset[1]);
  }

  /**
   * Returns the position offset by {@code n} blocks in the given
   * direction.
   *
   * @param dir the direction to offset
   * @param n   the distance in blocks
   * @return {@code (x + dir.dx * n, y + dir.dy * n)}
   */
  public PrecisePos offset(Direction2D dir, double n) {
    return new PrecisePos(x + dir.offset[0] * n, y + dir.offset[1] * n);
  }

  /**
   * Returns the difference of this position and another.
   *
   * @param o the other position
   * @return {@code this - o}
   */
  public PrecisePos subtract(PrecisePos o) {
    return new PrecisePos(x - o.x, y - o.y);
  }

  /**
   * Returns this position scaled by a scalar.
   *
   * @param s the scaling factor
   * @return {@code this * s}
   */
  public PrecisePos multiply(double s) {
    return new PrecisePos(x * s, y * s);
  }

  /**
   * Returns this position divided by a scalar.
   *
   * @param s the divisor; must be non-zero
   * @return {@code this / s}
   */
  public PrecisePos divide(double s) {
    return new PrecisePos(x / s, y / s);
  }

  /**
   * Returns the squared Euclidean distance to another position.
   *
   * @param o the other position
   * @return {@code dx² + dy²}
   */
  public double distanceSquared(PrecisePos o) {
    double dx = x - o.x;
    double dy = y - o.y;
    return dx * dx + dy * dy;
  }

  /**
   * Returns the Euclidean distance to another position.
   *
   * @param o the other position
   * @return the distance
   */
  public double distance(PrecisePos o) {
    return Math.sqrt(distanceSquared(o));
  }

  /**
   * Converts to the nearest integer block position (floor of each component).
   *
   * @return the block position containing this world position
   */
  public BlockPos toBlockPos() {
    return new BlockPos((int) Math.floor(x), (int) Math.floor(y));
  }

  /**
   * Converts to a chunk position.
   *
   * @return the chunk containing this world position
   */
  public ChunkPos toChunkPos() {
    return toBlockPos().toChunkPos();
  }

  /**
   * Converts to a {@link Vector2} for use with the rendering system.
   *
   * @return a float vector
   */
  public Vector2 toVector2() {
    return new Vector2(xf(), yf());
  }

  @Override
  public double getX() {
    return x;
  }

  @Override
  public double getY() {
    return y;
  }
}
