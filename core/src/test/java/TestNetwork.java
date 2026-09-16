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

import io.viki.rf.network.Connection;
import io.viki.rf.network.ServerConnection;
import io.viki.rf.network.Session;
import io.viki.rf.network.SessionState;
import io.viki.rf.network.packet.EchoPacket;

import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Localhost smoke test for the shared-UUID handshake and packet exchange.
 */
public class TestNetwork {

  static void main(String[] args) throws Exception {
    // Probe a free port instead of assuming DEFAULT_PORT is free: a leftover
    // process (e.g. a previous run whose Netty threads kept the JVM alive)
    // holding the port would otherwise hijack the handshake invisibly.
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    System.out.println("[TEST] using free port " + port);

    // --- Start server ---
    ServerConnection server = ServerConnection.open();
    CountDownLatch serverBound = new CountDownLatch(1);
    server.bind(port).thenRun(serverBound::countDown);
    if (!serverBound.await(3, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Server failed to bind to port " + port);
    }
    System.out.println("[SERVER] bound to port " + port);

    // Track sessions and received packets on the server side.
    CountDownLatch serverConnected = new CountDownLatch(1);
    UUID[] serverSessionId = new UUID[1];
    UUID[] serverRemoteId = new UUID[1];

    server.onConnected(s -> {
      serverSessionId[0] = s.id();
      serverRemoteId[0] = s.remoteId();
      System.out.println("[SERVER] client connected: session.id=" + s.id() + " remoteId=" + s.remoteId());
      serverConnected.countDown();
    });

    // --- Start client ---
    Connection client = new Connection();

    CountDownLatch clientConnected = new CountDownLatch(1);
    CountDownLatch echoReplied = new CountDownLatch(1);
    String[] echoPayload = new String[1];
    UUID[] clientSessionId = new UUID[1];
    UUID[] clientRemoteId = new UUID[1];

    client.onConnected(s -> {
      clientSessionId[0] = s.id();
      clientRemoteId[0] = s.remoteId();
      System.out.println("[CLIENT] connected! session.id=" + s.id() + " remoteId=" + s.remoteId());
      clientConnected.countDown();

      // Send an echo probe to verify bidirectional communication.
      client.send(new EchoPacket("hello"));
      System.out.println("[CLIENT] sent EchoPacket(\"hello\")");
    });

    // The built-in echo observer fires for every received echo; the first reply
    // completes the latch.
    EchoPacket.observe((s, echo) -> {
      if ("hello".equals(echo.message()) && echo.hops() == 1) {
        echoPayload[0] = echo.message();
        System.out.println("[CLIENT] echo replied: \"" + echo.message() + "\"");
        echoReplied.countDown();
      }
    });

    CompletableFuture<Void> connectFuture = client.connect("localhost", port);
    // The future now completes only when the handshake (open + ack) finishes.
    connectFuture.get(5, TimeUnit.SECONDS);
    System.out.println("[CLIENT] handshake future completed");

    // Tick both sides.
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      server.process();
      client.process();
      if (serverConnected.getCount() == 0 && echoReplied.getCount() == 0) {
        break;
      }
      Thread.sleep(16);
    }

    // --- Assertions ---
    System.out.println();
    System.out.println("=== RESULTS ===");

    // 1. Both sides should have fired onConnected (handshake agreed).
    boolean serverGotConnection = serverConnected.getCount() == 0;
    System.out.println("Server received connection: " + (serverGotConnection ? "PASS" : "FAIL"));
    assert serverGotConnection : "Server did not receive client connection";

    boolean clientFiredConnected = clientConnected.getCount() == 0;
    System.out.println("Client fired onConnected: " + (clientFiredConnected ? "PASS" : "FAIL"));
    assert clientFiredConnected : "Client did not fire onConnected";

    // 2. The core invariant: one shared UUID on both endpoints.
    //    server.id == client.id == client.remoteId == server.remoteId
    boolean sharedUuid = serverSessionId[0] != null && serverSessionId[0].equals(clientSessionId[0])
        && serverSessionId[0].equals(clientRemoteId[0]) && serverSessionId[0].equals(serverRemoteId[0]);
    System.out.println("Shared UUID across endpoints: " + (sharedUuid ? "PASS" : "FAIL") + "  " +
        "(server.id=" + serverSessionId[0] + " client.id=" + clientSessionId[0] +
        " client.remoteId=" + clientRemoteId[0] + " server.remoteId=" + serverRemoteId[0] + ")");
    assert sharedUuid : "Endpoints do not share the same connection UUID";

    // 3. Client should be in CONNECTED state and expose its session.
    boolean clientConnectedState = client.state() == SessionState.CONNECTED;
    System.out.println("Client state is CONNECTED: " + (clientConnectedState ? "PASS" : "FAIL") + "  (state=" + client.state() + ")");
    assert clientConnectedState : "Client state is not CONNECTED";

    boolean clientHasSession = client.session().isPresent();
    System.out.println("Client session is present: " + (clientHasSession ? "PASS" : "FAIL"));
    assert clientHasSession : "Client session is not present";

    boolean serverSessionActive = server.sessions().stream().allMatch(Session::isActive);
    System.out.println("Server sessions all active: " + (serverSessionActive ? "PASS" : "FAIL"));
    assert serverSessionActive : "Server has a non-active session";

    // 4. Session.remoteId() via the session object matches the shared UUID.
    UUID sessionRemoteId = client.session().orElseThrow().remoteId();
    boolean sessionRemoteMatches = sessionRemoteId.equals(serverSessionId[0]);
    System.out.println("Session.remoteId == shared UUID: " + (sessionRemoteMatches ? "PASS" : "FAIL") +
        "  (session.remoteId=" + sessionRemoteId + ")");
    assert sessionRemoteMatches : "Session.remoteId does not match";

    // 5. Echo round-trip proves both directions carry packets.
    boolean echoRoundTrip = echoReplied.getCount() == 0 && "hello".equals(echoPayload[0]);
    System.out.println("Echo round-trip: " + (echoRoundTrip ? "PASS" : "FAIL"));
    assert echoRoundTrip : "Echo probe did not complete a round trip";

    // --- Cleanup ---
    EchoPacket.observe(null);
    client.close();
    server.close();
    System.out.println();
    System.out.println("=== ALL TESTS PASSED ===");
  }
}
