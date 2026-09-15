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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An inventory backed by a list of {@link Slot}s.
 *
 * <p>Insertion tries to merge into existing stacks of the same type first,
 * then fills empty slots. Extraction pulls matching units from any allowed
 * slot. Both operations are filtered by a slot-index predicate.
 *
 * <p>Not thread-safe; a container and its slots must be confined to a single
 * thread.
 *
 * @param category the stack category
 * @param slots    the given slots
 * @param <T>      the concrete stack class held by this container, e.g.
 *                 {@code ItemStack} or {@code FluidStack}
 * @see Slot
 * @see Container
 */
public record SimpleContainer<T extends Stack<?, T>>(StackCategory<?, T> category,
                                                     List<Slot<T>> slots) implements Container<T> {
  /**
   * Creates a container with the given number of empty slots.
   *
   * @param category the stack category providing the container's empty stacks
   * @param size     the number of slots
   */
  public SimpleContainer(StackCategory<?, T> category, int size) {
    this(category, createSlots(category, size));
  }

  /**
   * Creates a container backed by the given slots.
   *
   * @param category the stack category providing the container's empty stacks
   * @param slots    the slot list backing this container
   */
  public SimpleContainer(StackCategory<?, T> category, List<Slot<T>> slots) {
    this.category = category;
    this.slots = List.copyOf(slots);
  }

  private static <T extends Stack<?, T>> List<Slot<T>> createSlots(StackCategory<?, T> category, int size) {
    List<Slot<T>> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(new SimpleSlot<>(category));
    }
    return List.copyOf(list);
  }

  @Override
  public Slot<T> slotAt(int slot) {
    assertInRange(slot);
    return slots.get(slot);
  }

  private void assertInRange(int slot) {
    if (slot < 0 || slot >= slots.size()) {
      throw new IndexOutOfBoundsException("Slot " + slot + " is out of bounds");
    }
  }

  @SuppressWarnings("all")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SimpleContainer<?> sc)) {
      return false;
    }
    return Objects.equals(category, sc.category) && Objects.equals(slots, sc.slots);
  }

  @Override
  public String toString() {
    return slots.toString();
  }
}
