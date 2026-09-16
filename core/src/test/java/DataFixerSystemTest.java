/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */

package io.viki.momentum.codec.nbt.fixer;

import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.nbt.ListNBT;
import io.viki.momentum.codec.nbt.NBT;
import io.viki.momentum.codec.nbt.fixer.*;

import java.util.Map;

/**
 * System tests for {@link DataFixerUpper} and {@link NBTWalker}, modeled after
 * the Minecraft 1.12 -> 1.13 item data migration: item stacks carry a flat
 * {@code Damage} field that moves into a nested {@code tag}, and a later
 * version renames an item id. Walkers locate item stacks inside chests.
 */
public final class DataFixerSystemTest {
  private static final TypeReference ITEM_STACK = new TypeReference("item_stack");
  private static final TypeReference CHEST = new TypeReference("chest");

  private static int passed = 0;

  public static void main(String[] args) {
    testTypeReferenceValidation();
    testSingleFix();
    testFixChain();
    testBoundedUpdate();
    testSkipAndApplyGaps();
    testNoDowngrade();
    testWalkedChest();
    testNestedWalk();
    testUnrelatedTypeUntouched();
    testInvalidRegistration();
    System.out.println("ALL TESTS PASSED: " + passed + " tests");
  }

  static void testTypeReferenceValidation() {
    assertThrows(IllegalArgumentException.class, () -> new TypeReference(""), "empty name rejected");
    assertThrows(NullPointerException.class, () -> new TypeReference(null), "null name rejected");
    passed++;
    System.out.println("PASS: type reference validation");
  }

  static void testSingleFix() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .build();
    CompoundNBT item = item("minecraft:stone", 5);

    CompoundNBT fixed = (CompoundNBT) fixer.update(ITEM_STACK, item, 0);

    assertFalse(fixed.contains("Damage"), "flat Damage removed");
    assertEquals(5, fixed.getCompound("tag").getInt("Damage", -1), "Damage moved into tag");
    assertEquals("minecraft:stone", fixed.getString("id", ""), "id preserved");
    // the input tree is not mutated
    assertTrue(item.contains("Damage"), "input tree left untouched");
    passed++;
    System.out.println("PASS: single fix");
  }

  static void testFixChain() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .register(ITEM_STACK, 1, 2, renameStone())
        .build();

    CompoundNBT fixed = (CompoundNBT) fixer.update(ITEM_STACK, item("minecraft:stone", 5), 0);

    assertEquals(5, fixed.getCompound("tag").getInt("Damage", -1), "fix 0->1 applied");
    assertEquals("minecraft:cobblestone", fixed.getString("id", ""), "fix 1->2 applied");
    passed++;
    System.out.println("PASS: fix chain");
  }

  static void testBoundedUpdate() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .register(ITEM_STACK, 1, 2, renameStone())
        .build();

    CompoundNBT fixed = (CompoundNBT) fixer.update(ITEM_STACK, item("minecraft:stone", 5), 0, 1);

    assertEquals(5, fixed.getCompound("tag").getInt("Damage", -1), "fix 0->1 applied");
    assertEquals("minecraft:stone", fixed.getString("id", ""), "fix 1->2 outside range skipped");
    passed++;
    System.out.println("PASS: bounded update");
  }

  static void testSkipAndApplyGaps() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .register(ITEM_STACK, 2, 3, addCount())
        .build();

    // 1->2 has no fix, but 2->3 still applies when it is inside the range.
    CompoundNBT upTo3 = (CompoundNBT) fixer.update(ITEM_STACK, item("minecraft:stone", 5), 0, 3);
    assertEquals(5, upTo3.getCompound("tag").getInt("Damage", -1), "fix 0->1 applied");
    assertEquals(1, upTo3.getInt("Count", 0), "fix 2->3 applied across the gap");

    CompoundNBT upTo2 = (CompoundNBT) fixer.update(ITEM_STACK, item("minecraft:stone", 5), 0, 2);
    assertFalse(upTo2.contains("Count"), "fix 2->3 outside range skipped");
    passed++;
    System.out.println("PASS: skip and apply gaps");
  }

  static void testNoDowngrade() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .build();
    CompoundNBT item = item("minecraft:stone", 5);

    // Data already at version 5, only fixes below that exist: unchanged.
    assertTrue(fixer.update(ITEM_STACK, item, 5) == item, "no fix applied at or above current version");
    passed++;
    System.out.println("PASS: no downgrade");
  }

  static void testWalkedChest() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .register(ITEM_STACK, 1, 2, renameStone())
        .registerWalker(DataFixerSystemTest::walkItemStacks)
        .build();

    CompoundNBT chest = chest(item("minecraft:stone", 5), item("minecraft:stone", 9));
    chest.putString("CustomName", "Chesty");

    // Version 0: both stacks fully migrated.
    CompoundNBT fixed = (CompoundNBT) fixer.update(ITEM_STACK, chest, 0);
    ListNBT items = fixed.getList("Items");
    assertEquals(2, items.size(), "both items kept");
    CompoundNBT first = items.getCompound(0);
    assertEquals(5, first.getCompound("tag").getInt("Damage", -1), "first stack migrated");
    assertEquals("minecraft:cobblestone", first.getString("id", ""), "first stack renamed");
    assertEquals(9, items.getCompound(1).getCompound("tag").getInt("Damage", -1), "second stack migrated");
    assertEquals("Chesty", fixed.getString("CustomName", ""), "non-item keys preserved");

    // Version 1: only the rename applies, Damage stays flat.
    CompoundNBT atOne = (CompoundNBT) fixer.update(ITEM_STACK, chest, 1);
    assertEquals("minecraft:cobblestone", atOne.getList("Items").getCompound(0).getString("id", ""), "rename applied");
    assertTrue(atOne.getList("Items").getCompound(0).contains("Damage"), "0->1 fix skipped at version 1");
    passed++;
    System.out.println("PASS: walked chest");
  }

  static void testNestedWalk() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .registerWalker(DataFixerSystemTest::walkItemStacks)
        .build();

    // A chest whose first item is itself a chest (a shulker-like container):
    // the walker must descend into it through the recursive update.
    CompoundNBT inner = chest(item("minecraft:stone", 7));
    CompoundNBT outer = chest(inner);

    CompoundNBT fixed = (CompoundNBT) fixer.update(ITEM_STACK, outer, 0);
    CompoundNBT fixedInner = fixed.getList("Items").getCompound(0);
    assertEquals(7, fixedInner.getList("Items").getCompound(0).getCompound("tag").getInt("Damage", -1),
        "nested chest's items migrated");
    passed++;
    System.out.println("PASS: nested walk");
  }

  static void testUnrelatedTypeUntouched() {
    DataFixer fixer = DataFixerUpper.builder()
        .register(ITEM_STACK, 0, 1, damageToTag())
        .registerWalker(DataFixerSystemTest::walkItemStacks)
        .build();
    CompoundNBT item = item("minecraft:stone", 5);

    // The walker ignores other types and no fixes exist for CHEST.
    assertTrue(fixer.update(CHEST, item, 0) == item, "unrelated type returned unchanged");
    passed++;
    System.out.println("PASS: unrelated type untouched");
  }

  static void testInvalidRegistration() {
    DataFixerUpper.Builder builder = DataFixerUpper.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.register(ITEM_STACK, 2, 1, damageToTag()),
        "non-increasing range rejected");
    builder.register(ITEM_STACK, 0, 1, damageToTag());
    assertThrows(IllegalArgumentException.class, () -> builder.register(ITEM_STACK, 0, 2, damageToTag()),
        "duplicate start version rejected");
    passed++;
    System.out.println("PASS: invalid registration");
  }

  /** Moves a flat {@code Damage} field into {@code tag.Damage} (1.12 -> 1.13). */
  static DataFix damageToTag() {
    return input -> {
      if (!(input instanceof CompoundNBT item) || !item.contains("Damage")) {
        return input;
      }
      CompoundNBT result = copyOf(item);
      result.remove("Damage");
      CompoundNBT tag = new CompoundNBT();
      tag.putInt("Damage", item.getInt("Damage", 0));
      result.putCompound("tag", tag);
      return result;
    };
  }

  /** Renames the item id {@code minecraft:stone} (1.13 block rename). */
  static DataFix renameStone() {
    return input -> {
      if (!(input instanceof CompoundNBT item) || !"minecraft:stone".equals(item.getString("id", ""))) {
        return input;
      }
      CompoundNBT result = copyOf(item);
      result.putString("id", "minecraft:cobblestone");
      return result;
    };
  }

  /** Adds a stack {@code Count} field (1.14 -> 1.15). */
  static DataFix addCount() {
    return input -> {
      if (!(input instanceof CompoundNBT item) || item.contains("Count")) {
        return input;
      }
      CompoundNBT result = copyOf(item);
      result.putInt("Count", 1);
      return result;
    };
  }

  /** Locates item stacks in every {@code Items} list, recursing into nested containers. */
  static NBT walkItemStacks(TypeReference type, NBT input, DataFixer fixer, int version) {
    if (type != ITEM_STACK) {
      return input;
    }
    if (input instanceof CompoundNBT chest) {
      CompoundNBT result = new CompoundNBT();
      for (Map.Entry<String, NBT> entry : chest.entrySet()) {
        NBT value = entry.getValue();
        if ("Items".equals(entry.getKey()) && value instanceof ListNBT items) {
          ListNBT fixed = new ListNBT();
          for (int i = 0; i < items.size(); i++) {
            fixed.add(fixer.update(ITEM_STACK, items.get(i), version));
          }
          result.putList("Items", fixed);
        } else {
          result.put(entry.getKey(), walkItemStacks(type, value, fixer, version));
        }
      }
      return result;
    }
    if (input instanceof ListNBT list) {
      ListNBT result = new ListNBT();
      for (int i = 0; i < list.size(); i++) {
        result.add(walkItemStacks(type, list.get(i), fixer, version));
      }
      return result;
    }
    return input;
  }

  static CompoundNBT item(String id, int damage) {
    CompoundNBT item = new CompoundNBT();
    item.putString("id", id);
    item.putInt("Damage", damage);
    return item;
  }

  static CompoundNBT chest(CompoundNBT... items) {
    CompoundNBT chest = new CompoundNBT();
    ListNBT list = new ListNBT();
    for (CompoundNBT item : items) {
      list.add(item);
    }
    chest.putList("Items", list);
    return chest;
  }

  static CompoundNBT copyOf(CompoundNBT source) {
    CompoundNBT copy = new CompoundNBT();
    for (Map.Entry<String, NBT> entry : source.entrySet()) {
      copy.put(entry.getKey(), entry.getValue());
    }
    return copy;
  }

  static void assertTrue(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError("FAILED: " + message);
    }
  }

  static void assertFalse(boolean condition, String message) {
    assertTrue(!condition, message);
  }

  static void assertEquals(int expected, int actual, String message) {
    if (expected != actual) {
      throw new AssertionError("FAILED: " + message + " (expected " + expected + ", got " + actual + ")");
    }
  }

  static void assertEquals(String expected, String actual, String message) {
    if (!expected.equals(actual)) {
      throw new AssertionError("FAILED: " + message + " (expected \"" + expected + "\", got \"" + actual + "\")");
    }
  }

  static void assertThrows(Class<? extends Throwable> expected, Runnable action, String message) {
    try {
      action.run();
    } catch (Throwable t) {
      if (expected.isInstance(t)) {
        return;
      }
      throw new AssertionError("FAILED: " + message + " (threw " + t.getClass().getSimpleName() + ")", t);
    }
    throw new AssertionError("FAILED: " + message + " (nothing thrown)");
  }
}
