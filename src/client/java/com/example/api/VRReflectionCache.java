package com.example.api;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reuses reflection metadata across pose snapshots without caching live poses. */
final class VRReflectionCache {
    private static final String CLIENT_API =
            "org.vivecraft.api.client.VRClientAPI";
    private static final String BODY_PART =
            "org.vivecraft.api.data.VRBodyPart";

    private static final ClassValue<Map<MethodKey, Method>> METHODS =
            new ClassValue<>() {
                @Override
                protected Map<MethodKey, Method> computeValue(Class<?> type) {
                    return new HashMap<>();
                }
            };

    private static final ClassValue<Map<String, Object>> ENUM_CONSTANTS =
            new ClassValue<>() {
                @Override
                protected Map<String, Object> computeValue(Class<?> type) {
                    Object[] constants = type.getEnumConstants();
                    if (constants == null) return Map.of();
                    Map<String, Object> values = new HashMap<>();
                    for (Object constant : constants) {
                        if (constant instanceof Enum<?> enumValue) {
                            values.put(enumValue.name(), constant);
                        }
                    }
                    return Map.copyOf(values);
                }
            };

    private static volatile Object apiInstance;
    private static volatile Class<?> bodyPartClass;

    private VRReflectionCache() {
    }

    static Class<?> clientApiClass(boolean initialize)
            throws ClassNotFoundException {
        return Class.forName(CLIENT_API, initialize,
                QuestCraftVRApi.class.getClassLoader());
    }

    static Class<?> bodyPartClass() throws ClassNotFoundException {
        Class<?> cached = bodyPartClass;
        if (cached != null) return cached;
        synchronized (VRReflectionCache.class) {
            cached = bodyPartClass;
            if (cached == null) {
                cached = Class.forName(BODY_PART, false,
                        QuestCraftVRApi.class.getClassLoader());
                bodyPartClass = cached;
            }
        }
        return cached;
    }

    static Object clientApiInstance() throws ReflectiveOperationException {
        Object cached = apiInstance;
        if (cached != null) return cached;
        synchronized (VRReflectionCache.class) {
            cached = apiInstance;
            if (cached == null) {
                Class<?> apiClass = clientApiClass(true);
                cached = method(apiClass, "instance").invoke(null);
                if (cached != null) apiInstance = cached;
            }
        }
        return cached;
    }

    static Method method(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Map<MethodKey, Method> methods = METHODS.get(type);
        MethodKey key = new MethodKey(name, List.copyOf(Arrays.asList(parameterTypes)));
        synchronized (methods) {
            Method cached = methods.get(key);
            if (cached != null) return cached;
            Method resolved = type.getMethod(name, parameterTypes);
            methods.put(key, resolved);
            return resolved;
        }
    }

    static Object enumConstant(Class<?> enumClass, String name) {
        if (!enumClass.isEnum()) return null;
        return ENUM_CONSTANTS.get(enumClass).get(name);
    }

    private record MethodKey(String name, List<Class<?>> parameterTypes) {
    }
}
