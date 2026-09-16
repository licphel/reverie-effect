package io.viki.rf.network.packet;

import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.util.ChunkPos;
import io.viki.rf.client.GameClient;

/** Serialized authoritative chunk sent to a client. */
public final class ChunkSnapshotPacket extends Packet {
  private CompoundNBT chunk = new CompoundNBT();

  public ChunkSnapshotPacket() {
  }

  public ChunkSnapshotPacket(CompoundNBT chunk) {
    this.chunk = CompoundNBT.ofUnmodifiable(chunk);
  }

  public CompoundNBT chunk() {
    return chunk;
  }

  @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }

  @Override
  public void read(BinaryBuffer buffer) {
    chunk = CompoundNBT.CODEC.deserialize(buffer);
  }

  @Override
  public void write(BinaryBuffer buffer) {
    CompoundNBT.CODEC.serialize(chunk, buffer);
  }

  @Override public void handle(Connection connection) {
    ClientLevel level = GameClient.level();
    Entity player = GameClient.player();
    level.deserializeChunk(chunk);
    initialized(level, chunk);
    if (level.initializing() && level.initializedChunks().size() >= level.expectedChunks()) {
      player.enterChunk(level);
      level.markReady();
    }
  }

  private void initialized(ClientLevel level, CompoundNBT chunk) {
    level.initializedChunks().add(new ChunkPos(chunk.getInt("x", 0), chunk.getInt("y", 0)).asLong());
  }
}
