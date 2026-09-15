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

import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.util.FastTrigonometric;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.fluid.Liquid;
import io.viki.rf.world.level.ChunkCache;
import io.viki.rf.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Base class for engines that maintain a sliding window of per-tile light data.
 *
 * <p>Each tile stores 3 floats: the raw RGB light value. The window covers the visible
 * tiles plus a light-travel border and is recomputed every tick — it is small (a
 * screen-sized area), so a full recompute is cheap. The window is
 * double-buffered so the renderer always reads a complete frame while the next one is
 * computed; resizing copies the overlap of the old buffers so the renderer never sees a
 * blank window.
 *
 * <p>The renderer uploads the raw RGB buffer as a lightmap texture; smooth per-tile
 * gradients and ambient occlusion are derived on the GPU at sample time.
 */
public abstract class LightEngine implements AutoCloseable {
  /** Scales light values when they are seeded and drawn. */
  public static final float AMPLIFIER = 1.0F;
  /** Luminance threshold below which light counts as darkness. */
  public static final float DARK_LUMINANCE = 0.1F;
  /** Floats per tile: raw RGB. */
  public static final int STRIDE = 3;
  /** Factor by which light on walls is dimmed. */
  public static final float WALL_MULTIPLIER = 0.7F;
  /** Maximum normalized light value. */
  public static final float MAX_VALUE_GENERAL = 1F;
  /** Light level of one discrete step of {@link #MAX_VALUE_GENERAL}. */
  public static final float UNIT = MAX_VALUE_GENERAL / 16F;
  /** Window margin in tiles: light travel distance plus slack. */
  protected static final int SPREAD_MARGIN = 17;
  /** Smallest allowed window side, in tiles. */
  protected static final int MIN_SIZE = 32;
  /** Largest allowed window side, in tiles. */
  protected static final int MAX_SIZE = 512;

  /** Sunlight gradient seeding sky illumination, refreshed externally each tick. */
  public final float[] sunlight = new float[Channel.CHANNELS.length];
  protected final Level level;
  /**
   * Bumped whenever the front buffer changes (recompute or resize), so
   * consumers like {@link LightMapRenderer} can rebuild their caches.
   */
  private final AtomicLong version = new AtomicLong();
  /** Scratch space for one tile's merged ambient light; worker-thread only. */
  private final float[] ambientScratch = new float[3];
  /**
   * Whether to use async channel dispatch.
   * This may increase performance but bring more pressure to CPU threads.
   */
  public boolean asyncDispatchRGB = false;
  /**
   * Light computation interval.
   */
  public int computeInterval = 1;
  /** The completed buffer, read by the renderer. */
  protected volatile float[] front;
  /** The buffer currently being computed. */
  protected volatile float[] back;
  /** Current window size in tiles. */
  protected volatile int sizeX = MIN_SIZE;
  protected volatile int sizeY = MIN_SIZE;
  protected volatile int oriX;
  protected volatile int oriY;
  /** Window origin of the current front buffer. */
  protected volatile int frontOriX;
  protected volatile int frontOriY;
  protected @Nullable ForkJoinTask<?> asyncComputation;
  protected ChunkCache cc;
  /**
   * How light values combine everywhere (seed, draw, beam merge, spread).
   */
  protected CompositionFormula formula = CompositionFormula.ADDITIVE_CAP;
  /** Pending window resize, applied when the worker is idle. */
  private int pendingW = -1;
  private int pendingH = -1;
  /** Raw RGB per tile for beam light, merged into {@code back} after spreading. */
  private float[] beamLayer;

  /**
   * Creates a light engine for the given level, allocating its initial front, back, and
   * beam buffers.
   *
   * @param level the level to light
   */
  protected LightEngine(Level level) {
    this.level = level;
    this.cc = new ChunkCache(level); // created once, grown via checkLoss
    int len = sizeX * sizeY * STRIDE;
    this.front = new float[len];
    this.back = new float[len];
    this.beamLayer = new float[len];
  }

  /**
   * Sets how light values are combined throughout the engine.
   *
   * @param formula the composition formula to use
   */
  public void setCompositionFormula(CompositionFormula formula) {
    this.formula = formula;
  }

  /**
   * Returns the composition formula in use.
   *
   * @return the composition formula
   */
  public CompositionFormula compositionFormula() {
    return formula;
  }

  /**
   * Mixes RGB light into the raw channels of the tile at the given offset, using the
   * current composition formula.
   *
   * @param o the tile offset in the working buffer
   * @param r the red light to blend
   * @param g the green light to blend
   * @param b the blue light to blend
   */
  protected void blendWrite(int o, float r, float g, float b) {
    back[o] = formula.blend(back[o], r);
    back[o + 1] = formula.blend(back[o + 1], g);
    back[o + 2] = formula.blend(back[o + 2], b);
  }

  /**
   * Computes the merged ambient light of a tile: sky light filtered through the wall,
   * combined per channel with the ambient emission of the block, wall, and liquid via
   * the composition formula.
   *
   * @param cc  the chunk cache
   * @param x   the tile X coordinate
   * @param y   the tile Y coordinate
   * @param out receives the three channel values
   */
  protected void tileAmbient(ChunkCache cc, int x, int y, float[] out) {
    BlockState block = cc.getBlock(x, y);
    BlockState wall = cc.getWall(x, y);
    Liquid liq = cc.getLiquid(x, y);

    float s = CelestialUtil.backEmissionStrength(level, y);
    float r = wall.filterSkylight(x, y, sunlight[0] * s, Channel.RED) * AMPLIFIER;
    float g = wall.filterSkylight(x, y, sunlight[1] * s, Channel.GREEN) * AMPLIFIER;
    float b = wall.filterSkylight(x, y, sunlight[2] * s, Channel.BLUE) * AMPLIFIER;

    /*
     * Note:
     * We use Math.max here is to avoid light-overlap,
     * which propagates higher light than expected
     * when luminous block over no walls.
     */
    r = Math.max(r, block.emitAmbient(x, y, Channel.RED) * AMPLIFIER);
    g = Math.max(g, block.emitAmbient(x, y, Channel.GREEN) * AMPLIFIER);
    b = Math.max(b, block.emitAmbient(x, y, Channel.BLUE) * AMPLIFIER);
    r = Math.max(r, wall.emitAmbient(x, y, Channel.RED) * AMPLIFIER);
    g = Math.max(g, wall.emitAmbient(x, y, Channel.GREEN) * AMPLIFIER);
    b = Math.max(b, wall.emitAmbient(x, y, Channel.BLUE) * AMPLIFIER);
    if (liq != null) {
      int liqAmt = cc.getLiquidAmount(x, y);
      if (liqAmt > 0) {
        r = Math.max(r, liq.emitAmbient(x, y, liqAmt, Channel.RED) * AMPLIFIER);
        g = Math.max(g, liq.emitAmbient(x, y, liqAmt, Channel.GREEN) * AMPLIFIER);
        b = Math.max(b, liq.emitAmbient(x, y, liqAmt, Channel.BLUE) * AMPLIFIER);
      }
    }
    out[0] = r;
    out[1] = g;
    out[2] = b;
  }

  /**
   * Draws every directional beam emitted by the tile into the beam layer and recycles the
   * pooled instances.
   *
   * @param cc the chunk cache
   * @param x  the tile X coordinate
   * @param y  the tile Y coordinate
   */
  protected void tileBeams(ChunkCache cc, int x, int y) {
    List<Beam> emitBeams = cc.getBlock(x, y).emitBeams(x, y);
    if (!emitBeams.isEmpty()) {
      for (Beam bm : emitBeams) {
        drawBeam(x + 0.5F, y + 0.5F, bm);
        bm.recycle();
      }
    }
    emitBeams = cc.getWall(x, y).emitBeams(x, y);
    if (!emitBeams.isEmpty()) {
      for (Beam bm : emitBeams) {
        drawBeam(x + 0.5F, y + 0.5F, bm);
        bm.recycle();
      }
    }
    Liquid liq = cc.getLiquid(x, y);
    if (liq != null) {
      int liqAmt = cc.getLiquidAmount(x, y);
      if (liqAmt > 0) {
        emitBeams = liq.emitBeams(x, y, liqAmt);
        if (!emitBeams.isEmpty()) {
          for (Beam bm : emitBeams) {
            drawBeam(x + 0.5F, y + 0.5F, bm);
            bm.recycle();
          }
        }
      }
    }
  }

  /**
   * Seeds the raw RGB channels of a tile from its merged ambient light, and draws the
   * tile's beams into the beam layer.
   *
   * @param cc the chunk cache
   * @param x  the tile X coordinate
   * @param y  the tile Y coordinate
   */
  public void seed(ChunkCache cc, int x, int y) {
    tileAmbient(cc, x, y, ambientScratch);
    draw(x, y, ambientScratch[0], ambientScratch[1], ambientScratch[2]);
    tileBeams(cc, x, y);
  }

  /**
   * Seeds the light of an entity: its ambient light is drawn interpolated around its
   * position, and its beams into the beam layer.
   *
   * @param e the entity to seed
   */
  public void seed(Entity e) {
    float er = e.emitAmbient(Channel.RED) * AMPLIFIER;
    float eg = e.emitAmbient(Channel.GREEN) * AMPLIFIER;
    float eb = e.emitAmbient(Channel.BLUE) * AMPLIFIER;
    if (er > DARK_LUMINANCE || eg > DARK_LUMINANCE || eb > DARK_LUMINANCE) {
      drawInterpolated(e.center().xf(), e.center().yf(), er, eg, eb);
    }
    for (Beam bm : e.emitBeams()) {
      drawBeam(e.center().xf(), e.center().yf(), bm);
      bm.recycle();
    }
  }

  /**
   * Applies the tile's channel filter to an incoming light value.
   *
   * @param cc      the chunk cache
   * @param channel the channel (R/G/B)
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   * @param v       the value to filter
   * @return the filtered value
   */
  public float filter(ChunkCache cc, byte channel, int x, int y, float v) {
    BlockState block = cc.getBlock(x, y);
    Liquid liq = cc.getLiquid(x, y);
    int liqAmt = cc.getLiquidAmount(x, y);

    v = block.filterLight(cc.getBlockShape(x, y), x, y, v, channel);
    if (liq != null) {
      v = liq.filterLight(x, y, liqAmt, v, channel);
    }

    return v;
  }

  /**
   * Runs the given function concurrently for every color channel, one task per
   * channel, and waits for all of them to finish.
   *
   * @param fn the per-channel work
   */
  protected void channelDispatch(Consumer<Byte> fn) {
    if (asyncDispatchRGB) {
      final AtomicInteger remaining = new AtomicInteger(3);
      ForkJoinPool pool = ForkJoinPool.commonPool();

      pool.execute(() -> {
        fn.accept(Channel.RED);
        remaining.decrementAndGet();
      });
      pool.execute(() -> {
        fn.accept(Channel.GREEN);
        remaining.decrementAndGet();
      });
      pool.execute(() -> {
        fn.accept(Channel.BLUE);
        remaining.decrementAndGet();
      });

      while (remaining.get() > 0) {
        Thread.onSpinWait();
      }
    } else {
      fn.accept(Channel.RED);
      fn.accept(Channel.GREEN);
      fn.accept(Channel.BLUE);
    }
  }

  /**
   * Returns the current window width in tiles.
   *
   * @return the window width
   */
  public int sizeX() {
    return sizeX;
  }

  /**
   * Returns the window origin of the current front buffer, in tiles.
   *
   * @return the front window origin X
   */
  public int frontOriginX() {
    return frontOriX;
  }

  /**
   * Returns the window origin of the current front buffer, in tiles.
   *
   * @return the front window origin Y
   */
  public int frontOriginY() {
    return frontOriY;
  }

  /**
   * Returns the number of completed front-buffer changes, bumped whenever the light data
   * or the window changes.
   *
   * @return the light version
   */
  public long lightVersion() {
    return version.get();
  }

  /**
   * Returns the current window height in tiles.
   *
   * @return the window height
   */
  public int sizeY() {
    return sizeY;
  }

  /**
   * Requests a window resize, rounded up to a multiple of 16 and clamped to
   * {@link #MIN_SIZE}..{@link #MAX_SIZE}. Applied by {@link #applyPendingResize()}
   * while the worker is idle.
   *
   * @param w the desired window width in tiles
   * @param h the desired window height in tiles
   */
  protected void requestSize(int w, int h) {
    int nw = Math.clamp((w + 15) & ~15, MIN_SIZE, MAX_SIZE);
    int nh = Math.clamp((h + 15) & ~15, MIN_SIZE, MAX_SIZE);
    if (nw != sizeX || nh != sizeY) {
      pendingW = nw;
      pendingH = nh;
    }
  }

  /**
   * Applies a pending resize, allocating a new back buffer of the new size. The front
   * buffer is left untouched: it is only replaced by {@link #swap()} once the worker
   * finished the new window, so the renderer never sees a partially-filled buffer.
   *
   * @return {@code true} if a resize was applied
   */
  protected boolean applyPendingResize() {
    if (pendingW <= 0) {
      return false;
    }
    sizeX = pendingW;
    sizeY = pendingH;
    pendingW = pendingH = -1;
    back = new float[sizeX * sizeY * STRIDE];
    return true;
  }

  /**
   * Swaps the front and back buffers, publishing the freshly computed buffer and
   * updating the front window origin. Called by the worker after a compute finishes
   * (never while the renderer could read a half-filled buffer).
   *
   * <p>The back buffer stays sized to the current window: the swapped-out front is
   * reused when its size still matches (the compute clears it anyway), otherwise a
   * fresh buffer is allocated. No overlap is copied — the compute fills the whole
   * window from seed each pass.
   */
  protected void swap() {
    float[] oldFront = front;
    front = back;
    back = oldFront.length == sizeX * sizeY * STRIDE ? oldFront : new float[sizeX * sizeY * STRIDE];
    frontOriX = oriX;
    frontOriY = oriY;
  }

  /**
   * Mixes light into a tile's current value, using the composition formula. Light whose
   * channels are all at or below {@link #DARK_LUMINANCE} is ignored.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @param r the red light to add
   * @param g the green light to add
   * @param b the blue light to add
   */
  public void draw(int x, int y, float r, float g, float b) {
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
      return;
    }
    int o = backBufferIndex(x, y);
    if (o < 0) {
      return;
    }
    blendWrite(o, r, g, b);
  }

  /**
   * Adds light from a source at fractional coordinates, spreading it over the surrounding
   * tiles with an inverse-square falloff. Tiles closer than one tile receive the full
   * value; sources whose channels are all at or below {@link #DARK_LUMINANCE} are ignored.
   *
   * @param x the light source X coordinate
   * @param y the light source Y coordinate
   * @param r the red light of the source
   * @param g the green light of the source
   * @param b the blue light of the source
   */
  public void drawInterpolated(float x, float y, float r, float g, float b) {
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
      return;
    }
    int lx = (int) Math.floor(x);
    int ly = (int) Math.floor(y);
    for (int tx = lx - 3; tx < lx + 3; tx++) {
      for (int ty = ly - 3; ty < ly + 3; ty++) {
        float d2 = (float) (Math.pow(tx - x, 2) + Math.pow(ty - y, 2));
        if (d2 < 1F) {
          d2 = 1F;
        }
        draw(tx, ty, r / d2, g / d2, b / d2);
      }
    }
  }

  /**
   * Draws a directional beam into the beam layer. Beam light is kept separate from the
   * spread buffer: the cellular spread is isotropic and would wash out the cone. The beam
   * layer is blended into the raw channels after spreading (see {@link #mergeBeam()}), so
   * the cone shape survives.
   *
   * @param x    the light source X coordinate
   * @param y    the light source Y coordinate
   * @param beam the beam to draw
   */
  public void drawBeam(float x, float y, Beam beam) {
    float r = beam.r;
    float g = beam.g;
    float b = beam.b;
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
      return;
    }

    float range = maxIntensity * beam.range;
    float perBlockAir = 1F / beam.range;
    float[] bdsc = FastTrigonometric.sincos(beam.direction);
    float bdx = bdsc[1];
    float bdy = bdsc[0];
    float beamK = beam.strength / (1F - FastTrigonometric.cos(beam.halfAngle));
    float ambi = beam.ambience;

    int lx = (int) Math.floor(x);
    int ly = (int) Math.floor(y);
    int radius = (int) Math.ceil(range);
    for (int tx = lx - radius; tx <= lx + radius; tx++) {
      for (int ty = ly - radius; ty <= ly + radius; ty++) {
        float dx = tx + 0.5F - x;
        float dy = ty + 0.5F - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist == 0F) {
          int o0 = backBufferIndex(tx, ty);
          if (o0 >= 0) {
            beamLayer[o0] = formula.blend(beamLayer[o0], r * AMPLIFIER);
            beamLayer[o0 + 1] = formula.blend(beamLayer[o0 + 1], g * AMPLIFIER);
            beamLayer[o0 + 2] = formula.blend(beamLayer[o0 + 2], b * AMPLIFIER);
          }
          continue;
        }

        float attenuation = dist * perBlockAir;
        if (beamK > 0F) {
          float dot = (dx * bdx + dy * bdy) / dist;
          attenuation += (1F - ambi) * Math.min(1F, Math.max(0F, beamK * (1F - dot)));
        }
        if (attenuation >= 1F) {
          continue;
        }
        float remaining = maxIntensity - attenuation;
        if (remaining <= 0F) {
          continue;
        }
        float factor = remaining / maxIntensity;

        int o = backBufferIndex(tx, ty);
        if (o < 0) {
          continue;
        }
        beamLayer[o] = formula.blend(beamLayer[o], r * factor * AMPLIFIER);
        beamLayer[o + 1] = formula.blend(beamLayer[o + 1], g * factor * AMPLIFIER);
        beamLayer[o + 2] = formula.blend(beamLayer[o + 2], b * factor * AMPLIFIER);
      }
    }
  }

  /**
   * Blends the beam layer into the raw channels of {@code back} using the composition
   * formula, then clears it for the next frame. Call after spreading.
   */
  protected void mergeBeam() {
    if (beamLayer == null) {
      return;
    }
    int len = sizeX * sizeY * STRIDE;
    for (int i = 0; i < len; i += STRIDE) {
      if (beamLayer[i] > 0F || beamLayer[i + 1] > 0F || beamLayer[i + 2] > 0F) {
        blendWrite(i, beamLayer[i], beamLayer[i + 1], beamLayer[i + 2]);
      }
      beamLayer[i] = beamLayer[i + 1] = beamLayer[i + 2] = 0F;
    }
  }

  /**
   * Rebuilds the light window around the given camera bounds.
   *
   * <p>The window covers the visible tiles plus a light-travel border and follows the
   * camera every tick; the small size (a screenful plus border) keeps a full recompute
   * cheap, so entity light and sky light always track the camera in real time.
   *
   * @param cam the camera bounds to cover
   */
  public void tick(Rectangle cam) {
    if (level.getTicks() % Math.max(1, computeInterval) != 0) {
      return;
    }

    // adapt window to the visible area + spread margin
    requestSize((int) Math.ceil(cam.width()) + 2 * SPREAD_MARGIN + 4,
        (int) Math.ceil(cam.height()) + 2 * SPREAD_MARGIN + 4);

    if (asyncComputation != null && !asyncComputation.isDone()) {
      return; // the previous compute is still running — keep its frame
    }
    // resize only while idle. the worker must not see the array change
    if (applyPendingResize()) {
      onResized();
      version.incrementAndGet();
    }
    // fix the window origin before the worker starts: the worker lays out the
    // back buffer with oriX/oriY, and publishes them via swap() when done
    oriX = (int) Math.floor(cam.centralX()) - sizeX / 2;
    oriY = (int) Math.floor(cam.centralY()) - sizeY / 2;
    asyncComputation = ForkJoinPool.commonPool().submit(() -> {
      calculate(cam);
      swap();
      version.incrementAndGet();
    });
  }

  /**
   * Rebuilds engine state that depends on the window size after a resize takes effect.
   */
  protected void onResized() {
    beamLayer = new float[sizeX * sizeY * STRIDE];
  }

  /**
   * Computes the next frame into the back buffer.
   *
   * @param cam the camera bounds to compute light for
   */
  protected abstract void calculate(Rectangle cam);

  /**
   * Returns the light value of one channel of a tile.
   *
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   * @param channel the gradient channel to read
   * @return the channel light value, or zero if the tile is outside the
   * window
   */
  protected float getChannelValue(int x, int y, int channel) {
    int o = backBufferIndex(x, y);
    return o < 0 ? 0F : back[o + channel];
  }

  /**
   * Returns the front buffer with the last completed light computation.
   *
   * @return the front buffer
   */
  public float[] buffer() {
    return front;
  }

  /**
   * Returns the offset of a tile in the front buffer.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @return the tile offset, or -1 if the tile is outside the window
   */
  public int bufferIndex(int x, int y) {
    x -= frontOriX;
    y -= frontOriY;
    if (x < 0 || x >= sizeX || y < 0 || y >= sizeY) {
      return -1;
    }
    return (x + y * sizeX) * STRIDE;
  }

  /**
   * Returns the offset of a tile in the working window.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @return the tile offset, or -1 if the tile is outside the window
   */
  protected int backBufferIndex(int x, int y) {
    x -= oriX;
    y -= oriY;
    if (x < 0 || x >= sizeX || y < 0 || y >= sizeY) {
      return -1;
    }
    return (x + y * sizeX) * STRIDE;
  }

  /**
   * No-op: the engine owns no pool of its own (channel dispatch runs on the
   * common ForkJoinPool); subclasses close their own pools.
   */
  @Override
  public void close() {
  }
}
