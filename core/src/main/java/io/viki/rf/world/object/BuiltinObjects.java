/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.rf.world.physics.Collision;
import io.viki.rf.world.util.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Built-in object definitions used by the demo and the initial world content. */
public final class BuiltinObjects {
  public static final String BIRCH_TEXTURE = "/object/tree/birch_wood.png";
  private static final int TREE_TEXTURE_WIDTH = 104;
  private static final int TREE_TEXTURE_HEIGHT = 96;
  private static final int TREE_CELL_PIXELS = 8;
  private static final int TREE_BRANCH_PIXELS = 32;
  private static final int TREE_TOP_PIXELS = 64;
  private static final int TREE_TOP_SOURCE_X = 40;
  private static final int DEFAULT_TREE_HEIGHT = 12;
  private static final int MIN_TREE_HEIGHT = 8;
  private static final int MAX_TREE_HEIGHT = 16;

  private BuiltinObjects() {
  }

  public static MultiBlockDefinition birchTree() {
    return new MultiBlockDefinition(BuiltinObjects::treeLayout, 0,
        List.of(new BlockPos(0, -1)), false,
        ObjectBreakPolicy.WHOLE_OBJECT,
        List.of(new ObjectPartRender(BIRCH_TEXTURE,
            Rectangle.of(0, 0, TREE_TEXTURE_WIDTH, TREE_TEXTURE_HEIGHT),
            Rectangle.of(0, 0, 1, 1), false)),
        BuiltinObjects::treeRender);
  }

  private static List<MultiBlockPart> treeLayout(int variant) {
    int height = treeHeight(variant);
    List<MultiBlockPart> parts = new ArrayList<>(height);
    for (int i = 0; i < height; i++) {
      parts.add(new MultiBlockPart(new BlockPos(0, i),
          Collision.EMPTY, List.of(), true,
          true, true, PhysicsConstants.STANDARD_FRICTION_COEFFICIENT));
    }
    return parts;
  }

  private static List<ObjectPartRender> treeRender(int variant, BlockPos rootPosition,
                                                   BlockPos partPosition, int partIndex,
                                                   MultiBlockPart part, long gameTicks) {
    int height = treeHeight(variant);
    int x = partPosition.x();
    int y = partPosition.y();
    int rootY = rootPosition.y();
    Vector2 pivot = new Vector2(rootPosition.x() + 0.5F - x, rootY - y);
    float angle = treeAngle(x, y, rootY, gameTicks);
    List<ObjectPartRender> renders = new ArrayList<>(3);

    boolean top = partIndex == height - 1 && !isTopCut(variant);
    if (top) {
      renders.add(new ObjectPartRender(BIRCH_TEXTURE,
          Rectangle.of(TREE_TOP_SOURCE_X, 0, TREE_TOP_PIXELS, TREE_TOP_PIXELS),
          Rectangle.of(-3.5F, 0.0F, 8.0F, 8.0F), false).rotated(pivot, angle));
    } else {
      renders.add(new ObjectPartRender(BIRCH_TEXTURE,
          Rectangle.of(0, Math.abs(y % 12) * TREE_CELL_PIXELS,
              TREE_CELL_PIXELS, TREE_CELL_PIXELS),
          Rectangle.of(0.0F, 0.0F, 1.0F, 1.0F), false).rotated(pivot, angle));

      if (y % 2 == 0 && y >= rootY + 2 && y <= rootY + height - 3) {
        long hash = placeHash(x, y);
        long branch = Math.abs(hash) % 4;
        int sourceY = Math.abs(y % 3) * TREE_BRANCH_PIXELS;
        if (branch == 0) {
          renders.add(new ObjectPartRender(BIRCH_TEXTURE,
              Rectangle.of(8, sourceY, TREE_BRANCH_PIXELS, TREE_BRANCH_PIXELS),
              Rectangle.of(-3.5F, 0.0F, 4.0F, 4.0F), false).rotated(pivot, angle));
        }
        if (branch == 2) {
          renders.add(new ObjectPartRender(BIRCH_TEXTURE,
              Rectangle.of(8, sourceY, TREE_BRANCH_PIXELS, TREE_BRANCH_PIXELS),
              Rectangle.of(0.5F, 0.0F, 4.0F, 4.0F), true).rotated(pivot, angle));
        }
      }
    }
    return renders;
  }

  private static int treeHeight(int variant) {
    int encoded = variant;
    int height = encoded == 0 ? DEFAULT_TREE_HEIGHT
        : encoded >= 128 ? encoded - 128 : encoded;
    if (height < MIN_TREE_HEIGHT || height > MAX_TREE_HEIGHT) {
      throw new IllegalArgumentException("Birch tree height must be in ["
          + MIN_TREE_HEIGHT + ", " + MAX_TREE_HEIGHT + "]: " + height);
    }

    return height;
  }

  private static boolean isTopCut(int variant) {
    return variant >= 128;
  }

  private static float treeAngle(int x, int y, int rootY, long gameTicks) {
    float phase = (float) (gameTicks * 2L + x * 5L - rootY);
    float degrees = (float) Math.sin(Math.toRadians(phase)) * (y - rootY) * 0.075F;
    return degrees * ((float) Math.PI / 180.0F);
  }

  private static long placeHash(int x, int y) {
    long hash = (long) x * 374761393L + (long) y * 668265263L;
    hash = (hash ^ (hash >> 15)) * 2246822519L;
    hash = (hash ^ (hash >> 13)) * 3266489917L;
    return hash ^ (hash >> 16);
  }
}
