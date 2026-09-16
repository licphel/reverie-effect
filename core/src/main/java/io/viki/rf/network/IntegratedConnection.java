package io.viki.rf.network;

import io.viki.rf.network.packet.Packet;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** In-process client/server transport used by single-player without bypassing packets. */
public final class IntegratedConnection implements AutoCloseable {
  private final Endpoint server;
  private final Endpoint client;

  private IntegratedConnection() {
    UUID id = UUID.randomUUID();
    server = new Endpoint(id);
    client = new Endpoint(id);
    server.peer = client.connection;
    client.peer = server.connection;
  }

  public static IntegratedConnection create() {
    return new IntegratedConnection();
  }

  public ConnectionHost server() {
    return server;
  }

  public ConnectionHost client() {
    return client;
  }

  @Override
  public void close() {
    client.close();
    server.close();
  }

  private static final class Endpoint implements ConnectionHost {
    private final LocalConnection connection;
    private final ConcurrentLinkedQueue<Packet> inbound = new ConcurrentLinkedQueue<>();
    private @Nullable LocalConnection peer;
    private @Nullable BiConsumer<Connection, Packet> onPacket;
    private @Nullable Consumer<Connection> onConnected;
    private @Nullable Consumer<Connection> onDisconnected;
    private boolean running;

    private Endpoint(UUID id) {
      connection = new LocalConnection(id, this);
    }

    @Override
    public CompletableFuture<Void> start() {
      running = true;
      Consumer<Connection> callback = onConnected;
      if (callback != null) {
        callback.accept(connection);
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public @Nullable Connection get(UUID netUuid) {
      return running && connection.id().equals(netUuid) ? connection : null;
    }

    @Override
    public Collection<Connection> connections() {
      return running ? List.of(connection) : List.of();
    }

    @Override
    public void process() {
      Packet packet;
      while ((packet = inbound.poll()) != null) {
        BiConsumer<Connection, Packet> callback = onPacket;
        if (callback != null) {
          callback.accept(connection, packet);
        } else {
          packet.handle(connection);
        }
      }
    }

    @Override
    public void onConnected(Consumer<Connection> callback) {
      onConnected = callback;
    }

    @Override
    public void onDisconnected(Consumer<Connection> callback) {
      onDisconnected = callback;
    }

    @Override
    public void onPacket(BiConsumer<Connection, Packet> callback) {
      onPacket = callback;
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    @Override
    public void close() {
      if (!running) {
        return;
      }
      running = false;
      Consumer<Connection> callback = onDisconnected;
      if (callback != null) {
        callback.accept(connection);
      }
      inbound.clear();
    }
  }

  private static final class LocalConnection implements Connection {
    private final UUID id;
    private final Endpoint owner;
    private long lastActivity = System.currentTimeMillis();

    private LocalConnection(UUID id, Endpoint owner) {
      this.id = id;
      this.owner = owner;
    }

    @Override public UUID id() { return id; }

    @Override
    public void send(Packet packet) {
      Endpoint target = owner.peer == null ? null : owner.peer.owner;
      if (target != null && target.running) {
        target.inbound.add(packet);
        lastActivity = System.currentTimeMillis();
      }
    }

    @Override public void close() { owner.close(); }
    @Override public boolean isActive() { return owner.running; }
    @Override public ConnectionState state() {
      return owner.running ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
    }
    @Override public long lastActivityTime() { return lastActivity; }
  }
}
