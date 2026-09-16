package io.viki.rf.server;

import io.viki.rf.network.Connection;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.util.PrecisePos;

import java.util.Objects;
import java.util.UUID;

/** Server-owned state associated with one authenticated connection. */
final class ServerPlayer {
  private final Connection connection;
  private final Entity entity;

  ServerPlayer(Connection connection, Entity entity) {
    this.connection = Objects.requireNonNull(connection, "connection");
    this.entity = Objects.requireNonNull(entity, "entity");
  }

  UUID netUuid() { return connection.netUuid(); }
  Connection connection() { return connection; }
  Entity entity() { return entity; }
  PrecisePos position() { return entity.position(); }
  float velocityX() { return entity.velocity().x(); }
  float velocityY() { return entity.velocity().y(); }
  void accept(PrecisePos position, float velocityX, float velocityY) {
    entity.setPosition(position);
    entity.setVelocity(velocityX, velocityY);
  }
}
