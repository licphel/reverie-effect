/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.rf.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.viki.rf.network.codec.PacketDecoder;
import io.viki.rf.network.codec.PacketEncoder;
import io.viki.rf.network.codec.RealtimePacketCodec;
import io.viki.rf.network.packet.HeartbeatPacket;
import io.viki.rf.network.packet.Packet;
import io.viki.rf.network.packet.ConnectionAckPacket;
import io.viki.rf.network.packet.ConnectionOpenPacket;
import io.viki.rf.network.packet.PacketDelivery;
import io.viki.rf.network.packet.RealtimeHelloPacket;
import io.viki.rf.network.packet.RealtimeWelcomePacket;
import io.viki.rf.annotation.Dist;
import io.viki.rf.annotation.SideOnly;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A server-side network endpoint that binds to a port, accepts client connections, and manages sessions.
 *
 * <p>Each accepted TCP channel begins a handshake: the server assigns the connection's shared identifier and sends it
 * in a {@link SessionOpenPacket}; the connection only becomes established — and {@code onConnected} only fires — when
 * the client answers with a {@link SessionAckPacket} echoing the same UUID. Until then the session reports
 * {@link SessionState#CONNECTING}, is not {@link Connection#isActive() active}, its packets are dropped, and it is
 * evicted
 * silently after {@link ServerConnection#HANDSHAKE_TIMEOUT_MS}.
 *
 * <h3>Heartbeat and timeouts</h3>
 * <p>The server broadcasts heartbeat packets at the interval defined by
 * {@link HeartbeatPacket#HEARTBEAT_INTERVAL_MS}.
 * Sessions that remain inactive for longer than {@link ServerConnection#SESSION_TIMEOUT_MS} are automatically
 * disconnected.
 *
 * <p>This class is thread-safe. {@link #send(Packet)} and {@link #send(UUID, Packet)} may be called from any thread,
 * while {@link #process()} should be called from a single dedicated thread.
 *
 * @see Connection
 * @see Connection
 */
@SideOnly(dist = Dist.SERVER)
public final class ServerConnection implements ConnectionHost {
  /** Maximum wire-frame size in bytes. */
  public static final int FRAME_MAX_SIZE = 2 * 1024 * 1024;
  private static final int MAX_GLOBAL_QUEUED_PACKETS = 8_192;
  private static final int MAX_QUEUED_PACKETS_PER_CONNECTION = 256;
  private static final int MAX_PACKETS_PER_TICK = 1_024;
  private static final int MAX_PACKETS_PER_SECOND_PER_CONNECTION = 240;
  private static final long RATE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);
  /**
   * Maximum permitted session inactivity in milliseconds. Sessions that have not sent or received data within this
   * window are forcibly disconnected.
   */
  public static final long SESSION_TIMEOUT_MS = 30_000;
  /**
   * Handshake timeout in milliseconds. A connection that has not completed the session-open/session-ack exchange within
   * this window of the TCP channel becoming active is aborted and — because agreement was never reached — never
   * surfaces as a connected session.
   */
  public static final long HANDSHAKE_TIMEOUT_MS = 10_000;
  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  private final ChannelGroup channels;
  private final ConcurrentLinkedQueue<PacketWithSession> inbound;
  private final AtomicInteger queuedPackets = new AtomicInteger();
  private final ConcurrentHashMap<UUID, NettySession> sessions;
  private final ConcurrentLinkedQueue<Connection> connectEvents;
  private final ConcurrentLinkedQueue<Connection> disconnectEvents;
  private final String bindHost;
  private final int bindPort;

  private @Nullable Channel serverChannel;
  private @Nullable Channel realtimeChannel;
  private volatile boolean running;
  private volatile long lastHeartbeatSent;

  private @Nullable Consumer<Connection> onConnected;
  private @Nullable Consumer<Connection> onDisconnected;
  private @Nullable BiConsumer<Connection, Packet> onPacket;

  /**
   * Creates a new, unbound server.
   */
  public ServerConnection() {
    this("", -1);
  }

  private ServerConnection(String bindHost, int bindPort) {
    this.bindHost = bindHost;
    this.bindPort = bindPort;
    this.bossGroup = new NioEventLoopGroup(1);
    this.workerGroup = new NioEventLoopGroup();
    this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    this.inbound = new ConcurrentLinkedQueue<>();
    this.sessions = new ConcurrentHashMap<>();
    this.connectEvents = new ConcurrentLinkedQueue<>();
    this.disconnectEvents = new ConcurrentLinkedQueue<>();
  }

  static ServerConnection configured(String host, int port) {
    if (host.isBlank()) {
      throw new IllegalArgumentException("Bind host must not be blank");
    }
    if (port < 0 || port > 65_535) {
      throw new IllegalArgumentException("Port must be within [0, 65535]: " + port);
    }
    return new ServerConnection(host, port);
  }

  @Override
  public CompletableFuture<Void> start() {
    if (bindPort < 0) {
      throw new IllegalStateException("ServerConnection has no configured bind address");
    }
    return bind(bindHost, bindPort);
  }

  /**
   * Binds to a port and begins accepting client connections asynchronously.
   *
   * @param port the TCP port to bind to
   * @return a future that completes when the server is bound and listening
   * @throws NetworkException if the server is already running
   */
  @SuppressWarnings("all")
  public CompletableFuture<Void> bind(int port) {
    return bind("0.0.0.0", port);
  }

  private CompletableFuture<Void> bind(String host, int port) {
    if (running) {
      throw new NetworkException("Server is already running");
    }

    CompletableFuture<Void> future = new CompletableFuture<>();

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childHandler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        ChannelPipeline p = ch.pipeline();
        // Outbound traversal is tail→head, so the encoders must sit head-side of
        // the session handler: writes from sessionHandler pass packetEncoder
        // (Packet→bytes) then frameEncoder (prepend length) before the socket.
        // inbound
        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(FRAME_MAX_SIZE, 0, 4, 0, 4));
        p.addLast("packetDecoder", new PacketDecoder());
        // outbound
        p.addLast("frameEncoder", new LengthFieldPrepender(4));
        p.addLast("packetEncoder", new PacketEncoder());
        // business
        p.addLast("sessionHandler", new ServerSessionHandler());
      }
    });

    bootstrap.bind(host, port).addListener((ChannelFutureListener) f -> {
      if (f.isSuccess()) {
        serverChannel = f.channel();
        bindRealtime(host, port, future);
      } else {
        Throwable cause = f.cause();
        future.completeExceptionally(cause != null ? cause : new NetworkException("Failed to bind to port " + port));
      }
    });

    return future;
  }

  private void bindRealtime(String host, int port, CompletableFuture<Void> future) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(workerGroup)
        .channel(NioDatagramChannel.class)
        .handler(new ServerDatagramHandler());
    bootstrap.bind(host, port).addListener((ChannelFutureListener) result -> {
      if (result.isSuccess()) {
        realtimeChannel = result.channel();
        running = true;
        lastHeartbeatSent = System.currentTimeMillis();
        future.complete(null);
      } else {
        // UDP is an optional acceleration path. Keep the reliable server alive
        // and let all realtime packets fall back to TCP.
        running = true;
        lastHeartbeatSent = System.currentTimeMillis();
        future.complete(null);
      }
    });
  }

  /**
   * Broadcasts a packet to every connected session.
   *
   * @param packet the packet to broadcast
   */
  public void send(Packet packet) {
    requireServerOutbound(packet);
    for (NettySession session : sessions.values()) {
      if (session.isActive()) {
        session.send(packet);
      }
    }
  }

  /**
   * Sends a packet to a single session identified by its identifier.
   *
   * @param sessionId the target session identifier
   * @param packet    the packet to send
   */
  public void send(UUID sessionId, Packet packet) {
    requireServerOutbound(packet);
    NettySession session = sessions.get(sessionId);
    if (session != null && session.isActive()) {
      session.send(packet);
    }
  }

  /**
   * Processes a single tick: drains the inbound packet queue, fires pending lifecycle events, transmits heartbeats, and
   * evicts stale or never-established sessions.
   *
   * <p>This method should be called once per frame from the main thread. Inbound packets receive their
   * {@link Packet#handle(Session)} call on the calling thread.
   */
  public void process() {
    Connection s;
    while ((s = connectEvents.poll()) != null) {
      Consumer<Connection> cb = onConnected;
      if (cb != null) {
        cb.accept(s);
      }
    }
    while ((s = disconnectEvents.poll()) != null) {
      Consumer<Connection> cb = onDisconnected;
      if (cb != null) {
        cb.accept(s);
      }
    }

    PacketWithSession entry;
    int processed = 0;
    while (processed < MAX_PACKETS_PER_TICK && (entry = inbound.poll()) != null) {
      queuedPackets.decrementAndGet();
      entry.connection.releaseQueuedPacket();
      processed++;
      try {
        BiConsumer<Connection, Packet> callback = onPacket;
        if (callback != null) {
          callback.accept(entry.connection, entry.packet);
        } else {
          entry.packet.handle(entry.connection);
        }
      } catch (RuntimeException exception) {
        entry.connection.close();
      }
    }

    long now = System.currentTimeMillis();
    if (now - lastHeartbeatSent >= HeartbeatPacket.HEARTBEAT_INTERVAL_MS) {
      send(new HeartbeatPacket());
      lastHeartbeatSent = now;
    }

    Iterator<NettySession> iter = sessions.values().iterator();
    while (iter.hasNext()) {
      NettySession session = iter.next();
      // Channel liveness (not Session.isActive(): a handshaking session is not
      // yet active but its channel is very much alive) decides eviction.
      if (!session.channel.isActive()) {
        iter.remove();
        channels.remove(session.channel);
        if (session.established) {
          disconnectEvents.add(session);
        }
      } else if (!session.established && now - session.createdAt > HANDSHAKE_TIMEOUT_MS) {
        // Handshake never finished: agreement was never reached, so evict
        // silently — no onConnected/onDisconnected pair ever fires.
        session.close();
        iter.remove();
        channels.remove(session.channel);
      } else if (session.established && now - session.lastActivityTime() > SESSION_TIMEOUT_MS) {
        session.close();
        iter.remove();
        channels.remove(session.channel);
        disconnectEvents.add(session);
      }
    }
  }

  /**
   * Registers a callback invoked when a new session is established.
   *
   * <p>The callback fires during {@link #process()} on the calling thread, after the handshake completed and the client
   * acknowledged the shared connection identifier.
   *
   * @param callback the callback to invoke for each new connection
   */
  public void onConnected(Consumer<Connection> callback) {
    this.onConnected = callback;
  }

  /**
   * Registers a callback invoked when a session is disconnected.
   *
   * <p>The callback fires during {@link #process()} on the calling thread.
   *
   * @param callback the callback to invoke for each disconnection
   */
  public void onDisconnected(Consumer<Connection> callback) {
    this.onDisconnected = callback;
  }

  @Override
  public void onPacket(BiConsumer<Connection, Packet> callback) {
    this.onPacket = callback;
  }

  /**
   * Returns a snapshot of all currently connected sessions.
   *
   * @return an unmodifiable collection of sessions
   */
  public Collection<Connection> connections() {
    List<Connection> result = new ArrayList<>();
    for (NettySession connection : sessions.values()) {
      if (connection.isActive()) {
        result.add(connection);
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public @Nullable Connection get(UUID netUuid) {
    NettySession connection = sessions.get(netUuid);
    return connection != null && connection.isActive() ? connection : null;
  }

  private static void requireServerOutbound(Packet packet) {
    if (!packet.flow().acceptedByClient()) {
      throw new IllegalArgumentException(
          "Server cannot send " + packet.getClass().getSimpleName() + " with flow " + packet.flow());
    }
  }

  /**
   * Forcefully disconnects the session with the given identifier.
   *
   * @param sessionId the session to disconnect
   */
  public void kick(UUID sessionId) {
    NettySession session = sessions.get(sessionId);
    if (session != null) {
      session.close();
    }
  }

  /**
   * Returns whether the server is currently bound and accepting connections.
   *
   * @return {@code true} if the server is running
   */
  public boolean isRunning() {
    return running;
  }

  @Override
  public void close() {
    running = false;
    channels.close().awaitUninterruptibly(2000);
    if (serverChannel != null) {
      serverChannel.close().awaitUninterruptibly(2000);
    }
    if (realtimeChannel != null) {
      realtimeChannel.close().awaitUninterruptibly(2000);
    }
    bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    sessions.clear();
  }

  private record PacketWithSession(Packet packet, NettySession connection) {
  }

  private final class NettySession implements Connection {
    private final UUID id;
    private final Channel channel;
    private final long createdAt;
    private final UUID realtimeToken = UUID.randomUUID();
    private volatile long lastActivity;
    private final AtomicInteger queuedPackets = new AtomicInteger();
    private volatile @Nullable InetSocketAddress realtimeAddress;
    private long rateWindowStarted = System.nanoTime();
    private int packetsInRateWindow;
    /** Whether the client acknowledged the handshake; set once on the I/O thread, read from any thread. */
    private volatile boolean established;

    NettySession(UUID id, Channel channel) {
      this.id = id;
      this.channel = channel;
      this.createdAt = System.currentTimeMillis();
      this.lastActivity = createdAt;
    }

    void touch() {
      lastActivity = System.currentTimeMillis();
    }

    synchronized boolean reserveInboundPacket() {
      long now = System.nanoTime();
      if (now - rateWindowStarted >= RATE_WINDOW_NANOS) {
        rateWindowStarted = now;
        packetsInRateWindow = 0;
      }
      if (++packetsInRateWindow > MAX_PACKETS_PER_SECOND_PER_CONNECTION) {
        return false;
      }
      if (queuedPackets.incrementAndGet() > MAX_QUEUED_PACKETS_PER_CONNECTION) {
        queuedPackets.decrementAndGet();
        return false;
      }
      return true;
    }

    void releaseQueuedPacket() {
      queuedPackets.updateAndGet(count -> Math.max(0, count - 1));
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public void send(Packet packet) {
      Channel udp = realtimeChannel;
      InetSocketAddress address = realtimeAddress;
      if (packet.delivery() == PacketDelivery.UNRELIABLE_SEQUENCED
          && udp != null && udp.isActive() && address != null) {
        ByteBuf payload = RealtimePacketCodec.encode(
            udp.alloc(), id, realtimeToken, packet);
        udp.writeAndFlush(new DatagramPacket(payload, address));
        touch();
        return;
      }
      if (channel.isActive()) {
        channel.writeAndFlush(packet);
        touch();
      }
    }

    @Override
    public void close() {
      channel.close();
    }

    @Override
    public boolean isActive() {
      // A session is only usable once both endpoints agreed on the identifier.
      return established && channel.isActive();
    }

    @Override
    public ConnectionState state() {
      if (!established) {
        return ConnectionState.CONNECTING;
      }
      if (channel.isActive()) {
        return ConnectionState.CONNECTED;
      }
      if (channel.isOpen()) {
        return ConnectionState.DISCONNECTING;
      }
      return ConnectionState.DISCONNECTED;
    }

    @Override
    public long lastActivityTime() {
      return lastActivity;
    }

    @Override
    public String toString() {
      return "Session[" + id + " " + state() + "]";
    }
  }

  private final class ServerSessionHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      Channel ch = ctx.channel();
      channels.add(ch);
      UUID connId = UUID.randomUUID();
      NettySession session = new NettySession(connId, ch);
      sessions.put(connId, session);

      // Assign the shared connection identifier; the session stays CONNECTING
      // until the client echoes it back in a SessionAckPacket. Write failures
      // surface on the promise, not via exceptionCaught — log them or they
      // vanish silently.
      ctx.writeAndFlush(new ConnectionOpenPacket(connId, session.realtimeToken))
          .addListener((ChannelFutureListener) f -> {
        if (!f.isSuccess()) {
          System.err.println("[momentum-net/server] session-open write failed: " + f.cause());
        }
      });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof ConnectionAckPacket ack) {
        NettySession session = sessionFor(ctx.channel());
        if (session == null) {
          return;
        }
        if (!session.established && ack.connId().equals(session.id)) {
          session.established = true;
          connectEvents.add(session);
        } else if (!ack.connId().equals(session.id)) {
          // Echo mismatch: the protocol is broken, abort the connection.
          ctx.close();
        }
        return;
      }
      if (msg instanceof Packet packet) {
        NettySession session = sessionFor(ctx.channel());
        if (session != null && session.established) {
          int globalQueued = queuedPackets.incrementAndGet();
          if (!packet.flow().acceptedByServer()
              || globalQueued > MAX_GLOBAL_QUEUED_PACKETS
              || !session.reserveInboundPacket()) {
            queuedPackets.decrementAndGet();
            ctx.close();
            return;
          }
          session.touch();
          inbound.add(new PacketWithSession(packet, session));
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
    }

    private @Nullable NettySession sessionFor(Channel ch) {
      for (NettySession s : sessions.values()) {
        if (s.channel == ch) {
          return s;
        }
      }
      return null;
    }
  }

  private final class ServerDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    protected void channelRead0(ChannelHandlerContext context, DatagramPacket datagram) {
      try {
        RealtimePacketCodec.Decoded decoded = RealtimePacketCodec.decode(datagram.content());
        NettySession session = sessions.get(decoded.connectionId());
        if (session == null || !session.established
            || !session.realtimeToken.equals(decoded.token())
            || !decoded.packet().flow().acceptedByServer()) {
          return;
        }
        session.realtimeAddress = datagram.sender();
        session.touch();
        if (decoded.packet() instanceof RealtimeHelloPacket) {
          session.send(new RealtimeWelcomePacket());
          return;
        }
        int globalQueued = queuedPackets.incrementAndGet();
        if (globalQueued > MAX_GLOBAL_QUEUED_PACKETS || !session.reserveInboundPacket()) {
          queuedPackets.decrementAndGet();
          return;
        }
        inbound.add(new PacketWithSession(decoded.packet(), session));
      } catch (RuntimeException ignored) {
        // UDP is untrusted and connectionless. Invalid datagrams are dropped
        // without affecting the authenticated TCP session.
      }
    }
  }
}
