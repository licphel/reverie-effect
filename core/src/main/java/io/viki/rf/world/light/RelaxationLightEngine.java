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

import io.viki.momentum.math.shape.Rectangle;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;

import java.util.Arrays;

/**
 * Light engine that recomputes its full window on a background virtual thread, spreading
 * light with four relaxation passes in alternating directions.
 *
 * <p>The window size adapts to the camera (see {@link LightEngine}); resizes copy the
 * overlapping region so the renderer never sees a blank window. The computed buffer is
 * swapped into the front only once finished, so the renderer always reads a complete,
 * consistent frame.
 *
 * @see LightEngine
 */
public class RelaxationLightEngine extends LightEngine {
  protected boolean ultraQuality = true;

  /**
   * Creates a relaxing light engine for the given level.
   *
   * @param level the level to light
   */
  public RelaxationLightEngine(Level level) {
    super(level);
  }

  /**
   * Computes the next frame into the back buffer: seeds block and entity light, spreads
   * it with four alternating passes, then populates the per-vertex values.
   *
   * @param cam the camera bounds to compute light for
   */
  @Override
  protected void calculate(Rectangle cam) {
    float margin = MAX_VALUE_GENERAL / UNIT - cam.width() / 128.0F;
    int x0 = (int) (cam.minX() - margin);
    int y0 = (int) (cam.minY() - margin);
    int x1 = (int) (cam.maxX() + margin);
    int y1 = (int) (cam.maxY() + margin);

    // guard: the window must contain the computation range
    if (x1 - x0 + 1 > sizeX || y1 - y0 + 1 > sizeY) {
      return; // window too small for this camera — next tick will resize
    }

    // clear the window — additive composition formulas would accumulate
    // leftover values from the previous frame otherwise
    Arrays.fill(back, 0F);

    cc.checkLoss(x0 - 1, y0 - 1, x1 + 1, y1 + 1);

    // Phase 1: seed
    for (int x = x0 - 1; x <= x1 + 1; x++) {
      for (int y = y0 - 1; y <= y1 + 1; y++) {
        seed(cc, x, y);
      }
    }

    // Phase 2: entity lights
    for (Entity e : level.entities().all()) {
      seed(e);
    }

    // Phase 3: 4-pass spread
    channelDispatch(channel -> {
      for (int x = x1; x >= x0; x--) {
        for (int y = y1; y >= y0; y--) {
          spread(x, y, channel);
        }
      }
      for (int x = x0; x <= x1; x++) {
        for (int y = y0; y <= y1; y++) {
          spread(x, y, channel);
        }
      }
      // Ping-pong relaxation is basically enough at large scale.
      if (ultraQuality) {
        for (int x = x0; x <= x1; x++) {
          for (int y = y1; y >= y0; y--) {
            spread(x, y, channel);
          }
        }
        for (int x = x1; x >= x0; x--) {
          for (int y = y0; y <= y1; y++) {
            spread(x, y, channel);
          }
        }
      }
    });

    // Beam light: max-blend into raw channels after spread (spread is
    // isotropic and would wash out the cone if the beam went through it)
    mergeBeam();
  }

  /**
   * Relaxes a tile's light toward the brightest of its four neighbors.
   */
  private void spread(int x, int y, byte channel) {
    if (!cc.isLoaded(x, y)) {
      return;
    }
    int o = backBufferIndex(x, y);
    if (o < 0) {
      return;
    }
    spreadOnChannel(o, channel, x, y);
  }

  /**
   * Relaxes one channel of a tile toward the brightest of its four neighbors, applying
   * the tile's light filter; values at or below {@link #DARK_LUMINANCE} are stored as
   * darkness.
   *
   * @param o       the working buffer offset of the tile
   * @param channel the gradient channel to spread
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   */
  private void spreadOnChannel(int o, byte channel, int x, int y) {
    float l1 = getChannelValue(x - 1, y, channel);
    float l2 = getChannelValue(x + 1, y, channel);
    float l3 = getChannelValue(x, y - 1, channel);
    float l4 = getChannelValue(x, y + 1, channel);
    float max = Math.max(l1, Math.max(l2, Math.max(l3, l4)));
    // only light traveling in from a neighbor is filtered; an already
    // converged tile keeps its value, otherwise the open-sky seed would be
    // dimmed by the filter on every pass (the passes iterate)
    float f = Math.max(back[o + channel], filter(cc, channel, x, y, max));
    back[o + channel] = f <= DARK_LUMINANCE ? 0F : f;
  }
}
