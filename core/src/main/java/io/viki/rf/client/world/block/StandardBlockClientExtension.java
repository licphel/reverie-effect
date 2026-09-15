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
import io.viki.rf.Registries;
import io.viki.rf.world.block.Block;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Standard textured block rendering, including body and edge geometry. */
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
  private static final boolean RENDER_EDGES = true;

  private static final int SLOT_LL = 0;
  private static final int SLOT_LU = 1;
  private static final int SLOT_UL = 2;
  private static final int SLOT_UR = 3;
  private static final int SLOT_RU = 4;
  private static final int SLOT_RL = 5;
  private static final int SLOT_DR = 6;
  private static final int SLOT_DL = 7;

  private static final byte ET_NONE = 0;
  private static final byte ET_EDGE = 1;
  private static final byte ET_BEND = 2;

  private final String texturePath;
  private @Nullable TexturePart texture;

  public StandardBlockClientExtension() {
    this("");
  }

  public StandardBlockClientExtension(String texturePath) {
    this.texturePath = Objects.requireNonNull(texturePath, "texturePath");
  }

  /** Registers the built-in standard extensions before the client atlas is built. */
  public static void registerBuiltinExtensions() {
    registerBuiltinExtension(Registries.DIRT, "/dirt.png");
    registerBuiltinExtension(Registries.GRASS, "/dirt.png");
    registerBuiltinExtension(Registries.STONE, "/stone.png");
    registerBuiltinExtension(Registries.WALL, "/ice.png");
    registerBuiltinExtension(Registries.COLORFUL, "/ice.png");
    registerBuiltinExtension(Registries.PLATFORM, "/ice.png");
  }

  private static void registerBuiltinExtension(Block block, String texturePath) {
    for (BlockState state : block.states()) {
      if (BlockClientExtension.get(state) == null) {
        BlockClientExtension.register(state, new StandardBlockClientExtension(texturePath));
      }
    }
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
  public void drawBlock(Graphics g, Level level, BlockState state, int x, int y) {
    TexturePart texture = this.texture;
    if (texture == null) {
      return;
    }
    drawBody(g, texture, x, y, TileShape.byId(level.getBlockShape(x, y)));
  }

  @Override
  public void drawBlockEdge(Graphics g, Level level, BlockState state, int x, int y) {
    TexturePart texture = this.texture;
    if (texture == null) {
      return;
    }
    TileShape shape = TileShape.byId(level.getBlockShape(x, y));
    drawEdges(g, level, state, x, y, shape, false, texture);
  }

  @Override
  public void drawWall(Graphics g, Level level, BlockState state, int x, int y) {
    TexturePart texture = this.texture;
    if (texture != null) {
      drawBody(g, texture, x, y, TileShape.FULL);
    }
  }

  @Override
  public void drawWallEdge(Graphics g, Level level, BlockState state, int x, int y) {
    TexturePart texture = this.texture;
    if (texture != null) {
      drawEdges(g, level, state, x, y, TileShape.FULL, true, texture);
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

  private static void drawEdges(Graphics g, Level level, BlockState self, int x, int y,
                                TileShape shape, boolean wall, TexturePart texture) {
    if (!RENDER_EDGES || (!isPerfectVoxel(self, shape) && shape == TileShape.FULL)) {
      return;
    }
    long hash = placeHash(x, y);
    int rdu = (int) (Math.abs(hash) % 4) * 13 * SCALE;
    int rdu2 = (int) (Math.abs(hash + 1) % 4) * 9 * SCALE;
    for (int slot = SLOT_LL; slot <= SLOT_DL; slot++) {
      byte type = classifySlot(level, x, y, self, shape, slot, wall);
      if (type != ET_NONE) {
        drawSlot(g, texture, x, y, shape, slot, type, rdu, rdu2, level, wall);
      }
    }
  }

  private static boolean isPerfectVoxel(BlockState state, TileShape shape) {
    return shape == TileShape.FULL && state.shape() == Shape.SOLID;
  }

  private static boolean connectable(BlockState neighbor, TileShape neighborShape,
                                     BlockState self, TileShape selfShape) {
    return neighbor.block() == self.block() && neighborShape == selfShape;
  }

  private static boolean slotSolid(TileShape shape, int slot) {
    return switch (shape) {
      case FULL -> true;
      case HALF_BRICK -> slot == SLOT_LL || slot == SLOT_UL || slot == SLOT_UR || slot == SLOT_RL
          || slot == SLOT_DR || slot == SLOT_DL;
      case HALF_BRICK_UP -> slot == SLOT_LU || slot == SLOT_UL || slot == SLOT_UR || slot == SLOT_RU
          || slot == SLOT_DR || slot == SLOT_DL;
      case SLOPE_LEFT_DOWN -> slot == SLOT_RU || slot == SLOT_RL || slot == SLOT_DR || slot == SLOT_DL;
      case SLOPE_RIGHT_DOWN -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_DR || slot == SLOT_DL;
      case SLOPE_LEFT_UP -> slot == SLOT_UL || slot == SLOT_UR || slot == SLOT_RU || slot == SLOT_RL;
      case SLOPE_RIGHT_UP -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_UL || slot == SLOT_UR;
    };
  }

  private static boolean neighborSlotSolid(TileShape shape, int slot,
                                            boolean brickTop, boolean brickBottom) {
    return switch (slot) {
      case SLOT_LL -> shape == TileShape.FULL || shape == TileShape.HALF_BRICK
          || shape == TileShape.SLOPE_LEFT_DOWN || shape == TileShape.SLOPE_LEFT_UP;
      case SLOT_LU -> shape == TileShape.FULL || shape == TileShape.HALF_BRICK_UP
          || shape == TileShape.SLOPE_LEFT_DOWN || shape == TileShape.SLOPE_LEFT_UP;
      case SLOT_UL, SLOT_UR -> !brickTop && (shape == TileShape.FULL || shape == TileShape.HALF_BRICK
          || shape == TileShape.SLOPE_LEFT_DOWN || shape == TileShape.SLOPE_RIGHT_DOWN);
      case SLOT_RU -> shape == TileShape.FULL || shape == TileShape.HALF_BRICK_UP
          || shape == TileShape.SLOPE_RIGHT_DOWN || shape == TileShape.SLOPE_RIGHT_UP;
      case SLOT_RL -> shape == TileShape.FULL || shape == TileShape.HALF_BRICK
          || shape == TileShape.SLOPE_RIGHT_DOWN || shape == TileShape.SLOPE_RIGHT_UP;
      case SLOT_DR, SLOT_DL -> !brickBottom && (shape == TileShape.FULL || shape == TileShape.HALF_BRICK_UP
          || shape == TileShape.SLOPE_LEFT_UP || shape == TileShape.SLOPE_RIGHT_UP);
      default -> false;
    };
  }

  private static boolean bendEligible(TileShape shape, int slot) {
    return switch (shape) {
      case FULL -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_UL || slot == SLOT_RU;
      case HALF_BRICK, HALF_BRICK_UP -> false;
      case SLOPE_LEFT_DOWN -> slot == SLOT_RU;
      case SLOPE_RIGHT_DOWN -> slot == SLOT_LL || slot == SLOT_LU;
      case SLOPE_LEFT_UP -> slot == SLOT_UL || slot == SLOT_RU;
      case SLOPE_RIGHT_UP -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_UL;
    };
  }

  private static boolean bendValid(TileShape diagonalShape, int slot) {
    return switch (slot) {
      case SLOT_LL -> slotSolid(diagonalShape, SLOT_UR);
      case SLOT_LU -> slotSolid(diagonalShape, SLOT_DR);
      case SLOT_UL -> slotSolid(diagonalShape, SLOT_RL);
      case SLOT_RU -> slotSolid(diagonalShape, SLOT_DL);
      default -> true;
    };
  }

  private static boolean bendStrokeMerged(Level level, int x, int y, BlockState diagonal,
                                          int slot, boolean wall) {
    int tx;
    int ty;
    int targetSlot;
    switch (slot) {
      case SLOT_LL -> {
        tx = x - 1;
        ty = y;
        targetSlot = SLOT_DR;
      }
      case SLOT_LU -> {
        tx = x - 1;
        ty = y;
        targetSlot = SLOT_UR;
      }
      case SLOT_UL -> {
        tx = x;
        ty = y + 1;
        targetSlot = SLOT_LU;
      }
      default -> {
        tx = x + 1;
        ty = y;
        targetSlot = SLOT_UL;
      }
    }
    BlockState target = wall ? level.getWallIfLoaded(tx, ty) : level.getBlockIfLoaded(tx, ty);
    if (target == null || target.isEmpty() || target.block() != diagonal.block()) {
      return false;
    }
    TileShape targetShape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(tx, ty));
    return slotSolid(targetShape, targetSlot);
  }

  private static byte classifySlot(Level level, int x, int y, BlockState self, TileShape shape,
                                   int slot, boolean wall) {
    int nx;
    int ny;
    switch (slot) {
      case SLOT_LL, SLOT_LU -> {
        nx = x - 1;
        ny = y;
      }
      case SLOT_UL, SLOT_UR -> {
        nx = x;
        ny = y + 1;
      }
      case SLOT_RU, SLOT_RL -> {
        nx = x + 1;
        ny = y;
      }
      default -> {
        nx = x;
        ny = y - 1;
      }
    }
    BlockState neighbor = wall ? level.getWallIfLoaded(nx, ny) : level.getBlockIfLoaded(nx, ny);
    TileShape neighborShape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(nx, ny));
    boolean me = slotSolid(shape, slot);
    boolean other = neighbor != null && !neighbor.isEmpty()
        && neighborSlotSolid(neighborShape, slot, shape == TileShape.HALF_BRICK,
            shape == TileShape.HALF_BRICK_UP);
    if (!me && !other) {
      return ET_NONE;
    }
    if (me && other) {
      if (self.block() == neighbor.block()
          || self.block().registryIndex() < neighbor.block().registryIndex()) {
        return ET_NONE;
      }
    } else if (!me) {
      return ET_NONE;
    }

    int dx;
    int dy;
    switch (slot) {
      case SLOT_LL, SLOT_DL -> {
        dx = x - 1;
        dy = y - 1;
      }
      case SLOT_LU, SLOT_UL -> {
        dx = x - 1;
        dy = y + 1;
      }
      case SLOT_UR, SLOT_RU -> {
        dx = x + 1;
        dy = y + 1;
      }
      default -> {
        dx = x + 1;
        dy = y - 1;
      }
    }
    BlockState diagonal = wall ? level.getWallIfLoaded(dx, dy) : level.getBlockIfLoaded(dx, dy);
    TileShape diagonalShape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(dx, dy));
    if (diagonal == null || !connectable(diagonal, diagonalShape, self, shape)) {
      return ET_EDGE;
    }
    if (bendEligible(shape, slot) && bendValid(diagonalShape, slot)
        && !bendStrokeMerged(level, x, y, diagonal, slot, wall)) {
      return ET_BEND;
    }
    return switch (slot) {
      case SLOT_UR -> classifySlot(level, dx, dy, diagonal, diagonalShape, SLOT_LL, wall) == ET_BEND
          ? ET_NONE : ET_EDGE;
      case SLOT_RL -> classifySlot(level, dx, dy, diagonal, diagonalShape, SLOT_UL, wall) == ET_BEND
          ? ET_NONE : ET_EDGE;
      case SLOT_DR -> classifySlot(level, dx, dy, diagonal, diagonalShape, SLOT_LU, wall) == ET_BEND
          ? ET_NONE : ET_EDGE;
      case SLOT_DL -> classifySlot(level, dx, dy, diagonal, diagonalShape, SLOT_RU, wall) == ET_BEND
          ? ET_NONE : ET_EDGE;
      default -> ET_EDGE;
    };
  }

  private static boolean brickLeftWrap(Level level, int x, int y, boolean wall) {
    BlockState left = wall ? level.getWallIfLoaded(x - 1, y) : level.getBlockIfLoaded(x - 1, y);
    if (left == null || left.isEmpty()) {
      return true;
    }
    TileShape shape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(x - 1, y));
    return shape != TileShape.FULL && shape != TileShape.SLOPE_LEFT_UP
        && shape != TileShape.SLOPE_RIGHT_UP;
  }

  private static void drawSlot(Graphics g, TexturePart texture, int x, int y, TileShape shape,
                               int slot, byte type, int rdu, int rdu2, Level level, boolean wall) {
    if (shape == TileShape.HALF_BRICK && (slot == SLOT_UL || slot == SLOT_UR)) {
      if (slot == SLOT_UL) {
        if (type == ET_BEND) {
          if (brickLeftWrap(level, x, y, wall)) {
            g.drawTexture(texture, x - 0.5F, y + 0.5F, 0.5F, 0.5F,
                rdu2 + 37 * SCALE, 13 * SCALE, 4 * SCALE, 4 * SCALE);
          }
          g.drawTexture(texture, x, y + 0.5F, 0.5F, 0.5F,
              rdu2 + 33 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(texture, x, y + 0.5F, 0.5F, 0.25F,
              rdu + 35 * SCALE, 0, 4 * SCALE, 2 * SCALE);
        }
      } else if (type == ET_BEND) {
        g.drawTexture(texture, x + 0.5F, y + 0.5F, 0.5F, 0.5F,
            rdu2 + 37 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
      } else {
        g.drawTexture(texture, x + 0.5F, y + 0.5F, 0.5F, 0.25F,
            rdu + 39 * SCALE, 0, 4 * SCALE, 2 * SCALE);
      }
      return;
    }
    switch (slot) {
      case SLOT_LL -> {
        if (type == ET_BEND) {
          g.drawTexture(texture, x - 0.5F, y, 0.5F, 0.5F,
              rdu2 + 37 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(texture, x - 0.25F, y, 0.25F, 0.5F, rdu + 33 * SCALE,
              shape == TileShape.HALF_BRICK ? 2 * SCALE : 6 * SCALE,
              2 * SCALE, 4 * SCALE);
        }
      }
      case SLOT_LU -> {
        if (type == ET_BEND) {
          g.drawTexture(texture, x - 0.5F, y + 0.5F, 0.5F, 0.5F,
              rdu2 + 37 * SCALE, 13 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(texture, x - 0.25F, y + 0.5F, 0.25F, 0.5F,
              rdu + 33 * SCALE, 2 * SCALE, 2 * SCALE, 4 * SCALE);
        }
      }
      case SLOT_UL -> {
        if (type == ET_BEND) {
          g.drawTexture(texture, x, y + 1F, 0.5F, 0.5F,
              rdu2 + 33 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(texture, x, y + 1F, 0.5F, 0.25F,
              rdu + 35 * SCALE, 0, 4 * SCALE, 2 * SCALE);
        }
      }
      case SLOT_UR -> g.drawTexture(texture, x + 0.5F, y + 1F, 0.5F, 0.25F,
          rdu + 39 * SCALE, 0, 4 * SCALE, 2 * SCALE);
      case SLOT_RU -> {
        if (type == ET_BEND) {
          g.drawTexture(texture, x + 1F, y + 0.5F, 0.5F, 0.5F,
              rdu2 + 33 * SCALE, 13 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(texture, x + 1F, y + 0.5F, 0.25F, 0.5F,
              rdu + 43 * SCALE, 2 * SCALE, 2 * SCALE, 4 * SCALE);
        }
      }
      case SLOT_RL -> g.drawTexture(texture, x + 1F, y, 0.25F, 0.5F,
          rdu + 43 * SCALE, shape == TileShape.HALF_BRICK ? 2 * SCALE : 6 * SCALE,
          2 * SCALE, 4 * SCALE);
      case SLOT_DR -> g.drawTexture(texture, x + 0.5F, bottomEdgeY(shape, y), 0.5F, 0.25F,
          rdu + 39 * SCALE, 10 * SCALE, 4 * SCALE, 2 * SCALE);
      default -> g.drawTexture(texture, x, bottomEdgeY(shape, y), 0.5F, 0.25F,
          rdu + 35 * SCALE, 10 * SCALE, 4 * SCALE, 2 * SCALE);
    }
  }

  private static float bottomEdgeY(TileShape shape, float y) {
    return shape == TileShape.HALF_BRICK_UP ? y + 0.25F : y - 0.25F;
  }

  private static long placeHash(int x, int y) {
    long hash = (long) x * 374761393 + (long) y * 668265263;
    hash = (hash ^ (hash >> 15)) * 2246822519L;
    hash = (hash ^ (hash >> 13)) * 3266489917L;
    hash ^= hash >> 16;
    return hash;
  }
}
