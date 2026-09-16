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
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A client-side network endpoint that manages a single connection to a remote server.
 *
 * <p>The connection identifier is assigned by the server during the handshake: the server sends a
 * {@link SessionOpenPacket}, the client adopts the carried UUID as its own session id and answers with a session ack
 * echoing it back. Until that exchange completes, the connection is not established — {@link #state()} reports
 * {@link SessionState#CONNECTING}, {@link #session()} is empty, and no lifecycle callback has fired.
 *
 * <p>The typical lifecycle is: register lifecycle callbacks, call {@link #connect(String, int)}, then invoke
 * {@link #process()} each frame to drain inbound packets and fire lifecycle events.
 *
 * <p>Outbound packets may be sent from any thread via {@link #send(Packet)} or through the session object. Inbound
 * packet handling and lifecycle callbacks always execute on the thread that calls {@link #process()}.
 *
 * <p>This class is thread-safe for sending. {@link #process()} should be called from a single dedicated thread.
 *
 * @see ServerConnection
 * @see Connection
 */
@SideOnly(dist = Dist.CLIENT)
public final class ClientConnection implements ConnectionHost {
  /** Maximum wire-frame size in bytes. */
  public static final int FRAME_MAX_SIZE = 2 * 1024 * 1024;
  private static final int MAX_QUEUED_PACKETS = 2_048;
  private static final int MAX_PACKETS_PER_TICK = 512;
  private static final long REALTIME_TIMEOUT_MS = 2_000;
  private static final long REALTIME_PROBE_INTERVAL_MS = 1_000;

  private final EventLoopGroup group;
  private final ConcurrentLinkedQueue<Packet> inbound;
  private final AtomicInteger queuedPackets = new AtomicInteger();
  private final ConcurrentLinkedQueue<Connection> connectEvents;
  private final ConcurrentLinkedQueue<Connection> disconnectEvents;
  private final String targetHost;
  private final int targetPort;

  private volatile @Nullable Channel channel;
  private volatile @Nullable Channel realtimeChannel;
  private volatile @Nullable NettySession session;
  private volatile boolean realtimeReady;
  private volatile long lastRealtimeReceived;
  private volatile long lastRealtimeProbe;
  private volatile ConnectionState state = ConnectionState.DISCONNECTED;
  private volatile long lastHeartbeatSent;

  private @Nullable Consumer<Connection> onConnected;
  private @Nullable Consumer<Connection> onDisconnected;
  private @Nullable BiConsumer<Connection, Packet> onPacket;

  /**
   * Creates a network client, unconnected.
   */
  public ClientConnection() {
    this("", -1);
  }

  private ClientConnection(String targetHost, int targetPort) {
    this.targetHost = targetHost;
    this.targetPort = targetPort;
    this.group = new NioEventLoopGroup(1);
    this.inbound = new ConcurrentLinkedQueue<>();
    this.connectEvents = new ConcurrentLinkedQueue<>();
    this.disconnectEvents = new ConcurrentLinkedQueue<>();
  }

  static ClientConnection configured(String host, int port) {
    if (host.isBlank()) {
      throw new IllegalArgumentException("Host must not be blank");
    }
    if (port < 0 || port > 65_535) {
      throw new IllegalArgumentException("Port must be within [0, 65535]: " + port);
    }
    return new ClientConnection(host, port);
  }

  @Override
  public CompletableFuture<Void> start() {
    if (targetPort < 0) {
      throw new IllegalStateException("ClientConnection has no configured remote address");
    }
    return connect(targetHost, targetPort);
  }

  /**
   * Initiates an asynchronous connection to a server.
   *
   * <p>The returned future completes only when the handshake finishes — that is, when the server-assigned connection
   * identifier has been received, adopted, and acknowledged — not when the TCP channel alone is open.
   *
   * @param host the server hostname or IP address
   * @param port the server port
   * @return a future that completes when the connection is fully established
   * @throws NetworkException if the client is already connecting or connected
   */
  @SuppressWarnings("all")
  public CompletableFuture<Void> connect(String host, int port) {
    if (state != ConnectionState.DISCONNECTED) {
      throw new NetworkException("Client is already connecting or connected");
    }
    state = ConnectionState.CONNECTING;

    PacketDecoder decoder = new PacketDecoder();
    PacketEncoder encoder = new PacketEncoder();

    CompletableFuture<Void> future = new CompletableFuture<>();
    connectRealtime(host, port);

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .handler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        ChannelPipeline p = ch.pipeline();
        // Outbound traversal is tail→head, so the encoders must sit head-side of
        // the session handler: writes from sessionHandler pass packetEncoder
        // (Packet→bytes) then frameEncoder (prepend length) before the socket.
        // inbound
        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(FRAME_MAX_SIZE, 0, 4, 0, 4));
        p.addLast("packetDecoder", decoder);
        // outbound
        p.addLast("frameEncoder", new LengthFieldPrepender(4));
        p.addLast("packetEncoder", encoder);
        // business
        p.addLast("sessionHandler", new ClientSessionHandler(future));
      }
    });

    bootstrap.connect(host, port).addListener((ChannelFutureListener) f -> {
      if (!f.isSuccess()) {
        state = ConnectionState.DISCONNECTED;
        Throwable cause = f.cause();
        future.completeExceptionally(cause != null ? cause : new ConnectException("Connection refused"));
      }
      // On success the handshake continues: the server sends SessionOpenPacket,
      // ClientSessionHandler completes the future when the exchange finishes.
    });

    return future;
  }

  private void connectRealtime(String host, int port) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
        .channel(NioDatagramChannel.class)
        .handler(new ClientDatagramHandler());
    bootstrap.connect(host, port).addListener((ChannelFutureListener) result -> {
      if (!result.isSuccess()) return;
      realtimeChannel = result.channel();
      NettySession current = session;
      if (current != null) current.send(new RealtimeHelloPacket());
    });
  }

  /**
   * Sends a packet to the server.
   *
   * <p>The call returns immediately; the packet is enqueued for asynchronous transmission. If the client is not
   * connected, the packet is silently dropped.
   *
   * @param packet the packet to send
   */
  public void send(Packet packet) {
    if (!packet.flow().acceptedByServer()) {
      throw new IllegalArgumentException(
          "Client cannot send " + packet.getClass().getSimpleName() + " with flow " + packet.flow());
    }
    NettySession current = session;
    if (current != null && current.isActive()) {
      current.send(packet);
    }
  }

  /**
   * Processes a single tick: drains the inbound packet queue, fires pending lifecycle events, and transmits
   * heartbeats.
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

    Packet packet;
    int processed = 0;
    while (processed < MAX_PACKETS_PER_TICK && (packet = inbound.poll()) != null) {
      queuedPackets.decrementAndGet();
      processed++;
      Connection sess = this.session;
      if (sess != null) {
        try {
          BiConsumer<Connection, Packet> callback = onPacket;
          if (callback != null) {
            callback.accept(sess, packet);
          } else {
            packet.handle(sess);
          }
        } catch (RuntimeException exception) {
          sess.close();
          break;
        }
      }
    }

    Connection sess = this.session;
    if (sess != null && sess.isActive()) {
      long now = System.currentTimeMillis();
      if (realtimeReady && now - lastRealtimeReceived > REALTIME_TIMEOUT_MS) {
        realtimeReady = false;
      }
      if (!realtimeReady && now - lastRealtimeProbe >= REALTIME_PROBE_INTERVAL_MS) {
        sess.send(new RealtimeHelloPacket());
        lastRealtimeProbe = now;
      }
      if (now - lastHeartbeatSent >= HeartbeatPacket.HEARTBEAT_INTERVAL_MS) {
        send(new HeartbeatPacket());
        lastHeartbeatSent = now;
      }
    }
  }

  /**
   * Registers a callback invoked when the connection to the server is established.
   *
   * <p>The callback fires during {@link #process()} on the calling thread, after the handshake completed and both
   * endpoints agreed on the connection identifier.
   *
   * @param callback the callback to invoke on connection
   */
  public void onConnected(Consumer<Connection> callback) {
    this.onConnected = callback;
  }

  /**
   * Registers a callback invoked when the connection to the server is lost.
   *
   * <p>The callback fires during {@link #process()} on the calling thread.
   *
   * @param callback the callback to invoke on disconnection
   */
  public void onDisconnected(Consumer<Connection> callback) {
    this.onDisconnected = callback;
  }

  @Override
  public void onPacket(BiConsumer<Connection, Packet> callback) {
    this.onPacket = callback;
  }

  /**
   * Returns the current session if the handshake has completed.
   *
   * @return the session if active, otherwise an empty {@link Optional}
   */
  public Optional<Connection> connection() {
    Connection s = this.session;
    return s != null && s.isActive() ? Optional.of(s) : Optional.empty();
  }

  /**
   * Returns the current lifecycle state of this client.
   *
   * <p>The state is {@link SessionState#CONNECTING} from the moment {@link #connect(String, int)} is called until the
   * handshake finishes, so a session identifier is only ever observable in {@link SessionState#CONNECTED}.
   *
   * @return the client state
   */
  public ConnectionState state() {
    return state;
  }

  @Override
  public void close() {
    Channel ch = this.channel;
    if (ch != null) {
      ch.close().awaitUninterruptibly(2000);
    }
    Channel udp = realtimeChannel;
    if (udp != null) udp.close().awaitUninterruptibly(2000);
    group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    state = ConnectionState.DISCONNECTED;
    realtimeReady = false;
  }

  @Override
  public @Nullable Connection get(UUID netUuid) {
    NettySession current = session;
    return current != null && current.id().equals(netUuid) ? current : null;
  }

  @Override
  public Collection<Connection> connections() {
    NettySession current = session;
    return current != null && current.isActive() ? List.of(current) : List.of();
  }

  @Override
  public boolean isRunning() {
    return state != ConnectionState.DISCONNECTED;
  }

  private final class NettySession implements Connection {
    private final UUID id;
    private final Channel channel;
    private final UUID realtimeToken;
    private volatile long lastActivity;

    NettySession(UUID id, UUID realtimeToken, Channel channel) {
      this.id = id;
      this.realtimeToken = realtimeToken;
      this.channel = channel;
      this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public void send(Packet packet) {
      Channel udp = realtimeChannel;
      boolean realtimeProbe = packet instanceof RealtimeHelloPacket;
      if (packet.delivery() == PacketDelivery.UNRELIABLE_SEQUENCED
          && (realtimeReady || realtimeProbe)
          && udp != null && udp.isActive()) {
        ByteBuf payload = RealtimePacketCodec.encode(udp.alloc(), id, realtimeToken, packet);
        udp.writeAndFlush(new DatagramPacket(payload, (InetSocketAddress) udp.remoteAddress()));
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
      return channel.isActive();
    }

    @Override
    public ConnectionState state() {
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

    void touch() {
      lastActivity = System.currentTimeMillis();
    }
  }

  private final class ClientSessionHandler extends ChannelInboundHandlerAdapter {
    private final CompletableFuture<Void> connectFuture;

    ClientSessionHandler(CompletableFuture<Void> connectFuture) {
      this.connectFuture = connectFuture;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      channel = ctx.channel();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      boolean wasConnected = session != null;
      state = ConnectionState.DISCONNECTED;
      realtimeReady = false;
      NettySession s = session;
      if (s != null) {
        disconnectEvents.add(s);
      }
      session = null;
      channel = null;
      if (!wasConnected) {
        // The channel died mid-handshake: agreement was never reached, so the
        // connect future must fail rather than hang until its own timeout.
        connectFuture.completeExceptionally(new ConnectException("Connection closed during handshake"));
      }
    }

    @SuppressWarnings("all")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof ConnectionOpenPacket open) {
        // The server assigned the shared connection identifier. Adopt it, then
        // acknowledge so the server can consider the connection established.
        UUID connId = open.connId();
        NettySession sess = new NettySession(connId, open.realtimeToken(), ctx.channel());
        session = sess;
        state = ConnectionState.CONNECTED;
        lastHeartbeatSent = System.currentTimeMillis();
        connectEvents.add(sess);
        connectFuture.complete(null);
        ctx.writeAndFlush(new ConnectionAckPacket(connId));
        sess.send(new RealtimeHelloPacket());
        return;
      }
      if (msg instanceof Packet packet) {
        NettySession s = session;
        if (s != null) {
          if (!packet.flow().acceptedByClient()) {
            ctx.close();
            return;
          }
          if (queuedPackets.incrementAndGet() > MAX_QUEUED_PACKETS) {
            queuedPackets.decrementAndGet();
            ctx.close();
            return;
          }
          s.touch();
          inbound.add(packet);
        } else {
          System.err.println("[momentum-net/client] dropped pre-handshake packet: " + packet);
        }
      }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
    }
  }

  private final class ClientDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    protected void channelRead0(ChannelHandlerContext context, DatagramPacket datagram) {
      try {
        RealtimePacketCodec.Decoded decoded = RealtimePacketCodec.decode(datagram.content());
        NettySession current = session;
        if (current == null || !current.id.equals(decoded.connectionId())
            || !current.realtimeToken.equals(decoded.token())
            || !decoded.packet().flow().acceptedByClient()) {
          return;
        }
        lastRealtimeReceived = System.currentTimeMillis();
        if (decoded.packet() instanceof RealtimeWelcomePacket) {
          realtimeReady = true;
          return;
        }
        if (queuedPackets.incrementAndGet() > MAX_QUEUED_PACKETS) {
          queuedPackets.decrementAndGet();
          return;
        }
        current.touch();
        inbound.add(decoded.packet());
      } catch (RuntimeException ignored) {
        // Invalid or stale realtime datagrams are disposable and must never
        // tear down the reliable connection.
      }
    }
  }
}
