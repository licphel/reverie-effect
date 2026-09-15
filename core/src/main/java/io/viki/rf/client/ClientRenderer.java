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

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.pass.RenderTargetDesc;
import io.viki.momentum.gfx.pipe.*;
import io.viki.momentum.gfx.shader.*;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.SamplerDesc;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureWrap;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.util.ZeroCopyVertexStore;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.input.Key;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.input.event.ResizeEvent;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Matrix4x4;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.resource.Resource;
import io.viki.momentum.util.Loop;
import io.viki.rf.Registries;
import io.viki.rf.client.render.LiquidRenderer;
import io.viki.rf.client.render.ObjectRenderer;
import io.viki.rf.client.render.PlayerRenderer;
import io.viki.rf.client.render.Postprocessor;
import io.viki.rf.client.render.SkyRenderer;
import io.viki.rf.client.render.TileRenderer;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.ui.TitleScreen;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.LightMapRenderer;
import io.viki.rf.world.physics.Collision;
import io.viki.rf.world.object.MultiBlockDefinition;
import io.viki.rf.world.object.MultiBlockPart;
import io.viki.rf.world.object.ObjectPartRef;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;
import io.viki.rf.world.util.Locatable;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Owns the client GPU graph and all world drawing passes. */
@SideOnly(dist = Dist.CLIENT)
final class ClientRenderer implements AutoCloseable {
  static final int FRAME_W = 1280;
  static final int FRAME_H = 720;
  private static final Color BLOCK_COLLISION_COLOR = new Color(1F, 0.45F, 0.1F);
  private static final Color ENTITY_COLLISION_COLOR = new Color(0.1F, 1F, 0.7F);

  private final Device device;
  private final DesktopView display;
  private final Level level;
  @Nullable private final TileRenderer tileRenderer;
  @Nullable private final LiquidRenderer liquidRenderer;
  @Nullable private final ObjectRenderer objectRenderer;
  private final PlayerRenderer playerRenderer;
  private final SkyRenderer skyRenderer;
  private final Camera2D frameCamera;
  private final RenderTarget colorRT;
  private final RenderTarget frontRT;
  private final RenderTarget sceneRT;
  private final RenderPass sceneAccum;
  private final LightMapRenderer lightMapRenderer;
  private final ResourceSetLayout composeLayout;
  private final Pipeline alphaPipeline;
  private final BufferObject composeUbo;
  private final Sampler composeSampler;
  private final ShaderProgram composeShader;
  private final BatchedGraphics graphics;
  private final TitleScreen titleScreen;
  private final Postprocessor postprocessor;
  private boolean showCollisionBoxes;
  private boolean collisionDebugKeyDown;

  private ClientRenderer(Device device, DesktopView display, Level level,
                          TileRenderer tileRenderer, @Nullable LiquidRenderer liquidRenderer,
                          @Nullable ObjectRenderer objectRenderer,
                          PlayerRenderer playerRenderer, SkyRenderer skyRenderer,
                          Camera2D frameCamera, RenderTarget colorRT, RenderTarget frontRT,
                          RenderTarget sceneRT, RenderPass sceneAccum,
                          LightMapRenderer lightMapRenderer, ResourceSetLayout composeLayout,
                          Pipeline alphaPipeline,
                          BufferObject composeUbo, Sampler composeSampler,
                          ShaderProgram composeShader, BatchedGraphics graphics,
                          TitleScreen titleScreen, Postprocessor postprocessor) {
    this.device = device;
    this.display = display;
    this.level = level;
    this.tileRenderer = tileRenderer;
    this.liquidRenderer = liquidRenderer;
    this.objectRenderer = objectRenderer;
    this.playerRenderer = playerRenderer;
    this.skyRenderer = skyRenderer;
    this.frameCamera = frameCamera;
    this.colorRT = colorRT;
    this.frontRT = frontRT;
    this.sceneRT = sceneRT;
    this.sceneAccum = sceneAccum;
    this.lightMapRenderer = lightMapRenderer;
    this.composeLayout = composeLayout;
    this.alphaPipeline = alphaPipeline;
    this.composeUbo = composeUbo;
    this.composeSampler = composeSampler;
    this.composeShader = composeShader;
    this.graphics = graphics;
    this.titleScreen = titleScreen;
    this.postprocessor = postprocessor;
  }

  static ClientRenderer open(Device device, DesktopView display, Level level,
                             Entity player, Camera2D camera) {
    TileRenderer tileRenderer = TileRenderer.create(device, Registries.BLOCKS);
    LiquidRenderer liquidRenderer = LiquidRenderer.create(device, Registries.LIQUIDS);
    ObjectRenderer objectRenderer = ObjectRenderer.create(device, Registries.OBJECTS);
    PlayerRenderer playerRenderer = PlayerRenderer.open(device);
    SkyRenderer skyRenderer = SkyRenderer.open(device);

    Camera2D frameCamera = new Camera2D(FRAME_W, FRAME_H, device.getTransformHandler());
    frameCamera.setOrthographic(FRAME_W, FRAME_H);
    frameCamera.setCenter(new Vector2(FRAME_W / 2F, FRAME_H / 2F));

    RenderTarget colorRT = device.getRenderTarget(RenderTargetDesc.offscreen(FRAME_W, FRAME_H));
    RenderTarget frontRT = device.getRenderTarget(RenderTargetDesc.offscreen(FRAME_W, FRAME_H));
    RenderTarget sceneRT = device.getRenderTarget(RenderTargetDesc.offscreen(FRAME_W, FRAME_H));
    RenderPass sceneAccum = new RenderPass.Builder().target(sceneRT).clearMask(0).build();
    LightMapRenderer lightMapRenderer = new LightMapRenderer();
    lightMapRenderer.init(device);

    VertexLayout composeVertexLayout = VertexLayout.bake(
        new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false),
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false));
    ResourceSetLayout composeLayout = ResourceSetLayout.bake(
        new Slot(0, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_albedo", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE),
        new Slot(2, "u_lightmap", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE));
    Resource resource = Resource.classpath(ClientRenderer.class);
    ShaderProgram composeShader = ShaderProgram.load(device,
        resource.readString("/shaders/light_compose.vert.glsl"),
        resource.readString("/shaders/light_compose.frag.glsl"),
        ShaderLanguage.GLSL);
    Pipeline alphaPipeline = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED)
        .rasterization(RasterizationDesc.DEFAULT)
        .shaderProgram(composeShader).vertexLayout(composeVertexLayout)
        .resourceLayouts(composeLayout).build());
    BufferObject composeUbo = device.getBuffer(BufferObjectDesc.uniform());
    composeUbo.allocate(160, null);
    Sampler composeSampler = device.getSampler(new SamplerDesc.Builder()
        .wrapX(TextureWrap.CLAMP_TO_EDGE).wrapY(TextureWrap.CLAMP_TO_EDGE).build());

    BatchedGraphics graphics = new BatchedGraphics(new ZeroCopyVertexStore(true), device);
    TitleScreen titleScreen = TitleScreen.open(device);
    Postprocessor postprocessor = Postprocessor.open(device, graphics, FRAME_W, FRAME_H);
    ClientRenderer renderer = new ClientRenderer(device, display, level, tileRenderer,
        liquidRenderer, objectRenderer, playerRenderer, skyRenderer, frameCamera, colorRT,
        frontRT, sceneRT,
        sceneAccum, lightMapRenderer, composeLayout, alphaPipeline,
        composeUbo, composeSampler, composeShader,
        graphics, titleScreen, postprocessor);
    level.setUnloadListener(pos -> {
      if (renderer.tileRenderer != null) {
        renderer.tileRenderer.unloadChunk(pos);
      }
    });
    display.eventBus().register(ResizeEvent.class, (ctx, event) -> {
      if (event.width() > 0 && event.height() > 0) {
        renderer.postprocessor.resize(event.width(), event.height());
      }
    });
    return renderer;
  }

  boolean gameStarted() {
    return titleScreen.gameStarted();
  }

  TitleScreen.@Nullable StartMode startMode() {
    return titleScreen.startMode();
  }

  void updateTitle(float dt, InputSnapshot snapshot, Key mouseLeft,
                   int outputWidth, int outputHeight, float inputWidth, float inputHeight) {
    titleScreen.update(dt, snapshot, mouseLeft, outputWidth, outputHeight,
        inputWidth, inputHeight);
  }

  void setFullBright(boolean fullBright) {
    lightMapRenderer.fullBright = fullBright;
  }

  void render(Level level, Entity player, Camera2D camera,
              Vector2 previousCamera, Vector2 currentCamera, InputSnapshot snapshot,
              Key mouseLeft) {
    int outputWidth = display.getWidth();
    int outputHeight = display.getHeight();
    Camera2D outputCamera = new Camera2D(outputWidth, outputHeight,
        device.getTransformHandler());
    outputCamera.setFlipY(true);
    outputCamera.setOrthographic(outputWidth, outputHeight);
    outputCamera.setCenter(new Vector2(outputWidth / 2F, outputHeight / 2F));
    if (!titleScreen.gameStarted()) {
      titleScreen.render(graphics, outputCamera, outputWidth, outputHeight, mouseLeft.isDown());
      submitFrame();
      return;
    }
    updateCollisionDebug(snapshot);

    String error = composeShader.checkCompilationError();
    if (error != null) {
      throw new GraphicsException("Compose shader error:\n" + error);
    }
    Vector2 interpolatedCamera = new Vector2(
        Loop.lerp(previousCamera.x(), currentCamera.x(), Loop.partialTicks()),
        Loop.lerp(previousCamera.y(), currentCamera.y(), Loop.partialTicks()));
    camera.setCenter(interpolatedCamera);

    camera.setFlipY(false);
    Locatable skyPosition = new Locatable() {
      @Override
      public double getX() {
        return camera.center().x();
      }

      @Override
      public double getY() {
        return camera.center().y();
      }
    };
    graphics.begin(RenderPass.of(sceneRT, new Color(0.05F, 0.05F, 0.08F)));
    graphics.setCamera(frameCamera);
    lightMapRenderer.update(level.lightEngine());
    skyRenderer.renderBackground(graphics, level, FRAME_W, FRAME_H, skyPosition);
    graphics.end();

    graphics.begin(RenderPass.of(colorRT, new Color(0, 0, 0, 0)));
    graphics.setCamera(camera);
    if (tileRenderer != null) {
      tileRenderer.renderWalls(graphics, level, camera);
    }
    graphics.end();

    Camera2D composeCamera = new Camera2D(FRAME_W, FRAME_H, device.getTransformHandler());
    composeCamera.setFlipY(true);
    composeCamera.setOrthographic(FRAME_W, FRAME_H);
    composeCamera.setCenter(new Vector2(FRAME_W / 2F, FRAME_H / 2F));
    graphics.begin(sceneAccum);
    graphics.setCamera(composeCamera);
    Texture wallTexture = colorRT.pin();
    writeComposeUniforms(composeUbo, composeCamera.viewProjectionMatrix(),
        camera.viewProjectionMatrix().invert(), lightMapRenderer.packedOriginX(),
        lightMapRenderer.packedOriginY(), lightMapRenderer.packedSizeX(),
        lightMapRenderer.packedSizeY());
    ResourceSet wallResources = device.getResourceSet(composeLayout);
    wallResources.bindUniform(0, composeUbo, 160);
    if (wallTexture != null) {
      wallResources.bindTexture(1, wallTexture, composeSampler);
    }
    if (lightMapRenderer.wallLightTexture() != null) {
      wallResources.bindTexture(2, lightMapRenderer.wallLightTexture(),
          lightMapRenderer.lightSampler());
    }
    graphics.setPipeline(alphaPipeline, wallResources);
    graphics.setTint(Color.WHITE);
    graphics.drawTexture(wallTexture, 0, 0, FRAME_W, FRAME_H);
    graphics.setPipeline(null, null);
    graphics.end();

    graphics.begin(RenderPass.of(frontRT, new Color(0, 0, 0, 0)));
    graphics.setCamera(camera);
    if (objectRenderer != null) {
      objectRenderer.render(graphics, level, camera);
    }
    for (Entity entity : level.entities().all()) {
      if (entity.type() == io.viki.rf.world.entity.EntityType.PLAYER) {
        playerRenderer.render(graphics, entity);
      } else {
        graphics.setTint(new Color(1F, 0.8F, 0F));
        var position = entity.renderPosition();
        var bounds = entity.bounds();
        graphics.drawRectangle(position.xf(), position.yf(), bounds.width(), bounds.height());
      }
    }
    if (liquidRenderer != null) {
      liquidRenderer.render(graphics, level, camera);
    }
    if (tileRenderer != null) {
      tileRenderer.render(graphics, level, camera);
    }
    if (showCollisionBoxes) {
      renderCollisionDebug(level, player, camera);
    }
    graphics.end();

    graphics.begin(sceneAccum);
    graphics.setCamera(composeCamera);
    Texture frontTexture = frontRT.pin();
    writeComposeUniforms(composeUbo, composeCamera.viewProjectionMatrix(),
        camera.viewProjectionMatrix().invert(), lightMapRenderer.packedOriginX(),
        lightMapRenderer.packedOriginY(), lightMapRenderer.packedSizeX(),
        lightMapRenderer.packedSizeY());
    ResourceSet frontResources = device.getResourceSet(composeLayout);
    frontResources.bindUniform(0, composeUbo, 160);
    if (frontTexture != null) {
      frontResources.bindTexture(1, frontTexture, composeSampler);
    }
    if (lightMapRenderer.frontLightTexture() != null) {
      frontResources.bindTexture(2, lightMapRenderer.frontLightTexture(),
          lightMapRenderer.lightSampler());
    }
    graphics.setPipeline(alphaPipeline, frontResources);
    graphics.setTint(Color.WHITE);
    graphics.drawTexture(frontTexture, 0, 0, FRAME_W, FRAME_H);
    graphics.setPipeline(null, null);
    graphics.end();

    postprocessor.render(sceneRT, outputCamera);
    graphics.begin(RenderPass.NOT_CLEAR);
    graphics.setCamera(outputCamera);
    graphics.drawText(Literal.of("Pos=" + player.position()), 5, 5);
    if (showCollisionBoxes) {
      graphics.drawText(Literal.of("Collision boxes: ON (F3)"), 5, 25);
    }
    graphics.end();
    submitFrame();
  }

  private void updateCollisionDebug(InputSnapshot snapshot) {
    boolean down = snapshot.isDown(KeyCode.F3);
    if (down && !collisionDebugKeyDown) {
      showCollisionBoxes = !showCollisionBoxes;
    }
    collisionDebugKeyDown = down;
  }

  private void renderCollisionDebug(Level level, Entity player, Camera2D camera) {
    Rectangle visible = cameraBounds(camera);
    Set<Entity> rendered = Collections.newSetFromMap(new IdentityHashMap<>());
    graphics.setTint(ENTITY_COLLISION_COLOR);
    for (Entity entity : level.entities().all()) {
      drawEntityCollision(entity, visible, rendered);
    }
    drawEntityCollision(player, visible, rendered);

    graphics.setTint(BLOCK_COLLISION_COLOR);
    int minX = (int) Math.floor(visible.minX());
    int maxX = (int) Math.floor(visible.maxX());
    int minY = (int) Math.floor(visible.minY());
    int maxY = (int) Math.floor(visible.maxY());
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        Chunk chunk = level.getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
        if (chunk == null) {
          continue;
        }
        var state = chunk.getBlock(x, y);
        Collision clip = state.getVoxelShape(chunk.getBlockShape(x, y));
        drawBlockCollision(clip, x, y);
      }
    }

    for (Chunk chunk : level.loadedChunks()) {
      for (ObjectPartRef reference : chunk.objectParts()) {
        MultiBlockDefinition definition = level.objects().definition(reference);
        MultiBlockPart part = level.objects().part(reference);
        if (definition == null || part == null) {
          continue;
        }
        BlockPos position = definition.partPosition(reference.rootPosition(),
            reference.mirrorX(), reference.variant(), reference.partIndex());
        drawBlockCollision(part.collisionShape(), position.x(), position.y());
      }
    }
  }

  private void drawEntityCollision(Entity entity, Rectangle visible, Set<Entity> rendered) {
    if (rendered.add(entity) && entity.bounds().intersects(visible)) {
      var collision = entity.collision();
      var position = entity.position();
      graphics.drawPolyFrame(collision.translate(position.toVector2()));
    }
  }

  private void drawBlockCollision(Collision clip, int tileX, int tileY) {
    if (clip == Collision.EMPTY) {
      return;
    }
    graphics.drawPolyFrame(clip.shape().translate(tileX, tileY));
  }

  private void submitFrame() {
    device.submit(display::present);
    device.execute();
    GraphicsMetrics.next();
    device.pollEvents();
    display.setTitle("Reverie Effect | FPS: " + Loop.fps());
  }

  static Rectangle cameraBounds(Camera2D camera) {
    var center = camera.center();
    float width = camera.width() / camera.zoom();
    float height = camera.height() / camera.zoom();
    return Rectangle.ofCentral(center.x(), center.y(), width, height);
  }

  static Rectangle presentationViewport(int outputWidth, int outputHeight) {
    float scale = Math.max((float) outputWidth / FRAME_W, (float) outputHeight / FRAME_H);
    float width = FRAME_W * scale;
    float height = FRAME_H * scale;
    return Rectangle.of((outputWidth - width) * 0.5F, (outputHeight - height) * 0.5F,
        width, height);
  }

  private static void writeComposeUniforms(BufferObject ubo, Matrix4x4 viewProjection,
                                           Matrix4x4 inverseCamera, float originX, float originY,
                                           float sizeX, float sizeY) {
    ByteBuffer output = ByteBuffer.wrap(new byte[160]).order(ByteOrder.LITTLE_ENDIAN);
    putMat4(output, viewProjection);
    putMat4(output, inverseCamera);
    output.putFloat(originX).putFloat(originY).putFloat(sizeX).putFloat(sizeY);
    ubo.submit(output.flip());
  }

  private static void putMat4(ByteBuffer output, Matrix4x4 matrix) {
    output.putFloat(matrix.m00()).putFloat(matrix.m10()).putFloat(matrix.m20()).putFloat(matrix.m30());
    output.putFloat(matrix.m01()).putFloat(matrix.m11()).putFloat(matrix.m21()).putFloat(matrix.m31());
    output.putFloat(matrix.m02()).putFloat(matrix.m12()).putFloat(matrix.m22()).putFloat(matrix.m32());
    output.putFloat(matrix.m03()).putFloat(matrix.m13()).putFloat(matrix.m23()).putFloat(matrix.m33());
  }

  @Override
  public void close() {
    level.setUnloadListener(null);
    titleScreen.close();
    postprocessor.close();
    playerRenderer.close();
    skyRenderer.close();
    if (objectRenderer != null) {
      objectRenderer.close();
    }
    if (liquidRenderer != null) {
      liquidRenderer.close();
    }
    if (tileRenderer != null) {
      tileRenderer.close();
    }
    graphics.close();
    alphaPipeline.close();
    composeShader.close();
    lightMapRenderer.close();
    colorRT.close();
    frontRT.close();
    sceneRT.close();
    composeUbo.close();
    composeSampler.close();
  }
}
