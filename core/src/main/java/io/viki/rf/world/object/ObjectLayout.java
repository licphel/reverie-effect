/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import java.util.List;

/** Computes the immutable active Part list for one object variant. */
@FunctionalInterface
public interface ObjectLayout {
  List<MultiBlockPart> create(int variant);
}
