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
package io.viki.rf.client.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.util.DrawFlags;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.math.Matrix3x2;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.object.MultiBlockDefinition;
import io.viki.rf.world.object.MultiBlockPart;
import io.viki.rf.world.object.ObjectPartRef;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;

/** Draws each loaded object Part from local data without loading its root chunk. */
@SideOnly(dist = Dist.CLIENT)
public final class ObjectRenderer implements AutoCloseable {
  private final ObjectTextureAtlas textures;

  private ObjectRenderer(ObjectTextureAtlas textures) {
    this.textures = textures;
  }

  public static @Nullable ObjectRenderer create(Device device,
                                                Registry<MultiBlockDefinition> definitions) {
    try {
      return new ObjectRenderer(ObjectTextureAtlas.open(device, definitions));
    } catch (RuntimeException exception) {
      return null;
    }
  }

  public void render(BatchedGraphics graphics, Level level, Camera2D camera) {
    var center = camera.center();
    float viewWidth = camera.width() / camera.zoom();
    float viewHeight = camera.height() / camera.zoom();
    int minimumChunkX = (int) Math.floor((center.x() - viewWidth * 0.5F) / ChunkPos.SIZE);
    int maximumChunkX = (int) Math.floor((center.x() + viewWidth * 0.5F) / ChunkPos.SIZE);
    int minimumChunkY = (int) Math.floor((center.y() - viewHeight * 0.5F) / ChunkPos.SIZE);
    int maximumChunkY = (int) Math.floor((center.y() + viewHeight * 0.5F) / ChunkPos.SIZE);

    graphics.setTint(io.viki.momentum.gfx.tint.Color.WHITE);
    for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
      for (int chunkY = minimumChunkY; chunkY <= maximumChunkY; chunkY++) {
        Chunk chunk = level.getChunk(new ChunkPos(chunkX, chunkY));
        if (chunk == null) {
          continue;
        }
        ArrayList<ObjectPartRef> references = new ArrayList<>(chunk.objectParts());
        references.sort(Comparator.comparingLong((ObjectPartRef reference) -> reference.objectId())
            .thenComparingInt(ObjectPartRef::partIndex));
        for (ObjectPartRef reference : references) {
          MultiBlockDefinition definition = level.objects().definition(reference);
          MultiBlockPart part = level.objects().part(reference);
          if (definition == null || part == null) {
            continue;
          }
          BlockPos partPosition = definition.partPosition(reference.rootPosition(),
              reference.mirrorX(), reference.variant(), reference.partIndex());
          drawPart(graphics, definition, reference, part, partPosition,
              level.getTicks());
        }
      }
    }
    graphics.setFlags(DrawFlags.NONE);
  }

  private void drawPart(BatchedGraphics graphics, MultiBlockDefinition definition,
                        ObjectPartRef reference, MultiBlockPart part,
                        BlockPos position, long gameTicks) {
    for (var render : definition.renderPieces(reference.variant(), reference.rootPosition(), position,
        reference.partIndex(), part, gameTicks)) {
      if (!render.visible()) {
        continue;
      }
      int oldFlags = graphics.flags();
      boolean flipX = render.flipX() ^ reference.mirrorX();
      graphics.setFlags(flipX ? oldFlags | DrawFlags.FLIP_X : oldFlags & ~DrawFlags.FLIP_X);
      graphics.transform().push(Matrix3x2.createRotation(render.rotationRadians(),
          new io.viki.momentum.math.Vector2(position.x() + render.rotationPivot().x(),
              position.y() + render.rotationPivot().y())).toMatrix4x4());
      try {
        TexturePart texture = textures.texture(render.texturePath());
        Rectangle destination = render.destination().translate(position.x(), position.y());
        graphics.drawTexture(texture, destination, render.source());
      } finally {
        graphics.transform().pop();
        graphics.setFlags(oldFlags);
      }
    }
  }

  @Override
  public void close() {
    textures.close();
  }
}
