package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.server.GameServer;

/** Client-requested streaming center. The server clamps and owns the actual radius. */
public final class InterestPacket extends Packet {
  private double x;
  private double y;

  public InterestPacket() {
  }

  public InterestPacket(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public double x() { return x; }
  public double y() { return y; }
  @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
  @Override public void read(BinaryBuffer buffer) {
    x = buffer.readDouble();
    y = buffer.readDouble();
  }
  @Override public void write(BinaryBuffer buffer) {
    buffer.writeDouble(x);
    buffer.writeDouble(y);
  }
  @Override public void handle(Connection connection) {
    if (Double.isFinite(x) && Double.isFinite(y)) {
      GameServer.level().chunkManager().interest(connection.netUuid())
          .update(x, y, GameServer.INTEREST_RADIUS);
    }
  }
}
