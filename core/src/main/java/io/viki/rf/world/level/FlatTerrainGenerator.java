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

import io.viki.momentum.math.noise.NoiseGenerator;
import io.viki.momentum.math.noise.PerlinNoise;
import io.viki.momentum.math.random.Xoroshiro128Random;
import io.viki.rf.Registries;
import io.viki.rf.world.block.Block;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.util.ChunkPos;
import io.viki.rf.world.util.BlockPos;
import org.jspecify.annotations.NullMarked;

import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;

/**
 * Simple flat terrain with grass → dirt → stone layers.
 */
@NullMarked
public final class FlatTerrainGenerator implements ChunkGenerator {
  private static final int TREE_ATTEMPTS_PER_CHUNK = 2;
  private static final int TREE_CHANCE_DENOMINATOR = 8;
  private static final int TREE_MIN_HEIGHT = 8;
  private static final int TREE_HEIGHT_RANGE = 8;
  private static final long TREE_RANDOM_SALT = 0x9E3779B97F4A7C15L;
  private static final long TREE_CHUNK_X_SALT = 0x632BE59BD9B4E019L;
  private static final long TREE_CHUNK_Y_SALT = 0xC6BC279692B5CC83L;

  private final int groundY;

  public FlatTerrainGenerator(int groundY) {
    this.groundY = groundY;
  }

  private static BlockState state(Block block) {
    return block.defaultState();
  }

  @Override
  public void generate(ChunkPos chunkPos, long seed, GenerationQueue queue) {
    NoiseGenerator noise = new PerlinNoise(new Xoroshiro128Random(seed));

    var air = state(Registries.AIR);
    var grass = state(Registries.GRASS);
    var dirt = state(Registries.DIRT);
    var stone = state(Registries.STONE);
    var plat = state(Registries.PLATFORM);
    var wall = state(Registries.WALL);
    var pole = state(Registries.COLORFUL);

    String source = "flat-terrain@" + chunkPos.x() + "," + chunkPos.y();
    long sequence = 0;
    for (int lx = 0; lx < ChunkPos.SIZE; lx++) {
      for (int ly = 0; ly < ChunkPos.SIZE; ly++) {
        int wx = chunkPos.x() * ChunkPos.SIZE + lx;
        int wy = chunkPos.y() * ChunkPos.SIZE + ly;

        int groundY = this.groundY + (int)noise.generate(wx / 24.0F) * 10;

        BlockState s = null;
        byte shape = TileShape.FULL.id();

        // light sources: WALL at (2, groundY+2), POLE at plateau
        if (wx == 2 && wy == groundY + 2) {
          s = wall;
        }
        if (wx == 15 && wy == groundY + 8) {
          s = pole;
        }

        if (wx <= 0) {
          if (wy == groundY + 5) {
            s = plat;
          }
        }

        // ↗ uphill: surface rises from (5,groundY) to (12,groundY+7)
        if (wx >= 5 && wx <= 12) {
          int surfaceY = groundY + (wx - 5);
          if (wy == surfaceY) {
            s = grass;
            shape = TileShape.SLOPE_LEFT_DOWN.id();
          } else if (wy < surfaceY && wy >= groundY - 4) {
            s = dirt;
          } else if (wy < groundY - 4) {
            s = stone;
          } else {
            s = air;
          }
        }
        // plateau between ramps
        else if (wx >= 13 && wx <= 17) {
          int surfaceY = groundY + 7;
          if (wy == surfaceY) {
            s = grass;
          } else if (wy < surfaceY && wy >= groundY - 4) {
            s = dirt;
          } else if (wy < groundY - 4) {
            s = stone;
          } else {
            s = air;
          }
        }
        // ↘ downhill: surface descends from (18,groundY+7) to (25,groundY)
        else if (wx >= 18 && wx <= 24) {
          int surfaceY = groundY + 7 - (wx - 18);
          if (wy == surfaceY) {
            s = grass;
            shape = TileShape.SLOPE_RIGHT_DOWN.id();
          } else if (wy < surfaceY && wy >= groundY - 4) {
            s = dirt;
          } else if (wy < groundY - 4) {
            s = stone;
          } else {
            s = air;
          }
        }
        // default flat terrain
        if (s == null) {
          if (wy > groundY) {
            s = air;
          } else if (wy == groundY) {
            s = grass;
          } else if (wy > groundY - 5) {
            s = dirt;
          } else {
            s = stone;
          }
        }

        double v = noise.generate(wx / 32.0, wy / 32.0);
        if (v < 0.2F) {
          s = air;
        }

        queue.add(new GeneratedBlock(wx, wy, 0, source, sequence++,
            GenerationFlags.SILENT, s));
        queue.add(new GeneratedShape(wx, wy, 0, source, sequence++,
            GenerationFlags.SILENT, shape));

        // background walls behind all underground tiles
        if (wy < groundY && s != air) {
          queue.add(new GeneratedWall(wx, wy, 0, source, sequence++,
              GenerationFlags.SILENT, stone));
        }
      }
    }
  }

  /** Runs the same step production on a worker thread for streaming loads. */
  @Override
  public CompletableFuture<Void> generateAsync(ChunkPos chunkPos, long seed, GenerationQueue queue) {
    return CompletableFuture.runAsync(() -> generate(chunkPos, seed, queue));
  }

  /** Adds deterministic decorative trees after the chunk's terrain is ready. */
  @Override
  public void populate(Level level, ChunkPos chunkPos, long seed) {
    NoiseGenerator noise = new PerlinNoise(new Xoroshiro128Random(seed));
    SplittableRandom random = new SplittableRandom(seed ^ TREE_RANDOM_SALT
        ^ ((long) chunkPos.x() * TREE_CHUNK_X_SALT)
        ^ ((long) chunkPos.y() * TREE_CHUNK_Y_SALT));
    var tree = Registries.BIRCH_TREE.get();

    for (int attempt = 0; attempt < TREE_ATTEMPTS_PER_CHUNK; attempt++) {
      int worldX = chunkPos.x() * ChunkPos.SIZE + random.nextInt(ChunkPos.SIZE);
      if (random.nextInt(TREE_CHANCE_DENOMINATOR) != 0) {
        continue;
      }

      int rootY = surfaceY(worldX, noise) + 1;
      var root = new BlockPos(worldX, rootY);
      if (!root.toChunkPos().equals(chunkPos)) {
        continue;
      }
      int height = TREE_MIN_HEIGHT + random.nextInt(TREE_HEIGHT_RANGE);
      level.objects().place(tree, root, height);
    }
  }

  private int surfaceY(int worldX, NoiseGenerator noise) {
    int baseGroundY = groundY(worldX, noise);
    if (worldX >= 5 && worldX <= 12) {
      return baseGroundY + (worldX - 5);
    }
    if (worldX >= 13 && worldX <= 17) {
      return baseGroundY + 7;
    }
    if (worldX >= 18 && worldX <= 24) {
      return baseGroundY + 7 - (worldX - 18);
    }
    return baseGroundY;
  }

  private int groundY(int worldX, NoiseGenerator noise) {
    return this.groundY + (int) noise.generate(worldX / 24.0F) * 10;
  }
}
