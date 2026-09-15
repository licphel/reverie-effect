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

import io.viki.rf.world.inventory.StackCategory;
import io.viki.rf.world.inventory.Stackable;
import io.viki.rf.Registries;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.Beam;
import io.viki.rf.world.light.LightEmitter;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.registry.RegistryContext;
import io.viki.momentum.registry.RegistryEntry;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A liquid type. Liquids live in a layer separate from blocks and walls:
 * every tile stores a liquid id and a level (see {@link LiquidMap}).
 */
public abstract class Liquid implements RegistryEntry, LightEmitter.LiquidSource, Stackable {
  /** The liquid stack category: one shared empty stack and type registry for every liquid type. */
  public static final StackCategory<Liquid, FluidStack> CATEGORY = new StackCategory<>() {
    @Override
    protected FluidStack createEmptyStack() {
      return FluidStack.EMPTY;
    }

    @Override
    public FluidStack stackOf(Liquid type, int count) {
      return FluidStack.of(type, count);
    }

    @Override
    public Registry<Liquid> registry() {
      return Registries.LIQUIDS;
    }
  };

  private final RegistryContext ctx = new RegistryContext();
  private final LiquidRenderDefinition renderDefinition;

  protected Liquid() {
    this(LiquidRenderDefinition.NONE);
  }

  protected Liquid(LiquidRenderDefinition renderDefinition) {
    this.renderDefinition = Objects.requireNonNull(renderDefinition, "renderDefinition");
  }

  @Override
  public boolean isEmpty() {
    return this == Liquids.EMPTY;
  }

  @Override
  public RegistryContext getRegistryContext() {
    return ctx;
  }

  /** The compact registry id stored per tile. */
  public final byte id() {
    int index = registryIndex();
    if (index > 255) {
      throw new IllegalStateException("Liquid registry exceeds byte storage capacity: " + index);
    }
    return (byte) index;
  }

  @SideOnly(dist = Dist.CLIENT)
  public final LiquidRenderDefinition renderDefinition() {
    return renderDefinition;
  }

  /** The gradient used to render this liquid. */
  @SideOnly(dist = Dist.CLIENT)
  public abstract Color color();

  /**
   * Reaction hook invoked when a tile of this liquid touches a different
   * liquid (the lava/water reaction is implemented here).
   *
   * @param src the liquid of the tile being processed
   * @param neighbor the touching liquid of the neighbor tile
   */
  public void onTouch(FluidStack src, FluidStack neighbor, Level level, int x, int y, int nx, int ny) {
  }

  // -- light --------------------------------------------------------------

  @Override
  public float filterLight(int x, int y, int amount, float in, byte channel) {
    return in * 0.98F - amount / (float) FluidEngine.FULL * 0.01F;
  }

  /** The ambient light emitted by this liquid on one channel, or {@code 0}. */
  @Override
  public float emitAmbient(int x, int y, int amount, byte channel) {
    return 0F;
  }

  /**
   * The directional beams emitted by this liquid; the caller draws and
   * recycles them.
   */
  @Override
  public List<Beam> emitBeams(int x, int y, int amount) {
    return Collections.emptyList();
  }

  // -- physics ------------------------------------------------------------

  /**
   * Liquids have no intrinsic per-stack limit; the capacity of a tank is
   * determined by its slot limit instead.
   */
  @Override
  public int limit() {
    return Integer.MAX_VALUE;
  }

  /** Dynamic viscosity in pascal-seconds. */
  public float viscosity() {
    return PhysicsConstants.STANDARD_WATER_VISCOSITY;
  }

  /** Mass density in kilograms per cubic metre. */
  public float density() {
    return PhysicsConstants.STANDARD_WATER_DENSITY;
  }

  /** Temperature in degrees; hot liquid produces a thermal updraft. */
  public float temperature() {
    return 20F;
  }
}
