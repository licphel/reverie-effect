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

package io.viki.rf.world.level;

/** One immutable operation emitted by a chunk generator. */
public interface GenerationStep {
  /** Larger values are applied later and therefore win. */
  int priority();

  /** Stable generator/structure identifier; never include worker state. */
  String source();

  /** Stable sequence within {@link #source()}. */
  long sequence();

  /** Optional side effects to suppress; the data write is never suppressed. */
  int flags();

  /** The storage layer used for deterministic per-cell conflict handling. */
  Kind kind();

  /** Applies this operation to the provider's world-thread map. */
  void generate(ChunkMap map);

  /** Generation output layers. Entity positions are not restricted to cells. */
  enum Kind {
    BLOCK,
    WALL,
    SHAPE,
    LIQUID,
    ENTITY
  }

  static void check(int priority, String source, long sequence, int flags) {
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("generation source must not be empty");
    }
    if (sequence < 0) {
      throw new IllegalArgumentException("generation sequence must be >= 0: " + sequence);
    }
    if (!GenerationFlags.valid(flags)) {
      throw new IllegalArgumentException("unknown generation flags: " + flags);
    }
  }
}
