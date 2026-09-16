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
import io.viki.rf.client.render.quick2d.bone.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkeletonTransformTest {
  private static final float EPSILON = 0.00001f;

  @Test
  void feetCenterResolvesFromCalculatedBounds() {
    Vector2 anchor = SkeletonAnchor.FEET_CENTER.resolve(new Rectangle(-2f, -1f, 6f, 7f));

    assertEquals(2f, anchor.x(), EPSILON);
    assertEquals(-1f, anchor.y(), EPSILON);
  }

  @Test
  void rootAnchorRemainsFixedDuringMirrorScaleAndRotation() {
    Vector2 anchor = new Vector2(2f, -1f);
    Matrix3x2 matrix = new SkeletonTransform(10f, 20f, 0.37f, 2f, 3f, true).matrix(anchor);

    assertPoint(matrix, anchor, 10f, 20f);
  }

  @Test
  void mirrorAndScaleAreAppliedAroundTheAnchor() {
    Vector2 anchor = new Vector2(2f, -1f);
    Matrix3x2 matrix = new SkeletonTransform(10f, 20f, 0f, 2f, 3f, true).matrix(anchor);

    assertPoint(matrix, new Vector2(3f, -1f), 8f, 20f);
    assertPoint(matrix, new Vector2(2f, 0f), 10f, 23f);
  }

  @Test
  void childBoneInheritsParentTransform() {
    SkeletonDefinition definition = SkeletonDefinition.builder()
        .root("root", Transform2D.at(2f, 3f, (float) (Math.PI * 0.5)))
        .bone("child", "root", Transform2D.at(1f, 0f))
        .build();
    Skeleton2D skeleton = Skeleton2D.create(definition);

    skeleton.updateWorldTransforms();

    assertPoint(skeleton.worldTransform(1), Vector2.ZERO, 2f, 4f);
  }

  private static void assertPoint(Matrix3x2 matrix, Vector2 point, float expectedX, float expectedY) {
    Vector2 actual = matrix.transform(point);
    assertEquals(expectedX, actual.x(), EPSILON);
    assertEquals(expectedY, actual.y(), EPSILON);
  }
}
