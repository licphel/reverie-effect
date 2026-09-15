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

package io.viki.rf.render.quick2d.particle;

import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.gfx.tint.Color;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base for a renderable particle.
 *
 * <p>Subclasses implement the rendering getters and manage their own
 * physics and lifecycle. Pass instances to
 * {@link ParticleSystem2D#spawn(Particle2D)}.
 */
public abstract class Particle2D {
  @Nullable TexturePart texture;

  /**
   * Returns the world-space X coordinate.
   *
   * @return the X position
   */
  public abstract float posX();

  /**
   * Returns the world-space Y coordinate.
   *
   * @return the Y position
   */
  public abstract float posY();

  /**
   * Returns the width in world units.
   *
   * @return the width
   */
  public abstract float width();

  /**
   * Returns the height in world units.
   *
   * @return the height
   */
  public abstract float height();

  /**
   * Returns the current RGBA color.
   *
   * @return the color
   */
  public abstract Color color();

  /**
   * Returns the rotation angle in radians.
   *
   * @return the rotation angle
   */
  public abstract float rotation();

  /**
   * Returns whether this particle should be drawn.
   *
   * <p>Dead particles are compacted out during {@link ParticleSystem2D#draw}.
   *
   * @return {@code true} if this particle should be rendered
   */
  public boolean alive() {
    return true;
  }

  /**
   * Sets the texture region and returns this particle for chaining.
   *
   * @param t the texture part
   * @return this particle
   */
  public Particle2D texture(TexturePart t) {
    this.texture = t;
    return this;
  }
}
