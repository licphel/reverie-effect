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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loading boundary between worker generation and the world thread. Worker
 * code only creates {@link PreparedChunk}; map/chunk mutation belongs to
 * {@link #publish(PreparedChunk)} on the world thread.
 */
public interface ChunkProvider extends AutoCloseable {
  /** A chunk is visible only after it is fully published. */
  @Nullable Chunk getIfLoaded(ChunkPos pos);

  ChunkStatus status(ChunkPos pos);

  /** Enqueues asynchronous generation; the future completes after world-thread publication. */
  CompletableFuture<Chunk> requestAsync(ChunkPos pos);

  /** Publishes completed worker results synchronously on the world thread. */
  int pump(int maxChunks);

  /** Applies one result atomically and routes every generated write by its map call. */
  void publish(PreparedChunk result);

  /** Synchronous boundary used when gameplay needs a new chunk immediately. */
  Chunk request(ChunkPos pos);

  List<ChunkGenerator> generators();

  long seed();

  /**
   * Saved-chunk hook. Storage is intentionally disabled for now. A decoder
   * should return the same step representation and report corrupt data rather
   * than treating it as an absent save.
   */
  default Optional<PreparedChunk> readSaved(ChunkPos pos) {
    Objects.requireNonNull(pos, "pos");
    // TODO load/decode the saved chunk.
    return Optional.empty();
  }

  /** Reads saved data or runs every generator into one queue. */
  default PreparedChunk prepare(ChunkPos pos) {
    Objects.requireNonNull(pos, "pos");
    var saved = readSaved(pos);
    if (saved.isPresent()) {
      var result = saved.get();
      if (!pos.equals(result.pos())) {
        throw new IllegalStateException("saved chunk position mismatch: " + result.pos());
      }
      return result.loadedFromSave()
          ? result
          : new PreparedChunk(result.pos(), result.steps(), true);
    }

    var queue = new GenerationQueue();
    var tasks = new java.util.ArrayList<CompletableFuture<Void>>();
    for (ChunkGenerator generator : generators()) {
      tasks.add(generator.generateAsync(pos, seed(), queue));
    }
    CompletableFuture<?>[] futures = tasks.toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).join();
    return new PreparedChunk(pos, queue.seal(), false);
  }

  /** Runs the synchronous path of every generator on the world thread. */
  default void generate(ChunkMap map, ChunkPos pos) {
    Objects.requireNonNull(map, "map");
    Objects.requireNonNull(pos, "pos");
    var queue = new GenerationQueue();
    for (ChunkGenerator generator : generators()) {
      generator.generate(pos, seed(), queue);
    }
    queue.generate(map);
  }

  @Override
  void close();
}
