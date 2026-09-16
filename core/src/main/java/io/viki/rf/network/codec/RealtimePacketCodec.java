package io.viki.rf.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.NetworkException;
import io.viki.rf.network.packet.Packet;
import io.viki.rf.network.packet.PacketDelivery;
import io.viki.rf.network.packet.PacketRegistry;

import java.util.Objects;
import java.util.UUID;

/** Stateless codec for authenticated, MTU-bounded realtime datagrams. */
public final class RealtimePacketCodec {
  public static final int MAX_DATAGRAM_SIZE = 1_200;
  private static final int MAGIC = 0x52465531;
  private static final int HEADER_SIZE = 40;

  private RealtimePacketCodec() {
  }

  public static ByteBuf encode(ByteBufAllocator allocator, UUID connectionId,
                               UUID token, Packet packet) {
    if (packet.delivery() != PacketDelivery.UNRELIABLE_SEQUENCED) {
      throw new IllegalArgumentException("Reliable packet cannot use realtime codec: "
          + packet.getClass().getSimpleName());
    }
    int packetId = PacketRegistry.id(packet.getClass());
    if (packetId < 0) {
      throw new IllegalArgumentException("Unregistered packet type: " + packet.getClass().getName());
    }
    ByteBuf output = allocator.buffer(MAX_DATAGRAM_SIZE);
    BinaryBuffer buffer = new NettyBinaryBuffer(output);
    buffer.writeInt(MAGIC);
    buffer.writeUUID(connectionId);
    buffer.writeUUID(token);
    buffer.writeInt(packetId);
    packet.write(buffer);
    output.writerIndex(buffer.writerIndex());
    if (output.readableBytes() > MAX_DATAGRAM_SIZE) {
      output.release();
      throw new NetworkException("Realtime packet exceeds " + MAX_DATAGRAM_SIZE
          + " bytes: " + packet.getClass().getSimpleName());
    }
    return output;
  }

  public static Decoded decode(ByteBuf input) {
    if (input.readableBytes() < HEADER_SIZE || input.readableBytes() > MAX_DATAGRAM_SIZE) {
      throw new NetworkException("Invalid realtime datagram size: " + input.readableBytes());
    }
    BinaryBuffer buffer = new NettyBinaryBuffer(input);
    int magic = buffer.readInt();
    if (magic != MAGIC) throw new NetworkException("Invalid realtime datagram magic");
    UUID connectionId = buffer.readUUID();
    UUID token = buffer.readUUID();
    int packetId = buffer.readInt();
    Packet packet = PacketRegistry.create(packetId);
    if (packet == null) throw new NetworkException("Unknown realtime packet ID: " + packetId);
    if (packet.delivery() != PacketDelivery.UNRELIABLE_SEQUENCED) {
      throw new NetworkException("Reliable packet received over realtime transport: "
          + packet.getClass().getSimpleName());
    }
    packet.read(buffer);
    if (buffer.readableBytes() != 0) {
      throw new NetworkException("Realtime packet " + packetId + " left "
          + buffer.readableBytes() + " unread payload bytes");
    }
    input.readerIndex(buffer.readerIndex());
    return new Decoded(connectionId, token, packet);
  }

  public record Decoded(UUID connectionId, UUID token, Packet packet) {
    public Decoded {
      Objects.requireNonNull(connectionId, "connectionId");
      Objects.requireNonNull(token, "token");
      Objects.requireNonNull(packet, "packet");
    }
  }
}
