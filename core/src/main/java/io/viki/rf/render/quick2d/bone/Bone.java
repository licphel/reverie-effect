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

import io.viki.momentum.math.Matrix3x2;

/** Mutable runtime pose of one bone. Not thread-safe. */
public final class Bone {
  private final Skeleton2D skeleton;
  private final int index;
  private final SkeletonDefinition.BoneDefinition definition;
  private float x;
  private float y;
  private float rotation;
  private float scaleX;
  private float scaleY;

  Bone(
      Skeleton2D skeleton, int index, SkeletonDefinition.BoneDefinition definition) {
    this.skeleton = skeleton;
    this.index = index;
    this.definition = definition;
    resetToSetupPose();
  }

  public String name() {
    return definition.name();
  }

  public int index() {
    return index;
  }

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public float rotation() {
    return rotation;
  }

  public float scaleX() {
    return scaleX;
  }

  public float scaleY() {
    return scaleY;
  }

  public Bone position(float x, float y) {
    requireFinite(x, "x");
    requireFinite(y, "y");
    if (this.x != x || this.y != y) {
      this.x = x;
      this.y = y;
      skeleton.markPoseDirty();
    }
    return this;
  }

  public Bone rotation(float rotation) {
    requireFinite(rotation, "rotation");
    if (this.rotation != rotation) {
      this.rotation = rotation;
      skeleton.markPoseDirty();
    }
    return this;
  }

  public Bone scale(float scale) {
    return scale(scale, scale);
  }

  public Bone scale(float scaleX, float scaleY) {
    requireFinite(scaleX, "scaleX");
    requireFinite(scaleY, "scaleY");
    if (this.scaleX != scaleX || this.scaleY != scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      skeleton.markPoseDirty();
    }
    return this;
  }

  public Bone resetToSetupPose() {
    Transform2D setup = definition.setup();
    x = setup.x();
    y = setup.y();
    rotation = setup.rotation();
    scaleX = setup.scaleX();
    scaleY = setup.scaleY();
    skeleton.markPoseDirty();
    return this;
  }

  Matrix3x2 localMatrix() {
    float sin = (float) Math.sin(rotation);
    float cos = (float) Math.cos(rotation);
    return new Matrix3x2(
        scaleX * cos, scaleX * sin,
        -scaleY * sin, scaleY * cos,
        x, y);
  }

  private static void requireFinite(float value, String field) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException("Bone " + field + " must be finite: " + value);
    }
  }
}
