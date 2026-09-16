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

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.gfx.util.ZeroCopyVertexStore;
import io.viki.momentum.gfx.cmd.Encoder;
import io.viki.momentum.gfx.cmd.EncoderDesc;
import io.viki.momentum.gfx.glfw.GlfwDesktopView;
import io.viki.momentum.gfx.io.ImageInfo;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.opengl.OpenGLDevice;
import io.viki.momentum.gfx.quick2d.particle.Particle2D;
import io.viki.momentum.gfx.quick2d.particle.ParticleSystem2D;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.text.FallbackFont;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.texture.TextureAtlas;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.math.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the GPU particle system with user-managed particles.
 */
public class TestParticles {

  static final class MyParticle extends Particle2D {
    float x;
    float y;
    float vx;
    float vy;
    float age;
    float maxLife;
    float size;
    float endSize;
    Color c0 = Color.WHITE;
    Color c1 = Color.EMPTY;
    float rot;
    float angVel;

    @Override public float posX() { return x; }
    @Override public float posY() { return y; }
    @Override public float width() { return size + (endSize - size) * (age / maxLife); }
    @Override public float height() { return width(); }
    @Override public Color color() { return Color.lerp(c0, c1, age / maxLife); }
    @Override public float rotation() { return rot; }
    @Override public boolean alive() { return age < maxLife; }

    void update(float dt, float gx, float gy) {
      vx += gx * dt;
      vy += gy * dt;
      x += vx * dt;
      y += vy * dt;
      rot += angVel * dt;
      age += dt;
    }
  }

  public static void main(String[] args) {
    View host = new GlfwDesktopView();
    host.initialize();

    Device dev = new OpenGLDevice();
    dev.load(host);
    FallbackFont.init(dev);

    Camera2D camera = new Camera2D(800, 450, dev.getTransformHandler());
    Encoder encoder = dev.getEncoder(EncoderDesc.DEFAULT);

    TextureAtlas atlas = new TextureAtlas(dev);
    TexturePart circleTex = atlas.accept(generateCircle(64));
    TexturePart circleTex2 = atlas.accept(generateCircle(32));

    ParticleSystem2D emitter = ParticleSystem2D.builder(dev)
        .maxParticles(40960)
        .build();

    RandomGenerator rng = RandomGenerator.DEFAULT;
    BatchedGraphics g = new BatchedGraphics(new ZeroCopyVertexStore(), dev);
    List<MyParticle> myParticles = new ArrayList<>();

    long lastNanos = System.nanoTime();
    float spawnTimer = 0f;

    while (!host.shouldClose()) {
      long nowNanos = System.nanoTime();
      float dt = (nowNanos - lastNanos) / 1_000_000_000f;
      lastNanos = nowNanos;
      if (dt > 1f / 20f) dt = 1f / 20f;

      host.pollEvents();
      camera.setOrthographic(800, 450);

      float mx = (float) host.snapshot().cursorX();
      float my = (float) host.snapshot().cursorY();
      Vector2 vec = camera.unproject(new Vector2(mx, my), Rectangle.of(0, 0, host.getWidth(), host.getHeight()));
      mx = vec.x();
      my = vec.y();

      if (mx >= 0 && my >= 0 && mx <= 800 && my <= 450) {
        spawnTimer += dt;
        while (spawnTimer >= 1f / 1200f) {
          spawnTimer -= 1f / 1200f;
          float angle = (float) rng.nextDouble(0, Math.PI * 2);
          float speed = (float) rng.nextDouble(40, 150);

          MyParticle p = new MyParticle();
          p.texture(circleTex);
          p.x = mx;
          p.y = my;
          p.vx = (float) Math.cos(angle) * speed;
          p.vy = (float) Math.sin(angle) * speed * 2;
          p.size = 8;
          p.endSize = 32;
          p.maxLife = (float) rng.nextDouble(3, 5);
          p.angVel = (float) rng.nextDouble(-Math.PI, Math.PI);
          p.rot = (float) rng.nextDouble(0, Math.PI * 2);
          emitter.spawn(p);
          myParticles.add(p);

          MyParticle p2 = new MyParticle();
          p2.texture(circleTex2);
          p2.x = mx;
          p2.y = my;
          p2.vx = (float) Math.cos(angle) * speed* 2;
          p2.vy = (float) Math.sin(angle) * speed;
          p2.size = 4;
          p2.endSize = 16;
          p2.c0 = Color.RED;
          p2.maxLife = (float) rng.nextDouble(2.5f, 5.5f);
          p2.angVel = (float) rng.nextDouble(-Math.PI, Math.PI);
          p2.rot = (float) rng.nextDouble(0, Math.PI * 2);
          emitter.spawn(p2);
          myParticles.add(p2);
        }
      }

      // user physics
      for (MyParticle mp : myParticles) {
        mp.update(dt, 0, 500);
      }
      // remove dead
      myParticles.removeIf(mp -> !mp.alive());

      encoder.beginPass(RenderPass.DEFAULT);
      encoder.setViewport(0, 0, host.getWidth(), host.getHeight());
      emitter.draw(encoder, camera.viewProjectionMatrix());
      encoder.endPass();
      encoder.queuedExecute();
      encoder.reset();

      g.begin(RenderPass.NOT_CLEAR);
      g.setCamera(camera);
      g.drawText(Literal.of("Particles: " + emitter.particleCount()), 5, 5);
      g.end();

      dev.execute();
      dev.submit(host::present);
      dev.pollEvents();
      host.snapshot().clearFrameState();
    }

    g.close();
    atlas.close();
    encoder.close();
    dev.close();
    host.close();
  }

  private static ImageInfo generateCircle(int size) {
    byte[] pixels = new byte[size * size * 4];
    float cx = size / 2f;
    float cy = size / 2f;
    float r = size / 2f - 1;
    float soft = r * 0.15f;
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        int alpha;
        if (dist <= r - soft) alpha = 255;
        else if (dist >= r) alpha = 0;
        else { float t = (dist - (r - soft)) / soft; alpha = (int) (255 * (1f - t * t)); }
        int idx = (y * size + x) * 4;
        pixels[idx] = (byte) 255;
        pixels[idx + 1] = (byte) 255;
        pixels[idx + 2] = (byte) 255;
        pixels[idx + 3] = (byte) alpha;
      }
    }
    return new ImageInfo(size, size, 4, pixels);
  }
}
