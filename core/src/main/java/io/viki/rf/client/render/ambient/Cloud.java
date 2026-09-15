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

import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.rf.world.level.Level;
import io.viki.rf.GameConstants;
import io.viki.rf.world.light.CelestialUtil;
import io.viki.rf.world.util.Locatable;

import java.util.random.RandomGenerator;

/** A drifting textured cloud. */
final class Cloud {
  private static final float TICKS_PER_SECOND = GameConstants.TICKS_PER_SECOND;
  private static final float MAX_LIFETIME_TICKS = 200F;

  private final TexturePart image;
  private final float sizeW;
  private final float sizeH;
  private final float x;
  private final float y;
  private final float madeAt;
  private final float speed;
  private boolean removed;

  Cloud(TexturePart image, float width, float height, boolean easeIn,
        RandomGenerator random, float frameTime) {
    this.image = image;
    sizeW = (height == 0F ? 0F : (float) Math.hypot(width, height) / 24F)
        * random.nextFloat(1F, 4F);
    sizeH = sizeW / 275F * 60F;
    x = easeIn && random.nextBoolean()
        ? width
        : easeIn ? -sizeW : random.nextFloat(0F, width);
    y = random.nextFloat(height / 2F, height);
    madeAt = frameTime * TICKS_PER_SECOND;
    float direction = x > 0F ? -1F : 1F;
    speed = random.nextFloat(0.1F, 0.5F) * direction;
  }

  void render(BatchedGraphics graphics, Level level, Locatable position,
              float width, float time) {
    float age = time * TICKS_PER_SECOND - madeAt;
    float dx = age * speed;
    Color base = CelestialUtil.backgroundLight(level, position);
    graphics.setTint(new Color(
        Math.min(1F, base.red() * 1.2F),
        Math.min(1F, base.green() * 1.2F),
        Math.min(1F, base.blue() * 1.2F),
        base.alpha() * (1F - CelestialUtil.spaceFactor(level, position.getY()))));
    graphics.drawTexture(image, x - sizeW / 2F + dx, y - sizeW / 2F, sizeW, sizeH);
    if (age > MAX_LIFETIME_TICKS) {
      removed = x + dx > width || x + dx + sizeW < 0F;
    }
  }

  boolean removed() {
    return removed;
  }
}
