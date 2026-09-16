/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

/** Immutable, thread-safe conversion between world and physical length units. */
public enum Ruler {
  /** SI metre. */
  METER(1.0),
  /** World tile; one tile represents half a metre. */
  TILE(0.5);

  private final double metersPerUnit;

  Ruler(double metersPerUnit) {
    this.metersPerUnit = metersPerUnit;
  }

  public double metersPerUnit() {
    return metersPerUnit;
  }

  public double toMeter(double value) {
    requireFinite(value);
    return value * metersPerUnit;
  }

  public float toMeter(float value) {
    requireFinite(value);
    return value * (float) metersPerUnit;
  }

  public double fromMeter(double meters) {
    requireFinite(meters);
    return meters / metersPerUnit;
  }

  public float fromMeter(float meters) {
    requireFinite(meters);
    return meters / (float) metersPerUnit;
  }

  public double convertTo(double value, Ruler target) {
    return target.fromMeter(toMeter(value));
  }

  public float convertTo(float value, Ruler target) {
    return target.fromMeter(toMeter(value));
  }

  private static void requireFinite(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Length value must be finite: " + value);
    }
  }

  private static void requireFinite(float value) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException("Length value must be finite: " + value);
    }
  }
}
