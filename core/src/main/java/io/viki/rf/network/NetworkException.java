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

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Unchecked exception for network-level errors.
 *
 * <p>Covers connection failures, protocol violations, and problems encountered during packet encoding or decoding.
 */
public class NetworkException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026070400L;

  /**
   * Creates an exception with a descriptive message.
   *
   * @param message a human-readable description of the error
   */
  public NetworkException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a descriptive message and a root cause.
   *
   * @param message a human-readable description of the error
   * @param cause   the underlying throwable that triggered this error, or {@code null}
   */
  public NetworkException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
