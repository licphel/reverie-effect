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

package io.viki.rf.render.ambient;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.Locatable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resource-backed ambient sky renderer. Rendering and resource lifetime are
 * confined to the graphics thread that owns the device.
 */
@SideOnly(dist = Dist.CLIENT)
public final class SkyRenderer implements AutoCloseable {
  private final AmbientAssets assets;
  private final SkySupplier skySupplier;
  private final Sky atmosphere;
  private final Deque<Sky> skies = new ArrayDeque<>();
  private AmbientLightComposer lightComposer = AmbientLightComposer.direct();
  private boolean closed;

  private SkyRenderer(AmbientAssets assets, SkySupplier skySupplier) {
    this.assets = Objects.requireNonNull(assets, "assets");
    this.skySupplier = Objects.requireNonNull(skySupplier, "skySupplier");
    atmosphere = SkyLibrary.atmosphere(assets);
  }

  /** Opens the renderer with a level-specific supplier and forest fallback. */
  public static SkyRenderer open(Device device, SkySupplier levelSkySupplier) {
    Objects.requireNonNull(levelSkySupplier, "levelSkySupplier");
    AmbientAssets assets = AmbientAssets.open(device);
    try {
      return new SkyRenderer(assets,
          new DelegatingSkySupplier(levelSkySupplier, SkyLibrary.biomeSupplier(assets)));
    } catch (RuntimeException exception) {
      assets.close();
      throw exception;
    }
  }

  /** Opens the renderer and builds the level supplier after loading assets. */
  public static SkyRenderer open(Device device,
                                 Function<AmbientAssets, ? extends SkySupplier> factory) {
    Objects.requireNonNull(factory, "factory");
    AmbientAssets assets = AmbientAssets.open(device);
    try {
      SkySupplier supplier = Objects.requireNonNull(factory.apply(assets), "factory result");
      return new SkyRenderer(assets,
          new DelegatingSkySupplier(supplier, SkyLibrary.biomeSupplier(assets)));
    } catch (RuntimeException exception) {
      assets.close();
      throw exception;
    }
  }

  /** Opens the renderer with the default forest fallback. */
  public static SkyRenderer open(Device device) {
    return open(device, SkySupplier.none());
  }

  /** Opens the renderer with explicit level and biome suppliers. */
  public static SkyRenderer open(Device device, SkySupplier levelSkySupplier,
                                 SkySupplier biomeSkySupplier) {
    Objects.requireNonNull(levelSkySupplier, "levelSkySupplier");
    Objects.requireNonNull(biomeSkySupplier, "biomeSkySupplier");
    AmbientAssets assets = AmbientAssets.open(device);
    try {
      return new SkyRenderer(assets,
          new DelegatingSkySupplier(levelSkySupplier, biomeSkySupplier));
    } catch (RuntimeException exception) {
      assets.close();
      throw exception;
    }
  }

  /** Returns the loaded assets for constructing custom sky suppliers. */
  public AmbientAssets assets() {
    checkOpen();
    return assets;
  }

  /** Sets the compose path used by light-impact parallax layers. */
  public void setLightComposer(AmbientLightComposer lightComposer) {
    checkOpen();
    this.lightComposer = Objects.requireNonNull(lightComposer, "lightComposer");
  }

  /** Draws the complete ambient background. */
  public void render(BatchedGraphics graphics, Level level, float width, float height,
                     Locatable position) {
    renderBackground(graphics, level, width, height, position, null);
  }

  /** Draws the complete ambient background with an optional world viewport. */
  public void render(BatchedGraphics graphics, Level level, float width, float height,
                     Locatable position, @Nullable Rectangle worldViewport) {
    renderBackground(graphics, level, width, height, position, worldViewport);
  }

  /** Draws the non-emissive gradient, celestial sprites, clouds and parallax layers. */
  public void renderBackground(BatchedGraphics graphics, Level level,
                               float width, float height, Locatable position) {
    renderBackground(graphics, level, width, height, position, null);
  }

  /** Draws the background; the viewport is reserved for future layer culling. */
  public void renderBackground(BatchedGraphics graphics, Level level,
                               float width, float height, Locatable position,
                               @Nullable Rectangle worldViewport) {
    prepare(level, width, height, position);
    atmosphere.renderBase(graphics, level, position, width, height);
    for (Iterator<Sky> iterator = skies.descendingIterator(); iterator.hasNext();) {
      iterator.next().renderLayers(graphics, level, position, width, height, lightComposer);
    }
  }

  /** Compatibility hook; all ambient sprites are drawn in the background pass. */
  public void renderEmissive(BatchedGraphics graphics, Level level, float width,
                             float height, Locatable position) {
    checkOpen();
  }

  private Sky prepare(Level level, float width, float height, Locatable position) {
    checkOpen();
    if (width <= 0F || height <= 0F) {
      throw new IllegalArgumentException(
          "Ambient viewport must be positive: " + width + "x" + height);
    }
    Sky selected = skySupplier.get(Objects.requireNonNull(level, "level"),
        Objects.requireNonNull(position, "position"));
    if (selected == null) {
      throw new IllegalStateException("No sky supplied for level and position");
    }
    if (skies.peekFirst() != selected) {
      skies.removeFirstOccurrence(selected);
      skies.addFirst(selected);
    }
    atmosphere.tickBase(width, height);
    for (Iterator<Sky> iterator = skies.iterator(); iterator.hasNext();) {
      Sky sky = iterator.next();
      boolean active = sky == selected;
      sky.tick(active);
      if (!active && sky.fadedOut()) {
        iterator.remove();
      }
    }
    return selected;
  }

  private void checkOpen() {
    if (closed) {
      throw new IllegalStateException("Ambient sky renderer is closed");
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    skies.clear();
    assets.close();
  }
}
