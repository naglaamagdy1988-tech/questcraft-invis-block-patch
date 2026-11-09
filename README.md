# QuestCraft VR Compatibility API

An experimental client-side compatibility layer for **QuestCraft 26.2** and
Vivecraft-compatible VR mods.

The goal is to give other Fabric mods a small, consistent way to inspect VR
state and read tracked poses without importing Vivecraft implementation
classes directly. This project is still under development; APIs may change
before a stable release.

## Invisible block patch

The optional Sodium/Vivecraft chunk-update patch is enabled by default. It
forces updates during non-vanilla VR render passes. In Sodium, open
**Options → Video Settings → QuestCraft Render Patch** to toggle
**Invisible Block Patch**. Without Sodium, a fallback toggle appears in the
vanilla video settings screen. Its setting is saved in
`config/questcraft-render-patch.json`.

## Current API

### VR state

`QuestCraftVRApi.getVRState()` returns one immutable snapshot containing:

- Whether the Vivecraft API is available and compatible.
- Whether VR is initialized and currently active.
- Seated mode and handedness.
- A status and diagnostic message if the query failed.

```java
QuestCraftVRApi.VRState state = QuestCraftVRApi.getVRState();

if (state.getStatus() == QuestCraftVRApi.StateStatus.READY
        && state.isActive()) {
    // The local player is actively in VR.
}
```

### Head and controller poses

`VRPoseSnapshot.capture(...)` copies head and hand positions, directions,
and rotations into an immutable snapshot.

```java
VRPoseSnapshot pose = VRPoseSnapshot.capture(
        VRPoseSnapshot.Timing.PRE_TICK_WORLD
);

if (pose.isAvailable() && pose.hasMainHand()) {
    VRPoseSnapshot.Vector3 handPosition =
            pose.getMainHand().position();
}
```

Choose timing based on the job:

| Timing | Intended use |
| --- | --- |
| `PRE_TICK_WORLD` | Tick-based movement and physics |
| `POST_TICK_WORLD` | Data after the game tick |
| `WORLD_RENDER` | Frame/render-time visual effects |
| `LATEST_ROOM` | Latest room-relative tracked pose |
| `POST_TICK_ROOM` | Room-relative pose after the game tick |

Vivecraft recommends pre-tick world poses for tick-based trackers and
world-render poses for per-frame trackers. Sample them on the appropriate
client thread; do not retain live Vivecraft objects.

### Full-body tracker poses

`VRTrackerSnapshot.capture(timing)` returns immutable positions, directions, and rotations for available feet, waist, knees, and elbows. It reads Vivecraft's public `VRPose.getBodyPartData(VRBodyPart)` API. Missing optional trackers are omitted from the map; they do not invalidate other tracker data.

```java
VRTrackerSnapshot trackers = VRTrackerSnapshot.capture(
        VRPoseSnapshot.Timing.PRE_TICK_WORLD
);

if (trackers.isAvailable()) {
    VRPoseSnapshot.BodyPart waist = trackers.getTracker(
            VRTrackerSnapshot.Tracker.WAIST
    );
}
```

`READY` means all seven tracker locations are present; `PARTIAL` means at least one is present. `NO_TRACKERS` means the pose was available but no full-body tracker positions were reported. Other statuses identify missing/incompatible APIs, inactive VR, or capture failures.
### Estimated leg poses

Quest 2 camera images are not available to the game, so `VRLegEstimateSnapshot.capture()` provides an optional procedural avatar estimate based on in-game movement and facing. It does not track the player's physical legs. Toggle **Estimated Leg Poses** in **Options → Video Settings**. The enabled state is stored in `config/questcraft-render-patch.json`.

```java
VRLegEstimateSnapshot legs = VRLegEstimateSnapshot.capture();
if (legs.isEstimated()) {
    VRPoseSnapshot.Vector3 leftFoot =
            legs.getPosition(VRLegEstimateSnapshot.Part.LEFT_FOOT);
}
```

### Pose history and movement

`VRPoseHistorySnapshot.capture(lookbackTicks, playerPositionRelative)`
returns immutable movement measurements for the head, hands, and body parts
supported by the current tracking mode.

```java
VRPoseHistorySnapshot history =
        VRPoseHistorySnapshot.capture(6, true);

if (history.isAvailable()) {
    VRPoseHistorySnapshot.Movement hand =
            history.getMovement(
                    VRPoseHistorySnapshot.BodyPart.MAIN_HAND
            );

    if (hand != null && hand.available()) {
        double speedInBlocksPerTick = hand.averageSpeed();
    }
}
```

- `netMovement` is the net displacement across the requested history.
- `averageVelocity` is a direction vector in blocks per tick.
- `averageSpeed` is movement speed in blocks per tick.
- `playerPositionRelative = true` removes player-body movement from the
  measurement; `false` measures movement in world space.
- The requested lookback is limited to the history currently available, up to
  Vivecraft's 200-tick history limit.

### Movement level logic

`VRMotionSnapshot` classifies available body-part movement as `STILL`,
`MOVING`, or `FAST` using thresholds chosen by the calling mod. Both thresholds
use blocks per tick, so mods can tune the bands for their own interactions.

```java
VRMotionSnapshot motion = VRMotionSnapshot.capture(6, true, 0.02, 0.12);
VRMotionSnapshot.Motion hand = motion.getMotion(
        VRPoseHistorySnapshot.BodyPart.MAIN_HAND
);

if (motion.isAvailable() && hand != null
        && hand.level() == VRMotionSnapshot.Level.FAST) {
    // Handle a fast hand movement.
}
```

Missing parts are omitted. The result keeps the pose-history status and reports
invalid thresholds or unavailable movement data through `getError()`.

### Safe result statuses

The state, pose, and history APIs report unavailable or failed states instead
of requiring calling mods to handle Vivecraft reflection directly. Check each
snapshot's status and check individual tracked body parts before using them.
A missing optional full-body tracker does not mean the headset or hands failed.

## Compatibility notes

- Target: Minecraft 26.2, Fabric Loader 0.19.3.
- Client-side API; this does not add server-side VR pose networking.
- Vivecraft is accessed reflectively, so it is not a compile-time dependency.
- A successful GitHub Actions build only verifies compilation and packaging.
  Runtime behavior still needs testing in QuestCraft on the Quest headset.

## Development

Build with:

```sh
./gradlew build
```

This project is experimental and not yet a stable dependency for other mods.
