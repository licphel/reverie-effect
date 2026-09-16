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
package io.viki.rf.client.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.texture.TextureAtlas;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.resource.Resource;
import io.viki.rf.world.object.MultiBlockDefinition;
import io.viki.rf.world.object.ObjectPartRender;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Client-side atlas for textures declared by registered object definitions. */
final class ObjectTextureAtlas implements AutoCloseable {
  private static final int PADDING = 1;

  private final TextureAtlas atlas;
  private final Map<String, TexturePart> textures;

  private ObjectTextureAtlas(TextureAtlas atlas, Map<String, TexturePart> textures) {
    this.atlas = atlas;
    this.textures = textures;
  }

  static ObjectTextureAtlas open(Device device, Registry<MultiBlockDefinition> definitions) {
    TextureAtlas atlas = new TextureAtlas(device, PADDING);
    Map<String, TexturePart> textures = new HashMap<>();
    Resource resources = Resource.classpath(ObjectTextureAtlas.class);
    try {
      for (MultiBlockDefinition definition : definitions) {
        for (ObjectPartRender render : definition.renderDefinitions()) {
          if (!render.visible() || textures.containsKey(render.texturePath())) {
            continue;
          }
          textures.put(render.texturePath(), atlas.accept(new PngInputStream(
              resources.open(render.texturePath())).info()));
        }
      }
      return new ObjectTextureAtlas(atlas, Map.copyOf(textures));
    } catch (Exception exception) {
      atlas.close();
      throw new IllegalStateException("Failed to build object texture atlas", exception);
    }
  }

  TexturePart texture(String path) {
    Objects.requireNonNull(path, "path");
    TexturePart texture = textures.get(path);
    if (texture == null) {
      throw new IllegalArgumentException("Object texture was not registered in the atlas: " + path);
    }
    return texture;
  }

  @Override
  public void close() {
    atlas.close();
  }
}
