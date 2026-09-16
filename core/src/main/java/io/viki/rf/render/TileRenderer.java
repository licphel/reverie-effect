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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.ChunkPos;
import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.mesh.Mesh;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.gfx.util.ZeroCopyVertexStore;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.util.impl.MeshGraphics;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.registry.Registry;
import io.viki.rf.Registries;
import io.viki.rf.client.world.block.BlockClientExtension;
import io.viki.rf.client.world.block.StandardBlockClientExtension;
import io.viki.rf.world.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Tile renderer: block bodies from a full-texture random sample (all UV
 * coordinates scaled by {@link #SCALE} for the high-resolution atlas),
 * borders from Enchant's {@code BlockRenderBuffer.MakeBorderData}
 * translated verbatim. Block bodies are delegated to each block's
 * {@link BlockClientExtension}.
 *
 * <p>Drawing order: all block bodies first, then all border pieces, so a
 * border always covers the body of the tile it is drawn on (Enchant
 * border meshes). Same-material neighbours connect seamlessly (no
 * borders between them).
 */
public final class TileRenderer implements AutoCloseable {
  public static final boolean RENDER_EDGES = true;
  private static final int SCALE = 1;
  /* Full block texture, sampled 8x8 per tile. */
  /* Slope faces: 8x8 cells. Left-down (↗ high right), right-down
   * (↖ high left), left-up and right-up (ceilings). */
  /**
   * Meshes built per frame; a moving camera can otherwise slam the render
   * thread with a whole column of chunk builds in a single frame.
   */
  private static final int MAX_BUILDS_PER_FRAME = 1;
  /**
   * The half-edge slot table (the "edgeMap"): each of a tile's 4 edges is
   * split in two halves, so every tile has 8 slots and every geometric
   * half-edge is shared with exactly one neighbour. Each half-edge is owned
   * by exactly one tile and drawn once (dedup by ownership, no cross-side
   * checks), and its type is ET_NONE (no edge), ET_EDGE (straight) or
   * ET_BEND (corner bend when the diagonal tile is the same type).
   */
  private static final int SLOT_LL = 0; // 左边缘下半段
  private static final int SLOT_LU = 1; // 左边缘上半段
  private static final int SLOT_UL = 2; // 上边缘左半段
  private static final int SLOT_UR = 3; // 上边缘右半段
  private static final int SLOT_RU = 4; // 右边缘上半段
  private static final int SLOT_RL = 5; // 右边缘下半段
  private static final int SLOT_DR = 6; // 下边缘右半段
  private static final int SLOT_DL = 7; // 下边缘左半段

  private static final byte ET_NONE = 0;
  private static final byte ET_EDGE = 1;
  private static final byte ET_BEND = 2;
  private final BlockTextureAtlas textures;
  private final Device dev;
  /** Per-chunk retained block meshes, rebuilt when the block layer is dirty. */
  private final Long2ObjectMap<ChunkMesh> meshes = new Long2ObjectOpenHashMap<>();
  /** Per-chunk retained wall meshes, rebuilt when the wall layer is dirty. */
  private final Long2ObjectMap<ChunkMesh> wallMeshes = new Long2ObjectOpenHashMap<>();
  private final MeshGraphics meshG;
  private int buildsThisFrame;

  private TileRenderer(Device dev, BlockTextureAtlas textures) {
    this.dev = dev;
    this.textures = textures;
    meshG = new MeshGraphics(new ZeroCopyVertexStore(), dev);
  }

  // -- material mapping -----------------------------------------------------

  /** Creates a renderer from the registered client-side block extensions. */
  public static @Nullable TileRenderer create(Device dev, Registry<Block> blocks) {
    try {
      registerBuiltinExtensions();
      return new TileRenderer(dev, BlockTextureAtlas.open(dev, blocks));
    } catch (Exception e) {
      return null;
    }
  }

  private static void registerBuiltinExtensions() {
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

  /** Enchant Dropper.GetPlaceHash, verbatim. */
  private static long placeHash(int x, int y) {
    long hash = (long) x * 374761393 + (long) y * 668265263;
    hash = (hash ^ (hash >> 15)) * 2246822519L;
    hash = (hash ^ (hash >> 13)) * 3266489917L;
    hash ^= (hash >> 16);
    return hash;
  }

  /**
   * Enchant BlockRenderMode.PerfectVoxel: a full cube of a solid block
   * (not a platform or other partial shape).
   */
  private static boolean isPerfectVoxel(BlockState s, TileShape shape) {
    return shape == TileShape.FULL && s.shape() == Shape.SOLID;
  }

  /**
   * Enchant IsConnectable: two tiles are the same type only when they are
   * the same block kind with the same shape. Used for the corner-bend rule
   * (a same-type diagonal draws a bend); whether two flat faces merge is a
   * pure same-block check in {@link #classifySlot}.
   */
  private static boolean connectable(BlockState neighbor, TileShape neighborShape,
                                     BlockState self, TileShape selfShape) {
    return neighbor.block() == self.block() && neighborShape == selfShape;
  }

  /**
   * Whether the tile's own shape has a flat face on the given half-edge
   * slot: full cubes on all eight, carved shapes only on their flat faces.
   * A half brick's top sits at y+0.5 (UL/UR); its sides only reach the
   * lower halves (LL/RL).
   */
  private static boolean slotSolid(TileShape shape, int slot) {
    return switch (shape) {
      case TileShape.FULL -> true;
      case TileShape.HALF_BRICK ->
          slot == SLOT_LL || slot == SLOT_UL || slot == SLOT_UR || slot == SLOT_RL
              || slot == SLOT_DR || slot == SLOT_DL;
      case TileShape.HALF_BRICK_UP ->
          slot == SLOT_LU || slot == SLOT_UL || slot == SLOT_UR || slot == SLOT_RU
              || slot == SLOT_DR || slot == SLOT_DL;
      case TileShape.SLOPE_LEFT_DOWN -> slot == SLOT_RU || slot == SLOT_RL || slot == SLOT_DR || slot == SLOT_DL;
      case TileShape.SLOPE_RIGHT_DOWN -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_DR || slot == SLOT_DL;
      case TileShape.SLOPE_LEFT_UP -> slot == SLOT_UL || slot == SLOT_UR || slot == SLOT_RU || slot == SLOT_RL;
      case TileShape.SLOPE_RIGHT_UP -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_UL || slot == SLOT_UR;
    };
  }

  /**
   * Whether the neighbouring tile has a flat face on the same world segment
   * as this half-edge. Vertical segments compare the neighbour's side faces
   * (a half brick's side only reaches the lower half); horizontal segments
   * sit on the neighbour's bottom/top line, and nothing is flush with a
   * half brick's mid-height top ({@code brickTop}) or an upper half brick's
   * mid-height bottom ({@code brickBottom}) — both sit at y+0.5, where no
   * neighbour tile has a face — so those edges are always this tile's own.
   */
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

  /**
   * 该形状在该槽位是否有折弯件：立方体四个角；斜面只在其全高侧转角画折弯
   * （左下/右下/左上的斜面各一个角，右上的斜面两个角）。半砖没有任何折弯
   * 件——顶部两角的轮廓在对角处断开，左右两侧只到下半段也没有角。
   */
  private static boolean bendEligible(TileShape shape, int slot) {
    return switch (shape) {
      case TileShape.FULL -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_UL || slot == SLOT_RU;
      case TileShape.HALF_BRICK, TileShape.HALF_BRICK_UP -> false;
      case TileShape.SLOPE_LEFT_DOWN -> slot == SLOT_RU;
      case TileShape.SLOPE_RIGHT_DOWN -> slot == SLOT_LL || slot == SLOT_LU;
      case TileShape.SLOPE_LEFT_UP -> slot == SLOT_UL || slot == SLOT_RU;
      case TileShape.SLOPE_RIGHT_UP -> slot == SLOT_LL || slot == SLOT_LU || slot == SLOT_UL;
    };
  }

  /**
   * 折弯件的外沿笔画必须落在对角同型方块的平面上，否则笔画悬空：
   * LL 的外沿是对角的 UR 半段，LU 是对角的 DR，UL 是对角的 RL，
   * RU 是对角的 DL。外沿覆盖的半段是否被同块平面贴合吃掉（叠在上坡
   * 顶面上的整块方块）由 {@link #bendStrokeMerged} 检查。
   */
  private static boolean bendValid(TileShape diagShape, int slot) {
    return switch (slot) {
      case SLOT_LL -> slotSolid(diagShape, SLOT_UR);
      case SLOT_LU -> slotSolid(diagShape, SLOT_DR);
      case SLOT_UL -> slotSolid(diagShape, SLOT_RL);
      case SLOT_RU -> slotSolid(diagShape, SLOT_DL);
      default -> true;
    };
  }

  /**
   * 折弯件的外沿笔画覆盖对角方块的一个半段（LL→UR、LU→DR、UL→RL、
   * RU→DL）。该半段是中间方块（我与对角之间的邻块）与对角之间的平面
   * 段；当两者同块且在该段都有实体面（如整块方块叠在上坡顶面上）时，
   * 该段被平面贴合吃掉，外沿笔画会画在贴合的平面上，是悬空笔画——此时
   * 画直边而不是折弯。折弯生效时对角与我的方块同块，故用对角与中间方块
   * 比较即可。
   */
  private static boolean bendStrokeMerged(Level level, int x, int y, BlockState diag, int slot,
                                          boolean wall) {
    int tx;
    int ty;
    int tslot;
    switch (slot) {
      case SLOT_LL -> {
        tx = x - 1;
        ty = y;
        tslot = SLOT_DR;
      }
      case SLOT_LU -> {
        tx = x - 1;
        ty = y;
        tslot = SLOT_UR;
      }
      case SLOT_UL -> {
        tx = x;
        ty = y + 1;
        tslot = SLOT_LU;
      }
      default -> { // SLOT_RU
        tx = x + 1;
        ty = y;
        tslot = SLOT_UL;
      }
    }
    BlockState t = wall ? level.getWallIfLoaded(tx, ty) : level.getBlockIfLoaded(tx, ty);
    if (t == null || t.isEmpty() || t.block() != diag.block()) {
      return false;
    }
    TileShape shape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(tx, ty));
    return slotSolid(shape, tslot);
  }

  /**
   * Classifies one of the tile's 8 half-edge slots and returns its type
   * (ET_NONE when another tile owns that geometric half-edge, so every
   * half-edge is drawn exactly once). Ownership: a same-block flat-face
   * contact has no edge at all (a slope or half brick never extends an
   * edge into its own block's flat face); otherwise the solid side, or
   * the higher registry index when both sides are solid, owns the
   * half-edge.
   *
   * The type is a bend when the diagonal tile is the same type, this
   * shape draws a bend on the slot ({@link #bendEligible}), the bend's
   * outer stroke lands on the diagonal's flat face ({@link #bendValid})
   * and that face segment is not flat-merged with the tile in between
   * ({@link #bendStrokeMerged}); a bend with a phantom stroke falls back
   * to a straight edge. Slots without a bend piece skip only when the
   * diagonal's bend covers this half-edge ({@code classifySlot(diag,
   * compSlot) == ET_BEND}); the bottom bands skip the same way (down-left
   * covers via the down-right diagonal's LU bend, down-right via the
   * down-left diagonal's RU bend). Half bricks never bend at their top
   * corners — the outline breaks at the diagonal whether the diagonal is
   * a half brick or a full cube.
   */
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
      default -> { // SLOT_DR, SLOT_DL
        nx = x;
        ny = y - 1;
      }
    }
    BlockState nb = wall ? level.getWallIfLoaded(nx, ny) : level.getBlockIfLoaded(nx, ny);
    TileShape nbShape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(nx, ny));
    boolean me = slotSolid(shape, slot);
    boolean neighbor = nb != null && !nb.isEmpty()
        && neighborSlotSolid(nbShape, slot, shape == TileShape.HALF_BRICK,
            shape == TileShape.HALF_BRICK_UP);
    if (!me && !neighbor) {
      return ET_NONE;
    }
    if (me && neighbor) {
      if (self.block() == nb.block()) {
        return ET_NONE; // 同方块平面贴合：不延伸边缘
      }
      if (self.block().registryIndex() < nb.block().registryIndex()) {
        return ET_NONE; // 对方拥有
      }
    } else if (!me) {
      return ET_NONE; // 对方拥有
    }
    // 我拥有：对角线同型 → 折弯，否则直边
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
      default -> { // SLOT_RL, SLOT_DR
        dx = x + 1;
        dy = y - 1;
      }
    }
    BlockState diag = wall ? level.getWallIfLoaded(dx, dy) : level.getBlockIfLoaded(dx, dy);
    TileShape diagShape = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(dx, dy));
    if (diag == null || !connectable(diag, diagShape, self, shape)) {
      return ET_EDGE;
    }
    // 对角同型：本形状有折弯件、外沿落在对角平面上且外沿覆盖的半段没被
    // 同块平面贴合吃掉 → 折弯；否则仅当对角的折弯恰好覆盖本半段时跳过
    // （上右半段 ← 右上对角的 LL 折弯，右下半段 ← 右下对角的 UL 折弯，
    // 下右半段 ← 右下对角的 LU 折弯，下左半段 ← 左下对角的 RU 折弯），
    // 其余情况画直边。下边缘两半段只有在对角折弯确实覆盖时才跳过，否则
    // 丢失边缘。
    if (bendEligible(shape, slot) && bendValid(diagShape, slot)
        && !bendStrokeMerged(level, x, y, diag, slot, wall)) {
      return ET_BEND;
    }
    return switch (slot) {
      case SLOT_UR ->
          classifySlot(level, dx, dy, diag, diagShape, SLOT_LL, wall) == ET_BEND ? ET_NONE : ET_EDGE;
      case SLOT_RL ->
          classifySlot(level, dx, dy, diag, diagShape, SLOT_UL, wall) == ET_BEND ? ET_NONE : ET_EDGE;
      case SLOT_DR ->
          classifySlot(level, dx, dy, diag, diagShape, SLOT_LU, wall) == ET_BEND ? ET_NONE : ET_EDGE;
      case SLOT_DL ->
          classifySlot(level, dx, dy, diag, diagShape, SLOT_RU, wall) == ET_BEND ? ET_NONE : ET_EDGE;
      default -> ET_EDGE;
    };
  }

  /**
   * A half brick's top-left bend draws its extension piece (37,13) only
   * when the tile to the left has no top face: with a full-height
   * neighbour the piece's right stroke would be drawn twice, and with a
   * same-block neighbour its top stroke would extend an edge into the
   * merged flat faces.
   */
  private static boolean brickLeftWrap(Level level, int x, int y, boolean wall) {
    BlockState left = wall ? level.getWallIfLoaded(x - 1, y) : level.getBlockIfLoaded(x - 1, y);
    if (left == null || left.isEmpty()) {
      return true;
    }
    TileShape s = wall ? TileShape.FULL
        : TileShape.byId(level.getBlockShapeIfLoaded(x - 1, y));
    return s != TileShape.FULL && s != TileShape.SLOPE_LEFT_UP && s != TileShape.SLOPE_RIGHT_UP;
  }

  /** Releases all chunk meshes, the shared mesh builder and the block texture atlas. */
  @Override
  public void close() {
    for (ChunkMesh cm : meshes.values()) {
      cm.close();
    }
    for (ChunkMesh cm : wallMeshes.values()) {
      cm.close();
    }
    meshes.clear();
    wallMeshes.clear();
    meshG.close();
    textures.close();
  }

  /**
   * Releases the retained meshes of one chunk (on chunk unload); a
   * reloaded chunk must not reuse geometry of the previous instance.
   */
  public void unloadChunk(ChunkPos pos) {
    ChunkMesh cm = meshes.remove(pos.asLong());
    if (cm != null) {
      cm.close();
    }
    ChunkMesh wm = wallMeshes.remove(pos.asLong());
    if (wm != null) {
      wm.close();
    }
  }

  /**
   * Renders the visible chunks from their retained meshes (rebuilt when
   * dirty); animated blocks are drawn every frame in immediate mode.
   * Chunks not yet published by the player-driven stream are skipped.
   * Borders are part of the chunk mesh and drawn after the bodies.
   */
  public void render(BatchedGraphics g, Level level, Camera2D cam) {
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    g.setTint(Color.WHITE);
    buildsThisFrame = 0;

    int minCx = (int) Math.floor((cp.x() - vw / 2F) / cs);
    int maxCx = (int) Math.floor((cp.x() + vw / 2F) / cs);
    int minCy = (int) Math.floor((cp.y() - vh / 2F) / cs);
    int maxCy = (int) Math.floor((cp.y() + vh / 2F) / cs);

    // pass 1: draw every chunk body (animated block bodies too); a chunk whose
    // retained mesh is not built yet (build budget exhausted) falls back to
    // immediate mode so the moving camera never hitches on a whole column of
    // new chunks at once
    for (int cx = minCx; cx <= maxCx; cx++) {
      for (int cy = minCy; cy <= maxCy; cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk ck = level.getChunk(pos);
        if (ck == null) {
          continue;
        }
        ChunkMesh cm = ensureFrontMesh(level, pos);
        if (cm != null) {
          g.drawMesh(cm.body());
        } else {
          drawChunkImmediate(g, level, ck, pos, false, false);
        }
        for (int ly = 0; ly < cs; ly++) {
          for (int lx = 0; lx < cs; lx++) {
            BlockState s = ck.getBlock(lx, ly);
            BlockClientExtension extension = s == null ? null : BlockClientExtension.get(s);
            if (s == null || s.isEmpty() || extension == null
                || !extension.isAnimatedRendering(s)) {
              continue;
            }
            drawBody(g, level, s, cx * cs + lx, cy * cs + ly, false);
          }
        }
      }
    }

    // pass 2: all borders on top, so a neighbouring chunk's body never
    // occludes this chunk's border at chunk boundaries
    for (int cx = minCx; cx <= maxCx; cx++) {
      for (int cy = minCy; cy <= maxCy; cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk ck = level.getChunk(pos);
        if (ck == null) {
          continue;
        }
        ChunkMesh cm = meshes.get(pos.asLong());
        if (cm != null) {
          g.drawMesh(cm.border());
        } else {
          drawChunkImmediate(g, level, ck, pos, true, false);
        }
        for (int ly = 0; ly < cs; ly++) {
          for (int lx = 0; lx < cs; lx++) {
            BlockState s = ck.getBlock(lx, ly);
            BlockClientExtension extension = s == null ? null : BlockClientExtension.get(s);
            if (s == null || s.isEmpty() || extension == null
                || !extension.isAnimatedRendering(s)) {
              continue;
            }
            drawEdges(g, level, cx * cs + lx, cy * cs + ly,
                TileShape.byId(ck.getBlockShape(lx, ly)), false);
          }
        }
      }
    }
  }

  // -- rendering -----------------------------------------------------------

  /**
   * Immediate-mode fallback for a chunk whose retained mesh is not built yet
   * (the per-frame build budget is exhausted): draws the chunk's tiles directly
   * so it stays visible while the retained meshes catch up.
   *
   * @param g     the batched renderer
   * @param level the level
   * @param ck    the chunk
   * @param pos   the chunk position
   * @param edges whether to draw the tile edges (pass 2) or the bodies (pass 1)
   * @param wall  whether to draw the wall layer instead of the block layer
   */
  private void drawChunkImmediate(BatchedGraphics g, Level level, Chunk ck, ChunkPos pos,
                                  boolean edges, boolean wall) {
    int cs = ChunkPos.SIZE;
    int x0 = pos.x() * cs;
    int y0 = pos.y() * cs;
    for (int ly = 0; ly < cs; ly++) {
      for (int lx = 0; lx < cs; lx++) {
        BlockState s = wall ? ck.getWall(lx, ly) : ck.getBlock(lx, ly);
        if (s == null || s.isEmpty()) {
          continue;
        }
        if (edges) {
          drawEdges(g, level, x0 + lx, y0 + ly,
              wall ? TileShape.FULL : TileShape.byId(ck.getBlockShape(lx, ly)), wall);
        } else {
          drawBody(g, level, s, x0 + lx, y0 + ly, wall);
        }
      }
    }
  }

  /**
   * Returns the retained block mesh of a chunk, rebuilding it when the block layer is
   * dirty — or {@code null} when the per-frame build budget is exhausted, so the
   * caller draws the chunk immediately and the mesh is built on a later frame.
   */
  private @Nullable ChunkMesh ensureFrontMesh(Level level, ChunkPos pos) {
    Chunk ck = level.getChunk(pos);
    if (ck == null) return null;
    ChunkMesh cm = meshes.get(pos.asLong());
    if (cm != null && !ck.frontDirty) {
      return cm;
    }
    if (buildsThisFrame >= MAX_BUILDS_PER_FRAME) {
      return null;
    }
    if (cm != null) {
      cm.close();
    }
    cm = buildChunkMesh(level, pos);
    meshes.put(pos.asLong(), cm);
    ck.frontDirty = false;
    buildsThisFrame++;
    return cm;
  }

  /**
   * Returns the retained wall mesh of a chunk, rebuilding it when the wall layer is
   * dirty — or {@code null} when the per-frame build budget is exhausted (see
   * {@link #ensureFrontMesh(Level, ChunkPos)}).
   */
  private @Nullable ChunkMesh ensureBackMesh(Level level, ChunkPos pos) {
    Chunk ck = level.getChunk(pos);
    if (ck == null) return null;
    ChunkMesh cm = wallMeshes.get(pos.asLong());
    if (cm != null && !ck.backDirty) {
      return cm;
    }
    if (buildsThisFrame >= MAX_BUILDS_PER_FRAME) {
      return null;
    }
    if (cm != null) {
      cm.close();
    }
    cm = buildWallMesh(level, pos);
    wallMeshes.put(pos.asLong(), cm);
    ck.backDirty = false;
    buildsThisFrame++;
    return cm;
  }

  /**
   * Builds the retained block mesh of a chunk: bodies then borders
   * (Enchant ChunkMesh), skipping animated blocks.
   */
  private ChunkMesh buildChunkMesh(Level level, ChunkPos pos) {
    Chunk chunk = level.getChunk(pos);
    if (chunk == null) {
      throw new IllegalStateException("cannot build a mesh for an unloaded chunk: " + pos);
    }
    int cs = ChunkPos.SIZE;
    int x0 = pos.x() * cs;
    int y0 = pos.y() * cs;

    meshG.begin();
    meshG.setTint(Color.WHITE);
    for (int ly = 0; ly < cs; ly++) {
      for (int lx = 0; lx < cs; lx++) {
        BlockState s = chunk.getBlock(lx, ly);
        BlockClientExtension extension = s == null ? null : BlockClientExtension.get(s);
        if (s == null || s.isEmpty() || extension == null
            || extension.isAnimatedRendering(s)) {
          continue;
        }
        drawBody(meshG, level, s, x0 + lx, y0 + ly, false);
      }
    }
    Mesh body = meshG.bake(dev);
    meshG.end();

    meshG.begin();
    meshG.setTint(Color.WHITE);
    for (int ly = 0; ly < cs; ly++) {
      for (int lx = 0; lx < cs; lx++) {
        BlockState s = chunk.getBlock(lx, ly);
        BlockClientExtension extension = s == null ? null : BlockClientExtension.get(s);
        if (s == null || s.isEmpty() || extension == null
            || extension.isAnimatedRendering(s)) {
          continue;
        }
        drawEdges(meshG, level, x0 + lx, y0 + ly,
            TileShape.byId(chunk.getBlockShape(lx, ly)), false);
      }
    }
    Mesh border = meshG.bake(dev);
    meshG.end();

    return new ChunkMesh(body, border);
  }

  /**
   * Builds the retained wall mesh of a chunk: the same body + border
   * logic as blocks, queried from the wall layer.
   */
  private ChunkMesh buildWallMesh(Level level, ChunkPos pos) {
    Chunk chunk = level.getChunk(pos);
    if (chunk == null) {
      throw new IllegalStateException("cannot build a wall mesh for an unloaded chunk: " + pos);
    }
    int cs = ChunkPos.SIZE;
    int x0 = pos.x() * cs;
    int y0 = pos.y() * cs;

    meshG.begin();
    meshG.setTint(Color.WHITE);
    for (int ly = 0; ly < cs; ly++) {
      for (int lx = 0; lx < cs; lx++) {
        BlockState w = chunk.getWall(lx, ly);
        BlockClientExtension extension = w == null ? null : BlockClientExtension.get(w);
        if (w == null || w.isEmpty() || extension == null
            || extension.isAnimatedRendering(w)) {
          continue;
        }
        drawBody(meshG, level, w, x0 + lx, y0 + ly, true);
      }
    }
    Mesh body = meshG.bake(dev);
    meshG.end();

    meshG.begin();
    meshG.setTint(Color.WHITE);
    for (int ly = 0; ly < cs; ly++) {
      for (int lx = 0; lx < cs; lx++) {
        BlockState w = chunk.getWall(lx, ly);
        BlockClientExtension extension = w == null ? null : BlockClientExtension.get(w);
        if (w == null || w.isEmpty() || extension == null
            || extension.isAnimatedRendering(w)) {
          continue;
        }
        drawEdges(meshG, level, x0 + lx, y0 + ly,
            TileShape.FULL, true);
      }
    }
    Mesh border = meshG.bake(dev);
    meshG.end();

    return new ChunkMesh(body, border);
  }

  /**
   * Renders the wall layer from the retained meshes, in the wall render
   * pass. All wall bodies first, then all borders (same chunk-boundary
   * rule as blocks).
   */
  public void renderWalls(BatchedGraphics g, Level level, Camera2D cam) {
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    g.setTint(Color.WHITE);
    buildsThisFrame = 0;

    int minCx = (int) Math.floor((cp.x() - vw / 2F) / cs);
    int maxCx = (int) Math.floor((cp.x() + vw / 2F) / cs);
    int minCy = (int) Math.floor((cp.y() - vh / 2F) / cs);
    int maxCy = (int) Math.floor((cp.y() + vh / 2F) / cs);

    for (int cx = minCx; cx <= maxCx; cx++) {
      for (int cy = minCy; cy <= maxCy; cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk ck = level.getChunk(pos);
        if (ck == null) {
          continue;
        }
        ChunkMesh cm = ensureBackMesh(level, pos);
        if (cm != null) {
          g.drawMesh(cm.body());
        } else {
          drawChunkImmediate(g, level, ck, pos, false, true);
        }
      }
    }
    for (int cx = minCx; cx <= maxCx; cx++) {
      for (int cy = minCy; cy <= maxCy; cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk ck = level.getChunk(pos);
        if (ck == null) {
          continue;
        }
        ChunkMesh cm = wallMeshes.get(pos.asLong());
        if (cm != null) {
          g.drawMesh(cm.border());
        } else {
          drawChunkImmediate(g, level, ck, pos, true, true);
        }
      }
    }
  }

  private static void drawBody(
      BatchedGraphics g, Level level, BlockState state, int x, int y, boolean wall) {
    BlockClientExtension extension = BlockClientExtension.get(state);
    if (extension == null || !extension.isVisible(state)) {
      return;
    }
    if (wall) {
      extension.drawWall(g, level, state, x, y);
    } else {
      extension.draw(g, level, state, x, y);
    }
  }

  /**
   * Enchant BlockRenderBuffer.MakeBorderData, translated verbatim. Blocks and walls share the
   * same border logic; walls never carry a shape byte.
   *
   * <p>Edge decisions come from the half-edge slot table ("edgeMap"):
   * each tile has 8 slots (4 edges × 2 halves), every geometric half-edge
   * is owned by exactly one tile and drawn once (no dedup checks between
   * neighbours), and the type (straight vs corner bend) follows the
   * same-type diagonal rule.
   */
  private void drawEdges(
      BatchedGraphics g, Level level, int x, int y, TileShape shape, boolean wall) {
    if (!RENDER_EDGES) {
      return;
    }
    BlockState self = wall ? level.getWallIfLoaded(x, y) : level.getBlockIfLoaded(x, y);
    if (self == null || self.isEmpty()) {
      return;
    }
    BlockClientExtension extension = BlockClientExtension.get(self);
    if (!(extension instanceof StandardBlockClientExtension standard)) {
      return;
    }
    TexturePart tex = standard.texture();
    if (tex == null) {
      return;
    }
    // carved shapes are PARTIAL (liquids fill the gaps) but still draw
    // edges on their flat faces; a full cube of a non-solid block
    // (platform) draws none
    if (!isPerfectVoxel(self, shape) && shape == TileShape.FULL) {
      return;
    }
    long hash = placeHash(x, y);
    int rdu = (int) (Math.abs(hash) % 4) * 13 * SCALE;
    int rdu2 = (int) (Math.abs(hash + 1) % 4) * 9 * SCALE;
    for (int slot = SLOT_LL; slot <= SLOT_DL; slot++) {
      byte type = classifySlot(level, x, y, self, shape, slot, wall);
      if (type != ET_NONE) {
        drawSlot(g, tex, x, y, shape, slot, type, rdu, rdu2, level, wall);
      }
    }
  }

  /**
   * Draws one owned half-edge slot: the straight piece, or the corner bend
   * (an L-piece wrapping the corner when the diagonal is same-type). A
   * half brick's top edge sits at y+0.5, so its UL/UR pieces (and bends)
   * are drawn half a tile lower than a full cube's.
   */
  private static void drawSlot(BatchedGraphics g, TexturePart tex, int x, int y,
                               TileShape shape, int slot, byte type, int rdu, int rdu2,
                               Level level, boolean wall) {
    if (shape == TileShape.HALF_BRICK && (slot == SLOT_UL || slot == SLOT_UR)) {
      if (slot == SLOT_UL) {
        if (type == ET_BEND) {
          if (brickLeftWrap(level, x, y, wall)) {
            g.drawTexture(tex, x - 0.5F, y + 0.5F, 0.5F, 0.5F, rdu2 + 37 * SCALE, 13 * SCALE, 4 * SCALE, 4 * SCALE);
          }
          g.drawTexture(tex, x, y + 0.5F, 0.5F, 0.5F, rdu2 + 33 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(tex, x, y + 0.5F, 0.5F, 0.25F, rdu + 35 * SCALE, 0, 4 * SCALE, 2 * SCALE);
        }
      } else if (type == ET_BEND) {
        g.drawTexture(tex, x + 0.5F, y + 0.5F, 0.5F, 0.5F, rdu2 + 37 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
      } else {
        g.drawTexture(tex, x + 0.5F, y + 0.5F, 0.5F, 0.25F, rdu + 39 * SCALE, 0, 4 * SCALE, 2 * SCALE);
      }
      return;
    }
    switch (slot) {
      case SLOT_LL -> {
        if (type == ET_BEND) {
          g.drawTexture(tex, x - 0.5F, y, 0.5F, 0.5F, rdu2 + 37 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          // 半砖只占格子下半段，侧面用完整方块侧面纹理的上半部分（v-4）
          g.drawTexture(tex, x - 0.25F, y, 0.25F, 0.5F, rdu + 33 * SCALE,
              shape == TileShape.HALF_BRICK ? 2 * SCALE : 6 * SCALE, 2 * SCALE, 4 * SCALE);
        }
      }
      case SLOT_LU -> {
        if (type == ET_BEND) {
          g.drawTexture(tex, x - 0.5F, y + 0.5F, 0.5F, 0.5F, rdu2 + 37 * SCALE, 13 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(tex, x - 0.25F, y + 0.5F, 0.25F, 0.5F, rdu + 33 * SCALE, 2 * SCALE, 2 * SCALE, 4 * SCALE);
        }
      }
      case SLOT_UL -> {
        if (type == ET_BEND) {
          g.drawTexture(tex, x, y + 1F, 0.5F, 0.5F, rdu2 + 33 * SCALE, 17 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(tex, x, y + 1F, 0.5F, 0.25F, rdu + 35 * SCALE, 0, 4 * SCALE, 2 * SCALE);
        }
      }
      case SLOT_UR -> g.drawTexture(tex, x + 0.5F, y + 1F, 0.5F, 0.25F, rdu + 39 * SCALE, 0, 4 * SCALE, 2 * SCALE);
      case SLOT_RU -> {
        if (type == ET_BEND) {
          g.drawTexture(tex, x + 1F, y + 0.5F, 0.5F, 0.5F, rdu2 + 33 * SCALE, 13 * SCALE, 4 * SCALE, 4 * SCALE);
        } else {
          g.drawTexture(tex, x + 1F, y + 0.5F, 0.25F, 0.5F, rdu + 43 * SCALE, 2 * SCALE, 2 * SCALE, 4 * SCALE);
        }
      }
      case SLOT_RL -> g.drawTexture(tex, x + 1F, y, 0.25F, 0.5F, rdu + 43 * SCALE,
          shape == TileShape.HALF_BRICK ? 2 * SCALE : 6 * SCALE, 2 * SCALE, 4 * SCALE);
      case SLOT_DR -> g.drawTexture(tex, x + 0.5F, bottomEdgeY(shape, y), 0.5F, 0.25F, rdu + 39 * SCALE, 10 * SCALE, 4 * SCALE, 2 * SCALE);
      default -> g.drawTexture(tex, x, bottomEdgeY(shape, y), 0.5F, 0.25F, rdu + 35 * SCALE, 10 * SCALE, 4 * SCALE, 2 * SCALE); // SLOT_DL
    }
  }

  /** The Y of a tile's bottom edge strip: an upper half brick's bottom
   * sits at y+0.5, every other shape's at the tile bottom (y-0.25 places
   * the 0.25-tall strip exactly under the edge). */
  private static float bottomEdgeY(TileShape shape, float y) {
    return shape == TileShape.HALF_BRICK_UP ? y + 0.25F : y - 0.25F;
  }

}
