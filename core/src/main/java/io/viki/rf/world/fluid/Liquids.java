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

import io.viki.rf.Registries;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.Channel;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.momentum.gfx.tint.Color;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;

/**
 * Built-in liquid declarations. Storage ids are assigned by
 * {@link Registries#LIQUIDS} in registration order, with empty registered first.
 */
@NullMarked
public final class Liquids {
  public static final Liquid EMPTY = register("empty", new Liquid() {
    @Override
    @SideOnly(dist = Dist.CLIENT)
    public Color color() {
      return new Color(0, 0, 0, 0);
    }
  });

  public static final Liquid WATER = register("water", new Liquid(
      LiquidRenderDefinition.textured("/liquid/water.png", "/liquid/water_edge.png")) {
    @Override
    @SideOnly(dist = Dist.CLIENT)
    public Color color() {
      return new Color(0.2F, 0.4F, 0.9F, 0.85F);
    }

    @Override
    public float viscosity() {
      return PhysicsConstants.STANDARD_WATER_VISCOSITY;
    }

    @Override
    public float density() {
      return PhysicsConstants.STANDARD_WATER_DENSITY;
    }

    @Override
    public float temperature() {
      return 20F;
    }
  });
  public static final Liquid LAVA = register("lava", new Liquid(
      LiquidRenderDefinition.textured("/liquid/lava.png", "/liquid/lava_edge.png")) {
    @Override
    @SideOnly(dist = Dist.CLIENT)
    public Color color() {
      return new Color(1.0F, 0.35F, 0.05F, 0.95F);
    }

    @Override
    public void onTouch(FluidStack src, FluidStack neighbor,
        Level level, int x, int y, int nx, int ny) {
      // Starbound liquid interaction: when enough non-lava liquid surrounds
      // the lava tile, the lava solidifies into stone and the water is
      // consumed
      int sum = 0;
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          if ((dx == 0) == (dy == 0)) {
            continue;
          }
          FluidStack ns = level.getLiquidStack(x + dx, y + dy);
          if (ns.count() > 0 && ns.type() != Liquids.LAVA) {
            sum += ns.count();
          }
        }
      }
      if (sum < 26) {
        return;
      }
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          if ((dx == 0) == (dy == 0)) {
            continue;
          }
          FluidStack ns = level.getLiquidStack(x + dx, y + dy);
          if (ns.count() > 0 && ns.type() != Liquids.LAVA) {
            level.setLiquid(x + dx, y + dy, Liquids.EMPTY, 0);
          }
        }
      }
      level.setLiquid(x, y, Liquids.EMPTY, 0);
      level.setBlock(new BlockPos(x, y), Registries.STONE.defaultState());
    }

    @Override
    public float emitAmbient(int x, int y, int amount, byte channel) {
      return switch (channel) {
        case Channel.RED -> 0.99F;
        case Channel.GREEN -> 0.5F;
        default -> 0.5F;
      };
    }

    @Override
    public float viscosity() {
      return 100.0F;
    }

    @Override
    public float density() {
      return 3100.0F;
    }

    @Override
    public float temperature() {
      return 1000F;
    }
  });
  public static final Liquid POISON = register("poison", new Liquid() {
    @Override
    @SideOnly(dist = Dist.CLIENT)
    public Color color() {
      return new Color(0.5F, 0.8F, 0.2F, 0.85F);
    }

    @Override
    public float viscosity() {
      return 3.0E-3F;
    }

    @Override
    public float density() {
      return 1100.0F;
    }

    @Override
    public float temperature() {
      return 30F;
    }
  });

  private static volatile Liquid[] byStorageId = new Liquid[0];

  private Liquids() {
  }

  public static void initialize() {
  }

  public static synchronized void freeze() {
    if (!Registries.LIQUIDS.isFrozen()) {
      Registries.LIQUIDS.freeze();
    }
    if (byStorageId.length == Registries.LIQUIDS.size()) {
      return;
    }
    Liquid[] entries = new Liquid[Registries.LIQUIDS.size()];
    for (Liquid liquid : Registries.LIQUIDS) {
      int index = liquid.registryIndex();
      if (index > 255) {
        throw new IllegalStateException(
            "Liquid registry exceeds byte storage capacity at " + liquid.registryId());
      }
      entries[index] = liquid;
    }
    if (entries.length == 0 || entries[0] != EMPTY || Arrays.asList(entries).contains(null)) {
      throw new IllegalStateException("Liquid registry storage ids are not contiguous from empty");
    }
    byStorageId = entries;
  }

  /** Returns the registered liquid with the given storage id, or {@link #EMPTY}. */
  public static Liquid byId(int id) {
    Liquid[] entries = byStorageId;
    return id >= 0 && id < entries.length ? entries[id] : EMPTY;
  }

  private static Liquid register(String name, Liquid liquid) {
    Registries.LIQUIDS.register(io.viki.rf.Core.NAMESPACE.resolve(name), () -> liquid);
    return liquid;
  }
}
