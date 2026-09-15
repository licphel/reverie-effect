/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import java.util.ArrayList;

import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Poly;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.util.Loop;
import io.viki.rf.world.fluid.FluidEngine;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.PrecisePos;

/**
 * Polygon-based moving world body. Instances are mutable and must remain
 * confined to the simulation thread.
 */
public abstract class Moveable {
  protected PrecisePos position = PrecisePos.ZERO;
  protected PrecisePos prevPosition = PrecisePos.ZERO;
  protected Vector2 velocity = Vector2.ZERO;
  protected boolean onGround;
  protected float gravityMultiplier = PhysicsConstants.DEFAULT_GRAVITY_MULTIPLIER;
  protected float bounceFactor = PhysicsConstants.STANDARD_RESTITUTION;
  protected boolean collisionEnabled = true;
  protected float mass = PhysicsConstants.DEFAULT_BODY_MASS;
  protected boolean frictionEnabled = true;
  protected float groundFriction = PhysicsConstants.STANDARD_FRICTION_COEFFICIENT;
  protected float density = PhysicsConstants.DEFAULT_BODY_DENSITY;
  protected float swimSpeed = PhysicsConstants.DEFAULT_SWIM_SPEED;
  protected float swimJumpSpeed = PhysicsConstants.DEFAULT_SWIM_JUMP_SPEED;
  protected float swimForce = PhysicsConstants.DEFAULT_SWIM_ACCELERATION;
  /** Maximum distance the surface correction may move this body. */
  protected float stepHeight = PhysicsConstants.DEFAULT_STEP_HEIGHT;
  /** Whether this body may use player-style artificial step-up movement. */
  protected boolean stepUpEnabled;
  /** Whether this body may be snapped down onto a nearby lower surface. */
  protected boolean stepDownEnabled;
  /** Whether gravity may make this body slide down sloped surfaces. */
  protected boolean slopeSliding;

  private final ArrayList<WorldCollider> collisionCandidates = new ArrayList<>();
  private boolean isFloating;
  private float liquidContact;
  private float surfaceFriction;
  private int fallThroughSustain;
  private Liquid dominantLiquid = Liquids.EMPTY;
  private boolean lastControlJump;

  public abstract Poly collision();

  public final Rectangle collisionBox() {
    return collision().bounds();
  }

  protected abstract float gravity();

  public PrecisePos position() {
    return position;
  }

  public PrecisePos center() {
    Rectangle bounds = bounds();
    return new PrecisePos(bounds.centralX(), bounds.centralY());
  }

  public Rectangle bounds() {
    return collisionBox().translate(position.xf(), position.yf());
  }

  public void setPosition(PrecisePos position) {
    this.position = position;
  }

  /** Sets a network/render correction without leaving a stale previous frame. */
  public void setPositionImmediate(PrecisePos position) {
    this.position = position;
    this.prevPosition = position;
  }

  /** Advances a replicated position while preserving render interpolation. */
  public void setPositionInterpolated(PrecisePos position) {
    this.prevPosition = this.position;
    this.position = position;
  }

  public PrecisePos renderPosition() {
    return PrecisePos.lerp(prevPosition, position, Loop.partialTicks());
  }

  public Vector2 velocity() {
    return velocity;
  }

  public void setVelocity(Vector2 velocity) {
    this.velocity = velocity;
  }

  public void setVelocity(float velocityX, float velocityY) {
    velocity = new Vector2(velocityX, velocityY);
  }

  public boolean onGround() {
    return onGround;
  }

  public boolean isFloating() {
    return isFloating;
  }

  public float gravityMultiplier() {
    return gravityMultiplier;
  }

  public void setGravityMultiplier(float gravityMultiplier) {
    this.gravityMultiplier = requireNonNegative(gravityMultiplier, "gravity multiplier");
  }

  public float mass() {
    return mass;
  }

  public void setMass(float mass) {
    this.mass = requirePositive(mass, "mass");
  }

  public float density() {
    return density;
  }

  public void setDensity(float density) {
    this.density = requirePositive(density, "density");
  }

  public float bounceFactor() {
    return bounceFactor;
  }

  public void setBounceFactor(float bounceFactor) {
    if (!Float.isFinite(bounceFactor) || bounceFactor < 0.0F || bounceFactor > 1.0F) {
      throw new IllegalArgumentException(
          "Bounce factor must be finite and within [0, 1]: " + bounceFactor);
    }
    this.bounceFactor = bounceFactor;
  }

  public float groundFriction() {
    return groundFriction;
  }

  public void setGroundFriction(float groundFriction) {
    this.groundFriction = requireNonNegative(groundFriction, "ground friction");
  }

  public float stepHeight() {
    return stepHeight;
  }

  public void setStepHeight(float stepHeight) {
    this.stepHeight = requireNonNegative(stepHeight, "step height");
  }

  public boolean slopeSliding() {
    return slopeSliding;
  }

  public void setSlopeSliding(boolean slopeSliding) {
    this.slopeSliding = slopeSliding;
  }

  public boolean stepUpEnabled() {
    return stepUpEnabled;
  }

  public void setStepUpEnabled(boolean stepUpEnabled) {
    this.stepUpEnabled = stepUpEnabled;
  }

  public boolean stepDownEnabled() {
    return stepDownEnabled;
  }

  public void setStepDownEnabled(boolean stepDownEnabled) {
    this.stepDownEnabled = stepDownEnabled;
  }

  public boolean liquidJump(boolean controlJump, float dt) {
    boolean newPress = controlJump && !lastControlJump;
    lastControlJump = controlJump;
    if (!isFloating) {
      return false;
    }
    if (newPress) {
      velocity = new Vector2(velocity.x(), velocity.y() + swimJumpSpeed);
    } else if (controlJump) {
      float acceleration = swimForce * PhysicsConstants.GRAVITY_REBALANCE_FACTOR * dt;
      float step = Math.clamp(swimSpeed - velocity.y(), -acceleration, acceleration);
      velocity = new Vector2(velocity.x(), velocity.y() + step);
    }
    return true;
  }

  public void ignorePlatformTemporarily() {
    fallThroughSustain = PhysicsConstants.PLATFORM_FALL_THROUGH_TICKS;
  }

  public void consumeJumpPress() {
    lastControlJump = true;
  }

  public void tick(double dt, Level level) {
    if (!Double.isFinite(dt) || dt <= 0.0) {
      return;
    }
    float frameTime = (float) dt;
    if (!Float.isFinite(frameTime) || frameTime <= 0.0F) {
      return;
    }
    prevPosition = position;
    clampVelocity();

    if (fallThroughSustain > 0) {
      fallThroughSustain--;
    }

    applyEnvironment(frameTime, level);
    if (collisionEnabled) {
      moveWithCollisions(frameTime, level);
    } else {
      position = position.add(velocity.x() * frameTime, velocity.y() * frameTime);
      onGround = false;
    }

    applyFriction(frameTime);
  }

  private void clampVelocity() {
    if (!Float.isFinite(velocity.x()) || !Float.isFinite(velocity.y())) {
      velocity = Vector2.ZERO;
      return;
    }
    float velocityX = Math.clamp(velocity.x(),
        -PhysicsConstants.MAXIMUM_BODY_SPEED, PhysicsConstants.MAXIMUM_BODY_SPEED);
    float velocityY = Math.clamp(velocity.y(),
        -PhysicsConstants.MAXIMUM_BODY_SPEED, PhysicsConstants.MAXIMUM_BODY_SPEED);
    if (velocityX != velocity.x() || velocityY != velocity.y()) {
      velocity = new Vector2(velocityX, velocityY);
    }
  }

  private void applyEnvironment(float dt, Level level) {
    liquidContact = liquidContactFraction(level);
    isFloating = liquidContact > 0.0F;
    float gravity = effectiveGravity();
    float verticalAcceleration = -gravity;
    if (isFloating) {
      verticalAcceleration += gravity * dominantLiquid.density() / density * liquidContact;
    }
    velocity = new Vector2(velocity.x(), velocity.y() + verticalAcceleration * dt);
    applyFluidDrag(dt);
  }

  private void moveWithCollisions(float dt, Level level) {
    float frameDistance = velocity.length() * dt;
    int steps = (int) Math.floor(
        frameDistance / PhysicsConstants.MAXIMUM_MOVEMENT_PER_STEP) + 1;
    float stepTime = dt / steps;
    boolean grounded = false;
    surfaceFriction = 0.0F;
    Poly body = collision();

    for (int i = 0; i < steps; i++) {
      Vector2 movement = velocity.multiply(stepTime);
      VoxelCollisionQuery.collect(level, body, position.x(), position.y(), movement,
          Math.max(0.0F, stepHeight) + (float) PhysicsConstants.SKIN_WIDTH,
          collisionCandidates);
      boolean ignorePlatforms = fallThroughSustain > 0 || movement.y() > 0.0F;
      boolean supportedBeforeStep = onGround || grounded;
      MotionResult result = MotionSolver.move(
          collisionCandidates, body, position.x(), position.y(), movement,
          ignorePlatforms, supportedBeforeStep, slopeSliding,
          stepUpEnabled && supportedBeforeStep,
          stepDownEnabled && supportedBeforeStep, stepHeight, stepTime);
      position = position.add(result.movement().x(), result.movement().y());
      grounded |= result.onGround();
      // Collision correction belongs to this bounded movement substep.
      if (result.contact() != null) {
        surfaceFriction = Math.max(surfaceFriction, result.contact().friction());
      }
      applyCollisionVelocity(result.correction(), stepTime);
      if (result.stuck()) {
        break;
      }
    }
    onGround = gravity() != 0.0F && grounded;
  }

  private void applyCollisionVelocity(Vector2 correction, float dt) {
    if (correction.lengthSquared() <= PhysicsConstants.MOTION_EPSILON
        * PhysicsConstants.MOTION_EPSILON) {
      return;
    }

    if (bounceFactor != 0.0F) {
      float correctionLength = correction.length();
      float velocityLength = velocity.length();
      if (correctionLength > 0.0F && velocityLength > 0.0F) {
        Vector2 correctionDirection = correction.divide(correctionLength);
        Vector2 velocityDirection = velocity.divide(velocityLength);
        float adjustmentMagnitude = velocityLength
            * correctionDirection.dot(velocityDirection.negate());
        Vector2 adjustment = correctionDirection.multiply(adjustmentMagnitude);
        velocity = velocity.add(adjustment)
            .add(adjustment.multiply(bounceFactor));
      }
      return;
    }

    // This is OpenStarbound's non-bouncing platformer response: only remove
    // velocity components that caused the positional correction.
    float velocityX = velocity.x();
    float velocityY = velocity.y();
    if (velocityX < 0.0F && correction.x() > 0.0F) {
      velocityX = Math.min(0.0F, velocityX + correction.x() / dt);
    } else if (velocityX > 0.0F && correction.x() < 0.0F) {
      velocityX = Math.max(0.0F, velocityX + correction.x() / dt);
    }
    if (velocityY < 0.0F && correction.y() > 0.0F) {
      velocityY = Math.min(0.0F, velocityY + correction.y() / dt);
    } else if (velocityY > 0.0F && correction.y() < 0.0F) {
      velocityY = Math.max(0.0F, velocityY + correction.y() / dt);
    }
    velocity = new Vector2(velocityX, velocityY);
  }

  private void applyFriction(float dt) {
    if (!frictionEnabled || !onGround) {
      return;
    }

    float friction = surfaceFriction > 0.0F
        ? (float) Math.sqrt(groundFriction * surfaceFriction) : groundFriction;
    float deceleration = friction * effectiveGravity() * dt;
    float velocityX = Math.copySign(
        Math.max(0.0F, Math.abs(velocity.x()) - deceleration), velocity.x());
    velocity = new Vector2(velocityX, velocity.y());
  }

  private void applyFluidDrag(float dt) {
    float worldSpeed = velocity.length();
    if (worldSpeed <= PhysicsConstants.MOTION_EPSILON || mass <= 0.0F || density <= 0.0F) {
      return;
    }

    float speed = Ruler.TILE.toMeter(worldSpeed);
    float fluidDensity = PhysicsConstants.STANDARD_AIR_DENSITY
        * (1.0F - liquidContact) + dominantLiquid.density() * liquidContact;
    float dynamicViscosity = PhysicsConstants.STANDARD_AIR_VISCOSITY
        * (1.0F - liquidContact) + dominantLiquid.viscosity() * liquidContact;
    float volume = mass / density;
    float radius = (float) Math.cbrt(3.0F * volume / (4.0F * Math.PI));
    float area = (float) Math.PI * radius * radius;
    float stokesDrag = 6.0F * (float) Math.PI * dynamicViscosity * radius * speed;
    float pressureDrag = 0.5F * fluidDensity * PhysicsConstants.STANDARD_DRAG_COEFFICIENT
        * area * speed * speed;
    float speedLoss = Math.min(speed, (stokesDrag + pressureDrag) / mass * dt);
    velocity = velocity.multiply((speed - speedLoss) / speed);
  }

  private float liquidContactFraction(Level level) {
    Rectangle bounds = bounds();
    dominantLiquid = Liquids.EMPTY;
    float total = 0.0F;
    float best = 0.0F;
    int minimumTileX = (int) Math.floor(bounds.minX());
    int maximumTileX = (int) Math.floor(bounds.maxX());
    int minimumTileY = (int) Math.floor(bounds.minY());
    int maximumTileY = (int) Math.floor(bounds.maxY());
    for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
      for (int tileY = minimumTileY; tileY <= maximumTileY; tileY++) {
        int liquidLevel = level.getLiquidLevel(tileX, tileY);
        if (liquidLevel <= 0) {
          continue;
        }
        float surfaceY = tileY + liquidLevel / (float) FluidEngine.FULL;
        float overlapX = Math.min(bounds.maxX(), tileX + 1.0F) - Math.max(bounds.minX(), tileX);
        float overlapY = Math.min(bounds.maxY(), surfaceY) - Math.max(bounds.minY(), tileY);
        if (overlapX <= 0.0F || overlapY <= 0.0F) {
          continue;
        }
        float overlap = overlapX * overlapY;
        total += overlap;
        if (overlap > best) {
          best = overlap;
          dominantLiquid = level.getLiquidStack(tileX, tileY).type();
        }
      }
    }
    float area = bounds.area();
    return area > 0.0F ? Math.min(1.0F, total / area) : 0.0F;
  }

  private static float requireNonNegative(float value, String name) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException(name + " must be finite and non-negative: " + value);
    }
    return value;
  }

  private static float requirePositive(float value, String name) {
    if (!Float.isFinite(value) || value <= 0.0F) {
      throw new IllegalArgumentException(name + " must be finite and positive: " + value);
    }
    return value;
  }

  private float effectiveGravity() {
    return gravity() * gravityScale();
  }

  /** Returns this body's effective gravity scale. */
  private float gravityScale() {
    return gravityMultiplier * PhysicsConstants.GRAVITY_REBALANCE_FACTOR;
  }
}
