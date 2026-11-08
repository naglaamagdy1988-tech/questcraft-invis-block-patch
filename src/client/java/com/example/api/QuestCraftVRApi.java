package com.example.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional compatibility bridge between QuestCraft-compatible mods and Vivecraft.
 *
 * <p>The diagnostic methods inspect the API without initializing Vivecraft.
 * This lets a mod distinguish a missing API from an API with an incompatible
 * shape before attempting to use it.</p>
 */
public final class QuestCraftVRApi {
    private static final String VIVECRAFT_CLIENT_API =
            "org.vivecraft.api.client.VRClientAPI";

    private QuestCraftVRApi() {
    }

    public enum ApiStatus {
        AVAILABLE,
        MISSING,
        INCOMPATIBLE
    }

    /**
     * Checks the public API shape without initializing Vivecraft.
     *
     * @return AVAILABLE when the expected methods exist, MISSING when the
     * API class is absent, or INCOMPATIBLE when it exists but does not match
     * the methods this bridge needs.
     */
    public static ApiStatus getApiStatus() {
        final Class<?> apiClass;
        try {
            apiClass = Class.forName(
                    VIVECRAFT_CLIENT_API,
                    false,
                    QuestCraftVRApi.class.getClassLoader()
            );
        } catch (ClassNotFoundException e) {
            return ApiStatus.MISSING;
        } catch (LinkageError | SecurityException e) {
            return ApiStatus.INCOMPATIBLE;
        }

        try {
            Method instance = apiClass.getMethod("instance");
            if (!Modifier.isStatic(instance.getModifiers())
                    || !apiClass.isAssignableFrom(instance.getReturnType())) {
                return ApiStatus.INCOMPATIBLE;
            }

            if (!hasBooleanMethod(apiClass, "isVRInitialized")
                    || !hasBooleanMethod(apiClass, "isVRActive")
                    || !hasBooleanMethod(apiClass, "isSeated")
                    || !hasBooleanMethod(apiClass, "isLeftHanded")) {
                return ApiStatus.INCOMPATIBLE;
            }

            return ApiStatus.AVAILABLE;
        } catch (ReflectiveOperationException | LinkageError
                 | SecurityException e) {
            return ApiStatus.INCOMPATIBLE;
        }
    }

    private static boolean hasBooleanMethod(Class<?> type, String name)
            throws NoSuchMethodException {
        Method method = type.getMethod(name);
        return method.getReturnType() == boolean.class
                && !Modifier.isStatic(method.getModifiers());
    }

    /**
     * Backwards-compatible presence check. Use getApiStatus() when the
     * distinction between missing and incompatible matters.
     */
    public static boolean isVivecraftApiAvailable() {
        return getApiStatus() != ApiStatus.MISSING;
    }

    public static boolean isVRInitialized() {
        return invokeBooleanApiMethod("isVRInitialized");
    }

    public static boolean isVRActive() {
        return invokeBooleanApiMethod("isVRActive");
    }

    public static boolean isSeated() {
        return invokeBooleanApiMethod("isSeated");
    }

    public static boolean isLeftHanded() {
        return invokeBooleanApiMethod("isLeftHanded");
    }

    public static boolean isInVR() {
        return isVRActive();
    }

    private static boolean invokeBooleanApiMethod(String methodName) {
        if (getApiStatus() != ApiStatus.AVAILABLE) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName(
                    VIVECRAFT_CLIENT_API,
                    true,
                    QuestCraftVRApi.class.getClassLoader()
            );

            Method instanceMethod = apiClass.getMethod("instance");
            Object apiInstance = instanceMethod.invoke(null);
            if (apiInstance == null) {
                return false;
            }

            Method stateMethod = apiClass.getMethod(methodName);
            return Boolean.TRUE.equals(stateMethod.invoke(apiInstance));
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException | SecurityException ignored) {
            return false;
        }
    }
}
