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

package io.viki.rf.world.fluid;

import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;

/**
 * Per-chunk liquid layer: one type byte and one unsigned level byte per tile
 * ({@code 255} is full and the minimum non-empty amount is 1).
 *
 * <p>Coordinates are masked into the chunk (world coordinates work like on
 * {@code MappingArray}).
 */
@NullMarked
public final class LiquidMap {
  private static final int MASK = ChunkPos.SIZE - 1;
  private static final int CELL_COUNT = ChunkPos.SIZE * ChunkPos.SIZE;
  private static final int BYTES_PER_CELL = 2;
  private static final int TYPE_OFFSET = 0;
  private static final int LEVEL_OFFSET = 1;
  public static final int RAW_SIZE = CELL_COUNT * BYTES_PER_CELL;
  private final byte[] data = new byte[RAW_SIZE];

  private static int idx(int wx, int wy) {
    return (((wy & MASK) << 4) | (wx & MASK)) * BYTES_PER_CELL;
  }

  /** The liquid level of a tile ({@code 0} = empty, {@code 255} = full). */
  public int level(int wx, int wy) {
    return data[idx(wx, wy) + LEVEL_OFFSET] & 0xFF;
  }

  /** The liquid id of a tile, see {@link Liquids#byId(int)}. */
  public byte liquidType(int wx, int wy) {
    return data[idx(wx, wy) + TYPE_OFFSET];
  }

  /** Sets the liquid level of a tile; {@code <= 0} clears it. */
  public void setLevel(int wx, int wy, int v) {
    int i = idx(wx, wy);
    if (v <= 0) {
      data[i + LEVEL_OFFSET] = 0;
      data[i + TYPE_OFFSET] = 0;
    } else {
      checkLevel(v);
      data[i + LEVEL_OFFSET] = (byte) v;
    }
  }

  /** Sets the liquid type of a tile. */
  public void setType(int wx, int wy, byte id) {
    data[idx(wx, wy) + TYPE_OFFSET] = id;
  }

  /** Sets the liquid of a tile; levels {@code <= 0} clear it. */
  public void set(int wx, int wy, Liquid liquid, int level) {
    int i = idx(wx, wy);
    if (level <= 0) {
      data[i + LEVEL_OFFSET] = 0;
      data[i + TYPE_OFFSET] = 0;
    } else {
      checkLevel(level);
      data[i + LEVEL_OFFSET] = (byte) level;
      data[i + TYPE_OFFSET] = liquid.id();
    }
  }

  public byte[] snapshot() {
    return data.clone();
  }

  public boolean load(byte[] source) {
    if (source.length != RAW_SIZE) {
      throw new IllegalArgumentException(
          "Invalid liquid map size: " + source.length + ", expected " + RAW_SIZE);
    }
    if (Arrays.equals(data, source)) {
      return false;
    }
    System.arraycopy(source, 0, data, 0, RAW_SIZE);
    return true;
  }

  private static void checkLevel(int level) {
    if (level > 255) {
      throw new IllegalArgumentException("Liquid level exceeds one-byte range: " + level);
    }
  }
}
