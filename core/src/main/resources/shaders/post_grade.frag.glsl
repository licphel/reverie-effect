#version 330
#ifdef GL_ARB_shading_language_420pack
#extension GL_ARB_shading_language_420pack : require
#endif

in vec4 vColor;
in vec2 vTexCoord;

layout(binding = 0, std140) uniform Transform {
    mat4 u_vp;
    vec2 u_sourceSize;
    vec2 u_targetSize;
};
layout(binding = 1) uniform sampler2D u_scene;

layout(location = 0) out vec4 fragColor;

/** Rec. 709 luminance coefficients used by saturation and grain shaping. */
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
/** Mild saturation lift keeps material colors distinct after lighting. */
const float SATURATION = 1.045;
/** S-curve contrast around mid-gray. */
const float CONTRAST = 1.055;
/** Warm bias applied mostly near the edge of the frame. */
const vec3 EDGE_TINT = vec3(1.025, 1.006, 0.965);
/** Maximum edge darkening; kept subtle enough to avoid visible black borders. */
const float VIGNETTE_STRENGTH = 0.25;
/** Vignette exponent controls how close the effect remains to the corners. */
const float VIGNETTE_POWER = 1.65;
/** One display-code step, used as the scale for quantization dither. */
const float DISPLAY_CODE_STEP = 1.0 / 255.0;
/** Dither amplitude in display-code steps. */
const float DITHER_STRENGTH = 0.72;
/** Extra static grain in shadows, also measured in display-code steps. */
const float SHADOW_GRAIN_STRENGTH = 0.34;

float hash12(vec2 position) {
    vec3 p = fract(vec3(position.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec2 coverUv(vec2 uv, vec2 sourceSize) {
    float sourceAspect = sourceSize.x / sourceSize.y;
    float targetAspect = u_targetSize.x / u_targetSize.y;
    vec2 scale = vec2(1.0);
    if (targetAspect > sourceAspect) {
        scale.y = sourceAspect / targetAspect;
    } else {
        scale.x = targetAspect / sourceAspect;
    }
    return (uv - 0.5) * scale + 0.5;
}

void main() {
    vec4 sampled = texture(u_scene, coverUv(vTexCoord, u_sourceSize));
    vec3 color = sampled.rgb;

    float luminance = dot(color, LUMA);
    color = mix(vec3(luminance), color, SATURATION);
    color = (color - 0.5) * CONTRAST + 0.5;

    vec2 centered = vTexCoord * 2.0 - 1.0;
    float edge = pow(clamp(dot(centered, centered) * 0.5, 0.0, 1.0), VIGNETTE_POWER);
    color *= mix(vec3(1.0), EDGE_TINT, edge);
    color *= 1.0 - edge * VIGNETTE_STRENGTH;

    // Two independent hashes produce triangular, screen-pixel-locked noise.
    float triangular = hash12(gl_FragCoord.xy) - hash12(gl_FragCoord.xy + vec2(47.0, 113.0));
    float shadowWeight = 1.0 - smoothstep(0.08, 0.72, luminance);
    float noise = triangular * DISPLAY_CODE_STEP
        * (DITHER_STRENGTH + shadowWeight * SHADOW_GRAIN_STRENGTH);
    color += vec3(noise);

    fragColor = vec4(clamp(color, 0.0, 1.0), sampled.a) * vColor;
}
