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

import io.viki.momentum.gfx.texture.TexturePart;

import java.util.List;

/** Built-in skies backed by the client ambient resources. */
public final class SkyLibrary {
  private SkyLibrary() {
  }

  /** Uses the forest sky until biome selection is implemented. */
  public static SkySupplier biomeSupplier(AmbientAssets assets) {
    Sky forest = forest(assets);
    return (level, position) -> forest;
  }

  /** Creates the shared gradient, cloud and celestial layer. */
  public static Sky atmosphere(AmbientAssets assets) {
    return sky(assets, List.of(), 0x4A7F1D2C90E5B603L);
  }

  /** Creates the temperate forest sky. */
  public static Sky forest(AmbientAssets assets) {
    return sky(assets, List.of(
        new SkyLayer()
            .addLayer(assets.require("forest_1"), false, 0.95F)
            .addLayer(assets.require("forest_0"), false, 0.85F)),
        0x46534B4F3F1A2D11L);
  }

  /** Creates the desert sky. */
  public static Sky desert(AmbientAssets assets) {
    return sky(assets, List.of(
        new SkyLayer()
            .addLayer(assets.require("desert_1"), false, 0.95F)
            .addLayer(assets.require("desert_0"), false, 0.85F)),
        0x2E7D7F2B9A1C4503L);
  }

  /** Creates the snow-field sky. */
  public static Sky snowField(AmbientAssets assets) {
    return sky(assets, List.of(
        new SkyLayer()
            .addLayer(assets.require("snow_field_1"), false, 0.95F)
            .addLayer(assets.require("snow_field_0"), false, 0.85F)),
        0x7B0E5D31AC8249F1L);
  }

  /** Creates the cave sky. */
  public static Sky cave(AmbientAssets assets) {
    return sky(assets, List.of(new SkyLayer()
        .addLayer(assets.require("cave_0"), true, 0.8F)),
        0x19C0FFEE44BADD11L);
  }

  /** Creates the lava-cave sky. */
  public static Sky lavaCave(AmbientAssets assets) {
    return sky(assets, List.of(new SkyLayer()
        .addLayer(assets.require("lava_cave_0"), true, 0.8F)),
        0x6A7E5A9D0B1242C3L);
  }

  private static Sky sky(AmbientAssets assets, List<SkyLayer> layers, long seed) {
    TexturePart cloud = assets.require("cloud");
    TexturePart body1 = assets.require("body_1");
    TexturePart body2 = assets.require("body_2");
    return new Sky(cloud, body1, body2, layers, seed);
  }
}
