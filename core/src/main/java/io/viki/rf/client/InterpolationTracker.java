package io.viki.rf.client;

import io.viki.rf.GameConstants;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.entity.EntityType;
import io.viki.rf.world.level.ClientLevel;
import io.viki.rf.world.physics.PhysicsConstants;
import io.viki.rf.world.util.PrecisePos;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client clock synchronization and authoritative correction for simulated replicas. */
public final class InterpolationTracker {
  /** Fraction of the client/server state error corrected per simulation tick. */
  private static final float AUTHORITY_BLEND_FACTOR = 0.5F;
  /** Entity updates already arrive every simulation tick; no delayed playback is needed. */
  private static final double TIME_LEAD = 0.0;
  private static final double TIME_TRACK_FACTOR = 1.0;
  private static final double TIME_MAX_DISTANCE = 3.0 / GameConstants.TICKS_PER_SECOND;
  /** Hides one missing network tick without allowing unbounded prediction. */
  private static final double MAX_EXTRAPOLATION = 1.0 / GameConstants.TICKS_PER_SECOND;
  /** Covers the request/server-tick delay before a newly spawned entity's first update. */
  private static final int BOOTSTRAP_EXTRAPOLATION_TICKS = 10;
  private static final double MAX_BOOTSTRAP_EXTRAPOLATION =
      (double) BOOTSTRAP_EXTRAPOLATION_TICKS / GameConstants.TICKS_PER_SECOND;
  private final Map<UUID, ArrayDeque<Snapshot>> snapshots = new HashMap<>();
  private final Map<UUID, Double> lastServerTimes = new HashMap<>();
  private double lastRemoteTime = Double.NaN;
  private double predictedTime = Double.NaN;

  public void receiveTimeUpdate(double remoteTime) {
    if (Double.isFinite(remoteTime)
        && (!Double.isFinite(lastRemoteTime) || remoteTime > lastRemoteTime)) {
      lastRemoteTime = remoteTime;
    }
  }

  public void track(Entity entity, double serverTime) {
    var states = new ArrayDeque<Snapshot>();
    states.addLast(new Snapshot(Math.min(0.0, snapshotTime(serverTime)),
        entity.position(), entity.velocity().x(), entity.velocity().y(),
        MAX_BOOTSTRAP_EXTRAPOLATION, true));
    snapshots.put(entity.entityId(), states);
    lastServerTimes.put(entity.entityId(), serverTime);
  }

  public void receive(Entity entity, double serverTime, PrecisePos position,
               float velocityX, float velocityY) {
    double lastServerTime = lastServerTimes.getOrDefault(entity.entityId(),
        Double.NEGATIVE_INFINITY);
    if (!Double.isFinite(serverTime) || serverTime < lastServerTime) return;
    lastServerTimes.put(entity.entityId(), serverTime);
    var states = snapshots.computeIfAbsent(entity.entityId(), ignored -> new ArrayDeque<>());
    double lead = snapshotTime(serverTime);
    if (states.size() == 1 && states.peekFirst().spawnAnchor) {
      // Project the first authoritative update from its server timestamp.
      // Keeping the bootstrap extrapolation window here prevents the newly
      // spawned entity from being pulled back to the delayed first sample.
      states.clear();
      states.addLast(new Snapshot(Math.min(0.0, lead), position, velocityX, velocityY,
          MAX_BOOTSTRAP_EXTRAPOLATION, false));
      return;
    }
    states.addLast(new Snapshot(lead, position, velocityX, velocityY,
        MAX_EXTRAPOLATION, false));
  }

  public void remove(UUID entityId) {
    snapshots.remove(entityId);
    lastServerTimes.remove(entityId);
  }

  public void tick(double delta, ClientLevel level) {
    updateClock(delta);
    for (var iterator = snapshots.entrySet().iterator(); iterator.hasNext();) {
      var entry = iterator.next();
      Entity entity = level.entities().get(entry.getKey());
      if (entity == null) {
        iterator.remove();
        lastServerTimes.remove(entry.getKey());
        continue;
      }
      var states = entry.getValue();
      for (Snapshot snapshot : states) snapshot.time -= delta;
      while (states.size() > 2 && second(states).time <= 0.0) {
        states.removeFirst();
      }
      apply(entity, states);
      level.entities().updateIndex(entity);
    }
  }

  private void updateClock(double delta) {
    if (!Double.isFinite(lastRemoteTime)) return;
    if (!Double.isFinite(predictedTime) || delta < 0.0) {
      predictedTime = lastRemoteTime;
      return;
    }
    lastRemoteTime += delta;
    predictedTime += delta;
    predictedTime += (lastRemoteTime - predictedTime) * TIME_TRACK_FACTOR;
    predictedTime = Math.clamp(predictedTime, lastRemoteTime - TIME_MAX_DISTANCE,
        lastRemoteTime + TIME_MAX_DISTANCE);
  }

  private double snapshotTime(double serverTime) {
    if (!Double.isFinite(serverTime) || !Double.isFinite(predictedTime)) return 0.0;
    return serverTime - predictedTime + TIME_LEAD;
  }

  private static void apply(Entity entity, ArrayDeque<Snapshot> states) {
    if (states.isEmpty()) return;
    Snapshot first = states.peekFirst();
    if (states.size() == 1) {
      // Until the first physics update arrives, the replica advances using the
      // same client-side physics as the server entity instead of a straight-line
      // bootstrap extrapolation.
      if (first.spawnAnchor) return;
      applyExtrapolated(entity, first);
      return;
    }
    Snapshot second = second(states);
    if (second.time <= 0.0) {
      // Both samples are behind the presentation time. Continue only from the
      // newest authoritative sample, and only for one fixed network tick.
      applyExtrapolated(entity, second);
      return;
    }
    double separation = second.time - first.time;
    double factor = separation <= 0.0 ? 1.0 : -first.time / separation;
    factor = Math.clamp(factor, 0.0, 1.0);
    blend(entity, PrecisePos.lerp(first.position, second.position, factor),
        (float) (first.velocityX + (second.velocityX - first.velocityX) * factor),
        (float) (first.velocityY + (second.velocityY - first.velocityY) * factor));
  }

  private static void applyExtrapolated(Entity entity, Snapshot snapshot) {
    double elapsed = -snapshot.time;
    if (elapsed < 0.0 || elapsed > snapshot.maximumExtrapolation) {
      // A stale packet must not keep dragging an independently simulated
      // replica back toward an expired server state.
      return;
    }
    double duration = Math.max(0.0, elapsed);
    double accelerationY = entity.type() == EntityType.THROWN_ITEM
        ? -PhysicsConstants.STANDARD_GRAVITY * PhysicsConstants.GRAVITY_REBALANCE_FACTOR : 0.0;
    PrecisePos targetPosition = snapshot.position.add(
        snapshot.velocityX * duration,
        snapshot.velocityY * duration + 0.5 * accelerationY * duration * duration);
    blend(entity, targetPosition, snapshot.velocityX,
        (float) (snapshot.velocityY + accelerationY * duration));
  }

  private static void blend(Entity entity, PrecisePos targetPosition,
                            float targetVelocityX, float targetVelocityY) {
    entity.setPosition(PrecisePos.lerp(
        entity.position(), targetPosition, AUTHORITY_BLEND_FACTOR));
    float inverse = 1.0F - AUTHORITY_BLEND_FACTOR;
    entity.setVelocity(
        entity.velocity().x() * inverse + targetVelocityX * AUTHORITY_BLEND_FACTOR,
        entity.velocity().y() * inverse + targetVelocityY * AUTHORITY_BLEND_FACTOR);
  }

  private static Snapshot second(ArrayDeque<Snapshot> states) {
    var iterator = states.iterator();
    iterator.next();
    return iterator.next();
  }

  private static final class Snapshot {
    private double time;
    private final PrecisePos position;
    private final float velocityX;
    private final float velocityY;
    private final double maximumExtrapolation;
    private final boolean spawnAnchor;

    private Snapshot(double time, PrecisePos position, float velocityX, float velocityY,
                     double maximumExtrapolation, boolean spawnAnchor) {
      this.time = time;
      this.position = position;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.maximumExtrapolation = maximumExtrapolation;
      this.spawnAnchor = spawnAnchor;
    }
  }
}
