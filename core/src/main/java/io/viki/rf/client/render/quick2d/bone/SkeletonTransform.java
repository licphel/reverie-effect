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
import io.viki.momentum.math.Vector2;

import java.util.Objects;

/** Immutable and thread-safe world transform for a skeleton anchor. */
public record SkeletonTransform(
    float x, float y, float rotation, float scaleX, float scaleY,
    boolean mirrorX, Vector2 offset) {
  public SkeletonTransform(
      float x, float y, float rotation, float scaleX, float scaleY, boolean mirrorX) {
    this(x, y, rotation, scaleX, scaleY, mirrorX, Vector2.ZERO);
  }

  public SkeletonTransform {
    Objects.requireNonNull(offset, "offset");
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(rotation)
        || !Float.isFinite(scaleX) || !Float.isFinite(scaleY)
        || !Float.isFinite(offset.x()) || !Float.isFinite(offset.y())
        || scaleX <= 0.0F || scaleY <= 0.0F) {
      throw new IllegalArgumentException(
          "Skeleton transform requires finite values and positive scales: "
              + x + ", " + y + ", " + rotation + ", " + scaleX + ", " + scaleY
              + ", offset=" + offset);
    }
  }

  public static SkeletonTransform at(float x, float y, boolean mirrorX) {
    return new SkeletonTransform(x, y, 0.0F, 1.0F, 1.0F, mirrorX);
  }

  public SkeletonTransform withRotation(float rotation) {
    return new SkeletonTransform(x, y, rotation, scaleX, scaleY, mirrorX, offset);
  }

  public SkeletonTransform withScale(float scale) {
    return withScale(scale, scale);
  }

  public SkeletonTransform withScale(float scaleX, float scaleY) {
    return new SkeletonTransform(x, y, rotation, scaleX, scaleY, mirrorX, offset);
  }

  public SkeletonTransform withOffset(Vector2 offset) {
    return new SkeletonTransform(x, y, rotation, scaleX, scaleY, mirrorX, offset);
  }

  Matrix3x2 matrix(Vector2 localAnchor) {
    float actualScaleX = mirrorX ? -scaleX : scaleX;
    float sin = (float) Math.sin(rotation);
    float cos = (float) Math.cos(rotation);
    float m00 = actualScaleX * cos;
    float m10 = actualScaleX * sin;
    float m01 = -scaleY * sin;
    float m11 = scaleY * cos;
    float tx = x + offset.x() - m00 * localAnchor.x() - m01 * localAnchor.y();
    float ty = y + offset.y() - m10 * localAnchor.x() - m11 * localAnchor.y();
    return new Matrix3x2(m00, m10, m01, m11, tx, ty);
  }
}
