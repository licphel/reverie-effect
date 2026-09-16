package io.viki.rf.world.entity;

import io.viki.momentum.math.Vector2;
import io.viki.rf.world.light.Channel;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.rf.world.util.PrecisePos;

/** A server-owned physical item emitted by the current throw interaction. */
public final class ThrownItem extends Entity {
  public ThrownItem(PrecisePos position, Vector2 velocity) {
    super(EntityType.THROWN_ITEM, PhysicsConstants.THROWN_ITEM_SIZE, PhysicsConstants.THROWN_ITEM_SIZE);
    setPosition(position);
    setVelocity(velocity);
    mass = 0.25F;
    bounceFactor = 0.5F;
    groundFriction = 0.5F;
  }

  @Override
  public float emitAmbient(byte channel) {
    return switch (channel) {
      case Channel.RED -> 1F;
      case Channel.GREEN -> 0.8F;
      default -> 0F;
    };
  }
}
