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

package io.viki.rf.client;

import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.block.Block;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.GameConstants;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.light.CelestialUtil;
import io.viki.rf.world.util.BlockPos;
import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.gfx.glfw.GlfwDesktopView;
import io.viki.momentum.input.Key;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.opengl.OpenGLDevice;
import io.viki.momentum.gfx.text.FallbackFont;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.util.Loop;
import io.viki.momentum.resource.Resource;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Runs the lighting demo and finishes each frame with a pixel-art presentation
 * post-process.
 *
 * <p>The world and lightmap are composed at the frame resolution. A dedicated
 * postprocessor then stabilizes source-pixel transitions, diffuses highlights,
 * and applies screen-aligned dithering and a restrained warm grade.
 *
 * <p>The renderer is not thread-safe. All graphics resources and draw calls
 * are owned by the thread running {@link #run()}.
 */
@SideOnly(dist = Dist.CLIENT)
public final class GameClient {
  private static @Nullable ClientLevel networkLevel;
  private static @Nullable Entity networkPlayer;
  private static final int WIN_W = 1280;
  private static final int WIN_H = 720;
  /** How often holding R advances the tile shape under the cursor (seconds). */
  private static final float SHAPE_CYCLE_INTERVAL = 0.12F;
  /** Day clock multiplier while Ctrl is held (8 real minutes → 15 s per day). */
  private static final float FAST_TIME_SCALE = 32F;

  /**
   * Starts the desktop demo, owns its render loop, and releases its graphics
   * resources when the loop exits.
   *
   * @param args command-line arguments; currently ignored
   */
  public static void run() {
    DesktopView display = new GlfwDesktopView();
    display.setTitle("Reverie Effect | 梦效应");
    display.setSize(new Vector2(WIN_W, WIN_H));
    display.initialize();
    display.setVsync(false);
    try {
      display.setIcon(new PngInputStream(
          Resource.classpath(GameClient.class).open("/icon.png")).info());
    } catch (IOException e) {
      // ignored
    }

    Device dev = new OpenGLDevice();
    dev.load(display);
    FallbackFont.init(dev);

    DemoWorld world = DemoWorld.open();
    Level level = world.level();
    Entity player = world.player();
    Camera2D camera = new Camera2D((float) WIN_W, (float) WIN_H, dev.getTransformHandler());
    ClientRenderer renderer = ClientRenderer.open(dev, display, level, player, camera);
    Controller[] controller = {null};
    Key ML = display.snapshot().key(KeyCode.MOUSE_LEFT);
    Vector2[] oldCtRef = {Vector2.ZERO};
    Vector2[] prevCamRef = {Vector2.ZERO};

    Loop.launch(GameConstants.TICKS_PER_SECOND, 120, () -> {
      if (display.shouldClose()) {
        Loop.stop();
      }

        // -- tick: fixed 20 Hz logic ----------------------------------------
      float dt = Loop.delta();
      InputSnapshot snap = display.snapshot();
      try {
        if (snap.isDown(KeyCode.ESCAPE)) {
          Loop.stop();
          return;
        }
        if (!renderer.gameStarted()) {
          Vector2 inputSize = display.getInputSize();
          renderer.updateTitle(dt, snap, ML, display.getWidth(), display.getHeight(),
              inputSize.x(), inputSize.y());
          return;
        }
        if (!world.started()) {
          var mode = renderer.startMode();
          if (mode == null) {
            return;
          }
          world.start(mode);
        }
        if (!world.ready()) {
          world.tick(dt);
          return;
        }
        if (controller[0] == null) {
          controller[0] = Controller.create(world, snap);
        }
        Controller activeController = controller[0];
        if (activeController == null) {
          throw new IllegalStateException("Ready client has no controller");
        }
        activeController.tick(dt, snap, display, camera, oldCtRef[0], renderer);

        float cx = player.center().xf();
        float cy = player.center().yf();

        prevCamRef[0] = oldCtRef[0];
        oldCtRef[0] = oldCtRef[0].add(new Vector2(cx, cy).subtract(oldCtRef[0]).multiply(0.2F));
        // -- sky / light engine ----------------------------------------------
        // sunlight is injected externally from the day phase (CelestialUtil); the
        // engine stays time-of-day agnostic
        Color sun = CelestialUtil.lightingSunlight(level);
        level.lightEngine().sunlight[0] = sun.red();
        level.lightEngine().sunlight[1] = sun.green();
        level.lightEngine().sunlight[2] = sun.blue();
        var camBounds = ClientRenderer.cameraBounds(camera);
        level.lightEngine().tick(camBounds);
      } finally {
        // The render callback samples native events. Keep their transitions
        // alive until this logic tick has consumed them.
        snap.clearFrameState();
      }
    }, () -> {
      // Native input must be sampled at render cadence. Sampling only from the
      // fixed 20 Hz tick can consume a complete press/release pair at once.
      display.pollEvents();
      renderer.render(level, player, camera,
          prevCamRef[0], oldCtRef[0], display.snapshot(), ML);
    });

    renderer.close();
    dev.close();
    display.close();
    GlfwDesktopView.terminate();
    world.close();
    GraphicsMetrics.dump();
  }

  public static ClientLevel level() {
    ClientLevel level = networkLevel;
    if (level == null) throw new IllegalStateException("Client level is not initialized");
    return level;
  }

  public static Entity player() {
    Entity player = networkPlayer;
    if (player == null) throw new IllegalStateException("Client player is not initialized");
    return player;
  }

  static void bindNetworkWorld(ClientLevel level, Entity player) {
    networkLevel = java.util.Objects.requireNonNull(level, "level");
    networkPlayer = java.util.Objects.requireNonNull(player, "player");
  }

  static void clearNetworkWorld(ClientLevel level) {
    if (networkLevel == level) {
      networkLevel = null;
      networkPlayer = null;
    }
  }

  // -- shared --------------------------------------------------------------

  /** Advances the shape of the solid block under the cursor one step (R). */
  private static void cycleBlockShape(Level level, BlockPos bp) {
    // intrinsic fill: solid blocks can be carved; air, platforms and walls
    // keep their own shape whatever the byte says
    if (level.getBlock(bp).shape() != Shape.SOLID) {
      return;
    }
    level.setBlockShape(bp.x(), bp.y(),
        TileShape.byId(level.getBlockShape(bp.x(), bp.y())).next().id());
  }

  static BlockState defaultState(Block block) {
    return block.defaultState();
  }

}
