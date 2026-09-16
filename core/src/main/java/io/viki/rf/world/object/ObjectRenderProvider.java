/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.rf.world.util.BlockPos;

import java.util.List;

@FunctionalInterface
public interface ObjectRenderProvider {
  List<ObjectPartRender> create(int variant, BlockPos rootPosition,
                                BlockPos partPosition, int partIndex,
                                MultiBlockPart part, long gameTicks);
}
