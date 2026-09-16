/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import io.viki.momentum.math.shape.Poly;

/** Pure convex-component SAT and swept-SAT operations. */
final class SatSolver {
  private SatSolver() {
  }

  static SatResult minimumTranslation(Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY) {
    MutableResult result = new MutableResult();
    if (!evaluateAxes(first, firstPart, firstX, firstY,
        first, firstPart, firstX, firstY, second, secondPart, secondX, secondY, result)
        || !evaluateAxes(second, secondPart, secondX, secondY,
            first, firstPart, firstX, firstY, second, secondPart, secondX, secondY, result)) {
      return SatResult.none();
    }
    return result.toSatResult();
  }

  /**
   * Finds a separating translation constrained to one line through the
   * supplied direction. This is the directional SAT operation used by
   * OpenStarbound for its upward surface correction.
   *
   * <p>A zero translation with {@code intersects() == true} means that the
   * polygons intersect, but cannot be separated along the requested line.
   */
  static SatResult directionalTranslation(Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY,
      double directionX, double directionY, boolean chooseSign) {
    if (!Double.isFinite(directionX) || !Double.isFinite(directionY)) {
      throw new IllegalArgumentException("Directional SAT direction must be finite");
    }
    double directionLength = Math.sqrt(directionX * directionX + directionY * directionY);
    if (!Double.isFinite(directionLength)
        || directionLength <= PhysicsConstants.AXIS_EPSILON_SQUARED) {
      throw new IllegalArgumentException("Directional SAT direction must be non-zero");
    }

    DirectionalResult result = new DirectionalResult(
        directionX / directionLength, directionY / directionLength);
    if (!evaluateDirectionalAxes(first, firstPart, firstX, firstY,
        first, firstPart, firstX, firstY, second, secondPart, secondX, secondY, result,
        chooseSign, false)
        || !evaluateDirectionalAxes(second, secondPart, secondX, secondY,
            first, firstPart, firstX, firstY, second, secondPart, secondX, secondY, result,
            chooseSign, true)) {
      return SatResult.none();
    }
    if (!result.hasTranslation) {
      return new SatResult(true, 0.0F, 0.0F, 0.0F);
    }
    double translationX = result.directionX * result.translation * result.directionSign;
    double translationY = result.directionY * result.translation * result.directionSign;
    return new SatResult(true, toFloat(translationX), toFloat(translationY),
        toFloat(result.translation * result.translation));
  }

  static SweepResult sweepTranslation(Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY,
      double movementX, double movementY) {
    if (!Double.isFinite(firstX) || !Double.isFinite(firstY)
        || !Double.isFinite(secondX) || !Double.isFinite(secondY)
        || !Double.isFinite(movementX) || !Double.isFinite(movementY)) {
      throw new IllegalArgumentException("Swept SAT coordinates and movement must be finite");
    }

    SweepState state = new SweepState();
    if (!evaluateSweepAxes(first, firstPart, firstX, firstY,
        first, firstPart, firstX, firstY, second, secondPart, secondX, secondY,
        movementX, movementY, state)
        || !evaluateSweepAxes(second, secondPart, secondX, secondY,
            first, firstPart, firstX, firstY, second, secondPart, secondX, secondY,
            movementX, movementY, state)) {
      return SweepResult.none();
    }

    if (state.initiallyOverlapping) {
      double length = Math.sqrt(state.minimumDistanceSquared);
      if (!Double.isFinite(length) || length <= PhysicsConstants.CONTACT_EPSILON) {
        return SweepResult.none();
      }
      return SweepResult.penetration(
          toFloat(state.minimumX / length), toFloat(state.minimumY / length), toFloat(length));
    }

    if (!state.hasEntry || state.exit < -PhysicsConstants.TIME_EPSILON
        || state.entry > 1.0 + PhysicsConstants.TIME_EPSILON
        || state.entry > state.exit + PhysicsConstants.TIME_EPSILON) {
      return SweepResult.none();
    }
    double time = Math.max(0.0, Math.min(1.0, state.entry));
    return new SweepResult(true, toFloat(time),
        toFloat(state.normalX), toFloat(state.normalY), 0.0F);
  }

  private static boolean evaluateAxes(Poly axes, int axesPart, double axesX, double axesY,
      Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY, MutableResult result) {
    int count = axes.vertexCount(axesPart);
    for (int i = 0; i < count; i++) {
      int next = (i + 1) % count;
      double edgeX = axes.vertexX(axesPart, next) - axes.vertexX(axesPart, i);
      double edgeY = axes.vertexY(axesPart, next) - axes.vertexY(axesPart, i);
      double inverseLength = inverseLength(edgeX, edgeY);
      if (inverseLength == 0.0) {
        continue;
      }
      if (!evaluateAxis(first, firstPart, firstX, firstY,
          second, secondPart, secondX, secondY,
          -edgeY * inverseLength, edgeX * inverseLength, result)) {
        return false;
      }
    }
    return true;
  }

  private static boolean evaluateAxis(Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY,
      double axisX, double axisY, MutableResult result) {
    double firstMin = projectionMin(first, firstPart, firstX, firstY, axisX, axisY);
    double firstMax = projectionMax(first, firstPart, firstX, firstY, axisX, axisY);
    double secondMin = projectionMin(second, secondPart, secondX, secondY, axisX, axisY);
    double secondMax = projectionMax(second, secondPart, secondX, secondY, axisX, axisY);
    if (!overlaps(firstMin, firstMax, secondMin, secondMax)) {
      return false;
    }

    double positive = secondMax - firstMin;
    double negative = firstMax - secondMin;
    double distance = positive < negative ? positive : -negative;
    double distanceSquared = distance * distance;
    if (distanceSquared < result.distanceSquared) {
      result.distanceSquared = distanceSquared;
      result.x = axisX * distance;
      result.y = axisY * distance;
    }
    return true;
  }

  private static boolean evaluateSweepAxes(Poly axes, int axesPart,
      double axesX, double axesY, Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY,
      double movementX, double movementY, SweepState state) {
    int count = axes.vertexCount(axesPart);
    for (int i = 0; i < count; i++) {
      int next = (i + 1) % count;
      double edgeX = axes.vertexX(axesPart, next) - axes.vertexX(axesPart, i);
      double edgeY = axes.vertexY(axesPart, next) - axes.vertexY(axesPart, i);
      double inverseLength = inverseLength(edgeX, edgeY);
      if (inverseLength == 0.0) {
        continue;
      }
      if (!evaluateSweepAxis(first, firstPart, firstX, firstY,
          second, secondPart, secondX, secondY,
          -edgeY * inverseLength, edgeX * inverseLength,
          movementX, movementY, state)) {
        return false;
      }
    }
    return true;
  }

  private static boolean evaluateDirectionalAxes(Poly axes, int axesPart,
      double axesX, double axesY, Poly first, int firstPart, double firstX, double firstY,
      Poly second, int secondPart, double secondX, double secondY,
      DirectionalResult result, boolean chooseSign, boolean outward) {
    int count = axes.vertexCount(axesPart);
    for (int i = 0; i < count; i++) {
      int next = (i + 1) % count;
      double edgeX = axes.vertexX(axesPart, next) - axes.vertexX(axesPart, i);
      double edgeY = axes.vertexY(axesPart, next) - axes.vertexY(axesPart, i);
      double inverseLength = inverseLength(edgeX, edgeY);
      if (inverseLength == 0.0) {
        continue;
      }
      double axisX = -edgeY * inverseLength;
      double axisY = edgeX * inverseLength;
      if (outward) {
        axisX = -axisX;
        axisY = -axisY;
      }
      if (!evaluateDirectionalAxis(first, firstPart, firstX, firstY,
          second, secondPart, secondX, secondY,
          axisX, axisY, result, chooseSign)) {
        return false;
      }
    }
    return true;
  }

  private static boolean evaluateDirectionalAxis(Poly first, int firstPart,
      double firstX, double firstY, Poly second, int secondPart, double secondX, double secondY,
      double axisX, double axisY, DirectionalResult result, boolean chooseSign) {
    double firstMin = projectionMin(first, firstPart, firstX, firstY, axisX, axisY);
    double firstMax = projectionMax(first, firstPart, firstX, firstY, axisX, axisY);
    double secondMin = projectionMin(second, secondPart, secondX, secondY, axisX, axisY);
    double secondMax = projectionMax(second, secondPart, secondX, secondY, axisX, axisY);
    if (!overlaps(firstMin, firstMax, secondMin, secondMax)) {
      return false;
    }

    double axisDot = result.directionX * axisX + result.directionY * axisY;
    if (Math.abs(axisDot) <= PhysicsConstants.VELOCITY_EPSILON) {
      return true;
    }

    // This is the projection of the separation distance onto the requested
    // direction. The first polygon is moved; the second one stays fixed.
    double overlap = secondMax - firstMin;
    double projected = overlap / axisDot;
    if (chooseSign) {
      double distance = Math.abs(projected);
      if (distance > PhysicsConstants.VELOCITY_EPSILON && distance < result.translation) {
        result.translation = distance;
        result.directionSign = projected >= 0.0 ? 1.0 : -1.0;
        result.hasTranslation = true;
      }
    } else if (projected >= 0.0 && projected < result.translation) {
      result.translation = projected;
      result.directionSign = 1.0;
      result.hasTranslation = true;
    }
    return true;
  }

  private static boolean evaluateSweepAxis(Poly first, int firstPart,
      double firstX, double firstY, Poly second, int secondPart, double secondX, double secondY,
      double axisX, double axisY, double movementX, double movementY, SweepState state) {
    double firstMin = projectionMin(first, firstPart, firstX, firstY, axisX, axisY);
    double firstMax = projectionMax(first, firstPart, firstX, firstY, axisX, axisY);
    double secondMin = projectionMin(second, secondPart, secondX, secondY, axisX, axisY);
    double secondMax = projectionMax(second, secondPart, secondX, secondY, axisX, axisY);

    double positiveDepth = secondMax - firstMin;
    double negativeDepth = firstMax - secondMin;
    double depth = Math.min(positiveDepth, negativeDepth);
    if (depth <= PhysicsConstants.CONTACT_EPSILON) {
      state.initiallyOverlapping = false;
    } else if (depth < state.minimumDistance) {
      state.minimumDistance = depth;
      state.minimumDistanceSquared = depth * depth;
      if (positiveDepth < negativeDepth) {
        state.minimumX = axisX * positiveDepth;
        state.minimumY = axisY * positiveDepth;
      } else {
        state.minimumX = -axisX * negativeDepth;
        state.minimumY = -axisY * negativeDepth;
      }
    }

    double speed = movementX * axisX + movementY * axisY;
    if (Math.abs(speed) <= PhysicsConstants.VELOCITY_EPSILON) {
      return overlaps(firstMin, firstMax, secondMin, secondMax);
    }

    double firstEntry = (secondMin - firstMax) / speed;
    double secondEntry = (secondMax - firstMin) / speed;
    double axisEntry = Math.min(firstEntry, secondEntry);
    double axisExit = Math.max(firstEntry, secondEntry);
    if (axisEntry > state.entry) {
      state.entry = axisEntry;
      state.normalX = speed > 0.0 ? -axisX : axisX;
      state.normalY = speed > 0.0 ? -axisY : axisY;
      state.hasEntry = true;
    }
    state.exit = Math.min(state.exit, axisExit);
    return state.entry <= state.exit + PhysicsConstants.TIME_EPSILON;
  }

  private static boolean overlaps(double firstMin, double firstMax,
      double secondMin, double secondMax) {
    return firstMax > secondMin + PhysicsConstants.CONTACT_EPSILON
        && secondMax > firstMin + PhysicsConstants.CONTACT_EPSILON;
  }

  private static double projectionMin(Poly poly, int part, double offsetX, double offsetY,
      double axisX, double axisY) {
    double minimum = Double.POSITIVE_INFINITY;
    for (int i = 0; i < poly.vertexCount(part); i++) {
      minimum = Math.min(minimum,
          (poly.vertexX(part, i) + offsetX) * axisX
              + (poly.vertexY(part, i) + offsetY) * axisY);
    }
    return minimum;
  }

  private static double projectionMax(Poly poly, int part, double offsetX, double offsetY,
      double axisX, double axisY) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < poly.vertexCount(part); i++) {
      maximum = Math.max(maximum,
          (poly.vertexX(part, i) + offsetX) * axisX
              + (poly.vertexY(part, i) + offsetY) * axisY);
    }
    return maximum;
  }

  private static double inverseLength(double x, double y) {
    double lengthSquared = x * x + y * y;
    return lengthSquared <= PhysicsConstants.AXIS_EPSILON_SQUARED
        ? 0.0 : 1.0 / Math.sqrt(lengthSquared);
  }

  private static float toFloat(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("SAT produced a non-finite result");
    }
    return (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, value));
  }

  private static final class MutableResult {
    private double x;
    private double y;
    private double distanceSquared = Double.POSITIVE_INFINITY;

    private SatResult toSatResult() {
      if (!Double.isFinite(distanceSquared)) {
        return SatResult.none();
      }
      return new SatResult(true, toFloat(x), toFloat(y), toFloat(distanceSquared));
    }
  }

  private static final class SweepState {
    private boolean initiallyOverlapping = true;
    private boolean hasEntry;
    private double entry = Double.NEGATIVE_INFINITY;
    private double exit = Double.POSITIVE_INFINITY;
    private double normalX;
    private double normalY;
    private double minimumDistance = Double.POSITIVE_INFINITY;
    private double minimumDistanceSquared = Double.POSITIVE_INFINITY;
    private double minimumX;
    private double minimumY;

    private SweepState() {
    }
  }

  private static final class DirectionalResult {
    private final double directionX;
    private final double directionY;
    private double directionSign = 1.0;
    private double translation = Double.POSITIVE_INFINITY;
    private boolean hasTranslation;

    private DirectionalResult(double directionX, double directionY) {
      this.directionX = directionX;
      this.directionY = directionY;
    }
  }
}
