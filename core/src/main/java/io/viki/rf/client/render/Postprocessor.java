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
import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.pipe.Blend;
import io.viki.momentum.gfx.pipe.Depth;
import io.viki.momentum.gfx.pipe.Pipeline;
import io.viki.momentum.gfx.pipe.PipelineDesc;
import io.viki.momentum.gfx.pipe.RasterizationDesc;
import io.viki.momentum.gfx.shader.MatrixUtil;
import io.viki.momentum.gfx.shader.ResourceSet;
import io.viki.momentum.gfx.shader.ResourceSetLayout;
import io.viki.momentum.gfx.shader.ResourceType;
import io.viki.momentum.gfx.shader.ShaderLanguage;
import io.viki.momentum.gfx.shader.ShaderProgram;
import io.viki.momentum.gfx.shader.ShaderType;
import io.viki.momentum.gfx.shader.Slot;
import io.viki.momentum.gfx.shader.VertexLayout;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.texture.FragileTexture;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.SamplerDesc;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureFilter;
import io.viki.momentum.gfx.texture.TextureWrap;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.resource.Resource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Owns the game's fullscreen color-grade pass.
 *
 * <p>The complete scene, including the sky, is rendered into one off-screen
 * target before this pass grades it into the swapchain.
 *
 * <p>This class is not thread-safe. Create, render, resize, and close it on the
 * graphics thread.
 */
public final class Postprocessor implements AutoCloseable {
  /** Size of a std140 transform matrix followed by two {@code vec2} values. */
  private static final int TRANSFORM_BYTES = 4 * 4 * Float.BYTES;
  private static final int SIZED_UNIFORM_BYTES = TRANSFORM_BYTES + 4 * Float.BYTES;
  private static final String VERTEX_SHADER = "/shaders/post.vert.glsl";
  private static final String GRADE_SHADER = "/shaders/post_grade.frag.glsl";

  private final Device device;
  private final Graphics graphics;
  private final ShaderProgram shaderProgram;
  private final Pipeline pipeline;
  private final ResourceSet resourceSet;
  private final BufferObject uniformBuffer;
  private final Sampler sampler;
  private final ByteBuffer uniformData = ByteBuffer.allocate(SIZED_UNIFORM_BYTES)
      .order(ByteOrder.LITTLE_ENDIAN);

  private Postprocessor(Device device, Graphics graphics, ShaderProgram shaderProgram,
                        Pipeline pipeline, ResourceSet resourceSet,
                        BufferObject uniformBuffer, Sampler sampler) {
    this.device = device;
    this.graphics = graphics;
    this.shaderProgram = shaderProgram;
    this.pipeline = pipeline;
    this.resourceSet = resourceSet;
    this.uniformBuffer = uniformBuffer;
    this.sampler = sampler;
  }

  /**
   * Creates the presentation color-grade pass.
   *
   * @param device graphics device that owns the post-processing resources
   * @param graphics graphics context used to draw fullscreen stages
   * @param width initial output width in pixels
   * @param height initial output height in pixels
   * @return a ready-to-use postprocessor
   * @throws IllegalArgumentException if either dimension is not positive
   */
  public static Postprocessor open(Device device, Graphics graphics, int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Postprocessor size must be positive: " + width + "x" + height);
    }

    Resource resource = Resource.classpath(Postprocessor.class);
    String vertexSource = resource.readString(VERTEX_SHADER);
    VertexLayout vertexLayout = VertexLayout.XYZ_F32_RGBA_F32_UV_F32;
    ResourceSetLayout resourceLayout = ResourceSetLayout.bake(
        new Slot(0, "T", ShaderType.VERTEX_BIT | ShaderType.FRAGMENT_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_scene", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE));
    SamplerDesc linearClamp = new SamplerDesc.Builder()
        .magFilter(TextureFilter.LINEAR)
        .minFilter(TextureFilter.LINEAR)
        .wrapX(TextureWrap.CLAMP_TO_EDGE)
        .wrapY(TextureWrap.CLAMP_TO_EDGE)
        .build();

    ShaderProgram shaderProgram = ShaderProgram.load(
        device, vertexSource, resource.readString(GRADE_SHADER), ShaderLanguage.GLSL);
    Pipeline pipeline = null;
    ResourceSet resourceSet = null;
    BufferObject uniformBuffer = null;
    Sampler sampler = null;
    try {
      String shaderError = shaderProgram.checkCompilationError();
      if (shaderError != null) {
        throw new GraphicsException("Color-grade shader error:\n" + shaderError);
      }
      pipeline = device.getRenderPipeline(new PipelineDesc.Builder()
          .blend(Blend.DISABLED)
          .depth(Depth.DISABLED)
          .rasterization(RasterizationDesc.NOT_CULL)
          .shaderProgram(shaderProgram)
          .vertexLayout(vertexLayout)
          .resourceLayouts(resourceLayout)
          .build());
      resourceSet = device.getResourceSet(resourceLayout);
      uniformBuffer = device.getBuffer(BufferObjectDesc.uniform());
      uniformBuffer.allocate(SIZED_UNIFORM_BYTES, null);
      sampler = device.getSampler(linearClamp);
      return new Postprocessor(device, graphics, shaderProgram, pipeline,
          resourceSet, uniformBuffer, sampler);
    } catch (RuntimeException exception) {
      if (sampler != null) {
        sampler.close();
      }
      if (uniformBuffer != null) {
        uniformBuffer.close();
      }
      if (resourceSet != null) {
        resourceSet.close();
      }
      if (pipeline != null) {
        pipeline.close();
      }
      shaderProgram.close();
      throw exception;
    }
  }

  /**
   * Grades the complete scene into the swapchain.
   *
   * @param scene complete scene image, including the sky
   * @param outputCamera screen-space camera template
   * @throws GraphicsException if the scene has no backing texture
   */
  public void render(FragileTexture scene, Camera2D outputCamera) {
    Texture texture = scene.pin();
    if (texture == null) {
      throw new GraphicsException("Post-process scene has no backing texture");
    }

    int targetWidth = device.getSwapchain().width();
    int targetHeight = device.getSwapchain().height();
    if (targetWidth <= 0 || targetHeight <= 0) {
      targetWidth = Math.round(outputCamera.width());
      targetHeight = Math.round(outputCamera.height());
    }

    uniformData.clear();
    MatrixUtil.store(outputCamera.viewProjectionMatrix(), uniformData);
    uniformData.putFloat(texture.width())
        .putFloat(texture.height())
        .putFloat(targetWidth)
        .putFloat(targetHeight)
        .flip();
    uniformBuffer.submit(uniformData);
    resourceSet.bindUniform(0, uniformBuffer, SIZED_UNIFORM_BYTES);
    resourceSet.bindTexture(1, texture, sampler);

    graphics.begin(RenderPass.DEFAULT);
    graphics.setCamera(outputCamera);
    graphics.setPipeline(pipeline, resourceSet);
    graphics.setTint(Color.WHITE);
    graphics.drawTexture(texture, 0F, 0F, targetWidth, targetHeight);
    graphics.setPipeline(null, null);
    graphics.end();
  }

  /**
   * Validates an output-size change.
   *
   * @param width new output width in pixels
   * @param height new output height in pixels
   * @throws IllegalArgumentException if either dimension is not positive
   */
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Postprocessor size must be positive: " + width + "x" + height);
    }
  }

  /** Releases the color-grade GPU resources owned by this object. */
  @Override
  public void close() {
    resourceSet.close();
    uniformBuffer.close();
    sampler.close();
    pipeline.close();
    shaderProgram.close();
  }
}
