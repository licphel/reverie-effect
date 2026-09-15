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

import io.viki.rf.world.block.BlockState;

import java.util.Map;

/**
 * World-thread write target for generation steps. Coordinates are absolute
 * world coordinates; the implementation routes writes to their chunks.
 */
public interface ChunkMap {
  /** Hook used by {@link GenerationQueue}; implementations may track the step key. */
  default void apply(GenerationStep step) {
    step.generate(this);
  }

  void setBlock(int x, int y, BlockState state, int flags);

  void setWall(int x, int y, BlockState state, int flags);

  void setBlockShape(int x, int y, byte shape, int flags);

  void setLiquid(int x, int y, byte liquidType, int amount, int flags);

  void spawnEntity(double x, double y, String typeId, Map<String, String> data, int flags);
}
