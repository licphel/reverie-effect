/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

/** Transaction lifecycle of a placed object record. */
public enum ObjectLifecycle {
  PREPARING,
  ACTIVE,
  REMOVING,
  REMOVED
}
