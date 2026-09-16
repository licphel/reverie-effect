package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.client.GameClient;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.fluid.LiquidMap;
import io.viki.rf.world.level.Chunk;

/** Reliable raw liquid-layer snapshot for one active chunk. */
public final class LiquidUpdatePacket extends Packet {
  private int chunkX;
  private int chunkY;
  private byte[] data = new byte[LiquidMap.RAW_SIZE];

  public LiquidUpdatePacket() {
  }

  public LiquidUpdatePacket(int chunkX, int chunkY, byte[] data) {
    this(chunkX, chunkY, data, false);
  }

  private LiquidUpdatePacket(int chunkX, int chunkY, byte[] data, boolean takeOwnership) {
    if (data.length != LiquidMap.RAW_SIZE) {
      throw new IllegalArgumentException(
          "Invalid liquid map size: " + data.length + ", expected " + LiquidMap.RAW_SIZE);
    }
    this.chunkX = chunkX;
    this.chunkY = chunkY;
    this.data = takeOwnership ? data : data.clone();
  }

  public static LiquidUpdatePacket capture(Chunk chunk) {
    return new LiquidUpdatePacket(
        chunk.chunkPos.x(), chunk.chunkPos.y(), chunk.liquidSnapshot(), true);
  }

  public int chunkX() { return chunkX; }
  public int chunkY() { return chunkY; }
  public byte[] data() { return data; }

  @Override
  public PacketFlow flow() {
    return PacketFlow.CLIENTBOUND;
  }

  @Override
  public void read(BinaryBuffer buffer) {
    chunkX = buffer.readInt();
    chunkY = buffer.readInt();
    data = buffer.readBytes(LiquidMap.RAW_SIZE);
  }

  @Override
  public void write(BinaryBuffer buffer) {
    buffer.writeInt(chunkX);
    buffer.writeInt(chunkY);
    buffer.writeBytes(data, 0, data.length);
  }

  @Override
  public void handle(Connection connection) {
    ClientLevel level = GameClient.level();
    var chunk = level.getChunk(
        new io.viki.rf.world.util.ChunkPos(chunkX, chunkY));
    if (chunk != null && chunk.applyLiquidSnapshot(data)) {
      level.fluidEngine().activateChunk(chunk);
    }
  }
}
