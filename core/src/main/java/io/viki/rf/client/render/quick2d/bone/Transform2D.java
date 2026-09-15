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

import io.viki.momentum.math.Matrix3x2;

/** Immutable and thread-safe local translation, rotation, and scale. */
public record Transform2D(float x, float y, float rotation, float scaleX, float scaleY) {
  public static final Transform2D IDENTITY = new Transform2D(0.0F, 0.0F, 0.0F, 1.0F, 1.0F);

  public Transform2D {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(rotation)
        || !Float.isFinite(scaleX) || !Float.isFinite(scaleY)) {
      throw new IllegalArgumentException("Transform values must be finite: "
          + x + ", " + y + ", " + rotation + ", " + scaleX + ", " + scaleY);
    }
  }

  public static Transform2D at(float x, float y) {
    return new Transform2D(x, y, 0.0F, 1.0F, 1.0F);
  }

  public static Transform2D at(float x, float y, float rotation) {
    return new Transform2D(x, y, rotation, 1.0F, 1.0F);
  }

  public Matrix3x2 matrix() {
    float sin = (float) Math.sin(rotation);
    float cos = (float) Math.cos(rotation);
    return new Matrix3x2(
        scaleX * cos, scaleX * sin,
        -scaleY * sin, scaleY * cos,
        x, y);
  }
}
