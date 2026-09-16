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

<<<<<<<< HEAD:core/src/main/java/io/viki/rf/world/level/GeneratedEntity.java
package io.viki.rf.world.level;

import java.util.Map;
========
package io.viki.rf.render;

import io.viki.momentum.gfx.mesh.Mesh;
>>>>>>>> origin/main:core/src/main/java/io/viki/rf/render/ChunkMesh.java

/** A generated entity spawn; unlike tile writes its position is fractional. */
public record GeneratedEntity(double x, double y, int priority, String source,
                              long sequence, int flags, String typeId,
                              Map<String, String> data)
    implements GenerationStep {
  public GeneratedEntity {
    GenerationStep.check(priority, source, sequence, flags);
    if (typeId == null || typeId.isEmpty()) {
      throw new IllegalArgumentException("generated entity type must not be empty");
    }
    if (data == null) {
      throw new NullPointerException("generated entity data must not be null");
    }
    data = Map.copyOf(data);
  }

  @Override
  public Kind kind() {
    return Kind.ENTITY;
  }

  @Override
  public void generate(ChunkMap map) {
    map.spawnEntity(x, y, typeId, data, flags);
  }
}
