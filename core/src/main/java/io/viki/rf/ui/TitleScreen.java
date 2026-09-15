/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */

package io.viki.rf.ui;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.input.Key;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.gfx.io.PngInputStream;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.resource.Resource;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;

import java.io.IOException;
import java.io.InputStream;

/** Animated title menu. Not thread-safe; update and render it on the graphics thread. */
@SideOnly(dist = Dist.CLIENT)
public final class TitleScreen implements AutoCloseable {
  private static final String TITLE_RESOURCE = "/title.png";
  private static final String START_PROMPT = "按任意键开始";
  private static final String START_LABEL = "开始游戏";
  private static final String FOOTER_LABEL = "PROJECT VIKI.";
  private static final float REVEAL_DURATION = 0.65F;
  private static final float BUTTON_FADE_DURATION = 0.45F;
  private static final float TITLE_ASPECT = 448F / 78F;
  private static final float[] NODES = {
      -0.06F, 0.06F, -0.22F, 0.1F,   0.06F, 0.07F, 0.18F, 1.0F,
      0.15F, 0.17F, 0.42F, 2.1F,    0.19F, 0.29F, -0.12F, 2.8F,
      0.08F, 0.23F, 0.27F, 3.4F,    -0.04F, 0.15F, -0.35F, 4.3F,
      0.12F, -0.04F, 0.36F, 5.0F,

      0.47F, 0.08F, -0.18F, 0.7F,   0.50F, 0.17F, 0.24F, 1.8F,
      0.59F, 0.16F, 0.43F, 2.6F,    0.56F, 0.25F, -0.16F, 3.7F,

      0.83F, 0.08F, 0.28F, 0.3F,    0.91F, 0.12F, -0.25F, 1.5F,
      0.93F, 0.20F, 0.35F, 2.9F,    0.85F, 0.17F, 0.02F, 4.1F,

      0.12F, 0.69F, -0.28F, 0.6F,   0.22F, 0.66F, 0.33F, 1.9F,
      0.26F, 0.77F, -0.04F, 3.0F,   0.19F, 0.83F, 0.42F, 4.4F,

      0.62F, 0.65F, -0.40F, 0.2F,   0.72F, 0.58F, 0.12F, 0.9F,
      0.83F, 0.65F, 0.45F, 1.7F,    0.94F, 0.62F, -0.18F, 2.5F,
      1.06F, 0.71F, 0.24F, 3.3F,    0.57F, 0.80F, 0.34F, 4.1F,
      0.73F, 0.83F, -0.16F, 5.0F,   0.84F, 0.77F, 0.21F, 5.8F,
      0.96F, 0.82F, -0.38F, 0.5F,   1.08F, 0.91F, 0.41F, 1.4F,
      0.52F, 1.02F, -0.12F, 2.3F,   0.69F, 0.98F, 0.38F, 3.1F,
      0.83F, 1.05F, -0.32F, 4.0F,   0.98F, 1.01F, 0.17F, 5.2F
  };
  private static final int[] EDGES = {
      0, 1, 1, 2, 2, 3, 2, 4, 2, 5, 2, 6, 0, 5, 1, 4,
      7, 8, 8, 9, 9, 10,
      11, 12, 12, 13, 13, 14, 14, 11,
      15, 16, 16, 17, 17, 18, 18, 15,
      19, 20, 20, 21, 21, 22, 22, 23, 19, 24, 20, 24, 20, 25,
      21, 25, 21, 26, 22, 26, 23, 24, 24, 25, 25, 26, 26, 27,
      27, 28, 24, 29, 25, 29, 25, 30, 26, 30, 26, 31, 27, 31,
      28, 31, 29, 30, 30, 31
  };
  private static final Color BACKGROUND = Color.createHex("#25233F");
  private static final Color LINE_FAR = new Color(0.47F, 0.56F, 0.77F, 0.22F);
  private static final Color LINE_MID = new Color(0.57F, 0.69F, 0.91F, 0.42F);
  private static final Color LINE_NEAR = new Color(0.72F, 0.80F, 0.98F, 0.62F);
  private static final Color NODE_GLOW = new Color(0.61F, 0.72F, 1F, 0.13F);
  private static final Color NODE_DIM = new Color(0.68F, 0.74F, 0.91F, 0.62F);
  private static final Color NODE_BRIGHT = new Color(0.91F, 0.93F, 1F, 0.92F);
  private static final Color BUTTON_SHADOW = new Color(0.03F, 0.04F, 0.12F, 0.38F);
  private static final Color BUTTON_IDLE = new Color(0.16F, 0.18F, 0.34F, 0.88F);
  private static final Color BUTTON_HOVER = new Color(0.22F, 0.27F, 0.49F, 0.94F);
  private static final Color BUTTON_PRESSED = new Color(0.12F, 0.14F, 0.28F, 0.96F);
  private static final Color BUTTON_FRAME = new Color(0.76F, 0.82F, 1F, 0.82F);
  private static final Color BUTTON_ACCENT = new Color(0.93F, 0.67F, 0.52F, 0.95F);
  private static final Gradient BACKGROUND_GRADIENT = vertex -> vertex < 2
      ? Color.packF16LE(0.16F, 0.14F, 0.27F, 1F)
      : Color.packF16LE(0.12F, 0.18F, 0.34F, 1F);
  private static final Gradient TITLE_GRADIENT = vertex -> vertex == 0 || vertex == 3
      ? Color.packF16LE(0.95F, 0.72F, 0.58F, 1F)
      : Color.packF16LE(1F, 1F, 1F, 1F);

  private final Texture titleTexture;
  private final float[] projectedX = new float[NODES.length / 4];
  private final float[] projectedY = new float[NODES.length / 4];
  private final Literal prompt;
  private final Literal[] buttonLabels;
  private final Literal footer;
  private Phase phase = Phase.WAITING;
  private float elapsed;
  private float revealElapsed;
  private float readyElapsed;
  private int hoveredButton = -1;
  private @org.jspecify.annotations.Nullable StartMode startMode;

  private TitleScreen(Texture titleTexture) {
    this.titleTexture = titleTexture;
    prompt = Literal.of(START_PROMPT).with(TextFormat.of().size(21F)
        .tint(new Color(0.84F, 0.87F, 0.97F, 0.82F)));
    buttonLabels = new Literal[] {
        Literal.of(START_LABEL).with(TextFormat.of().size(22F).tint(Color.WHITE)),
        Literal.of("启动 LAN :12345").with(TextFormat.of().size(22F).tint(Color.WHITE)),
        Literal.of("加入 LAN :12345").with(TextFormat.of().size(22F).tint(Color.WHITE))
    };
    footer = Literal.of(FOOTER_LABEL).with(TextFormat.of().size(14F)
        .tint(new Color(0.69F, 0.73F, 0.88F, 0.72F)));
  }

  public static TitleScreen open(Device device) {
    try (Resource resources = Resource.classpath(TitleScreen.class);
         InputStream input = resources.open(TITLE_RESOURCE)) {
      if (input == null) {
        throw new IOException("Resource is missing: " + TITLE_RESOURCE);
      }
      return new TitleScreen(Texture.loadRGBA8(device, new PngInputStream(input).info()));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load title screen texture " + TITLE_RESOURCE, e);
    }
  }

  public void update(float delta, InputSnapshot snapshot, Key primaryButton,
                     int screenWidth, int screenHeight, float inputWidth, float inputHeight) {
    elapsed += delta;
    if (phase == Phase.WAITING && (anyKeyboardPressed(snapshot) || primaryButton.transitioned())) {
      phase = Phase.REVEALING;
    }
    if (phase == Phase.REVEALING) {
      revealElapsed = Math.min(REVEAL_DURATION, revealElapsed + delta);
      if (revealElapsed >= REVEAL_DURATION) {
        phase = Phase.READY;
      }
    } else if (phase == Phase.READY) {
      readyElapsed = Math.min(BUTTON_FADE_DURATION, readyElapsed + delta);
    }

    float safeInputWidth = Math.max(1F, inputWidth);
    float safeInputHeight = Math.max(1F, inputHeight);
    float cursorX = (float) snapshot.cursorX() * screenWidth / safeInputWidth;
    float cursorY = (float) snapshot.cursorY() * screenHeight / safeInputHeight;
    hoveredButton = phase == Phase.READY
        ? buttonAt(cursorX, cursorY, screenWidth, screenHeight) : -1;
    if (hoveredButton >= 0 && primaryButton.transitioned()) {
      startMode = StartMode.values()[hoveredButton];
    }
  }

  public boolean gameStarted() {
    return startMode != null;
  }

  public @org.jspecify.annotations.Nullable StartMode startMode() {
    return startMode;
  }

  public void render(BatchedGraphics graphics, Camera2D camera, int width, int height, boolean pressed) {
    graphics.begin(RenderPass.of(BACKGROUND));
    graphics.setCamera(camera);
    graphics.setTint(BACKGROUND_GRADIENT);
    graphics.drawRectangle(0F, 0F, width, height);

    renderBackdrop(graphics, width, height);
    renderNetwork(graphics, width, height);
    renderTitle(graphics, width, height);
    if (phase == Phase.WAITING) {
      renderPrompt(graphics, width, height);
    } else if (phase == Phase.READY) {
      renderButton(graphics, width, height, pressed);
    }
    graphics.setTint(Color.WHITE);
    graphics.drawText(footer, 18F, height - 26F);
    graphics.end();
  }

  private void renderBackdrop(BatchedGraphics graphics, int width, int height) {
    graphics.setTint(new Color(0.31F, 0.39F, 0.69F, 0.055F));
    graphics.drawTriangle(width * 0.58F, height, width, height * 0.36F, width, height);
    graphics.setTint(new Color(0.72F, 0.48F, 0.50F, 0.035F));
    graphics.drawTriangle(0F, 0F, width * 0.38F, 0F, 0F, height * 0.68F);
  }

  private void renderNetwork(BatchedGraphics graphics, int width, int height) {
    projectNodes(width, height);
    for (int i = 0; i < EDGES.length; i += 2) {
      int from = EDGES[i];
      int to = EDGES[i + 1];
      float depth = (NODES[from * 4 + 2] + NODES[to * 4 + 2]) * 0.5F;
      graphics.setTint(depth < -0.18F ? LINE_FAR : depth > 0.24F ? LINE_NEAR : LINE_MID);
      graphics.drawLine(projectedX[from], projectedY[from], projectedX[to], projectedY[to]);
    }

    for (int i = 0; i < projectedX.length; i++) {
      float pulse = 0.5F + 0.5F * (float) Math.sin(elapsed * 1.35F + NODES[i * 4 + 3]);
      float size = 2.2F + Math.max(0F, NODES[i * 4 + 2]) * 1.8F + pulse;
      graphics.setTint(NODE_GLOW);
      graphics.drawOval(projectedX[i] - size * 1.8F, projectedY[i] - size * 1.8F,
          size * 3.6F, size * 3.6F);
      graphics.setTint(pulse > 0.48F ? NODE_BRIGHT : NODE_DIM);
      graphics.drawRectangle(projectedX[i] - size * 0.5F, projectedY[i] - size * 0.5F, size, size);
    }
  }

  private void projectNodes(int width, int height) {
    float yaw = (float) Math.sin(elapsed * 0.17F) * 0.12F;
    float pitch = (float) Math.cos(elapsed * 0.13F) * 0.065F;
    float cosYaw = (float) Math.cos(yaw);
    float sinYaw = (float) Math.sin(yaw);
    float cosPitch = (float) Math.cos(pitch);
    float sinPitch = (float) Math.sin(pitch);
    for (int i = 0; i < projectedX.length; i++) {
      int offset = i * 4;
      float x = NODES[offset] - 0.5F;
      float y = NODES[offset + 1] - 0.5F;
      float z = NODES[offset + 2];
      float rotatedX = x * cosYaw - z * sinYaw;
      float yawZ = x * sinYaw + z * cosYaw;
      float rotatedY = y * cosPitch - yawZ * sinPitch;
      float rotatedZ = y * sinPitch + yawZ * cosPitch;
      float perspective = 1F / (1F + rotatedZ * 0.23F);
      float phaseOffset = NODES[offset + 3];
      float driftX = (float) Math.sin(elapsed * 0.31F + phaseOffset) * 0.0045F;
      float driftY = (float) Math.cos(elapsed * 0.27F + phaseOffset) * 0.0055F;
      projectedX[i] = (0.5F + rotatedX * perspective + driftX) * width;
      projectedY[i] = (0.5F + rotatedY * perspective + driftY) * height;
    }
  }

  private void renderTitle(BatchedGraphics graphics, int width, int height) {
    float progress = smoothStep(revealElapsed / REVEAL_DURATION);
    float titleWidth = Math.min(width - 48F, Math.min(width * 0.78F, 700F));
    float titleHeight = titleWidth / TITLE_ASPECT;
    float centerY = lerp(height * 0.46F, height * 0.28F, progress);
    graphics.setTint(TITLE_GRADIENT);
    graphics.drawTexture(titleTexture, (width - titleWidth) * 0.5F,
        centerY - titleHeight * 0.5F, titleWidth, titleHeight);
  }

  private void renderPrompt(BatchedGraphics graphics, int width, int height) {
    float pulse = 0.72F + (float) Math.sin(elapsed * 2.3F) * 0.28F;
    graphics.setTint(new Color(1F, 1F, 1F, pulse));
    graphics.drawText(prompt, width * 0.5F, height * 0.61F, Alignment.CENTRAL);
  }

  private void renderButton(BatchedGraphics graphics, int width, int height, boolean pressed) {
    float alpha = smoothStep(readyElapsed / BUTTON_FADE_DURATION);
    float buttonWidth = buttonWidth(width);
    float buttonHeight = buttonHeight(height);
    float x = (width - buttonWidth) * 0.5F;
    for (int index = 0; index < buttonLabels.length; index++) {
      float y = buttonY(height, index);
      boolean hovered = hoveredButton == index;
      graphics.setTint(new Color(BUTTON_SHADOW, alpha * BUTTON_SHADOW.alpha()));
      graphics.drawRectangle(x + 4F, y + 7F, buttonWidth, buttonHeight);
      Color fill = pressed && hovered ? BUTTON_PRESSED : hovered ? BUTTON_HOVER : BUTTON_IDLE;
      graphics.setTint(new Color(fill, alpha * fill.alpha()));
      graphics.drawRectangle(x, y, buttonWidth, buttonHeight);
      graphics.setTint(new Color(BUTTON_FRAME, alpha * BUTTON_FRAME.alpha()));
      graphics.drawRectangleFrame(x, y, buttonWidth, buttonHeight);
      graphics.setTint(new Color(BUTTON_ACCENT, alpha * BUTTON_ACCENT.alpha()));
      graphics.drawRectangle(x, y, 3F, buttonHeight);
      graphics.drawText(buttonLabels[index], width * 0.5F,
          y + buttonHeight * 0.5F, Alignment.CENTRAL);
    }
  }

  private int buttonAt(float x, float y, int width, int height) {
    float buttonWidth = buttonWidth(width);
    float left = (width - buttonWidth) * 0.5F;
    for (int index = 0; index < buttonLabels.length; index++) {
      float top = buttonY(height, index);
      if (x >= left && x <= left + buttonWidth
          && y >= top && y <= top + buttonHeight(height)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean anyKeyboardPressed(InputSnapshot snapshot) {
    for (KeyCode code : KeyCode.values()) {
      if (code.mouseId() < 0 && code != KeyCode.ESCAPE && snapshot.get(code) == KeyAction.PRESS) {
        return true;
      }
    }
    return false;
  }

  private static float buttonWidth(int width) {
    return Math.clamp(width * 0.25F, 228F, 324F);
  }

  private static float buttonHeight(int height) {
    return Math.clamp(height * 0.078F, 48F, 62F);
  }

  private static float buttonY(int height, int index) {
    return height * 0.48F + index * (buttonHeight(height) + 12F);
  }

  private static float smoothStep(float value) {
    float t = Math.clamp(value, 0F, 1F);
    return t * t * (3F - 2F * t);
  }

  private static float lerp(float from, float to, float progress) {
    return from + (to - from) * progress;
  }

  @Override
  public void close() {
    titleTexture.close();
  }

  private enum Phase {
    WAITING,
    REVEALING,
    READY
  }

  public enum StartMode {
    SINGLE_PLAYER,
    HOST_LAN,
    JOIN_LAN
  }
}
