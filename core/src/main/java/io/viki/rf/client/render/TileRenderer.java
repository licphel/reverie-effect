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

package io.viki.rf.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.mesh.Mesh;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.util.ZeroCopyVertexStore;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.util.impl.MeshGraphics;
import io.viki.momentum.registry.Registry;
import io.viki.rf.client.world.block.BlockClientExtension;
import io.viki.rf.world.block.Block;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

/** Dispatches block states to client-side block extensions and retains chunk meshes. */
public final class TileRenderer implements AutoCloseable {
  /** Limits chunk mesh construction to keep a camera move from stalling a frame. */
  private static final int MAX_BUILDS_PER_FRAME = 1;

  private final BlockTextureAtlas textures;
  private final Device dev;
  private final Long2ObjectMap<ChunkMesh> meshes = new Long2ObjectOpenHashMap<>();
  private final Long2ObjectMap<ChunkMesh> wallMeshes = new Long2ObjectOpenHashMap<>();
  private final MeshGraphics meshG;
  private int buildsThisFrame;

  private TileRenderer(Device dev, BlockTextureAtlas textures) {
    this.dev = dev;
    this.textures = textures;
    meshG = new MeshGraphics(new ZeroCopyVertexStore(), dev);
  }

  /** Creates a renderer from the registered client-side block extensions. */
  public static @Nullable TileRenderer create(Device dev, Registry<Block> blocks) {
    try {
      return new TileRenderer(dev, BlockTextureAtlas.open(dev, blocks));
    } catch (Exception e) {
      return null;
    }
  }

  /** Releases all retained meshes and client-side texture resources. */
  @Override
  public void close() {
    for (ChunkMesh mesh : meshes.values()) {
      mesh.close();
    }
    for (ChunkMesh mesh : wallMeshes.values()) {
      mesh.close();
    }
    meshes.clear();
    wallMeshes.clear();
    meshG.close();
    textures.close();
  }

  /** Releases retained meshes for an unloaded chunk. */
  public void unloadChunk(ChunkPos pos) {
    closeMesh(meshes.remove(pos.asLong()));
    closeMesh(wallMeshes.remove(pos.asLong()));
  }

  private static void closeMesh(@Nullable ChunkMesh mesh) {
    if (mesh != null) {
      mesh.close();
    }
  }

  /** Renders the block layer. */
  public void render(BatchedGraphics g, Level level, Camera2D cam) {
    renderLayer(g, level, cam, false);
  }

  /** Renders the wall layer. */
  public void renderWalls(BatchedGraphics g, Level level, Camera2D cam) {
    renderLayer(g, level, cam, true);
  }

  private void renderLayer(BatchedGraphics g, Level level, Camera2D cam, boolean wall) {
    var center = cam.center();
    float viewWidth = cam.width() / cam.zoom();
    float viewHeight = cam.height() / cam.zoom();
    int chunkSize = ChunkPos.SIZE;
    int minChunkX = (int) Math.floor((center.x() - viewWidth / 2F) / chunkSize);
    int maxChunkX = (int) Math.floor((center.x() + viewWidth / 2F) / chunkSize);
    int minChunkY = (int) Math.floor((center.y() - viewHeight / 2F) / chunkSize);
    int maxChunkY = (int) Math.floor((center.y() + viewHeight / 2F) / chunkSize);

    g.setTint(Color.WHITE);
    buildsThisFrame = 0;
    drawStatic(g, level, minChunkX, maxChunkX, minChunkY, maxChunkY, wall, false);
    drawAnimated(g, level, minChunkX, maxChunkX, minChunkY, maxChunkY, wall, false);
    drawStatic(g, level, minChunkX, maxChunkX, minChunkY, maxChunkY, wall, true);
    drawAnimated(g, level, minChunkX, maxChunkX, minChunkY, maxChunkY, wall, true);
  }

  private void drawStatic(BatchedGraphics g, Level level,
                          int minChunkX, int maxChunkX, int minChunkY, int maxChunkY,
                          boolean wall, boolean edge) {
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      for (int cy = minChunkY; cy <= maxChunkY; cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk chunk = level.getChunk(pos);
        if (chunk == null) {
          continue;
        }
        ChunkMesh mesh = edge ? readyMesh(chunk, pos, wall) : ensureMesh(level, pos, wall);
        if (mesh != null) {
          g.drawMesh(edge ? mesh.edge() : mesh.body());
        } else {
          drawImmediate(g, level, chunk, pos, wall, edge);
        }
      }
    }
  }

  private void drawImmediate(BatchedGraphics g, Level level, Chunk chunk, ChunkPos pos,
                             boolean wall, boolean edge) {
    int chunkSize = ChunkPos.SIZE;
    int x0 = pos.x() * chunkSize;
    int y0 = pos.y() * chunkSize;
    for (int ly = 0; ly < chunkSize; ly++) {
      for (int lx = 0; lx < chunkSize; lx++) {
        BlockState state = wall ? chunk.getWall(lx, ly) : chunk.getBlock(lx, ly);
        if (shouldDraw(state) && !extension(state).isAnimatedRendering(state)) {
          draw(g, level, state, x0 + lx, y0 + ly, wall, edge);
        }
      }
    }
  }

  private void drawAnimated(BatchedGraphics g, Level level,
                            int minChunkX, int maxChunkX, int minChunkY, int maxChunkY,
                            boolean wall, boolean edge) {
    int chunkSize = ChunkPos.SIZE;
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      for (int cy = minChunkY; cy <= maxChunkY; cy++) {
        Chunk chunk = level.getChunk(new ChunkPos(cx, cy));
        if (chunk == null) {
          continue;
        }
        int x0 = cx * chunkSize;
        int y0 = cy * chunkSize;
        for (int ly = 0; ly < chunkSize; ly++) {
          for (int lx = 0; lx < chunkSize; lx++) {
            BlockState state = wall ? chunk.getWall(lx, ly) : chunk.getBlock(lx, ly);
            if (shouldDraw(state) && extension(state).isAnimatedRendering(state)) {
              draw(g, level, state, x0 + lx, y0 + ly, wall, edge);
            }
          }
        }
      }
    }
  }

  private @Nullable ChunkMesh readyMesh(Chunk chunk, ChunkPos pos, boolean wall) {
    if (wall ? chunk.backDirty : chunk.frontDirty) {
      return null;
    }
    return (wall ? wallMeshes : meshes).get(pos.asLong());
  }

  private ChunkMesh ensureMesh(Level level, ChunkPos pos, boolean wall) {
    Chunk chunk = level.getChunk(pos);
    if (chunk == null) {
      return null;
    }
    Long2ObjectMap<ChunkMesh> retained = wall ? wallMeshes : meshes;
    ChunkMesh mesh = retained.get(pos.asLong());
    boolean dirty = wall ? chunk.backDirty : chunk.frontDirty;
    if (mesh != null && !dirty) {
      return mesh;
    }
    if (buildsThisFrame >= MAX_BUILDS_PER_FRAME) {
      return null;
    }
    closeMesh(mesh);
    mesh = buildMesh(level, pos, wall);
    retained.put(pos.asLong(), mesh);
    if (wall) {
      chunk.backDirty = false;
    } else {
      chunk.frontDirty = false;
    }
    buildsThisFrame++;
    return mesh;
  }

  private ChunkMesh buildMesh(Level level, ChunkPos pos, boolean wall) {
    Chunk chunk = level.getChunk(pos);
    if (chunk == null) {
      throw new IllegalStateException("cannot build a mesh for an unloaded chunk: " + pos);
    }
    int chunkSize = ChunkPos.SIZE;
    int x0 = pos.x() * chunkSize;
    int y0 = pos.y() * chunkSize;

    meshG.begin();
    meshG.setTint(Color.WHITE);
    for (int ly = 0; ly < chunkSize; ly++) {
      for (int lx = 0; lx < chunkSize; lx++) {
        BlockState state = wall ? chunk.getWall(lx, ly) : chunk.getBlock(lx, ly);
        if (shouldDraw(state) && !extension(state).isAnimatedRendering(state)) {
          draw(meshG, level, state, x0 + lx, y0 + ly, wall, false);
        }
      }
    }
    Mesh body = meshG.bake(dev);
    meshG.end();

    meshG.begin();
    meshG.setTint(Color.WHITE);
    for (int ly = 0; ly < chunkSize; ly++) {
      for (int lx = 0; lx < chunkSize; lx++) {
        BlockState state = wall ? chunk.getWall(lx, ly) : chunk.getBlock(lx, ly);
        if (shouldDraw(state) && !extension(state).isAnimatedRendering(state)) {
          draw(meshG, level, state, x0 + lx, y0 + ly, wall, true);
        }
      }
    }
    Mesh edge = meshG.bake(dev);
    meshG.end();
    return new ChunkMesh(body, edge);
  }

  private static boolean shouldDraw(@Nullable BlockState state) {
    if (state == null || state.isEmpty()) {
      return false;
    }
    BlockClientExtension extension = BlockClientExtension.get(state);
    return extension != null && extension.isVisible(state);
  }

  private static BlockClientExtension extension(BlockState state) {
    BlockClientExtension extension = BlockClientExtension.get(state);
    if (extension == null) {
      throw new IllegalStateException("No client extension registered for block state "
          + state.identity());
    }
    return extension;
  }

  private static void draw(Graphics g, Level level, BlockState state,
                           int x, int y, boolean wall, boolean edge) {
    BlockClientExtension extension = extension(state);
    if (wall) {
      if (edge) {
        extension.drawWallEdge(g, level, state, x, y);
      } else {
        extension.drawWall(g, level, state, x, y);
      }
    } else if (edge) {
      extension.drawBlockEdge(g, level, state, x, y);
    } else {
      extension.drawBlock(g, level, state, x, y);
    }
  }
}
