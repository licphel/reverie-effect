package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.server.GameServer;
import io.viki.rf.Registries;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.ServerLevel;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;

import java.util.Map;
import java.util.UUID;

/** A predicted client world-edit intent. Values are untrusted until validated. */
public final class WorldActionPacket extends Packet {
  public static final byte SET_BLOCK = 0;
  public static final byte SET_WALL = 1;
  public static final byte SET_SHAPE = 2;
  public static final byte SET_LIQUID = 3;
  private byte action;
  private int x;
  private int y;
  private int value;
  private int auxiliary;

  public WorldActionPacket() {
  }

  public WorldActionPacket(byte action, int x, int y, int value, int auxiliary) {
    this.action = action;
    this.x = x;
    this.y = y;
    this.value = value;
    this.auxiliary = auxiliary;
  }

  public byte action() { return action; }
  public int x() { return x; }
  public int y() { return y; }
  public int value() { return value; }
  public int auxiliary() { return auxiliary; }

  @Override
  public PacketFlow flow() {
    return PacketFlow.SERVERBOUND;
  }

  @Override
  public void read(BinaryBuffer buffer) {
    action = buffer.read();
    x = buffer.readInt();
    y = buffer.readInt();
    value = buffer.readInt();
    auxiliary = buffer.readInt();
  }

  @Override
  public void write(BinaryBuffer buffer) {
    buffer.write(action);
    buffer.writeInt(x);
    buffer.writeInt(y);
    buffer.writeInt(value);
    buffer.writeInt(auxiliary);
  }

  @Override
  public void handle(Connection connection) {
    ServerLevel level = GameServer.level();
    UUID owner = connection.netUuid();
    int count = GameServer.worldActionsThisTick().merge(owner, 1, Integer::sum);
    ChunkPos chunkPos = chunkOf(x, y);
    boolean interested = level.chunkManager().isInterested(owner, chunkPos);
    if (count > 32
        || !validAction() || !interested) {
      if (interested) sendCell(level, connection, x, y);
      return;
    }
    Chunk before = level.getChunk(chunkPos);
    long previousRevision = before == null ? -1L : before.terrainRevision();
    apply(level);
    Chunk after = level.getChunk(chunkPos);
    if (after != null && after.terrainRevision() != previousRevision) {
      for (Connection peer : GameServer.host().connections()) {
        if (level.chunkManager().isInterested(peer.netUuid(), chunkPos)) {
          Map<Long, Long> sent = GameServer.sentChunks().get(peer.netUuid());
          if (sent != null) sent.put(chunkPos.asLong(), after.terrainRevision());
        }
      }
    }
    for (Connection peer : GameServer.host().connections()) {
      if (level.chunkManager().isInterested(peer.netUuid(), chunkPos)) {
        sendCell(level, peer, x, y);
      }
    }
  }

  private boolean validAction() {
    if (action < SET_BLOCK || action > SET_LIQUID) return false;
    return switch (action) {
      case SET_BLOCK, SET_WALL -> value >= 0 && value < BlockState.BLOCK_STATE_PROPERTY_PALETTE.size();
      case SET_SHAPE -> value >= TileShape.FULL.id() && value < TileShape.COUNT;
      case SET_LIQUID -> value >= 0 && value < Registries.LIQUIDS.size()
          && auxiliary >= 0 && auxiliary <= 255;
      default -> false;
    };
  }

  private void apply(ServerLevel level) {
    switch (action) {
      case SET_BLOCK -> level.setBlock(new BlockPos(x, y),
          BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(value));
      case SET_WALL -> level.setWall(new BlockPos(x, y),
          BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(value));
      case SET_SHAPE -> level.setBlockShape(x, y, (byte) value);
      case SET_LIQUID -> level.setLiquid(x, y, Liquids.byId(value), auxiliary);
      default -> throw new IllegalArgumentException("Unsupported world action: " + action);
    }
  }

  private static void sendCell(ServerLevel level, Connection connection, int x, int y) {
    ChunkPos chunk = chunkOf(x, y);
    level.requestChunk(chunk);
    connection.send(new CellUpdatePacket(x, y,
        level.getBlock(x, y).identity(), level.getWall(x, y).identity(),
        level.getBlockShape(x, y), level.getLiquidType(x, y), level.getLiquidLevel(x, y)));
  }

  private static ChunkPos chunkOf(int x, int y) {
    return new ChunkPos(Math.floorDiv(x, ChunkPos.SIZE), Math.floorDiv(y, ChunkPos.SIZE));
  }
}
