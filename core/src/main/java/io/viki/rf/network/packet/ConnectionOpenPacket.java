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

package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;

import java.util.UUID;

/**
 * Handshake packet sent by the server immediately after the TCP channel becomes active, assigning the connection's
 * shared identifier.
 *
 * <p>The UUID carried here is the single authority for the connection: both endpoints adopt it as their session id
 * once the handshake completes. The client answers with a {@link ConnectionAckPacket} echoing the same UUID.
 *
 * <p>This packet is registered by {@link PacketRegistry} at startup and must never be sent by application code.
 *
 * @see Connection
 * @see ConnectionAckPacket
 */
public final class ConnectionOpenPacket extends Packet {
  private UUID connId = new UUID(0, 0);
  private UUID realtimeToken = new UUID(0, 0);

  /** Creates an empty packet for decode; fields are populated by {@link #read(BinaryBuffer)}. */
  public ConnectionOpenPacket() {
  }

  /**
   * Creates an open packet carrying the server-assigned connection identifier.
   *
   * @param connId the connection identifier assigned by the server
   */
  public ConnectionOpenPacket(UUID connId, UUID realtimeToken) {
    this.connId = connId;
    this.realtimeToken = realtimeToken;
  }

  /**
   * Returns the connection identifier assigned by the server.
   *
   * @return the connection identifier
   */
  public UUID connId() {
    return connId;
  }

  public UUID realtimeToken() {
    return realtimeToken;
  }

  @Override
  public PacketFlow flow() {
    return PacketFlow.INTERNAL;
  }

  @Override
  public void read(BinaryBuffer buf) {
    connId = buf.readUUID();
    realtimeToken = buf.readUUID();
  }

  @Override
  public void write(BinaryBuffer buf) {
    buf.writeUUID(connId);
    buf.writeUUID(realtimeToken);
  }

  @Override
  public void handle(Connection connection) {
    // Handled internally by Connection's handshake logic before reaching the
    // inbound queue; application code never sees this packet.
  }
}
