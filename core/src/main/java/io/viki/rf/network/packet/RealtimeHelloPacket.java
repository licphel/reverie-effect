package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;

/** Authenticated UDP probe that binds a realtime address to a TCP session. */
public final class RealtimeHelloPacket extends Packet {
  @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
  @Override public PacketDelivery delivery() { return PacketDelivery.UNRELIABLE_SEQUENCED; }
  @Override public void read(BinaryBuffer buffer) { }
  @Override public void write(BinaryBuffer buffer) { }
  @Override public void handle(Connection connection) { }
}
