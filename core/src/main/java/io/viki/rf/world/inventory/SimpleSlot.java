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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A single inventory slot holding an optional {@link Stack}.
 *
 * <p>A slot may be empty, indicated by holding an empty stack. The slot
 * limit caps how many units can be inserted at once, and each slot tracks
 * its own count independently. The empty stack used as the slot's idle
 * value is provided by the {@link StackCategory} passed to the constructor
 * (e.g. the item category for item inventories, the liquid category for tanks).
 *
 * <p>Input/output restrictions for automation are configured with
 * {@link #canInput(boolean)} and {@link #canOutput(boolean)};
 * a forced {@link StackOp} bypasses them and the validator.
 *
 * @param <T> the concrete stack class held by this slot, e.g.
 *            {@code ItemStack} or {@code FluidStack}
 * @see Slot
 * @see Stack
 */
public class SimpleSlot<T extends Stack<?, T>> implements Slot<T> {
  private final StackCategory<?, T> category;
  private final int slotLimit;
  private final List<Runnable> changeListeners = new ArrayList<>();
  private T stack;
  private @Nullable Predicate<T> validator;
  private boolean canInput = true;
  private boolean canOutput = true;

  /**
   * Creates an empty slot with no slot-specific limit.
   *
   * @param category the stack category providing the slot's empty stack
   */
  public SimpleSlot(StackCategory<?, T> category) {
    this(category, Integer.MAX_VALUE);
  }

  /**
   * Creates an empty slot with the given maximum number of units.
   *
   * @param category  the stack category providing the slot's empty stack
   * @param slotLimit the maximum number of units this slot can accept in a single insertion
   */
  public SimpleSlot(StackCategory<?, T> category, int slotLimit) {
    this.category = category;
    this.slotLimit = slotLimit;
    this.stack = category.emptyStack();
  }

  /**
   * Sets the stack validator of this slot.
   *
   * @param validator the validator
   * @return self for chaining
   */
  public SimpleSlot<T> withValidator(@Nullable Predicate<T> validator) {
    this.validator = validator;
    return this;
  }

  /**
   * Sets whether inserts are allowed (input restriction).
   *
   * @param allowed {@code true} to allow inserts
   * @return self for chaining
   */
  public SimpleSlot<T> canInput(boolean allowed) {
    this.canInput = allowed;
    return this;
  }

  /**
   * Sets whether extracts are allowed (output restriction).
   *
   * @param allowed {@code true} to allow extracts
   * @return self for chaining
   */
  public SimpleSlot<T> canOutput(boolean allowed) {
    this.canOutput = allowed;
    return this;
  }

  private void fireChanged() {
    for (Runnable listener : changeListeners) {
      listener.run();
    }
  }

  @Override
  public StackCategory<?, T> category() {
    return category;
  }

  @Override
  public T getUnsafe() {
    return stack;
  }

  @Override
  public void addChangeListener(Runnable listener) {
    changeListeners.add(Objects.requireNonNull(listener));
  }

  @Override
  public void removeChangeListener(Runnable listener) {
    changeListeners.remove(listener);
  }

  @Override
  public void set(T stack) {
    // copy, never alias the caller's stack
    T copy = stack.isEmpty() ? category.emptyStack() : stack.copy();
    if (copy.equals(this.stack)) {
      return; // no actual change, no notification
    }
    this.stack = copy;
    fireChanged();
  }

  @Override
  public boolean validate(T stack) {
    return validator == null || validator.test(stack);
  }

  @Override
  public int slotLimit() {
    return slotLimit;
  }

  @Override
  public boolean mayInsert(T stack) {
    return canInput;
  }

  @Override
  public boolean mayExtract(int amount) {
    return canOutput;
  }

  @Override
  public T insert(T incoming, StackOp op) {
    if (incoming.isEmpty()) {
      return category.emptyStack();
    }
    if (!op.force() && (!validate(incoming) || !mayInsert(incoming))) {
      return incoming.copy();
    }

    int fit;
    if (stack.isEmpty()) {
      fit = Math.min(incoming.count(), Math.min(slotLimit(), incoming.limit()));
    } else if (Stack.isMergeable(incoming, stack)) {
      fit = Math.min(incoming.count(),
          Math.max(0, Math.min(slotLimit(), incoming.limit()) - stack.count()));
    } else {
      fit = 0;
    }

    if (op.execute() && fit > 0) {
      if (stack.isEmpty()) {
        // copy, never alias the caller's stack
        stack = incoming.copy(fit);
      } else {
        stack.grow(fit);
      }
      fireChanged();
    }
    return incoming.copy(incoming.count() - fit);
  }

  @Override
  public T extract(int count, StackOp op) {
    if (count <= 0 || stack.isEmpty()) {
      return category.emptyStack();
    }
    if (!op.force() && !mayExtract(count)) {
      return category.emptyStack();
    }
    int take = Math.min(count, stack.count());
    if (op.simulate()) {
      return stack.copy(take);
    }
    T result = stack.copy(take);
    stack.shrink(take);
    if (stack.isEmpty()) {
      stack = category.emptyStack();
    }
    fireChanged();
    return result;
  }

  @Override
  public int hashCode() {
    return Objects.hash(category, slotLimit, stack);
  }

  // Value equality: category, slot limit, and content. Listeners and the
  // input/output restrictions are behavior, not state, and are ignored.
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SimpleSlot<?> that)) {
      return false;
    }
    return slotLimit == that.slotLimit
        && Objects.equals(category, that.category)
        && Objects.equals(stack, that.stack);
  }

  @Override
  public String toString() {
    return stack.toString();
  }
}
