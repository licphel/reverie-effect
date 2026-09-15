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

import io.viki.momentum.codec.streaming.CursorBuffer;
import io.viki.rf.network.Connection;

/**
 * Base class for network messages exchanged between client and server.
 *
 * <p>Each subclass must register itself with {@link PacketRegistry} to receive a unique wire-format integer ID.
 * Subclasses need a no-argument constructor for factory-based instantiation during decoding.
 *
 * <h3>Threading contract</h3>
 * <ul>
 *   <li>{@link #read(CursorBuffer)} — called on the I/O event loop during decode</li>
 *   <li>{@link #write(CursorBuffer)} — called on whichever thread sends the packet</li>
 *   <li>{@link #handle(Session)} — called on the main thread during the processing tick</li>
 * </ul>
 *
 * @see PacketRegistry
 * @see HeartbeatPacket
 */
public abstract class Packet {
  /** Returns the legal wire direction for this packet type. */
  public PacketFlow flow() {
    return PacketFlow.BIDIRECTIONAL;
  }

  /** Returns the transport semantics required by this packet type. */
  public PacketDelivery delivery() {
    return PacketDelivery.RELIABLE_ORDERED;
  }

  /**
   * Returns the wire-format ID assigned to this packet type during registration.
   *
   * @return the packet ID, or {@code -1} if the type has not been registered
   */
  public final int id() {
    return PacketRegistry.id(getClass());
  }

  /**
   * Populates this packet's fields from the given buffer.
   *
   * <p>Called on the I/O event loop after the wire frame has been decoded and decompressed. The buffer is positioned
   * past the packet ID; implementations should read fields in the same order they were written.
   *
   * @param buf the buffer containing the packet payload
   */
  public abstract void read(CursorBuffer buf);

  /**
   * Writes this packet's fields into the given buffer for wire transmission.
   *
   * <p>The packet ID is prepended automatically by the encoder and must not be written here. Implementations should
   * write fields in the same order they are read.
   *
   * @param buf the target buffer to write into
   */
  public abstract void write(CursorBuffer buf);

  /**
   * Executes this packet's logic on the main thread.
   *
   * <p>Called once per received packet during the processing tick. The session parameter represents the remote peer
   * that sent this packet.
   *
   * @param session the session from which this packet originated
   */
  public abstract void handle(Connection connection);

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(id=" + id() + ")";
  }
}
