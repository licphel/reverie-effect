/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import java.util.List;

import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Poly;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

/** Resolves polygon movement using Starbound-style SAT separation. */
final class MotionSolver {
  private MotionSolver() {
  }

  static MotionResult move(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, Vector2 movement, boolean ignorePlatforms,
      boolean wasOnGround, boolean allowSlopeSliding,
      boolean enableStepUp, boolean enableStepDown,
      float maximumStepHeight, float timeStep) {
    if (!Double.isFinite(bodyX) || !Double.isFinite(bodyY)
        || !Float.isFinite(movement.x()) || !Float.isFinite(movement.y())
        || !Float.isFinite(maximumStepHeight) || maximumStepHeight < 0.0F
        || !Float.isFinite(timeStep) || timeStep <= 0.0F) {
      throw new IllegalArgumentException(
          "Motion coordinates, movement, step height, and timestep must be finite");
    }
    if (body.isEmpty()) {
      return new MotionResult(new Vector2(movement.x(), movement.y()), Vector2.ZERO,
          null, null, false);
    }

    double movementX = movement.x();
    double movementY = movement.y();
    double separationTolerance = Math.max(PhysicsConstants.CONTACT_EPSILON,
        PhysicsConstants.SEPARATION_TOLERANCE_PER_TICK * timeStep
            * PhysicsConstants.NOMINAL_TICKS_PER_SECOND);
    double maximumCorrection = Math.max(maximumStepHeight, separationTolerance)
        + PhysicsConstants.SKIN_WIDTH + Math.max(0.0, -movementY);
    @Nullable MoveCandidate selected = null;

    // OpenStarbound's first pass is a directional SAT correction. Horizontal
    // movement uses it for slope/step traversal; a resting non-sliding body
    // also needs it so gravity cannot turn a sloped contact into lateral drift.
    boolean traversingSurface = enableStepUp
        && Math.abs(movementX) > PhysicsConstants.MOTION_EPSILON;
    boolean lockingSurface = wasOnGround && !allowSlopeSliding
        && Math.abs(movementX) <= PhysicsConstants.MOTION_EPSILON;
    if ((traversingSurface || lockingSurface)
        && movementY <= PhysicsConstants.CONTACT_EPSILON) {
      @Nullable SeparationResult upward = directionalSeparation(colliders, body,
          bodyX + movementX, bodyY + movementY, ignorePlatforms,
          bodyY, movementY, maximumCorrection, separationTolerance);
      if (upward != null
          && (lockingSurface || acceptsUpwardCorrection(upward, movementX, movementY,
              maximumStepHeight, separationTolerance))) {
        selected = MoveCandidate.fromMovement(movementX, movementY, upward);
      }
    }

    if (selected == null) {
      selected = regularMovement(colliders, body, bodyX, bodyY, movementX, movementY,
          ignorePlatforms, maximumCorrection, separationTolerance);
    }
    if (selected == null) {
      selected = recoverFromUnsolvableMovement(colliders, body, bodyX, bodyY,
          movementX, movementY, maximumCorrection, separationTolerance);
    }

    if (selected != null && enableStepDown && movementX != 0.0
        && movementY <= PhysicsConstants.CONTACT_EPSILON
        && selected.displacementY <= separationTolerance) {
      @Nullable MoveCandidate stepDown = tryStepDown(colliders, body, bodyX, bodyY,
          movementX, movementY, ignorePlatforms, maximumStepHeight,
          maximumCorrection, separationTolerance, selected);
      if (stepDown != null
          && horizontalProgress(stepDown.displacementX, movementX)
              >= horizontalProgress(selected.displacementX, movementX)
          && stepDown.displacementY < selected.displacementY - separationTolerance) {
        selected = stepDown;
      }
    }

    if (selected == null) {
      return new MotionResult(Vector2.ZERO, movement.negate(), null, null, true);
    }

    if (selected.contact == null) {
      @Nullable MotionContact support = probeSupport(colliders, body,
          bodyX + selected.displacementX, bodyY + selected.displacementY,
          ignorePlatforms, separationTolerance);
      if (support != null) {
        selected.contact = support;
      }
    }
    return new MotionResult(
        new Vector2(toFloat(selected.displacementX), toFloat(selected.displacementY)),
        new Vector2(toFloat(selected.correctionX), toFloat(selected.correctionY)),
        selected.contact, selected.collision, false);
  }

  @Nullable
  private static MoveCandidate regularMovement(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, double movementX, double movementY,
      boolean ignorePlatforms, double maximumCorrection, double separationTolerance) {
    @Nullable SeparationResult separation = regularSeparation(colliders, body,
        bodyX + movementX, bodyY + movementY, ignorePlatforms, bodyY, movementY,
        maximumCorrection, separationTolerance);
    if (separation == null) {
      return null;
    }
    return MoveCandidate.fromMovement(movementX, movementY, separation);
  }

  @Nullable
  private static MoveCandidate recoverFromUnsolvableMovement(List<WorldCollider> colliders,
      Poly body, double bodyX, double bodyY, double movementX, double movementY,
      double maximumCorrection, double separationTolerance) {
    @Nullable SeparationResult separation = regularSeparation(colliders, body, bodyX, bodyY,
        true, bodyY, 0.0, maximumCorrection, separationTolerance);
    if (separation == null) {
      return null;
    }
    double displacementX = separation.correctionX;
    double displacementY = separation.correctionY;
    return new MoveCandidate(displacementX, displacementY,
        displacementX - movementX, displacementY - movementY,
        separation.contact, separation.collision);
  }

  @Nullable
  private static SeparationResult regularSeparation(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, boolean ignorePlatforms,
      double oneWayBodyY, double oneWayMovementY,
      double maximumCorrection, double separationTolerance) {
    SeparationResult total = new SeparationResult();
    double currentX = bodyX;
    double currentY = bodyY;
    for (int i = 0; i < PhysicsConstants.MAXIMUM_SEPARATION_LOOPS; i++) {
      SeparationResult separation = separate(colliders, body, currentX, currentY,
          ignorePlatforms, false, oneWayBodyY, oneWayMovementY,
          maximumCorrection, separationTolerance);
      total.merge(separation);
      currentX += separation.correctionX;
      currentY += separation.correctionY;
      if (!withinCorrectionLimit(total, maximumCorrection, separationTolerance)) {
        return null;
      }
      if (separation.solutionFound
          && isLegalPosition(colliders, body, currentX, currentY, separationTolerance)) {
        total.solutionFound = true;
        return total;
      }
    }
    return null;
  }

  @Nullable
  private static SeparationResult directionalSeparation(List<WorldCollider> colliders,
      Poly body, double bodyX, double bodyY, boolean ignorePlatforms,
      double oneWayBodyY, double oneWayMovementY,
      double maximumCorrection, double separationTolerance) {
    SeparationResult separation = separate(colliders, body, bodyX, bodyY,
        ignorePlatforms, true, oneWayBodyY, oneWayMovementY,
        maximumCorrection, separationTolerance);
    if (!separation.solutionFound
        || !withinCorrectionLimit(separation, maximumCorrection, separationTolerance)
        || !isLegalPosition(colliders, body,
            bodyX + separation.correctionX, bodyY + separation.correctionY,
            separationTolerance)) {
      return null;
    }
    return separation;
  }

  private static SeparationResult separate(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, boolean ignorePlatforms, boolean upward,
      double oneWayBodyY, double oneWayMovementY,
      double maximumPlatformCorrection, double separationTolerance) {
    SeparationResult result = new SeparationResult();
    double correctedX = bodyX;
    double correctedY = bodyY;
    for (WorldCollider collider : colliders) {
      if (collider.initiallyOverlapping()
          || collider.oneWay()
          && (ignorePlatforms || !activeOneWay(collider, body, oneWayBodyY, oneWayMovementY))) {
        continue;
      }
      if (!boundsIntersect(body.bounds(), correctedX, correctedY,
          collider.shape().bounds(), collider.x(), collider.y(), separationTolerance)) {
        continue;
      }

      for (int bodyPart = 0; bodyPart < body.convexCount(); bodyPart++) {
        for (int worldPart = 0; worldPart < collider.shape().convexCount(); worldPart++) {
          SatResult intersection;
          if (upward) {
            intersection = SatSolver.directionalTranslation(body, bodyPart,
                correctedX, correctedY, collider.shape(), worldPart,
                collider.x(), collider.y(), 0.0, 1.0, false);
          } else if (collider.oneWay()) {
            // Platforms may only be separated vertically, like Starbound's
            // directional SAT platform branch.
            intersection = SatSolver.directionalTranslation(body, bodyPart,
                correctedX, correctedY, collider.shape(), worldPart,
                collider.x(), collider.y(), 0.0, 1.0, true);
          } else {
            intersection = SatSolver.minimumTranslation(body, bodyPart,
                correctedX, correctedY, collider.shape(), worldPart,
                collider.x(), collider.y());
          }
          if (!intersection.intersects()) {
            continue;
          }

          double correctionX = intersection.x();
          double correctionY = intersection.y();
          double correctionLengthSquared = correctionX * correctionX
              + correctionY * correctionY;
          if (!Double.isFinite(correctionLengthSquared)) {
            result.solutionFound = false;
            continue;
          }
          if ((upward || collider.oneWay())
              && correctionLengthSquared <= separationTolerance * separationTolerance) {
            // The shapes intersect, but this pair cannot be separated on the
            // requested line. Let the caller fall back to normal SAT.
            result.solutionFound = false;
            continue;
          }
          if (collider.oneWay()
              && (correctionY <= separationTolerance
                  || correctionY > maximumPlatformCorrection + separationTolerance)) {
            continue;
          }

          correctedX += correctionX;
          correctedY += correctionY;
          result.correctionX += correctionX;
          result.correctionY += correctionY;
          result.intersects = true;
          result.record(correctionX, correctionY, collider, separationTolerance);
        }
      }
    }

    if (result.intersects
        && !isLegalPosition(colliders, body, correctedX, correctedY, separationTolerance)) {
      result.solutionFound = false;
    }
    return result;
  }

  private static boolean acceptsUpwardCorrection(SeparationResult separation,
      double movementX, double movementY, float maximumStepHeight,
      double separationTolerance) {
    if (!separation.solutionFound
        || Math.abs(separation.correctionX) > separationTolerance) {
      return false;
    }

    // The height check intentionally uses the raw SAT correction. SKIN_WIDTH
    // is only a separation aid and must not make an otherwise legal step too
    // high. Equality at the configured height is accepted by epsilon.
    double rise = movementY + separation.correctionY;
    if (!Double.isFinite(rise) || rise < -separationTolerance
        || rise >= maximumStepHeight + PhysicsConstants.CONTACT_EPSILON) {
      return false;
    }
    if (rise <= separationTolerance) {
      return true;
    }

    Vector2 correctedMovement = new Vector2(toFloat(movementX + separation.correctionX),
        toFloat(movementY + separation.correctionY));
    double correctionLength = Math.sqrt(
        separation.correctionX * separation.correctionX
            + separation.correctionY * separation.correctionY);
    double horizontalLength = Math.abs(correctedMovement.x());
    double verticalLength = Math.abs(correctedMovement.y());
    boolean shallowSurface = correctionLength <= PhysicsConstants.SURFACE_SLOPE_CORRECTION_LIMIT
        || (horizontalLength > separationTolerance
            && Math.atan2(verticalLength, horizontalLength)
                < PhysicsConstants.SURFACE_SLOPE_ANGLE);
    return separation.stepable || shallowSurface;
  }

  @Nullable
  private static MoveCandidate tryStepDown(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, double movementX, double movementY,
      boolean ignorePlatforms, float maximumStepHeight, double maximumCorrection,
      double separationTolerance, MoveCandidate selected) {
    if (maximumStepHeight <= separationTolerance) {
      return null;
    }
    double targetX = bodyX + selected.displacementX;
    double targetY = bodyY + selected.displacementY;
    double downwardDistance = maximumStepHeight + PhysicsConstants.SKIN_WIDTH;
    @Nullable SeparationResult separation = directionalSeparation(colliders, body,
        targetX, targetY - downwardDistance, ignorePlatforms,
        targetY, -downwardDistance, maximumCorrection, separationTolerance);
    if (separation == null || separation.correctionY <= separationTolerance
        || Math.abs(separation.correctionX) > separationTolerance) {
      return null;
    }

    double finalX = targetX + separation.correctionX;
    double finalY = targetY - downwardDistance + separation.correctionY;
    double drop = finalY - targetY;
    if (drop >= -separationTolerance
        || drop < -maximumStepHeight - PhysicsConstants.CONTACT_EPSILON
        || !isLegalPosition(colliders, body, finalX, finalY, separationTolerance)) {
      return null;
    }

    MoveCandidate result = new MoveCandidate(finalX - bodyX, finalY - bodyY,
        separation.correctionX, separation.correctionY,
        selected.contact, selected.collision);
    result.merge(separation);
    return result;
  }

  @Nullable
  private static MotionContact probeSupport(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, boolean ignorePlatforms, double separationTolerance) {
    double probeDistance = PhysicsConstants.GROUND_PROBE_DISTANCE;
    @Nullable SeparationResult separation = directionalSeparation(colliders, body,
        bodyX, bodyY - probeDistance, ignorePlatforms,
        bodyY, -probeDistance, probeDistance + PhysicsConstants.SKIN_WIDTH,
        separationTolerance);
    if (separation == null || separation.correctionY <= separationTolerance) {
      return null;
    }
    return separation.contact;
  }

  private static boolean activeOneWay(WorldCollider collider, Poly body,
      double bodyY, double movementY) {
    if (movementY >= -PhysicsConstants.CONTACT_EPSILON) {
      return false;
    }
    double bodyBottom = bodyY + body.minY();
    double platformTop = collider.y() + collider.shape().maxY();
    return bodyBottom >= platformTop - PhysicsConstants.CONTACT_EPSILON;
  }

  private static boolean isLegalPosition(List<WorldCollider> colliders, Poly body,
      double bodyX, double bodyY, double separationTolerance) {
    for (WorldCollider collider : colliders) {
      if (collider.initiallyOverlapping() || collider.oneWay()
          || !boundsIntersect(body.bounds(), bodyX, bodyY,
          collider.shape().bounds(), collider.x(), collider.y(), separationTolerance)) {
        continue;
      }
      for (int bodyPart = 0; bodyPart < body.convexCount(); bodyPart++) {
        for (int worldPart = 0; worldPart < collider.shape().convexCount(); worldPart++) {
          SatResult result = SatSolver.minimumTranslation(body, bodyPart, bodyX, bodyY,
              collider.shape(), worldPart, collider.x(), collider.y());
          if (result.intersects()
              && result.distanceSquared() > separationTolerance * separationTolerance) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean boundsIntersect(Rectangle first, double firstX, double firstY,
      Rectangle second, double secondX, double secondY, double tolerance) {
    return first.minX() + firstX < second.maxX() + secondX + tolerance
        && first.maxX() + firstX > second.minX() + secondX - tolerance
        && first.minY() + firstY < second.maxY() + secondY + tolerance
        && first.maxY() + firstY > second.minY() + secondY - tolerance;
  }

  private static boolean withinCorrectionLimit(SeparationResult result,
      double maximumCorrection, double separationTolerance) {
    double lengthSquared = result.correctionX * result.correctionX
        + result.correctionY * result.correctionY;
    double limit = maximumCorrection + separationTolerance;
    return Double.isFinite(lengthSquared) && lengthSquared <= limit * limit;
  }

  private static double horizontalProgress(double displacementX, double movementX) {
    return displacementX * Math.signum(movementX);
  }

  @Nullable
  private static MotionContact steeperContact(@Nullable MotionContact first,
      @Nullable MotionContact second) {
    if (first == null) {
      return second;
    }
    if (second == null || first.normal().y() >= second.normal().y()) {
      return first;
    }
    return second;
  }

  private static float toFloat(double value) {
    if (!Double.isFinite(value) || value > Float.MAX_VALUE || value < -Float.MAX_VALUE) {
      throw new IllegalStateException("Motion displacement cannot be represented as a float");
    }
    return (float) value;
  }

  private static final class SeparationResult {
    private double correctionX;
    private double correctionY;
    private boolean solutionFound = true;
    private boolean intersects;
    private boolean stepable;
    @Nullable private MotionContact contact;
    @Nullable private MotionContact collision;
    private double collisionMagnitude;

    private void record(double correctionX, double correctionY, WorldCollider collider,
        double separationTolerance) {
      double magnitude = Math.sqrt(correctionX * correctionX + correctionY * correctionY);
      if (!Double.isFinite(magnitude) || magnitude <= separationTolerance) {
        return;
      }
      MotionContact contact = new MotionContact(
          new Vector2(toFloat(correctionX / magnitude), toFloat(correctionY / magnitude)),
          collider.friction());
      if (collision == null || magnitude > collisionMagnitude) {
        collision = contact;
        collisionMagnitude = magnitude;
      }
      // Match Starbound's grounding rule: any meaningful upward separation is
      // support. This deliberately includes a capsule's lower bevel resting
      // on the lip of a narrow hole even while its flat bottom is suspended.
      if (correctionY > separationTolerance) {
        this.contact = steeperContact(this.contact, contact);
      }
      stepable |= collider.stepable();
    }

    private void merge(SeparationResult other) {
      correctionX += other.correctionX;
      correctionY += other.correctionY;
      solutionFound &= other.solutionFound;
      intersects |= other.intersects;
      stepable |= other.stepable;
      contact = steeperContact(contact, other.contact);
      if (other.collision != null
          && (collision == null || other.collisionMagnitude > collisionMagnitude)) {
        collision = other.collision;
        collisionMagnitude = other.collisionMagnitude;
      }
    }
  }

  private static final class MoveCandidate {
    private final double displacementX;
    private final double displacementY;
    private final double correctionX;
    private final double correctionY;
    @Nullable private MotionContact contact;
    @Nullable private MotionContact collision;

    private MoveCandidate(double displacementX, double displacementY,
        double correctionX, double correctionY, @Nullable MotionContact contact,
        @Nullable MotionContact collision) {
      this.displacementX = displacementX;
      this.displacementY = displacementY;
      this.correctionX = correctionX;
      this.correctionY = correctionY;
      this.contact = contact;
      this.collision = collision;
    }

    private static MoveCandidate fromMovement(double movementX, double movementY,
        SeparationResult separation) {
      return new MoveCandidate(movementX + separation.correctionX,
          movementY + separation.correctionY, separation.correctionX,
          separation.correctionY, separation.contact, separation.collision);
    }

    private void merge(SeparationResult separation) {
      contact = steeperContact(contact, separation.contact);
      if (collision == null) {
        collision = separation.collision;
      }
    }
  }
}
