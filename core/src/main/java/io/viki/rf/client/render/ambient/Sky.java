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
import io.viki.momentum.gfx.tint.QuadGradient;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.util.Loop;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.CelestialUtil;
import io.viki.rf.world.util.Locatable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Stateful ambient sky whose textured layers fade in and out as a unit. */
public final class Sky {
  private static final float OPACITY_STEP = 0.1F;
  private static final float EXPECTED_STAR_SIZE_BASE = 64F;
  private static final float EXPECTED_CLOUD_COUNT = 12F;

  private final TexturePart cloud;
  private final TexturePart body1;
  private final TexturePart body2;
  private final List<SkyLayer> layers;
  private final List<Star> stars = new ArrayList<>();
  private final List<Cloud> clouds = new ArrayList<>();
  private final Color[] gradientColors = new Color[4];
  private final RandomGenerator random;
  private int lastWidth = -1;
  private int lastHeight = -1;
  private float opacity;

  public Sky(TexturePart cloud, TexturePart body1, TexturePart body2,
             List<SkyLayer> layers, long randomSeed) {
    this.cloud = Objects.requireNonNull(cloud, "cloud");
    this.body1 = Objects.requireNonNull(body1, "body1");
    this.body2 = Objects.requireNonNull(body2, "body2");
    this.layers = List.copyOf(layers);
    random = new SplittableRandom(randomSeed);
  }

  /** Returns the current transition opacity. */
  public float opacity() {
    return opacity;
  }

  boolean fadedOut() {
    return opacity <= 0F;
  }

  void tick(boolean active) {
    opacity = clamp(opacity + (active ? OPACITY_STEP : -OPACITY_STEP), 0F, 1F);
  }

  void tickBase(float width, float height) {
    int wi = Math.max(1, (int) width);
    int hi = Math.max(1, (int) height);
    if (lastWidth != wi || lastHeight != hi) {
      flush(wi, hi);
      lastWidth = wi;
      lastHeight = hi;
    }

    float diagonal = (float) Math.hypot(width, height);
    int expectedStars = (int) (EXPECTED_STAR_SIZE_BASE * ((width + height) / 2F / 300F));
    while (stars.size() < expectedStars) {
      stars.add(new Star(random.nextFloat(0F, width), random.nextFloat(0F, height),
          diagonal, random));
    }
    while (clouds.size() < (int) EXPECTED_CLOUD_COUNT) {
      clouds.add(new Cloud(cloud, width, height, true, random, Loop.frameTime()));
    }
  }

  void renderBase(BatchedGraphics graphics, Level level, Locatable position,
                  float width, float height) {
    CelestialUtil.getSkyGradient(level, position, gradientColors);
    graphics.setTint(new QuadGradient(gradientColors[0], gradientColors[1],
        gradientColors[2], gradientColors[3]));
    graphics.drawRectangle(0F, 0F, width, height);
    graphics.setTint(Color.WHITE);

    float daytime = CelestialUtil.sin(level);
    float space = CelestialUtil.spaceFactor(level, position.getY());
    for (Star star : stars) {
      star.render(graphics, daytime, space);
    }

    float time = Loop.frameTime();
    for (int i = clouds.size() - 1; i >= 0; i--) {
      Cloud current = clouds.get(i);
      current.render(graphics, level, position, width, time);
      if (current.removed()) {
        clouds.remove(i);
      }
    }
    renderBodies(graphics, level, width, height);
    graphics.setTint(Color.WHITE);
  }

  void renderLayers(BatchedGraphics graphics, Level level, Locatable position,
                    float width, float height, AmbientLightComposer lightComposer) {
    if (opacity <= 0F) {
      return;
    }
    for (SkyLayer layer : layers) {
      layer.render(graphics, level, position, width, height, opacity, lightComposer);
    }
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private void renderBodies(BatchedGraphics graphics, Level level,
                            float width, float height) {
    float radians = CelestialUtil.bodyRadians(level);
    float centerY = height / 8F;
    float offsetX = (float) Math.cos(radians) * height * 1.15F;
    float offsetY = (float) Math.sin(radians) * height * 0.75F;
    float scale = width / 1280F;
    float sunSize = 64F * scale;
    float moonSize = 32F * scale;
    graphics.setTint(Color.WHITE);
    graphics.drawTexture(body1, width / 2F + offsetX - sunSize / 2F,
        centerY + offsetY - sunSize / 2F, sunSize, sunSize);
    graphics.setTint(new Color(1F, 1F, 1F, 0.75F));
    graphics.drawTexture(body2, width / 2F - offsetX - moonSize / 2F,
        centerY - offsetY - moonSize / 2F, moonSize, moonSize);
    graphics.setTint(Color.WHITE);
  }

  private void flush(int width, int height) {
    stars.clear();
    clouds.clear();
    for (int i = 0; i < (int) EXPECTED_CLOUD_COUNT; i++) {
      clouds.add(new Cloud(cloud, width, height, false, random, Loop.frameTime()));
    }
  }
}
