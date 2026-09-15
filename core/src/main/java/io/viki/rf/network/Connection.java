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

package io.viki.rf.network;

import io.viki.rf.network.packet.Packet;

import java.util.UUID;

/**
 * A network session representing one endpoint of a client-server connection.
 *
 * <p>On the client side there is a single session that communicates with the server. On the server side there is one
 * session per connected client. Both endpoints of a connection share the same server-assigned identifier, exposed via
 * {@link #id()} and {@link #remoteId()}; the session (and the identifier) only becomes usable once the handshake
 * completes.
 *
 * <p>Sessions are thread-safe: {@link #send(Packet)} may be called from any thread, while lifecycle introspection
 * methods may be called from the main thread without external synchronization.
 *
 * @see Connection#session()
 * @see ServerConnection#sessions()
 */
public interface Connection {
  static ConnectionHost createLAN(int port) {
    return ServerConnection.configured("0.0.0.0", port);
  }

  static ConnectionHost createDedicated(int port) {
    return ServerConnection.configured("0.0.0.0", port);
  }

  static ConnectionHost createIPAddr(String host, int port) {
    return ClientConnection.configured(host, port);
  }

  /** Returns the network UUID used by a host to route packets to this peer. */
  default UUID netUuid() {
    return id();
  }

  /**
   * Returns the unique identifier assigned to this session when it was established.
   *
   * @return the session identifier
   */
  UUID id();

  /**
   * Sends a packet through this session.
   *
   * <p>The call returns immediately; the packet is enqueued for asynchronous transmission on the underlying channel.
   *
   * @param packet the packet to send
   */
  void send(Packet packet);

  /**
   * Initiates a graceful close of this session, sending any pending data before shutting down the underlying channel.
   */
  void close();

  /**
   * Reports whether the session is currently able to carry data.
   *
   * @return {@code true} if the session is in the {@link SessionState#CONNECTED} state
   */
  boolean isActive();

  /**
   * Returns the current lifecycle state of this session.
   *
   * @return the session state
   */
  ConnectionState state();

  /**
   * Returns the identity of the remote peer.
   *
   * <p>Both endpoints of a connection share the same server-assigned identifier, so by default this returns
   * {@link #id()}. Implementations that track a separate remote identity may override it.
   *
   * @return the shared connection identifier once the handshake completes
   */
  default UUID remoteId() {
    return id();
  }

  /**
   * Returns the wall-clock timestamp of the most recent send or receive activity on this session.
   *
   * @return a timestamp compatible with {@link System#currentTimeMillis()}
   */
  long lastActivityTime();

  /** Binds the gameplay owner used by received packets on this endpoint. */
  void bindContext(Object context);

  /** Returns the bound gameplay owner with a checked, descriptive cast. */
  <T> T context(Class<T> type);
}
