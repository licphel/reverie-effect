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

import io.viki.momentum.codec.nbt.CompoundNBT;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A counted reference to a {@link Stackable} with optional attached data.
 *
 * <p>A stack holds a type, a count, and optional {@link CompoundNBT}
 * components. Any stack with a non-positive count is considered empty; each
 * stack class provides its globally shared empty stack (e.g.
 * {@code ItemStack.EMPTY}), and most mutating operations throw
 * {@link IllegalStateException} when called on an empty stack.
 *
 * <p>The self type parameter {@code S} is the concrete stack class
 * ({@code ItemStack}, {@code FluidStack}, ...): every factory-like method
 * ({@link #copy(int)}, {@link #copy()}, {@link #split(int)}) is
 * declared to return it, so derived types are preserved without casts.
 *
 * @param <T> the stack type, e.g. an item or a liquid
 * @param <S> the concrete stack class
 * @see Stackable
 * @see Slot
 */
public abstract class Stack<T extends Stackable, S extends Stack<T, S>> {
  protected final T type;
  protected int count;
  protected @Nullable CompoundNBT data;

  protected Stack(T type, int count, @Nullable CompoundNBT data) {
    if (count < 0) {
      throw new IllegalArgumentException("count < 0");
    }
    this.type = type;
    this.count = count;
    this.data = data;
  }

  /**
   * Returns whether two stacks share the same type.
   *
   * @param a the first stack
   * @param b the second stack
   * @return {@code true} if both stacks have the same type
   */
  public static boolean isSameType(Stack<?, ?> a, Stack<?, ?> b) {
    return Objects.equals(a.type(), b.type());
  }

  /**
   * Returns whether two stacks share the same type and attached data.
   *
   * @param a the first stack
   * @param b the second stack
   * @return {@code true} if both type and data are equal
   */
  public static boolean isSameTypeSameData(Stack<?, ?> a, Stack<?, ?> b) {
    return isSameType(a, b) && Objects.equals(a.getData(), b.getData());
  }

  /**
   * Returns whether two stacks can be merged into a single slot.
   *
   * <p>Stacks are mergeable if either is empty, or if they share the same
   * type and data.
   *
   * @param a the first stack
   * @param b the second stack
   * @return {@code true} if the stacks are mergeable
   */
  public static boolean isMergeable(Stack<?, ?> a, Stack<?, ?> b) {
    return a.isEmpty() || b.isEmpty() || isSameTypeSameData(a, b);
  }

  /**
   * Creates a new instance of this stack's concrete class with the given
   * state. Implemented by each concrete stack class.
   *
   * @param count the unit count
   * @param data  the attached data, or {@code null}
   * @return a new stack
   */
  protected abstract S copy(int count, @Nullable CompoundNBT data);

  /**
   * Returns the type of this stack.
   *
   * @return the type, or {@code null} if this stack is empty
   */
  public T type() {
    return type;
  }

  /**
   * Returns the maximum stack size for the type.
   *
   * @return the max stack size, or {@code 0} if this stack is empty
   */
  public int limit() {
    return isEmpty() ? 0 : type.limit();
  }

  /**
   * Splits up to {@code take} units from this stack, shrinking it and
   * returning a new stack with the taken count.
   *
   * <p>If {@code take} exceeds the current count or is non-positive,
   * returns an empty stack.
   *
   * @param take the number of units to split off
   * @return the taken units, or an empty stack if the split failed
   * @throws IllegalStateException if this stack is empty
   */
  public S split(int take) {
    assertNonempty();
    if (count < take || take <= 0) {
      return copy(0);
    }
    shrink(take);
    return copy(take);
  }

  /**
   * Splits up to {@code take} units, clamped to the actual count.
   *
   * @param take the desired number of units to split
   * @return the taken units, or an empty stack if the count is zero
   * @throws IllegalStateException if this stack is empty
   */
  public S splitByMaxEffort(int take) {
    return split(Math.min(take, count));
  }

  /**
   * Returns the current stack count.
   *
   * @return the count, or {@code 0} if this stack is empty
   */
  public int count() {
    return isEmpty() ? 0 : count;
  }

  /**
   * Returns whether this stack is empty.
   *
   * @return {@code true} if the count is non-positive
   */
  public boolean isEmpty() {
    return count <= 0 || type.isEmpty();
  }

  /**
   * Grows the count by {@code n}, clamped to the type's maximum stack size.
   *
   * @param n the amount to add
   * @throws IllegalStateException if this stack is empty
   */
  public void grow(int n) {
    assertNonempty();
    count += n;
  }

  /**
   * Reduces the count by {@code n}, clamped to 0.
   *
   * @param n the amount to subtract
   * @throws IllegalStateException if this stack is empty
   */
  public void shrink(int n) {
    assertNonempty();
    count -= n;
  }

  /**
   * Sets the count to the given value.
   *
   * @param count the new count
   * @throws IllegalStateException if this stack is empty
   */
  public void setCount(int count) {
    assertNonempty();
    this.count = count;
  }

  /**
   * Returns a copy of this stack with the given count, sharing the same
   * type and data.
   *
   * @param count the count for the copy
   * @return a new stack with the same type and data
   */
  public S copy(int count) {
    return copy(count, data == null ? null : data.copy());
  }

  /**
   * Returns a full copy of this stack, including the same type, count, and
   * data.
   *
   * @return a copy of this stack
   */
  public S copy() {
    return copy(count);
  }

  /**
   * Returns the attached component data.
   *
   * @return the data, or {@code null} if not set or if this stack is empty
   */
  public @Nullable CompoundNBT getData() {
    return isEmpty() ? null : data;
  }

  /**
   * Sets the attached component data.
   *
   * @param data the data to attach, or {@code null} to clear
   * @throws IllegalStateException if this stack is empty
   */
  public void setData(@Nullable CompoundNBT data) {
    assertNonempty();
    this.data = data;
  }

  /**
   * Returns the attached component data, creating a new empty tag if none
   * is present.
   *
   * @return the data; never {@code null}
   * @throws IllegalStateException if this stack is empty
   */
  public CompoundNBT getOrCreateData() {
    assertNonempty();
    if (data == null) {
      data = new CompoundNBT();
    }
    return data;
  }

  @Override
  public int hashCode() {
    if (isEmpty()) {
      return 0;
    }
    int h = type.hashCode() * 31 + count;
    if (data != null) {
      h = h * 31 + data.hashCode();
    }
    return h;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Stack<?, ?> that)) {
      return false;
    }
    if (count != that.count) {
      return false;
    }
    return isSameTypeSameData(this, that);
  }

  @Override
  public String toString() {
    return isEmpty() ? "empty" : count + " " + type;
  }

  private void assertNonempty() {
    if (isEmpty()) {
      throw new IllegalStateException("Cannot modify empty stack");
    }
  }
}
