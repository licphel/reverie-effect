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

import io.viki.momentum.registry.RegistryContext;
import io.viki.momentum.registry.RegistryEntry;
import io.viki.momentum.util.Lazy;
import io.viki.rf.util.property.ImmutablePropertyMap;
import io.viki.rf.util.property.PropertyDef;
import io.viki.rf.world.inventory.Stackable;
import io.viki.rf.world.light.Beam;
import io.viki.rf.world.light.LightEngine;
import io.viki.rf.world.physics.Collision;
import io.viki.rf.world.physics.PhysicsConstants;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A registered block type and the behaviour shared by its states. */
public class Block implements Stackable, RegistryEntry {
  public static final Lazy<Block> AIR = Lazy.ofInjected();

  private final PropertyDef propertyDef = new PropertyDef();
  private final List<BlockState> states = new ArrayList<>();
  private final RegistryContext registryContext = new RegistryContext();
  private @Nullable BlockState defaultState;

  public Block() {
  }

  @Override
  public boolean isEmpty() {
    return registryIndex() == 0;
  }

  /** Returns whether this concrete state represents the empty block. */
  public boolean isEmpty(BlockState state) {
    return isEmpty();
  }

  @Override
  public RegistryContext getRegistryContext() {
    return registryContext;
  }

  public PropertyDef propertyDef() {
    return propertyDef;
  }

  public List<BlockState> states() {
    return List.copyOf(states);
  }

  public void collectProperties(PropertyDef def) {
  }

  public void fillStates() {
    for (ImmutablePropertyMap map : propertyDef.maps()) {
      BlockState state = new BlockState(this, map);
      BlockState.BLOCK_STATE_PROPERTY_PALETTE.assign(state);
      states.add(state);
    }

    defaultState = BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(propertyDef.defaultMap().identity());
  }

  /** The collision shape used by the physics engine. */
  public Collision getVoxelShape(BlockState state) {
    return Collision.CUBE;
  }

  /** The collision shape of a tile after its per-tile carve is applied. */
  public Collision getVoxelShape(BlockState state, TileShape tileShape) {
    if (shape(state) == Shape.SOLID && tileShape != TileShape.FULL) {
      return tileShape.clip();
    }
    return getVoxelShape(state);
  }

  /** Converts byte-backed tile metadata at the block API boundary. */
  public Collision getVoxelShape(BlockState state, byte tileShape) {
    return getVoxelShape(state, TileShape.byId(tileShape));
  }

  /** Restitution: 0 = no bounce, 1 = perfect. */
  public float restitution(BlockState state) {
    return 0F;
  }

  /** Dimensionless kinetic friction coefficient. */
  public float friction(BlockState state) {
    return PhysicsConstants.STANDARD_FRICTION_COEFFICIENT;
  }

  /** How this block fills its tile (collision, light and liquid rules). */
  public Shape shape(BlockState state) {
    return Shape.SOLID;
  }

  /** The effective fill after this tile's shape has been applied. */
  public Shape shape(BlockState state, TileShape tileShape) {
    Shape base = shape(state);
    return base == Shape.SOLID ? tileShape.fill() : base;
  }

  /** Converts byte-backed tile metadata at the block API boundary. */
  public Shape shape(BlockState state, byte tileShape) {
    return shape(state, TileShape.byId(tileShape));
  }

  public float filterSkylight(BlockState state, int x, int y, float in, byte channel) {
    if (shape(state) == Shape.SOLID) {
      return 0.0F;
    }
    return in;
  }

  /** Filters incoming light through this state, per channel. */
  public float filterLight(BlockState state, int x, int y, float in, byte channel) {
    return filterLight(state, TileShape.FULL, x, y, in, channel);
  }

  /** Filters incoming light through this carved tile, per channel. */
  public float filterLight(BlockState state, TileShape tileShape, int x, int y,
                           float in, byte channel) {
    if (shape(state, tileShape) == Shape.SOLID) {
      return in * 0.92F - LightEngine.UNIT;
    }
    return in * 0.99F - LightEngine.UNIT;
  }

  /** Converts byte-backed tile metadata at the block API boundary. */
  public float filterLight(BlockState state, byte tileShape, int x, int y,
                           float in, byte channel) {
    return filterLight(state, TileShape.byId(tileShape), x, y, in, channel);
  }

  /** The ambient light emitted by this state on one channel, or {@code 0}. */
  public float emitAmbient(BlockState state, int x, int y, byte channel) {
    return 0F;
  }

  /** The directional beams emitted by this state. */
  public List<Beam> emitBeams(BlockState state, int x, int y) {
    return List.of();
  }

  public BlockState defaultState() {
    return Objects.requireNonNull(defaultState);
  }
}
