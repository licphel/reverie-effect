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

package io.viki.rf;

import io.viki.rf.client.GameClient;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.fluid.Liquids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryArchitectureTest {
  @Test
  void bootstrapFreezesCentralRegistriesAndBakesLiquidStorageIds() {
    Registries.bootstrap();

    assertTrue(Registries.BLOCKS.isFrozen());
    assertTrue(Registries.ITEMS.isFrozen());
    assertTrue(Registries.LIQUIDS.isFrozen());
    assertEquals(4, Registries.LIQUIDS.size());
    assertSame(Liquids.EMPTY, Liquids.byId(0));
    assertSame(Liquids.WATER, Liquids.byId(1));
    assertSame(Liquids.LAVA, Liquids.byId(2));
    assertSame(Liquids.POISON, Liquids.byId(3));
    assertEquals(1, Byte.toUnsignedInt(Liquids.WATER.id()));
  }

  @Test
  void clientOnlyMarkersAreAvailableAtRuntime() throws NoSuchMethodException {
    assertEquals(Dist.CLIENT, GameClient.class.getAnnotation(SideOnly.class).dist());
    assertEquals(Dist.CLIENT,
        Main.class.getMethod("main", String[].class).getAnnotation(SideOnly.class).dist());
  }
}
