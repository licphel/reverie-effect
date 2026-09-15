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

package io.viki.rf.world.light;

/**
 * Blends an existing light value with an incoming one, per color channel.
 *
 * <p>{@code src} is the light already in the tile, {@code dst} the incoming light. The
 * choice of formula trades color fidelity against brightness control:
 * <ul>
 *   <li>{@link #MAX} — per-channel maximum. Fastest; a bright white light washes out
 *       colored light entirely (its high green/blue channels win).</li>
 *   <li>{@link #ADDITIVE} — unbounded addition. Colored light keeps its hue, but
 *       overlapping strong lights blow out to white.</li>
 *   <li>{@link #ADDITIVE_CAP} — additive with a luminance-aware cap:
 *       {@code src + dst * (1 - src/cap)}. Dark areas add fully (hue kept), bright
 *       areas asymptotically accept no more light (no whiteout).</li>
 * </ul>
 */
@FunctionalInterface
public interface CompositionFormula {
  /** Per-channel maximum: the brighter of the two values wins. */
  CompositionFormula MAX = Math::max;
  /** Unbounded per-channel sum of the two values. */
  CompositionFormula ADDITIVE = Float::sum;
  /** Additive blend with a luminance-aware cap. */
  CompositionFormula ADDITIVE_CAP = (src, dst) -> Math.min(src + dst * (1F - src / 2.5F), 1.25F);

  /**
   * Combines the existing light value with the incoming one.
   *
   * @param src the light already present
   * @param dst the incoming light
   * @return the combined light value
   */
  float blend(float src, float dst);
}
