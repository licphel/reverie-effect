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

package io.viki.rf.world.fluid;

import java.util.Objects;

/** Immutable and thread-safe client appearance declared by a liquid type. */
public record LiquidRenderDefinition(String bodyTexturePath, String surfaceTexturePath) {
  public static final LiquidRenderDefinition NONE = new LiquidRenderDefinition("", "");

  public LiquidRenderDefinition {
    requireResourcePath(bodyTexturePath, "bodyTexturePath");
    requireResourcePath(surfaceTexturePath, "surfaceTexturePath");
    if (bodyTexturePath.isEmpty() != surfaceTexturePath.isEmpty()) {
      throw new IllegalArgumentException(
          "Liquid body and surface textures must either both be present or both be absent");
    }
  }

  public static LiquidRenderDefinition textured(
      String bodyTexturePath, String surfaceTexturePath) {
    return new LiquidRenderDefinition(bodyTexturePath, surfaceTexturePath);
  }

  public boolean visible() {
    return !bodyTexturePath.isEmpty();
  }

  private static void requireResourcePath(String path, String field) {
    Objects.requireNonNull(path, field);
    if (!path.isEmpty() && !path.startsWith("/")) {
      throw new IllegalArgumentException(
          field + " must be absolute within resources: " + path);
    }
  }
}
