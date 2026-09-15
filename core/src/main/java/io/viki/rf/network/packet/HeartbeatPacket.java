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
import io.viki.rf.network.ServerConnection;
import io.viki.rf.network.Connection;

/**
 * A built-in keepalive packet with no payload.
 *
 * <p>Sent periodically by both client and server to detect stale connections. This packet is registered at ID 0 and is
 * always available. Reception is tracked by the session activity timestamp, with timeout detection handled
 * automatically by the server.
 *
 * @see HeartbeatPacket#HEARTBEAT_INTERVAL_MS
 * @see ServerConnection#SESSION_TIMEOUT_MS
 */
public final class HeartbeatPacket extends Packet {
  /** Interval in milliseconds between automatic heartbeat transmissions. */
  public static final long HEARTBEAT_INTERVAL_MS = 5000;

  @Override
  public void read(CursorBuffer buf) {
    // Heartbeat carries no payload.
  }

  @Override
  public void write(CursorBuffer buf) {
    // Heartbeat carries no payload.
  }

  @Override
  public void handle(Connection connection) {
    // Heartbeat reception is tracked by the session's activity timestamp;
    // timeout detection runs in the server's processing loop.
  }
}
