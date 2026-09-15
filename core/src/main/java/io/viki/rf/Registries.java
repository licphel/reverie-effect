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

package io.viki.rf;

import io.viki.rf.world.block.Block;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.item.Item;
import io.viki.rf.world.light.Beam;
import io.viki.rf.world.light.Channel;
import io.viki.rf.world.object.BuiltinObjects;
import io.viki.rf.world.object.MultiBlockDefinition;
import io.viki.rf.world.physics.Collision;
import io.viki.rf.util.property.ImmutablePropertyMap;
import io.viki.momentum.registry.Holder;
import io.viki.momentum.registry.DirectRegistry;
import io.viki.momentum.registry.IndirectRegistry;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.util.Palette;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Central registry for built-in blocks, items, and other engine objects.
 *
 * <p>Call {@link #bootstrap()} to finalize block property definitions
 * and fill state palettes before using any blocks.
 */
@NullMarked
public final class Registries {
  public static final Registry<Block> BLOCKS = new IndirectRegistry<>(Core.NAMESPACE.resolve("block"));
  public static final Registry<Item> ITEMS = new IndirectRegistry<>(Core.NAMESPACE.resolve("item"));
  public static final Registry<Liquid> LIQUIDS =
      new DirectRegistry<>(Core.NAMESPACE.resolve("liquid"));
  public static final Registry<MultiBlockDefinition> OBJECTS =
      new IndirectRegistry<>(Core.NAMESPACE.resolve("object"));

  /** Global palette for all ImmutablePropertyMap states. */
  public static final Holder<Item> AIR_ITEM = ITEMS.register(Core.NAMESPACE.resolve("air"), Item::new);

  // -- blocks -------------------------------------------------------------
  public static final Block AIR = registerBlock("air", new Block() {
    @Override
    public Collision getVoxelShape(BlockState state) {
      return Collision.EMPTY;
    }

    @Override
    public Shape shape(BlockState state) {
      return Shape.VACUUM;
    }
  });
  public static final Holder<MultiBlockDefinition> BIRCH_TREE = OBJECTS.register(
      Core.NAMESPACE.resolve("birch_tree"), BuiltinObjects::birchTree);
  public static final Block DIRT = registerBlock("dirt", new Block() {
    @Override
    public float restitution(BlockState state) {
      return 0.1F;
    }
  });
  public static final Block GRASS = registerBlock("grass", new Block() {
  });
  public static final Block STONE = registerBlock("stone", new Block() {
  });
  /** A wall block that emits warm torchlight. */
  public static final Block WALL = registerBlock("wall", new Block() {
    @Override
    public float emitAmbient(BlockState state, int x, int y, byte channel) {
      return switch (channel) {
        case Channel.RED -> 1.0F;
        case Channel.GREEN -> 0.7F;
        default -> 0.3F;
      };
    }
  });
  /** A pole that emits cool blue light and three rotating beams. */
  public static final Block COLORFUL = registerBlock("pole", new Block() {
    @Override
    public float emitAmbient(BlockState state, int x, int y, byte channel) {
      double fm = System.currentTimeMillis();
      return switch (channel) {
        case Channel.RED -> (float) Math.sin(fm / 1000.0) * 0.25F + 0.5F;
        case Channel.GREEN -> (float) Math.sin(fm / 1000.0 + 1) * 0.25F + 0.5F;
        default -> (float) Math.sin(fm / 1000.0 + 2) * 0.25F + 0.5F;
      };
    }

    @Override
    public List<Beam> emitBeams(BlockState state, int x, int y) {
        float f = (float) (System.currentTimeMillis() % 1000000) / 1000.0F;
        return List.of(
            Beam.of(x + 0.5F, y + 0.5F, 1, 0.2F, 0.2F,
                (float) -Math.PI / 2 + f, 0.5F, 0.0F, 15.0F, 2.0F),
            Beam.of(x + 0.5F, y + 0.5F, 0.2F, 1F, 0.2F,
                (float) -Math.PI / 2 + 2 + f * 2, 0.5F, 0.0F, 15.0F, 2.0F),
            Beam.of(x + 0.5F, y + 0.5F, 0.2F, 0.2F, 1F,
                (float) -Math.PI / 2 + 0.3F + f * 3, 0.5F, 0.0F, 15.0F, 2.0F)
        );
    }
  });

  // -- items --------------------------------------------------------------
  public static final Block PLATFORM = registerBlock("platform", new Block() {
    @Override
    public Collision getVoxelShape(BlockState state) {
      return Collision.PLATFORM;
    }

    @Override
    public Shape shape(BlockState state) {
      return Shape.PARTIAL;
    }
  });

  static {
    Liquids.initialize();
  }

  // -- registration -------------------------------------------------------
  private static boolean bootstrapped;

  private Registries() {
  }

  private static Block registerBlock(String name, Block block) {
    BLOCKS.register(Core.NAMESPACE.resolve(name), () -> block);
    return block;
  }

  /**
   * Runs property collection + state generation for all registered blocks.
   * Must be called once before blocks are used.
   */
  public static synchronized void bootstrap() {
    if (bootstrapped) {
      return;
    }

    BLOCKS.freeze();
    ITEMS.freeze();
    Liquids.freeze();
    OBJECTS.freeze();

    final Palette<ImmutablePropertyMap> tmpPalette = new Palette<>();
    for (Block block : BLOCKS) {
      block.collectProperties(block.propertyDef());
      block.propertyDef().collectStates(tmpPalette);
      block.fillStates();
    }

    BlockState.EMPTY = Registries.AIR.defaultState();
    bootstrapped = true;
  }
}
