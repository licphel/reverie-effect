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

import io.viki.rf.world.fluid.FluidStack;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.item.Item;
import io.viki.rf.world.item.ItemStack;
import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.nbt.ListNBT;
import io.viki.momentum.codec.nbt.NBT;

/**
 * Ready-made codecs for whole containers of the built-in stack categories.
 *
 * <p>Each codec serializes a {@link SimpleContainer} self-contained: the
 * slot count and every slot's stack, resolved through the category's type
 * registry. It is stateless and thread-safe, and works for NBT
 * ({@code toNbt}/{@code fromNbt}) and binary ({@code serialize}/
 * {@code deserialize}) transports alike.
 *
 * <pre>{@code
 * CursorBuffer buffer = CursorBuffer.heap(64);
 * BuiltinContainerCodecs.ITEM.serialize(container, buffer);
 * }</pre>
 *
 * @see StackCategory#stackCodec()
 * @see StackCategory#stackCodec()
 */
public final class BuiltinContainerCodecs {
  /** Codec for a whole item container, e.g. a chest or furnace inventory. */
  public static final Codec<SimpleContainer<ItemStack>> ITEM = containerCodec(Item.CATEGORY);
  /** Codec for a whole fluid container, e.g. a tank group. */
  public static final Codec<SimpleContainer<FluidStack>> FLUID = containerCodec(Liquid.CATEGORY);

  private BuiltinContainerCodecs() {
  }

  private static <T extends Stackable, S extends Stack<T, S>> Codec<SimpleContainer<S>> containerCodec(
      StackCategory<T, S> category) {
    Codec<S> stackCodec = category.stackCodec();
    return Codec.of(container -> {
          CompoundNBT tag = new CompoundNBT();
          tag.putInt("size", container.size());
          ListNBT slots = new ListNBT();
          for (S stack : container.stacks()) {
            slots.add(stackCodec.serialize(stack));
          }
          tag.put("slots", slots);
          return tag;
        },
        nbt -> {
          CompoundNBT tag = (CompoundNBT) nbt;
          int size = tag.getInt("size", 0);
          NBT slotsTag = tag.get("slots");
          if (!(slotsTag instanceof ListNBT slots)) {
            throw new IllegalArgumentException("Missing container slots in " + tag);
          }
          SimpleContainer<S> container = new SimpleContainer<>(category, size);
          for (int i = 0; i < Math.min(size, slots.size()); i++) {
            NBT slotTag = slots.get(i);
            if (slotTag != null) {
              container.set(i, stackCodec.deserialize(slotTag));
            }
          }
          return container;
        });
  }
}
