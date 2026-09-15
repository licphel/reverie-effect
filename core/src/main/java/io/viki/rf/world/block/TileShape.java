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

package io.viki.rf.world.block;

import io.viki.rf.world.physics.Collision;

/** The compact set of shapes that can be stored by a tile. */
public enum TileShape {
  /** Full cube: the block fills the whole tile. */
  FULL(0),
  /** Half brick: only the bottom half of the tile is filled. */
  HALF_BRICK(1),
  /** Floor slope rising to the right: low left, high right. */
  SLOPE_LEFT_DOWN(2),
  /** Floor slope rising to the left: high left, low right. */
  SLOPE_RIGHT_DOWN(3),
  /** Ceiling slope hanging lower on the left. */
  SLOPE_LEFT_UP(4),
  /** Ceiling slope hanging lower on the right. */
  SLOPE_RIGHT_UP(5),
  /** Upper half brick: only the top half of the tile is filled. */
  HALF_BRICK_UP(6);

  /** Number of valid tile shapes. */
  public static final int COUNT;
  private static final TileShape[] BY_ID;

  private final byte id;

  static {
    TileShape[] values = values();
    BY_ID = new TileShape[values.length];
    for (TileShape shape : values) {
      int id = shape.id & 0xFF;
      if (id >= BY_ID.length || BY_ID[id] != null) {
        throw new ExceptionInInitializerError("Invalid tile shape id: " + id);
      }
      BY_ID[id] = shape;
    }
    for (TileShape shape : BY_ID) {
      if (shape == null) {
        throw new ExceptionInInitializerError("Tile shape ids must be contiguous");
      }
    }
    COUNT = BY_ID.length;
  }

  TileShape(int id) {
    this.id = (byte) id;
  }

  /** Returns the compact byte stored in a tile's metadata. */
  public byte id() {
    return id;
  }

  /** Resolves a stored tile-shape id. */
  public static TileShape byId(byte id) {
    return byId(id & 0xFF);
  }

  /** Resolves a tile-shape id received from an integer-backed protocol field. */
  public static TileShape byId(int id) {
    if (id < 0 || id >= BY_ID.length) {
      throw new IllegalArgumentException("Unknown tile shape id: " + id);
    }
    return BY_ID[id];
  }

  /** The next shape in the cycle used by the shape tool. */
  public TileShape next() {
    return BY_ID[((id & 0xFF) + 1) % BY_ID.length];
  }

  /** Whether this shape is one of the four slopes. */
  public boolean isSloped() {
    return this == SLOPE_LEFT_DOWN || this == SLOPE_RIGHT_DOWN
        || this == SLOPE_LEFT_UP || this == SLOPE_RIGHT_UP;
  }

  /** The fill category a solid block takes with this tile shape. */
  public Shape fill() {
    return this == FULL ? Shape.SOLID : Shape.PARTIAL;
  }

  /** The collision clip of this tile shape, for solid-fill blocks. */
  public Collision clip() {
    return switch (this) {
      case FULL -> Collision.CUBE;
      case HALF_BRICK -> Collision.HALF;
      case SLOPE_LEFT_DOWN -> Collision.SLOPE_LEFT_DOWN;
      case SLOPE_RIGHT_DOWN -> Collision.SLOPE_RIGHT_DOWN;
      case SLOPE_LEFT_UP -> Collision.SLOPE_LEFT_UP;
      case SLOPE_RIGHT_UP -> Collision.SLOPE_RIGHT_UP;
      case HALF_BRICK_UP -> Collision.HALF_UP;
    };
  }

}
