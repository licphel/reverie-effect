package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.util.PrecisePos;
import io.viki.rf.client.GameClient;
import io.viki.rf.world.level.ClientLevel;

/** Begins client world initialization and declares the initial snapshot count. */
public final class WorldInitPacket extends Packet {
  private long seed;
  private int chunkCount;
  private double spawnX;
  private double spawnY;

  public WorldInitPacket() {
  }

  public WorldInitPacket(long seed, int chunkCount, double spawnX, double spawnY) {
    this.seed = seed;
    this.chunkCount = chunkCount;
    this.spawnX = spawnX;
    this.spawnY = spawnY;
  }

  public long seed() { return seed; }
  public int chunkCount() { return chunkCount; }
  public double spawnX() { return spawnX; }
  public double spawnY() { return spawnY; }
  @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }
  @Override public void read(BinaryBuffer buffer) {
    seed = buffer.readLong();
    chunkCount = buffer.readInt();
    spawnX = buffer.readDouble();
    spawnY = buffer.readDouble();
  }
  @Override public void write(BinaryBuffer buffer) {
    buffer.writeLong(seed);
    buffer.writeInt(chunkCount);
    buffer.writeDouble(spawnX);
    buffer.writeDouble(spawnY);
  }
  @Override public void handle(Connection connection) {
    ClientLevel level = GameClient.level();
    Entity player = GameClient.player();
    level.beginInitialization(chunkCount);
    player.setPosition(new PrecisePos(spawnX, spawnY));
  }
}
