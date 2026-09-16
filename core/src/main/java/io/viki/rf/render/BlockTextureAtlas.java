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

package io.viki.rf.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.texture.TextureAtlas;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.resource.Resource;
import io.viki.rf.client.world.block.BlockClientExtension;
import io.viki.rf.client.world.block.StandardBlockClientExtension;
import io.viki.rf.world.block.Block;

import java.util.HashMap;
import java.util.Map;

/** Client-side texture atlas built from registered block extensions. Not thread-safe. */
final class BlockTextureAtlas implements AutoCloseable {
  private static final int PADDING = 1;

  private final TextureAtlas atlas;
  private final Map<String, TexturePart> textures;

  private BlockTextureAtlas(
      TextureAtlas atlas, Map<String, TexturePart> textures) {
    this.atlas = atlas;
    this.textures = textures;
  }

  static BlockTextureAtlas open(Device device, Registry<Block> blocks) {
    TextureAtlas atlas = new TextureAtlas(device, PADDING);
    Map<String, TexturePart> textures = new HashMap<>();
    Resource resources = Resource.classpath(BlockTextureAtlas.class);
    try {
      for (Block block : blocks) {
        for (var state : block.states()) {
          BlockClientExtension extension = BlockClientExtension.get(state);
          if (!(extension instanceof StandardBlockClientExtension standard)
              || standard.texturePath().isEmpty()) {
            continue;
          }
          String texturePath = standard.texturePath();
          TexturePart texture = textures.get(texturePath);
          if (texture == null) {
            texture = atlas.accept(new PngInputStream(resources.open(texturePath)).info());
            textures.put(texturePath, texture);
          }
          standard.bindTexture(texture);
        }
      }
      return new BlockTextureAtlas(atlas, Map.copyOf(textures));
    } catch (Exception e) {
      atlas.close();
      throw new IllegalStateException("Failed to build block texture atlas", e);
    }
  }

  TexturePart texture(String texturePath) {
    TexturePart texture = textures.get(texturePath);
    if (texture == null) {
      throw new IllegalArgumentException(
          "Block texture was not registered in the atlas: " + texturePath);
    }
    return texture;
  }

  @Override
  public void close() {
    atlas.close();
  }
}
