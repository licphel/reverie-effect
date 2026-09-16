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

package io.viki.rf.network.codec;

import io.netty.buffer.ByteBuf;
<<<<<<<< HEAD:core/src/main/java/io/viki/rf/network/codec/NettyBinaryBuffer.java
import io.viki.momentum.codec.streaming.BinaryBuffer;
========
import io.viki.momentum.codec.streaming.CursorBuffer;
>>>>>>>> origin/main:core/src/main/java/io/viki/rf/network/codec/NettyCursorBuffer.java
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.nio.ByteOrder;

/**
 * A {@link BinaryBuffer} that wraps a Netty {@link ByteBuf}.
 *
 * <p>Read and write cursors are mapped to the Netty buffer's reader and writer indices.
 */
@InternalApi
final class NettyBinaryBuffer extends BinaryBuffer {
  private final ByteBuf buffer;
  private boolean bigEndian;

  /**
   * Wraps an existing Netty buffer with native byte order.
   *
   * @param buffer the Netty buffer to wrap
   */
  NettyBinaryBuffer(ByteBuf buffer) {
    this.buffer = buffer;
    this.bigEndian = true; // Network buffers are often big endian
    this.readerIndex = buffer.readerIndex();
    this.writerIndex = buffer.writerIndex();
  }

  @Override
  protected void grow(int minCapacity) {
    if (buffer.capacity() < minCapacity) {
      buffer.capacity(minCapacity);
    }
  }

  @Override
  public int capacity() {
    return buffer.capacity();
  }

  @Override
  public void writerIndex(int index) {
    super.writerIndex(index);
    buffer.writerIndex(index);
  }

  @Override
  public void readerIndex(int index) {
    super.readerIndex(index);
    buffer.readerIndex(index);
  }

  @Override
  public ByteOrder order() {
    return bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
  }

  @Override
  public void order(ByteOrder order) {
    this.bigEndian = order == ByteOrder.BIG_ENDIAN;
  }

  @Override
  public void clear() {
    super.clear();
    buffer.clear();
  }

  @Override
  public void write(byte value) {
    ensureWritable(1);
    buffer.writeByte(value);
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeShort(short value) {
    ensureWritable(2);
    if (bigEndian) {
      buffer.writeShort(value);
    } else {
      buffer.writeShortLE(value);
    }
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeInt(int value) {
    ensureWritable(4);
    if (bigEndian) {
      buffer.writeInt(value);
    } else {
      buffer.writeIntLE(value);
    }
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeLong(long value) {
    ensureWritable(8);
    if (bigEndian) {
      buffer.writeLong(value);
    } else {
      buffer.writeLongLE(value);
    }
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeFloat(float value) {
    ensureWritable(4);
    if (bigEndian) {
      buffer.writeFloat(value);
    } else {
      buffer.writeFloatLE(value);
    }
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeDouble(double value) {
    ensureWritable(8);
    if (bigEndian) {
      buffer.writeDouble(value);
    } else {
      buffer.writeDoubleLE(value);
    }
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeBytes(byte[] src, int srcOffset, int length) {
    if (length == 0) {
      return;
    }
    ensureWritable(length);
    buffer.writeBytes(src, srcOffset, length);
    writerIndex = buffer.writerIndex();
  }

  @Override
  public void writeBuf(BinaryBuffer src, int length) {
    ensureWritable(length);
    buffer.writeBytes(src.readBytes(length));
    writerIndex = buffer.writerIndex();
  }

  @Override
  public byte read() {
    ensureReadable(1);
    byte v = buffer.readByte();
    readerIndex = buffer.readerIndex();
    return v;
  }

  @Override
  public short readShort() {
    ensureReadable(2);
    short v = bigEndian ? buffer.readShort() : buffer.readShortLE();
    readerIndex = buffer.readerIndex();
    return v;
  }

  @Override
  public int readInt() {
    ensureReadable(4);
    int v = bigEndian ? buffer.readInt() : buffer.readIntLE();
    readerIndex = buffer.readerIndex();
    return v;
  }

  @Override
  public long readLong() {
    ensureReadable(8);
    long v = bigEndian ? buffer.readLong() : buffer.readLongLE();
    readerIndex = buffer.readerIndex();
    return v;
  }

  @Override
  public float readFloat() {
    ensureReadable(4);
    float v = bigEndian ? buffer.readFloat() : buffer.readFloatLE();
    readerIndex = buffer.readerIndex();
    return v;
  }

  @Override
  public double readDouble() {
    ensureReadable(8);
    double v = bigEndian ? buffer.readDouble() : buffer.readDoubleLE();
    readerIndex = buffer.readerIndex();
    return v;
  }

  @Override
  public void readBytes(byte[] dst, int dstOffset, int length) {
    ensureReadable(length);
    buffer.readBytes(dst, dstOffset, length);
    readerIndex = buffer.readerIndex();
  }

  @Override
  public void readBuf(BinaryBuffer dst, int length) {
    ensureReadable(length);
    dst.writeBytes(readBytes(length)); // A copy here is nearly inevitable.
    readerIndex = buffer.readerIndex();
  }

  @Override
  public void compact() {
    buffer.discardReadBytes();
    readerIndex = buffer.readerIndex();
    writerIndex = buffer.writerIndex();
  }

  @Override
  public byte[] copiedArray() {
    int len = readableBytes();
    byte[] dst = new byte[len];
    buffer.getBytes(readerIndex, dst);
    return dst;
  }

  @Override
  public byte @Nullable [] backingArray() {
    return buffer.hasArray() ? buffer.array() : null;
  }

  @Override
  public void close() {
    buffer.release();
  }

  public ByteBuf unwrap() {
    return buffer;
  }
}