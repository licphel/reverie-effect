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

package io.viki.rf.render.ambient;

import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.Locatable;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Resolves the sky used for a level and render position. */
@FunctionalInterface
public interface SkySupplier {
  /** Returns a supplier that never handles an input. */
  static SkySupplier none() {
    return (level, position) -> null;
  }

  /** Returns a supplier that always returns the given sky. */
  static SkySupplier constant(Sky sky) {
    Objects.requireNonNull(sky, "sky");
    return (level, position) -> sky;
  }

  /** Returns a sky, or {@code null} when this supplier does not match. */
  @Nullable Sky get(Level level, Locatable position);

  /** Appends a fallback supplier after this supplier. */
  default SkySupplier or(SkySupplier fallback) {
    return new DelegatingSkySupplier(this, fallback);
  }
}
