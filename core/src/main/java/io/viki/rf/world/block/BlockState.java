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

import io.viki.rf.world.light.Beam;
import io.viki.rf.world.light.LightEmitter;
import io.viki.rf.world.physics.Collision;
import io.viki.rf.util.property.ImmutablePropertyMap;
import io.viki.rf.util.property.Property;
import io.viki.momentum.util.Palette;
import io.viki.momentum.util.PaletteCandidate;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A concrete state of a block type, delegating behavior to its {@link Block}.
 */
public final class BlockState implements LightEmitter.TileSource, PaletteCandidate {
  public static final Palette<BlockState> BLOCK_STATE_PROPERTY_PALETTE = new Palette<>();
  public static BlockState EMPTY;

  private final ImmutablePropertyMap propertyMap;
  private final Block block;

  BlockState(Block block, ImmutablePropertyMap propertyMap) {
    this.propertyMap = propertyMap;
    this.block = block;
  }

  @Override
  public int identity() {
    return propertyMap.identity();
  }

  public <T> BlockState with(Property<T> property, T value) {
    return BLOCK_STATE_PROPERTY_PALETTE.get(propertyMap.with(property, value).identity());
  }

  public <T> T get(Property<T> property) {
    return propertyMap.get(property);
  }

  public boolean has(Property<?> property) {
    return propertyMap.has(property);
  }

  public Iterator<Map.Entry<Property<?>, Object>> iterator() {
    return propertyMap.iterator();
  }

  public Block block() {
    return block;
  }

  /** Whether this state represents the empty block. */
  public boolean isEmpty() {
    return block.isEmpty(this);
  }

  /** How this block fills its tile (collision, light and liquid rules). */
  public Shape shape() {
    return block.shape(this);
  }

  /** The effective fill after this tile's shape has been applied. */
  public Shape shape(TileShape tileShape) {
    return block.shape(this, tileShape);
  }

  /** The effective fill after byte-backed tile metadata has been applied. */
  public Shape shape(byte tileShape) {
    return shape(TileShape.byId(tileShape));
  }

  /** Restitution: 0 = no bounce, 1 = perfect. */
  public float restitution() {
    return block.restitution(this);
  }

  /** Compatibility alias for callers that describe restitution as bounce. */
  public float bounce() {
    return restitution();
  }

  /** Dimensionless kinetic friction coefficient. */
  public float friction() {
    return block.friction(this);
  }

  /** The collision shape used by the physics engine. */
  public Collision getVoxelShape() {
    return block.getVoxelShape(this);
  }

  /** The collision shape of this tile, carved by its tile shape. */
  public Collision getVoxelShape(TileShape tileShape) {
    return block.getVoxelShape(this, tileShape);
  }

  /** The collision shape of this tile, carved by byte-backed tile metadata. */
  public Collision getVoxelShape(byte tileShape) {
    return getVoxelShape(TileShape.byId(tileShape));
  }

  /** Whether the tile lets light through (not a full {@link Shape#SOLID}). */
  public float filterSkylight(int x, int y, float in, byte channel) {
    return block.filterSkylight(this, x, y, in, channel);
  }

  /** Filters incoming light through this state, per channel. */
  public float filterLight(int x, int y, float in, byte channel) {
    return block.filterLight(this, x, y, in, channel);
  }

  /** Filters incoming light through this tile shape, per channel. */
  public float filterLight(TileShape tileShape, int x, int y, float in, byte channel) {
    return block.filterLight(this, tileShape, x, y, in, channel);
  }

  /** The ambient light emitted by this state on one channel, or {@code 0}. */
  public float emitAmbient(int x, int y, byte channel) {
    return block.emitAmbient(this, x, y, channel);
  }

  /**
   * The directional beams emitted by this state; the caller draws and
   * recycles them.
   */
  public List<Beam> emitBeams(int x, int y) {
    return block.emitBeams(this, x, y);
  }

  /** Filters incoming light through this tile, carved by its shape byte. */
  public float filterLight(byte tileShape, int x, int y, float in, byte channel) {
    return filterLight(TileShape.byId(tileShape), x, y, in, channel);
  }
}
