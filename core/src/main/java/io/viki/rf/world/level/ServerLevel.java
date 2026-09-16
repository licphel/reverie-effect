/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */

package io.viki.rf.world.level;

import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;

import java.util.List;
import java.util.function.BiConsumer;

/** Authoritative level that owns generation and simulation. */
@SideOnly(dist = Dist.SERVER)
public final class ServerLevel extends Level {
  public ServerLevel(ChunkGenerator generator, long seed) {
    super(generator, seed);
  }

  public ServerLevel(List<? extends ChunkGenerator> generators, long seed) {
    super(generators, seed);
  }

  public ServerLevel(BiConsumer<Chunk, Long> legacyGenerator, long seed) {
    super(legacyGenerator, seed);
  }
}
