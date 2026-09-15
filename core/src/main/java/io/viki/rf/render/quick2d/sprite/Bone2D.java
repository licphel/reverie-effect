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
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a 2D skeletal hierarchy.
 *
 * <p>Each bone holds a local transform (translation, rotation, scale) relative
 * to its parent. The world transform is computed by composing all ancestor
 * transforms up to the root. Bones form a tree through parent-child links;
 * child bones are automatically added to the parent on construction.
 *
 * @see Skeleton2D
 */
public final class Bone2D {
  private final String name;
  private final @Nullable Bone2D parent;
  private final List<Bone2D> children = new ArrayList<>();

  private Vector2 translation = Vector2.ZERO;
  private float rotation; // radians
  private Vector2 scale = Vector2.ONE;
  private Vector2 origin = Vector2.ZERO;

  /**
   * Creates a root bone with no parent.
   *
   * @param name the bone identifier
   */
  public Bone2D(String name) {
    this(name, null);
  }

  /**
   * Creates a child bone attached to the given parent.
   *
   * @param name   the bone identifier
   * @param parent the parent bone, or {@code null} for a root bone
   */
  public Bone2D(String name, @Nullable Bone2D parent) {
    this.name = name;
    this.parent = parent;
    if (parent != null) {
      parent.children.add(this);
    }
  }

  /**
   * Returns the bone name.
   *
   * @return the bone name
   */
  public String name() {
    return name;
  }

  /**
   * Returns the parent bone.
   *
   * @return the parent bone, or {@code null} if this bone is a root
   */
  public @Nullable Bone2D parent() {
    return parent;
  }

  /**
   * Returns the child bones.
   *
   * @return the child bones
   */
  public List<Bone2D> children() {
    return List.copyOf(children);
  }

  /**
   * Returns the local translation.
   *
   * @return the translation vector
   */
  public Vector2 translation() {
    return translation;
  }

  /**
   * Sets the local translation.
   *
   * @param t the translation vector
   */
  public void translation(Vector2 t) {
    this.translation = t;
  }

  /**
   * Returns the local rotation.
   *
   * @return the rotation angle in radians
   */
  public float rotation() {
    return rotation;
  }

  /**
   * Sets the local rotation.
   *
   * @param r the rotation angle in radians
   */
  public void rotation(float r) {
    this.rotation = r;
  }

  /**
   * Returns the local scale.
   *
   * @return the scale vector
   */
  public Vector2 scale() {
    return scale;
  }

  /**
   * Sets the local scale.
   *
   * @param s the scale vector
   */
  public void scale(Vector2 s) {
    this.scale = s;
  }

  /**
   * Returns the pivot origin for rotation and scaling.
   *
   * @return the origin vector
   */
  public Vector2 origin() {
    return origin;
  }

  /**
   * Sets the pivot origin for rotation and scaling.
   *
   * @param o the origin vector
   */
  public void origin(Vector2 o) {
    this.origin = o;
  }

  /**
   * Computes the local transform matrix.
   *
   * <p>The transform applies the origin offset, scale, rotation, and
   * translation in the standard order.
   *
   * @return the local-space transform matrix
   */
  public Matrix3x2 localTransform() {
    Matrix3x2 m = Matrix3x2.IDENTITY;
    if (origin.x() != 0 || origin.y() != 0) {
      m = m.translate(-origin.x(), -origin.y());
    }
    if (scale.x() != 1 || scale.y() != 1) {
      m = m.scale(scale.x(), scale.y());
    }
    if (rotation != 0) {
      m = m.rotate(rotation);
    }
    return m.translate(translation.x() + origin.x(), translation.y() + origin.y());
  }

  /**
   * Computes the world-space transform by composing all ancestor transforms.
   *
   * @return the world-space transform matrix
   */
  public Matrix3x2 worldTransform() {
    Matrix3x2 local = localTransform();
    if (parent == null) {
      return local;
    }
    return parent.worldTransform().multiply(local);
  }
}
