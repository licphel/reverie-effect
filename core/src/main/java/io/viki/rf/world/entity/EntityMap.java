package io.viki.rf.world.entity;

import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runtime entity registry and chunk-space index, owned by one level thread. */
@NullMarked
public final class EntityMap {
  private final Map<UUID, Entity> byId = new HashMap<>();
  private final Map<Long, Set<UUID>> byChunk = new HashMap<>();

  public void add(Entity entity) {
    Entity previous = byId.putIfAbsent(entity.entityId(), entity);
    if (previous != null && previous != entity) {
      throw new IllegalStateException("Duplicate entity id: " + entity.entityId());
    }
    index(entity, entity.position().toChunkPos());
  }

  public @Nullable Entity get(UUID entityId) {
    return byId.get(entityId);
  }

  public boolean contains(UUID entityId) {
    return byId.containsKey(entityId);
  }

  public void remove(UUID entityId) {
    Entity entity = byId.remove(entityId);
    if (entity != null) {
      Set<UUID> ids = byChunk.get(entity.chunkPos.asLong());
      if (ids != null) {
        ids.remove(entityId);
        if (ids.isEmpty()) byChunk.remove(entity.chunkPos.asLong());
      }
    }
  }

  public Collection<Entity> all() {
    return List.copyOf(byId.values());
  }

  public Collection<Entity> inChunk(ChunkPos chunkPos) {
    Set<UUID> ids = byChunk.get(chunkPos.asLong());
    if (ids == null || ids.isEmpty()) return List.of();
    var result = new ArrayList<Entity>(ids.size());
    for (UUID id : ids) {
      Entity entity = byId.get(id);
      if (entity != null) result.add(entity);
    }
    return result;
  }

  public void updateIndex(Entity entity) {
    ChunkPos target = entity.position().toChunkPos();
    if (!target.equals(entity.chunkPos)) {
      Set<UUID> old = byChunk.get(entity.chunkPos.asLong());
      if (old != null) {
        old.remove(entity.entityId());
        if (old.isEmpty()) byChunk.remove(entity.chunkPos.asLong());
      }
      index(entity, target);
    }
  }

  private void index(Entity entity, ChunkPos chunkPos) {
    entity.chunkPos = chunkPos;
    byChunk.computeIfAbsent(chunkPos.asLong(), ignored -> new HashSet<>()).add(entity.entityId());
  }
}
