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

import io.viki.momentum.codec.nbt.*;
import io.viki.momentum.codec.nbt.primitives.*;
import io.viki.momentum.codec.streaming.BinaryBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Comprehensive tests for the NBT codec: tag types, containers, binary round-trips, JSON interop. */
public class NbtCodecTest {

  // ================================================================
  // Serialization helpers
  // ================================================================

  private static byte[] serializeBytes(NBT nbt) {
    BinaryBuffer buf = BinaryBuffer.heap();
    buf.writeNBT(nbt);
    return buf.copiedArray();
  }

  private static CompoundNBT roundTrip(CompoundNBT tag) {
    BinaryBuffer buf = BinaryBuffer.heap();
    CompoundNBT.CODEC.serialize(tag, buf);
    return CompoundNBT.CODEC.deserialize(buf);
  }

  private static ListNBT roundTrip(ListNBT list) {
    BinaryBuffer buf = BinaryBuffer.heap();
    ListNBT.CODEC.serialize(list, buf);
    return ListNBT.CODEC.deserialize(buf);
  }

  private static void assertByteArrayEquals(byte[] expected, byte[] actual) {
    assert Arrays.equals(expected, actual) : "expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual);
  }

  // ================================================================
  // Entry point
  // ================================================================

  public static void main(String[] args) {
    testTagValues();
    testNumericConversions();
    testTagEquality();
    testTagCopy();
    testNullTag();
    testTagMarkLookup();
    testTagMarkValidate();
    testCompoundGetPut();
    testCompoundTypedGetters();
    testCompoundMissingAndFallback();
    testCompoundNullValue();
    testCompoundPaths();
    testCompoundEscape();
    testCompoundRemove();
    testCompoundCopyDeep();
    testCompoundSerializationDeterministic();
    testCompoundRoundTrip();
    testCompoundUnmodifiable();
    testCompoundIterationOrder();
    testCompoundByteLevel();
    testCompoundMapConstructor();
    testListAddAndGet();
    testListTypedGetters();
    testListMutation();
    testListNullElement();
    testListCopy();
    testListRoundTrip();
    testJsonRoundTrip();
    testJsonNumberTypes();
    testJsonLongBoundary();
    testJsonByteArrayAndNull();
    testJsonParseErrors();
    testJsonPrettyRoundTrip();
    testTagWrap();
    testTagWrapInvalid();
    System.out.println("ALL TESTS PASSED");
  }

  // ================================================================
  // Tag types
  // ================================================================

  private static void testTagValues() {
    assert new ByteNBT((byte) 5).get() == (byte) 5;
    assert new ByteNBT((byte) 5).dataType() == DataType.BYTE;
    assert new ShortNBT((short) 300).get() == (short) 300;
    assert new ShortNBT((short) 300).dataType() == DataType.SHORT;
    assert new IntNBT(123456).get() == 123456;
    assert new IntNBT(123456).dataType() == DataType.INT;
    assert new LongNBT(Long.MAX_VALUE).get() == Long.MAX_VALUE;
    assert new LongNBT(Long.MAX_VALUE).dataType() == DataType.LONG;
    assert new FloatNBT(1.5f).get() == 1.5f;
    assert new FloatNBT(1.5f).dataType() == DataType.FLOAT;
    assert new DoubleNBT(-2.75).get() == -2.75;
    assert new DoubleNBT(-2.75).dataType() == DataType.DOUBLE;
    assert new BooleanNBT(true).get();
    assert new BooleanNBT(true).dataType() == DataType.BOOLEAN;
    assert new StringNBT("hi").get().equals("hi");
    assert new StringNBT("hi").dataType() == DataType.STRING;
    assertByteArrayEquals(new byte[]{1, -1}, new ByteArrayNBT(new byte[]{1, -1}).get());
    assert new ByteArrayNBT(new byte[]{1}).dataType() == DataType.BYTE_ARRAY;
    assert new CompoundNBT().dataType() == DataType.COMPOUND;
    assert new ListNBT().dataType() == DataType.LIST;

    // asObject() unwraps to the raw boxed value
    assert new IntNBT(5).asObject().equals(5);
    assert new LongNBT(5L).asObject().equals(5L);
    assert new DoubleNBT(2.5).asObject().equals(2.5);
    assert new StringNBT("hi").asObject().equals("hi");
    assert new BooleanNBT(true).asObject().equals(true);
    assertByteArrayEquals(new byte[]{1}, (byte[]) new ByteArrayNBT(new byte[]{1}).asObject());

    CompoundNBT c = new CompoundNBT();
    assert c.asObject() == c;
    ListNBT l = new ListNBT();
    assert l.asObject() == l;
    System.out.println("  ✓ testTagValues");
  }

  private static void testNumericConversions() {
    assert new IntNBT(300).asByte() == (byte) 44;   // narrowing wraps
    assert new IntNBT(300).asShort() == (short) 300;
    assert new ShortNBT((short) 300).asInt() == 300; // widening
    assert new LongNBT(Long.MAX_VALUE).asInt() == -1; // overflow truncates
    assert new DoubleNBT(3.7).asInt() == 3;         // float truncates toward zero
    assert new DoubleNBT(-3.7).asLong() == -3L;
    assert new FloatNBT(1.5f).asLong() == 1L;
    assert new IntNBT(42).asDouble() == 42.0;
    assert new IntNBT(42).asFloat() == 42.0f;
    assert new ByteNBT((byte) -5).asDouble() == -5.0;
    assert new DoubleNBT(2.0).asLong() == 2L;
    System.out.println("  ✓ testNumericConversions");
  }

  private static void testTagEquality() {
    assert new IntNBT(5).equals(new IntNBT(5));
    assert new IntNBT(5).hashCode() == new IntNBT(5).hashCode();
    assert !new IntNBT(5).equals(new IntNBT(6));
    assert !new IntNBT(5).equals(new ByteNBT((byte) 5)); // different tag types never equal
    assert new StringNBT("a").equals(new StringNBT("a"));
    assert !new StringNBT("a").equals(new StringNBT("b"));
    assert new BooleanNBT(true).equals(new BooleanNBT(true));
    assert !new BooleanNBT(true).equals(new BooleanNBT(false));
    assert new ByteArrayNBT(new byte[]{1, 2}).equals(new ByteArrayNBT(new byte[]{1, 2}));
    assert !new ByteArrayNBT(new byte[]{1}).equals(new ByteArrayNBT(new byte[]{2}));

    CompoundNBT a = new CompoundNBT();
    a.putInt("k", 1);
    CompoundNBT b = new CompoundNBT();
    b.putInt("k", 1);
    assert a.equals(b);
    assert a.hashCode() == b.hashCode();
    b.putInt("k2", 2);
    assert !a.equals(b);

    ListNBT l1 = new ListNBT();
    l1.addInt(1);
    ListNBT l2 = new ListNBT();
    l2.addInt(1);
    assert l1.equals(l2);
    l2.addInt(2);
    assert !l1.equals(l2);

    assert NullNBT.INSTANCE.equals(NullNBT.INSTANCE);
    System.out.println("  ✓ testTagEquality");
  }

  private static void testTagCopy() {
    IntNBT i = new IntNBT(5);
    NBT ic = i.copy();
    assert ic != i;
    assert ic.equals(i);
    assert ((IntNBT) ic).get() == 5;

    // ByteArrayTag: constructor copies the source array, get() returns a copy,
    // copy() deep-copies — no aliasing anywhere.
    byte[] src = {1, 2};
    ByteArrayNBT arr = new ByteArrayNBT(src);
    src[0] = 99;
    assertByteArrayEquals(new byte[]{1, 2}, arr.get());
    byte[] first = arr.get();
    first[0] = 88;
    assertByteArrayEquals(new byte[]{1, 2}, arr.get());
    ByteArrayNBT arrCopy = (ByteArrayNBT) arr.copy();
    assertByteArrayEquals(arr.get(), arrCopy.get());
    assert arr.get() != arrCopy.get();

    assert new StringNBT("x").copy() != new StringNBT("x");
    assert NullNBT.INSTANCE.copy() == NullNBT.INSTANCE;
    System.out.println("  ✓ testTagCopy");
  }

  private static void testNullTag() {
    assert NullNBT.INSTANCE.dataType() == DataType.NULL;
    assert NullNBT.INSTANCE.asObject() == null;
    assert NullNBT.INSTANCE.copy() == NullNBT.INSTANCE;
    assertByteArrayEquals(new byte[0], serializeBytes(NullNBT.INSTANCE));
    System.out.println("  ✓ testNullTag");
  }

  private static void testTagMarkLookup() {
    for (DataType m : DataType.values()) {
      if (m != DataType.UNKNOWN) {
        assert DataType.fromID(m.id()) == m;
      }
    }
    assert DataType.fromID((byte) 0xFE) == DataType.UNKNOWN;
    assert DataType.fromClass(Integer.class) == DataType.INT;
    assert DataType.fromClass(Byte.class) == DataType.BYTE;
    assert DataType.fromClass(byte[].class) == DataType.BYTE_ARRAY;
    assert DataType.fromClass(String.class) == DataType.STRING;
    assert DataType.fromClass(null) == DataType.NULL;
    assert DataType.fromClass(Object.class) == DataType.UNKNOWN;
    assert DataType.fromValue(null) == DataType.NULL;
    assert DataType.fromValue(5) == DataType.INT;
    assert DataType.fromValue(5L) == DataType.LONG;
    assert DataType.fromValue("s") == DataType.STRING;
    assert DataType.fromValue(true) == DataType.BOOLEAN;
    assert DataType.fromValue(new IntNBT(5)) == DataType.INT;
    assert DataType.fromValue(new CompoundNBT()) == DataType.COMPOUND;
    assert DataType.fromValue(new ListNBT()) == DataType.LIST;
    System.out.println("  ✓ testTagMarkLookup");
  }

  private static void testTagMarkValidate() {
    DataType.validate(5);
    DataType.validate(null);
    boolean threw = false;
    try {
      DataType.validate(new Object());
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw : "TagMark.validate must reject unknown values";
    System.out.println("  ✓ testTagMarkValidate");
  }

  // ================================================================
  // CompoundTag
  // ================================================================

  private static void testCompoundGetPut() {
    CompoundNBT c = new CompoundNBT();
    c.putByte("b", (byte) 1);
    c.putShort("s", (short) 2);
    c.putInt("i", 3);
    c.putLong("l", 4L);
    c.putFloat("f", 1.5f);
    c.putDouble("d", 2.5);
    c.putBoolean("bool", true);
    c.putString("str", "x");
    c.putBytes("bytes", new byte[]{1});
    assert c.size() == 9;
    assert c.get("b") instanceof ByteNBT;
    assert c.get("s") instanceof ShortNBT;
    assert c.get("i") instanceof IntNBT;
    assert c.get("l") instanceof LongNBT;
    assert c.get("f") instanceof FloatNBT;
    assert c.get("d") instanceof DoubleNBT;
    assert c.get("bool") instanceof BooleanNBT;
    assert c.get("str") instanceof StringNBT;
    assert c.get("bytes") instanceof ByteArrayNBT;
    System.out.println("  ✓ testCompoundGetPut");
  }

  private static void testCompoundTypedGetters() {
    CompoundNBT c = new CompoundNBT();
    c.putShort("s", (short) 300);
    assert c.getByte("s", (byte) 0) == (byte) 44;      // narrowing
    assert c.getInt("s", 0) == 300;                    // widening
    assert c.getLong("s", 0) == 300L;
    assert c.getDouble("s", 0) == 300.0;
    c.putByte("one", (byte) 1);
    assert c.getBoolean("one", false);                 // nonzero numeric → true
    c.putBoolean("t", true);
    assert c.getByte("t", (byte) 0) == (byte) 1;       // BooleanTag → 1
    c.putDouble("d", 3.7);
    assert c.getInt("d", 0) == 3;                      // float truncation
    c.putString("str", "x");
    assert c.getInt("str", -1) == -1;                  // type mismatch → fallback
    assert c.getString("str", "").equals("x");
    System.out.println("  ✓ testCompoundTypedGetters");
  }

  private static void testCompoundMissingAndFallback() {
    CompoundNBT c = new CompoundNBT();
    assert c.isEmpty();
    assert c.get("nope") == null;
    assert c.getInt("nope", -1) == -1;
    assert c.getString("nope", "def").equals("def");
    assert !c.contains("nope");
    assert c.tryGet("nope").isEmpty();
    assert c.get("nope", NullNBT.INSTANCE) == NullNBT.INSTANCE;
    System.out.println("  ✓ testCompoundMissingAndFallback");
  }

  private static void testCompoundNullValue() {
    CompoundNBT c = new CompoundNBT();
    c.put("k", null);
    assert c.get("k") instanceof NullNBT;
    assert c.contains("k");                            // key exists, value is the NULL tag
    assert c.getString("k", "fallback").equals("fallback");
    System.out.println("  ✓ testCompoundNullValue");
  }

  private static void testCompoundPaths() {
    CompoundNBT c = new CompoundNBT();
    c.put("$a.b.c", new IntNBT(5));
    assert c.getInt("$a.b.c", 0) == 5;
    assert c.contains("$a.b.c");
    assert c.contains("$a");
    assert c.contains("$a.b");
    CompoundNBT a = c.getCompound("$a");
    assert a.contains("b");
    assert c.get("$a") instanceof CompoundNBT;

    // A non-compound value on a path is never silently replaced
    CompoundNBT c2 = new CompoundNBT();
    c2.putInt("$x", 1);
    c2.putInt("$x.y", 2);                              // seekParent returns null → write dropped
    assert c2.get("$x.y") == null;
    assert c2.getInt("$x", 0) == 1;
    System.out.println("  ✓ testCompoundPaths");
  }

  private static void testCompoundEscape() {
    CompoundNBT c = new CompoundNBT();
    c.put("$$foo", new StringNBT("bar"));
    assert c.keySet().contains("$foo");                // stored under the literal key "$foo"
    assert c.getString("$$foo", "").equals("bar");
    assert c.getString("$foo", "default").equals("default"); // single $ is a path "foo", absent
    assert c.contains("$$foo");
    System.out.println("  ✓ testCompoundEscape");
  }

  private static void testCompoundRemove() {
    CompoundNBT c = new CompoundNBT();
    c.putInt("k", 1);
    c.putInt("$$esc", 2);
    c.put("$a.b", new IntNBT(3));
    c.remove("k");
    assert !c.contains("k");
    c.remove("$$esc");
    assert !c.contains("$$esc");
    assert !c.keySet().contains("$esc");
    c.remove("$a.b");
    assert c.get("$a.b") == null;
    assert c.contains("$a");                           // intermediate node survives
    c.remove("$a");
    assert !c.contains("$a");
    System.out.println("  ✓ testCompoundRemove");
  }

  private static void testCompoundCopyDeep() {
    CompoundNBT nested = new CompoundNBT();
    nested.putInt("x", 1);
    CompoundNBT c = new CompoundNBT();
    c.putInt("top", 2);
    c.put("$$dollar", new IntNBT(7));
    c.putCompound("n", nested);

    CompoundNBT copy = c.copy();
    assert copy.equals(c);
    assert copy.getInt("$$dollar", 0) == 7;            // $-prefixed key survives copy
    CompoundNBT copyNested = copy.getCompound("n");
    assert copyNested != nested;
    copyNested.putInt("x", 99);
    assert c.getInt("$n.x", 0) == 1;                   // original unaffected
    copy.putInt("extra", 1);
    assert !c.contains("extra");
    System.out.println("  ✓ testCompoundCopyDeep");
  }

  private static void testCompoundSerializationDeterministic() {
    CompoundNBT c = new CompoundNBT();
    c.putInt("a", 1);
    c.putString("b", "x");
    c.put("$n.m", new IntNBT(2));
    c.put("$$dollar", new IntNBT(3));
    assertByteArrayEquals(serializeBytes(c), serializeBytes(c));
    System.out.println("  ✓ testCompoundSerializationDeterministic");
  }

  private static void testCompoundRoundTrip() {
    CompoundNBT c = new CompoundNBT();
    c.putByte("b", (byte) -3);
    c.putShort("s", (short) 300);
    c.putInt("i", 123456);
    c.putLong("l", Long.MAX_VALUE);
    c.putFloat("f", 1.5f);
    c.putDouble("d", -2.75);
    c.putBoolean("bool", true);
    c.putString("str", "héllo 世界");
    c.putBytes("bytes", new byte[]{0, 1, -1, 127});
    c.put("nul", null);
    c.put("$$dollar", new IntNBT(7));
    CompoundNBT nested = new CompoundNBT();
    nested.putInt("x", 42);
    c.putCompound("nest", nested);
    ListNBT list = new ListNBT();
    list.addByte((byte) 1);
    list.addString("two");
    list.addBoolean(false);
    c.putList("list", list);

    CompoundNBT r = roundTrip(c);
    assert r.equals(c);                                // structural equality after the round trip
    assert r.getByte("b", (byte) 0) == (byte) -3;
    assert r.getShort("s", (short) 0) == (short) 300;
    assert r.getInt("i", 0) == 123456;
    assert r.getLong("l", 0) == Long.MAX_VALUE;
    assert r.getFloat("f", 0f) == 1.5f;
    assert r.getDouble("d", 0) == -2.75;
    assert r.getBoolean("bool", false);
    assert r.getString("str", "").equals("héllo 世界");
    assertByteArrayEquals(new byte[]{0, 1, -1, 127}, r.getBytes("bytes"));
    assert r.get("nul") instanceof NullNBT;
    assert r.getInt("$$dollar", 0) == 7;
    assert r.get("$dollar") == null;                   // literal $ key is not a path
    assert r.getInt("$nest.x", 0) == 42;
    ListNBT rl = r.getList("list");
    assert rl.size() == 3;
    assert rl.getByte(0, (byte) 0) == (byte) 1;
    assert rl.getString(1, "").equals("two");
    assert !rl.getBoolean(2, true);
    System.out.println("  ✓ testCompoundRoundTrip");
  }

  private static void testCompoundUnmodifiable() {
    CompoundNBT c = new CompoundNBT();
    c.putInt("a", 1);
    CompoundNBT u = CompoundNBT.ofUnmodifiable(c);
    u.putInt("b", 2);
    assert !u.contains("b");
    assert u.getInt("a", 0) == 1;
    u.remove("a");
    assert u.contains("a");
    System.out.println("  ✓ testCompoundUnmodifiable");
  }

  private static void testCompoundIterationOrder() {
    CompoundNBT c = new CompoundNBT();
    c.putInt("a", 1);
    c.putInt("b", 2);
    c.putInt("c", 3);
    List<String> keys = new ArrayList<>();
    for (var entry : c) {
      keys.add(entry.getKey());
    }
    assert keys.equals(List.of("a", "b", "c"));        // insertion order preserved
    assert new ArrayList<>(c.keySet()).equals(List.of("a", "b", "c"));
    System.out.println("  ✓ testCompoundIterationOrder");
  }

  private static void testCompoundByteLevel() {
    // TagMark IDs are written offset by Byte.MIN_VALUE: END(0) → 0x80, BOOLEAN(3) → 0x83
    assertByteArrayEquals(new byte[]{(byte) 0x80}, serializeBytes(new CompoundNBT())); // empty → END tag only
    assertByteArrayEquals(new byte[]{1}, serializeBytes(new BooleanNBT(true)));
    assertByteArrayEquals(new byte[]{0}, serializeBytes(new BooleanNBT(false)));
    assertByteArrayEquals(new byte[]{(byte) 0xFF}, serializeBytes(new ByteNBT((byte) -1)));
    System.out.println("  ✓ testCompoundByteLevel");
  }

  private static void testCompoundMapConstructor() {
    CompoundNBT c = new CompoundNBT(Map.of("k", new IntNBT(1)));
    assert c.getInt("k", 0) == 1;
    System.out.println("  ✓ testCompoundMapConstructor");
  }

  // ================================================================
  // ListTag
  // ================================================================

  private static void testListAddAndGet() {
    ListNBT list = new ListNBT();
    assert list.isEmpty();
    assert list.size() == 0;
    list.addByte((byte) 1);
    list.addShort((short) 2);
    list.addInt(3);
    list.addLong(4L);
    list.addFloat(5.5f);
    list.addDouble(6.5);
    list.addBoolean(true);
    list.addString("seven");
    list.addBytes(new byte[]{8});
    CompoundNBT inner = new CompoundNBT();
    list.addCompound(inner);
    ListNBT innerList = new ListNBT();
    list.addList(innerList);
    assert list.size() == 11;
    assert list.getByte(0, (byte) 0) == (byte) 1;
    assert list.getShort(1, (short) 0) == (short) 2;
    assert list.getInt(2, 0) == 3;
    assert list.getLong(3, 0) == 4L;
    assert list.getFloat(4, 0f) == 5.5f;
    assert list.getDouble(5, 0) == 6.5;
    assert list.getBoolean(6, false);
    assert list.getString(7, "").equals("seven");
    assertByteArrayEquals(new byte[]{8}, list.getBytes(8));
    assert list.getCompound(9) == inner;
    assert list.getList(10) == innerList;

    // out-of-bounds and type mismatch fall back
    assert list.get(99) == null;
    assert list.getCompound(99) == null;
    assert list.getBytes(99) == null;
    assert list.getInt(7, -1) == -1;
    assert list.getByte(2, (byte) 0) == (byte) 3;
    assert list.getLong(0, 0) == 1L;
    System.out.println("  ✓ testListAddAndGet");
  }

  private static void testListTypedGetters() {
    ListNBT list = new ListNBT();
    list.addInt(300);
    assert list.getByte(0, (byte) 0) == (byte) 44;     // narrowing
    assert list.getShort(0, (short) 0) == (short) 300;
    assert list.getInt(0, 0) == 300;
    assert list.getLong(0, 0) == 300L;
    assert list.getDouble(0, 0) == 300.0;
    list.addBoolean(true);
    assert list.getByte(1, (byte) 0) == (byte) 1;      // BooleanTag → 1
    list.addByte((byte) 1);
    assert list.getBoolean(2, false);                  // nonzero → true
    list.addByte((byte) 0);
    assert !list.getBoolean(3, true);                  // zero → false
    System.out.println("  ✓ testListTypedGetters");
  }

  private static void testListMutation() {
    ListNBT list = new ListNBT();
    list.addInt(1);
    list.addInt(3);
    list.insert(1, new IntNBT(2));
    assert list.size() == 3;
    assert list.getInt(1, 0) == 2;
    list.set(0, new IntNBT(10));
    assert list.getInt(0, 0) == 10;
    list.removeAt(1);
    assert list.size() == 2;
    assert list.getInt(1, 0) == 3;
    list.addInt(1);
    assert list.remove(new IntNBT(1));                 // removes by value equality
    assert list.size() == 2;
    list.clear();
    assert list.isEmpty();
    System.out.println("  ✓ testListMutation");
  }

  private static void testListNullElement() {
    ListNBT list = new ListNBT();
    list.add(null);
    list.addString("x");
    assert list.get(0) == NullNBT.INSTANCE;
    assert list.get(1) instanceof StringNBT;
    list.insert(0, null);
    assert list.get(0) == NullNBT.INSTANCE;
    assert list.size() == 3;
    list.set(2, null);
    assert list.get(2) == NullNBT.INSTANCE;
    System.out.println("  ✓ testListNullElement");
  }

  private static void testListCopy() {
    CompoundNBT inner = new CompoundNBT();
    inner.putInt("x", 1);
    ListNBT list = new ListNBT();
    list.addInt(1);
    list.addCompound(inner);

    ListNBT copy = list.copy();
    assert copy.equals(list);
    CompoundNBT copyInner = copy.getCompound(1);
    assert copyInner != inner;
    copyInner.putInt("x", 99);
    assert inner.getInt("x", 0) == 1;                  // original unaffected
    System.out.println("  ✓ testListCopy");
  }

  private static void testListRoundTrip() {
    ListNBT list = new ListNBT();
    list.addInt(1);
    list.addString("x");
    list.add(null);
    list.addBoolean(true);
    list.addBytes(new byte[]{-1, 1});
    CompoundNBT inner = new CompoundNBT();
    inner.putInt("k", 2);
    list.addCompound(inner);

    ListNBT r = roundTrip(list);
    assert r.equals(list);
    assert r.getInt(0, 0) == 1;
    assert r.getString(1, "").equals("x");
    assert r.get(2) == NullNBT.INSTANCE;
    assert r.getBoolean(3, false);
    assertByteArrayEquals(new byte[]{-1, 1}, r.getBytes(4));
    assert r.getCompound(5).getInt("k", 0) == 2;
    System.out.println("  ✓ testListRoundTrip");
  }

  // ================================================================
  // JsonUtil
  // ================================================================

  private static void testJsonRoundTrip() {
    CompoundNBT c = new CompoundNBT();
    c.putByte("b", (byte) 1);
    c.putInt("i", 42);
    c.putLong("l", 2147483648L);
    c.putDouble("d", 3.5);
    c.putBoolean("bo", true);
    c.putString("s", "hi");
    c.putInt("$$dollar", 9);
    CompoundNBT nested = new CompoundNBT();
    nested.putInt("x", 7);
    c.putCompound("nest", nested);

    CompoundNBT r = JsonUtil.parse(JsonUtil.dump(c, false));
    assert r.getByte("b", (byte) 0) == (byte) 1;
    assert r.getInt("i", 0) == 42;
    assert r.getLong("l", 0) == 2147483648L;
    assert r.getDouble("d", 0) == 3.5;
    assert r.getBoolean("bo", false);
    assert r.getString("s", "").equals("hi");
    assert r.getInt("$$dollar", 0) == 9;
    assert r.getInt("$nest.x", 0) == 7;
    System.out.println("  ✓ testJsonRoundTrip");
  }

  private static void testJsonNumberTypes() {
    CompoundNBT r = JsonUtil.parse("{\"a\":5,\"b\":2147483648,\"c\":3.5,\"d\":true,\"e\":\"s\",\"f\":null,\"g\":[1,\"x\"]}");
    assert r.get("a") instanceof IntNBT;
    assert r.get("b") instanceof LongNBT;
    assert r.get("c") instanceof DoubleNBT;
    assert r.get("d") instanceof BooleanNBT;
    assert r.get("e") instanceof StringNBT;
    assert r.get("f") instanceof NullNBT;
    ListNBT g = r.getList("g");
    assert g.get(0) instanceof IntNBT;
    assert g.get(1) instanceof StringNBT;
    System.out.println("  ✓ testJsonNumberTypes");
  }

  private static void testJsonLongBoundary() {
    CompoundNBT c = new CompoundNBT();
    c.putLong("big", 9007199254740992L);   // 2^53 (MAX_SAFE_INTEGER + 1)
    c.putLong("safe", 9007199254740991L);  // MAX_SAFE_INTEGER
    String json = JsonUtil.dump(c, false);
    assert json.contains("\"9007199254740992\"");      // unsafe → string
    assert !json.contains("\"9007199254740991\"");     // safe → plain number

    CompoundNBT r = JsonUtil.parse(json);
    assert r.get("big") instanceof StringNBT;
    assert r.get("safe") instanceof LongNBT;
    assert r.getString("big", "").equals("9007199254740992");
    assert r.getLong("safe", 0) == 9007199254740991L;
    System.out.println("  ✓ testJsonLongBoundary");
  }

  private static void testJsonByteArrayAndNull() {
    CompoundNBT c = new CompoundNBT();
    c.putBytes("bytes", new byte[]{1, -1});
    c.put("nul", null);
    String json = JsonUtil.dump(c, false);
    assert json.contains("[1,255]");
    assert json.contains("null");

    CompoundNBT r = JsonUtil.parse(json);
    ListNBT l = r.getList("bytes");                    // byte arrays round-trip as int lists
    assert l.getInt(0, 0) == 1;
    assert l.getInt(1, 0) == 255;
    assert r.get("nul") instanceof NullNBT;
    System.out.println("  ✓ testJsonByteArrayAndNull");
  }

  private static void testJsonParseErrors() {
    boolean threw = false;
    try {
      JsonUtil.parse("not json");
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw : "parse must reject non-JSON input";

    threw = false;
    try {
      JsonUtil.parse("[1,2]");                         // root must be an object
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw : "parse must reject non-object roots";
    System.out.println("  ✓ testJsonParseErrors");
  }

  private static void testJsonPrettyRoundTrip() {
    CompoundNBT c = new CompoundNBT();
    c.putInt("a", 1);
    String pretty = JsonUtil.dumpPrettily(c);
    assert pretty.contains("\n");
    assert JsonUtil.parse(pretty).getInt("a", 0) == 1;
    System.out.println("  ✓ testJsonPrettyRoundTrip");
  }

  // ================================================================
  // Tag.wrap
  // ================================================================

  private static void testTagWrap() {
    assert NBT.wrap((byte) 1) instanceof ByteNBT;
    assert ((ByteNBT) NBT.wrap((byte) 1)).get() == (byte) 1;
    assert NBT.wrap((short) 1) instanceof ShortNBT;
    assert NBT.wrap(1) instanceof IntNBT;
    assert NBT.wrap(1L) instanceof LongNBT;
    assert NBT.wrap(1.5f) instanceof FloatNBT;
    assert NBT.wrap(2.5) instanceof DoubleNBT;
    assert NBT.wrap(true) instanceof BooleanNBT;
    assert NBT.wrap("s") instanceof StringNBT;
    assert NBT.wrap(new byte[]{1}) instanceof ByteArrayNBT;
    assert NBT.wrap(new Byte[]{1}) instanceof ByteArrayNBT;
    assert NBT.wrap(null) == NullNBT.INSTANCE;

    // already-typed Tag pass through unchanged
    IntNBT tag = new IntNBT(1);
    assert NBT.wrap(tag) == tag;
    CompoundNBT comp = new CompoundNBT();
    assert NBT.wrap(comp) == comp;
    assert NBT.wrap(NullNBT.INSTANCE) == NullNBT.INSTANCE;
    System.out.println("  ✓ testTagWrap");
  }

  private static void testTagWrapInvalid() {
    boolean threw = false;
    try {
      NBT.wrap(new Object());
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw : "Tag.wrap must reject unknown values";
    System.out.println("  ✓ testTagWrapInvalid");
  }
}
