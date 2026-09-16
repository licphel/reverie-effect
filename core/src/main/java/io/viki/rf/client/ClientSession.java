package io.viki.rf.client;

import io.viki.rf.network.Connection;
import io.viki.rf.network.ConnectionHost;
import io.viki.rf.network.packet.Packet;
import io.viki.rf.network.packet.PlayerPositionPacket;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.ClientLevel;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Client-only world mirror and connection state machine. */
public final class ClientSession implements AutoCloseable {
  private final ConnectionHost host;
  private final ClientLevel level;
  private final Entity player;
  private @Nullable Connection connection;

  ClientSession(ConnectionHost host, ClientLevel level, Entity player) {
    this.host = Objects.requireNonNull(host, "host");
    this.level = Objects.requireNonNull(level, "level");
    this.player = Objects.requireNonNull(player, "player");
    host.onConnected(this::connected);
    host.onDisconnected(this::disconnected);
    host.onPacket((connection, packet) -> packet.handle(connection));
    if (!host.isRunning()) {
      host.start();
    }
    host.connections().stream().findFirst().ifPresent(this::connected);
  }

  ClientLevel level() { return level; }
  Entity player() { return player; }
  boolean ready() { return level.ready(); }

  void send(Packet packet) {
    Connection current = connection;
    if (current == null) throw new IllegalStateException("Client is not connected");
    current.send(packet);
  }

  void tick(double delta) {
    host.process();
    if (ready()) {
      level.tick(delta);
      level.interpolationTracker().tick(delta, level);
      Connection current = connection;
      if (current != null) {
        current.send(new PlayerPositionPacket(player.position().xf(),
            player.position().yf(), player.velocity().x(), player.velocity().y()));
      }
    }
  }

  private void connected(Connection connection) {
    this.connection = connection;
    GameClient.bindNetworkWorld(level, player);
    level.bindNetworkState(new InterpolationTracker());
    player.setEntityId(connection.netUuid());
  }

  private void disconnected(Connection disconnected) {
    Connection current = connection;
    if (current == null || !current.netUuid().equals(disconnected.netUuid())) return;
    connection = null;
    level.clearNetworkState();
    GameClient.clearNetworkWorld(level);
    System.err.println("[reverie-client] Connection closed: " + disconnected.netUuid());
  }

  @Override
  public void close() {
    host.close();
    level.close();
  }

}
