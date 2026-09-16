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

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.gfx.glfw.GlfwDesktopView;
import io.viki.momentum.gfx.opengl.OpenGLDevice;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.ui.*;
import io.viki.momentum.gfx.util.ZeroCopyVertexStore;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.Vector2;

/** Small manually launched window demo for observing the logical UI scale and button states. */
public final class UiDemo {
  private UiDemo() {
  }

  public static void main(String[] args) {
    DesktopView display = new GlfwDesktopView();
    Device device = null;
    BatchedGraphics graphics = null;
    Canvas canvas = null;
    try {
      display.setTitle("Momentum UI demo");
      display.setSize(new Vector2(960, 540));
      display.setVsync(true);
      display.initialize();

      device = new OpenGLDevice();
      device.load(display);
      graphics = new BatchedGraphics(new ZeroCopyVertexStore(), device);

      Look look = new Look();
      look.put(Button.IDLE_BACKGROUND, new Color(0.15F, 0.45F, 0.85F));
      look.put(Button.HOVERED_BACKGROUND, new Color(0.25F, 0.60F, 1.0F));
      look.put(Button.PRESSED_BACKGROUND, new Color(0.08F, 0.25F, 0.60F));
      look.put(Button.DISABLED_BACKGROUND, new Color(0.20F, 0.20F, 0.20F));
      look.put(Button.LABEL, Literal.of("Click me"));
      look.put(Button.LABEL_COLOR, Color.WHITE);

      PrimaryContext context = new PrimaryContext(display, device.getTransformHandler());
      look.put(Button.ACTIVATE_BINDING, Button.makeDefaultActivationKeyBinding(context.snapshot()));
      canvas = new Canvas(context, look);
      Button button = new Button(Rectangle.of(300, 190, 200, 70), look);
      button.setOnClick(() -> System.out.println("UI button clicked"));
      canvas.add(button);

      while (!display.shouldClose()) {
        display.pollEvents();
        graphics.begin(RenderPass.of(new Color(0.04F, 0.05F, 0.08F)));
        canvas.draw(graphics);

        graphics.end();
        device.execute();
        device.submit(display::present);
        device.execute();
      }
    } finally {
      if (canvas != null) {
        canvas.close();
      }
      if (graphics != null) {
        graphics.close();
      }
      if (device != null) {
        device.close();
      }
      display.close();
      GlfwDesktopView.terminate();
    }
  }
}
