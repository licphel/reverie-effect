package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.server.GameServer;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.util.PrecisePos;

/** Client-authoritative local player transform submitted for server validation. */
public final class PlayerPositionPacket extends Packet {
  private float x;
  private float y;
  private float velocityX;
  private float velocityY;

  public PlayerPositionPacket() {
  }

  public PlayerPositionPacket(float x, float y, float velocityX, float velocityY) {
    this.x = x;
    this.y = y;
    this.velocityX = velocityX;
    this.velocityY = velocityY;
  }

  public float x() { return x; }
  public float y() { return y; }
  public float velocityX() { return velocityX; }
  public float velocityY() { return velocityY; }
  @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
  @Override public void read(BinaryBuffer b) {
    x = b.readFloat(); y = b.readFloat();
    velocityX = b.readFloat(); velocityY = b.readFloat();
  }
  @Override public void write(BinaryBuffer b) {
    b.writeFloat(x); b.writeFloat(y);
    b.writeFloat(velocityX); b.writeFloat(velocityY);
  }
  @Override public void handle(Connection connection) {
    Entity player = GameServer.player(connection.netUuid());
    if (player == null) return;
    player.setPosition(new PrecisePos(x, y));
    player.setVelocity(velocityX, velocityY);
    GameServer.level().chunkManager().interest(connection.netUuid())
        .update(x, y, GameServer.INTEREST_RADIUS);
  }
}
