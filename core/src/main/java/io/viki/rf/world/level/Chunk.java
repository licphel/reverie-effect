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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.fluid.LiquidMap;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.object.ObjectPartRef;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;
import io.viki.momentum.util.Grid;
import org.jspecify.annotations.Nullable;
import io.viki.momentum.codec.nbt.CompoundNBT;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Chunk {
  private static final int SERIAL_VERSION = 1;
  /**
   * Per-cell metadata bytes in the block map. Object references are stored in
   * their own sparse table; the block map only owns tile-local shape data.
   */
  private static final int BLOCK_META_BYTES = 1;
  /** Metadata index of the tile's shape byte, see {@link TileShape}. */
  private static final int META_SHAPE = 0;
  public final Level level;
  public final ChunkPos chunkPos;
  private final Grid<BlockState> blockMap =
      new Grid<>(BlockState.BLOCK_STATE_PROPERTY_PALETTE, ChunkPos.SIZE, BLOCK_META_BYTES);
  private final Grid<BlockState> wallMap =
      new Grid<>(BlockState.BLOCK_STATE_PROPERTY_PALETTE, ChunkPos.SIZE, 2);
  /** Entity spawn descriptions emitted during generation, before entity factories run. */
  private final List<GeneratedEntity> generatedEntities = new ArrayList<>();
  private final LiquidMap liquidMap = new LiquidMap();
  private final Int2ObjectMap<ObjectPartRef> objectParts = new Int2ObjectOpenHashMap<>();
  public boolean isLoaded;
  /** Whether the block mesh of this chunk must be rebuilt. */
  public boolean frontDirty;
  /** Whether the wall mesh of this chunk must be rebuilt. */
  public boolean backDirty;
  /** Whether this chunk has active liquid that needs another simulation pass. */
  public boolean liquidPanic;
  private long revision;
  /** Revision of block, wall and shape data; liquid-only changes do not affect it. */
  private long terrainRevision;

  public Chunk(Level level, ChunkPos chunkPos) {
    this.level = level;
    this.chunkPos = chunkPos;
  }

  /** Serializes the complete tile layers of this chunk for saves and transport. */
  public CompoundNBT serialize() {
    var nbt = new CompoundNBT();
    nbt.putInt("version", SERIAL_VERSION);
    nbt.putInt("x", chunkPos.x());
    nbt.putInt("y", chunkPos.y());
    nbt.putLong("revision", revision);
    nbt.putLong("terrainRevision", terrainRevision);
    nbt.putBytes("blocks", blockMap.storage());
    nbt.putBytes("walls", wallMap.storage());
    byte[] liquidTypes = new byte[ChunkPos.SIZE * ChunkPos.SIZE];
    byte[] liquidLevels = new byte[liquidTypes.length * Integer.BYTES];
    ByteBuffer levels = ByteBuffer.wrap(liquidLevels).order(ByteOrder.LITTLE_ENDIAN);
    int startX = chunkPos.x() * ChunkPos.SIZE;
    int startY = chunkPos.y() * ChunkPos.SIZE;
    for (int localY = 0; localY < ChunkPos.SIZE; localY++) {
      for (int localX = 0; localX < ChunkPos.SIZE; localX++) {
        int index = localX + localY * ChunkPos.SIZE;
        int x = startX + localX;
        int y = startY + localY;
        liquidTypes[index] = liquidMap.liquidType(x, y);
        levels.putInt(index * Integer.BYTES, liquidMap.level(x, y));
      }
    }
    nbt.putBytes("liquidTypes", liquidTypes);
    nbt.putBytes("liquidLevels", liquidLevels);
    return nbt;
  }

  /** Reconstructs a chunk without invoking loading or generation. */
  public static Chunk deserialize(Level level, CompoundNBT nbt) {
    if (nbt.getInt("version", -1) != SERIAL_VERSION) {
      throw new IllegalArgumentException("Unsupported chunk version: " + nbt.getInt("version", -1));
    }
    var chunk = new Chunk(level, new ChunkPos(nbt.getInt("x", 0), nbt.getInt("y", 0)));
    chunk.deserialize(nbt);
    return chunk;
  }

  /** Replaces serialized tile layers in place, preserving client-side entities. */
  public void deserialize(CompoundNBT nbt) {
    int x = nbt.getInt("x", Integer.MIN_VALUE);
    int y = nbt.getInt("y", Integer.MIN_VALUE);
    if (x != chunkPos.x() || y != chunkPos.y()) {
      throw new IllegalArgumentException("Chunk coordinates do not match target " + chunkPos);
    }
    copyRequired(nbt, "blocks", blockMap.storage());
    copyRequired(nbt, "walls", wallMap.storage());
    byte[] types = required(nbt, "liquidTypes", ChunkPos.SIZE * ChunkPos.SIZE);
    byte[] encodedLevels = required(nbt, "liquidLevels",
        ChunkPos.SIZE * ChunkPos.SIZE * Integer.BYTES);
    ByteBuffer levels = ByteBuffer.wrap(encodedLevels).order(ByteOrder.LITTLE_ENDIAN);
    int startX = chunkPos.x() * ChunkPos.SIZE;
    int startY = chunkPos.y() * ChunkPos.SIZE;
    for (int localY = 0; localY < ChunkPos.SIZE; localY++) {
      for (int localX = 0; localX < ChunkPos.SIZE; localX++) {
        int index = localX + localY * ChunkPos.SIZE;
        int amount = levels.getInt(index * Integer.BYTES);
        liquidMap.set(startX + localX, startY + localY,
            Liquids.byId(types[index] & 0xFF), amount);
      }
    }
    revision = nbt.getLong("revision", 0);
    terrainRevision = nbt.getLong("terrainRevision", revision);
    isLoaded = true;
    liquidPanic = true;
    frontDirty = true;
    backDirty = true;
  }

  public long revision() {
    return revision;
  }

  public long terrainRevision() {
    return terrainRevision;
  }

  private static void copyRequired(CompoundNBT nbt, String key, byte[] destination) {
    byte[] source = required(nbt, key, destination.length);
    System.arraycopy(source, 0, destination, 0, destination.length);
  }

  private static byte[] required(CompoundNBT nbt, String key, int length) {
    byte[] value = nbt.getBytes(key);
    if (value == null || value.length != length) {
      throw new IllegalArgumentException("Invalid chunk field " + key + ", expected " + length + " bytes");
    }
    return value;
  }

  // -- liquids -------------------------------------------------------------

  public void setLoaded(boolean loaded) {
    isLoaded = loaded;
  }

  /** The liquid level of a tile ({@code 0} = empty, {@code 255} = full). */
  public int getLiquidLevel(int wx, int wy) {
    return liquidMap.level(wx, wy);
  }

  /** The liquid id of a tile, see {@code Liquids#byId}. */
  public byte getLiquidType(int wx, int wy) {
    return liquidMap.liquidType(wx, wy);
  }

  public byte[] liquidSnapshot() {
    return liquidMap.snapshot();
  }

  public boolean applyLiquidSnapshot(byte[] data) {
    if (!liquidMap.load(data)) {
      return false;
    }
    liquidPanic = true;
    revision++;
    return true;
  }

  /** Sets the liquid of a tile; levels {@code <= 0} clear it. */
  public void setLiquid(int wx, int wy, Liquid liquid, int level) {
    int previousLevel = liquidMap.level(wx, wy);
    byte previousType = liquidMap.liquidType(wx, wy);
    liquidMap.set(wx, wy, liquid, level);
    if (previousLevel == liquidMap.level(wx, wy)
        && previousType == liquidMap.liquidType(wx, wy)) {
      return;
    }
    revision++;
    liquidPanic = true;
    notifyLiquidChanged(wx, wy);
  }

  // -- blocks ---------------------------------------------------------------

  /** Sets the liquid level of a tile; {@code <= 0} clears it. */
  public void setLiquidLevel(int wx, int wy, int level) {
    int previousLevel = liquidMap.level(wx, wy);
    byte previousType = liquidMap.liquidType(wx, wy);
    liquidMap.setLevel(wx, wy, level);
    if (previousLevel == liquidMap.level(wx, wy)
        && previousType == liquidMap.liquidType(wx, wy)) {
      return;
    }
    revision++;
    liquidPanic = true;
    notifyLiquidChanged(wx, wy);
  }

  /** Sets the liquid type of a tile. */
  public void setLiquidType(int wx, int wy, byte id) {
    if (liquidMap.liquidType(wx, wy) == id) {
      return;
    }
    liquidMap.setType(wx, wy, id);
    revision++;
    liquidPanic = true;
    notifyLiquidChanged(wx, wy);
  }

  public void setBlock(int wx, int wy, BlockState state) {
    blockMap.set(wx, wy, state);
    // A freshly placed block is a full cube. Object references are managed by
    // ObjectManager and are never encoded in block metadata.
    blockMap.setMetaByte(wx, wy, META_SHAPE, TileShape.FULL.id());
    frontDirty = true;
    revision++;
    terrainRevision++;
  }

  public void setBlock(BlockPos pos, BlockState state) {
    setBlock(pos.x(), pos.y(), state);
  }

  public BlockState getBlock(BlockPos pos) {
    return getBlock(pos.x(), pos.y());
  }

  public BlockState getBlock(int wx, int wy) {
    return blockMap.get(wx, wy);
  }

  // -- block shape ----------------------------------------------------------

  /** The shape byte of a tile, see {@link TileShape}. */
  public byte getBlockShape(int wx, int wy) {
    return blockMap.getMetaByte(wx, wy, META_SHAPE);
  }

  /** Sets the shape byte of a tile and invalidates the chunk mesh. */
  public void setBlockShape(int wx, int wy, byte shape) {
    blockMap.setMetaByte(wx, wy, META_SHAPE, shape);
    frontDirty = true;
    revision++;
    terrainRevision++;
  }

  /**
   * The effective fill of a tile: the block's intrinsic fill, carved by
   * the tile's shape byte (solid blocks shaped into slopes become
   * {@link Shape#PARTIAL}).
   */
  public Shape getEffectiveShape(int wx, int wy) {
    BlockState state = getBlock(wx, wy);
    return state.shape(getBlockShape(wx, wy));
  }

  // -- object parts ---------------------------------------------------------

  /** Returns the local object reference at a world cell, without loading data. */
  public @Nullable ObjectPartRef getObjectPart(int wx, int wy) {
    return objectParts.get(objectPartKey(wx, wy));
  }

  /** Installs one local object reference during an object transaction. */
  public void setObjectPart(int wx, int wy, ObjectPartRef part) {
    if (!chunkPos.contains(new BlockPos(wx, wy))) {
      throw new IllegalArgumentException("Object Part is outside chunk " + chunkPos + ": "
          + wx + "," + wy);
    }
    if (part == null) {
      throw new NullPointerException("Object Part reference must not be null");
    }
    objectParts.put(objectPartKey(wx, wy), part);
    frontDirty = true;
  }

  /** Removes one local object reference during an object transaction. */
  public void removeObjectPart(int wx, int wy) {
    objectParts.remove(objectPartKey(wx, wy));
    frontDirty = true;
  }

  /** Returns the local object references retained by this chunk. */
  public Collection<ObjectPartRef> objectParts() {
    return Collections.unmodifiableCollection(objectParts.values());
  }

  private static int objectPartKey(int wx, int wy) {
    return Math.floorMod(wx, ChunkPos.SIZE) + Math.floorMod(wy, ChunkPos.SIZE) * ChunkPos.SIZE;
  }

  // -- walls ----------------------------------------------------------------

  public void setWall(int wx, int wy, BlockState state) {
    wallMap.set(wx, wy, state);
    backDirty = true;
    revision++;
    terrainRevision++;
  }

  private void notifyLiquidChanged(int wx, int wy) {
    if (isLoaded) {
      level.onLiquidChanged(wx, wy);
    }
  }

  public void setWall(BlockPos pos, BlockState state) {
    setWall(pos.x(), pos.y(), state);
  }

  public BlockState getWall(BlockPos pos) {
    return getWall(pos.x(), pos.y());
  }

  public BlockState getWall(int wx, int wy) {
    return wallMap.get(wx, wy);
  }

  /** Adds an immutable generated spawn description to this chunk. */
  public void addGeneratedEntity(GeneratedEntity entity) {
    if (!generatedEntities.contains(entity)) {
      generatedEntities.add(entity);
    }
  }

  /** Returns generated spawn descriptions retained until entity creation. */
  public List<GeneratedEntity> generatedEntities() {
    return List.copyOf(generatedEntities);
  }
}
