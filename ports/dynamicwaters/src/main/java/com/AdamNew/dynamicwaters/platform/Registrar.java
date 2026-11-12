package com.AdamNew.dynamicwaters.platform;

import com.AdamNew.dynamicwaters.DynamicWaters;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public final class Registrar<T> {
    private final Registry<T> registry;

    @SuppressWarnings("unchecked")
    private Registrar(ResourceKey<? extends Registry<T>> key) {
        this.registry = (Registry<T>) net.minecraft.core.registries.BuiltInRegistries.REGISTRY.getValue(key.identifier());
    }

    public <I extends T> Supplier<I> register(String name, Supplier<? extends I> factory) {
        final I value = factory.get();
        Registry.register(registry, net.minecraft.resources.Identifier.fromNamespaceAndPath(DynamicWaters.MODID, name), value);
        return () -> value;
    }

    public void attach() {
    }

    /** The key an object registered under {@code name} here will have (26.3 wants it on the object's properties). */
    public ResourceKey<T> key(String name) {
        return ResourceKey.create(registry.key(), net.minecraft.resources.Identifier.fromNamespaceAndPath(DynamicWaters.MODID, name));
    }

    public static <T> Registrar<T> create(ResourceKey<? extends Registry<T>> registry) {
        return new Registrar<>(registry);
    }
}
