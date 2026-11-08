package com.example.api;

import java.lang.reflect.Method;

/**
 * Public, optional bridge between QuestCraft-compatible mods and Vivecraft.
 *
 * <p>This class deliberately does not compile against Vivecraft. Mods can call
 * it safely even when Vivecraft (or its public API) is not installed.</p>
 */
public final class QuestCraftVRApi {
    private static final String VIVECRAFT_CLIENT_API =
            "org.vivecraft.api.client.VRClientAPI";

    private QuestCraftVRApi() {
    }

    /**
     * Returns true when Vivecraft's official client API is present.
     */
    public static boolean isVivecraftApiAvailable() {
        try {
            Class.forName(VIVECRAFT_CLIENT_API, false,
                    QuestCraftVRApi.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return false;
        }
    }

    /**
     * Returns true when Vivecraft has initialized its VR support.
     * Returns false if Vivecraft or its public API is unavailable.
     */
    public static boolean isVRInitialized() {
        return invokeBooleanApiMethod("isVRInitialized");
    }

    /**
     * Returns true only while the client is actively running in VR.
     * Returns false if Vivecraft or its public API is unavailable.
     */
    public static boolean isVRActive() {
        return invokeBooleanApiMethod("isVRActive");
    }

    /**
     * Convenient alias for mods that want to check whether the player is
     * currently playing in VR.
     */
    public static boolean isInVR() {
        return isVRActive();
    }

    private static boolean invokeBooleanApiMethod(String methodName) {
        try {
            Class<?> apiClass = Class.forName(VIVECRAFT_CLIENT_API, true,
                    QuestCraftVRApi.class.getClassLoader());

            Method instanceMethod = apiClass.getMethod("instance");
            Object apiInstance = instanceMethod.invoke(null);
            if (apiInstance == null) {
                return false;
            }

            Method stateMethod = apiClass.getMethod(methodName);
            return Boolean.TRUE.equals(stateMethod.invoke(apiInstance));
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException ignored) {
            return false;
        }
    }
}
