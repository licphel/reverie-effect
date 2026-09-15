/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.rf.world.entity;

import io.viki.momentum.math.shape.Poly;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.Beam;
import io.viki.rf.world.light.LightEmitter;
import io.viki.rf.world.physics.Moveable;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.rf.world.util.ChunkPos;
import io.viki.rf.world.util.PrecisePos;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@NullMarked
public class Entity extends Moveable implements LightEmitter.DynamicSource {
  private UUID entityId = UUID.randomUUID();
  private final EntityType type;
  private final Poly collision;
  private boolean simulated = true;
  private Vector2 facing = Vector2.UNIT_X;
  private long networkRevision;
  public ChunkPos chunkPos = new ChunkPos(0, 0);
  public long lastTick;

  protected Entity(float width, float height) {
    this(EntityType.PLAYER, Poly.of(Rectangle.of(0F, 0F, width, height)));
  }

  protected Entity(Poly collision) {
    this(EntityType.PLAYER, collision);
  }

  protected Entity(EntityType type, float width, float height) {
    this(type, Poly.of(Rectangle.of(0F, 0F, width, height)));
  }

  protected Entity(EntityType type, Poly collision) {
    this.type = Objects.requireNonNull(type, "type");
    this.collision = collision;
  }

  public static Entity player(PrecisePos pos) {
    float cap = PhysicsConstants.PLAYER_COLLISION_CAP_HALF_WIDTH;
    float side = PhysicsConstants.PLAYER_COLLISION_HALF_WIDTH;
    float halfHeight = PhysicsConstants.PLAYER_COLLISION_HALF_HEIGHT;
    float shoulderY = PhysicsConstants.PLAYER_COLLISION_SHOULDER_Y;
    Poly collision = Poly.of(
        new Vector2(-cap, halfHeight),
        new Vector2(cap, halfHeight),
        new Vector2(side, shoulderY),
        new Vector2(side, -shoulderY),
        new Vector2(cap, -halfHeight),
        new Vector2(-cap, -halfHeight),
        new Vector2(-side, -shoulderY),
        new Vector2(-side, shoulderY)
    ).translate(PhysicsConstants.PLAYER_COLLISION_CENTER_X, halfHeight);

    var e = new Entity(EntityType.PLAYER, collision);
    e.mass = PhysicsConstants.PLAYER_MASS;
    e.density = PhysicsConstants.PLAYER_DENSITY;
    e.setPosition(pos);
    e.setSlopeSliding(false);
    e.setStepUpEnabled(true);
    e.setStepDownEnabled(true);
    return e;
  }

  public EntityType type() {
    return type;
  }

  public UUID entityId() {
    return entityId;
  }

  /** Assigns the stable network identity before adding this entity to a level. */
  public void setEntityId(UUID entityId) {
    this.entityId = Objects.requireNonNull(entityId, "entityId");
  }

  /** Monotonic transform revision used for per-client dirty tracking. */
  public long networkRevision() {
    return networkRevision;
  }

  /** Whether this replica advances its own physics instead of accepting network state. */
  public boolean simulated() {
    return simulated;
  }

  public void setSimulated(boolean simulated) {
    this.simulated = simulated;
  }

  @Override
  public Poly collision() {
    return collision;
  }

  /** The last non-zero movement direction, normalized. */
  public Vector2 facing() {
    return facing;
  }

  /** Returns the normalized facing encoded as one angle in radians. */
  public float facingAngle() {
    return (float) Math.atan2(facing.y(), facing.x());
  }

  /** Restores a normalized facing from its packed angle. */
  public void setFacingAngle(float angle) {
    if (!Float.isFinite(angle)) {
      throw new IllegalArgumentException("Facing angle must be finite: " + angle);
    }
    facing = Vector2.createDirectional(angle);
  }

  @Override
  public void setVelocity(Vector2 v) {
    boolean changed = !velocity.equals(v);
    super.setVelocity(v);
    updateFacing();
    if (changed) networkRevision++;
  }

  @Override
  public void setVelocity(float vx, float vy) {
    boolean changed = velocity.x() != vx || velocity.y() != vy;
    super.setVelocity(vx, vy);
    updateFacing();
    if (changed) networkRevision++;
  }

  @Override
  public void setPosition(PrecisePos position) {
    boolean changed = !this.position.equals(position);
    super.setPosition(position);
    if (changed) networkRevision++;
  }

  @Override
  public void setPositionImmediate(PrecisePos position) {
    boolean changed = !this.position.equals(position);
    super.setPositionImmediate(position);
    if (changed) networkRevision++;
  }

  @Override
  public void setPositionInterpolated(PrecisePos position) {
    boolean changed = !this.position.equals(position);
    super.setPositionInterpolated(position);
    if (changed) networkRevision++;
  }

  @Override
  protected float gravity() {
    return PhysicsConstants.STANDARD_GRAVITY;
  }

  @Override
  public void tick(double dt, Level level) {
    if (!simulated) return;
    PrecisePos previousPosition = position;
    Vector2 previousVelocity = velocity;
    super.tick(dt, level);
    updateFacing();
    if (!position.equals(previousPosition) || !velocity.equals(previousVelocity)) {
      networkRevision++;
    }
  }

  private void updateFacing() {
    if (velocity.x() != 0.0F || velocity.y() != 0.0F) {
      facing = velocity.normalize();
    }
  }

  /** Call once after spawning to add this runtime entity to the level registry. */
  public void enterChunk(Level level) {
    level.entities().add(this);
  }

  /** The ambient light emitted by this entity on one channel, or {@code 0}. */
  public float emitAmbient(byte channel) {
    return 0F;
  }

  /**
   * The directional beams emitted by this entity; the caller draws and
   * recycles them.
   */
  public List<Beam> emitBeams() {
    return Collections.emptyList();
  }
}
