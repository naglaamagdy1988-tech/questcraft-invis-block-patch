package com.example.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Public, optional compatibility bridge between QuestCraft-compatible mods
 * and the Vivecraft client API.
 *
 * <p>Mods can use this class without importing Vivecraft implementation
 * classes. API shape checks do not initialize Vivecraft; state queries do.</p>
 */
public final class QuestCraftVRApi {
    /** Version of this bridge's public API contract. */
    public static final int API_VERSION = 1;

    private static volatile ApiStatus cachedApiStatus;

    private QuestCraftVRApi() {
    }

    public enum ApiStatus {
        AVAILABLE,
        MISSING,
        INCOMPATIBLE
    }

    /** Result of one safe VR-state query. */
    public enum StateStatus {
        READY,
        NOT_INITIALIZED,
        API_MISSING,
        API_INCOMPATIBLE,
        ERROR
    }

    /**
     * Immutable snapshot so callers can read several VR flags from one API
     * query instead of repeatedly reflecting into Vivecraft.
     */
    public static final class VRState {
        private final ApiStatus apiStatus;
        private final StateStatus status;
        private final boolean initialized;
        private final boolean active;
        private final boolean seated;
        private final boolean leftHanded;
        private final String error;

        private VRState(
                ApiStatus apiStatus,
                StateStatus status,
                boolean initialized,
                boolean active,
                boolean seated,
                boolean leftHanded,
                String error
        ) {
            this.apiStatus = apiStatus;
            this.status = status;
            this.initialized = initialized;
            this.active = active;
            this.seated = seated;
            this.leftHanded = leftHanded;
            this.error = error;
        }

        public ApiStatus getApiStatus() {
            return apiStatus;
        }

        public StateStatus getStatus() {
            return status;
        }

        public boolean isApiAvailable() {
            return apiStatus == ApiStatus.AVAILABLE;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isSeated() {
            return seated;
        }

        public boolean isLeftHanded() {
            return leftHanded;
        }

        /**
         * Returns a concise diagnostic when the query failed; otherwise null.
         */
        public String getError() {
            return error;
        }
    }

    /**
     * Checks the public API shape without initializing Vivecraft.
     *
     * @return AVAILABLE when the expected methods exist, MISSING when the
     * API class is absent, or INCOMPATIBLE when it does not match this bridge.
     */
    public static ApiStatus getApiStatus() {
        ApiStatus cachedStatus = cachedApiStatus;
        if (cachedStatus != null) return cachedStatus;

        final Class<?> apiClass;
        try {
            apiClass = VRReflectionCache.clientApiClass(false);
        } catch (ClassNotFoundException e) {
            return ApiStatus.MISSING;
        } catch (LinkageError | SecurityException e) {
            return cacheApiStatus(ApiStatus.INCOMPATIBLE);
        }

        try {
            Method instance = VRReflectionCache.method(apiClass, "instance");
            if (!Modifier.isStatic(instance.getModifiers())
                    || !apiClass.isAssignableFrom(instance.getReturnType())) {
                return cacheApiStatus(ApiStatus.INCOMPATIBLE);
            }

            if (!hasBooleanMethod(apiClass, "isVRInitialized")
                    || !hasBooleanMethod(apiClass, "isVRActive")
                    || !hasBooleanMethod(apiClass, "isSeated")
                    || !hasBooleanMethod(apiClass, "isLeftHanded")) {
                return cacheApiStatus(ApiStatus.INCOMPATIBLE);
            }

            return cacheApiStatus(ApiStatus.AVAILABLE);
        } catch (ReflectiveOperationException | LinkageError
                 | SecurityException e) {
            return cacheApiStatus(ApiStatus.INCOMPATIBLE);
        }
    }

    private static ApiStatus cacheApiStatus(ApiStatus status) {
        cachedApiStatus = status;
        return status;
    }

    private static boolean hasBooleanMethod(Class<?> type, String name)
            throws NoSuchMethodException {
        Method method = VRReflectionCache.method(type, name);
        return method.getReturnType() == boolean.class
                && !Modifier.isStatic(method.getModifiers());
    }

    /**
     * Queries all supported state flags once and returns an immutable result.
     * A failed or unavailable Vivecraft API is reported in the result rather
     * than escaping as an exception into the calling mod.
     */
    public static VRState getVRState() {
        ApiStatus apiStatus = getApiStatus();
        if (apiStatus == ApiStatus.MISSING) {
            return unavailable(apiStatus, StateStatus.API_MISSING,
                    "Vivecraft client API class was not found");
        }
        if (apiStatus == ApiStatus.INCOMPATIBLE) {
            return unavailable(apiStatus, StateStatus.API_INCOMPATIBLE,
                    "Vivecraft client API does not match the expected methods");
        }

        try {
            Class<?> apiClass = VRReflectionCache.clientApiClass(true);
            Object apiInstance = VRReflectionCache.clientApiInstance();
            if (apiInstance == null) {
                return unavailable(apiStatus, StateStatus.ERROR,
                        "VRClientAPI.instance() returned null");
            }

            boolean initialized = invokeBoolean(apiClass, apiInstance, "isVRInitialized");
            if (!initialized) {
                return new VRState(apiStatus, StateStatus.NOT_INITIALIZED,
                        false, false, false, false, null);
            }

            boolean active = invokeBoolean(apiClass, apiInstance, "isVRActive");
            boolean seated = invokeBoolean(apiClass, apiInstance, "isSeated");
            boolean leftHanded = invokeBoolean(apiClass, apiInstance,
                    "isLeftHanded");

            return new VRState(apiStatus, StateStatus.READY, true, active,
                    seated, leftHanded, null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return unavailable(apiStatus, StateStatus.ERROR,
                    describeFailure(cause));
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException e) {
            return unavailable(apiStatus, StateStatus.ERROR,
                    describeFailure(e));
        }
    }

    private static boolean invokeBoolean(
            Class<?> apiClass,
            Object apiInstance,
            String methodName
    ) throws ReflectiveOperationException {
        Method method = VRReflectionCache.method(apiClass, methodName);
        return Boolean.TRUE.equals(method.invoke(apiInstance));
    }

    private static VRState unavailable(
            ApiStatus apiStatus,
            StateStatus status,
            String error
    ) {
        return new VRState(apiStatus, status, false, false, false, false, error);
    }

    private static String describeFailure(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /**
     * Backwards-compatible presence check. This reports whether the class
     * exists, even if its method shape is incompatible; use getApiStatus()
     * when compatibility matters.
     */
    public static boolean isVivecraftApiAvailable() {
        return getApiStatus() != ApiStatus.MISSING;
    }

    /**
     * Convenience accessors retained for simple callers. Mods that need
     * multiple flags should call getVRState() once and reuse the snapshot.
     */
    public static boolean isVRInitialized() {
        return getVRState().isInitialized();
    }

    public static boolean isVRActive() {
        return getVRState().isActive();
    }

    public static boolean isSeated() {
        return getVRState().isSeated();
    }

    public static boolean isLeftHanded() {
        return getVRState().isLeftHanded();
    }

    public static boolean isInVR() {
        return isVRActive();
    }
}
