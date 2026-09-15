/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Poly;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable local-space collision geometry supplied by one block tile. */
@FunctionalInterface
public interface Collision {
  Collision EMPTY = Collision.of(Poly.EMPTY);
  Collision CUBE = Collision.of(Rectangle.of(0.0F, 0.0F, 1.0F, 1.0F), false, true);
  Collision HALF = Collision.of(Rectangle.of(0.0F, 0.0F, 1.0F, 0.5F), false, true);
  Collision HALF_UP = Collision.of(Poly.of(Rectangle.of(0.0F, 0.5F, 1.0F, 0.5F)), false, true);
  Collision SLOPE_LEFT_DOWN = Collision.of(Poly.of(new Vector2(0.0F, 0.0F), new Vector2(1.0F, 0.0F), new Vector2(1.0F, 1.0F)), false, true);
  Collision SLOPE_RIGHT_DOWN = Collision.of(Poly.of(new Vector2(0.0F, 0.0F), new Vector2(1.0F, 0.0F), new Vector2(0.0F, 1.0F)), false, true);
  Collision SLOPE_LEFT_UP = Collision.of(Poly.of(new Vector2(0.0F, 1.0F), new Vector2(1.0F, 0.0F), new Vector2(1.0F, 1.0F)), false, true);
  Collision SLOPE_RIGHT_UP = Collision.of(Poly.of(new Vector2(0.0F, 0.0F), new Vector2(1.0F, 1.0F), new Vector2(0.0F, 1.0F)), false, true);
  Collision PLATFORM = Collision.of(Poly.of(Rectangle.of(0.0F, 0.8F, 1.0F, 0.2F)), true, false);

  Poly shape();

  default boolean isPlatform() {
    return false;
  }

  /** Whether a vertical blocking face may be used by the stair solver. */
  default boolean canStepOn() {
    return false;
  }

  static Collision of(Rectangle shape) {
    return of(Poly.of(shape), false, false);
  }

  static Collision of(Poly shape) {
    return of(shape, false, false);
  }

  static Collision of(Rectangle shape, boolean oneWay, boolean stepable) {
    return of(Poly.of(shape), oneWay, stepable);
  }

  static Collision of(Poly shape, boolean oneWay, boolean stepable) {
    return new  Collision() {
      @Override
      public Poly shape() {
        return shape;
      }

      @Override
      public boolean isPlatform() {
        return oneWay;
      }

      @Override
      public boolean canStepOn() {
        return stepable;
      }
    };
  }
}
