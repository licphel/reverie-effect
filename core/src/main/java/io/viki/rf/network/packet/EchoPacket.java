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
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * A built-in diagnostic packet that echoes its payload back to the sender.
 *
 * <p>Carries a hop count so a probe terminates instead of bouncing forever: each hop decrements the counter, and a
 * packet that reaches zero is not echoed. The default count of 2 gives exactly one round trip — client → server →
 * client — before going quiet.
 *
 * @see PacketRegistry
 */
public final class EchoPacket extends Packet {
  /** Default hop count: one request plus one reply, then stop. */
  public static final int DEFAULT_HOPS = 2;

  /** Optional observer invoked on every received echo before hop handling; diagnostic hook for tests and tools. */
  private static volatile @Nullable BiConsumer<Connection, EchoPacket> observer;

  private String message = "";
  private int hops = DEFAULT_HOPS;

  /** Creates an empty packet for decode; fields are populated by {@link #read(BinaryBuffer)}. */
  public EchoPacket() {
  }

  /**
   * Creates an echo probe carrying a message.
   *
   * @param message the payload to echo
   */
  public EchoPacket(String message) {
    this.message = message;
  }

  /**
   * Installs a diagnostic observer for received echo packets, or clears it with {@code null}.
   *
   * @param observer invoked on the processing thread for each received echo, or {@code null} to clear
   */
  public static void observe(@Nullable BiConsumer<Connection, EchoPacket> observer) {
    EchoPacket.observer = observer;
  }

  /**
   * Returns the carried message.
   *
   * @return the message payload
   */
  public String message() {
    return message;
  }

  /**
   * Returns the remaining hop count.
   *
   * @return how many times this probe will still be echoed
   */
  public int hops() {
    return hops;
  }

  @Override
  public void read(BinaryBuffer buf) {
    message = buf.readUTF8();
    hops = buf.readVarInt();
  }

  @Override
  public void write(BinaryBuffer buf) {
    buf.writeUTF8(message);
    buf.writeVarInt(hops);
  }

  @Override
  public void handle(Connection connection) {
    BiConsumer<Connection, EchoPacket> obs = observer;
    if (obs != null) {
      obs.accept(connection, this);
    }
    if (hops > 0) {
      EchoPacket reply = new EchoPacket(message);
      reply.hops = hops - 1;
      connection.send(reply);
    }
  }
}
