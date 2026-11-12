package com.AdamNew.dynamicwaters.registry;

import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

public class ModParticles {
    public static final Registrar<ParticleType<?>> PARTICLE_TYPES = Registrar.create(Registries.PARTICLE_TYPE);

    public static final Supplier<SimpleParticleType> RIVER_BUBBLE =
            PARTICLE_TYPES.register("river_bubble", net.fabricmc.fabric.api.particle.v1.FabricParticleTypes::simple);
}
