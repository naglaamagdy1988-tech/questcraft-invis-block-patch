package com.example.api;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable snapshot of Vivecraft full-body tracker poses. */
public final class VRTrackerSnapshot {
    /** Tracker locations supported by Vivecraft's 26.2 public API. */
    public enum Tracker {
        RIGHT_FOOT, LEFT_FOOT, WAIST,
        RIGHT_KNEE, LEFT_KNEE, RIGHT_ELBOW, LEFT_ELBOW
    }

    public enum Status {
        READY, PARTIAL, NOT_INITIALIZED, NOT_IN_VR, NO_POSE, NO_TRACKERS,
        API_MISSING, API_INCOMPATIBLE, ERROR
    }

    private final VRPoseSnapshot.Timing timing;
    private final Status status;
    private final Map<Tracker, VRPoseSnapshot.BodyPart> trackers;
    private final String error;

    private VRTrackerSnapshot(VRPoseSnapshot.Timing timing, Status status,
                              Map<Tracker, VRPoseSnapshot.BodyPart> trackers,
                              String error) {
        this.timing = timing;
        this.status = status;
        this.trackers = Collections.unmodifiableMap(new EnumMap<>(trackers));
        this.error = error;
    }

    /** Captures the selected pose timing; absent optional trackers are omitted. */
    public static VRTrackerSnapshot capture(VRPoseSnapshot.Timing timing) {
        if (timing == null) {
            return failed(null, Status.ERROR, "Pose timing must not be null");
        }
        QuestCraftVRApi.VRState state = QuestCraftVRApi.getVRState();
        switch (state.getStatus()) {
            case API_MISSING: return failed(timing, Status.API_MISSING, state.getError());
            case API_INCOMPATIBLE: return failed(timing, Status.API_INCOMPATIBLE, state.getError());
            case ERROR: return failed(timing, Status.ERROR, state.getError());
            case NOT_INITIALIZED: return failed(timing, Status.NOT_INITIALIZED, null);
            case READY: break;
            default: return failed(timing, Status.ERROR,
                    "Unknown Vivecraft state: " + state.getStatus());
        }
        if (!state.isActive()) return failed(timing, Status.NOT_IN_VR, null);

        try {
            Class<?> apiClass = VRReflectionCache.clientApiClass(true);
            Object api = VRReflectionCache.clientApiInstance();
            if (api == null) return failed(timing, Status.ERROR,
                    "VRClientAPI.instance() returned null");
            Object pose = VRReflectionCache.method(apiClass, timingMethod(timing))
                    .invoke(api);
            if (pose == null) return failed(timing, Status.NO_POSE,
                    "Vivecraft returned no pose for " + timing);

            Class<?> bodyPartClass = VRReflectionCache.bodyPartClass();
            Method getBodyPartData = VRReflectionCache.method(
                    pose.getClass(), "getBodyPartData", bodyPartClass);
            Map<Tracker, VRPoseSnapshot.BodyPart> result = new EnumMap<>(Tracker.class);
            for (Tracker tracker : Tracker.values()) {
                Object part = VRReflectionCache.enumConstant(bodyPartClass, tracker.name());
                if (part == null) continue;
                Object data = getBodyPartData.invoke(pose, part);
                if (data == null) continue;
                Object rawPos = VRReflectionCache.method(data.getClass(), "getPos").invoke(data);
                Object rawDir = VRReflectionCache.method(data.getClass(), "getDir").invoke(data);
                Object rawRotation = VRReflectionCache.method(
                        data.getClass(), "getRotation").invoke(data);
                VRPoseSnapshot.Vector3 position = toVector(rawPos);
                if (position == null) continue;
                result.put(tracker, new VRPoseSnapshot.BodyPart(position,
                        toVector(rawDir), toRotation(rawRotation)));
            }
            if (result.isEmpty()) return failed(timing, Status.NO_TRACKERS,
                    "No full-body tracker positions are available");
            return new VRTrackerSnapshot(timing,
                    result.size() == Tracker.values().length ? Status.READY : Status.PARTIAL,
                    result, null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return failed(timing, Status.ERROR, describeFailure(cause));
        } catch (NoSuchMethodException e) {
            return failed(timing, Status.API_INCOMPATIBLE, describeFailure(e));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return failed(timing, Status.ERROR, describeFailure(e));
        }
    }

    private static String timingMethod(VRPoseSnapshot.Timing timing) {
        return switch (timing) {
            case LATEST_ROOM -> "getLatestRoomPose";
            case POST_TICK_ROOM -> "getPostTickRoomPose";
            case PRE_TICK_WORLD -> "getPreTickWorldPose";
            case POST_TICK_WORLD -> "getPostTickWorldPose";
            case WORLD_RENDER -> "getWorldRenderPose";
        };
    }

    private static VRPoseSnapshot.Vector3 toVector(Object value) {
        return value instanceof Vec3 v ? new VRPoseSnapshot.Vector3(v.x, v.y, v.z) : null;
    }

    private static VRPoseSnapshot.Rotation toRotation(Object value) {
        return value instanceof Quaternionfc q
                ? new VRPoseSnapshot.Rotation(q.x(), q.y(), q.z(), q.w()) : null;
    }

    private static VRTrackerSnapshot failed(VRPoseSnapshot.Timing timing, Status status, String error) {
        return new VRTrackerSnapshot(timing, status, new EnumMap<>(Tracker.class), error);
    }

    private static String describeFailure(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public VRPoseSnapshot.Timing getTiming() { return timing; }
    public Status getStatus() { return status; }
    public boolean isAvailable() { return status == Status.READY || status == Status.PARTIAL; }
    public VRPoseSnapshot.BodyPart getTracker(Tracker tracker) { return trackers.get(tracker); }
    public Map<Tracker, VRPoseSnapshot.BodyPart> getTrackers() { return trackers; }
    public String getError() { return error; }
}
