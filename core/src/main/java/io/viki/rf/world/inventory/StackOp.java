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
 * Operation mode for inventory transactions.
 *
 * <p>Controls whether to preview or apply changes, and whether to bypass validation.
 *
 * <ul>
 *   <li>{@link #SIMULATE} - Dry run with validation</li>
 *   <li>{@link #FORCE_SIMULATE} - Dry run without validation</li>
 *   <li>{@link #EXECUTE} - Apply changes with validation</li>
 *   <li>{@link #FORCE_EXECUTE} - Apply changes without validation</li>
 * </ul>
 *
 * <p>For example, a shop transaction might simulate first to check affordability,
 * then execute if successful.
 */
public enum StackOp {
  /** Preview the operation, enforcing all validation rules. */
  SIMULATE,
  /** Preview the operation, bypassing validation rules (debug/admin use). */
  FORCE_SIMULATE,
  /** Apply the operation, enforcing all validation rules. */
  EXECUTE,
  /** Apply the operation, bypassing validation rules (debug/admin use). */
  FORCE_EXECUTE;

  /**
   * Returns the forced operation.
   *
   * @return the forced operation
   */
  public StackOp forced() {
    return switch (this) {
      case SIMULATE, FORCE_SIMULATE -> FORCE_SIMULATE;
      case EXECUTE, FORCE_EXECUTE -> FORCE_EXECUTE;
    };
  }

  /**
   * Returns whether this operation bypasses validation.
   *
   * @return true if FORCE_SIMULATE or FORCE_EXECUTE
   */
  public boolean force() {
    return this == FORCE_EXECUTE || this == FORCE_SIMULATE;
  }

  /**
   * Returns whether this operation applies changes to the container.
   *
   * @return true if EXECUTE or FORCE_EXECUTE
   */
  public boolean execute() {
    return this == EXECUTE || this == FORCE_EXECUTE;
  }

  /**
   * Returns whether this operation is a dry-run (does not modify state).
   *
   * @return true if SIMULATE or FORCE_SIMULATE
   */
  public boolean simulate() {
    return this == SIMULATE || this == FORCE_SIMULATE;
  }
}