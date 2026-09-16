package io.viki.rf.server;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import io.viki.rf.network.Connection;
import io.viki.rf.GameConstants;
import io.viki.rf.network.ConnectionHost;
import io.viki.rf.network.packet.ChunkSnapshotPacket;
import io.viki.rf.network.packet.LiquidUpdatePacket;
import io.viki.rf.network.packet.WorldInitPacket;
import io.viki.rf.network.packet.EntityPhysicsPacket;
import io.viki.rf.network.packet.EntityLifecyclePacket;
import io.viki.rf.network.packet.StepUpdatePacket;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import io.viki.rf.world.level.Chunk;
import io.viki.rf.world.level.FlatTerrainGenerator;
import io.viki.rf.world.level.ServerLevel;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.util.ChunkPos;
import io.viki.rf.world.util.PrecisePos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Dedicated authoritative world loop. Client code communicates with it only through packets. */
@SideOnly(dist = Dist.SERVER)
public final class GameServer implements AutoCloseable {
  public static final int INTEREST_RADIUS = 6;
  private static final int GROUND_Y = 20;
  private static final long WORLD_SEED = 42L;
  private static final double SPAWN_X = 0;
  private static final double SPAWN_Y = GROUND_Y + 1;
  private static @org.jspecify.annotations.Nullable GameServer current;
  private final ConnectionHost host;
  private final ServerLevel level = new ServerLevel(new FlatTerrainGenerator(GROUND_Y), WORLD_SEED);
  private final Map<UUID, ServerPlayer> players = new HashMap<>();
  private final Map<UUID, Map<Long, Long>> sentChunks = new HashMap<>();
  private final Map<UUID, Set<UUID>> trackedEntities = new HashMap<>();
  private final LongOpenHashSet changedLiquidChunks = new LongOpenHashSet();
  private final Map<UUID, Integer> worldActionsThisTick = new HashMap<>();
  private final ScheduledExecutorService ticks = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "reverie-server");
    thread.setDaemon(false);
    return thread;
  });

  public GameServer(ConnectionHost host) {
    this.host = host;
    level.setLiquidChangeListener(cell -> changedLiquidChunks.add(
        ChunkPos.packBlockPosAsLong((int) (cell >> 32), (int) cell)));
    host.onConnected(this::connected);
    host.onDisconnected(this::disconnected);
    host.onPacket((connection, packet) -> packet.handle(connection));
    current = this;
  }

  public void start() {
    host.start().join();
    long period = TimeUnit.SECONDS.toNanos(1) / GameConstants.TICKS_PER_SECOND;
    ticks.scheduleAtFixedRate(this::tickSafely, 0, period, TimeUnit.NANOSECONDS);
  }

  private void tickSafely() {
    try {
      worldActionsThisTick.clear();
      host.process();
      level.tick(1.0 / GameConstants.TICKS_PER_SECOND);
      publishTime();
      publishPlayers();
      publishLiquids();
      publishChunks();
    } catch (RuntimeException exception) {
      // Isolate a bad tick from the scheduler; malformed client packets are
      // already disconnected by the network host.
      exception.printStackTrace();
    }
  }

  private void publishTime() {
    double time = level.getTicks() / (double) GameConstants.TICKS_PER_SECOND;
    for (ServerPlayer player : players.values()) player.connection().send(new StepUpdatePacket(time));
  }

  private void connected(Connection connection) {
    var spawn = new PrecisePos(SPAWN_X, SPAWN_Y);
    Entity entity = Entity.player(spawn);
    entity.setEntityId(connection.netUuid());
    // Starbound's player is client-master. The server keeps this slave
    // replica for visibility and interaction, without integrating
    // a second copy of the player's movement.
    entity.setSimulated(false);
    entity.enterChunk(level);
    var player = new ServerPlayer(connection, entity);
    players.put(connection.netUuid(), player);
    sentChunks.put(connection.netUuid(), new HashMap<>());
    trackedEntities.put(connection.netUuid(), new HashSet<>());
    level.chunkManager().interest(connection.netUuid()).update(SPAWN_X, SPAWN_Y, INTEREST_RADIUS);
    int diameter = INTEREST_RADIUS * 2 + 1;
    connection.send(new WorldInitPacket(WORLD_SEED, diameter * diameter, SPAWN_X, SPAWN_Y));
  }

  private void disconnected(Connection connection) {
    players.remove(connection.netUuid());
    sentChunks.remove(connection.netUuid());
    trackedEntities.remove(connection.netUuid());
    worldActionsThisTick.remove(connection.netUuid());
    level.entities().remove(connection.netUuid());
    level.chunkManager().removeInterest(connection.netUuid());
  }


  private void publishPlayers() {
    for (ServerPlayer viewer : players.values()) {
      Set<UUID> visible = new HashSet<>();
      for (ChunkPos chunkPos : level.chunkManager().desiredChunks(viewer.netUuid())) {
        for (Entity entity : level.entities().inChunk(chunkPos)) {
          if (!entity.entityId().equals(viewer.netUuid())) visible.add(entity.entityId());
        }
      }
      Set<UUID> known = trackedEntities.get(viewer.netUuid());
      List<EntityLifecyclePacket.Spawn> spawns = new ArrayList<>();
      List<UUID> destroys = new ArrayList<>();
      for (UUID entityId : new HashSet<>(known)) {
        if (!visible.contains(entityId)) {
          destroys.add(entityId);
          known.remove(entityId);
        }
      }
      for (UUID entityId : visible) {
        Entity entity = level.entities().get(entityId);
        if (entity == null) continue;
        if (known.add(entityId)) {
          spawns.add(new EntityLifecyclePacket.Spawn(entityId, entity.type(),
              entity.position().xf(), entity.position().yf(), entity.facingAngle(),
              entity.velocity().x(), entity.velocity().y()));
        }
      }
      if (!spawns.isEmpty() || !destroys.isEmpty()) {
        viewer.connection().send(new EntityLifecyclePacket(currentTime(), spawns, destroys));
      }
      publishDirtyPhysics(viewer, visible);
    }
  }

  private void publishDirtyPhysics(ServerPlayer viewer, Set<UUID> visible) {
    List<EntityPhysicsPacket.State> states = new ArrayList<>(EntityPhysicsPacket.MAX_STATES);
    for (UUID entityId : visible) {
      Entity entity = level.entities().get(entityId);
      if (entity == null) {
        continue;
      }
      states.add(new EntityPhysicsPacket.State(entityId,
          entity.position().xf(), entity.position().yf(), entity.facingAngle(),
          entity.velocity().x(), entity.velocity().y()));
      if (states.size() == EntityPhysicsPacket.MAX_STATES) {
        sendPhysics(viewer, states);
        states = new ArrayList<>(EntityPhysicsPacket.MAX_STATES);
      }
    }
    if (!states.isEmpty()) sendPhysics(viewer, states);
  }

  private void sendPhysics(ServerPlayer viewer, List<EntityPhysicsPacket.State> states) {
    viewer.connection().send(new EntityPhysicsPacket(currentTime(), states));
  }

  private double currentTime() {
    return level.getTicks() / (double) GameConstants.TICKS_PER_SECOND;
  }

  private void publishLiquids() {
    if (changedLiquidChunks.isEmpty()) return;
    for (var iterator = changedLiquidChunks.iterator(); iterator.hasNext();) {
      long chunkKey = iterator.nextLong();
      Chunk chunk = level.getChunkByKey(chunkKey);
      if (chunk == null) continue;
      ChunkPos chunkPos = ChunkPos.fromLong(chunkKey);
      var packet = LiquidUpdatePacket.capture(chunk);
      for (ServerPlayer player : players.values()) {
        Map<Long, Long> sent = sentChunks.get(player.netUuid());
        if (sent.containsKey(chunkKey)
            && level.chunkManager().isInterested(player.netUuid(), chunkPos)) {
          player.connection().send(packet);
        }
      }
    }
    changedLiquidChunks.clear();
  }

  private void publishChunks() {
    for (ServerPlayer player : players.values()) {
      Map<Long, Long> sent = sentChunks.get(player.netUuid());
      var desired = level.chunkManager().desiredChunks(player.netUuid());
      Set<Long> desiredKeys = desired.stream().map(ChunkPos::asLong).collect(Collectors.toSet());
      sent.keySet().retainAll(desiredKeys);
      for (ChunkPos pos : desired) {
        Chunk chunk = level.getChunk(pos);
        if (chunk != null
            && sent.getOrDefault(pos.asLong(), -1L) != chunk.terrainRevision()) {
          player.connection().send(snapshot(chunk));
          sent.put(pos.asLong(), chunk.terrainRevision());
        }
      }
    }
  }

  private static ChunkSnapshotPacket snapshot(Chunk chunk) {
    return new ChunkSnapshotPacket(chunk.serialize());
  }

  public static GameServer current() {
    GameServer server = current;
    if (server == null) throw new IllegalStateException("No game server is running");
    return server;
  }

  public static ServerLevel level() { return current().level; }
  public static ConnectionHost host() { return current().host; }
  public static @org.jspecify.annotations.Nullable Entity player(UUID id) {
    ServerPlayer player = current().players.get(id);
    return player == null ? null : player.entity();
  }
  public static Map<UUID, Map<Long, Long>> sentChunks() { return current().sentChunks; }
  public static Map<UUID, Integer> worldActionsThisTick() { return current().worldActionsThisTick; }

  @Override
  public void close() {
    ticks.shutdownNow();
    host.close();
    level.close();
    if (current == this) current = null;
  }
}
