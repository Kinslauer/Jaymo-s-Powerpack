package com.jaymospowers.core.air;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Sky, light and air (Reference Mods patch 14): the particle types whose sprites the client's {@code client/air} draws
 * with, registered on both sides so their sprites load into the particle atlas. Only the client makes them: soot
 * scorched onto what a fire touches, the cooled crust at a lava pool's edge, and fallen leaves floating on water.
 */
public final class AirParticles {
	public static final SimpleParticleType SCORCH = simple("scorch");
	public static final SimpleParticleType LAVA_CRUST = simple("lava_crust");
	public static final SimpleParticleType WATER_LEAF = simple("water_leaf");

	private AirParticles() {
	}

	/** Loads the class (and so registers the types). */
	public static void init() {
	}

	private static SimpleParticleType simple(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id(name), FabricParticleTypes.simple(true));
	}
}
