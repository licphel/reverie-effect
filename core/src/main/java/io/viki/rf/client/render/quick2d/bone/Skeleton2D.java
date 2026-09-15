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

import java.util.Objects;

/** Mutable pose instance backed by an immutable skeleton definition. Not thread-safe. */
public final class Skeleton2D {
  private final SkeletonDefinition definition;
  private final Bone[] bones;
  private final Slot[] slots;
  private final Matrix3x2[] worldTransforms;
  private boolean poseDirty = true;

  private Skeleton2D(SkeletonDefinition definition) {
    this.definition = definition;
    bones = new Bone[definition.bones().size()];
    slots = new Slot[definition.slots().size()];
    worldTransforms = new Matrix3x2[bones.length];
    for (int i = 0; i < bones.length; i++) {
      bones[i] = new Bone(this, i, definition.bones().get(i));
      worldTransforms[i] = Matrix3x2.IDENTITY;
    }
    for (int i = 0; i < slots.length; i++) {
      slots[i] = new Slot(i, definition.slots().get(i));
    }
  }

  public static Skeleton2D create(SkeletonDefinition definition) {
    return new Skeleton2D(Objects.requireNonNull(definition, "definition"));
  }

  public SkeletonDefinition definition() {
    return definition;
  }

  public int boneCount() {
    return bones.length;
  }

  public Bone bone(int index) {
    if (index < 0 || index >= bones.length) {
      throw new IndexOutOfBoundsException("Bone index out of range: " + index);
    }
    return bones[index];
  }

  public Bone bone(String name) {
    return bones[definition.boneIndex(name)];
  }

  public int slotCount() {
    return slots.length;
  }

  public Slot slot(int index) {
    if (index < 0 || index >= slots.length) {
      throw new IndexOutOfBoundsException("Slot index out of range: " + index);
    }
    return slots[index];
  }

  public Slot slot(String name) {
    return slots[definition.slotIndex(name)];
  }

  public void resetToSetupPose() {
    for (Bone bone : bones) {
      bone.resetToSetupPose();
    }
    for (Slot slot : slots) {
      slot.resetToSetupPose();
    }
  }

  public void updateWorldTransforms() {
    if (!poseDirty) {
      return;
    }
    for (int i = 0; i < bones.length; i++) {
      Matrix3x2 local = bones[i].localMatrix();
      int parentIndex = definition.bones().get(i).parentIndex();
      worldTransforms[i] = parentIndex < 0
          ? local
          : worldTransforms[parentIndex].multiply(local);
    }
    poseDirty = false;
  }

  void markPoseDirty() {
    poseDirty = true;
  }

  Matrix3x2 worldTransform(int boneIndex) {
    return worldTransforms[boneIndex];
  }
}
