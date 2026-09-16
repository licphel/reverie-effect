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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTest {
  private final Item item = new Item();

  // -- Stack ------------------------------------------------------------

  @Test
  void emptyStackSemantics() {
    ItemStack empty = Item.CATEGORY.emptyStack();
    assertTrue(empty.isEmpty());
    assertEquals(0, empty.count());
    assertNull(empty.type());
    assertTrue(ItemStack.of(item, 0).isEmpty());
  }

  @Test
  void growClampsToTypeLimit() {
    ItemStack s = ItemStack.of(item, 95);
    s.grow(10);
    assertEquals(Stackable.DEFAULT_LIMIT, s.count());
  }

  @Test
  void splitDetachesCount() {
    ItemStack s = ItemStack.of(item, 30);
    assertEquals(10, s.split(10).count());
    assertEquals(20, s.count());
    assertEquals(20, s.splitByMaxEffort(100).count());
    assertTrue(s.isEmpty());
  }

  @Test
  void copySharesTypeButNotData() {
    ItemStack a = ItemStack.of(item, 3);
    a.getOrCreateData().putInt("k", 7);
    ItemStack b = a.copy();
    assertEquals(a, b);
    b.getData().putInt("k", 8);
    assertNotEquals(a, b);
  }

  @Test
  void stackEquality() {
    assertEquals(Item.CATEGORY.emptyStack(), ItemStack.of(item, 0));
    assertNotEquals(ItemStack.of(item, 1), ItemStack.of(item, 2));
    assertNotEquals(ItemStack.of(item, 1), ItemStack.of(new Item(), 1));
  }

  // -- SimpleSlot -------------------------------------------------------

  @Test
  void insertFillsEmptySlot() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    assertTrue(slot.insert(ItemStack.of(item, 30), StackOp.EXECUTE).isEmpty());
    assertEquals(30, slot.get().count());
  }

  @Test
  void insertMergesUpToLimits() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    slot.set(ItemStack.of(item, 80));
    ItemStack rest = slot.insert(ItemStack.of(item, 30), StackOp.EXECUTE);
    assertEquals(10, rest.count());
    assertEquals(100, slot.get().count());
  }

  @Test
  void insertRefusesIncompatibleType() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    slot.set(ItemStack.of(item, 5));
    ItemStack other = ItemStack.of(new Item(), 5);
    assertEquals(other, slot.insert(other, StackOp.EXECUTE));
    assertEquals(5, slot.get().count());
  }

  @Test
  void insertOverTypeLimitClamps() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    ItemStack rest = slot.insert(ItemStack.of(item, 150), StackOp.EXECUTE);
    assertEquals(50, rest.count());
    assertEquals(100, slot.get().count());
  }

  @Test
  void insertSimulateDoesNotMutate() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    slot.set(ItemStack.of(item, 40));
    ItemStack rest = slot.insert(ItemStack.of(item, 30), StackOp.SIMULATE);
    assertEquals(0, rest.count());
    assertEquals(40, slot.get().count());
  }

  @Test
  void insertValidatorRejectedUnlessForced() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<ItemStack>(Item.CATEGORY).withValidator(s -> false);
    ItemStack incoming = ItemStack.of(item, 5);
    // the rejected remainder is a copy, never the caller's instance
    assertEquals(incoming, slot.insert(incoming, StackOp.EXECUTE));
    assertTrue(slot.isEmpty());
    assertTrue(slot.insert(ItemStack.of(item, 5), StackOp.FORCE_EXECUTE).isEmpty());
    assertEquals(5, slot.get().count());
  }

  @Test
  void insertDoesNotAliasCallerStack() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    ItemStack incoming = ItemStack.of(item, 5);
    slot.insert(incoming, StackOp.EXECUTE);
    incoming.grow(10);
    assertEquals(5, slot.get().count());
  }

  @Test
  void extractAndSimulate() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    slot.set(ItemStack.of(item, 30));
    ItemStack sim = slot.extract(10, StackOp.SIMULATE);
    assertEquals(10, sim.count());
    assertEquals(30, slot.get().count());
    ItemStack got = slot.extract(10, StackOp.EXECUTE);
    assertEquals(10, got.count());
    assertEquals(20, slot.get().count());
  }

  @Test
  void outputRestrictionBlocksExtractUnlessForced() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<ItemStack>(Item.CATEGORY).canOutput(false);
    slot.set(ItemStack.of(item, 30));
    assertTrue(slot.extract(5, StackOp.EXECUTE).isEmpty());
    assertEquals(30, slot.get().count());
    assertEquals(5, slot.extract(5, StackOp.FORCE_EXECUTE).count());
    assertEquals(25, slot.get().count());
  }

  @Test
  void inputRestrictionBlocksInsertUnlessForced() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<ItemStack>(Item.CATEGORY).canInput(false);
    ItemStack incoming = ItemStack.of(item, 3);
    assertEquals(incoming, slot.insert(incoming, StackOp.EXECUTE));
    assertTrue(slot.isEmpty());
    assertTrue(slot.insert(ItemStack.of(item, 3), StackOp.FORCE_EXECUTE).isEmpty());
    assertEquals(3, slot.get().count());
  }

  @Test
  void forceSimulateQueriesIgnoringRestrictions() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<ItemStack>(Item.CATEGORY).canOutput(false);
    slot.set(ItemStack.of(item, 30));
    assertTrue(slot.extract(5, StackOp.SIMULATE).isEmpty());
    assertEquals(5, slot.extract(5, StackOp.FORCE_SIMULATE).count());
    assertEquals(30, slot.get().count());
  }

  // -- SimpleContainer --------------------------------------------------

  @Test
  void insertMergesThenFillsAcrossSlots() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 2);
    c.slotAt(0).set(ItemStack.of(item, 30));
    assertTrue(c.insert(ItemStack.of(item, 150)).isEmpty());
    assertEquals(100, c.slotAt(0).get().count()); // merge first, capped at the type limit
    assertEquals(80, c.slotAt(1).get().count());
  }

  @Test
  void insertRespectsSlotLimit() {
    SimpleContainer<ItemStack> c =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<>(Item.CATEGORY, 10)));
    ItemStack rest = c.insert(ItemStack.of(item, 25));
    assertEquals(15, rest.count());
    assertEquals(10, c.slotAt(0).get().count());
  }

  @Test
  void containerInsertSimulateDoesNotMutate() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 2);
    c.slotAt(0).set(ItemStack.of(item, 80));
    ItemStack rest = c.insert(ItemStack.of(item, 150), StackOp.SIMULATE);
    assertEquals(30, rest.count()); // 20 into slot 0 (capped at 100), 100 into slot 1
    assertEquals(80, c.slotAt(0).get().count());
    assertTrue(c.slotAt(1).get().isEmpty());
  }

  @Test
  void extractMatchesTemplateTypeAndData() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 2);
    ItemStack tagged = ItemStack.of(item, 10);
    tagged.getOrCreateData().putInt("tag", 1);
    c.slotAt(0).set(tagged);
    c.slotAt(1).set(ItemStack.of(item, 5));

    // an untagged template only matches the untagged slot
    assertEquals(5, c.extract(ItemStack.of(item, 8)).count());
    assertEquals(10, c.slotAt(0).get().count());
    // a tagged template only matches the tagged slot
    assertEquals(10, c.extract(tagged).count());
    assertTrue(c.slotAt(0).get().isEmpty());
  }

  @Test
  void containerExtractSimulateDoesNotMutate() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 1);
    c.slotAt(0).set(ItemStack.of(item, 30));
    ItemStack sim = c.extract(ItemStack.of(item, 10), StackOp.SIMULATE);
    assertEquals(10, sim.count());
    assertEquals(30, c.slotAt(0).get().count());
  }

  @Test
  void forceExtractBypassesOutputRestriction() {
    SimpleContainer<ItemStack> c =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<ItemStack>(Item.CATEGORY).canOutput(false)));
    c.slotAt(0).set(ItemStack.of(item, 30));
    assertTrue(c.extract(ItemStack.of(item, 5)).isEmpty());
    assertEquals(5, c.extract(ItemStack.of(item, 5), StackOp.FORCE_EXECUTE).count());
    assertEquals(25, c.slotAt(0).get().count());
  }

  @Test
  void clearEmptiesAllSlots() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 2);
    c.slotAt(0).set(ItemStack.of(item, 5));
    c.slotAt(1).set(ItemStack.of(item, 5));
    assertFalse(c.isEmpty());
    c.clear();
    assertTrue(c.isEmpty());
  }

  // -- concrete stack types ----------------------------------------------

  @Test
  void concreteStackTypesNeedNoCasts() {
    SimpleContainer<ItemStack> inv = new SimpleContainer<>(Item.CATEGORY, 1);
    ItemStack incoming = ItemStack.of(item, 3);
    inv.set(0, incoming);
    assertEquals(3, inv.get(0).count()); // Container<ItemStack> operations are typed on ItemStack
    ItemStack held = inv.slotAt(0).get(); // safe read: a copy, not an alias to the live stack
    ItemStack extracted = inv.extract(incoming);
    assertEquals(3, held.count());
    assertEquals(3, extracted.count());
    assertSame(ItemStack.EMPTY, Item.CATEGORY.emptyStack());
  }

  // -- copy semantics ---------------------------------------------------

  @Test
  void setCopiesCallerStack() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    ItemStack incoming = ItemStack.of(item, 5);
    incoming.getOrCreateData().putInt("k", 1);
    slot.set(incoming);
    // mutating the caller's stack afterwards must not affect the slot
    incoming.grow(10);
    incoming.getData().putInt("k", 2);
    assertEquals(5, slot.get().count());
    assertEquals(1, slot.get().getData().getInt("k", 0));
  }

  @Test
  void setStoresSharedEmptyStack() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    slot.set(ItemStack.of(item, 3));
    slot.set(ItemStack.of(item, 0));
    assertSame(Item.CATEGORY.emptyStack(), slot.get());
  }

  @Test
  void rejectedInsertReturnsCopy() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<ItemStack>(Item.CATEGORY).withValidator(s -> false);
    ItemStack incoming = ItemStack.of(item, 5);
    incoming.getOrCreateData().putInt("k", 1);
    ItemStack rejected = slot.insert(incoming, StackOp.EXECUTE);
    assertNotSame(incoming, rejected);
    assertEquals(incoming, rejected);
    // mutating the rejected copy must not affect the original
    rejected.grow(10);
    assertEquals(5, incoming.count());
  }

  // -- contains ----------------------------------------------------------

  @Test
  void containsMatchesAnySlot() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 2);
    assertFalse(c.contains(s -> !s.isEmpty()));
    c.set(0, ItemStack.of(item, 5));
    assertTrue(c.contains(s -> !s.isEmpty()));
    assertTrue(c.contains(s -> s.type() == item));
    assertFalse(c.contains(s -> false));
  }

  // -- change notification ------------------------------------------------

  @Test
  void slotSilentWhenContentUnchanged() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    List<String> log = new ArrayList<>();
    slot.addChangeListener(() -> log.add("changed"));
    slot.set(ItemStack.of(item, 5));
    slot.set(ItemStack.of(item, 5)); // same content: no notification
    assertEquals(List.of("changed"), log);
  }

  @Test
  void clearNotifiesOnlyChangedSlots() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 3);
    List<Integer> changes = new ArrayList<>();
    c.slotAt(1).addChangeListener(() -> changes.add(1));
    c.set(1, ItemStack.of(item, 5));
    changes.clear();
    c.clear();
    assertEquals(List.of(1), changes);
  }

  @Test
  void slotListenersAreMultipleAndRemovable() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    List<String> log = new ArrayList<>();
    Runnable first = () -> log.add("first");
    Runnable second = () -> log.add("second");
    slot.addChangeListener(first);
    slot.addChangeListener(second);
    slot.set(ItemStack.of(item, 1));
    assertEquals(List.of("first", "second"), log);
    slot.removeChangeListener(first);
    slot.set(ItemStack.of(item, 2));
    assertEquals(List.of("first", "second", "second"), log);
  }

  @Test
  void simulatedOpsNeverNotifySlotListeners() {
    SimpleSlot<ItemStack> slot = new SimpleSlot<>(Item.CATEGORY);
    List<String> log = new ArrayList<>();
    slot.addChangeListener(() -> log.add("changed"));
    slot.insert(ItemStack.of(item, 5), StackOp.SIMULATE);
    slot.extract(3, StackOp.SIMULATE);
    assertTrue(log.isEmpty());
    slot.insert(ItemStack.of(item, 5), StackOp.EXECUTE);
    assertEquals(List.of("changed"), log);
  }

  // -- transfer -----------------------------------------------------------

  @Test
  void transferMovesUnitsBetweenContainers() {
    SimpleContainer<ItemStack> from = new SimpleContainer<>(Item.CATEGORY, 2);
    SimpleContainer<ItemStack> to = new SimpleContainer<>(Item.CATEGORY, 2);
    from.set(0, ItemStack.of(item, 30));
    assertEquals(30, from.transfer(to, ItemStack.of(item, 1), 64));
    assertTrue(from.isEmpty());
    assertEquals(30, to.get(0).count());
  }

  @Test
  void transferRespectsAmountAndTemplate() {
    SimpleContainer<ItemStack> from = new SimpleContainer<>(Item.CATEGORY, 1);
    SimpleContainer<ItemStack> to = new SimpleContainer<>(Item.CATEGORY, 1);
    from.set(0, ItemStack.of(item, 30));
    assertEquals(10, from.transfer(to, ItemStack.of(item, 1), 10));
    assertEquals(20, from.get(0).count());
    assertEquals(10, to.get(0).count());
  }

  @Test
  void transferMovesOnlyWhatFits() {
    SimpleContainer<ItemStack> from = new SimpleContainer<>(Item.CATEGORY, 1);
    SimpleContainer<ItemStack> to =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<>(Item.CATEGORY, 5)));
    from.set(0, ItemStack.of(item, 5));
    to.set(0, ItemStack.of(item, 2)); // room for 3 more
    assertEquals(3, from.transfer(to, ItemStack.of(item, 1), 5));
    assertEquals(5, to.get(0).count());
    assertEquals(2, from.get(0).count());
  }

  @Test
  void transferStopsAtDestinationCapacity() {
    SimpleContainer<ItemStack> from = new SimpleContainer<>(Item.CATEGORY, 1);
    SimpleContainer<ItemStack> to =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<>(Item.CATEGORY, 2)));
    from.set(0, ItemStack.of(item, 5));
    assertEquals(2, from.transfer(to, ItemStack.of(item, 1), 5));
    assertEquals(2, to.get(0).count());
    assertEquals(3, from.get(0).count());
  }

  @Test
  void transferMovesNothingWhenDestinationRejectsAll() {
    SimpleContainer<ItemStack> from = new SimpleContainer<>(Item.CATEGORY, 1);
    SimpleContainer<ItemStack> to =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<ItemStack>(Item.CATEGORY).canInput(false)));
    from.set(0, ItemStack.of(item, 5));
    assertEquals(0, from.transfer(to, ItemStack.of(item, 1), 5));
    assertEquals(5, from.get(0).count());
  }

  @Test
  void transferRespectsSourceOutputRestriction() {
    SimpleContainer<ItemStack> from =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<ItemStack>(Item.CATEGORY).canOutput(false)));
    SimpleContainer<ItemStack> to = new SimpleContainer<>(Item.CATEGORY, 1);
    from.set(0, ItemStack.of(item, 5));
    assertEquals(0, from.transfer(to, ItemStack.of(item, 1), 5));
    assertEquals(5, from.get(0).count());
    assertTrue(to.isEmpty());
  }

  @Test
  void forcedTransferBypassesDestinationValidator() {
    SimpleContainer<ItemStack> from = new SimpleContainer<>(Item.CATEGORY, 1);
    SimpleContainer<ItemStack> to =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<ItemStack>(Item.CATEGORY).withValidator(s -> s.type() != item)));
    from.set(0, ItemStack.of(item, 5));
    assertEquals(0, from.transfer(to, ItemStack.of(item, 1), 5)); // validator refuses
    assertEquals(5, from.get(0).count());
    assertEquals(5, from.transfer(to, ItemStack.of(item, 1), 5, true)); // forced: bypasses the validator
    assertEquals(5, to.get(0).count());
    assertTrue(from.get(0).isEmpty());
  }

  @Test
  void forcedTransferBypassesSourceOutputRestriction() {
    SimpleContainer<ItemStack> from =
        new SimpleContainer<>(Item.CATEGORY, List.of(new SimpleSlot<ItemStack>(Item.CATEGORY).canOutput(false)));
    SimpleContainer<ItemStack> to = new SimpleContainer<>(Item.CATEGORY, 1);
    from.set(0, ItemStack.of(item, 5));
    assertEquals(5, from.transfer(to, ItemStack.of(item, 1), 5, true));
    assertEquals(5, to.get(0).count());
    assertTrue(from.get(0).isEmpty());
  }

  // -- stacks -------------------------------------------------------------

  @Test
  void stacksSnapshotsSlotContents() {
    SimpleContainer<ItemStack> c = new SimpleContainer<>(Item.CATEGORY, 3);
    c.set(1, ItemStack.of(item, 2));
    List<ItemStack> stacks = c.stacks();
    assertEquals(3, stacks.size());
    assertTrue(stacks.get(0).isEmpty());
    assertEquals(2, stacks.get(1).count());
    assertTrue(stacks.get(2).isEmpty());
  }
}
