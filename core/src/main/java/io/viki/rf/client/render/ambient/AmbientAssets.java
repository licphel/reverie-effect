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

package io.viki.rf.client.render.ambient;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.resource.Resource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads and owns the GPU textures used by the client ambient renderer. */
public final class AmbientAssets implements AutoCloseable {
  private static final Map<String, String> PATHS = Map.ofEntries(
      Map.entry("body_1", "/asset/texture/ambient/body_1.png"),
      Map.entry("body_2", "/asset/texture/ambient/body_2.png"),
      Map.entry("cloud", "/asset/texture/ambient/cloud.png"),
      Map.entry("forest_0", "/asset/texture/ambient/forest/forest_0.png"),
      Map.entry("forest_1", "/asset/texture/ambient/forest/forest_1.png"),
      Map.entry("desert_0", "/asset/texture/ambient/desert/desert_0.png"),
      Map.entry("desert_1", "/asset/texture/ambient/desert/desert_1.png"),
      Map.entry("snow_field_0", "/asset/texture/ambient/snow_field/snow_field_0.png"),
      Map.entry("snow_field_1", "/asset/texture/ambient/snow_field/snow_field_1.png"),
      Map.entry("cave_0", "/asset/texture/ambient/cave/cave_0.png"),
      Map.entry("lava_cave_0", "/asset/texture/ambient/lava_cave/lava_cave_0.png"));

  private final Map<String, TexturePart> parts = new HashMap<>();
  private final List<Texture> textures = new ArrayList<>();
  private boolean closed;

  private AmbientAssets(Device device) {
    Objects.requireNonNull(device, "device");
    load(device);
  }

  /** Opens and loads the built-in ambient textures. */
  public static AmbientAssets open(Device device) {
    return new AmbientAssets(device);
  }

  /** Returns an ambient texture part, or {@code null} for an unknown name. */
  public @Nullable TexturePart get(String name) {
    return parts.get(name);
  }

  /** Returns an ambient texture part or fails with the missing name. */
  public TexturePart require(String name) {
    TexturePart part = parts.get(name);
    if (part == null) {
      throw new IllegalArgumentException("Unknown ambient texture: " + name);
    }
    return part;
  }

  private void load(Device device) {
    try (Resource resources = Resource.classpath(AmbientAssets.class)) {
      for (Map.Entry<String, String> entry : PATHS.entrySet()) {
        try (InputStream input = resources.open(entry.getValue())) {
          if (input == null) {
            throw new IOException("Resource not found: " + entry.getValue());
          }
          Texture texture = Texture.loadRGBA8(device, new PngInputStream(input).info());
          textures.add(texture);
          parts.put(entry.getKey(), new TexturePart(texture));
        }
      }
    } catch (IOException exception) {
      close();
      throw new IllegalStateException("Failed to load ambient textures", exception);
    } catch (RuntimeException exception) {
      close();
      throw exception;
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    for (Texture texture : textures) {
      texture.close();
    }
    textures.clear();
    parts.clear();
  }
}
