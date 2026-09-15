package io.viki.rf.client;

import io.viki.rf.network.Connection;
import io.viki.rf.network.IntegratedBootstrap;
import io.viki.rf.network.LanBootstrap;
import io.viki.rf.network.packet.Packet;
import io.viki.rf.ui.TitleScreen;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.util.PrecisePos;
import org.jspecify.annotations.Nullable;

/** Owns only client world state plus opaque process/transport lifetimes. */
final class DemoWorld implements AutoCloseable {
  private static final int LAN_PORT = 12_345;
  private final ClientLevel level = new ClientLevel(0);
  private final Entity player = Entity.player(new PrecisePos(0, 21));
  private @Nullable ClientSession client;
  private @Nullable AutoCloseable server;

  static DemoWorld open() {
    return new DemoWorld();
  }

  void start(TitleScreen.StartMode mode) {
    if (client != null) {
      return;
    }
    switch (mode) {
      case SINGLE_PLAYER -> {
        IntegratedBootstrap.Session process = IntegratedBootstrap.start();
        server = process;
        client = new ClientSession(process.client(), level, player);
      }
      case HOST_LAN -> {
        LanBootstrap.Session process = LanBootstrap.host(LAN_PORT);
        server = process;
        client = new ClientSession(process.client(), level, player);
      }
      case JOIN_LAN ->
          client = new ClientSession(Connection.createIPAddr("127.0.0.1", LAN_PORT), level, player);
    }
  }

  ClientLevel level() { return level; }
  Entity player() { return player; }
  boolean started() { return client != null; }
  boolean ready() { return client != null && client.ready(); }

  void updateInterest() {
  }

  void tick(double delta) {
    ClientSession current = client;
    if (current != null) {
      current.tick(delta);
    }
  }

  void send(Packet packet) {
    ClientSession current = client;
    if (current == null) throw new IllegalStateException("Client session has not started");
    current.send(packet);
  }

  @Override
  public void close() {
    ClientSession current = client;
    if (current != null) {
      current.close();
    }
    AutoCloseable currentServer = server;
    if (currentServer != null) {
      try {
        currentServer.close();
      } catch (Exception exception) {
        throw new IllegalStateException("Failed to close game server", exception);
      }
    }
    level.close();
  }
}
