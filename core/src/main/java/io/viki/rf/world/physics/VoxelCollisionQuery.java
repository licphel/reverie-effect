/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.physics;

import java.util.Comparator;
import java.util.List;

import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Poly;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.object.MultiBlockPart;
import io.viki.rf.world.object.ObjectPartRef;
import io.viki.rf.world.util.ChunkPos;

/** Converts loaded voxel cells into immutable world collider data. */
final class VoxelCollisionQuery {
  private static final Comparator<WorldCollider> NEAREST_FIRST =
      Comparator.comparingDouble(WorldCollider::sortDistanceSquared);

  private VoxelCollisionQuery() {
  }

  static void collect(Level level, Poly body, double bodyX, double bodyY, Vector2 movement,
      float padding, List<WorldCollider> output) {
    output.clear();
    Rectangle localBounds = body.bounds();
    double startMinX = localBounds.minX() + bodyX;
    double startMinY = localBounds.minY() + bodyY;
    double startMaxX = localBounds.maxX() + bodyX;
    double startMaxY = localBounds.maxY() + bodyY;
    double queryMinX = Math.min(startMinX, startMinX + movement.x()) - padding;
    double queryMinY = Math.min(startMinY, startMinY + movement.y()) - padding;
    double queryMaxX = Math.max(startMaxX, startMaxX + movement.x()) + padding;
    double queryMaxY = Math.max(startMaxY, startMaxY + movement.y()) + padding;
    double centerX = (startMinX + startMaxX) * 0.5;
    double centerY = (startMinY + startMaxY) * 0.5;

    int minimumTileX = (int) Math.floor(queryMinX);
    int minimumTileY = (int) Math.floor(queryMinY);
    int maximumTileX = (int) Math.floor(queryMaxX);
    int maximumTileY = (int) Math.floor(queryMaxY);
    for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
      for (int tileY = minimumTileY; tileY <= maximumTileY; tileY++) {
        Chunk chunk = level.getChunkByKey(ChunkPos.packBlockPosAsLong(tileX, tileY));
        if (chunk == null) {
          continue;
        }
        ObjectPartRef objectPart = chunk.getObjectPart(tileX, tileY);
        Collision clip;
        float friction;
        if (objectPart != null) {
          MultiBlockPart part = level.objects().part(objectPart);
          if (part == null) {
            // A missing definition is a storage problem, not empty space. Keep
            // the cell conservative until the object store can repair it.
            clip = Collision.CUBE;
            friction = PhysicsConstants.STANDARD_FRICTION_COEFFICIENT;
          } else {
            clip = part.collisionShape();
            friction = part.friction();
          }
        } else {
          BlockState state = chunk.getBlock(tileX, tileY);
          clip = state.getVoxelShape(chunk.getBlockShape(tileX, tileY));
          friction = state.friction();
        }
        Poly shape = clip.shape();
        if (shape.isEmpty() || !intersectsQuery(shape.bounds(), tileX, tileY,
            queryMinX, queryMinY, queryMaxX, queryMaxY)) {
          continue;
        }
        double colliderCenterX = (double) tileX + shape.centralX();
        double colliderCenterY = (double) tileY + shape.centralY();
        double distanceX = colliderCenterX - centerX;
        double distanceY = colliderCenterY - centerY;
        boolean initiallyOverlapping = overlaps(body, bodyX, bodyY, shape, tileX, tileY);
        output.add(new WorldCollider(shape, tileX, tileY, clip.isPlatform(), clip.canStepOn(),
            friction, distanceX * distanceX + distanceY * distanceY,
            initiallyOverlapping));
      }
    }
    output.sort(NEAREST_FIRST);
  }

  private static boolean intersectsQuery(Rectangle bounds, double offsetX, double offsetY,
      double queryMinX, double queryMinY, double queryMaxX, double queryMaxY) {
    return bounds.minX() + offsetX < queryMaxX
        && bounds.maxX() + offsetX > queryMinX
        && bounds.minY() + offsetY < queryMaxY
        && bounds.maxY() + offsetY > queryMinY;
  }

  private static boolean overlaps(Poly body, double bodyX, double bodyY,
      Poly collider, double colliderX, double colliderY) {
    for (int bodyPart = 0; bodyPart < body.convexCount(); bodyPart++) {
      for (int colliderPart = 0; colliderPart < collider.convexCount(); colliderPart++) {
        if (SatSolver.minimumTranslation(body, bodyPart, bodyX, bodyY,
            collider, colliderPart, colliderX, colliderY).intersects()) {
          return true;
        }
      }
    }
    return false;
  }
}
