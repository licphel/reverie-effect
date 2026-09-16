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

package io.viki.momentum.gfx.opengl;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.gfx.glfw.GlfwDesktopView;
import io.viki.momentum.gfx.pass.RenderTargetDesc;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureDesc;
import io.viki.momentum.gfx.texture.TextureFormat;
import io.viki.momentum.math.Cube;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glReadPixels;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RGBA;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGetTexImage;

/**
 * Verifies that {@code OpenGLTexture.submit} and {@code FboTexture.submit} store the
 * uploaded rows identically: a known top-origin pattern (row 0 = red, row 1 = blue) is
 * submitted to both, read back, and compared byte for byte.
 */
public class TestTextureFlip {
  /** 2×2 RGBA8: top row red, bottom row blue. */
  private static final byte[] PATTERN = {
      (byte) 255, 0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255,
      0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255, (byte) 255
  };

  @Test
  void storageMatches() {
    DesktopView display = new GlfwDesktopView();
    display.setTitle("Texture flip test");
    display.initialize();
    Device dev = new OpenGLDevice();
    dev.load(display);

    try {
      byte[] fromTexture = readOpenGLTexture(dev);
      byte[] fromFbo = readFboTexture(dev);
      System.out.println("OpenGLTexture readback: " + hex(fromTexture));
      System.out.println("FboTexture     readback: " + hex(fromFbo));
      // row 0 of the readback is the GL first row: it must hold the submitted
      // bottom row (blue) — the flip is applied, matching the lightmap convention
      System.out.println("Expected pattern  [bottom-first]: " + hex(new byte[] {
          0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255, (byte) 255,
          (byte) 255, 0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255
      }));
      System.out.println("Identical storage: " + Arrays.equals(fromTexture, fromFbo));
      assertTrue(Arrays.equals(fromTexture, fromFbo), "OpenGLTexture and FboTexture store rows differently");
    } finally {
      dev.close();
      display.close();
    }
  }

  /** Submits the pattern to a plain texture and reads the stored bytes back. */
  private static byte[] readOpenGLTexture(Device dev) {
    OpenGLTexture tex = (OpenGLTexture) dev.getTexture(new TextureDesc.Builder()
        .width(2).height(2).format(TextureFormat.RGBA8).build());
    tex.submit(ByteBuffer.wrap(PATTERN), Cube.of(0, 0, 0, 2, 2, 1));
    dev.execute();

    ByteBuffer out = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    glBindTexture(GL_TEXTURE_2D, tex.handle);
    glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, out);
    glBindTexture(GL_TEXTURE_2D, 0);
    return drain(out);
  }

  /** Submits the pattern to a render target's FboTexture and reads its FBO back. */
  private static byte[] readFboTexture(Device dev) {
    OpenGLRenderTarget rt = (OpenGLRenderTarget) dev.getRenderTarget(RenderTargetDesc.offscreen(2, 2));
    Texture fboTex = rt.pin();
    fboTex.submit(ByteBuffer.wrap(PATTERN), Cube.of(0, 0, 0, 2, 2, 1));
    dev.execute();

    ByteBuffer out = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    glBindFramebuffer(GL_FRAMEBUFFER, rt.fboHandle());
    glReadPixels(0, 0, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, out);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return drain(out);
  }

  private static byte[] drain(ByteBuffer buf) {
    byte[] out = new byte[buf.remaining()];
    buf.rewind();
    buf.get(out);
    return out;
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString().trim();
  }
}
