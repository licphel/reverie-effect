package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.client.GameClient;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.util.BlockPos;

/** Complete authoritative state of one changed world cell. */
public final class CellUpdatePacket extends Packet {
  private int x;
  private int y;
  private int blockState;
  private int wallState;
  private byte shape;
  private byte liquid;
  private int liquidLevel;

  public CellUpdatePacket() {
  }

  public CellUpdatePacket(int x, int y, int blockState,
                          int wallState, byte shape, byte liquid, int liquidLevel) {
    this.x = x;
    this.y = y;
    this.blockState = blockState;
    this.wallState = wallState;
    this.shape = shape;
    this.liquid = liquid;
    this.liquidLevel = liquidLevel;
  }

  public int x() { return x; }
  public int y() { return y; }
  public int blockState() { return blockState; }
  public int wallState() { return wallState; }
  public byte shape() { return shape; }
  public byte liquid() { return liquid; }
  public int liquidLevel() { return liquidLevel; }

  @Override
  public PacketFlow flow() {
    return PacketFlow.CLIENTBOUND;
  }

  @Override
  public void read(BinaryBuffer buffer) {
    x = buffer.readInt();
    y = buffer.readInt();
    blockState = buffer.readInt();
    wallState = buffer.readInt();
    shape = buffer.read();
    liquid = buffer.read();
    liquidLevel = buffer.readInt();
  }

  @Override
  public void write(BinaryBuffer buffer) {
    buffer.writeInt(x);
    buffer.writeInt(y);
    buffer.writeInt(blockState);
    buffer.writeInt(wallState);
    buffer.write(shape);
    buffer.write(liquid);
    buffer.writeInt(liquidLevel);
  }

  @Override
  public void handle(Connection connection) {
    ClientLevel level = GameClient.level();
    level.setBlock(new BlockPos(x, y), BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(blockState));
    level.setWall(new BlockPos(x, y), BlockState.BLOCK_STATE_PROPERTY_PALETTE.get(wallState));
    level.setBlockShape(x, y, shape);
    level.setLiquid(x, y, Liquids.byId(liquid & 0xFF), liquidLevel);
  }
}
