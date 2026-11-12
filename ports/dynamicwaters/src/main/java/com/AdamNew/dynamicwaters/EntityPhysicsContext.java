package com.AdamNew.dynamicwaters;

import net.minecraft.world.entity.Entity;

public class EntityPhysicsContext {
    private static final ThreadLocal<Entity> CURRENT_ENTITY = new ThreadLocal<>();

    public static void set(Entity entity) {
        CURRENT_ENTITY.set(entity);
    }

    public static Entity get() {
        return CURRENT_ENTITY.get();
    }

    public static void clear() {
        CURRENT_ENTITY.remove();
    }
}
