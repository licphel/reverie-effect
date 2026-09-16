/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** World-thread store for canonical object records and monotonic identities. */
public final class ObjectStore {
  private final Long2ObjectMap<ObjectRecord> records = new Long2ObjectOpenHashMap<>();
  private long nextId = 1L;

  public long allocateId() {
    if (nextId == Long.MAX_VALUE) {
      throw new IllegalStateException("Object id space is exhausted");
    }
    return nextId++;
  }

  public @Nullable ObjectRecord get(long objectId) {
    return records.get(objectId);
  }

  public void put(ObjectRecord record) {
    records.put(record.objectId(), record);
  }

  public Collection<ObjectRecord> records() {
    return List.copyOf(records.values());
  }
}
