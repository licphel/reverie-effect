package io.viki.rf.network;

import io.viki.rf.server.GameServer;

/** Starts an isolated in-process server and exposes only its client transport. */
public final class IntegratedBootstrap {
  private IntegratedBootstrap() {
  }

  public static Session start() {
    IntegratedConnection transport = IntegratedConnection.create();
    transport.client().start().join();
    var server = new GameServer(transport.server());
    server.start();
    return new Session(transport.client(), server);
  }

  public record Session(ConnectionHost client, AutoCloseable server) implements AutoCloseable {
    @Override
    public void close() {
      client.close();
      try {
        server.close();
      } catch (Exception exception) {
        throw new IllegalStateException("Failed to close integrated server", exception);
      }
    }
  }
}
