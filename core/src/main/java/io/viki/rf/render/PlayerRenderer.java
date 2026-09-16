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

package io.viki.rf.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.resource.Resource;
import io.viki.momentum.util.Loop;
import io.viki.rf.render.quick2d.bone.Bone;
import io.viki.rf.render.quick2d.bone.RegionAttachment;
import io.viki.rf.render.quick2d.bone.Skeleton2D;
import io.viki.rf.render.quick2d.bone.SkeletonAnchor;
import io.viki.rf.render.quick2d.bone.SkeletonDefinition;
import io.viki.rf.render.quick2d.bone.SkeletonRenderer;
import io.viki.rf.render.quick2d.bone.SkeletonTransform;
import io.viki.rf.render.quick2d.bone.Transform2D;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.physics.PhysicsConstants;

import java.io.IOException;
import java.io.InputStream;

/** Renders the player as a rigid cutout skeleton. Not thread-safe. */
public final class PlayerRenderer implements AutoCloseable {
  private static final String SKIN_RESOURCE = "/player_skin_0.png";
  private static final String ROOT_BONE = "root";
  private static final String ARM_BACK_BONE = "arm.back";
  private static final String HAND_BACK_BONE = "hand.back";
  private static final String ARM_FRONT_BONE = "arm.front";
  private static final String HAND_FRONT_BONE = "hand.front";
  private static final String LEG_BACK_BONE = "leg.back";
  private static final String FOOT_BACK_BONE = "foot.back";
  private static final String LEG_FRONT_BONE = "leg.front";
  private static final String FOOT_FRONT_BONE = "foot.front";
  private static final String HEAD_BONE = "head";

  private static final float WALK_PHASE_SPEED = 10.0F;
  /** Small visual sink so the feet do not appear to hover above terrain. */
  private static final float PLAYER_RENDER_SINK = 0.125F;
  private static final Vector2 PLAYER_RENDER_OFFSET =
      new Vector2(0.0F, -PLAYER_RENDER_SINK);
  private static final float LIMB_INITIAL_ANGLE = -(float) (Math.PI * 0.5);
  private static final float LEG_SWING = 0.55F;
  private static final float ARM_SWING = 0.33F;
  private static final float FOOT_SWING = 0.18F;

  private final Texture skin;
  private final Skeleton2D skeleton;
  private final Bone handBack;
  private final Bone handFront;
  private final Bone armBack;
  private final Bone armFront;
  private final Bone legBack;
  private final Bone legFront;
  private final Bone footBack;
  private final Bone footFront;

  private PlayerRenderer(Texture skin) {
    this.skin = skin;

    TexturePart body = new TexturePart(this.skin, Rectangle.of(0, 32, 16, 16));
    TexturePart handFrontSkin = new TexturePart(this.skin, Rectangle.of(22, 31, 7, 5));
    TexturePart handBackSkin = new TexturePart(this.skin, Rectangle.of(22, 37, 7, 5));
    TexturePart armFrontSkin = new TexturePart(this.skin, Rectangle.of(16, 31, 5, 5));
    TexturePart armBackSkin = new TexturePart(this.skin, Rectangle.of(16, 37, 5, 5));
    TexturePart footSkin = new TexturePart(this.skin, Rectangle.of(21, 24, 8, 5));
    TexturePart legSkin = new TexturePart(this.skin, Rectangle.of(16, 24, 3, 5));
    TexturePart face = new TexturePart(this.skin, Rectangle.of(4, 24, 8, 8));
    TexturePart hair = new TexturePart(this.skin, Rectangle.of(0, 0, 24, 24));

    SkeletonDefinition definition = SkeletonDefinition.builder()
        .anchor(SkeletonAnchor.FEET_CENTER)
        .root(ROOT_BONE)
        .bone(LEG_BACK_BONE, ROOT_BONE,
            Transform2D.at(0.875F, 1.15F, LIMB_INITIAL_ANGLE))
        .bone(FOOT_BACK_BONE, LEG_BACK_BONE, Transform2D.at(0.25F, 0.0F))
        .bone(LEG_FRONT_BONE, ROOT_BONE,
            Transform2D.at(1.0F, 1.15F, LIMB_INITIAL_ANGLE))
        .bone(FOOT_FRONT_BONE, LEG_FRONT_BONE, Transform2D.at(0.25F, 0.0F))
        .bone(ARM_BACK_BONE, ROOT_BONE,
            Transform2D.at(0.75F, 1.9125F, LIMB_INITIAL_ANGLE))
        .bone(HAND_BACK_BONE, ARM_BACK_BONE, Transform2D.at(0.25F, 0.0F))
        .bone(ARM_FRONT_BONE, ROOT_BONE,
            Transform2D.at(0.75F, 1.9125F, LIMB_INITIAL_ANGLE))
        .bone(HAND_FRONT_BONE, ARM_FRONT_BONE, Transform2D.at(0.25F, 0.0F))
        .bone(HEAD_BONE, ROOT_BONE, Transform2D.at(1.0F, 2.5F))
        .slot("leg.back", LEG_BACK_BONE,
            attachment(legSkin, 0.375F, 0.625F, 0.0F, 0.24F))
        .slot("foot.back", FOOT_BACK_BONE,
            attachment(footSkin, 1.0F, 0.5F, 0.0F, 0.3F))
        .slot("leg.front", LEG_FRONT_BONE,
            attachment(legSkin, 0.375F, 0.5F, 0.0F, 0.3F))
        .slot("foot.front", FOOT_FRONT_BONE,
            attachment(footSkin, 1.0F, 0.5F, 0.0F, 0.3F))
        .slot("arm.back", ARM_BACK_BONE,
            attachment(armBackSkin, 0.625F, 0.625F, 0.0F, 0.5F))
        .slot("hand.back", HAND_BACK_BONE,
            attachment(handBackSkin, 0.875F, 0.625F, 0.0F, 0.5F))
        .slot("body", ROOT_BONE,
            attachment(body, 2.0F, 2.0F, 0.0F, 0.0F))
        .slot("face", HEAD_BONE,
            attachment(face, 1.0F, 1.0F, 0.5F, 0.5F))
        .slot("hair", HEAD_BONE,
            attachment(hair, 3.0F, 3.0F, 0.5F, 0.5F))
        .slot("arm.front", ARM_FRONT_BONE,
            attachment(armFrontSkin, 0.625F, 0.625F, 0.0F, 0.5F))
        .slot("hand.front", HAND_FRONT_BONE,
            attachment(handFrontSkin, 0.875F, 0.625F, 0.0F, 0.5F))
        .build();

    skeleton = Skeleton2D.create(definition);
    handBack = skeleton.bone(HAND_BACK_BONE);
    handFront = skeleton.bone(HAND_FRONT_BONE);
    armBack = skeleton.bone(ARM_BACK_BONE);
    armFront = skeleton.bone(ARM_FRONT_BONE);
    legBack = skeleton.bone(LEG_BACK_BONE);
    legFront = skeleton.bone(LEG_FRONT_BONE);
    footBack = skeleton.bone(FOOT_BACK_BONE);
    footFront = skeleton.bone(FOOT_FRONT_BONE);
  }

  public static PlayerRenderer open(Device device) {
    try (Resource resources = Resource.classpath(PlayerRenderer.class);
         InputStream input = resources.open(SKIN_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Player skin resource not found: " + SKIN_RESOURCE);
      }
      PngInputStream png = new PngInputStream(input);
      return new PlayerRenderer(Texture.loadRGBA8(device, png.info()));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load player skin: " + SKIN_RESOURCE, e);
    }
  }

  public void render(Graphics graphics, Entity player) {
    float movement = Math.clamp(
        Math.abs(player.velocity().x()) / PhysicsConstants.PLAYER_WALK_SPEED, 0.0F, 1.0F);
    float phase = Loop.frameTime() * WALK_PHASE_SPEED;
    float swing = (float) Math.sin(phase) * movement;
    float footSwing = swing * FOOT_SWING;

    legFront.rotation(LIMB_INITIAL_ANGLE - swing * LEG_SWING);
    legBack.rotation(LIMB_INITIAL_ANGLE + swing * LEG_SWING);
    armFront.rotation(LIMB_INITIAL_ANGLE + swing * ARM_SWING);
    armBack.rotation(LIMB_INITIAL_ANGLE - swing * ARM_SWING);
    footFront.rotation(footSwing);
    footBack.rotation(-footSwing);
    handFront.rotation(footSwing);
    handBack.rotation(-footSwing);

    var position = player.renderPosition();
    Rectangle collisionBox = player.collisionBox();
    SkeletonTransform transform = SkeletonTransform.at(
        position.xf() + collisionBox.centralX(),
        position.yf() + collisionBox.minY(),
        player.facing().x() < 0.0F).withOffset(PLAYER_RENDER_OFFSET);

    Gradient oldTint = graphics.gradient();
    graphics.setTint(io.viki.momentum.gfx.tint.Color.WHITE);
    try {
      SkeletonRenderer.draw(graphics, skeleton, transform);
    } finally {
      graphics.setTint(oldTint);
    }
  }

  @Override
  public void close() {
    skin.close();
  }

  private static RegionAttachment attachment(
      TexturePart texture, float width, float height, float pivotX, float pivotY) {
    return RegionAttachment.builder(texture)
        .size(width, height)
        .pivot(pivotX, pivotY)
        .build();
  }
}
