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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import io.viki.rf.Registries;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.fluid.FluidEngine;
import io.viki.rf.world.fluid.FluidStack;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.light.LightEngine;
import io.viki.rf.world.light.RelaxationLightEngine;
import io.viki.rf.world.object.ObjectManager;
import io.viki.rf.world.object.ObjectPartRef;
import io.viki.rf.GameConstants;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.entity.EntityMap;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.concurrent.CompletableFuture;

/**
 * A chunked 2D world.
 *
 * <p>Chunks are generated on demand via {@link ChunkGenerator}. The
 * level owns all chunks and provides tile and collision queries.
 */
public abstract class Level implements ChunkMap {
  /** Length of a full day in game ticks (20 Hz, 20 s per game minute, 24 h). */
  public static final long TICKS_PER_DAY = (long) GameConstants.TICKS_PER_SECOND * 20 * 24;
  /** World Y coordinate below which the player is considered underground. */
  public static final int SEA_LEVEL = 0;
  /**
   * Chunks farther than this (in chunk units) from the focus are unloaded.
   * 41×41 chunks cover the widest light window (512 tiles = 32 chunks) plus
   * margin; was 32 → 4225 chunks resident.
   */
  private final List<ChunkGenerator> generators;
  private final EntityMap entities = new EntityMap();
  /** Compatibility path for old tests/tools that directly fill a Chunk. */
  private final @Nullable BiConsumer<Chunk, Long> legacyGenerator;
  private final @Nullable ChunkProvider chunkProvider;
  private final long seed;
  private final Long2ObjectMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
  private final Set<Long> generating = new HashSet<>();
  private final Map<GenerationCell, GenerationStep> generationWinners = new HashMap<>();
  /** Accepted generation output retained so an unloaded target can be rebuilt. */
  private final Map<Long, List<GenerationStep>> generationHistory = new HashMap<>();
  private @Nullable GenerationStep currentGenerationStep;
  private final LightEngine lightEngine;
  private final FluidEngine fluidEngine;
  private final ObjectManager objectManager;
  private final ChunkManager chunkManager;
  private long ticks;
  /** Game time within the current day, advanced at the day clock's pace. */
  private double dayTicks;
  /** Multiplier on the day clock; 1 is real time. */
  private float timeScale = 1F;
  /** Notified with each chunk position that unloads (e.g. to release meshes). */
  private @Nullable Consumer<ChunkPos> unloadListener;
  /** Server-side sink for loaded liquid cells changed after initial chunk publication. */
  private @Nullable LongConsumer liquidChangeListener;

  /**
   * Creates a level with the given chunk generator.
   *
   * @param generator the chunk generator
   * @param seed      the world seed
   */
  @SuppressWarnings("this-escape")
  protected Level(ChunkGenerator generator, long seed) {
    this(List.of(Objects.requireNonNull(generator, "generator")), null, seed);
  }

  /** Creates a level with several generators sharing one queue per chunk. */
  @SuppressWarnings("this-escape")
  protected Level(List<? extends ChunkGenerator> generators, long seed) {
    this(List.copyOf(generators), null, seed);
  }

  /**
   * Compatibility constructor for the former {@code (Chunk, seed)} callback.
   * New world generation should use {@link ChunkGenerator}.
   */
  @SuppressWarnings("this-escape")
  protected Level(BiConsumer<Chunk, Long> legacyGenerator, long seed) {
    this(List.of(), Objects.requireNonNull(legacyGenerator, "legacyGenerator"), seed);
  }

  private Level(List<ChunkGenerator> generators,
                @Nullable BiConsumer<Chunk, Long> legacyGenerator, long seed) {
    this.generators = generators;
    this.legacyGenerator = legacyGenerator;
    this.seed = seed;
    chunkProvider = legacyGenerator == null
        ? new AsyncChunkProvider(this, generators, seed)
        : null;
    lightEngine = new RelaxationLightEngine(this);
    fluidEngine = new FluidEngine(this);
    objectManager = new ObjectManager(this, Registries.OBJECTS);
    chunkManager = new ChunkManager(this);
  }

  public LightEngine lightEngine() {
    return lightEngine;
  }

  public FluidEngine fluidEngine() {
    return fluidEngine;
  }

  /** Returns the multi-block object service. Object logic is not part of block storage. */
  public ObjectManager objects() {
    return objectManager;
  }

  public ChunkManager chunkManager() {
    return chunkManager;
  }

  public Collection<Chunk> loadedChunks() {
    return chunks.values();
  }

  /** The asynchronous provider, or {@code null} for the legacy callback constructor. */
  public @Nullable ChunkProvider chunkProvider() {
    return chunkProvider;
  }

  /** Synchronously loads a chunk and returns only after it is published. */
  public Chunk requestChunk(ChunkPos pos) {
    return chunkProvider == null ? getOrLoadChunk(pos) : chunkProvider.request(pos);
  }

  /** Requests a chunk asynchronously and returns its coalesced preparation task. */
  public CompletableFuture<Chunk> requestChunkAsync(ChunkPos pos) {
    return chunkProvider == null
        ? CompletableFuture.completedFuture(getOrLoadChunk(pos))
        : chunkProvider.requestAsync(pos);
  }

  /** Publishes completed background generation on the world thread. */
  public int pumpChunks(int maxChunks) {
    return chunkProvider == null ? 0 : chunkProvider.pump(maxChunks);
  }

  /** Stops background chunk workers. */
  public void close() {
    if (chunkProvider != null) {
      chunkProvider.close();
    }
  }

  // -- tick ----------------------------------------------------------------

  public void tick(double delta) {
    chunkManager.tick();
    tickEntities(delta, true);
  }

  protected void tickEntities(double delta, boolean simulateFluids) {
    ticks++;
    dayTicks += delta * GameConstants.TICKS_PER_SECOND * timeScale;
    if (simulateFluids) {
      fluidEngine.tick(delta);
    }
    for (Entity entity : entities.all()) {
      entity.tick(delta, this);
      entities.updateIndex(entity);
    }
  }

  /** Runtime entities are independent of chunk persistence and transport. */
  public EntityMap entities() {
    return entities;
  }

  public long getTicks() {
    return ticks;
  }

  /**
   * Returns the game time within the current day, in ticks (fractional).
   */
  public double ticksOfDay() {
    return dayTicks;
  }

  /**
   * Returns the multiplier on the day clock; 1 is real time.
   */
  public float timeScale() {
    return timeScale;
  }

  /**
   * Sets the multiplier on the day clock. Only the day clock is affected;
   * simulation ticks, fluids and entities keep their real-time pace.
   */
  public void setTimeScale(float timeScale) {
    this.timeScale = timeScale;
  }

  // -- chunks --------------------------------------------------------------

  /**
   * Returns the chunk at the given position, generating it if needed.
   */
  public Chunk getOrLoadChunk(ChunkPos pos) {
    long key = pos.asLong();
    var existing = chunks.get(key);
    if (existing != null && existing.isLoaded) {
      return existing;
    }

    var chunk = chunks.computeIfAbsent(key, k -> new Chunk(this, pos));
    generateChunk(chunk);
    return chunk;
  }

  /**
   * Returns the chunk at the given position, or {@code null} if not
   * loaded.
   */
  public @Nullable Chunk getChunk(ChunkPos pos) {
    Chunk chunk = chunks.get(pos.asLong());
    return chunk != null && chunk.isLoaded ? chunk : null;
  }

  /**
   * Returns the chunk containing the given block position by its packed
   * map key, or {@code null} if not loaded. Allocation-free lookup.
   */
  public @Nullable Chunk getChunkByKey(long key) {
    Chunk chunk = chunks.get(key);
    return chunk != null && chunk.isLoaded ? chunk : null;
  }

  /**
   * Returns the chunk containing the given block position, generating it
   * if needed. Allocation-free lookup.
   */
  public Chunk getOrLoadChunkByKey(long key) {
    var existing = chunks.get(key);
    if (existing != null && existing.isLoaded) {
      return existing;
    }
    var chunk = chunks.computeIfAbsent(key, k -> new Chunk(this, ChunkPos.fromLong(key)));
    generateChunk(chunk);
    return chunk;
  }

  /** Generates a chunk synchronously; nested cross-chunk writes reuse a placeholder. */
  private void generateChunk(Chunk chunk) {
    if (chunk.isLoaded || !generating.add(chunk.chunkPos.asLong())) {
      return;
    }
    try {
      if (legacyGenerator != null) {
        legacyGenerator.accept(chunk, seed);
      } else {
        if (chunkProvider == null) {
          throw new IllegalStateException("chunk provider is not initialized");
        }
        chunkProvider.request(chunk.chunkPos);
      }
      if (legacyGenerator != null) {
        chunk.setLoaded(true);
        objectManager.onChunkReady(chunk);
      }
    } finally {
      generating.remove(chunk.chunkPos.asLong());
    }
  }

  /** Raw provider access; the returned chunk may be a not-yet-loaded placeholder. */
  Chunk findChunk(ChunkPos pos) {
    return chunks.get(pos.asLong());
  }

  /** Returns or creates the provider's private placeholder. */
  Chunk ensureChunk(ChunkPos pos) {
    return chunks.computeIfAbsent(pos.asLong(), ignored -> new Chunk(this, pos));
  }

  void installChunk(Chunk chunk) {
    if (chunk.level != this) {
      throw new IllegalArgumentException("Cannot install a chunk owned by another level");
    }
    chunks.put(chunk.chunkPos.asLong(), chunk);
    onChunkReady(chunk);
  }

  /** Called by the provider after generation and saved data are fully published. */
  void onChunkReady(Chunk chunk) {
    objectManager.onChunkReady(chunk);
  }

  void populateChunk(ChunkPos chunkPos) {
    if (chunkProvider == null) {
      return;
    }
    for (ChunkGenerator generator : generators) {
      generator.populate(this, chunkPos, seed);
    }
  }

  public int loadedChunkCount() {
    return chunks.size();
  }

  /** Registers a callback invoked with each chunk position that unloads. */
  public void setUnloadListener(@Nullable Consumer<ChunkPos> listener) {
    this.unloadListener = listener;
  }

  public void setLiquidChangeListener(@Nullable LongConsumer listener) {
    liquidChangeListener = listener;
  }

  void onLiquidChanged(int x, int y) {
    LongConsumer listener = liquidChangeListener;
    if (listener != null) {
      listener.accept(((long) x << 32) | (y & 0xFFFFFFFFL));
    }
  }

  /**
   * Unloads every chunk farther than {@link #UNLOAD_RADIUS_CHUNKS} from the
   * focus, so explored areas do not accumulate for the whole session.
   */
  void unloadChunksOutside(ChunkManager manager) {
    var candidates = new ArrayList<ChunkPos>();
    for (var it = chunks.keySet().longIterator(); it.hasNext(); ) {
      long key = it.nextLong();
      ChunkPos pos = ChunkPos.fromLong(key);
      if (!manager.retains(pos)) {
        candidates.add(pos);
      }
    }
    for (ChunkPos pos : candidates) {
      unloadChunk(pos);
    }
  }

  /**
   * Removes the chunk from the world and its liquid cells from the fluid
   * engine (the chunk is regenerated on next access). Notifies the unload
   * listener so retained render meshes can be released.
   */
  public void unloadChunk(ChunkPos pos) {
    long chunkKey = pos.asLong();
    chunks.remove(chunkKey);
    // Winners are scoped to the currently resident chunk. The immutable step
    // history stays behind so cross-chunk structure output is replayed when
    // this chunk is streamed back in.
    generationWinners.entrySet().removeIf(entry ->
        ChunkPos.packBlockPosAsLong(entry.getKey().x(), entry.getKey().y()) == chunkKey);
    fluidEngine.delChunk(pos);
    if (unloadListener != null) {
      unloadListener.accept(pos);
    }
  }

  // -- tiles ---------------------------------------------------------------

  /**
   * Returns the block state at the given position, generating the
   * containing chunk if needed.
   */
  public BlockState getBlock(BlockPos pos) {
    return getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y())).getBlock(pos.x(), pos.y());
  }

  public BlockState getBlock(int x, int y) {
    return getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).getBlock(x, y);
  }

  /**
   * Non-blocking block lookup for rendering and other best-effort readers.
   * Unloaded chunks read as empty; use {@link #getBlock(int, int)} when a
   * caller requires the chunk to exist.
   */
  public BlockState getBlockIfLoaded(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getBlock(x, y) : BlockState.EMPTY;
  }


  /** Tracks one step while it calls {@link GenerationStep#generate(ChunkMap)}. */
  @Override
  public void apply(GenerationStep step) {
    Objects.requireNonNull(step, "step");
    GenerationStep previous = currentGenerationStep;
    currentGenerationStep = step;
    try {
      step.generate(this);
    } finally {
      currentGenerationStep = previous;
    }
  }

  @Override
  public void setBlock(int x, int y, BlockState state, int flags) {
    checkGenerationFlags(flags);
    if (!acceptGeneration(x, y, GenerationStep.Kind.BLOCK)) {
      return;
    }
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk.getObjectPart(x, y) != null) {
      throw new IllegalStateException("Generation attempted to overwrite object Part at "
          + x + "," + y);
    }
    chunk.setBlock(x, y, state);
    if (state.shape() == Shape.SOLID) {
      chunk.setLiquid(x, y, Liquids.EMPTY, 0);
    }
    if ((flags & GenerationFlags.NO_NEIGHBOR_UPDATES) == 0) {
      markDirtyNeighbours(x, y, true);
    }
  }

  @Override
  public void setWall(int x, int y, BlockState state, int flags) {
    checkGenerationFlags(flags);
    if (!acceptGeneration(x, y, GenerationStep.Kind.WALL)) {
      return;
    }
    getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).setWall(x, y, state);
    if ((flags & GenerationFlags.NO_NEIGHBOR_UPDATES) == 0) {
      markDirtyNeighbours(x, y, false);
    }
  }

  @Override
  public void setBlockShape(int x, int y, byte shape, int flags) {
    checkGenerationFlags(flags);
    if (!acceptGeneration(x, y, GenerationStep.Kind.SHAPE)) {
      return;
    }
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk.getObjectPart(x, y) != null) {
      throw new IllegalStateException("Generation attempted to overwrite object Part at "
          + x + "," + y);
    }
    chunk.setBlockShape(x, y, shape);
    if (chunk.getEffectiveShape(x, y) == Shape.SOLID) {
      chunk.setLiquid(x, y, Liquids.EMPTY, 0);
    }
    if ((flags & GenerationFlags.NO_NEIGHBOR_UPDATES) == 0) {
      markDirtyNeighbours(x, y, true);
    }
  }

  @Override
  public void setLiquid(int x, int y, byte liquidType, int amount, int flags) {
    checkGenerationFlags(flags);
    if (!acceptGeneration(x, y, GenerationStep.Kind.LIQUID)) {
      return;
    }
    getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y))
        .setLiquid(x, y, Liquids.byId(liquidType & 0xFF), amount);
    if (amount > 0 && (flags & GenerationFlags.NO_FLUID_UPDATES) == 0) {
      fluidEngine.join(x, y);
    }
  }

  @Override
  public void spawnEntity(double x, double y, String typeId,
                          Map<String, String> data, int flags) {
    checkGenerationFlags(flags);
    int wx = (int) Math.floor(x);
    int wy = (int) Math.floor(y);
    GenerationStep generationStep = currentGenerationStep;
    var entity = new GeneratedEntity(x, y,
        generationStep == null ? 0 : generationStep.priority(),
        generationStep == null ? "runtime" : generationStep.source(),
        generationStep == null ? 0 : generationStep.sequence(),
        flags, typeId, data);
    long chunkKey = ChunkPos.packBlockPosAsLong(wx, wy);
    getOrLoadChunkByKey(chunkKey).addGeneratedEntity(entity);
    if (generationStep != null) {
      rememberGeneration(chunkKey, generationStep);
    }
  }

  private void checkGenerationFlags(int flags) {
    if (!GenerationFlags.valid(flags)) {
      throw new IllegalArgumentException("unknown generation flags: " + flags);
    }
  }

  private boolean acceptGeneration(int x, int y, GenerationStep.Kind kind) {
    GenerationStep incoming = currentGenerationStep;
    if (incoming == null) {
      return true;
    }
    var key = new GenerationCell(x, y, kind);
    GenerationStep previous = generationWinners.get(key);
    if (previous == null) {
      generationWinners.put(key, incoming);
      rememberGeneration(ChunkPos.packBlockPosAsLong(x, y), incoming);
      return true;
    }
    int order = compareGeneration(previous, incoming);
    if (order < 0) {
      generationWinners.put(key, incoming);
      rememberGeneration(ChunkPos.packBlockPosAsLong(x, y), incoming);
      return true;
    }
    if (order == 0 && !previous.equals(incoming)) {
      throw new IllegalStateException("conflicting generation steps at " + x + "," + y);
    }
    return false;
  }

  /** Reapplies accepted generated output for a chunk after its base is rebuilt. */
  void replayGeneration(ChunkPos pos) {
    List<GenerationStep> history = generationHistory.get(pos.asLong());
    if (history == null || history.isEmpty()) {
      return;
    }
    var queue = new GenerationQueue();
    // Copy before applying: an entity/structure step may discover another
    // target chunk, but replaying an existing step must not mutate the list
    // currently being traversed.
    queue.addAll(List.copyOf(history));
    queue.generate(this);
  }

  private void rememberGeneration(long chunkKey, GenerationStep step) {
    var history = generationHistory.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
    if (!history.contains(step)) {
      history.add(step);
    }
  }

  private static int compareGeneration(GenerationStep left, GenerationStep right) {
    int result = Integer.compare(left.kind().ordinal(), right.kind().ordinal());
    if (result != 0) {
      return result;
    }
    result = Integer.compare(left.priority(), right.priority());
    if (result != 0) {
      return result;
    }
    result = left.source().compareTo(right.source());
    if (result != 0) {
      return result;
    }
    return Long.compare(left.sequence(), right.sequence());
  }

  private record GenerationCell(int x, int y, GenerationStep.Kind kind) {
  }

  public BlockState getWall(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getWall(x, y) : BlockState.EMPTY;
  }

  /** Non-blocking wall lookup; unloaded chunks read as empty. */
  public BlockState getWallIfLoaded(int x, int y) {
    return getWall(x, y);
  }

  /**
   * Sets the block at the given position.
   */
  public void setBlock(BlockPos pos, BlockState state) {
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y()));
    ObjectPartRef objectPart = chunk.getObjectPart(pos.x(), pos.y());
    if (objectPart != null) {
    if (state.isEmpty()) {
        objectManager.breakAt(pos);
        return;
      }
      throw new IllegalStateException("Cannot overwrite object Part at " + pos
          + "; break the complete object first");
    }
    chunk.setBlock(pos.x(), pos.y(), state);
    // a solid block replaces any liquid in its tile
    if (state.shape() == Shape.SOLID) {
      chunk.setLiquid(pos.x(), pos.y(), Liquids.EMPTY, 0);
    }
    markDirtyNeighbours(pos.x(), pos.y(), true);
  }

  public void setWall(BlockPos pos, BlockState state) {
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y()));
    chunk.setWall(pos.x(), pos.y(), state);
    markDirtyNeighbours(pos.x(), pos.y(), false);
  }

  // -- block shape ----------------------------------------------------------

  /** The shape byte of the tile, see {@link TileShape}. */
  public byte getBlockShape(int x, int y) {
    return getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).getBlockShape(x, y);
  }

  /** Non-blocking shape lookup; unloaded chunks read as a full tile. */
  public byte getBlockShapeIfLoaded(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getBlockShape(x, y) : TileShape.FULL.id();
  }

  /**
   * Sets the shape byte of a tile and invalidates the mesh of the tile's
   * chunk and its neighbours (their border pieces depend on this tile). A
   * shape carved back to a full cube replaces any liquid in its tile.
   */
  public void setBlockShape(int x, int y, byte shape) {
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk.getObjectPart(x, y) != null) {
      throw new IllegalStateException("Cannot shape an object Part at " + x + "," + y);
    }
    chunk.setBlockShape(x, y, shape);
    if (chunk.getEffectiveShape(x, y) == Shape.SOLID) {
      chunk.setLiquid(x, y, Liquids.EMPTY, 0);
    }
    markDirtyNeighbours(x, y, true);
  }

  /**
   * The effective fill of a tile (block type carved by its shape byte):
   * {@link Shape#SOLID} only for full cubes of solid blocks.
   */
  public Shape shapeAt(int x, int y) {
    return getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).getEffectiveShape(x, y);
  }

  /** Non-blocking effective-shape lookup; unloaded chunks are empty. */
  public Shape shapeAtIfLoaded(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getEffectiveShape(x, y) : Shape.VACUUM;
  }

  /** Returns a local object Part reference without causing a chunk load. */
  public @Nullable ObjectPartRef getObjectPartIfLoaded(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk == null ? null : chunk.getObjectPart(x, y);
  }

  /**
   * A tile on a chunk border changes the border pieces of the adjacent
   * chunk (its edges depend on this tile); a corner tile affects all four
   * surrounding chunks (Enchant NearDirty). {@code front} selects whether
   * the block or the wall layer of the neighbours is invalidated.
   */
  private void markDirtyNeighbours(int wx, int wy, boolean front) {
    int cs = ChunkPos.SIZE;
    int lx = Math.floorMod(wx, cs);
    int ly = Math.floorMod(wy, cs);
    int cx = Math.floorDiv(wx, cs);
    int cy = Math.floorDiv(wy, cs);
    if (lx == 0) {
      dirtyChunk(cx - 1, cy, front);
    }
    if (lx == cs - 1) {
      dirtyChunk(cx + 1, cy, front);
    }
    if (ly == 0) {
      dirtyChunk(cx, cy - 1, front);
    }
    if (ly == cs - 1) {
      dirtyChunk(cx, cy + 1, front);
    }
    if (lx == 0 && ly == 0) {
      dirtyChunk(cx - 1, cy - 1, front);
    }
    if (lx == cs - 1 && ly == 0) {
      dirtyChunk(cx + 1, cy - 1, front);
    }
    if (lx == 0 && ly == cs - 1) {
      dirtyChunk(cx - 1, cy + 1, front);
    }
    if (lx == cs - 1 && ly == cs - 1) {
      dirtyChunk(cx + 1, cy + 1, front);
    }
  }

  private void dirtyChunk(int cx, int cy, boolean front) {
    Chunk c = chunks.get(((long) cx << 32) | (cy & 0xFFFFFFFFL));
    if (c != null) {
      if (front) {
        c.frontDirty = true;
      } else {
        c.backDirty = true;
      }
    }
  }

  /** Invalidates all local cells and neighbour borders touched by an object transaction. */
  public void markObjectDirty(Collection<BlockPos> positions) {
    Objects.requireNonNull(positions, "positions");
    for (BlockPos position : positions) {
      Chunk chunk = getChunk(position.toChunkPos());
      if (chunk != null) {
        chunk.frontDirty = true;
      }
      markDirtyNeighbours(position.x(), position.y(), true);
    }
  }

  public long seed() {
    return seed;
  }

  // -- liquids -------------------------------------------------------------

  /**
   * Sets the liquid of a tile, generating the containing chunk if needed
   * and joining the tile to the fluid engine. Levels are discrete tile
   * units: {@code 255} is a full tile, the minimum amount is 1.
   */
  public void setLiquid(int x, int y, Liquid liquid, int level) {
    getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).setLiquid(x, y, liquid, level);
    if (level > 0) {
      fluidEngine.join(x, y);
    }
  }

  /**
   * Returns the liquid level of a tile, or {@code 0} if none or the chunk
   * is not loaded.
   */
  public int getLiquidLevel(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getLiquidLevel(x, y) : 0;
  }

  /** Returns the liquid id of a tile, or {@code 0} if none or unloaded. */
  public byte getLiquidType(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getLiquidType(x, y) : 0;
  }

  /** Sets the liquid level of a tile (engine-internal writes). */
  public void setLiquidLevel(int x, int y, int level) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk != null) {
      chunk.setLiquidLevel(x, y, level);
    }
  }

  /** Sets the liquid type of a tile (engine-internal writes). */
  public void setLiquidType(int x, int y, byte id) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk != null) {
      chunk.setLiquidType(x, y, id);
    }
  }

  /**
   * Returns the liquid stack of a tile, or an empty stack if none or the
   * chunk is not loaded.
   */
  public FluidStack getLiquidStack(int x, int y) {
    int lv = getLiquidLevel(x, y);
    return lv <= 0 ? FluidStack.EMPTY : FluidStack.of(Liquids.byId(getLiquidType(x, y)), lv);
  }

  public int getSeaLevel() {
    return SEA_LEVEL;
  }

  public int getSpaceLevel() {
    return 256;
  }
}
