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

import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.Matrix3x2;

/** Stateless and thread-safe renderer for rigid region attachments. */
public final class SkeletonRenderer {
  private SkeletonRenderer() {
  }

  public static void draw(
      Graphics graphics, Skeleton2D skeleton, SkeletonTransform transform) {
    skeleton.updateWorldTransforms();
    SkeletonDefinition definition = skeleton.definition();
    Matrix3x2 root = transform.matrix(definition.localAnchor());

    for (int i = 0; i < skeleton.slotCount(); i++) {
      Slot slot = skeleton.slot(i);
      RegionAttachment attachment = slot.attachment();
      if (attachment == null) {
        continue;
      }
      Matrix3x2 matrix = root
          .multiply(skeleton.worldTransform(slot.boneIndex()))
          .multiply(attachment.localMatrix());
      graphics.transform().push(matrix.toMatrix4x4());
      try {
        graphics.drawTexture(
            attachment.texture(), 0.0F, 0.0F, attachment.width(), attachment.height());
      } finally {
        graphics.transform().pop();
      }
    }
  }
}
