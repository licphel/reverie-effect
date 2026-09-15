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

import org.jspecify.annotations.Nullable;

/** Mutable runtime attachment state for one draw-order entry. Not thread-safe. */
public final class Slot {
  private final int index;
  private final SkeletonDefinition.SlotDefinition definition;
  private @Nullable RegionAttachment attachment;

  Slot(int index, SkeletonDefinition.SlotDefinition definition) {
    this.index = index;
    this.definition = definition;
    attachment = definition.attachment();
  }

  public String name() {
    return definition.name();
  }

  public int index() {
    return index;
  }

  public @Nullable RegionAttachment attachment() {
    return attachment;
  }

  public Slot attachment(@Nullable RegionAttachment attachment) {
    this.attachment = attachment;
    return this;
  }

  public Slot resetToSetupPose() {
    attachment = definition.attachment();
    return this;
  }

  int boneIndex() {
    return definition.boneIndex();
  }
}
