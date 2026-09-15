#version 330
#ifdef GL_ARB_shading_language_420pack
#extension GL_ARB_shading_language_420pack : require
#endif

in vec4 vColor;
in vec2 vTexCoord;

out vec4 fragColor;

// The composed low-res frame is sampled with NEAREST filtering. Keeping the
// source pixels discrete makes the CRT treatment fit the game's pixel art.
layout(binding = 1) uniform sampler2D u_scene;

// Mathematical constants used by periodic CRT masks.
const float PI = 3.14159265359;
const float TWO_PI = 2.0 * PI;

// Screen geometry. Zero keeps the image flat; a small positive value bends
// the image progressively more strongly toward the corners.
const float CURVATURE = 0.0;

// Width of the soft boundary transition, measured in normalized UV units.
// It produces no visible border while the image remains inside the screen.
const float SCREEN_EDGE_FADE_WIDTH = 0.0;

// Chromatic separation in normalized UV units. The base offset affects the
// whole screen; the radial offset adds separation toward the outer screen.
const float CHROMA_BASE_OFFSET = 0.0;
const float CHROMA_RADIAL_OFFSET = 0.0;

// Scanline controls in physical output pixels. One row per period is dark;
// increasing the period leaves more fully lit rows between scanlines.
const float SCANLINE_PERIOD = 3.0;
const float SCANLINE_DARK_ROWS = 1.0;
const float SCANLINE_DARK_FACTOR = 0.95;

// Repeating phosphor grille controls in physical output pixels.
const float GRILLE_PERIOD = 4.0;
const float GRILLE_DARK_FACTOR = 0.93;

// Squared normalized-radius thresholds and color grading for the vignette.
// Warmth blends in the tint only at the edge, leaving the center neutral.
const float VIGNETTE_START_RADIUS_SQUARED = 0.25;
const float VIGNETTE_END_RADIUS_SQUARED = 1.20;
const float VIGNETTE_DARKENING = 0.14;
const float VIGNETTE_WARMTH = 0.24;
const vec3 VIGNETTE_TINT = vec3(1.0, 0.985, 0.90);

// Static analogue grain brightness range. Both values remain near one to
// preserve the average exposure of the scene.
const float GRAIN_MIN_BRIGHTNESS = 0.99;
const float GRAIN_MAX_BRIGHTNESS = 1.01;

// Deterministic hash coefficients used to synthesize grain without a texture
// or time-dependent uniform.
const vec2 GRAIN_HASH_DIRECTION = vec2(12.9898, 78.233);
const float GRAIN_HASH_SCALE = 43758.5453;

// A deterministic per-fragment grain keeps the image from looking perfectly
// flat without requiring an additional noise texture or a time uniform.
float hash(vec2 p) {
    return fract(sin(dot(p, GRAIN_HASH_DIRECTION)) * GRAIN_HASH_SCALE);
}

void main() {
    // Bend the image away from the viewer at the edges of the virtual tube.
    vec2 centered = vTexCoord * 2.0 - 1.0;
    float radius = dot(centered, centered);
    vec2 warped = centered * (1.0 + CURVATURE * radius);
    vec2 uv = warped * 0.5 + 0.5;

    // Fade only the narrow overscan area to black instead of leaving a large
    // border around the playable image.
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float screenMask = smoothstep(0.0, SCREEN_EDGE_FADE_WIDTH, edge);
    vec2 clampedUv = clamp(uv, vec2(0.0), vec2(1.0));

    // Separate the red and blue phosphors slightly more near the screen edge.
    // The center channel remains anchored to avoid a generally blurry image.
    vec2 chromaOffset = centered
        * (CHROMA_BASE_OFFSET + CHROMA_RADIAL_OFFSET * radius);
    vec4 centerSample = texture(u_scene, clampedUv);
    float red = texture(u_scene, clamp(clampedUv + chromaOffset,
        vec2(0.0), vec2(1.0))).r;
    float blue = texture(u_scene, clamp(clampedUv - chromaOffset,
        vec2(0.0), vec2(1.0))).b;
    vec3 color = vec3(red, centerSample.g, blue);

    // Alternate physical output rows so scanlines remain visible even when
    // the source texture is enlarged with nearest-neighbour sampling.
    float scanlineRow = mod(floor(gl_FragCoord.y), SCANLINE_PERIOD);
    float scanline = mix(SCANLINE_DARK_FACTOR, 1.0,
        step(SCANLINE_DARK_ROWS, scanlineRow));

    // A very subtle three-column phosphor grille completes the CRT texture.
    float grille = mix(GRILLE_DARK_FACTOR, 1.0,
        0.5 + 0.5 * sin(gl_FragCoord.x * (TWO_PI / GRILLE_PERIOD)));

    // Grade the outer screen toward a subtly darker, warmer phosphor tone.
    // Tint and brightness share the same radial mask so the center remains
    // color-neutral and fully bright.
    float vignetteAmount = smoothstep(
        VIGNETTE_START_RADIUS_SQUARED, VIGNETTE_END_RADIUS_SQUARED, radius);
    float vignette = 1.0 - VIGNETTE_DARKENING * vignetteAmount;
    vec3 vignetteTint = mix(vec3(1.0), VIGNETTE_TINT,
        VIGNETTE_WARMTH * vignetteAmount);

    // Add restrained analogue grain after the edge grade.
    float grain = mix(
        GRAIN_MIN_BRIGHTNESS, GRAIN_MAX_BRIGHTNESS, hash(gl_FragCoord.xy));
    color *= vignetteTint * scanline * grille * vignette * grain * screenMask;

    fragColor = vec4(color * vColor.rgb, centerSample.a * vColor.a);
}
