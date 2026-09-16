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

import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.fluid.FluidEngine;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.fluid.LiquidRenderDefinition;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.ChunkPos;
import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.util.Loop;
import io.viki.momentum.resource.Resource;
import io.viki.momentum.registry.Registry;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Liquid renderer ported from Enchant's {@code LiquidRenderer}. Liquid bodies
 * and exposed surfaces are emitted in separate passes, preventing the texture
 * change for every cell that would otherwise fragment the GPU batch.
 */
public final class LiquidRenderer implements AutoCloseable {
  private final Map<Integer, Texture> bodies = new HashMap<>();
  private final Map<Integer, Texture> surfaces = new HashMap<>();

  private LiquidRenderer() {
  }

  /**
   * Loads the body and surface textures for every registered liquid; returns
   * {@code null} if any texture is missing.
   */
  public static @Nullable LiquidRenderer create(Device dev, Registry<Liquid> liquids) {
    Resource rp = Resource.classpath(LiquidRenderer.class);
    LiquidRenderer r = new LiquidRenderer();
    for (Liquid liquid : liquids) {
      LiquidRenderDefinition definition = liquid.renderDefinition();
      if (!definition.visible()) {
        continue;
      }
      try {
        int storageId = liquid.registryIndex();
        r.bodies.put(storageId, Texture.loadRGBA8(dev,
            new PngInputStream(rp.open(definition.bodyTexturePath())).info()));
        r.surfaces.put(storageId, Texture.loadRGBA8(dev,
            new PngInputStream(rp.open(definition.surfaceTexturePath())).info()));
      } catch (Exception e) {
        r.close();
        return null;
      }
    }
    return r;
  }

  private static boolean perfect(Level level, int x, int y) {
    BlockState s = level.getBlockIfLoaded(x, y);
    return s != null && !s.isEmpty() && level.shapeAtIfLoaded(x, y) == Shape.SOLID;
  }

  /**
   * Renders visible liquid bodies first and exposed surfaces second.
   */
  public void render(BatchedGraphics g, Level level, Camera2D cam) {
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    float time = Loop.frameTime();
    float uScroll = time * 5F;
    float vScroll = time * 2.5F;
    int minChunkX = (int) Math.floor((cp.x() - vw / 2F) / cs);
    int maxChunkX = (int) Math.floor((cp.x() + vw / 2F) / cs);
    int minChunkY = (int) Math.floor((cp.y() - vh / 2F) / cs);
    int maxChunkY = (int) Math.floor((cp.y() + vh / 2F) / cs);
    g.setTint(Color.WHITE);

    renderBodies(g, level, cs, minChunkX, maxChunkX, minChunkY, maxChunkY,
        time, uScroll, vScroll);
    renderSurfaces(g, level, cs, minChunkX, maxChunkX, minChunkY, maxChunkY,
        time, uScroll);
  }

  private void renderBodies(
      BatchedGraphics g, Level level, int chunkSize,
      int minChunkX, int maxChunkX, int minChunkY, int maxChunkY,
      float time, float uScroll, float vScroll) {
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      for (int cy = minChunkY; cy <= maxChunkY; cy++) {
        Chunk ck = level.getChunk(new ChunkPos(cx, cy));
        if (ck == null) {
          continue;
        }
        for (int ly = 0; ly < chunkSize; ly++) {
          for (int lx = 0; lx < chunkSize; lx++) {
            int wx = cx * chunkSize + lx;
            int wy = cy * chunkSize + ly;
            int lv = ck.getLiquidLevel(wx, wy);
            if (lv <= 0) {
              continue;
            }
            Texture tex = bodies.get(Byte.toUnsignedInt(ck.getLiquidType(wx, wy)));
            if (tex == null) {
              continue;
            }

            float h = surfaceHeight(level, wx, wy, lv, time);

            // falling liquid (nothing below): a small centered blob
            if (isFalling(level, wx, wy)) {
              float half = (float) lv / FluidEngine.FULL / 2F;
              g.drawTexture(tex, wx + 0.5F - half, wy + 0.5F - half, half * 2F, half * 2F,
                  (uScroll + wx * 8F) % tex.width(), (vScroll + wy * 8F) % tex.height(), 8F, 8F);
              continue;
            }

            float u = uScroll + wx * 8F;
            float v = vScroll + wy * 8F + 8F * (1F - h);
            // One cell, one quad. UVs stay inside the sheet so scrolling keeps
            // sub-texel precision without creating extra surface geometry.
            float tw = tex.width();
            float th = tex.height();
            float cropH = h * 8F;
            u = (u % tw + tw) % tw;
            v = (v % th + th) % th;

            g.drawTexture(tex, wx, wy, 1F, h, u, v, 8F, cropH);
          }
        }
      }
    }
  }

  private void renderSurfaces(
      BatchedGraphics g, Level level, int chunkSize,
      int minChunkX, int maxChunkX, int minChunkY, int maxChunkY,
      float time, float uScroll) {
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      for (int cy = minChunkY; cy <= maxChunkY; cy++) {
        Chunk ck = level.getChunk(new ChunkPos(cx, cy));
        if (ck == null) {
          continue;
        }
        for (int ly = 0; ly < chunkSize; ly++) {
          for (int lx = 0; lx < chunkSize; lx++) {
            int wx = cx * chunkSize + lx;
            int wy = cy * chunkSize + ly;
            int lv = ck.getLiquidLevel(wx, wy);
            if (lv <= 0 || level.getLiquidLevel(wx, wy + 1) > 0 || isFalling(level, wx, wy)) {
              continue;
            }
            Texture edge = surfaces.get(Byte.toUnsignedInt(ck.getLiquidType(wx, wy)));
            if (edge == null) {
              continue;
            }
            float height = surfaceHeight(level, wx, wy, lv, time);
            float u = positiveModulo(uScroll, edge.width());
            g.drawTexture(edge, wx, wy + height - 1F / 8F, 1F, 1F / 8F,
                u, 0F, 8F, 1F);
          }
        }
      }
    }
  }

  private static float surfaceHeight(Level level, int x, int y, int liquidLevel, float time) {
    float height = liquidLevel / (float) FluidEngine.FULL;
    if (level.getLiquidLevel(x, y + 1) == 0
        && (height < 1F || !perfect(level, x, y + 1))) {
      height += 0.05F * (float) Math.sin(x * 0.5F + y * 0.05F + time * 3F);
    }
    return Math.clamp(height, 0F, 1F);
  }

  private static boolean isFalling(Level level, int x, int y) {
    BlockState below = level.getBlockIfLoaded(x, y - 1);
    return level.getLiquidLevel(x, y - 1) < FluidEngine.FULL
        && below != null && below.isEmpty();
  }

  private static float positiveModulo(float value, float divisor) {
    return (value % divisor + divisor) % divisor;
  }

  @Override
  public void close() {
    for (Texture texture : bodies.values()) {
      texture.close();
    }
    for (Texture texture : surfaces.values()) {
      texture.close();
    }
    bodies.clear();
    surfaces.clear();
  }
}
