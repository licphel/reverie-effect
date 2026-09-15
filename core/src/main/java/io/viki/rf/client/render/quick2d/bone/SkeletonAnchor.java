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

package io.viki.rf.client.render.quick2d.bone;

import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;

/** Immutable and thread-safe normalized anchor within setup-pose bounds. */
public record SkeletonAnchor(float normalizedX, float normalizedY) {
  public static final SkeletonAnchor BOTTOM_LEFT = new SkeletonAnchor(0.0F, 0.0F);
  public static final SkeletonAnchor FEET_CENTER = new SkeletonAnchor(0.5F, 0.0F);
  public static final SkeletonAnchor CENTER = new SkeletonAnchor(0.5F, 0.5F);

  public SkeletonAnchor {
    if (!Float.isFinite(normalizedX) || !Float.isFinite(normalizedY)
        || normalizedX < 0.0F || normalizedX > 1.0F
        || normalizedY < 0.0F || normalizedY > 1.0F) {
      throw new IllegalArgumentException(
          "Skeleton anchor coordinates must be finite values in [0, 1]: "
              + normalizedX + ", " + normalizedY);
    }
  }

  public Vector2 resolve(Rectangle bounds) {
    return new Vector2(
        bounds.minX() + bounds.width() * normalizedX,
        bounds.minY() + bounds.height() * normalizedY);
  }
}
