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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.viki.momentum.codec.streaming.CursorBuffer;
import io.viki.rf.network.packet.Packet;
import io.viki.rf.network.packet.PacketRegistry;

import java.util.zip.Deflater;

/**
 * A Netty outbound handler that encodes a {@link Packet} into wire-format bytes.
 *
 * <p>In uncompressed mode, the output is the packet ID followed by the payload. In compressed mode, when the
 * ID-plus-payload block reaches the compression threshold, the block is compressed before transmission; blocks below
 * the threshold are sent uncompressed with a zero-length marker.
 *
 * <p>A downstream length-prefixing handler must be installed to prepend the frame-length header.
 *
 * @see PacketDecoder
 */
public final class PacketEncoder extends MessageToByteEncoder<Packet> {
  private final int compressionThreshold;

  /**
   * Creates an encoder with compression disabled.
   */
  public PacketEncoder() {
    this(-1);
  }

  /**
   * Creates an encoder with the given compression threshold.
   *
   * @param compressionThreshold the minimum ID-plus-payload byte count that triggers compression; a negative value
   *                             disables compression entirely
   */
  public PacketEncoder(int compressionThreshold) {
    this.compressionThreshold = compressionThreshold;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) {
    int packetId = PacketRegistry.id(msg.getClass());
    if (packetId < 0) {
      throw new IllegalArgumentException("Unregistered packet type: " + msg.getClass().getName());
    }
    ByteBuf content = ctx.alloc().buffer();
    try {
      CursorBuffer buf = new NettyCursorBuffer(content);
      buf.writeInt(packetId);
      msg.write(buf);
      // sync Netty cursors from Buf cursors
      content.writerIndex(buf.writerIndex());
      content.readerIndex(buf.readerIndex());

      if (compressionThreshold >= 0) {
        int dataSize = content.readableBytes();
        if (dataSize >= compressionThreshold) {
          byte[] uncompressed = new byte[dataSize];
          content.readBytes(uncompressed);

          Deflater deflater = new Deflater();
          deflater.setInput(uncompressed);
          deflater.finish();

          byte[] compressed = new byte[dataSize];
          int compressedSize = deflater.deflate(compressed);
          deflater.end();

          out.writeInt(dataSize);
          out.writeBytes(compressed, 0, compressedSize);
        } else {
          out.writeInt(0);
          out.writeBytes(content);
        }
      } else {
        out.writeBytes(content);
      }
    } finally {
      content.release();
    }
  }
}
