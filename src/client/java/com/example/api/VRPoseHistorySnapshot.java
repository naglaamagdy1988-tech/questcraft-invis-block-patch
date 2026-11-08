package com.example.api;

import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable movement measurements derived from Vivecraft's local VR pose
 * history. This provides the common data needed by throwing, swing and
 * gesture mods without exposing Vivecraft classes in this API's signatures.
 */
public final class VRPoseHistorySnapshot {
    private static final String VIVECRAFT_CLIENT_API =
            "org.vivecraft.api.client.VRClientAPI";
    private static final String VIVECRAFT_BODY_PART =
            "org.vivecraft.api.data.VRBodyPart";

    /** Tracked body parts exposed by Vivecraft's public API. */
    public enum BodyPart {
        HEAD,
        MAIN_HAND,
        OFF_HAND,
        RIGHT_FOOT,
        LEFT_FOOT,
        WAIST,
        RIGHT_KNEE,
        LEFT_KNEE,
        RIGHT_ELBOW,
        LEFT_ELBOW
    }

    public enum Status {
        READY,
        PARTIAL,
        NOT_INITIALIZED,
        NOT_IN_VR,
        NO_HISTORY,
        NO_TRACKED_PARTS,
        API_MISSING,
        API_INCOMPATIBLE,
        ERROR
    }

    /** Movement measurements for one body part, in blocks and blocks/tick. */
    public record Movement(
            VRPoseSnapshot.Vector3 netMovement,
            VRPoseSnapshot.Vector3 averageVelocity,
            double averageSpeed,
            boolean available
    ) {
    }

    private final Status status;
    private final int ticksAvailable;
    private final int lookbackTicks;
    private final boolean playerPositionRelative;
    private final Map<BodyPart, Movement> movements;
    private final String error;

    private VRPoseHistorySnapshot(
            Status status,
            int ticksAvailable,
            int lookbackTicks,
            boolean playerPositionRelative,
            Map<BodyPart, Movement> movements,
            String error
    ) {
        this.status = status;
        this.ticksAvailable = ticksAvailable;
        this.lookbackTicks = lookbackTicks;
        this.playerPositionRelative = playerPositionRelative;
        this.movements = Collections.unmodifiableMap(
                new EnumMap<>(movements));
        this.error = error;
    }

    /**
     * Captures movement measurements for all body parts supported by the
     * Vivecraft API. The requested lookback is clamped to available history.
     *
     * @param lookbackTicks maximum number of ticks to measure backwards (0-200)
     * @param playerPositionRelative true to remove player-body movement from
     *                               the measurements, false for world space
     */
    public static VRPoseHistorySnapshot capture(
            int lookbackTicks,
            boolean playerPositionRelative
    ) {
        if (lookbackTicks < 0 || lookbackTicks > 200) {
            return failed(Status.ERROR, 0, lookbackTicks,
                    playerPositionRelative, "lookbackTicks must be between 0 and 200");
        }

        QuestCraftVRApi.ApiStatus apiStatus = QuestCraftVRApi.getApiStatus();
        if (apiStatus == QuestCraftVRApi.ApiStatus.MISSING) {
            return failed(Status.API_MISSING, 0, lookbackTicks,
                    playerPositionRelative,
                    "Vivecraft client API class was not found");
        }
        if (apiStatus == QuestCraftVRApi.ApiStatus.INCOMPATIBLE) {
            return failed(Status.API_INCOMPATIBLE, 0, lookbackTicks,
                    playerPositionRelative,
                    "Vivecraft client API does not match the expected methods");
        }

        QuestCraftVRApi.VRState state = QuestCraftVRApi.getVRState();
        switch (state.getStatus()) {
            case API_MISSING:
                return failed(Status.API_MISSING, 0, lookbackTicks,
                        playerPositionRelative, state.getError());
            case API_INCOMPATIBLE:
                return failed(Status.API_INCOMPATIBLE, 0, lookbackTicks,
                        playerPositionRelative, state.getError());
            case ERROR:
                return failed(Status.ERROR, 0, lookbackTicks,
                        playerPositionRelative, state.getError());
            case NOT_INITIALIZED:
                return failed(Status.NOT_INITIALIZED, 0, lookbackTicks,
                        playerPositionRelative, null);
            case READY:
                break;
            default:
                return failed(Status.ERROR, 0, lookbackTicks,
                        playerPositionRelative,
                        "Unknown Vivecraft state: " + state.getStatus());
        }

        if (!state.isActive()) {
            return failed(Status.NOT_IN_VR, 0, lookbackTicks,
                    playerPositionRelative, null);
        }

        try {
            ClassLoader loader = QuestCraftVRApi.class.getClassLoader();
            Class<?> apiClass = Class.forName(
                    VIVECRAFT_CLIENT_API, true, loader);
            Object apiInstance = apiClass.getMethod("instance").invoke(null);
            if (apiInstance == null) {
                return failed(Status.ERROR, 0, lookbackTicks,
                        playerPositionRelative,
                        "VRClientAPI.instance() returned null");
            }

            Object history = apiClass.getMethod("getHistoricalVRPoses")
                    .invoke(apiInstance);
            if (history == null) {
                return failed(Status.NO_HISTORY, 0, lookbackTicks,
                        playerPositionRelative,
                        "Vivecraft has not collected pose history yet");
            }

            Class<?> historyClass = history.getClass();
            Method ticksMethod = historyClass.getMethod("ticksOfHistory");
            int ticksAvailable = ((Number) ticksMethod.invoke(history)).intValue();
            if (ticksAvailable <= 0) {
                return failed(Status.NO_HISTORY, 0, lookbackTicks,
                        playerPositionRelative,
                        "Vivecraft pose history is empty");
            }

            int actualLookback = Math.min(lookbackTicks,
                    Math.min(ticksAvailable, 200));

            Class<?> bodyPartClass = Class.forName(
                    VIVECRAFT_BODY_PART, false, loader);
            if (!bodyPartClass.isEnum()) {
                return failed(Status.API_INCOMPATIBLE, ticksAvailable,
                        actualLookback, playerPositionRelative,
                        "Vivecraft VRBodyPart is not an enum");
            }

            Method netMovementMethod = historyClass.getMethod(
                    "netMovement", bodyPartClass, int.class, boolean.class);
            Method averageVelocityMethod = historyClass.getMethod(
                    "averageVelocity", bodyPartClass, int.class, boolean.class);
            Method averageSpeedMethod = historyClass.getMethod(
                    "averageSpeed", bodyPartClass, int.class, boolean.class);

            Map<BodyPart, Movement> measurements =
                    new EnumMap<>(BodyPart.class);
            for (BodyPart part : BodyPart.values()) {
                Object vivecraftPart = findEnumConstant(bodyPartClass, part.name());
                if (vivecraftPart == null) {
                    continue;
                }

                Object rawMovement = netMovementMethod.invoke(
                        history, vivecraftPart, actualLookback,
                        playerPositionRelative);
                Object rawVelocity = averageVelocityMethod.invoke(
                        history, vivecraftPart, actualLookback,
                        playerPositionRelative);
                Object rawSpeed = averageSpeedMethod.invoke(
                        history, vivecraftPart, actualLookback,
                        playerPositionRelative);

                VRPoseSnapshot.Vector3 netMovement = toVector(rawMovement);
                VRPoseSnapshot.Vector3 averageVelocity = toVector(rawVelocity);
                double averageSpeed = rawSpeed instanceof Number number
                        ? number.doubleValue() : 0.0;
                boolean available = netMovement != null
                        || averageVelocity != null;

                measurements.put(part, new Movement(
                        netMovement, averageVelocity, averageSpeed, available));
            }

            int availableParts = 0;
            for (Movement movement : measurements.values()) {
                if (movement.available()) {
                    availableParts++;
                }
            }
            if (availableParts == 0) {
                return failed(Status.NO_TRACKED_PARTS, ticksAvailable,
                        actualLookback, playerPositionRelative,
                        "No tracked body-part movement was available");
            }

            boolean coreTrackingAvailable =
                    isAvailable(measurements, BodyPart.HEAD)
                    && isAvailable(measurements, BodyPart.MAIN_HAND)
                    && isAvailable(measurements, BodyPart.OFF_HAND);
            Status resultStatus = coreTrackingAvailable
                    ? Status.READY : Status.PARTIAL;

            return new VRPoseHistorySnapshot(
                    resultStatus, ticksAvailable, actualLookback,
                    playerPositionRelative, measurements, null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return failed(Status.ERROR, 0, lookbackTicks,
                    playerPositionRelative, describeFailure(cause));
        } catch (NoSuchMethodException e) {
            return failed(Status.API_INCOMPATIBLE, 0, lookbackTicks,
                    playerPositionRelative, describeFailure(e));
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException e) {
            return failed(Status.ERROR, 0, lookbackTicks,
                    playerPositionRelative, describeFailure(e));
        }
    }

    private static Object findEnumConstant(Class<?> enumClass, String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> enumValue
                    && enumValue.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    private static VRPoseSnapshot.Vector3 toVector(Object value) {
        if (!(value instanceof Vec3 vector)) {
            return null;
        }
        return new VRPoseSnapshot.Vector3(vector.x, vector.y, vector.z);
    }

    private static boolean isAvailable(
            Map<BodyPart, Movement> measurements,
            BodyPart part
    ) {
        Movement movement = measurements.get(part);
        return movement != null && movement.available();
    }

    private static VRPoseHistorySnapshot failed(
            Status status,
            int ticksAvailable,
            int lookbackTicks,
            boolean playerPositionRelative,
            String error
    ) {
        return new VRPoseHistorySnapshot(
                status, ticksAvailable, lookbackTicks,
                playerPositionRelative, new EnumMap<>(BodyPart.class), error);
    }

    private static String describeFailure(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return status == Status.READY || status == Status.PARTIAL;
    }

    public int getTicksAvailable() {
        return ticksAvailable;
    }

    public int getLookbackTicks() {
        return lookbackTicks;
    }

    public boolean isPlayerPositionRelative() {
        return playerPositionRelative;
    }

    public Movement getMovement(BodyPart bodyPart) {
        return movements.get(bodyPart);
    }

    public Map<BodyPart, Movement> getMovements() {
        return movements;
    }

    /** Returns a concise diagnostic for failed captures; otherwise null. */
    public String getError() {
        return error;
    }
}
