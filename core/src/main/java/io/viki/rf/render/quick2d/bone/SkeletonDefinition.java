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

import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Matrix3x2;
import io.viki.momentum.math.Vector2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable and thread-safe setup pose, attachments, and draw order. */
public final class SkeletonDefinition {
  private final List<BoneDefinition> bones;
  private final List<SlotDefinition> slots;
  private final Map<String, Integer> boneIndices;
  private final Map<String, Integer> slotIndices;
  private final Rectangle setupBounds;
  private final Vector2 localAnchor;

  private SkeletonDefinition(Builder builder) {
    bones = List.copyOf(builder.bones);
    slots = List.copyOf(builder.slots);
    boneIndices = Map.copyOf(builder.boneIndices);
    slotIndices = Map.copyOf(builder.slotIndices);
    setupBounds = calculateSetupBounds(bones, slots);
    localAnchor = builder.anchor.resolve(setupBounds);
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<BoneDefinition> bones() {
    return bones;
  }

  public List<SlotDefinition> slots() {
    return slots;
  }

  public Rectangle setupBounds() {
    return setupBounds;
  }

  public Vector2 localAnchor() {
    return localAnchor;
  }

  public int boneIndex(String name) {
    Integer index = boneIndices.get(name);
    if (index == null) {
      throw new IllegalArgumentException("Unknown bone: " + name);
    }
    return index;
  }

  public int slotIndex(String name) {
    Integer index = slotIndices.get(name);
    if (index == null) {
      throw new IllegalArgumentException("Unknown slot: " + name);
    }
    return index;
  }

  private static Rectangle calculateSetupBounds(
      List<BoneDefinition> bones, List<SlotDefinition> slots) {
    if (slots.isEmpty()) {
      return Rectangle.ZERO;
    }

    Matrix3x2[] world = new Matrix3x2[bones.size()];
    for (int i = 0; i < bones.size(); i++) {
      BoneDefinition bone = bones.get(i);
      Matrix3x2 local = bone.setup().matrix();
      world[i] = bone.parentIndex() < 0
          ? local
          : world[bone.parentIndex()].multiply(local);
    }

    float[] bounds = {
        Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
    };
    for (SlotDefinition slot : slots) {
      RegionAttachment attachment = slot.attachment();
      Matrix3x2 matrix = world[slot.boneIndex()].multiply(attachment.localMatrix());
      include(bounds, matrix, 0.0F, 0.0F);
      include(bounds, matrix, attachment.width(), 0.0F);
      include(bounds, matrix, attachment.width(), attachment.height());
      include(bounds, matrix, 0.0F, attachment.height());
    }
    return new Rectangle(bounds[0], bounds[1], bounds[2], bounds[3]);
  }

  private static void include(float[] bounds, Matrix3x2 matrix, float x, float y) {
    float transformedX = matrix.m00() * x + matrix.m01() * y + matrix.m02();
    float transformedY = matrix.m10() * x + matrix.m11() * y + matrix.m12();
    bounds[0] = Math.min(bounds[0], transformedX);
    bounds[1] = Math.min(bounds[1], transformedY);
    bounds[2] = Math.max(bounds[2], transformedX);
    bounds[3] = Math.max(bounds[3], transformedY);
  }

  public record BoneDefinition(String name, int parentIndex, Transform2D setup) {
    public BoneDefinition {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(setup, "setup");
      if (name.isBlank()) {
        throw new IllegalArgumentException("Bone name must not be blank");
      }
      if (parentIndex < -1) {
        throw new IllegalArgumentException("Invalid parent index for bone " + name + ": " + parentIndex);
      }
    }
  }

  public record SlotDefinition(String name, int boneIndex, RegionAttachment attachment) {
    public SlotDefinition {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(attachment, "attachment");
      if (name.isBlank()) {
        throw new IllegalArgumentException("Slot name must not be blank");
      }
      if (boneIndex < 0) {
        throw new IllegalArgumentException("Invalid bone index for slot " + name + ": " + boneIndex);
      }
    }
  }

  /** Fluent setup-pose builder. Not thread-safe. */
  public static final class Builder {
    private final List<BoneDefinition> bones = new ArrayList<>();
    private final List<SlotDefinition> slots = new ArrayList<>();
    private final Map<String, Integer> boneIndices = new LinkedHashMap<>();
    private final Map<String, Integer> slotIndices = new LinkedHashMap<>();
    private SkeletonAnchor anchor = SkeletonAnchor.CENTER;

    public Builder anchor(SkeletonAnchor anchor) {
      this.anchor = Objects.requireNonNull(anchor, "anchor");
      return this;
    }

    public Builder root(String name) {
      return root(name, Transform2D.IDENTITY);
    }

    public Builder root(String name, Transform2D setup) {
      if (!bones.isEmpty()) {
        throw new IllegalStateException("The root bone must be configured first and only once");
      }
      addBone(name, -1, setup);
      return this;
    }

    public Builder bone(String name, String parentName, Transform2D setup) {
      if (bones.isEmpty()) {
        throw new IllegalStateException("Configure the root bone before child bones");
      }
      addBone(name, requireBone(parentName), setup);
      return this;
    }

    public Builder slot(String name, String boneName, RegionAttachment attachment) {
      requireName(name, "Slot");
      Objects.requireNonNull(attachment, "attachment");
      if (slotIndices.containsKey(name)) {
        throw new IllegalArgumentException("Duplicate slot name: " + name);
      }
      slotIndices.put(name, slots.size());
      slots.add(new SlotDefinition(name, requireBone(boneName), attachment));
      return this;
    }

    public SkeletonDefinition build() {
      if (bones.isEmpty()) {
        throw new IllegalStateException("A skeleton requires one root bone");
      }
      return new SkeletonDefinition(this);
    }

    private void addBone(String name, int parentIndex, Transform2D setup) {
      requireName(name, "Bone");
      Objects.requireNonNull(setup, "setup");
      if (boneIndices.containsKey(name)) {
        throw new IllegalArgumentException("Duplicate bone name: " + name);
      }
      int index = bones.size();
      bones.add(new BoneDefinition(name, parentIndex, setup));
      boneIndices.put(name, index);
    }

    private int requireBone(String name) {
      Objects.requireNonNull(name, "boneName");
      Integer index = boneIndices.get(name);
      if (index == null) {
        throw new IllegalArgumentException("Bone must be defined before use: " + name);
      }
      return index;
    }

    private static void requireName(String name, String kind) {
      Objects.requireNonNull(name, "name");
      if (name.isBlank()) {
        throw new IllegalArgumentException(kind + " name must not be blank");
      }
    }
  }
}
