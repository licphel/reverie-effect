/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.network;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.viki.rf.network.packet.Packet;

/** Owns one network endpoint and the logical connections reachable through it. */
public interface ConnectionHost extends AutoCloseable {
  CompletableFuture<Void> start();

  @Nullable Connection get(UUID netUuid);

  Collection<Connection> connections();

  void process();

  void onConnected(Consumer<Connection> callback);

  void onDisconnected(Consumer<Connection> callback);

  void onPacket(BiConsumer<Connection, Packet> callback);

  boolean isRunning();

  @Override
  void close();
}
