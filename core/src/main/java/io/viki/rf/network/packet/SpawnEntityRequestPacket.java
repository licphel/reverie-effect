package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.server.GameServer;
import io.viki.momentum.math.Vector2;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.entity.ThrownItem;
import io.viki.rf.world.entity.EntityType;

import java.util.Objects;

/** Untrusted request for the server to create a runtime entity. */
public final class SpawnEntityRequestPacket extends Packet {
  private EntityType type = EntityType.THROWN_ITEM;
  private float velocityX;
  private float velocityY;

  public SpawnEntityRequestPacket() {
  }

  public SpawnEntityRequestPacket(EntityType type, float velocityX, float velocityY) {
    this.type = Objects.requireNonNull(type, "type");
    this.velocityX = requireFinite(velocityX, "velocityX");
    this.velocityY = requireFinite(velocityY, "velocityY");
  }

  public EntityType type() { return type; }
  public float velocityX() { return velocityX; }
  public float velocityY() { return velocityY; }
  @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
  @Override public void read(BinaryBuffer buffer) {
    int ordinal = buffer.read();
    if (ordinal < 0 || ordinal >= EntityType.values().length) {
      throw new IllegalArgumentException("Unknown entity type: " + ordinal);
    }
    type = EntityType.values()[ordinal];
    velocityX = requireFinite(buffer.readFloat(), "velocityX");
    velocityY = requireFinite(buffer.readFloat(), "velocityY");
  }
  @Override public void write(BinaryBuffer buffer) {
    buffer.write((byte) type.ordinal());
    buffer.writeFloat(velocityX);
    buffer.writeFloat(velocityY);
  }
  @Override public void handle(Connection connection) {
    Entity player = GameServer.player(connection.netUuid());
    if (player == null || type != EntityType.THROWN_ITEM
        || !Float.isFinite(velocityX) || !Float.isFinite(velocityY)) return;
    new ThrownItem(player.center(), new Vector2(velocityX, velocityY))
        .enterChunk(GameServer.level());
  }

  private static float requireFinite(float value, String name) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite: " + value);
    }
    return value;
  }
}
