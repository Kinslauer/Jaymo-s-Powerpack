package com.AdamNew.dynamicwaters.registry;

import com.AdamNew.dynamicwaters.entity.IceFloe;
import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final Registrar<EntityType<?>> ENTITY_TYPES = Registrar.create(Registries.ENTITY_TYPE);

    public static final Supplier<EntityType<IceFloe>> ICE_FLOE = ENTITY_TYPES.register("ice_floe",
            () -> EntityType.Builder.<IceFloe>of(IceFloe::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .noSave()
                    .build(ENTITY_TYPES.key("ice_floe")));
}
