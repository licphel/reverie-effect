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

package io.viki.rf.world.inventory;

import io.viki.rf.world.item.Item;
import io.viki.rf.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A furnace-like machine: three slots (input / fuel / output), a three-tick
 * smelt cycle consuming 1 ore + 1 coal per tick, output emitted into the
 * output slot, and an automation pipeline pushing finished ingots into a
 * chest. Exercises the inventory API the way real machines do.
 */
class FurnaceTest {
  private final Item ore = new Item();
  private final Item coal = new Item();
  private final Item ingot = new Item();

  private static final class Furnace {
    static final int INPUT = 0;
    static final int FUEL = 1;
    static final int OUTPUT = 2;
    static final int SMELT_TICKS = 3;

    final SimpleContainer<ItemStack> inv;
    /** Every slot change, in order (driven by per-slot change listeners). */
    final List<Integer> changes = new ArrayList<>();
    private final Item ore;
    private final Item coal;
    private int progress;

    Furnace(Item ore, Item coal) {
      this.ore = ore;
      this.coal = coal;
      inv = new SimpleContainer<>(Item.CATEGORY, List.of(
          new SimpleSlot<ItemStack>(Item.CATEGORY).withValidator(s -> s.type() == ore),
          new SimpleSlot<ItemStack>(Item.CATEGORY).withValidator(s -> s.type() == coal),
          new SimpleSlot<>(Item.CATEGORY)));
      inv.slotAt(INPUT).addChangeListener(() -> changes.add(INPUT));
      inv.slotAt(FUEL).addChangeListener(() -> changes.add(FUEL));
      inv.slotAt(OUTPUT).addChangeListener(() -> changes.add(OUTPUT));
    }

    /** One machine tick: burn 1 ore + 1 coal, and emit an ingot every 3 ticks. */
    void tick(Item ingot) {
      if (progress < SMELT_TICKS
          && inv.contains(s -> s.type() == ore)
          && inv.contains(s -> s.type() == coal)) {
        inv.extract(ItemStack.of(ore, 1));
        inv.extract(ItemStack.of(coal, 1));
        progress++;
      } else {
        progress = 0; // input or fuel ran out: the smelt cannot continue
      }
      if (progress >= SMELT_TICKS) {
        inv.insert(ItemStack.of(ingot, 1));
        progress = 0;
      }
    }
  }

  // -- smelting -----------------------------------------------------------

  @Test
  void smeltsOreIntoIngots() {
    Furnace f = new Furnace(ore, coal);
    f.inv.set(Furnace.INPUT, ItemStack.of(ore, 9));
    f.inv.set(Furnace.FUEL, ItemStack.of(coal, 9));
    for (int i = 0; i < 9; i++) { // 3 smelt cycles of 3 ticks
      f.tick(ingot);
    }
    assertEquals(3, f.inv.get(Furnace.OUTPUT).count());
    assertTrue(f.inv.get(Furnace.INPUT).isEmpty());
    assertTrue(f.inv.get(Furnace.FUEL).isEmpty());
  }

  @Test
  void doesNotSmeltWithoutFuel() {
    Furnace f = new Furnace(ore, coal);
    f.inv.set(Furnace.INPUT, ItemStack.of(ore, 5));
    for (int i = 0; i < 9; i++) {
      f.tick(ingot);
    }
    assertTrue(f.inv.get(Furnace.OUTPUT).isEmpty());
    assertEquals(5, f.inv.get(Furnace.INPUT).count());
  }

  @Test
  void stopsWhenInputRunsOut() {
    Furnace f = new Furnace(ore, coal);
    f.inv.set(Furnace.INPUT, ItemStack.of(ore, 1));
    f.inv.set(Furnace.FUEL, ItemStack.of(coal, 5));
    for (int i = 0; i < 9; i++) {
      f.tick(ingot);
    }
    assertEquals(0, f.inv.get(Furnace.OUTPUT).count());
    assertTrue(f.inv.get(Furnace.INPUT).isEmpty());
    assertEquals(4, f.inv.get(Furnace.FUEL).count()); // burned only 1 coal
  }

  @Test
  void inputSlotRejectsWrongItem() {
    Furnace f = new Furnace(ore, coal);
    // coal pushed into the input slot is refused (validator)
    assertEquals(2, f.inv.insert(ItemStack.of(coal, 2), StackOp.EXECUTE, i -> i == Furnace.INPUT).count());
    assertTrue(f.inv.get(Furnace.INPUT).isEmpty());
    // but it is accepted by the fuel slot
    assertTrue(f.inv.insert(ItemStack.of(coal, 2), StackOp.EXECUTE, i -> i == Furnace.FUEL).isEmpty());
    assertEquals(2, f.inv.get(Furnace.FUEL).count());
  }

  // -- automation -----------------------------------------------------------

  @Test
  void pushesOutputIntoChest() {
    Furnace f = new Furnace(ore, coal);
    SimpleContainer<ItemStack> chest = new SimpleContainer<>(Item.CATEGORY, 4);
    f.inv.set(Furnace.INPUT, ItemStack.of(ore, 9));
    f.inv.set(Furnace.FUEL, ItemStack.of(coal, 9));
    for (int i = 0; i < 9; i++) {
      f.tick(ingot);
      f.inv.transfer(chest, ItemStack.of(ingot, 1), 64); // sweep the output each tick
    }
    assertEquals(3, chest.get(0).count());
    assertTrue(f.inv.get(Furnace.OUTPUT).isEmpty());
  }

  @Test
  void chestSweepRespectsChestCapacity() {
    Furnace f = new Furnace(ore, coal);
    SimpleContainer<ItemStack> chest = new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<>(Item.CATEGORY, 2)));
    f.inv.set(Furnace.OUTPUT, ItemStack.of(ingot, 5));
    assertEquals(2, f.inv.transfer(chest, ItemStack.of(ingot, 1), 64));
    assertEquals(2, chest.get(0).count());
    assertEquals(3, f.inv.get(Furnace.OUTPUT).count());
    // the chest is full now; the next sweep must move nothing and keep the output
    assertEquals(0, f.inv.transfer(chest, ItemStack.of(ingot, 1), 64));
    assertEquals(3, f.inv.get(Furnace.OUTPUT).count());
  }

  // -- change notifications --------------------------------------------------

  @Test
  void changeNotificationsTrackEverySlotMutation() {
    Furnace f = new Furnace(ore, coal);
    f.inv.set(Furnace.INPUT, ItemStack.of(ore, 5)); // [0]
    f.inv.set(Furnace.FUEL, ItemStack.of(coal, 3)); // [0, 1]
    for (int i = 0; i < 3; i++) { // each tick burns ore + coal; the ingot is emitted on the third
      f.tick(ingot);
    }
    assertEquals(List.of(0, 1, 0, 1, 0, 1, 0, 1, 2), f.changes);
  }

  @Test
  void burnedInputsAndProductsMatchNotificationLog() {
    Furnace f = new Furnace(ore, coal);
    f.inv.set(Furnace.INPUT, ItemStack.of(ore, 5));
    f.inv.set(Furnace.FUEL, ItemStack.of(coal, 3));
    f.tick(ingot);
    // 1 ore + 1 coal burned → 2 notifications, no product yet
    assertEquals(List.of(0, 1, 0, 1), f.changes);
    assertEquals(4, f.inv.get(Furnace.INPUT).count());
    assertEquals(2, f.inv.get(Furnace.FUEL).count());
    assertTrue(f.inv.get(Furnace.OUTPUT).isEmpty());
  }
}
