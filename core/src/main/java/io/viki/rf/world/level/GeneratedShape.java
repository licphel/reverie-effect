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

package io.viki.rf.world.level;

import io.viki.rf.world.block.TileShape;

/** A generated per-tile shape/carve value. */
public record GeneratedShape(int x, int y, int priority, String source,
                             long sequence, int flags, byte shape)
    implements GenerationStep {
  public GeneratedShape {
    GenerationStep.check(priority, source, sequence, flags);
    if ((shape & 0xFF) >= TileShape.COUNT) {
      throw new IllegalArgumentException("unknown generated shape: " + (shape & 0xFF));
    }
  }

  @Override
  public Kind kind() {
    return Kind.SHAPE;
  }

  @Override
  public void generate(ChunkMap map) {
    map.setBlockShape(x, y, shape, flags);
  }
}
