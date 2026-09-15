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

package io.viki.rf.render.quick2d.bone;

import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.math.Matrix3x2;

import java.util.Objects;

/** Immutable and thread-safe rigid texture attachment. */
public final class RegionAttachment {
  private final TexturePart texture;
  private final float width;
  private final float height;
  private final float pivotX;
  private final float pivotY;
  private final Transform2D transform;
  private final Matrix3x2 localMatrix;

  private RegionAttachment(Builder builder) {
    texture = builder.texture;
    width = builder.width;
    height = builder.height;
    pivotX = builder.pivotX;
    pivotY = builder.pivotY;
    transform = new Transform2D(
        builder.x, builder.y, builder.rotation, builder.scaleX, builder.scaleY);
    localMatrix = transform.matrix().multiply(Matrix3x2.createTranslation(
        -pivotX * width, -pivotY * height));
  }

  public static Builder builder(TexturePart texture) {
    return new Builder(texture);
  }

  public TexturePart texture() {
    return texture;
  }

  public float width() {
    return width;
  }

  public float height() {
    return height;
  }

  public float pivotX() {
    return pivotX;
  }

  public float pivotY() {
    return pivotY;
  }

  public Transform2D transform() {
    return transform;
  }

  Matrix3x2 localMatrix() {
    return localMatrix;
  }

  /** Fluent attachment configuration. Not thread-safe. */
  public static final class Builder {
    private final TexturePart texture;
    private float width = Float.NaN;
    private float height = Float.NaN;
    private float pivotX = SkeletonAnchor.CENTER.normalizedX();
    private float pivotY = SkeletonAnchor.CENTER.normalizedY();
    private float x;
    private float y;
    private float rotation;
    private float scaleX = 1.0F;
    private float scaleY = 1.0F;

    private Builder(TexturePart texture) {
      this.texture = Objects.requireNonNull(texture, "texture");
    }

    public Builder size(float width, float height) {
      if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
        throw new IllegalArgumentException(
            "Attachment size must contain positive finite values: " + width + " x " + height);
      }
      this.width = width;
      this.height = height;
      return this;
    }

    public Builder pivot(float normalizedX, float normalizedY) {
      if (!Float.isFinite(normalizedX) || !Float.isFinite(normalizedY)) {
        throw new IllegalArgumentException(
            "Attachment pivot must be finite: " + normalizedX + ", " + normalizedY);
      }
      pivotX = normalizedX;
      pivotY = normalizedY;
      return this;
    }

    public Builder pivot(SkeletonAnchor pivot) {
      Objects.requireNonNull(pivot, "pivot");
      return pivot(pivot.normalizedX(), pivot.normalizedY());
    }

    public Builder position(float x, float y) {
      this.x = x;
      this.y = y;
      return this;
    }

    public Builder rotation(float rotation) {
      this.rotation = rotation;
      return this;
    }

    public Builder scale(float scale) {
      return scale(scale, scale);
    }

    public Builder scale(float scaleX, float scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      return this;
    }

    public RegionAttachment build() {
      if (!Float.isFinite(width) || !Float.isFinite(height)) {
        throw new IllegalStateException("Attachment size must be configured before build");
      }
      return new RegionAttachment(this);
    }
  }
}
