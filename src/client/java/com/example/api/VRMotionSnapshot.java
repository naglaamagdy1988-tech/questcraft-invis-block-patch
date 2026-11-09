package com.example.api;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Classifies tracked-part movement from Vivecraft pose history into
 * caller-configurable speed bands.
 */
public final class VRMotionSnapshot {
    public enum Level {
        STILL,
        MOVING,
        FAST
    }

    /** Classified movement speed and the average velocity, when available. */
    public record Motion(
            Level level,
            double averageSpeed,
            VRPoseSnapshot.Vector3 averageVelocity
    ) {
    }

    private final VRPoseHistorySnapshot.Status status;
    private final int ticksAvailable;
    private final int lookbackTicks;
    private final boolean playerPositionRelative;
    private final double movingThreshold;
    private final double fastThreshold;
    private final Map<VRPoseHistorySnapshot.BodyPart, Motion> movements;
    private final String error;

    private VRMotionSnapshot(
            VRPoseHistorySnapshot.Status status,
            int ticksAvailable,
            int lookbackTicks,
            boolean playerPositionRelative,
            double movingThreshold,
            double fastThreshold,
            Map<VRPoseHistorySnapshot.BodyPart, Motion> movements,
            String error
    ) {
        this.status = status;
        this.ticksAvailable = ticksAvailable;
        this.lookbackTicks = lookbackTicks;
        this.playerPositionRelative = playerPositionRelative;
        this.movingThreshold = movingThreshold;
        this.fastThreshold = fastThreshold;
        this.movements = Collections.unmodifiableMap(
                new EnumMap<>(movements));
        this.error = error;
    }

    /**
     * Captures movement levels for every body part with usable history.
     * Thresholds are in blocks per tick. Speeds at or below the moving
     * threshold are STILL; speeds at or above the fast threshold are FAST.
     */
    public static VRMotionSnapshot capture(
            int lookbackTicks,
            boolean playerPositionRelative,
            double movingThreshold,
            double fastThreshold
    ) {
        if (!Double.isFinite(movingThreshold) || movingThreshold < 0.0
                || !Double.isFinite(fastThreshold)
                || fastThreshold <= movingThreshold) {
            return failed(lookbackTicks, playerPositionRelative,
                    movingThreshold, fastThreshold,
                    "Thresholds must be finite and non-negative, with fastThreshold greater than movingThreshold");
        }

        VRPoseHistorySnapshot history = VRPoseHistorySnapshot.capture(
                lookbackTicks, playerPositionRelative);
        if (!history.isAvailable()) {
            return new VRMotionSnapshot(history.getStatus(),
                    history.getTicksAvailable(), history.getLookbackTicks(),
                    playerPositionRelative, movingThreshold, fastThreshold,
                    new EnumMap<>(VRPoseHistorySnapshot.BodyPart.class),
                    history.getError());
        }

        Map<VRPoseHistorySnapshot.BodyPart, Motion> result =
                new EnumMap<>(VRPoseHistorySnapshot.BodyPart.class);
        for (VRPoseHistorySnapshot.BodyPart part
                : VRPoseHistorySnapshot.BodyPart.values()) {
            VRPoseHistorySnapshot.Movement movement =
                    history.getMovement(part);
            if (movement == null || !movement.available()) continue;

            double speed = usableSpeed(movement, history.getLookbackTicks());
            if (!Double.isFinite(speed) || speed < 0.0) continue;

            Level level = speed <= movingThreshold ? Level.STILL
                    : speed >= fastThreshold ? Level.FAST : Level.MOVING;
            result.put(part, new Motion(level, speed,
                    movement.averageVelocity()));
        }

        String error = result.isEmpty()
                ? "No finite movement speeds were available for classification"
                : null;
        return new VRMotionSnapshot(history.getStatus(),
                history.getTicksAvailable(), history.getLookbackTicks(),
                playerPositionRelative, movingThreshold, fastThreshold,
                result, error);
    }

    private static double usableSpeed(
            VRPoseHistorySnapshot.Movement movement,
            int lookbackTicks
    ) {
        double speed = movement.averageSpeed();
        if (Double.isFinite(speed) && speed >= 0.0) return speed;

        double vectorSpeed = magnitude(movement.averageVelocity());
        if (Double.isFinite(vectorSpeed)) return vectorSpeed;

        double netDistance = magnitude(movement.netMovement());
        if (lookbackTicks > 0 && Double.isFinite(netDistance)) {
            return netDistance / lookbackTicks;
        }
        return Double.NaN;
    }

    private static double magnitude(VRPoseSnapshot.Vector3 vector) {
        if (vector == null) return Double.NaN;
        return Math.hypot(Math.hypot(vector.x(), vector.y()), vector.z());
    }

    private static VRMotionSnapshot failed(
            int lookbackTicks,
            boolean playerPositionRelative,
            double movingThreshold,
            double fastThreshold,
            String error
    ) {
        return new VRMotionSnapshot(VRPoseHistorySnapshot.Status.ERROR,
                0, lookbackTicks, playerPositionRelative, movingThreshold,
                fastThreshold,
                new EnumMap<>(VRPoseHistorySnapshot.BodyPart.class), error);
    }

    public VRPoseHistorySnapshot.Status getStatus() { return status; }
    public boolean isAvailable() {
        return (status == VRPoseHistorySnapshot.Status.READY
                || status == VRPoseHistorySnapshot.Status.PARTIAL)
                && !movements.isEmpty();
    }
    public int getTicksAvailable() { return ticksAvailable; }
    public int getLookbackTicks() { return lookbackTicks; }
    public boolean isPlayerPositionRelative() { return playerPositionRelative; }
    public double getMovingThreshold() { return movingThreshold; }
    public double getFastThreshold() { return fastThreshold; }
    public Motion getMotion(VRPoseHistorySnapshot.BodyPart part) {
        return part == null ? null : movements.get(part);
    }
    public Map<VRPoseHistorySnapshot.BodyPart, Motion> getMovements() {
        return movements;
    }
    public String getError() { return error; }
}
