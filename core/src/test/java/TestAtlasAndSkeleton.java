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
import io.viki.momentum.gfx.glfw.GlfwDesktopView;
import io.viki.momentum.gfx.io.ImageInfo;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.opengl.OpenGLDevice;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.quick2d.sprite.Bone2D;
import io.viki.momentum.gfx.quick2d.sprite.Skeleton2D;
import io.viki.momentum.gfx.text.FallbackFont;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.texture.TextureAtlas;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.Matrix3x2;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.math.noise.PerlinNoise;
import io.viki.momentum.math.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demonstrates the {@link TextureAtlas} with procedurally generated noise
 * sprites, plus a 2D humanoid skeleton with animated walking motion.
 */
public class TestAtlasAndSkeleton {

  private static int spriteCount;
  private static final long SEED = 42;

  public static void main(String[] args) {
    View host = new GlfwDesktopView();
    host.initialize();

    Device dev = new OpenGLDevice();
    dev.load(host);
    FallbackFont.init(dev);

    Camera2D camera = new Camera2D(800, 450, dev.getTransformHandler());
    BatchedGraphics g = new BatchedGraphics(new ZeroCopyVertexStore(), dev);

    // --- Atlas set up ---
    Random rng = new Random(SEED);
    TextureAtlas atlas = new TextureAtlas(dev);
    PerlinNoise noise = new PerlinNoise(RandomGenerator.DEFAULT);
    List<SpriteInfo> sprites = new ArrayList<>();
    List<Vector2> positions = new ArrayList<>();

    System.out.println("Atlas initial size: " + atlas.size() + "x" + atlas.size());

    // --- Build humanoid skeleton ---
    Skeleton2D skeleton = buildHumanoid();
    float animTime = 0f;

    int it = 0;

    // --- TestVertexBuilder2D loop ---
    while (!host.shouldClose()) {
      host.pollEvents();

      float dt = 1f / 60f;
      animTime += dt;

      camera.setOrthographic(800, 450);
      g.begin(RenderPass.DEFAULT);
      g.setViewport(Rectangle.of(0, 0, host.getWidth(), host.getHeight()));
      g.setCamera(camera);

      // ── Add new sprite every ~0.33s ──────────
      if (++it % 20 == 0) {
        int w = 12 + rng.nextInt(64);
        int h = 12 + rng.nextInt(64);
        float ox = rng.nextFloat() * 100f;
        float oy = rng.nextFloat() * 100f;
        float scale = 0.02f + rng.nextFloat() * 0.06f;

        byte[] pixels = generateNoiseTexture(noise, w, h, ox, oy, scale);
        ImageInfo info = new ImageInfo(w, h, 4, pixels);
        TexturePart part = atlas.accept(info);
        sprites.add(new SpriteInfo(part, w, h));
        spriteCount++;
        float x = 50 + rng.nextFloat() * 500;
        float y = 50 + rng.nextFloat() * 350;
        positions.add(new Vector2(x, y));
      }

      // ── Draw noise sprites ────────────────────
      for (int i = 0; i < spriteCount; i++) {
        SpriteInfo s = sprites.get(i);
        Vector2 pos = positions.get(i);
        g.draw(s.part, pos.x(), pos.y(), s.w, s.h);
      }

      // ── Animate and draw skeleton ─────────────
      animateWalk(skeleton, animTime);
      Map<String, Matrix3x2> boneXforms = skeleton.computeWorldTransforms();
      drawSkeleton(g, skeleton, boneXforms, 650, 300);

      // ── Atlas preview (bottom-left corner) ─────
      if (atlas.texture() != null) {
        int previewSize = Math.min(atlas.size(), 128);
        g.draw(atlas.texture(), 5, 5, previewSize, previewSize,
            0, 0, atlas.size(), atlas.size());
      }

      // ── Labels ────────────────────────────────
      g.setTint(Color.WHITE);
      g.drawText(Literal.of("Sprites: " + spriteCount + "  |  Atlas: " + atlas.size() + "px"),
          10, host.getHeight() - 20);

      g.end();
      g.flush();

      dev.execute();
      dev.submit(host::present);
      dev.pollEvents();
    }

    atlas.close();
    g.close();
    dev.close();
    host.close();
  }

  // ─── Skeleton2D ─────────────────────────────────

  /** Builds a simple humanoid skeleton. */
  private static Skeleton2D buildHumanoid() {
    // Root → torso
    Bone2D torso = new Bone2D("torso");
    torso.translation(new Vector2(0, 0));
    torso.origin(new Vector2(0, 20));

    // Head
    Bone2D head = new Bone2D("head", torso);
    head.translation(new Vector2(0, -40));
    head.origin(new Vector2(0, 12));

    // Upper arms
    Bone2D upperArmL = new Bone2D("upper_arm_l", torso);
    upperArmL.translation(new Vector2(-18, -25));
    upperArmL.origin(new Vector2(0, 8));
    Bone2D upperArmR = new Bone2D("upper_arm_r", torso);
    upperArmR.translation(new Vector2(18, -25));
    upperArmR.origin(new Vector2(0, 8));

    // Lower arms
    Bone2D lowerArmL = new Bone2D("lower_arm_l", upperArmL);
    lowerArmL.translation(new Vector2(0, 24));
    lowerArmL.origin(new Vector2(0, 6));
    Bone2D lowerArmR = new Bone2D("lower_arm_r", upperArmR);
    lowerArmR.translation(new Vector2(0, 24));
    lowerArmR.origin(new Vector2(0, 6));

    // Upper legs
    Bone2D upperLegL = new Bone2D("upper_leg_l", torso);
    upperLegL.translation(new Vector2(-10, 20));
    upperLegL.origin(new Vector2(0, -8));
    Bone2D upperLegR = new Bone2D("upper_leg_r", torso);
    upperLegR.translation(new Vector2(10, 20));
    upperLegR.origin(new Vector2(0, -8));

    // Lower legs
    Bone2D lowerLegL = new Bone2D("lower_leg_l", upperLegL);
    lowerLegL.translation(new Vector2(0, 24));
    lowerLegL.origin(new Vector2(0, -6));
    Bone2D lowerLegR = new Bone2D("lower_leg_r", upperLegR);
    lowerLegR.translation(new Vector2(0, 24));
    lowerLegR.origin(new Vector2(0, -6));

    return new Skeleton2D(torso);
  }

  /** Applies a walking animation to the skeleton. */
  private static void animateWalk(Skeleton2D skeleton, float time) {
    float swing = (float) Math.sin(time * 3f) * 0.6f;

    // Arms swing opposite to legs
    Bone2D upperArmL = skeleton.findBone("upper_arm_l");
    Bone2D upperArmR = skeleton.findBone("upper_arm_r");
    Bone2D lowerArmL = skeleton.findBone("lower_arm_l");
    Bone2D lowerArmR = skeleton.findBone("lower_arm_r");
    Bone2D upperLegL = skeleton.findBone("upper_leg_l");
    Bone2D upperLegR = skeleton.findBone("upper_leg_r");
    Bone2D lowerLegL = skeleton.findBone("lower_leg_l");
    Bone2D lowerLegR = skeleton.findBone("lower_leg_r");

    if (upperArmL != null) upperArmL.rotation(swing);
    if (upperArmR != null) upperArmR.rotation(-swing);
    if (lowerArmL != null) lowerArmL.rotation(swing * 2.5f);
    if (lowerArmR != null) lowerArmR.rotation(-swing * 2.5f);
    if (upperLegL != null) upperLegL.rotation(-swing);
    if (upperLegR != null) upperLegR.rotation(swing);
    if (lowerLegL != null) lowerLegL.rotation(-swing * 2.5f);
    if (lowerLegR != null) lowerLegR.rotation(swing * 2.5f);

    // Slight body bob
    Bone2D torso = skeleton.findBone("torso");
    if (torso != null) {
      float bob = Math.abs((float) Math.sin(time * 6f)) * 3f;
      torso.translation(new Vector2(0, bob - 1.5f));
    }
  }

  /** Draws the skeleton as colored lines (bones) and circles (joints). */
  private static void drawSkeleton(BatchedGraphics g, Skeleton2D skeleton,
                                   Map<String, Matrix3x2> transforms,
                                   float originX, float originY) {
    // Bone2D length → gradient pairs
    String[][] bones = {
        {"torso", "head",         "#FFCC80"},
        {"torso", "upper_arm_l",  "#FF9966"},
        {"torso", "upper_arm_r",  "#FF9966"},
        {"upper_arm_l", "lower_arm_l", "#FF7744"},
        {"upper_arm_r", "lower_arm_r", "#FF7744"},
        {"torso", "upper_leg_l",  "#6699CC"},
        {"torso", "upper_leg_r",  "#6699CC"},
        {"upper_leg_l", "lower_leg_l", "#4477AA"},
        {"upper_leg_r", "lower_leg_r", "#4477AA"},
    };

    for (String[] bone : bones) {
      String parent = bone[0];
      String child = bone[1];
      Matrix3x2 pt = transforms.get(parent);
      Matrix3x2 ct = transforms.get(child);
      if (pt == null || ct == null) continue;

      Vector2 p1 = pt.transform(Vector2.ZERO);
      Vector2 p2 = ct.transform(Vector2.ZERO);
      g.setTint(Color.createHex(bone[2]));
      g.drawLine(originX + p1.x(), originY + p1.y(),
          originX + p2.x(), originY + p2.y());
    }

    // Joints
    g.setTint(Color.WHITE);
    for (Matrix3x2 t : transforms.values()) {
      Vector2 p = t.transform(Vector2.ZERO);
      g.drawPoint(originX + p.x(), originY + p.y());
    }

    // Head circle
    Matrix3x2 headT = transforms.get("head");
    if (headT != null) {
      Vector2 hp = headT.transform(Vector2.ZERO);
      g.setTint(Color.createHex("#FFCC80"));
      g.drawOval(originX + hp.x() - 12, originY + hp.y() - 12, 24, 24);
    }
  }

  // ─── Noise texture ────────────────────────────

  private static byte[] generateNoiseTexture(PerlinNoise noise, int w, int h,
                                             float ox, float oy, float scale) {
    byte[] pixels = new byte[w * h * 4];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        float nx = ox + x * scale;
        float ny = oy + y * scale;
        int r = clampByte((int) ((noise.generate(nx, ny, 0f) + 1f) * 127));
        int g = clampByte((int) ((noise.generate(nx + 0.3f, ny, 0f) + 1f) * 127));
        int b = clampByte((int) ((noise.generate(nx, ny + 0.3f, 0f) + 1f) * 127));

        int idx = (y * w + x) * 4;
        pixels[idx]     = (byte) r;
        pixels[idx + 1] = (byte) g;
        pixels[idx + 2] = (byte) b;
        pixels[idx + 3] = (byte) 255;
      }
    }
    return pixels;
  }

  private static int clampByte(int v) {
    return Math.clamp(v, 0, 255);
  }

  private record SpriteInfo(TexturePart part, int w, int h) {}
}
