package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * (0.19.63) The wet weather's particle types, registered on both sides so their sprites load into the particle atlas.
 * Only the client makes them (the client's {@code client/wet}): water puddles left by rain and by dripping bodies, and
 * the light shafts seen under water.
 */
public final class WetParticles {
	public static final SimpleParticleType PUDDLE = simple("water_puddle");
	public static final SimpleParticleType LIGHT_SHAFT = simple("light_shaft");

	private WetParticles() {
	}

	/** Loads the class (and so registers the types). */
	public static void init() {
	}

	private static SimpleParticleType simple(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id(name), FabricParticleTypes.simple(true));
	}
}
