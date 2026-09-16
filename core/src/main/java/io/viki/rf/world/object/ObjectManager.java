/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.momentum.registry.Registry;
import io.viki.momentum.util.Identifier;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * World-thread façade for object lookup and complete-footprint transactions.
 * Static definition data and mutable canonical records remain separate.
 */
public final class ObjectManager {
  private final Level level;
  private final Registry<MultiBlockDefinition> definitions;
  private final ObjectStore store = new ObjectStore();

  public ObjectManager(Level level, Registry<MultiBlockDefinition> definitions) {
    this.level = Objects.requireNonNull(level, "level");
    this.definitions = Objects.requireNonNull(definitions, "definitions");
  }

  public Registry<MultiBlockDefinition> definitions() {
    return definitions;
  }

  public ObjectResolution resolveIfLoaded(BlockPos position) {
    Objects.requireNonNull(position, "position");
    Chunk chunk = level.getChunk(position.toChunkPos());
    if (chunk == null) {
      return new ObjectResolution(ObjectResolution.Status.CHUNK_UNLOADED,
          null, null, null, null);
    }
    ObjectPartRef ref = chunk.getObjectPart(position.x(), position.y());
    if (ref == null) {
      return new ObjectResolution(ObjectResolution.Status.NONE, null, null, null, null);
    }

    MultiBlockDefinition definition = definitions.get(ref.definitionId());
    if (definition == null) {
      return new ObjectResolution(ObjectResolution.Status.CORRUPT, ref, null, null, null);
    }
    ObjectRecord record = store.get(ref.objectId());
    if (record == null) {
      return new ObjectResolution(ObjectResolution.Status.ROOT_UNLOADED,
          ref, definition, null, null);
    }
    if (record.lifecycle() == ObjectLifecycle.REMOVED
        || record.lifecycle() == ObjectLifecycle.REMOVING) {
      return new ObjectResolution(ObjectResolution.Status.TOMBSTONED,
          ref, definition, record, null);
    }
    if (record.lifecycle() == ObjectLifecycle.PREPARING) {
      return new ObjectResolution(ObjectResolution.Status.ORPHANED,
          ref, definition, record, null);
    }
    if (record.revision() != ref.revision()
        || !record.definitionId().equals(ref.definitionId())
        || record.mirrorX() != ref.mirrorX()) {
      return new ObjectResolution(ObjectResolution.Status.STALE_REVISION,
          ref, definition, record, null);
    }
    try {
      MultiBlockPart part = definition.part(ref.variant(), ref.partIndex());
      return new ObjectResolution(ObjectResolution.Status.RESOLVED,
          ref, definition, record, part);
    } catch (RuntimeException exception) {
      return new ObjectResolution(ObjectResolution.Status.CORRUPT,
          ref, definition, record, null);
    }
  }

  public @Nullable ObjectPartRef partAtIfLoaded(BlockPos position) {
    Objects.requireNonNull(position, "position");
    Chunk chunk = level.getChunk(position.toChunkPos());
    return chunk == null ? null : chunk.getObjectPart(position.x(), position.y());
  }

  public @Nullable MultiBlockDefinition definition(ObjectPartRef ref) {
    Objects.requireNonNull(ref, "ref");
    return definitions.get(ref.definitionId());
  }

  public @Nullable MultiBlockPart part(ObjectPartRef ref) {
    MultiBlockDefinition definition = definition(ref);
    if (definition == null) {
      return null;
    }
    try {
      return definition.part(ref.variant(), ref.partIndex());
    } catch (RuntimeException exception) {
      return null;
    }
  }

  public Optional<ObjectRecord> place(MultiBlockDefinition definition, BlockPos rootPosition,
                                      int variant) {
    return place(definition, rootPosition, false, variant);
  }

  public Optional<ObjectRecord> place(MultiBlockDefinition definition, BlockPos rootPosition,
                                      boolean mirrorX, int variant) {
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(rootPosition, "rootPosition");
    Identifier definitionId = Objects.requireNonNull(definitions.getId(definition),
        "Object definition is not registered: " + definition);
    List<MultiBlockPart> parts = definition.parts(variant);
    List<BlockPos> footprint = definition.footprint(rootPosition, mirrorX, variant);
    Set<ChunkPos> involvedChunks = chunkSet(footprint);

    for (ChunkPos chunk : involvedChunks) {
      level.requestChunk(chunk);
    }
    if (!canOccupy(footprint)) {
      return Optional.empty();
    }
    if (!hasSupport(definition, rootPosition)) {
      return Optional.empty();
    }

    long objectId = store.allocateId();
    ObjectRecord preparing = new ObjectRecord(objectId, definitionId, rootPosition, mirrorX,
        1, ObjectLifecycle.PREPARING, involvedChunks, variant);
    store.put(preparing);
    try {
      for (int i = 0; i < parts.size(); i++) {
        BlockPos position = footprint.get(i);
        Chunk chunk = requireLoaded(position.toChunkPos());
        chunk.setObjectPart(position.x(), position.y(), new ObjectPartRef(objectId,
            rootPosition, definitionId, mirrorX, i, preparing.revision(), variant, 0));
      }
      ObjectRecord active = preparing.withLifecycle(ObjectLifecycle.ACTIVE);
      store.put(active);
      level.markObjectDirty(footprint);
      return Optional.of(active);
    } catch (RuntimeException exception) {
      for (BlockPos position : footprint) {
        Chunk chunk = level.getChunk(position.toChunkPos());
        if (chunk != null) {
          chunk.removeObjectPart(position.x(), position.y());
        }
      }
      store.put(preparing.withLifecycle(ObjectLifecycle.REMOVED));
      throw exception;
    }
  }

  public Optional<ObjectRecord> breakAt(BlockPos position) {
    Objects.requireNonNull(position, "position");
    Chunk target = level.requestChunk(position.toChunkPos());
    ObjectPartRef ref = target.getObjectPart(position.x(), position.y());
    if (ref == null) {
      return Optional.empty();
    }
    ObjectRecord active = store.get(ref.objectId());
    if (active == null || active.lifecycle() != ObjectLifecycle.ACTIVE
        || active.revision() != ref.revision()) {
      return Optional.empty();
    }
    MultiBlockDefinition definition = definitions.get(active.definitionId());
    if (definition == null) {
      return Optional.empty();
    }
    if (definition.breakPolicy() != ObjectBreakPolicy.WHOLE_OBJECT) {
      throw new IllegalStateException("Unsupported object break policy: " + definition.breakPolicy());
    }

    List<BlockPos> footprint = definition.footprint(active.rootPosition(), active.mirrorX(),
        active.variant());
    for (ChunkPos chunk : active.involvedChunks()) {
      level.requestChunk(chunk);
    }
    if (!matches(active, footprint)) {
      return Optional.empty();
    }

    ObjectRecord removing = active.withLifecycle(ObjectLifecycle.REMOVING);
    store.put(removing);
    for (BlockPos part : footprint) {
      requireLoaded(part.toChunkPos()).removeObjectPart(part.x(), part.y());
    }
    ObjectRecord removed = removing.withLifecycle(ObjectLifecycle.REMOVED);
    store.put(removed);
    level.markObjectDirty(footprint);
    return Optional.of(removed);
  }

  /** Rehydrates local Part references after a chunk becomes readable. */
  public void onChunkReady(Chunk chunk) {
    Objects.requireNonNull(chunk, "chunk");
    for (ObjectRecord record : store.records()) {
      if (record.lifecycle() != ObjectLifecycle.ACTIVE
          || !record.involvedChunks().contains(chunk.chunkPos)) {
        continue;
      }
      MultiBlockDefinition definition = definitions.get(record.definitionId());
      if (definition == null) {
        continue;
      }
      List<MultiBlockPart> parts = definition.parts(record.variant());
      for (int i = 0; i < parts.size(); i++) {
        BlockPos position = definition.partPosition(record.rootPosition(), record.mirrorX(),
            record.variant(), i);
        if (!position.toChunkPos().equals(chunk.chunkPos)) {
          continue;
        }
        ObjectPartRef existing = chunk.getObjectPart(position.x(), position.y());
        ObjectPartRef expected = new ObjectPartRef(record.objectId(), record.rootPosition(),
            record.definitionId(), record.mirrorX(), i, record.revision(),
            record.variant(), 0);
        if (existing == null) {
          chunk.setObjectPart(position.x(), position.y(), expected);
        } else if (!existing.equals(expected)) {
          throw new IllegalStateException("Conflicting object Part at " + position
              + ": expected " + expected + ", found " + existing);
        }
      }
    }
  }

  private boolean canOccupy(List<BlockPos> footprint) {
    for (BlockPos position : footprint) {
      if (partAtIfLoaded(position) != null) {
        return false;
      }
      BlockState state = level.getBlockIfLoaded(position.x(), position.y());
      if (!state.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private boolean hasSupport(MultiBlockDefinition definition, BlockPos rootPosition) {
    if (definition.anchors().isEmpty()) {
      return true;
    }
    boolean any = false;
    for (BlockPos anchor : definition.anchors()) {
      BlockPos support = rootPosition.offset(anchor.x(), anchor.y());
      boolean solid = level.shapeAtIfLoaded(support.x(), support.y()) == Shape.SOLID;
      any |= solid;
      if (!solid && !definition.requireAnyAnchor()) {
        return false;
      }
    }
    return !definition.requireAnyAnchor() || any;
  }

  private boolean matches(ObjectRecord record, List<BlockPos> footprint) {
    for (int i = 0; i < footprint.size(); i++) {
      BlockPos position = footprint.get(i);
      ObjectPartRef ref = requireLoaded(position.toChunkPos())
          .getObjectPart(position.x(), position.y());
      if (ref == null || ref.objectId() != record.objectId()
          || !ref.definitionId().equals(record.definitionId())
          || ref.revision() != record.revision() || ref.partIndex() != i
          || ref.mirrorX() != record.mirrorX()) {
        return false;
      }
    }
    return true;
  }

  private Chunk requireLoaded(ChunkPos position) {
    Chunk chunk = level.getChunk(position);
    if (chunk == null) {
      throw new IllegalStateException("Object transaction requires loaded chunk: " + position);
    }
    return chunk;
  }

  private static Set<ChunkPos> chunkSet(List<BlockPos> positions) {
    Set<ChunkPos> result = new HashSet<>();
    for (BlockPos position : positions) {
      result.add(position.toChunkPos());
    }
    return Set.copyOf(result);
  }
}
