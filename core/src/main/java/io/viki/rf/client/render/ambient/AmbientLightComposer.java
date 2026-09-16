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
import io.viki.momentum.gfx.util.impl.BatchedGraphics;

/** Draws a texture region through the foreground light composition path. */
@FunctionalInterface
public interface AmbientLightComposer {
  /** Draws an ambient image region at the supplied destination rectangle. */
  void draw(BatchedGraphics graphics, TexturePart image, float x, float y, float width, float height,
            float u, float v, float sourceWidth, float sourceHeight);

  /** Returns a direct full-bright composition fallback. */
  static AmbientLightComposer direct() {
    return (graphics, image, x, y, width, height, u, v, sourceWidth, sourceHeight) ->
        graphics.drawTexture(image, x, y, width, height, u, v, sourceWidth, sourceHeight);
  }
}
