package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.util.PrecisePos;
import io.viki.rf.client.GameClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reliable batch of entity transforms from one server tick. */
public final class EntityPhysicsPacket extends Packet {
  public static final int MAX_STATES = 30;
  private double serverTime;
  private List<State> states = List.of();

  public EntityPhysicsPacket() {
  }

  public EntityPhysicsPacket(double serverTime, List<State> states) {
    if (states.size() > MAX_STATES) {
      throw new IllegalArgumentException("Too many entity physics states: " + states.size());
    }
    this.serverTime = serverTime;
    this.states = List.copyOf(states);
  }

  public double serverTime() { return serverTime; }
  public List<State> states() { return states; }
  @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }
  @Override
  public void read(BinaryBuffer buffer) {
    serverTime = buffer.readDouble();
    int count = buffer.readVarInt();
    if (count < 0 || count > MAX_STATES) {
      throw new IllegalArgumentException("Invalid entity physics state count: " + count);
    }
    var decoded = new ArrayList<State>(count);
    for (int i = 0; i < count; i++) {
      decoded.add(new State(buffer.readUUID(),
          buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
          buffer.readFloat(), buffer.readFloat()));
    }
    states = List.copyOf(decoded);
  }

  @Override
  public void write(BinaryBuffer buffer) {
    buffer.writeDouble(serverTime);
    buffer.writeVarInt(states.size());
    for (State state : states) {
      buffer.writeUUID(state.entityId());
      buffer.writeFloat(state.x());
      buffer.writeFloat(state.y());
      buffer.writeFloat(state.facing());
      buffer.writeFloat(state.velocityX());
      buffer.writeFloat(state.velocityY());
    }
  }

  @Override
  public void handle(Connection connection) {
    ClientLevel level = GameClient.level();
    Entity player = GameClient.player();
    level.interpolationTracker().receiveTimeUpdate(serverTime);
    for (State state : states) {
      Entity entity = level.entities().get(state.entityId());
      if (entity == null) {
        if (level.pendingPhysics().size() < 4_096
            || level.pendingPhysics().containsKey(state.entityId())) {
          level.pendingPhysics().compute(state.entityId(), (ignored, previous) ->
              previous == null || serverTime > previous.serverTime()
                  ? new ClientLevel.PendingPhysics(serverTime, state) : previous);
        }
      } else if (!entity.entityId().equals(player.entityId())) {
        level.interpolationTracker().receive(entity, serverTime,
            new PrecisePos(state.x(), state.y()), state.velocityX(), state.velocityY());
        entity.setFacingAngle(state.facing());
      }
    }
  }

  public record State(UUID entityId, float x, float y, float facing,
                      float velocityX, float velocityY) {
    public State {
      Objects.requireNonNull(entityId, "entityId");
    }
  }
}
