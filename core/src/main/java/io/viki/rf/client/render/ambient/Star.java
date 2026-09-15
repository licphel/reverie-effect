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

package io.viki.rf.client.render.ambient;

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.util.Loop;

import java.util.random.RandomGenerator;

/** A small, slowly twinkling screen-space star. */
final class Star {
  private final float x;
  private final float y;
  private final float size;
  private final float opacity;

  Star(float x, float y, float diagonal, RandomGenerator random) {
    this.x = x;
    this.y = y;
    size = diagonal / 600F;
    opacity = random.nextFloat();
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  void render(BatchedGraphics graphics, float daytime, float space) {
    float alpha = clamp(opacity - daytime + space, 0F, 1F)
        * ((float) Math.sin(Loop.frameTime() + x) * 0.5F + 0.25F);
    if (alpha <= 0.01F) {
      return;
    }
    graphics.setTint(new Color(1F, 1F, 1F, clamp(alpha, 0F, 1F)));
    graphics.drawRectangle(x - size / 2F, y - size / 2F, size, size);
  }
}
