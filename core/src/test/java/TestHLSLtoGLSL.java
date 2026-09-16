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

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.util.spvc.Spvc.*;

/**
 * Verifies that HLSL shaders can be compiled to GLSL at runtime
 * via shaderc (HLSL → SPIR-V) + SPIRV-Cross (SPIR-V → GLSL).
 */
public class TestHLSLtoGLSL {

  static final String COLOR_VERT_HLSL = """
      cbuffer T : register(b0) {
          float4 u_vp[4];
      };

      struct VSInput {
          [[vk::location(0)]] float3 pos   : POSITION;
          [[vk::location(1)]] float4 color : COLOR0;
      };

      struct VSOutput {
          [[vk::location(0)]] float4 color : COLOR0;
          float4 pos : SV_POSITION;
      };

      VSOutput main(VSInput input) {
          VSOutput output;
          float4 p = float4(input.pos, 1.0);
          output.color = input.color;
          output.pos = u_vp[0] * p.x + u_vp[1] * p.y + u_vp[2] * p.z + u_vp[3] * p.w;
          return output;
      }
      """;

  static final String COLOR_FRAG_HLSL = """
      struct PSInput {
          [[vk::location(0)]] float4 color : COLOR0;
      };

      struct PSOutput {
          [[vk::location(0)]] float4 f : SV_TARGET0;
      };

      PSOutput main(PSInput input) {
          PSOutput output;
          output.f = input.color;
          return output;
      }
      """;

  static final String PARTICLE_VERT_HLSL = """
      cbuffer T : register(b0) {
          float4 u_vp[4];
      };

      struct VSInput {
          [[vk::location(0)]] float2 quadPos : TEXCOORD0;
          [[vk::location(1)]] float2 quadUv  : TEXCOORD1;
          [[vk::location(2)]] float2 pos     : TEXCOORD2;
          [[vk::location(3)]] float2 sz      : TEXCOORD3;
          [[vk::location(4)]] float4 color   : TEXCOORD4;
          [[vk::location(5)]] float  rotation : TEXCOORD5;
          [[vk::location(6)]] float4 texUv   : TEXCOORD6;
      };

      struct VSOutput {
          [[vk::location(0)]] float4 color : COLOR0;
          [[vk::location(1)]] float2 uv    : TEXCOORD0;
          float4 pos : SV_POSITION;
      };

      VSOutput main(VSInput input) {
          VSOutput output;
          float2 centered = (input.quadPos - 0.5) * input.sz;
          float c, s;
          sincos(input.rotation, s, c);
          float2 rotated = float2(centered.x * c - centered.y * s,
                                   centered.x * s + centered.y * c);
          float4 world = float4(rotated + input.pos, 0.0, 1.0);
          output.color = input.color;
          output.uv = input.texUv.xy + input.quadUv * input.texUv.zw;
          output.pos = u_vp[0] * world.x + u_vp[1] * world.y + u_vp[2] * world.z + u_vp[3] * world.w;
          return output;
      }
      """;

  static final String PARTICLE_FRAG_HLSL = """
      Texture2D u_tex : register(t0);
      SamplerState u_tex_sampler : register(s0);

      struct PSInput {
          [[vk::location(0)]] float4 color : COLOR0;
          [[vk::location(1)]] float2 uv    : TEXCOORD0;
      };

      struct PSOutput {
          [[vk::location(0)]] float4 f : SV_TARGET0;
      };

      PSOutput main(PSInput input) {
          PSOutput output;
          output.f = input.color * u_tex.Sample(u_tex_sampler, input.uv);
          return output;
      }
      """;

  public static void main(String[] args) {
    System.out.println("=== HLSL -> SPIR-V -> GLSL Test ===\n");

    testRoundtrip("builtin_color.vert", COLOR_VERT_HLSL, shaderc_vertex_shader);
    testRoundtrip("builtin_color.frag", COLOR_FRAG_HLSL, shaderc_fragment_shader);
    testRoundtrip("particle.vert", PARTICLE_VERT_HLSL, shaderc_vertex_shader);
    testRoundtrip("particle.frag", PARTICLE_FRAG_HLSL, shaderc_fragment_shader);

    System.out.println("\nDone.");
  }

  static void testRoundtrip(String label, String hlslSource, int shaderKind) {
    System.out.println("--- " + label + " ---");

    long compiler = shaderc_compiler_initialize();
    if (compiler == 0) {
      System.out.println("  FAILED: shaderc_compiler_initialize returned 0");
      return;
    }

    // Try both Vulkan and OpenGL targets
    for (int env : new int[]{shaderc_target_env_opengl, shaderc_target_env_vulkan}) {
      String envName = env == shaderc_target_env_opengl ? "opengl" : "vulkan";
      long options = shaderc_compile_options_initialize();
      try {
        shaderc_compile_options_set_source_language(options, shaderc_source_language_hlsl);
        shaderc_compile_options_set_target_env(options, env,
            env == shaderc_target_env_opengl ? shaderc_env_version_opengl_4_5 : shaderc_env_version_vulkan_1_0);
        shaderc_compile_options_set_optimization_level(options,
            shaderc_optimization_level_performance);

        long result = shaderc_compile_into_spv(compiler, hlslSource, shaderKind,
            label + ".hlsl", "main", options);

        int status = shaderc_result_get_compilation_status(result);
        if (status != shaderc_compilation_status_success) {
          System.out.println("  [" + envName + "] shaderc FAILED: " + shaderc_result_get_error_message(result));
          shaderc_result_release(result);
          continue;
        }

        ByteBuffer spirv = shaderc_result_get_bytes(result);
        System.out.println("  [" + envName + "] SPIR-V: " + spirv.remaining() + " bytes");

        String glsl = spirvToGLSL(spirv);
        if (glsl != null) {
          System.out.println("  [" + envName + "] GLSL (" + glsl.lines().count() + " lines):");
          System.out.println(glsl.indent(4));
        } else {
          System.out.println("  [" + envName + "] spvc FAILED (null)");
        }
        shaderc_result_release(result);
      } finally {
        shaderc_compile_options_release(options);
      }
    }
    shaderc_compiler_release(compiler);
  }

  static String spirvToGLSL(ByteBuffer spirv) {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      PointerBuffer pointer = stack.mallocPointer(1);

      spvc_context_create(pointer);
      long context = pointer.get(0);

      // reinterpret as IntBuffer (SPIR-V is uint32, host endianness)
      spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining() / 4, pointer);
      long parsedIr = pointer.get(0);

      spvc_context_create_compiler(context, SPVC_BACKEND_GLSL, parsedIr,
          SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pointer);
      long compilerHandle = pointer.get(0);

      spvc_compiler_build_combined_image_samplers(compilerHandle);

      spvc_compiler_create_compiler_options(compilerHandle, pointer);
      long compilerOptions = pointer.get(0);
      spvc_compiler_options_set_uint(compilerOptions,
          SPVC_COMPILER_OPTION_GLSL_VERSION, 330);
      spvc_compiler_options_set_bool(compilerOptions,
          SPVC_COMPILER_OPTION_GLSL_ES, false);
      spvc_compiler_options_set_bool(compilerOptions,
          SPVC_COMPILER_OPTION_GLSL_ENABLE_420PACK_EXTENSION, true);
      spvc_compiler_options_set_bool(compilerOptions,
          SPVC_COMPILER_OPTION_GLSL_SEPARATE_SHADER_OBJECTS, true);
      spvc_compiler_install_compiler_options(compilerHandle, compilerOptions);

      spvc_compiler_compile(compilerHandle, pointer);
      long resultPtr = pointer.get(0);
      if (resultPtr == 0) {
        System.out.println("  spvc FAILED: null result");
        return null;
      }
      String glsl = MemoryUtil.memUTF8(resultPtr);

      spvc_context_destroy(context);
      return glsl;
    }
  }
}
