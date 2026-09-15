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
import java.util.function.Predicate;

/**
 * An inventory made up of multiple {@link Slot} slots.
 *
 * <p>Provides bulk {@link #insert(Stack, StackOp, Predicate)} and
 * {@link #extract(Stack, StackOp, Predicate)} operations that iterate over
 * slots matching a predicate, delegating the per-slot logic (merging,
 * capacity limits, validation, input/output restrictions) to the slots
 * themselves.
 *
 * <p>Convenience overloads default to {@link StackOp#EXECUTE} and all slots.
 *
 * @param <T> the concrete stack class held by this container, e.g.
 *            {@code ItemStack} or {@code FluidStack}
 * @see Slot
 * @see SimpleContainer
 */
public interface Container<T extends Stack<?, T>> {
  /**
   * Returns the underlying slot list (possibly unmodifiable).
   *
   * @return the slots
   */
  List<Slot<T>> slots();

  /**
   * Returns the stack category of this container.
   *
   * @return the stack category
   */
  StackCategory<?, T> category();

  /**
   * Returns a snapshot of every slot's stack, in slot order.
   *
   * @return the slot contents
   */
  default List<T> stacks() {
    List<T> stacks = new ArrayList<>(size());
    for (Slot<T> slot : slots()) {
      stacks.add(slot.get());
    }
    return stacks;
  }

  /**
   * Returns the number of slots in this handler.
   *
   * @return the slot count
   */
  default int size() {
    return slots().size();
  }

  /**
   * Inserts a stack into slots matching the given predicate, one slot at a
   * time until it fits or no slot remains.
   *
   * @param stack       the stack to insert
   * @param op          the operation to apply to each slot
   * @param slotMatcher a predicate that returns {@code true} for allowed slot indices
   * @return the portion that could not fit, or an empty stack if fully inserted
   */
  default T insert(T stack, StackOp op, Predicate<Integer> slotMatcher) {
    if (stack.isEmpty()) {
      return category().emptyStack();
    }
    T remainder = stack;
    for (int i = 0; i < size() && !remainder.isEmpty(); i++) {
      if (!slotMatcher.test(i)) {
        continue;
      }
      remainder = slotAt(i).insert(remainder, op);
    }
    return remainder.isEmpty() ? category().emptyStack() : remainder;
  }

  /**
   * Extracts units matching the given template from slots that match the
   * predicate.
   *
   * @param template    a stack describing the type and data to match
   * @param op          the operation to apply to each slot
   * @param slotMatcher a predicate that returns {@code true} for allowed slot indices
   * @return the extracted units, or an empty stack if nothing matched
   */
  default T extract(T template, StackOp op, Predicate<Integer> slotMatcher) {
    if (template.isEmpty()) {
      return category().emptyStack();
    }
    int remaining = template.count();
    T collected = category().emptyStack();

    for (int i = 0; i < size() && remaining > 0; i++) {
      if (!slotMatcher.test(i)) {
        continue;
      }
      Slot<T> slot = slotAt(i);
      if (slot.get().isEmpty() || !Stack.isSameTypeSameData(template, slot.get())) {
        continue;
      }
      T taken = slot.extract(Math.min(remaining, slot.get().count()), op);
      remaining -= taken.count();
      if (collected.isEmpty()) {
        collected = taken;
      } else if (!taken.isEmpty()) {
        collected.grow(taken.count());
      }
    }

    return collected;
  }

  /**
   * Inserts a stack into all slots.
   *
   * @param stack the stack to insert
   * @return the portion that could not fit, or an empty stack if fully inserted
   */
  default T insert(T stack) {
    return insert(stack, StackOp.EXECUTE, i -> true);
  }

  /**
   * Inserts a stack into all slots.
   *
   * @param stack the stack to insert
   * @param op    the operation to apply to each slot
   * @return the portion that could not fit, or an empty stack if fully inserted
   */
  default T insert(T stack, StackOp op) {
    return insert(stack, op, i -> true);
  }

  /**
   * Extracts units matching the given template from all slots.
   *
   * @param template a stack describing the type and data to match
   * @return the extracted units, or an empty stack if nothing matched
   */
  default T extract(T template) {
    return extract(template, StackOp.EXECUTE, i -> true);
  }

  /**
   * Extracts units matching the given template from all slots.
   *
   * @param template a stack describing the type and data to match
   * @param op       the operation to apply to each slot
   * @return the extracted units, or an empty stack if nothing matched
   */
  default T extract(T template, StackOp op) {
    return extract(template, op, i -> true);
  }

  /**
   * Returns whether any slot holds a stack matching the given predicate.
   *
   * @param predicate the predicate to test each slot's stack against
   * @return {@code true} if at least one slot matches
   */
  default boolean contains(Predicate<T> predicate) {
    for (Slot<T> slot : slots()) {
      if (predicate.test(slot.get())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Moves up to {@code amount} units matching the template from this
   * container into the destination container.
   *
   * <p>The destination is probed with a simulated insert first, so only the
   * portion it can actually accept is extracted and inserted for real — the
   * operation never has to refund rejected units. The returned value is the
   * number of units that moved.
   *
   * @param to       the destination container
   * @param template a stack describing the type and data to move
   * @param amount   the maximum number of units to move
   * @param force    whether to bypass validators and input/output
   *                 restrictions on both sides, like a {@code FORCE_*} op
   * @return the number of units actually transferred
   */
  default int transfer(Container<T> to, T template, int amount, boolean force) {
    if (template.isEmpty() || amount <= 0) {
      return 0;
    }
    // probe the destination capacity first: a simulated insert tells us how
    // much of the template would fit, with no side effects
    T probe = to.insert(template.copy(amount), force ? StackOp.FORCE_SIMULATE : StackOp.SIMULATE);
    int desired = amount - probe.count();
    if (desired <= 0) {
      return 0;
    }
    // extract only the accepted portion, so the insertion below can never
    // be partially rejected and no refund is ever needed
    T pulled = extract(template.copy(desired), force ? StackOp.FORCE_EXECUTE : StackOp.EXECUTE);
    if (pulled.isEmpty()) {
      return 0;
    }
    to.insert(pulled, force ? StackOp.FORCE_EXECUTE : StackOp.EXECUTE);
    return pulled.count();
  }

  /**
   * Moves up to {@code amount} units matching the template from this
   * container into the destination container, respecting both sides'
   * validators and input/output restrictions.
   *
   * @param to       the destination container
   * @param template a stack describing the type and data to move
   * @param amount   the maximum number of units to move
   * @return the number of units actually transferred
   */
  default int transfer(Container<T> to, T template, int amount) {
    return transfer(to, template, amount, false);
  }

  /**
   * Returns the stack in the given slot.
   *
   * @param slot the slot index
   * @return the stack at that slot
   */
  default T get(int slot) {
    return slotAt(slot).get();
  }

  /**
   * Returns the slot handler at the given index.
   *
   * @param slot the slot index
   * @return the slot handler
   */
  Slot<T> slotAt(int slot);

  /**
   * Sets the stack in the given slot.
   *
   * @param slot  the slot index
   * @param stack the stack to store
   */
  default void set(int slot, T stack) {
    slotAt(slot).set(stack);
  }

  /**
   * Clears every slot in this handler.
   */
  default void clear() {
    T empty = category().emptyStack();
    for (Slot<T> slot : slots()) {
      slot.set(empty);
    }
  }

  /**
   * Returns whether every slot in this handler is empty.
   *
   * @return {@code true} if all slots are empty
   */
  default boolean isEmpty() {
    for (Slot<T> slot : slots()) {
      if (!slot.isEmpty()) {
        return false;
      }
    }
    return true;
  }
}
