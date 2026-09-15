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

package io.viki.rf.render.quick2d.sprite;

import io.viki.momentum.math.Matrix3x2;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A 2D skeletal hierarchy built from {@link Bone2D} nodes.
 *
 * <p>A skeleton holds the bind-pose bone structure and provides name-based
 * lookup for attaching sprites or computing attachment points. World-space
 * transforms for all bones can be computed in a single pass via
 * {@link #computeWorldTransforms()}.
 *
 * @see Bone2D
 */
public final class Skeleton2D {
  private final Bone2D root;
  private final Map<String, Bone2D> boneMap = new LinkedHashMap<>();

  /**
   * Creates a skeleton from the given root bone.
   *
   * <p>All descendant bones are indexed by name for efficient lookup.
   *
   * @param root the root bone
   */
  public Skeleton2D(Bone2D root) {
    this.root = root;
    indexBones(root);
  }

  private void indexBones(Bone2D bone) {
    boneMap.put(bone.name(), bone);
    for (Bone2D child : bone.children()) {
      indexBones(child);
    }
  }

  /**
   * Returns the root bone.
   *
   * @return the root bone
   */
  public Bone2D root() {
    return root;
  }

  /**
   * Looks up a bone by name.
   *
   * @param name the bone name
   * @return the bone, or {@code null} if not found
   */
  public @Nullable Bone2D findBone(String name) {
    return boneMap.get(name);
  }

  /**
   * Returns all bones indexed by name.
   *
   * @return a map of bone names to bones
   */
  public Map<String, Bone2D> bones() {
    return Collections.unmodifiableMap(boneMap);
  }

  /**
   * Computes the world-space transform for every bone in the hierarchy.
   *
   * @return a map of bone names to world-space transform matrices
   */
  public Map<String, Matrix3x2> computeWorldTransforms() {
    Map<String, Matrix3x2> transforms = new LinkedHashMap<>();
    computeRecursive(root, transforms);
    return transforms;
  }

  private void computeRecursive(Bone2D bone, Map<String, Matrix3x2> out) {
    out.put(bone.name(), bone.worldTransform());
    for (Bone2D child : bone.children()) {
      computeRecursive(child, out);
    }
  }
}
