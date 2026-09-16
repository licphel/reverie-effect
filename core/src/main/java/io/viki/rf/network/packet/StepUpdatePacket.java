package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.client.GameClient;

/** Server clock sample used to place entity snapshots on a shared timeline. */
public final class StepUpdatePacket extends Packet {
  private double time;

  public StepUpdatePacket() {
  }

  public StepUpdatePacket(double time) {
    this.time = time;
  }

  public double time() { return time; }
  @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }
  @Override public void read(BinaryBuffer buffer) { time = buffer.readDouble(); }
  @Override public void write(BinaryBuffer buffer) { buffer.writeDouble(time); }
  @Override public void handle(Connection connection) {
    GameClient.level().interpolationTracker().receiveTimeUpdate(time);
  }
}
