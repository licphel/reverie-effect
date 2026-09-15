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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor-backed provider. Generation is parallel; chunk publication and all
 * mutable world access remain on the caller/world thread.
 */
public final class AsyncChunkProvider implements ChunkProvider {
  private final Level level;
  private final List<ChunkGenerator> generators;
  private final long seed;
  private final ExecutorService executor;
  private final ConcurrentHashMap<Long, CompletableFuture<PreparedChunk>> jobs =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, CompletableFuture<Chunk>> requested =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, ChunkStatus> statuses = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<PreparedChunk> completed = new ConcurrentLinkedQueue<>();
  private final java.util.Set<Long> publishing = ConcurrentHashMap.newKeySet();

  public AsyncChunkProvider(Level level, List<? extends ChunkGenerator> generators, long seed) {
    this(level, generators, seed,
        Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
  }

  public AsyncChunkProvider(Level level, List<? extends ChunkGenerator> generators,
                            long seed, int workerCount) {
    this.level = Objects.requireNonNull(level, "level");
    this.generators = List.copyOf(generators);
    if (workerCount <= 0) {
      throw new IllegalArgumentException("workerCount must be positive: " + workerCount);
    }
    this.seed = seed;
    this.executor = Executors.newFixedThreadPool(workerCount, task -> {
      var thread = new Thread(task, "rf-chunk-generator");
      thread.setDaemon(true);
      return thread;
    });
  }

  @Override
  public Chunk getIfLoaded(ChunkPos pos) {
    var chunk = level.findChunk(pos);
    return chunk != null && chunk.isLoaded ? chunk : null;
  }

  @Override
  public ChunkStatus status(ChunkPos pos) {
    var chunk = level.findChunk(pos);
    if (chunk != null && chunk.isLoaded) {
      return ChunkStatus.READY;
    }
    return statuses.getOrDefault(pos.asLong(), ChunkStatus.ABSENT);
  }

  @Override
  public CompletableFuture<Chunk> requestAsync(ChunkPos pos) {
    long key = Objects.requireNonNull(pos, "pos").asLong();
    Chunk loaded = getIfLoaded(pos);
    if (loaded != null) {
      return CompletableFuture.completedFuture(loaded);
    }
    if (statuses.get(key) == ChunkStatus.FAILED) {
      jobs.remove(key);
      requested.remove(key);
      statuses.remove(key, ChunkStatus.FAILED);
    }
    var future = requested.computeIfAbsent(key, ignored -> new CompletableFuture<>());
    schedule(pos);
    return future;
  }

  @Override
  public int pump(int maxChunks) {
    if (maxChunks <= 0) {
      return 0;
    }
    int published = 0;
    while (published < maxChunks) {
      var result = completed.poll();
      if (result == null) {
        break;
      }
      long key = result.pos().asLong();
      if (jobs.containsKey(key)) {
        try {
          publish(result);
          completeRequest(result.pos(), level.findChunk(result.pos()));
          published++;
        } catch (RuntimeException exception) {
          failRequest(key, exception);
          throw exception;
        }
      }
    }
    return published;
  }

  @Override
  public void publish(PreparedChunk result) {
    Objects.requireNonNull(result, "result");
    long key = result.pos().asLong();
    Chunk chunk = level.ensureChunk(result.pos());
    if (chunk.isLoaded) {
      // A result can remain in the completion queue when a synchronous
      // request won the race with the async consumer. Never replay steps
      // (especially entity spawns) into an already-published chunk.
      statuses.put(key, ChunkStatus.READY);
      jobs.remove(key);
      completeRequest(result.pos(), chunk);
      return;
    }
    if (!publishing.add(key)) {
      return;
    }
    try {
      var queue = new GenerationQueue();
      queue.addAll(result.steps());
      queue.generate(level);
      if (!result.loadedFromSave()) {
        level.replayGeneration(result.pos());
      }
      chunk.setLoaded(true);
      level.onChunkReady(chunk);
      if (!result.loadedFromSave()) {
        level.populateChunk(result.pos());
      }
      statuses.put(key, ChunkStatus.READY);
      jobs.remove(key);
      completeRequest(result.pos(), chunk);
    } catch (RuntimeException exception) {
      statuses.put(key, ChunkStatus.FAILED);
      throw exception;
    } finally {
      publishing.remove(key);
    }
  }

  @Override
  public Chunk request(ChunkPos pos) {
    Objects.requireNonNull(pos, "pos");
    Chunk existing = level.findChunk(pos);
    long key = pos.asLong();
    if (existing != null && existing.isLoaded) {
      return existing;
    }
    if (publishing.contains(key)) {
      return existing != null ? existing : level.ensureChunk(pos);
    }

    CompletableFuture<PreparedChunk> job = jobs.get(key);
    if (job == null) {
      return generateSynchronously(pos);
    }
    try {
      PreparedChunk result = job.join();
      if (jobs.get(key) == job) {
        publish(result);
        completeRequest(pos, level.findChunk(pos));
      }
    } catch (CompletionException exception) {
      statuses.put(key, ChunkStatus.FAILED);
      failRequest(key, exception.getCause() == null ? exception : exception.getCause());
      throw exception;
    }
    Chunk loaded = level.findChunk(pos);
    if (loaded == null || !loaded.isLoaded) {
      throw new IllegalStateException("chunk was not published: " + pos);
    }
    return loaded;
  }

  @Override
  public List<ChunkGenerator> generators() {
    return generators;
  }

  @Override
  public long seed() {
    return seed;
  }

  @Override
  public void close() {
    executor.shutdownNow();
    jobs.clear();
    requested.forEach((key, future) -> future.cancel(false));
    requested.clear();
    completed.clear();
  }

  private CompletableFuture<PreparedChunk> schedule(ChunkPos pos) {
    Objects.requireNonNull(pos, "pos");
    long key = pos.asLong();
    return jobs.computeIfAbsent(key, ignored -> {
      statuses.put(key, ChunkStatus.QUEUED);
      var future = CompletableFuture.supplyAsync(() -> {
        statuses.put(key, ChunkStatus.PREPARING);
        return prepare(pos);
      }, executor);
      future.whenComplete((result, error) -> {
        if (error != null) {
          statuses.put(key, ChunkStatus.FAILED);
          failRequest(key, error);
        } else {
          completed.add(result);
        }
      });
      return future;
    });
  }

  private Chunk generateSynchronously(ChunkPos pos) {
    long key = pos.asLong();
    Chunk chunk = level.ensureChunk(pos);
    if (!publishing.add(key)) {
      return chunk;
    }
    try {
      var saved = readSaved(pos);
      boolean shouldPopulate;
      if (saved.isPresent()) {
        var result = saved.get();
        if (!pos.equals(result.pos())) {
          throw new IllegalStateException("saved chunk position mismatch: " + result.pos());
        }
        shouldPopulate = !result.loadedFromSave();
        var queue = new GenerationQueue();
        queue.addAll(result.steps());
        queue.generate(level);
        if (!result.loadedFromSave()) {
          level.replayGeneration(pos);
        }
      } else {
        shouldPopulate = true;
        generate(level, pos);
        level.replayGeneration(pos);
      }
      chunk.setLoaded(true);
      level.onChunkReady(chunk);
      if (shouldPopulate) {
        level.populateChunk(pos);
      }
      statuses.put(key, ChunkStatus.READY);
      completeRequest(pos, chunk);
      return chunk;
    } catch (RuntimeException exception) {
      statuses.put(key, ChunkStatus.FAILED);
      failRequest(key, exception);
      throw exception;
    } finally {
      publishing.remove(key);
    }
  }

  private void completeRequest(ChunkPos pos, @org.jspecify.annotations.Nullable Chunk chunk) {
    var future = requested.remove(pos.asLong());
    if (future == null) {
      return;
    }
    if (chunk == null || !chunk.isLoaded) {
      future.completeExceptionally(new IllegalStateException("chunk was not published: " + pos));
    } else {
      future.complete(chunk);
    }
  }

  private void failRequest(long key, Throwable error) {
    var future = requested.remove(key);
    if (future != null) {
      future.completeExceptionally(error);
    }
  }
}
