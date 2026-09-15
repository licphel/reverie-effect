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
import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.CelestialUtil;
import io.viki.rf.world.util.Locatable;

/** One textured, camera-relative layer of a sky. */
final class Parallax {
  private static final float BASE_IMAGE_SIZE = 512F;
  private static final float CAMERA_PARALLAX_SCALE = 16F;

  private final TexturePart image;
  private final boolean lightImpact;
  private final float resist;

  Parallax(TexturePart image, boolean lightImpact, float resist) {
    if (resist < 0F || resist > 1F) {
      throw new IllegalArgumentException("Parallax resistance must be in [0, 1]: " + resist);
    }
    this.image = image;
    this.lightImpact = lightImpact;
    this.resist = resist;
  }

  void render(BatchedGraphics graphics, Level level, Locatable position,
              float width, float height, float opacity,
              AmbientLightComposer lightComposer) {
    float ratio = height / BASE_IMAGE_SIZE;
    float displacementX = (float) position.getX() * (1F - resist) * CAMERA_PARALLAX_SCALE;
    float displacementY = (float) -position.getY() * (1F - resist) * CAMERA_PARALLAX_SCALE;
    float sourceX = displacementX / ratio;
    float sourceY = displacementY / ratio;
    Gradient previous = graphics.gradient();
    try {
      if (lightImpact) {
        graphics.setTint(new Color(1F, 1F, 1F, opacity));
        lightComposer.draw(graphics, image, 0F, 0F, width, height,
            sourceX, sourceY - height / ratio, width / ratio, height / ratio);
      } else {
        Color sunlight = CelestialUtil.backgroundLight(level, position);
        graphics.setTint(new Color(sunlight.red(), sunlight.green(), sunlight.blue(),
            sunlight.alpha() * opacity));
        float imageHeight = BASE_IMAGE_SIZE * ratio;
        graphics.drawTexture(image, 0F,
            displacementY - (1F - resist) * imageHeight, width, imageHeight,
            sourceX, 0F, width / ratio, BASE_IMAGE_SIZE);
      }
    } finally {
      graphics.setTint(previous);
    }
  }
}
