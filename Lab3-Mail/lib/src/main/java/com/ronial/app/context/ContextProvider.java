package com.ronial.app.context;

import java.util.HashMap;
import java.util.Map;

public final class ContextProvider {
    private static final Map<Class<? extends Context>, Object> models;
    static {
        models = new HashMap<>();
    }
    private ContextProvider() {}
    public static <T> T get(Class<? extends Context> clazz) {
        if (models.containsKey(clazz)) {
            Object object =  models.get(clazz);
            return (T) object;
        }
        throw new RuntimeException("No such class " + clazz.getName());
    }
    public static void register(Class<? extends Context> clazz, Object model) {
        if (models.containsKey(clazz)) {
            throw new RuntimeException("Model already registered for " + clazz.getName());
        }
        if (clazz.isInstance(model)) {
            models.put(clazz, model);
            return;
        }
        throw new RuntimeException(model + " is not an instance of " + clazz.getName());
    }
}