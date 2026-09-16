package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;

/** Confirms that the server can receive and reply through the UDP path. */
public final class RealtimeWelcomePacket extends Packet {
  @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }
  @Override public PacketDelivery delivery() { return PacketDelivery.UNRELIABLE_SEQUENCED; }
  @Override public void read(BinaryBuffer buffer) { }
  @Override public void write(BinaryBuffer buffer) { }
  @Override public void handle(Connection connection) { }
}
