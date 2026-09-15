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

import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The standard textured client-side representation of a block. */
@SideOnly(dist = Dist.CLIENT)
public final class StandardBlockClientExtension implements BlockClientExtension {
  private static final int SCALE = 1;
  private static final int CELL = 8;
  private static final int FULL_X = 0;
  private static final int FULL_Y = 0;
  private static final int SLOPE_LEFT_DOWN_X = 33;
  private static final int SLOPE_LEFT_DOWN_Y = 22;
  private static final int SLOPE_RIGHT_DOWN_X = 42;
  private static final int SLOPE_RIGHT_DOWN_Y = 22;
  private static final int SLOPE_LEFT_UP_X = 51;
  private static final int SLOPE_LEFT_UP_Y = 22;
  private static final int SLOPE_RIGHT_UP_X = 60;
  private static final int SLOPE_RIGHT_UP_Y = 22;

  private final String texturePath;
  private @Nullable TexturePart texture;

  public StandardBlockClientExtension() {
    this("");
  }

  public StandardBlockClientExtension(String texturePath) {
    this.texturePath = Objects.requireNonNull(texturePath, "texturePath");
  }

  public String texturePath() {
    return texturePath;
  }

  public void bindTexture(TexturePart texture) {
    this.texture = Objects.requireNonNull(texture, "texture");
  }

  public @Nullable TexturePart texture() {
    return texture;
  }

  @Override
  public boolean isVisible(BlockState state) {
    return texture != null;
  }

  @Override
  public void draw(Graphics g, Level level, BlockState state, int x, int y) {
    TexturePart texture = this.texture;
    if (texture == null) {
      return;
    }
    drawBody(g, texture, x, y, TileShape.byId(level.getBlockShape(x, y)));
  }

  @Override
  public void drawWall(Graphics g, Level level, BlockState state, int x, int y) {
    TexturePart texture = this.texture;
    if (texture != null) {
      drawBody(g, texture, x, y, TileShape.FULL);
    }
  }

  private static void drawBody(Graphics g, TexturePart texture, int x, int y, TileShape shape) {
    if (shape != TileShape.FULL) {
      switch (shape) {
        case HALF_BRICK -> {
          long hash = placeHash(x, y);
          int u = FULL_X + (int) (Math.abs(hash) & 3) * CELL * SCALE;
          int v = FULL_Y + (int) ((Math.abs(hash) >> 3) & 3) * CELL * SCALE;
          g.drawTexture(texture, x, y, 1F, 0.5F,
              u, v, CELL * SCALE, CELL * SCALE / 2.0F);
        }
        case HALF_BRICK_UP -> {
          long hash = placeHash(x, y);
          int u = FULL_X + (int) (Math.abs(hash) & 3) * CELL * SCALE;
          int v = FULL_Y + (int) ((Math.abs(hash) >> 3) & 3) * CELL * SCALE;
          g.drawTexture(texture, x, y + 0.5F, 1F, 0.5F,
              u, v + CELL * SCALE / 2.0F, CELL * SCALE, CELL * SCALE / 2.0F);
        }
        case SLOPE_LEFT_DOWN -> g.drawTexture(texture, x, y, 1F, 1F,
            SLOPE_LEFT_DOWN_X * SCALE, SLOPE_LEFT_DOWN_Y * SCALE,
            CELL * SCALE, CELL * SCALE);
        case SLOPE_RIGHT_DOWN -> g.drawTexture(texture, x, y, 1F, 1F,
            SLOPE_RIGHT_DOWN_X * SCALE, SLOPE_RIGHT_DOWN_Y * SCALE,
            CELL * SCALE, CELL * SCALE);
        case SLOPE_LEFT_UP -> g.drawTexture(texture, x, y, 1F, 1F,
            SLOPE_LEFT_UP_X * SCALE, SLOPE_LEFT_UP_Y * SCALE,
            CELL * SCALE, CELL * SCALE);
        case SLOPE_RIGHT_UP -> g.drawTexture(texture, x, y, 1F, 1F,
            SLOPE_RIGHT_UP_X * SCALE, SLOPE_RIGHT_UP_Y * SCALE,
            CELL * SCALE, CELL * SCALE);
        case FULL -> throw new AssertionError("full tile shape handled above");
      }
      return;
    }
    long hash = placeHash(x, y);
    int u = FULL_X + (int) (Math.abs(hash) & 3) * CELL * SCALE;
    int v = FULL_Y + (int) ((Math.abs(hash) >> 3) & 3) * CELL * SCALE;
    g.drawTexture(texture, x, y, 1F, 1F, u, v, CELL * SCALE, CELL * SCALE);
  }

  private static long placeHash(int x, int y) {
    long hash = (long) x * 374761393 + (long) y * 668265263;
    hash = (hash ^ (hash >> 15)) * 2246822519L;
    hash = (hash ^ (hash >> 13)) * 3266489917L;
    hash ^= hash >> 16;
    return hash;
  }
}
