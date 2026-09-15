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

package io.viki.rf.world.light;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A mutable descriptor of a directional beam light, passed between the light engine and
 * its renderer.
 *
 * <p>Instances come from a thread-local pool via
 * {@link #of(float, float, float, float, float, float, float, float, float, float)}
 * to avoid allocation in the light simulation;
 * return them with {@link #recycle()} once the surrounding computation is done.
 */
public final class Beam {
  private static final ConcurrentLinkedQueue<Beam> POOL = new ConcurrentLinkedQueue<>();
  /** The X coordinate of the light source. */
  public float x;
  /** The Y coordinate of the light source. */
  public float y;
  /** The red intensity of the light. */
  public float r;
  /** The green intensity of the light. */
  public float g;
  /** The blue intensity of the light. */
  public float b;
  /** The beam direction in radians from the positive X-axis. */
  public float direction;
  /** The half-angle of the beam cone in radians. */
  public float halfAngle;
  /** Edge softness; a value below {@code 1} flags this light as a beam. */
  public float ambience;
  /** The maximum reach of the beam in tiles. */
  public float range;
  /** The brightness multiplier of the beam at its origin. */
  public float strength;

  private Beam() {
  }

  /**
   * Creates a pooled beam light with the given parameters.
   *
   * @param x         the emitter X coordinate
   * @param y         the emitter Y coordinate
   * @param r         the red intensity
   * @param g         the green intensity
   * @param b         the blue intensity
   * @param direction the beam direction in radians from the positive X-axis
   * @param halfAngle the half-angle of the beam cone in radians
   * @param ambience  the edge softness; must be below {@code 1} for a beam
   * @param range     the maximum reach in tiles
   * @param strength  the brightness multiplier at the origin
   * @return a pooled light
   */
  public static Beam of(float x, float y, float r, float g, float b, float direction, float halfAngle,
                        float ambience, float range, float strength) {
    Beam src = POOL.poll();
    if (src == null) {
      src = new Beam();
    }
    return src.set(x, y, r, g, b, direction, halfAngle, ambience, range, strength);
  }

  /**
   * Returns this light to the pool for reuse.
   *
   * <p>Must be called once the surrounding computation is done; the light must not be
   * used afterward.
   */
  void recycle() {
    POOL.offer(this);
  }

  /**
   * Fills this light as a directional beam emitted from a tile position.
   *
   * @param x         the emitter X coordinate
   * @param y         the emitter Y coordinate
   * @param r         the red intensity
   * @param g         the green intensity
   * @param b         the blue intensity
   * @param direction the beam direction in radians from the positive X-axis
   * @param halfAngle the half-angle of the beam cone in radians
   * @param ambience  the edge softness; must be below {@code 1} for a beam
   * @param range     the maximum reach in tiles
   * @param strength  the brightness multiplier at the origin
   * @return itself for chaining
   */
  Beam set(float x, float y, float r, float g, float b, float direction, float halfAngle,
                  float ambience, float range, float strength) {
    this.x = x;
    this.y = y;
    this.r = r;
    this.g = g;
    this.b = b;
    this.direction = direction;
    this.halfAngle = halfAngle;
    this.ambience = ambience;
    this.range = range;
    this.strength = strength;
    return this;
  }
}
