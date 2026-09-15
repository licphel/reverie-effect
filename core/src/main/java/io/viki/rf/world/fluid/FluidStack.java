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

package io.viki.rf.world.fluid;

import io.viki.rf.world.inventory.SimpleContainer;
import io.viki.rf.world.inventory.Stack;
import io.viki.momentum.codec.nbt.CompoundNBT;
import org.jspecify.annotations.Nullable;

/**
 * A concrete {@link Stack} of {@link Liquid} types. The count is the fluid
 * level in tile units (0–{@code 255} for a full tile), matching the
 * simulation layer.
 *
 * <p>Use {@link #of(Liquid, int)} to create stacks and {@link #EMPTY} (or
 * {@link Liquid#CATEGORY}) for the shared empty stack. Tanks are typed on
 * this class: {@code SimpleContainer<FluidStack>}, {@code Slot<FluidStack>}.
 *
 * @see Liquid
 * @see SimpleContainer
 */
public final class FluidStack extends Stack<Liquid, FluidStack> {
  /** The globally shared empty fluid stack. */
  public static final FluidStack EMPTY = new FluidStack(Liquids.EMPTY, 0, null);

  private FluidStack(Liquid type, int count, @Nullable CompoundNBT data) {
    super(type, count, data);
  }

  /**
   * Creates a fluid stack with the given count.
   *
   * @param type  the liquid type
   * @param count the unit count (fluid level)
   * @return a new stack
   */
  public static FluidStack of(Liquid type, int count) {
    return new FluidStack(type, count, null);
  }

  /**
   * Creates a single-unit fluid stack.
   *
   * @param type the liquid type
   * @return a new stack
   */
  public static FluidStack of(Liquid type) {
    return new FluidStack(type, 1, null);
  }

  @Override
  protected FluidStack copy(int count, @Nullable CompoundNBT data) {
    return new FluidStack(type, count, data);
  }
}
