package io.viki.rf.network;

import io.viki.rf.server.GameServer;

/** Process-level LAN startup; neither side receives the other side's Level. */
public final class LanBootstrap {
  private LanBootstrap() {
  }

  public static Session host(int port) {
    var server = new GameServer(Connection.createLAN(port));
    server.start();
    return new Session(Connection.createIPAddr("127.0.0.1", port), server);
  }

  public record Session(ConnectionHost client, AutoCloseable server) implements AutoCloseable {
    @Override
    public void close() {
      client.close();
      try {
        server.close();
      } catch (Exception exception) {
        throw new IllegalStateException("Failed to close LAN server", exception);
      }
    }
  }
}
