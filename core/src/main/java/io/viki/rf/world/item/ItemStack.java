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

import io.viki.rf.world.inventory.SimpleContainer;
import io.viki.rf.world.inventory.Stack;
import io.viki.momentum.codec.nbt.CompoundNBT;
import org.jspecify.annotations.Nullable;

/**
 * A concrete {@link Stack} of {@link Item} types, e.g. the stack held in a
 * chest or a furnace slot.
 *
 * <p>Use {@link #of(Item, int)} to create stacks and {@link #EMPTY} (or
 * {@link Item#CATEGORY}) for the shared empty stack. Item inventories are
 * typed on this class: {@code SimpleContainer<ItemStack>}, {@code Slot<ItemStack>}.
 *
 * @see Item
 * @see SimpleContainer
 */
public final class ItemStack extends Stack<Item, ItemStack> {
  /** The globally shared empty item stack. */
  public static final ItemStack EMPTY = new ItemStack(Item.EMPTY, 0, null);

  private ItemStack(Item type, int count, @Nullable CompoundNBT data) {
    super(type, count, data);
  }

  /**
   * Creates an item stack with the given count.
   *
   * @param type  the item type
   * @param count the unit count
   * @return a new stack
   */
  public static ItemStack of(Item type, int count) {
    return new ItemStack(type, count, null);
  }

  /**
   * Creates a single-item stack.
   *
   * @param type the item type
   * @return a new stack
   */
  public static ItemStack of(Item type) {
    return new ItemStack(type, 1, null);
  }

  @Override
  protected ItemStack copy(int count, @Nullable CompoundNBT data) {
    return new ItemStack(type, count, data);
  }
}
