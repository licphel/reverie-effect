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
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;

import java.util.*;

/**
 * Light engine that computes light by flood-filling from light sources.
 *
 * <p>Every emitting tile (sky light, glowing blocks and liquids, entities) is collected
 * as a source, sorted brightest-first, and spread through the four neighbors with a
 * breadth-first wave; a tile already lit at or above the incoming intensity truncates
 * the wave, so overlapping sources stop quickly and a recompute after a small edit
 * costs only the affected area.
 *
 * <p>The window is recomputed only when the world, the sunlight, or the camera window
 * changes (see {@link LightEngine#tick(Rectangle)}); a static frame costs nothing.
 *
 * @see LightEngine
 */
@Deprecated // Always slower than Relaxation method.
public class BFSLightEngine extends LightEngine {
  /** Tolerance below which an incoming intensity counts as already covered. */
  private static final float BFS_EPS = 0.015F;
  /** Queue slack: a tile may re-enter once per brighter source in the worst case. */
  private static final int QUEUE_SLACK = 4;
  /** Ambient light sources gathered by the current compute; worker-thread only. */
  private final List<Source> sources = new ArrayList<>();
  /** Scratch space for the per-neighbor filtered light during spread. */
  private final float[] filtered = new float[3];
  /** BFS ring-buffer queue, sized to the window and reused across computes. */
  private int[] qx;
  private int[] qy;
  private float[] qr;
  private float[] qg;
  private float[] qb;

  /**
   * Creates a BFS light engine for the given level, with queues sized to the initial
   * window.
   *
   * @param level the level to light
   */
  public BFSLightEngine(Level level) {
    super(level);
    allocQueues();
  }

  /** Sizes the BFS queues to the current window, reallocating as needed. */
  private void allocQueues() {
    int cap = sizeX * sizeY * QUEUE_SLACK;
    qx = new int[cap];
    qy = new int[cap];
    qr = new float[cap];
    qg = new float[cap];
    qb = new float[cap];
  }

  /**
   * Collects a light value as a flood-fill source instead of writing the
   * working buffer: the base seed path ({@code tileAmbient} and
   * {@code drawInterpolated}) funnels every emission through here.
   */
  @Override
  public void draw(int x, int y, float r, float g, float b) {
    if (r > DARK_LUMINANCE || g > DARK_LUMINANCE || b > DARK_LUMINANCE) {
      sources.add(Source.pooled().set(x, y, r, g, b));
    }
  }

  @Override
  protected void onResized() {
    super.onResized(); // rebuild the beam layer for the new window
    allocQueues();
  }

  /**
   * Computes the next frame into the back buffer: seeds and collects light sources, then
   * flood-fills from every source into the raw RGB channels.
   *
   * @param cam the camera bounds to compute light for
   */
  @Override
  protected void calculate(Rectangle cam) {
    float margin = MAX_VALUE_GENERAL / UNIT - cam.width() / 128.0F;
    int x0 = (int) (cam.minX() - margin);
    int y0 = (int) (cam.minY() - margin);
    int x1 = (int) (cam.maxX() + margin);
    int y1 = (int) (cam.maxY() + margin);

    // guard: the window must contain the computation range
    if (x1 - x0 + 1 > sizeX || y1 - y0 + 1 > sizeY) {
      return; // window too small for this camera — next tick will resize
    }

    // the raw RGB channels are cleared so light of a stale pass never
    // lingers (the spread writes them, the seed only collects sources)
    Arrays.fill(back, 0F);

    cc.checkLoss(x0 - 1, y0 - 1, x1 + 1, y1 + 1);

    // Phase 1+2: seed — every emitting tile and entity becomes a source via
    // the overridden draw(); beams are drawn into the beam layer by the base
    sources.clear();

    for (int x = x0 - 1; x <= x1 + 1; x++) {
      for (int y = y0 - 1; y <= y1 + 1; y++) {
        seed(cc, x, y);
      }
    }

    for (Entity e : level.entities().all()) {
      seed(e);
    }

    // Phase 3: brightest first, so weaker sources truncate immediately
    sources.sort((a, b) -> Float.compare(b.luminance(), a.luminance()));

    // Phase 4: flood-fill from every source, truncating on already-lit tiles
    // (the queue arrays are window-sized, allocated on resize; the fill is
    // defensive — head/tail pointers already bound the read range; beams
    // were already drawn by the seed step, the sources are all ambient)
    Arrays.fill(qx, 0);
    Arrays.fill(qy, 0);
    Arrays.fill(qr, 0F);
    Arrays.fill(qg, 0F);
    Arrays.fill(qb, 0F);

    for (Source s : sources) {
      bfsSpread(s);
    }
    for (Source s : sources) {
      s.recycle();
    }

    sources.clear();

    // Beam light: max-blend into raw channels after spread (spread is
    // isotropic and would wash out the cone if the beam went through it)
    mergeBeam();
  }

  /**
   * Flood-fills from one source; a tile already lit at or above the incoming intensity
   * truncates the wave (filter + cutoff).
   */
  private void bfsSpread(Source src) {
    int cap = qx.length;
    int head = 0;
    int tail = 1;
    qx[0] = src.x;
    qy[0] = src.y;
    qr[0] = src.r;
    qg[0] = src.g;
    qb[0] = src.b;

    while (head < tail) {
      int x = qx[head];
      int y = qy[head];
      float cr = qr[head];
      float cg = qg[head];
      float cb = qb[head];
      head++;

      int o = backBufferIndex(x, y);
      if (o < 0) {
        continue;
      }
      // per-channel max write: a stronger light that arrived after this
      // entry must not be rolled back — but the wave keeps spreading either
      // way, the carried value is exactly this entry's light
      if (cr > back[o]) {
        back[o] = cr;
      }
      if (cg > back[o + 1]) {
        back[o + 1] = cg;
      }
      if (cb > back[o + 2]) {
        back[o + 2] = cb;
      }

      tail = spreadTo(x - 1, y, cr, cg, cb, tail);
      tail = spreadTo(x + 1, y, cr, cg, cb, tail);
      tail = spreadTo(x, y - 1, cr, cg, cb, tail);
      tail = spreadTo(x, y + 1, cr, cg, cb, tail);
    }
  }

  /**
   * Filters the incoming light through the neighbor's block and liquid, writing and
   * enqueueing it if it brightens the tile.
   *
   * @return the next queue tail
   */
  private int spreadTo(int nx, int ny, float pr, float pg, float pb, int tail) {
    int no = backBufferIndex(nx, ny);
    if (no < 0) {
      return tail;
    }

    float nr = filter(cc, Channel.RED, nx, ny, pr);
    float ng = filter(cc, Channel.GREEN, nx, ny, pg);
    float nb = filter(cc, Channel.BLUE, nx, ny, pb);
    if (nr <= DARK_LUMINANCE && ng <= DARK_LUMINANCE && nb <= DARK_LUMINANCE) {
      return tail;
    }
    // per-channel cutoff: each channel keeps its brighter value,
    // so overlapping sources compose by maximum instead of overwriting
    // each other's stronger channels
    boolean brighter = false;
    if (nr > back[no] + BFS_EPS) {
      back[no] = nr;
      brighter = true;
    }
    if (ng > back[no + 1] + BFS_EPS) {
      back[no + 1] = ng;
      brighter = true;
    }
    if (nb > back[no + 2] + BFS_EPS) {
      back[no + 2] = nb;
      brighter = true;
    }
    if (!brighter) {
      return tail;
    }
    if (tail >= qx.length) {
      return tail; // queue overflow: stop this wave, keep what we have
    }
    qx[tail] = nx;
    qy[tail] = ny;
    qr[tail] = nr;
    qg[tail] = ng;
    qb[tail] = nb;
    return tail + 1;
  }

  /** A mutable ambient light source at a tile position, pooled per thread. */
  private static final class Source {
    private static final ThreadLocal<Queue<Source>> POOL = ThreadLocal.withInitial(ArrayDeque::new);

    int x;
    int y;
    float r;
    float g;
    float b;

    private Source() {
    }

    static Source pooled() {
      Source src = POOL.get().poll();
      return src != null ? src : new Source();
    }

    void recycle() {
      POOL.get().offer(this);
    }

    Source set(int x, int y, float r, float g, float b) {
      this.x = x;
      this.y = y;
      this.r = r;
      this.g = g;
      this.b = b;
      return this;
    }

    float luminance() {
      return r + g + b;
    }
  }
}
