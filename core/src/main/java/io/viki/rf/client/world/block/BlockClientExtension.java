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

package io.viki.rf.client.world.block;

import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.level.Level;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

@SideOnly(dist = Dist.CLIENT)
public interface BlockClientExtension {
  // thread safe once done
  Int2ObjectMap<BlockClientExtension> MAPPER = new Int2ObjectOpenHashMap<>();

  static @Nullable BlockClientExtension get(BlockState state) {
    return MAPPER.getOrDefault(state.identity(), null);
  }

  static void register(BlockState state, BlockClientExtension extension) {
    MAPPER.put(state.identity(), extension);
  }

  void draw(Graphics g, Level level, BlockState state, int x, int y);

  default void drawWall(Graphics g, Level level, BlockState state, int x, int y) {
    draw(g, level, state, x, y);
  }

  default boolean isAnimatedRendering(BlockState state) {
    return false;
  }

  default boolean isVisible(BlockState state) {
    return true;
  }
}
