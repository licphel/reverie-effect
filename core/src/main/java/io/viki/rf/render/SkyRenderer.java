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

package io.viki.rf.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.rf.render.ambient.AmbientLightComposer;
import io.viki.rf.render.ambient.SkySupplier;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.Locatable;

/** Compatibility facade for the resource-backed ambient sky renderer. */
@SideOnly(dist = Dist.CLIENT)
public final class SkyRenderer implements AutoCloseable {
  private final io.viki.rf.render.ambient.SkyRenderer delegate;

  private SkyRenderer(io.viki.rf.render.ambient.SkyRenderer delegate) {
    this.delegate = delegate;
  }

  /** Opens the default forest-backed ambient renderer. */
  public static SkyRenderer open(Device device) {
    return new SkyRenderer(io.viki.rf.render.ambient.SkyRenderer.open(device));
  }

  /** Opens the renderer with a level-specific sky supplier. */
  public static SkyRenderer open(Device device, SkySupplier supplier) {
    return new SkyRenderer(io.viki.rf.render.ambient.SkyRenderer.open(device, supplier));
  }

  /** Draws the complete ambient background. */
  public void render(BatchedGraphics graphics, Level level, float width, float height,
                     Locatable position) {
    delegate.render(graphics, level, width, height, position);
  }

  /** Draws the non-emissive ambient background. */
  public void renderBackground(BatchedGraphics graphics, Level level,
                               float width, float height, Locatable position) {
    delegate.renderBackground(graphics, level, width, height, position);
  }

  /** Draws the compatibility emissive pass. */
  public void renderEmissive(BatchedGraphics graphics, Level level,
                             float width, float height, Locatable position) {
    delegate.renderEmissive(graphics, level, width, height, position);
  }

  /** Sets the composition path for light-impact ambient layers. */
  public void setLightComposer(AmbientLightComposer composer) {
    delegate.setLightComposer(composer);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
