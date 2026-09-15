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

import io.viki.rf.world.util.ChunkPos;

import java.util.concurrent.CompletableFuture;

/** Synchronous and worker-safe generation paths for one chunk. */
public interface ChunkGenerator {
  /**
   * Emits steps synchronously on the caller's thread. This is the blocking
   * entry point used by initial world/bootstrap loading; generators that only
   * implement the worker path get this bridge automatically.
   */
  default void generate(ChunkPos chunkPos, long seed, GenerationQueue queue) {
    generateAsync(chunkPos, seed, queue).join();
  }

  /**
   * Populates a generated chunk on the world thread after its base terrain
   * has been published. Implementations may safely create world-owned
   * objects here.
   */
  default void populate(Level level, ChunkPos chunkPos, long seed) {
  }

  /**
   * Emits steps asynchronously. Implementations must perform the actual step
   * production off the world thread and complete only after all queue writes
   * are finished; the queue is safe for concurrent generators.
   */
  CompletableFuture<Void> generateAsync(ChunkPos chunkPos, long seed, GenerationQueue queue);
}
