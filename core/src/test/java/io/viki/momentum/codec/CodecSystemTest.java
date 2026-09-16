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

package io.viki.momentum.codec;

import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.nbt.ListNBT;
import io.viki.momentum.codec.nbt.NBT;
import io.viki.momentum.codec.nbt.primitives.IntNBT;
import io.viki.momentum.codec.streaming.BinaryBuffer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests for the minimal Codec contract: the object defines only the NBT
 * mapping, and binary streaming is derived from it automatically.
 */
public final class CodecSystemTest {

  /** Example record with its codec — the object knows nothing about codecs. */
  record Point(int x, int y) {
  }

  private static final Codec<Point> POINT_CODEC = new Codec<>() {
    @Override
    public CompoundNBT serialize(Point value) {
      CompoundNBT tag = new CompoundNBT();
      tag.putInt("x", value.x());
      tag.putInt("y", value.y());
      return tag;
    }

    @Override
    public Point deserialize(NBT nbt) {
      CompoundNBT tag = (CompoundNBT) nbt;
      return new Point(tag.getInt("x", 0), tag.getInt("y", 0));
    }
  };

  /** Nested record: a name plus a list of points. */
  record Polyline(String name, List<Point> points) {
  }

  private static final Codec<Polyline> POLYLINE_CODEC = new Codec<>() {
    @Override
    public CompoundNBT serialize(Polyline value) {
      CompoundNBT tag = new CompoundNBT();
      tag.putString("name", value.name());
      ListNBT points = new ListNBT();
      for (Point p : value.points()) {
        points.add(POINT_CODEC.serialize(p));
      }
      tag.putList("points", points);
      return tag;
    }

    @Override
    public Polyline deserialize(NBT nbt) {
      CompoundNBT tag = (CompoundNBT) nbt;
      ListNBT points = tag.getList("points");
      if (points == null) {
        throw new IllegalArgumentException("Polyline requires field points");
      }
      List<Point> decoded = new java.util.ArrayList<>(points.size());
      for (NBT element : points) {
        decoded.add(POINT_CODEC.deserialize(element));
      }
      return new Polyline(tag.getString("name", ""), decoded);
    }
  };

  /** Strict codec: rejects missing required fields instead of using fallbacks. */
  record ServerConfig(String host, int port) {
  }

  private static final Codec<ServerConfig> STRICT_CODEC = new Codec<>() {
    @Override
    public CompoundNBT serialize(ServerConfig value) {
      CompoundNBT tag = new CompoundNBT();
      tag.putString("host", value.host());
      tag.putInt("port", value.port());
      return tag;
    }

    @Override
    public ServerConfig deserialize(NBT nbt) {
      CompoundNBT tag = (CompoundNBT) nbt;
      if (!tag.contains("host") || !tag.contains("port")) {
        throw new IllegalArgumentException("ServerConfig requires fields host and port");
      }
      return new ServerConfig(tag.getString("host", ""), tag.getInt("port", 0));
    }
  };

  /** Identity codec over a raw compound: the tag tree itself as a codec value. */
  private static final Codec<CompoundNBT> COMPOUND_CODEC = new Codec<>() {
    @Override
    public CompoundNBT serialize(CompoundNBT value) {
      return value.copy();
    }

    @Override
    public CompoundNBT deserialize(NBT nbt) {
      return (CompoundNBT) nbt;
    }
  };

  /** Built from {@link Codec#of} instead of an anonymous class. */
  private static final Codec<String> STRING_CODEC = Codec.of(
      value -> {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("value", value);
        return tag;
      },
      nbt -> ((CompoundNBT) nbt).getString("value", ""));

  /** Derived from {@code STRING_CODEC} via {@link Codec#xmap}. */
  private static final Codec<UUID> UUID_CODEC = STRING_CODEC.xmap(UUID::fromString, UUID::toString);

  /** Top-level list codec via {@link Codec#listOf}. */
  private static final Codec<List<Point>> POINTS_CODEC = Codec.listOf(POINT_CODEC);

  /** Identity codec over any NBT value. */
  private static final Codec<NBT> NBT_CODEC = Codec.of(nbt -> nbt, nbt -> nbt);

  public static void main(String[] args) {
    testNbtRoundTrip();
    testStreamingRoundTrip();
    testStreamingMatchesNbtBytes();
    testNestedObject();
    testListRootField();
    testStrictMissingField();
    testRawCompoundCodec();
    testOfFactory();
    testXmap();
    testListOf();
    testIdentityNbt();
    System.out.println("ALL TESTS PASSED");
  }

  private static void testNbtRoundTrip() {
    Point point = new Point(3, 4);
    NBT tag = POINT_CODEC.serialize(point);
    assert tag instanceof CompoundNBT;
    assert ((CompoundNBT) tag).getInt("x", 0) == 3;
    assert ((CompoundNBT) tag).getInt("y", 0) == 4;
    assert POINT_CODEC.deserialize(tag).equals(point);
    System.out.println("  ✓ testNbtRoundTrip");
  }

  private static void testStreamingRoundTrip() {
    Point point = new Point(-7, 1000000);
    BinaryBuffer buffer = BinaryBuffer.heap();
    POINT_CODEC.serialize(point, buffer);
    Point decoded = POINT_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray()));
    assert decoded.equals(point);
    System.out.println("  ✓ testStreamingRoundTrip");
  }

  private static void testStreamingMatchesNbtBytes() {
    // The derived streaming format must be exactly the NBT tree's own binary format.
    Point point = new Point(1, 2);
    BinaryBuffer stream = BinaryBuffer.heap();
    POINT_CODEC.serialize(point, stream);
    byte[] streamBytes = stream.copiedArray();
    BinaryBuffer nbt = BinaryBuffer.heap();
    nbt.writeNBT(POINT_CODEC.serialize(point));
    assert Arrays.equals(streamBytes, nbt.copiedArray()) : "streaming and NBT bytes differ";
    System.out.println("  ✓ testStreamingMatchesNbtBytes");
  }

  private static void testNestedObject() {
    Polyline polyline = new Polyline("route-a", List.of(new Point(0, 0), new Point(5, 5)));
    BinaryBuffer buffer = BinaryBuffer.heap();
    POLYLINE_CODEC.serialize(polyline, buffer);
    Polyline decoded = POLYLINE_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray()));
    assert decoded.equals(polyline);
    // Round trip through the tree form too.
    assert POLYLINE_CODEC.deserialize(POLYLINE_CODEC.serialize(polyline)).equals(polyline);
    System.out.println("  ✓ testNestedObject");
  }

  private static void testListRootField() {
    NBT tag = POLYLINE_CODEC.serialize(new Polyline("p", List.of()));
    ListNBT points = ((CompoundNBT) tag).getList("points");
    assert points != null && points.isEmpty();
    assert POLYLINE_CODEC.deserialize(tag).points().isEmpty();
    System.out.println("  ✓ testListRootField");
  }

  private static void testStrictMissingField() {
    boolean threw = false;
    try {
      STRICT_CODEC.deserialize(new CompoundNBT());
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw : "fromNbt must reject missing required fields";

    ServerConfig config = new ServerConfig("localhost", 25565);
    BinaryBuffer buffer = BinaryBuffer.heap();
    STRICT_CODEC.serialize(config, buffer);
    assert STRICT_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray())).equals(config);
    System.out.println("  ✓ testStrictMissingField");
  }

  private static void testRawCompoundCodec() {
    CompoundNBT original = new CompoundNBT();
    original.putInt("a", 1);
    original.putString("b", "two");
    BinaryBuffer buffer = BinaryBuffer.heap();
    COMPOUND_CODEC.serialize(original, buffer);
    CompoundNBT decoded = COMPOUND_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray()));
    assert decoded.equals(original);
    System.out.println("  ✓ testRawCompoundCodec");
  }

  private static void testOfFactory() {
    assert STRING_CODEC.deserialize(STRING_CODEC.serialize("hello")).equals("hello");
    BinaryBuffer buffer = BinaryBuffer.heap();
    STRING_CODEC.serialize("world", buffer);
    assert STRING_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray())).equals("world");
    System.out.println("  ✓ testOfFactory");
  }

  private static void testXmap() {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assert UUID_CODEC.deserialize(UUID_CODEC.serialize(uuid)).equals(uuid);
    BinaryBuffer buffer = BinaryBuffer.heap();
    UUID_CODEC.serialize(uuid, buffer);
    assert UUID_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray())).equals(uuid);
    // The tree is a plain compound containing the string form.
    NBT tree = UUID_CODEC.serialize(uuid);
    assert ((CompoundNBT) tree).getString("value", "").equals(uuid.toString());
    System.out.println("  ✓ testXmap");
  }

  private static void testListOf() {
    List<Point> points = List.of(new Point(1, 2), new Point(-3, 4));
    assert POINTS_CODEC.deserialize(POINTS_CODEC.serialize(points)).equals(points);
    BinaryBuffer buffer = BinaryBuffer.heap();
    POINTS_CODEC.serialize(points, buffer);
    assert POINTS_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray())).equals(points);
    // Root must be a list of compounds.
    NBT tree = POINTS_CODEC.serialize(points);
    assert tree instanceof ListNBT list && list.size() == 2 && list.getCompound(0) != null;
    // Rejecting a non-list root.
    boolean threw = false;
    try {
      POINTS_CODEC.deserialize(new IntNBT(7));
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw : "listOf.fromNbt must reject non-list roots";
    System.out.println("  ✓ testListOf");
  }

  private static void testIdentityNbt() {
    IntNBT value = new IntNBT(42);
    assert NBT_CODEC.deserialize(NBT_CODEC.serialize(value)).equals(value);
    BinaryBuffer buffer = BinaryBuffer.heap();
    NBT_CODEC.serialize(value, buffer);
    assert NBT_CODEC.deserialize(BinaryBuffer.wrap(buffer.copiedArray())).equals(value);
    System.out.println("  ✓ testIdentityNbt");
  }
}
