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
    private static final String VIVECRAFT_CLIENT_API =
            "org.vivecraft.api.client.VRClientAPI";
    private static final String VIVECRAFT_BODY_PART =
            "org.vivecraft.api.data.VRBodyPart";

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
        QuestCraftVRApi.ApiStatus apiStatus = QuestCraftVRApi.getApiStatus();
        if (apiStatus == QuestCraftVRApi.ApiStatus.MISSING) {
            return failed(timing, Status.API_MISSING,
                    "Vivecraft client API class was not found");
        }
        if (apiStatus == QuestCraftVRApi.ApiStatus.INCOMPATIBLE) {
            return failed(timing, Status.API_INCOMPATIBLE,
                    "Vivecraft client API does not match the expected methods");
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
            ClassLoader loader = QuestCraftVRApi.class.getClassLoader();
            Class<?> apiClass = Class.forName(VIVECRAFT_CLIENT_API, true, loader);
            Object api = apiClass.getMethod("instance").invoke(null);
            if (api == null) return failed(timing, Status.ERROR,
                    "VRClientAPI.instance() returned null");
            Object pose = apiClass.getMethod(timingMethod(timing)).invoke(api);
            if (pose == null) return failed(timing, Status.NO_POSE,
                    "Vivecraft returned no pose for " + timing);

            Class<?> bodyPartClass = Class.forName(VIVECRAFT_BODY_PART, false, loader);
            Method getBodyPartData = pose.getClass().getMethod("getBodyPartData", bodyPartClass);
            Map<Tracker, VRPoseSnapshot.BodyPart> result = new EnumMap<>(Tracker.class);
            for (Tracker tracker : Tracker.values()) {
                Object part = enumConstant(bodyPartClass, tracker.name());
                if (part == null) continue;
                Object data = getBodyPartData.invoke(pose, part);
                if (data == null) continue;
                Object rawPos = data.getClass().getMethod("getPos").invoke(data);
                Object rawDir = data.getClass().getMethod("getDir").invoke(data);
                Object rawRotation = data.getClass().getMethod("getRotation").invoke(data);
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

    private static Object enumConstant(Class<?> type, String name) {
        if (!type.isEnum()) return null;
        Object[] constants = type.getEnumConstants();
        if (constants != null) for (Object value : constants) {
            if (value instanceof Enum<?> enumValue && enumValue.name().equals(name)) return value;
        }
        return null;
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
