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

package io.viki.rf.world.light;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.DirectBufferPool;
import io.viki.momentum.gfx.texture.*;
import io.viki.momentum.math.Cube;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.block.Shape;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Uploads a {@link LightEngine}'s raw RGB buffer to GPU as lightmap textures.
 *
 * <p>Each tile becomes one RGBA16F texel of the front or wall lightmap (the wall layer
 * bakes in {@link LightEngine#WALL_MULTIPLIER}). The textures match the engine window
 * one-to-one, so the lightmap uv covers them completely and linear sampling never
 * mixes in data outside the window. Ambient occlusion is not rendered here: the
 * engine's obstacle light attenuation produces corner shadows in the light data itself.
 *
 * <p>The textures are re-uploaded when the engine's light version changes (a static
 * frame costs nothing) and resized when the window resizes. The uploads are submitted
 * on the render thread and executed by the frame-end {@code execute()} before the
 * compose passes draw, so the compose always samples the content packed this frame;
 * the uniform origin/size therefore reports the last successfully packed window.
 */
@SideOnly(dist = Dist.CLIENT)
public final class LightMapRenderer implements AutoCloseable {
  /** Wall-level ambient occlusion multiplier. */
  public static final float AO_MUL = 0.65F;
  /** Halves per RGBA16F texel. */
  private static final int HALVES_PER_TEXEL = 4;
  /**
   * Debug switch: when {@code true}, the lightmaps are packed as plain white
   * rectangles, skipping the light values to isolate rendering cost.
   */
  public boolean fullBright;
  private @Nullable Device dev;
  private @Nullable Texture frontLightTex;
  private @Nullable Texture wallLightTex;
  private @Nullable Sampler lightSampler;
  private int texW;
  private int texH;
  private long lastVersion = -1;
  /**
   * Window origin/size of the last successfully packed window; the compose
   * uniform must sample with these so it stays aligned with the texture
   * contents even on frames where the upload was skipped (mid-resize).
   */
  private int packedOriginX;
  private int packedOriginY;
  private int packedSizeX;
  private int packedSizeY;

  /**
   * Creates a lightmap renderer.
   */
  public LightMapRenderer() {
  }

  /**
   * Allocates the samplers used to sample the lightmaps; the textures themselves are
   * created lazily on the first {@link #update(LightEngine)} once the engine window size
   * is known.
   *
   * @param dev the graphics device
   */
  public void init(Device dev) {
    this.dev = dev;
    lightSampler = dev.getSampler(new SamplerDesc.Builder()
        .minFilter(TextureFilter.LINEAR)
        .magFilter(TextureFilter.LINEAR)
        .wrapX(TextureWrap.CLAMP_TO_EDGE)
        .wrapY(TextureWrap.CLAMP_TO_EDGE)
        .build());
  }

  /**
   * Returns the front-layer lightmap texture (1 texel per tile).
   *
   * @return the front lightmap texture
   */
  public @Nullable Texture frontLightTexture() {
    return frontLightTex;
  }

  /**
   * Returns the wall-layer lightmap texture, dimmed on wall tiles.
   *
   * @return the wall lightmap texture
   */
  public @Nullable Texture wallLightTexture() {
    return wallLightTex;
  }

  /**
   * Returns the linearly-filtered sampler for the lightmap textures.
   *
   * @return the lightmap sampler
   */
  public @Nullable Sampler lightSampler() {
    return lightSampler;
  }

  /**
   * Returns the window origin the textures currently contain — the origin of the last
   * successfully packed window. Sampling with this origin keeps the compose uv aligned
   * with the texture contents even on frames where the upload was skipped.
   *
   * @return the X origin of the packed window
   */
  public int packedOriginX() {
    return packedOriginX;
  }

  /**
   * Returns the window origin the textures currently contain (see
   * {@link #packedOriginX()}).
   *
   * @return the Y origin of the packed window
   */
  public int packedOriginY() {
    return packedOriginY;
  }

  /**
   * Returns the width of the texture contents (the window size when last packed).
   *
   * @return the packed window width in tiles
   */
  public int packedSizeX() {
    return packedSizeX;
  }

  /**
   * Returns the height of the texture contents (the window size when last packed).
   *
   * @return the packed window height in tiles
   */
  public int packedSizeY() {
    return packedSizeY;
  }

  /**
   * Re-uploads the lightmaps from the engine's front buffer when its light version
   * changed; a static frame does nothing.
   *
   * @param engine the engine whose front buffer is uploaded
   */
  public void update(LightEngine engine) {
    if (dev == null) {
      return;
    }
    long version = engine.lightVersion();
    if (version == lastVersion) {
      return;
    }
    int w = engine.sizeX();
    int h = engine.sizeY();
    float[] buf = engine.buffer();
    if (buf.length != w * h * LightEngine.STRIDE) {
      return; // the worker is mid-resize — try again next frame (version unacked)
    }

    lastVersion = version;
    int ox = engine.frontOriginX();
    int oy = engine.frontOriginY();
    ensureTextures(w, h);
    packedOriginX = ox;
    packedOriginY = oy;
    packedSizeX = w;
    packedSizeY = h;

    packLight(engine, buf, false, frontLightTex, w, h, ox, oy);
    packLight(engine, buf, true, wallLightTex, w, h, ox, oy);
  }

  /** Creates or resizes the two lightmap textures to the current engine window. */
  private void ensureTextures(int w, int h) {
    assert dev != null;
    if (frontLightTex != null && texW == w && texH == h) {
      return;
    }
    if (frontLightTex != null) {
      frontLightTex.close();
    }
    if (wallLightTex != null) {
      wallLightTex.close();
    }
    texW = w;
    texH = h;
    frontLightTex = dev.getTexture(new TextureDesc.Builder()
        .width(w).height(h).format(TextureFormat.RGBA16F).build());
    wallLightTex = dev.getTexture(new TextureDesc.Builder()
        .width(w).height(h).format(TextureFormat.RGBA16F).build());
  }

  /**
   * Packs the engine's raw RGB values into one RGBA16F texel per tile and uploads them
   * into the window-sized texture.
   *
   * @param engine the light engine
   * @param buf    the engine's front buffer
   * @param wall   whether to bake {@link LightEngine#WALL_MULTIPLIER} on wall tiles
   * @param tex    the texture to upload into
   * @param w      the engine window width in tiles
   * @param h      the engine window height in tiles
   * @param ox     the engine window origin X
   * @param oy     the engine window origin Y
   */
  private void packLight(LightEngine engine, float[] buf, boolean wall, @Nullable Texture tex,
                         int w, int h, int ox, int oy) {
    if (tex == null) {
      return;
    }
    ByteBuffer bb = DirectBufferPool.acquire(w * h * HALVES_PER_TEXEL * Short.BYTES);

    for (int yy = 0; yy < h; yy++) {
      int y = h - 1 - yy;
      for (int x = 0; x < w; x++) {
        int o = (x + y * w) * LightEngine.STRIDE;
        float r = buf[o];
        float g = buf[o + 1];
        float b = buf[o + 2];
        if (fullBright) {
          r = Math.max(r, 1.0F);
          g = Math.max(g, 1.0F);
          b = Math.max(b, 1.0F);
        }
        if (wall) {
          r *= LightEngine.WALL_MULTIPLIER;
          g *= LightEngine.WALL_MULTIPLIER;
          b *= LightEngine.WALL_MULTIPLIER;
          if (engine.cc.getBlock(ox + x, oy + y).shape() == Shape.SOLID) {
            r *= AO_MUL;
            g *= AO_MUL;
            b *= AO_MUL;
          }
        }
        bb.putShort(Float.floatToFloat16(r));
        bb.putShort(Float.floatToFloat16(g));
        bb.putShort(Float.floatToFloat16(b));
        bb.putShort(Float.floatToFloat16(1F));
      }
    }
    bb.flip();
    // submit snapshots the data on this thread (see OpenGLTexture.submit), so
    // the pooled buffer can be released right away
    tex.submit(bb, Cube.of(0, 0, 0, w, h, 1));
    DirectBufferPool.release(bb);
  }

  @Override
  public void close() {
    if (frontLightTex != null) {
      frontLightTex.close();
    }
    if (wallLightTex != null) {
      wallLightTex.close();
    }
    if (lightSampler != null) {
      lightSampler.close();
    }
  }
}
