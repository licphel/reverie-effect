package io.viki.rf.network.packet;

/** Wire delivery semantics fixed by each packet type. */
public enum PacketDelivery {
  RELIABLE_ORDERED,
  UNRELIABLE_SEQUENCED
}
