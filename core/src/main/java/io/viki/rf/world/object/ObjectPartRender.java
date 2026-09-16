/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;

import java.util.Objects;

/** Server-safe description of one textured visual piece inside a Part cell. */
public record ObjectPartRender(String texturePath, Rectangle source, Rectangle destination,
                               boolean flipX, Vector2 rotationPivot, float rotationRadians) {
  public static final ObjectPartRender NONE = new ObjectPartRender(
      "", Rectangle.ZERO, Rectangle.of(0.0F, 0.0F, 1.0F, 1.0F), false,
      Vector2.ZERO, 0.0F);

  public ObjectPartRender(String texturePath, Rectangle source, Rectangle destination,
                          boolean flipX) {
    this(texturePath, source, destination, flipX, Vector2.ZERO, 0.0F);
  }

  public ObjectPartRender {
    Objects.requireNonNull(texturePath, "texturePath");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(rotationPivot, "rotationPivot");
    if (!texturePath.isEmpty()
        && (source.width() <= 0.0F || source.height() <= 0.0F
        || source.minX() < 0.0F || source.minY() < 0.0F)) {
      throw new IllegalArgumentException("Visible object texture source must be positive: " + source);
    }
    if (destination.width() <= 0.0F || destination.height() <= 0.0F) {
      throw new IllegalArgumentException("Object render destination must be positive: " + destination);
    }
    if (!Float.isFinite(rotationPivot.x()) || !Float.isFinite(rotationPivot.y())
        || !Float.isFinite(rotationRadians)) {
      throw new IllegalArgumentException("Object render transform must be finite");
    }
  }

  public static ObjectPartRender textured(String texturePath, Rectangle source,
                                          Rectangle destination) {
    return new ObjectPartRender(texturePath, source, destination, false);
  }

  public ObjectPartRender rotated(Vector2 pivot, float radians) {
    return new ObjectPartRender(texturePath, source, destination, flipX, pivot, radians);
  }

  public boolean visible() {
    return !texturePath.isEmpty();
  }
}
