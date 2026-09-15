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

/**
 * A single slot in an inventory, capable of holding one {@link Stack} at a time.
 *
 * <p>Slots support insertion (merging into the existing stack or refusing
 * incompatible stacks) and extraction (removing a portion of the held
 * stack). The {@link #slotLimit()} caps the number of units that can be
 * inserted in a single operation.
 *
 * <p>For automation, a slot can restrict its input and output via
 * {@link #mayInsert(Stack)} and {@link #mayExtract(int)}; an operation with
 * {@link StackOp#force()} bypasses those restrictions as well as the
 * {@link #validate(Stack)} check.
 *
 * @param <T> the concrete stack class held by this slot, e.g.
 *            {@code ItemStack} or {@code FluidStack}
 * @see Stack
 * @see Container
 * @see SimpleSlot
 */
public interface Slot<T extends Stack<?, T>> {
  /**
   * Returns the stack category of this slot.
   *
   * @return the stack category
   */
  StackCategory<?, T> category();

  /**
   * Returns the copied stack currently in this slot.
   *
   * @return the copied stack
   */

  default T get() {
    return getUnsafe().copy();
  }

  /**
   * Returns the referred stack currently in this slot.
   *
   * <p>DO NOT modify it.
   *
   * @return the referred stack
   */
  T getUnsafe();

  /**
   * Registers a listener invoked whenever the content of this slot actually
   * changes (an executed insert, extract, or set). Simulated operations
   * never trigger it. Multiple listeners are supported.
   *
   * @param listener the listener to register
   */
  void addChangeListener(Runnable listener);

  /**
   * Removes a previously registered change listener.
   *
   * @param listener the listener to remove
   */
  void removeChangeListener(Runnable listener);

  /**
   * Sets the stack in this slot.
   *
   * @param stack the stack to store
   */
  void set(T stack);

  /**
   * Returns whether the given stack is allowed in this slot.
   *
   * @param stack the stack to validate
   * @return {@code true} if the stack is allowed; defaults to {@code true}
   */
  default boolean validate(T stack) {
    return true;
  }

  /**
   * Returns whether this slot is empty.
   *
   * @return {@code true} if the held stack is empty
   */
  default boolean isEmpty() {
    return get().isEmpty();
  }

  /**
   * Returns the maximum number of units this slot can accept in a single insertion.
   *
   * @return the slot limit
   */
  int slotLimit();

  /**
   * Returns whether an insert is allowed (input restriction).
   *
   * <p>Restricting this marks the slot as output-only: automation must not
   * push into it. A forced operation bypasses the restriction.
   *
   * @param stack the stack about to be inserted
   * @return {@code true} if inserts are allowed; defaults to {@code true}
   */
  default boolean mayInsert(T stack) {
    return true;
  }

  /**
   * Returns whether an extract of the given amount is allowed (output restriction).
   *
   * <p>Restricting this marks the slot as input-only: automation must not
   * pull from it. A forced operation bypasses the restriction.
   *
   * @param amount the amount about to be extracted
   * @return {@code true} if extracts are allowed; defaults to {@code true}
   */
  default boolean mayExtract(int amount) {
    return true;
  }

  /**
   * Inserts a stack into this slot.
   *
   * <p>If the incoming stack is mergeable with the current one, counts are
   * merged up to the slot limit and the stack type limit. Otherwise, the
   * insertion is refused and the incoming stack is returned unmodified.
   * A simulate operation computes the result without modifying the slot.
   *
   * @param incoming the stack to insert
   * @param op       the operation; {@code FORCE_*} bypasses mayInsert and validate
   * @return the portion that could not fit, or an empty stack if fully inserted
   */
  T insert(T incoming, StackOp op);

  /**
   * Removes up to {@code count} units from this slot and returns them as a new stack.
   *
   * <p>A simulate operation returns what would be removed without modifying
   * the slot.
   *
   * @param count the number of units to remove
   * @param op    the operation; {@code FORCE_*} bypasses mayExtract
   * @return the removed units, or an empty stack if the slot is empty or count is non-positive
   */
  T extract(int count, StackOp op);

  /**
   * Inserts a stack with {@link StackOp#EXECUTE}.
   *
   * @param incoming the stack to insert
   * @return the portion that could not fit, or an empty stack if fully inserted
   */
  default T insert(T incoming) {
    return insert(incoming, StackOp.EXECUTE);
  }

  /**
   * Removes up to {@code count} units with {@link StackOp#EXECUTE}.
   *
   * @param count the number of units to remove
   * @return the removed units, or an empty stack if the slot is empty or count is non-positive
   */
  default T extract(int count) {
    return extract(count, StackOp.EXECUTE);
  }
}
