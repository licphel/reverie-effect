/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.network.packet;

/** Declares which remote endpoint is permitted to send a packet type. */
public enum PacketFlow {
  CLIENTBOUND,
  SERVERBOUND,
  BIDIRECTIONAL,
  INTERNAL;

  public boolean acceptedByClient() {
    return this == CLIENTBOUND || this == BIDIRECTIONAL;
  }

  public boolean acceptedByServer() {
    return this == SERVERBOUND || this == BIDIRECTIONAL;
  }
}
