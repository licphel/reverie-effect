#version 330
#ifdef GL_ARB_shading_language_420pack
#extension GL_ARB_shading_language_420pack : require
#endif

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aColor;
layout(location = 2) in vec2 aTexCoord;

layout(binding = 0, std140) uniform Transform {
    mat4 u_vp;
    mat4 u_invCamVP;
    vec2 u_lightOrigin;
    vec2 u_lightSize;
};

out vec4 vColor;
out vec2 vTexCoord;
out vec2 vLightUv;

void main() {
    vColor = aColor;
    vTexCoord = aTexCoord;
    // the quad is drawn in screen space; back out the world position so the
    // lightmap uv can be derived: screen pixel -> NDC (u_vp, the quad's own
    // view-projection) -> world (the inverse of the world camera)
    vec4 ndc = u_vp * vec4(aPos, 1.0);
    vec4 world = u_invCamVP * ndc;
    vLightUv = (world.xy - u_lightOrigin) / u_lightSize;
    gl_Position = ndc;
}
