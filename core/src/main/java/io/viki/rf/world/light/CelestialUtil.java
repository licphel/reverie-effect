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

package io.viki.rf.world.light;

import io.viki.momentum.gfx.tint.Color;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.Locatable;

/**
 * Day-cycle math for the sky and the sunlight that seeds the light engine.
 */
public final class CelestialUtil {
  private CelestialUtil() {
  }

  /**
   * Returns the angle of the sun/moon body on the day circle.
   *
   * @param level the level whose day phase to use
   * @return the body angle in radians
   */
  public static float bodyRadians(Level level) {
    return (float) (Math.PI * 2.0 / Level.TICKS_PER_DAY * level.ticksOfDay()) + 0.75F;
  }

  /**
   * Returns the day curve along the body's horizontal axis, in {@code [0, 1]}.
   *
   * @param level the level whose day phase to use
   * @return 1 at noon, 0 at midnight
   */
  public static float cos(Level level) {
    return Math.clamp((float) Math.cos(bodyRadians(level)) * 0.5F + 0.5F, 0F, 1F);
  }

  /**
   * Returns the day curve along the body's vertical axis, in {@code [0, 1]}.
   *
   * @param level the level whose day phase to use
   * @return 1 at noon, 0 at midnight
   */
  public static float sin(Level level) {
    return Math.clamp((float) Math.sin(bodyRadians(level)) * 0.5F + 0.5F, 0F, 1F);
  }

  /**
   * Returns how far into space the given world Y is: 0 at or below the surface line,
   * rising linearly to 1 far above it.
   *
   * @param level the level
   * @param y     the world Y of the tile
   * @return the space depth in {@code [0, 1]}
   */
  public static float spaceFactor(Level level, double y) {
    int sl = level.getSpaceLevel();
    if (y <= sl) {
      return 0F;
    }
    float v = ((float) y - sl) / 256F;
    return v <= 0.05F ? 0.0F : Math.clamp(v, 0F, 1F);
  }

  /**
   * Returns the strength multiplier of the wall's back emission light.
   *
   * @param level the level
   * @param y     the world Y of the tile
   * @return the emission strength in {@code [0, 1]}
   */
  public static float backEmissionStrength(Level level, int y) {
    int sl = Level.SEA_LEVEL;
    if (y >= sl) {
      return 1.0F;
    }
    return Math.clamp(1.0F - (sl - y) / 16F, 0F, 1F);
  }

  /**
   * Returns the sunlight gradient seeding sky illumination: warm-tinted near the horizon,
   * bluish high in the sky, never reaching full black at night.
   *
   * @param level the level whose day phase to use
   * @return the sunlight gradient
   */
  public static Color lightingSunlight(Level level) {
    return lightingSunlight(level, 1F);
  }

  /**
   * Returns the sunlight gradient with an extra dimming factor, e.g. for walls that
   * receive less sun.
   *
   * @param level the level whose day phase to use
   * @param hard  extra dimming applied toward night
   * @return the sunlight gradient
   */
  public static Color lightingSunlight(Level level, float hard) {
    float f1 = sin(level) + 0.25F;
    float i = -f1 + 0.8F;
    return new Color(
        Math.clamp(f1 - i * 0.15F * hard, 0F, 1.1F),
        Math.clamp(f1 - i * 0.1F * hard, 0F, 1.1F),
        Math.clamp(f1 + i * 0.15F * hard, 0F, 1.1F));
  }

  /**
   * Returns the ambient background light at the given height, used to draw the sky and
   * clouds. A {@code 0.15} floor keeps the night background visible; the alpha fades
   * with depth into space.
   *
   * @param level the level whose day phase to use
   * @param loc   the world location
   * @return the background light gradient
   */
  public static Color backgroundLight(Level level, Locatable loc) {
    float spf = spaceFactor(level, loc.getY());
    Color c = lightingSunlight(level).multiply(1F - spf);
    return new Color(
        Math.clamp(c.red(), 0.15F, 1F),
        Math.clamp(c.green(), 0.15F, 1F),
        Math.clamp(c.blue(), 0.15F, 1F),
        1.0F - spf
    );
  }

  /**
   * Returns the sky gradient at the given height for a given day phase. The biome
   * temperature is fixed to temperate until biomes are implemented.
   *
   * @param level the level whose day phase to use
   * @param loc   the world location
   * @return the sky gradient
   */
  public static Color colorOfSky(Level level, Locatable loc) {
    float f = cos(level);
    float f1 = sin(level);
    // TODO: temperature 0 (temperate) — biomes are not implemented yet
    Color rgb0 = Color.createHsv((0.6F - (f - 0.5F) * 0.1F), 0.08F + (1F - f) * 0.25F, 0.98F)
        .multiply(f1)
        .multiply(1.05F - spaceFactor(level, loc.getY()));
    return new Color(rgb0.red() + 0.03F, rgb0.green() + 0.03F, rgb0.blue() + 0.05F);
  }

  /**
   * Fills the four corner colors of the background sky gradient, in quad vertex order:
   * the lower corners get the warm dusk/dawn tint, the upper corners a cool blue tint.
   *
   * @param level the level whose day phase to use
   * @param loc   the world location
   * @param out   receives the four corner colors; must hold at least 4 elements
   */
  public static void getSkyGradient(Level level, Locatable loc, Color[] out) {
    Color base = colorOfSky(level, loc);
    float day = sin(level);
    float i = Math.abs(day - 0.45F);
    final float len = 0.4F;
    if (i >= len) {
      i = 0F;
    } else {
      i = len - i;
    }
    float per = i / len * 2F;
    Color warm = base.multiply(new Color(1F - per * 0.05F, 1F - per * 0.05F, 1F - per * 0.45F));
    Color cool = base.multiply(new Color(0.85F, 0.9F, 1F));
    out[0] = warm;
    out[1] = warm;
    out[2] = cool;
    out[3] = cool;
  }
}
