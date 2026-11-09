package com.example.api;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Immutable, dependency-light snapshot of the local player's tracked head and
 * hand poses. No Vivecraft classes are referenced at compile time.
 *
 * <p>Use PRE_TICK_WORLD from tick-based processing and WORLD_RENDER from
 * rendering/frame-based processing. Other timing modes are exposed for mods
 * that need room-space or post-tick data.</p>
 */
public final class VRPoseSnapshot {
    public enum Timing {
        LATEST_ROOM("getLatestRoomPose"),
        POST_TICK_ROOM("getPostTickRoomPose"),
        PRE_TICK_WORLD("getPreTickWorldPose"),
        POST_TICK_WORLD("getPostTickWorldPose"),
        WORLD_RENDER("getWorldRenderPose");

        private final String methodName;

        Timing(String methodName) {
            this.methodName = methodName;
        }
    }

    public enum Status {
        READY,
        PARTIAL,
        NOT_INITIALIZED,
        NOT_IN_VR,
        NO_POSE,
        NO_TRACKED_PARTS,
        API_MISSING,
        API_INCOMPATIBLE,
        ERROR
    }

    /** A three-dimensional vector copied out of Vivecraft's live pose data. */
    public record Vector3(double x, double y, double z) {
    }

    /** A quaternion copied out of Vivecraft's live pose data. */
    public record Rotation(float x, float y, float z, float w) {
    }

    /** Position, forward direction, and rotation for one tracked body part. */
    public record BodyPart(
            Vector3 position,
            Vector3 direction,
            Rotation rotation
    ) {
    }

    private final Timing timing;
    private final Status status;
    private final BodyPart head;
    private final BodyPart mainHand;
    private final BodyPart offHand;
    private final String error;

    private VRPoseSnapshot(
            Timing timing,
            Status status,
            BodyPart head,
            BodyPart mainHand,
            BodyPart offHand,
            String error
    ) {
        this.timing = timing;
        this.status = status;
        this.head = head;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.error = error;
    }

    /**
     * Captures a single immutable pose snapshot.
     *
     * <p>The method never lets ordinary reflection, linkage, or Vivecraft
     * runtime failures escape into the calling mod. A PARTIAL result is still
     * useful; callers should check individual body parts before using them.</p>
     */
    public static VRPoseSnapshot capture(Timing timing) {
        if (timing == null) {
            return failed(null, Status.ERROR, "Pose timing must not be null");
        }

        QuestCraftVRApi.VRState state = QuestCraftVRApi.getVRState();
        switch (state.getStatus()) {
            case API_MISSING:
                return failed(timing, Status.API_MISSING, state.getError());
            case API_INCOMPATIBLE:
                return failed(timing, Status.API_INCOMPATIBLE, state.getError());
            case ERROR:
                return failed(timing, Status.ERROR, state.getError());
            case NOT_INITIALIZED:
                return failed(timing, Status.NOT_INITIALIZED, null);
            case READY:
                break;
            default:
                return failed(timing, Status.ERROR,
                        "Unknown Vivecraft state: " + state.getStatus());
        }

        if (!state.isActive()) {
            return failed(timing, Status.NOT_IN_VR, null);
        }

        try {
            Class<?> apiClass = VRReflectionCache.clientApiClass(true);
            Object apiInstance = VRReflectionCache.clientApiInstance();
            if (apiInstance == null) {
                return failed(timing, Status.ERROR,
                        "VRClientAPI.instance() returned null");
            }

            Method poseGetter = VRReflectionCache.method(
                    apiClass, timing.methodName);
            Object pose = poseGetter.invoke(apiInstance);
            if (pose == null) {
                return failed(timing, Status.NO_POSE,
                        "Vivecraft returned no pose for " + timing);
            }

            BodyPart head = readBodyPart(pose, "getHead");
            BodyPart mainHand = readBodyPart(pose, "getMainHand");
            BodyPart offHand = readBodyPart(pose, "getOffHand");

            int availableParts = countAvailable(head, mainHand, offHand);
            if (availableParts == 0) {
                return failed(timing, Status.NO_TRACKED_PARTS,
                        "The pose contained no usable head or hand positions");
            }

            Status resultStatus = availableParts == 3
                    ? Status.READY : Status.PARTIAL;
            return new VRPoseSnapshot(
                    timing, resultStatus, head, mainHand, offHand, null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return failed(timing, Status.ERROR, describeFailure(cause));
        } catch (NoSuchMethodException e) {
            return failed(timing, Status.API_INCOMPATIBLE,
                    describeFailure(e));
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException e) {
            return failed(timing, Status.ERROR, describeFailure(e));
        }
    }

    private static BodyPart readBodyPart(Object pose, String getterName)
            throws ReflectiveOperationException {
        Method getter = VRReflectionCache.method(pose.getClass(), getterName);
        Object data = getter.invoke(pose);
        if (data == null) {
            return null;
        }

        Object rawPosition = VRReflectionCache.method(data.getClass(), "getPos").invoke(data);
        Object rawDirection = VRReflectionCache.method(data.getClass(), "getDir").invoke(data);
        Object rawRotation = VRReflectionCache.method(data.getClass(), "getRotation").invoke(data);

        Vector3 position = toVector(rawPosition);
        Vector3 direction = toVector(rawDirection);
        Rotation rotation = toRotation(rawRotation);

        // A tracked part without a position is not useful to most consumers.
        return position == null ? null : new BodyPart(position, direction, rotation);
    }

    private static Vector3 toVector(Object value) {
        if (!(value instanceof Vec3 vector)) {
            return null;
        }
        return new Vector3(vector.x, vector.y, vector.z);
    }

    private static Rotation toRotation(Object value) {
        if (!(value instanceof Quaternionfc quaternion)) {
            return null;
        }
        return new Rotation(
                quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
    }

    private static int countAvailable(
            BodyPart head,
            BodyPart mainHand,
            BodyPart offHand
    ) {
        int count = 0;
        if (head != null) count++;
        if (mainHand != null) count++;
        if (offHand != null) count++;
        return count;
    }

    private static VRPoseSnapshot failed(
            Timing timing,
            Status status,
            String error
    ) {
        return new VRPoseSnapshot(timing, status, null, null, null, error);
    }

    private static String describeFailure(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public Timing getTiming() {
        return timing;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return status == Status.READY || status == Status.PARTIAL;
    }

    public boolean hasHead() {
        return head != null;
    }

    public boolean hasMainHand() {
        return mainHand != null;
    }

    public boolean hasOffHand() {
        return offHand != null;
    }

    public BodyPart getHead() {
        return head;
    }

    public BodyPart getMainHand() {
        return mainHand;
    }

    public BodyPart getOffHand() {
        return offHand;
    }

    /** Returns a concise diagnostic for failed captures; otherwise null. */
    public String getError() {
        return error;
    }
}
