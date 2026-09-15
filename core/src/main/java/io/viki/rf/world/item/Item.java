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

package io.viki.rf.world.item;

import io.viki.rf.Registries;
import io.viki.rf.world.inventory.StackCategory;
import io.viki.rf.world.inventory.Stackable;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.registry.RegistryContext;
import io.viki.momentum.registry.RegistryEntry;

public class Item implements RegistryEntry, Stackable {
  /** The placeholder item used as the empty stack's type; never registered. */
  static final Item EMPTY = new Item();
  /** The item stack category: one shared empty stack and type registry for every item type. */
  public static final StackCategory<Item, ItemStack> CATEGORY = new StackCategory<>() {
    @Override
    protected ItemStack createEmptyStack() {
      return ItemStack.EMPTY;
    }

    @Override
    public ItemStack stackOf(Item type, int count) {
      return ItemStack.of(type, count);
    }

    @Override
    public Registry<Item> registry() {
      return Registries.ITEMS;
    }
  };

  private final RegistryContext ctx = new RegistryContext();

  @Override
  public RegistryContext getRegistryContext() {
    return ctx;
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
  }
}
