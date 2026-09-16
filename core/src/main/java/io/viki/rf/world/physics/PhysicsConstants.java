/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

/** Shared SI reference values, gameplay parameters, and solver tolerances. */
public final class PhysicsConstants {
  /** Standard gravitational acceleration in metres per second squared. */
  public static final float STANDARD_GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665F;
  /** Standard gravitational acceleration in tiles per second squared. */
  public static final float STANDARD_GRAVITY =
      Ruler.TILE.fromMeter(STANDARD_GRAVITY_METERS_PER_SECOND_SQUARED);
  /** Dry air density at sea level and 15 degrees Celsius, in kilograms per cubic metre. */
  public static final float STANDARD_AIR_DENSITY = 1.225F;
  /** Dry air dynamic viscosity near room temperature, in pascal-seconds. */
  public static final float STANDARD_AIR_VISCOSITY = 1.81E-5F;
  /** Fresh water density near room temperature, in kilograms per cubic metre. */
  public static final float STANDARD_WATER_DENSITY = 997.0F;
  /** Fresh water dynamic viscosity near room temperature, in pascal-seconds. */
  public static final float STANDARD_WATER_VISCOSITY = 1.002E-3F;
  /** Default kinetic friction coefficient for an ordinary dry surface. */
  public static final float STANDARD_FRICTION_COEFFICIENT = 0.5F;
  /** Drag coefficient used for the equivalent spherical body approximation. */
  public static final float STANDARD_DRAG_COEFFICIENT = 0.47F;
  /** Default restitution coefficient. */
  public static final float STANDARD_RESTITUTION = 0.0F;

  /** Default dynamic-body mass in kilograms. */
  public static final float DEFAULT_BODY_MASS = 1.0F;
  /** Default dynamic-body density in kilograms per cubic metre. */
  public static final float DEFAULT_BODY_DENSITY = STANDARD_WATER_DENSITY;
  /** Default multiplier applied to local gravity. */
  public static final float DEFAULT_GRAVITY_MULTIPLIER = 1.0F;
  /** Global gameplay rebalance applied uniformly to gravitational acceleration. */
  public static final float GRAVITY_REBALANCE_FACTOR = 3.5F;
  /** Maximum representable gameplay speed in metres per second. */
  public static final float MAXIMUM_BODY_SPEED_METERS_PER_SECOND = 100.0F;
  /** Maximum representable gameplay speed in tiles per second. */
  public static final float MAXIMUM_BODY_SPEED =
      Ruler.TILE.fromMeter(MAXIMUM_BODY_SPEED_METERS_PER_SECOND);
  /** Maximum distance integrated by one collision substep, in metres. */
  public static final float MAXIMUM_MOVEMENT_PER_STEP_METERS = 0.1F;
  /** Maximum distance integrated by one collision substep, in tiles. */
  public static final float MAXIMUM_MOVEMENT_PER_STEP =
      Ruler.TILE.fromMeter(MAXIMUM_MOVEMENT_PER_STEP_METERS);
  /** Number of ticks for which a body ignores one-way platforms. */
  public static final int PLATFORM_FALL_THROUGH_TICKS = 10;
  /** Default automatic step height in metres. */
  public static final float DEFAULT_STEP_HEIGHT_METERS = 0.5F;
  /** Default automatic step height in tiles. */
  public static final float DEFAULT_STEP_HEIGHT =
      Ruler.TILE.fromMeter(DEFAULT_STEP_HEIGHT_METERS);
  /** Default swimming target speed in metres per second. */
  public static final float DEFAULT_SWIM_SPEED_METERS_PER_SECOND = 3.0F;
  /** Default swimming target speed in tiles per second. */
  public static final float DEFAULT_SWIM_SPEED =
      Ruler.TILE.fromMeter(DEFAULT_SWIM_SPEED_METERS_PER_SECOND);
  /** Initial swimming impulse in metres per second. */
  public static final float DEFAULT_SWIM_JUMP_SPEED_METERS_PER_SECOND = 4.0F;
  /** Initial swimming impulse in tiles per second. */
  public static final float DEFAULT_SWIM_JUMP_SPEED =
      Ruler.TILE.fromMeter(DEFAULT_SWIM_JUMP_SPEED_METERS_PER_SECOND);
  /** Swimming acceleration in metres per second squared. */
  public static final float DEFAULT_SWIM_ACCELERATION_METERS_PER_SECOND_SQUARED = 12.0F;
  /** Swimming acceleration in tiles per second squared. */
  public static final float DEFAULT_SWIM_ACCELERATION =
      Ruler.TILE.fromMeter(DEFAULT_SWIM_ACCELERATION_METERS_PER_SECOND_SQUARED);

  /** Player mass in kilograms. */
  public static final float PLAYER_MASS = 70.0F;
  /** Mean human body density in kilograms per cubic metre. */
  public static final float PLAYER_DENSITY = 985.0F;
  /** Player walking speed in metres per second. */
  public static final float PLAYER_WALK_SPEED_METERS_PER_SECOND = 5.0F;
  /** Player walking speed in tiles per second. */
  public static final float PLAYER_WALK_SPEED =
      Ruler.TILE.fromMeter(PLAYER_WALK_SPEED_METERS_PER_SECOND);
  /** Target player jump height in metres. */
  public static final float PLAYER_JUMP_HEIGHT_METERS = 2.0F;
  /** Player jump take-off speed in metres per second. */
  public static final float PLAYER_JUMP_SPEED_METERS_PER_SECOND = (float) Math.sqrt(
      2.0F * STANDARD_GRAVITY_METERS_PER_SECOND_SQUARED
          * GRAVITY_REBALANCE_FACTOR * PLAYER_JUMP_HEIGHT_METERS);
  /** Player jump take-off speed in tiles per second. */
  public static final float PLAYER_JUMP_SPEED =
      Ruler.TILE.fromMeter(PLAYER_JUMP_SPEED_METERS_PER_SECOND);
  /** Horizontal centre of the player collision polygon in metres. */
  public static final float PLAYER_COLLISION_CENTER_X_METERS = 0.375F;
  /** Horizontal centre of the player collision polygon in tiles. */
  public static final float PLAYER_COLLISION_CENTER_X =
      Ruler.TILE.fromMeter(PLAYER_COLLISION_CENTER_X_METERS);
  /** Half-height of the player collision polygon in metres. */
  public static final float PLAYER_COLLISION_HALF_HEIGHT_METERS = 0.6875F;
  /** Half-height of the player collision polygon in tiles. */
  public static final float PLAYER_COLLISION_HALF_HEIGHT =
      Ruler.TILE.fromMeter(PLAYER_COLLISION_HALF_HEIGHT_METERS);
  /** Half-width of the flat collision caps in metres. */
  public static final float PLAYER_COLLISION_CAP_HALF_WIDTH_METERS = 0.125F;
  /** Half-width of the flat collision caps in tiles. */
  public static final float PLAYER_COLLISION_CAP_HALF_WIDTH =
      Ruler.TILE.fromMeter(PLAYER_COLLISION_CAP_HALF_WIDTH_METERS);
  /** Maximum half-width of the player collision polygon in metres. */
  public static final float PLAYER_COLLISION_HALF_WIDTH_METERS = 0.325F;
  /** Maximum half-width of the player collision polygon in tiles. */
  public static final float PLAYER_COLLISION_HALF_WIDTH =
      Ruler.TILE.fromMeter(PLAYER_COLLISION_HALF_WIDTH_METERS);
  /** Vertical shoulder offset of the player collision polygon in metres. */
  public static final float PLAYER_COLLISION_SHOULDER_Y_METERS = 0.3125F;
  /** Vertical shoulder offset of the player collision polygon in tiles. */
  public static final float PLAYER_COLLISION_SHOULDER_Y =
      Ruler.TILE.fromMeter(PLAYER_COLLISION_SHOULDER_Y_METERS);

  /** Demo thrown-item collision width and height in metres. */
  public static final float THROWN_ITEM_SIZE_METERS = 0.275F;
  /** Demo thrown-item collision width and height in tiles. */
  public static final float THROWN_ITEM_SIZE = Ruler.TILE.fromMeter(THROWN_ITEM_SIZE_METERS);

  /** Maximum number of repeated overlap-separation passes. */
  public static final int MAXIMUM_SEPARATION_LOOPS = 3;
  /** Separation tolerance accumulated per nominal 20 Hz tick, in metres. */
  public static final double SEPARATION_TOLERANCE_PER_TICK_METERS = 5.0E-4;
  /** Separation tolerance accumulated per nominal 20 Hz tick, in tiles. */
  public static final double SEPARATION_TOLERANCE_PER_TICK =
      Ruler.TILE.fromMeter(SEPARATION_TOLERANCE_PER_TICK_METERS);
  /** Nominal simulation frequency used to scale per-tick tolerances. */
  public static final double NOMINAL_TICKS_PER_SECOND = io.viki.rf.GameConstants.TICKS_PER_SECOND;
  /** Maximum short surface correction in metres. */
  public static final double SURFACE_SLOPE_CORRECTION_LIMIT_METERS = 0.1;
  /** Maximum short surface correction in tiles. */
  public static final double SURFACE_SLOPE_CORRECTION_LIMIT =
      Ruler.TILE.fromMeter(SURFACE_SLOPE_CORRECTION_LIMIT_METERS);
  /** Maximum walkable surface angle in radians. */
  public static final double SURFACE_SLOPE_ANGLE = Math.PI / 3.0;
  /** Squared threshold below which a candidate SAT axis is degenerate. */
  public static final double AXIS_EPSILON_SQUARED = 1.0E-14;
  /** Direction projection threshold used by directional and swept SAT. */
  public static final double VELOCITY_EPSILON = 1.0E-12;
  /** Contact depth below which two shapes are treated as touching, in metres. */
  public static final double CONTACT_EPSILON_METERS = 5.0E-6;
  /** Contact depth below which two shapes are treated as touching, in tiles. */
  public static final double CONTACT_EPSILON = Ruler.TILE.fromMeter(CONTACT_EPSILON_METERS);
  /** Separation skin in metres. */
  public static final double SKIN_WIDTH_METERS = 5.0E-5;
  /** Separation skin in tiles. */
  public static final double SKIN_WIDTH = Ruler.TILE.fromMeter(SKIN_WIDTH_METERS);
  /** Ground probe distance in metres. */
  public static final double GROUND_PROBE_DISTANCE_METERS = 2.5E-4;
  /** Ground probe distance in tiles. */
  public static final double GROUND_PROBE_DISTANCE =
      Ruler.TILE.fromMeter(GROUND_PROBE_DISTANCE_METERS);
  /** Time comparison tolerance used by swept collision queries. */
  public static final double TIME_EPSILON = 1.0E-7;
  /** Small movement threshold in metres. */
  public static final double MOTION_EPSILON_METERS = 5.0E-8;
  /** Small movement threshold in tiles. */
  public static final double MOTION_EPSILON = Ruler.TILE.fromMeter(MOTION_EPSILON_METERS);
  /** Maximum number of contact/slide iterations for one path. */
  public static final int MAXIMUM_CONTACTS = 4;

  private PhysicsConstants() {
  }
}
