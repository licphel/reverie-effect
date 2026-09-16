package io.viki.rf.network.packet;

import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.rf.network.Connection;
import io.viki.momentum.math.Vector2;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.entity.ThrownItem;
import io.viki.rf.world.entity.EntityType;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.util.PrecisePos;
import io.viki.rf.client.GameClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reliable entity spawn and destroy changes for one client. */
public final class EntityLifecyclePacket extends Packet {
  private static final int MAX_ENTRIES_PER_SECTION = 16_384;
  private double serverTime;
  private List<Spawn> spawns = List.of();
  private List<UUID> destroys = List.of();

  public EntityLifecyclePacket() {
  }

  public EntityLifecyclePacket(double serverTime, List<Spawn> spawns, List<UUID> destroys) {
    this.serverTime = serverTime;
    this.spawns = List.copyOf(spawns);
    this.destroys = List.copyOf(destroys);
  }

  public double serverTime() { return serverTime; }
  public List<Spawn> spawns() { return spawns; }
  public List<UUID> destroys() { return destroys; }
  @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }

  @Override
  public void read(BinaryBuffer buffer) {
    serverTime = buffer.readDouble();
    int spawnCount = readCount(buffer, "spawn");
    var decodedSpawns = new ArrayList<Spawn>(spawnCount);
    for (int i = 0; i < spawnCount; i++) {
      UUID entityId = buffer.readUUID();
      EntityType type = readType(buffer);
      decodedSpawns.add(new Spawn(entityId, type,
          buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
          buffer.readFloat(), buffer.readFloat()));
    }
    spawns = List.copyOf(decodedSpawns);
    int destroyCount = readCount(buffer, "destroy");
    var decodedDestroys = new ArrayList<UUID>(destroyCount);
    for (int i = 0; i < destroyCount; i++) decodedDestroys.add(buffer.readUUID());
    destroys = List.copyOf(decodedDestroys);
  }

  @Override
  public void write(BinaryBuffer buffer) {
    buffer.writeDouble(serverTime);
    buffer.writeVarInt(spawns.size());
    for (Spawn spawn : spawns) {
      buffer.writeUUID(spawn.entityId());
      buffer.write((byte) spawn.type().ordinal());
      buffer.writeFloat(spawn.x());
      buffer.writeFloat(spawn.y());
      buffer.writeFloat(spawn.facing());
      buffer.writeFloat(spawn.velocityX());
      buffer.writeFloat(spawn.velocityY());
    }
    buffer.writeVarInt(destroys.size());
    for (UUID entityId : destroys) buffer.writeUUID(entityId);
  }

  @Override
  public void handle(Connection connection) {
    ClientLevel level = GameClient.level();
    Entity player = GameClient.player();
    level.interpolationTracker().receiveTimeUpdate(serverTime);
    for (UUID entityId : destroys) {
      if (!entityId.equals(player.entityId())) {
        level.entities().remove(entityId);
        level.interpolationTracker().remove(entityId);
        level.pendingPhysics().remove(entityId);
      }
    }
    for (Spawn spawn : spawns) {
      if (level.entities().contains(spawn.entityId())) continue;
      Entity entity = createReplica(spawn);
      entity.setEntityId(spawn.entityId());
      entity.setPositionImmediate(new PrecisePos(spawn.x(), spawn.y()));
      entity.setVelocity(spawn.velocityX(), spawn.velocityY());
      entity.setFacingAngle(spawn.facing());
      entity.setSimulated(true);
      entity.enterChunk(level);
      level.interpolationTracker().track(entity, serverTime);
      ClientLevel.PendingPhysics pending = level.pendingPhysics().remove(spawn.entityId());
      if (pending != null && pending.serverTime() >= serverTime) {
        level.interpolationTracker().receive(entity, pending.serverTime(),
            new PrecisePos(pending.state().x(), pending.state().y()),
            pending.state().velocityX(), pending.state().velocityY());
        entity.setFacingAngle(pending.state().facing());
      }
    }
  }

  private static Entity createReplica(Spawn spawn) {
    PrecisePos position = new PrecisePos(spawn.x(), spawn.y());
    return switch (spawn.type()) {
      case PLAYER -> Entity.player(position);
      case THROWN_ITEM -> new ThrownItem(position, new Vector2(spawn.velocityX(), spawn.velocityY()));
    };
  }

  private static int readCount(BinaryBuffer buffer, String section) {
    int count = buffer.readVarInt();
    if (count < 0 || count > MAX_ENTRIES_PER_SECTION) {
      throw new IllegalArgumentException("Invalid entity " + section + " count: " + count);
    }
    return count;
  }

  private static EntityType readType(BinaryBuffer buffer) {
    int ordinal = buffer.read();
    if (ordinal < 0 || ordinal >= EntityType.values().length) {
      throw new IllegalArgumentException("Unknown entity type: " + ordinal);
    }
    return EntityType.values()[ordinal];
  }

  public record Spawn(UUID entityId, EntityType type, float x, float y, float facing,
                      float velocityX, float velocityY) {
    public Spawn {
      Objects.requireNonNull(entityId, "entityId");
      Objects.requireNonNull(type, "type");
    }
  }
}
