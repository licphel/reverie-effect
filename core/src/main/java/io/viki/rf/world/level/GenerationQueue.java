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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Append-only queue shared by all generators for one requested chunk. */
public final class GenerationQueue {
  private static final Comparator<GenerationStep> ORDER = Comparator
      .<GenerationStep>comparingInt(step -> step.kind().ordinal())
      .thenComparingInt(GenerationStep::priority)
      .thenComparing(GenerationStep::source)
      .thenComparingLong(GenerationStep::sequence);

  private final Object lock = new Object();
  private final List<GenerationStep> pending = new ArrayList<>();
  private List<GenerationStep> result = List.of();
  private boolean sealed;

  /** Safe for multiple generators to call concurrently. */
  public void add(GenerationStep step) {
    Objects.requireNonNull(step, "step");
    synchronized (lock) {
      if (sealed) {
        throw new IllegalStateException("generation queue is already sealed");
      }
      pending.add(step);
    }
  }

  public void addAll(Iterable<? extends GenerationStep> steps) {
    Objects.requireNonNull(steps, "steps");
    for (GenerationStep step : steps) {
      add(step);
    }
  }

  /** Sorts, validates duplicate keys and freezes the queue. */
  public List<GenerationStep> seal() {
    synchronized (lock) {
      if (sealed) {
        return result;
      }
      pending.sort(ORDER);
      var frozen = new ArrayList<GenerationStep>(pending.size());
      GenerationStep previous = null;
      for (GenerationStep step : pending) {
        if (previous != null && sameKey(previous, step)) {
          if (!previous.equals(step)) {
            throw new IllegalStateException("conflicting generation steps from "
                + step.source() + "#" + step.sequence());
          }
          continue;
        }
        frozen.add(step);
        previous = step;
      }
      result = List.copyOf(frozen);
      pending.clear();
      sealed = true;
      return result;
    }
  }

  /** Applies the frozen order on the world thread. */
  public void generate(ChunkMap map) {
    Objects.requireNonNull(map, "map");
    for (GenerationStep step : seal()) {
      map.apply(step);
    }
  }

  public boolean isSealed() {
    synchronized (lock) {
      return sealed;
    }
  }

  private static boolean sameKey(GenerationStep left, GenerationStep right) {
    return left.kind() == right.kind()
        && left.priority() == right.priority()
        && left.sequence() == right.sequence()
        && left.source().equals(right.source());
  }
}
