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

import io.viki.momentum.registry.RegistryEntry;

/**
 * A type that can be stored in a {@link Stack}.
 *
 * <p>Implementations define the maximum stack size via {@link #limit()}.
 * The default maximum is {@value #DEFAULT_LIMIT}. Every stack type is
 * registered in its category's registry (see {@link StackCategory#registry()}),
 * which also provides the ids used by stack codecs.
 *
 * @see Stack
 * @see Slot
 */
public interface Stackable extends RegistryEntry {
  /** The default maximum stack size. */
  int DEFAULT_LIMIT = 100;

  /**
   * Returns the maximum number of units of this type that can fit in a single stack.
   *
   * @return the maximum stack size; defaults to {@value #DEFAULT_LIMIT}
   */
  default int limit() {
    return DEFAULT_LIMIT;
  }

  /**
   * Returns whether this type is an empty type.
   *
   * @return true if is empty
   */
  boolean isEmpty();
}
