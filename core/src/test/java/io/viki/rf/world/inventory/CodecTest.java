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

package io.viki.rf.world.inventory;

import io.viki.rf.Registries;
import io.viki.rf.world.fluid.FluidStack;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.item.Item;
import io.viki.rf.world.item.ItemStack;
import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodecTest {
  private static final Codec<FluidStack> STACK_CODEC = Liquid.CATEGORY.stackCodec();

  @BeforeAll
  static void freezeItemRegistry() {
    // AIR_ITEM lives in the deferred item registry: its holder only resolves
    // once the registry is frozen, which the real game does at startup
    Registries.ITEMS.freeze();
  }

  // -- stack codec -------------------------------------------------------

  @Test
  void stackRoundTrip() {
    FluidStack water = FluidStack.of(Liquids.WATER, 255);
    water.getOrCreateData().putInt("level", 7);
    assertEquals(water, STACK_CODEC.deserialize(STACK_CODEC.serialize(water)));
  }

  @Test
  void stackRoundTripWithoutData() {
    FluidStack lava = FluidStack.of(Liquids.LAVA, 42);
    assertEquals(lava, STACK_CODEC.deserialize(STACK_CODEC.serialize(lava)));
  }

  @Test
  void emptyStackRoundTrip() {
    FluidStack decoded = STACK_CODEC.deserialize(STACK_CODEC.serialize(Liquid.CATEGORY.emptyStack()));
    assertTrue(decoded.isEmpty());
  }

  @Test
  void stackBinaryRoundTrip() {
    FluidStack poison = FluidStack.of(Liquids.POISON, 3);
    poison.getOrCreateData().putString("origin", "swamp");
    BinaryBuffer buffer = BinaryBuffer.heap(64);
    STACK_CODEC.serialize(poison, buffer);
    buffer.readerIndex(0);
    assertEquals(poison, STACK_CODEC.deserialize(buffer));
  }

  @Test
  void unknownTypeIdThrows() {
    CompoundNBT tag = new CompoundNBT();
    tag.putString("id", "core:not_a_liquid");
    tag.putInt("count", 1);
    assertThrows(IllegalArgumentException.class, () -> STACK_CODEC.deserialize(tag));
  }

  @Test
  void unregisteredTypeThrows() {
    Codec<ItemStack> itemCodec = Item.CATEGORY.stackCodec();
    assertThrows(IllegalArgumentException.class,
        () -> itemCodec.serialize(ItemStack.of(new Item(), 1)));
  }

  // -- BuiltinContainerCodecs -----------------------------------------------

  @Test
  void builtinItemContainerRoundTrip() {
    // only registered types can be encoded; "air" is registered in Registries
    Item air = Registries.AIR_ITEM.get();
    SimpleContainer<ItemStack> chest = new SimpleContainer<>(Item.CATEGORY, 3);
    chest.set(0, ItemStack.of(air, 7));
    ItemStack tagged = ItemStack.of(air, 2);
    tagged.getOrCreateData().putInt("ench", 1);
    chest.set(2, tagged);
    assertEquals(chest, BuiltinContainerCodecs.ITEM.deserialize(BuiltinContainerCodecs.ITEM.serialize(chest)));
  }

  @Test
  void builtinItemContainerBinaryRoundTrip() {
    SimpleContainer<ItemStack> chest = new SimpleContainer<>(Item.CATEGORY, 2);
    chest.set(1, ItemStack.of(Registries.AIR_ITEM.get(), 3));
    BinaryBuffer buffer = BinaryBuffer.heap(64);
    BuiltinContainerCodecs.ITEM.serialize(chest, buffer);
    buffer.readerIndex(0);
    assertEquals(chest, BuiltinContainerCodecs.ITEM.deserialize(buffer));
  }

  @Test
  void builtinFluidContainerRoundTrip() {
    SimpleContainer<FluidStack> tank = new SimpleContainer<>(Liquid.CATEGORY, 4);
    tank.set(0, FluidStack.of(Liquids.WATER, 255));
    tank.set(3, FluidStack.of(Liquids.LAVA, 128));
    assertEquals(tank, BuiltinContainerCodecs.FLUID.deserialize(BuiltinContainerCodecs.FLUID.serialize(tank)));
  }

  @Test
  void builtinEmptyContainerRoundTrip() {
    SimpleContainer<ItemStack> chest = new SimpleContainer<>(Item.CATEGORY, 2);
    assertEquals(chest, BuiltinContainerCodecs.ITEM.deserialize(BuiltinContainerCodecs.ITEM.serialize(chest)));
  }
}
