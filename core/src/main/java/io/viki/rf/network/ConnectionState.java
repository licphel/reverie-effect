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

/**
 * Lifecycle states of a {@link Session}.
 *
 * <p>The canonical progression is {@link #CONNECTING} → {@link #CONNECTED} → {@link #DISCONNECTING} →
 * {@link #DISCONNECTED}. A freshly created session that has never connected starts at {@code DISCONNECTED}.
 *
 * @see Session#state()
 */
public enum ConnectionState {
  /** Initial connection handshake is in progress. */
  CONNECTING,
  /** Connection is established and data may flow. */
  CONNECTED,
  /** Close handshake has been initiated but the channel may still be open. */
  DISCONNECTING,
  /** Connection is fully closed and no further communication is possible. */
  DISCONNECTED
}
