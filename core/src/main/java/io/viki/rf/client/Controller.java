package io.viki.rf.client;

import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.input.InputModifiers;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.Vector2;
import io.viki.rf.Registries;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.Shape;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.entity.EntityType;
import io.viki.rf.world.fluid.FluidEngine;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.network.packet.WorldActionPacket;
import io.viki.rf.network.packet.SpawnEntityRequestPacket;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.rf.world.util.BlockPos;


/** Converts client input into gameplay intents. */
@SideOnly(dist = Dist.CLIENT)
public final class Controller {
  private static final float MIN_VIEW_WIDTH = 8F;
  private static final float MAX_VIEW_WIDTH = 400F;
  private static final float ZOOM_SPEED = 40F;
  private static final float SHAPE_CYCLE_INTERVAL = 0.12F;
  private static final float FAST_TIME_SCALE = 32F;
  private static final float THROWN_ITEM_SPEED = 25F;
  private static final float THROW_INTERVAL = 0.15F;
  private final DemoWorld world;
  private final ClientLevel level;
  private final Entity player;
  private final BlockState airState = GameClient.defaultState(Registries.AIR);
  private final BlockState colorfulState = GameClient.defaultState(Registries.COLORFUL);
  private float viewWidth = 32F;
  private float shapeCycle;
  private float throwCooldown;

  private Controller(DemoWorld world, InputSnapshot input) {
    this.world = world;
    level = world.level();
    player = world.player();
  }

  public static Controller create(DemoWorld world, InputSnapshot input) {
    return new Controller(world, input);
  }

  public void tick(float delta, InputSnapshot input, DesktopView display,
                   Camera2D camera, Vector2 cameraCenter, ClientRenderer renderer) {
    updateCamera(delta, input, camera, cameraCenter);
    controlPlayer(delta, input);
    BlockPos cursor = cursorBlock(input, display, camera);
    editWorld(delta, input, cursor);
    throwItem(delta, input, display, camera);
    world.updateInterest();
    renderer.setFullBright(input.isDown(KeyCode.F1));
    world.tick(delta);
    level.setTimeScale(input.isDown(KeyCode.LEFT_CONTROL) || input.isDown(KeyCode.RIGHT_CONTROL)
        ? FAST_TIME_SCALE : 1F);
  }

  private void updateCamera(float delta, InputSnapshot input,
                            Camera2D camera, Vector2 cameraCenter) {
    if (input.isDown(KeyCode.X)) {
      viewWidth = Math.min(MAX_VIEW_WIDTH, viewWidth + ZOOM_SPEED * delta);
    }
    if (input.isDown(KeyCode.Z)) {
      viewWidth = Math.max(MIN_VIEW_WIDTH, viewWidth - ZOOM_SPEED * delta);
    }
    camera.setOrthographic(viewWidth,
        viewWidth * ClientRenderer.FRAME_H / ClientRenderer.FRAME_W);
    camera.setCenter(cameraCenter);
  }

  private void controlPlayer(float delta, InputSnapshot input) {
    if (input.isDown(KeyCode.S)) {
      player.ignorePlatformTemporarily();
    }
    float velocityX = input.isDown(KeyCode.A) ? -PhysicsConstants.PLAYER_WALK_SPEED : 0F;
    if (input.isDown(KeyCode.D)) {
      velocityX = PhysicsConstants.PLAYER_WALK_SPEED;
    }
    boolean jump = input.isDown(KeyCode.W) || input.isDown(KeyCode.SPACE);
    if (jump && player.onGround()) {
      player.setVelocity(velocityX, PhysicsConstants.PLAYER_JUMP_SPEED);
      player.consumeJumpPress();
    } else {
      player.setVelocity(velocityX, player.velocity().y());
      if (jump) {
        player.liquidJump(true, delta);
      }
    }
  }

  private BlockPos cursorBlock(InputSnapshot input, DesktopView display, Camera2D camera) {
    var viewport = ClientRenderer.presentationViewport(display.getWidth(), display.getHeight());
    Vector2 position = camera.unproject(
        new Vector2((float) input.cursorX(), (float) input.cursorY()), viewport);
    return new BlockPos((int) Math.floor(position.x()), (int) Math.floor(position.y()));
  }

  private void editWorld(float delta, InputSnapshot input, BlockPos cursor) {
    boolean mouseLeft = input.isDown(KeyCode.MOUSE_LEFT);
    boolean mouseRight = input.isDown(KeyCode.MOUSE_RIGHT);
    boolean control = (input.mods() & InputModifiers.CONTROL) != 0;
    if (mouseLeft && control) {
      predict(WorldActionPacket.SET_WALL, cursor, airState.identity(), 0);
    } else if (mouseRight && control && level.getBlock(cursor).isEmpty()) {
      predict(WorldActionPacket.SET_WALL, cursor, colorfulState.identity(), 0);
    } else if (mouseLeft) {
      predict(WorldActionPacket.SET_BLOCK, cursor, airState.identity(), 0);
    } else if (mouseRight && level.getBlock(cursor).isEmpty()) {
      predict(WorldActionPacket.SET_BLOCK, cursor, colorfulState.identity(), 0);
    }
    if (input.isDown(KeyCode.R)) {
      shapeCycle -= delta;
      if (shapeCycle <= 0F) {
        shapeCycle = SHAPE_CYCLE_INTERVAL;
        cycleShape(cursor);
      }
    } else {
      shapeCycle = 0F;
    }
    if (input.isDown(KeyCode.LEFT_ALT)) {
      player.setPosition(cursor.toCenter());
    }
    if (input.isDown(KeyCode.F1)) {
      predict(WorldActionPacket.SET_BLOCK, cursor, airState.identity(), 0);
      predict(WorldActionPacket.SET_LIQUID, cursor, Liquids.WATER.registryIndex(), FluidEngine.FULL);
    }
    if (input.isDown(KeyCode.F2)) {
      predict(WorldActionPacket.SET_BLOCK, cursor, airState.identity(), 0);
      predict(WorldActionPacket.SET_LIQUID, cursor, Liquids.LAVA.registryIndex(), FluidEngine.FULL);
    }
  }

  private void cycleShape(BlockPos cursor) {
    if (level.getBlock(cursor).shape() == Shape.SOLID) {
      predict(WorldActionPacket.SET_SHAPE, cursor,
          TileShape.byId(level.getBlockShape(cursor.x(), cursor.y())).next().id(), 0);
    }
  }

  private void predict(byte action, BlockPos position, int value, int auxiliary) {
    var packet = new WorldActionPacket(action,
        position.x(), position.y(), value, auxiliary);
    switch (action) {
      case WorldActionPacket.SET_BLOCK -> level.setBlock(position,
          BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(value));
      case WorldActionPacket.SET_WALL -> level.setWall(position,
          BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(value));
      case WorldActionPacket.SET_SHAPE -> level.setBlockShape(position.x(), position.y(), (byte) value);
      case WorldActionPacket.SET_LIQUID -> level.setLiquid(position.x(), position.y(),
          Liquids.byId(value), auxiliary);
      default -> throw new IllegalArgumentException("Unsupported predicted action: " + action);
    }
    world.send(packet);
  }

  private void throwItem(float delta, InputSnapshot input, DesktopView display, Camera2D camera) {
    throwCooldown = Math.max(0F, throwCooldown - delta);
    if (!input.isDown(KeyCode.Q) || throwCooldown > 0F) {
      return;
    }
    var viewport = ClientRenderer.presentationViewport(display.getWidth(), display.getHeight());
    Vector2 target = camera.unproject(
        new Vector2((float) input.cursorX(), (float) input.cursorY()), viewport);
    float dx = target.x() - player.center().xf();
    float dy = target.y() - player.center().yf();
    float length = (float) Math.sqrt(dx * dx + dy * dy);
    if (length == 0F) {
      return;
    }
    world.send(new SpawnEntityRequestPacket(EntityType.THROWN_ITEM,
        dx / length * THROWN_ITEM_SPEED, dy / length * THROWN_ITEM_SPEED));
    throwCooldown = THROW_INTERVAL;
  }
}
