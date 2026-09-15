# Reverie Effect

[![License](https://img.shields.io/badge/License-MIT-2563eb)](./LICENSE)

**Reverie Effect is a Starbound-style 2D sandbox game.** It features a procedurally
generated block-based world, fluid simulation, voxel clip physics, a day/night
light engine and a GPU-driven renderer, all built on top of the
[Momentum](https://github.com/licphel/momentum) engine.

---

## Repository layout

- **modules/momentum** — the Momentum engine, a git submodule consumed as a Gradle
  composite build (`includeBuild`). Excluded from the module scan.
- **modules/launcher** → `:launcher` — an independent module (git submodule)
  that spawns the game jar with tuned JVM arguments. Depends only on momentum,
  never on the game core. Published as `launcher.jar`.
- **core** → `:core` — the game core (world, fluid, light, physics, renderers,
  registries, `io.viki.momentum.Main` entry) and its test suite. Standard Gradle layout:
  sources under `core/src/main/java`, resources under `core/src/main/resources`,
  tests under `core/src/test/java`. Exposes the engine via `api` and builds the
  self-contained `Reverie Effect.jar`.
- **script** — build / run / one-click multi-platform publish scripts.
- **.github/workflows** — GitHub Actions CI (compile, lint, test).

## Quick Start

Prerequisites: JDK 21+.

```bash
git clone --recurse-submodules https://github.com/licphel/Reverie Effect.git
cd Reverie Effect
./script/build.sh        # compile + test
./script/run.sh          # launch the game locally (via :core:run)
```

## Building

```bash
./gradlew build          # compiles, runs JUnit tests (core + launcher)
```

## UI scaling

The Momentum UI layer uses logical coordinates. For a Minecraft-style UI scale,
the policy selects the largest integer scale that still keeps the configured
minimum logical canvas visible; resizing the framebuffer changes the scale in
steps instead of stretching widgets independently. Use `fixed` when the
logical canvas itself must remain exactly the same at every window size.

```java
UiResolution resolution = UiResolution.minecraft(320.0F, 240.0F);
try (UiContext ui = new UiContext(device, view, resolution)) {
  Button start = Button.create(120.0F, 90.0F, 160.0F, 32.0F);
  start.setLabel("Start");
  ui.addWidget(start);

  // Once per frame, after polling input and before presenting.
  ui.render(graphics, deltaSeconds);
}
```

`UiContext` maps GLFW/window cursor coordinates through the same viewport used
for rendering, including high-DPI framebuffer ratios and fixed-canvas
letterboxing. Widgets therefore continue to receive logical coordinates after a
resize.

## Publishing a release

`script/publish.sh` builds the game (`rf.jar`) and the launcher
(`launcher.jar`), each self-contained with every platform's LWJGL natives, and
writes a platform-ready zip under `build/dist/`:

```bash
./script/publish.sh
```

The launcher spawns `rf.jar` with tuned JVM arguments (override via a
`launcher.properties` next to the jars).

## License

MIT — see [LICENSE](./LICENSE).
