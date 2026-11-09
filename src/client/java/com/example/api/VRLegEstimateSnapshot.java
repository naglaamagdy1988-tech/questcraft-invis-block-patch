package com.example.api;

import com.example.client.QuestCraftRenderPatchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Approximate avatar leg poses derived from in-game movement, never camera data. */
public final class VRLegEstimateSnapshot {
    public enum Part { WAIST, LEFT_KNEE, RIGHT_KNEE, LEFT_FOOT, RIGHT_FOOT }
    public enum Status {
        ESTIMATED, DISABLED, NOT_INITIALIZED, NOT_IN_VR, NO_PLAYER,
        API_MISSING, API_INCOMPATIBLE, ERROR
    }

    private static final double STRIDE_LENGTH = 0.72;
    private static long lastUpdatedTick = Long.MIN_VALUE;
    private static double gaitPhase;

    private final Status status;
    private final Map<Part, VRPoseSnapshot.Vector3> positions;

    private VRLegEstimateSnapshot(Status status,
                                  Map<Part, VRPoseSnapshot.Vector3> positions) {
        this.status = status;
        this.positions = Collections.unmodifiableMap(new EnumMap<>(positions));
    }

    /** Captures estimated world-space avatar leg positions when enabled. */
    public static VRLegEstimateSnapshot capture() {
        if (!QuestCraftRenderPatchClient.isEstimatedLegTrackingEnabled()) {
            return empty(Status.DISABLED);
        }

        QuestCraftVRApi.VRState vrState = QuestCraftVRApi.getVRState();
        switch (vrState.getStatus()) {
            case API_MISSING: return empty(Status.API_MISSING);
            case API_INCOMPATIBLE: return empty(Status.API_INCOMPATIBLE);
            case ERROR: return empty(Status.ERROR);
            case NOT_INITIALIZED: return empty(Status.NOT_INITIALIZED);
            case READY: break;
            default: return empty(Status.ERROR);
        }
        if (!vrState.isActive()) {
            return empty(Status.NOT_IN_VR);
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return empty(Status.NO_PLAYER);
        }

        long tick = minecraft.level.getGameTime();
        if (tick != lastUpdatedTick) {
            Vec3 velocity = player.getDeltaMovement();
            double distance = Math.min(0.25,
                    Math.hypot(velocity.x, velocity.z));
            gaitPhase = (gaitPhase + distance * Math.PI * 2.0 / STRIDE_LENGTH)
                    % (Math.PI * 2.0);
            lastUpdatedTick = tick;
        }

        Vec3 origin = player.position();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));

        double leftPhase = gaitPhase + Math.PI;
        double rightPhase = gaitPhase;
        Vec3 waist = origin.add(0.0, 0.58, 0.0);
        Vec3 leftHip = origin.add(right.scale(-0.11)).add(0.0, 0.48, 0.0);
        Vec3 rightHip = origin.add(right.scale(0.11)).add(0.0, 0.48, 0.0);
        Vec3 leftFoot = foot(origin, right, forward, leftPhase, -0.11);
        Vec3 rightFoot = foot(origin, right, forward, rightPhase, 0.11);
        Vec3 leftKnee = leftHip.add(leftFoot).scale(0.5)
                .add(forward.scale(0.04));
        Vec3 rightKnee = rightHip.add(rightFoot).scale(0.5)
                .add(forward.scale(0.04));

        Map<Part, VRPoseSnapshot.Vector3> result = new EnumMap<>(Part.class);
        result.put(Part.WAIST, copy(waist));
        result.put(Part.LEFT_KNEE, copy(leftKnee));
        result.put(Part.RIGHT_KNEE, copy(rightKnee));
        result.put(Part.LEFT_FOOT, copy(leftFoot));
        result.put(Part.RIGHT_FOOT, copy(rightFoot));
        return new VRLegEstimateSnapshot(Status.ESTIMATED, result);
    }

    private static Vec3 foot(Vec3 origin, Vec3 right, Vec3 forward,
                             double phase, double side) {
        double swing = Math.sin(phase) * 0.18;
        double lift = Math.max(0.0, Math.sin(phase)) * 0.12;
        return origin.add(right.scale(side)).add(forward.scale(swing))
                .add(0.0, lift, 0.0);
    }

    private static VRPoseSnapshot.Vector3 copy(Vec3 vector) {
        return new VRPoseSnapshot.Vector3(vector.x, vector.y, vector.z);
    }

    private static VRLegEstimateSnapshot empty(Status status) {
        return new VRLegEstimateSnapshot(status, new EnumMap<>(Part.class));
    }

    public Status getStatus() { return status; }
    public boolean isEstimated() { return status == Status.ESTIMATED; }
    public VRPoseSnapshot.Vector3 getPosition(Part part) { return positions.get(part); }
    public Map<Part, VRPoseSnapshot.Vector3> getPositions() { return positions; }
}
