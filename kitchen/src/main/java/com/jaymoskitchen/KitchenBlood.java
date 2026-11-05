package com.jaymoskitchen;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.63) Blood for Kitchen's bodies, hangings, grates and machines. Andy (2026-11-05): "the redstone like particle effect
 * needs to go as well. it does not ever resemble blood." Kitchen bled redstone-block crumbs and red dust; now it sends
 * Jaymo's Powers' red blood (small spatters of Iron's Simple Blood's drops), and with no Powers, none at all.
 */
public final class KitchenBlood {
	private static @Nullable ParticleOptions red;
	private static boolean looked;

	private KitchenBlood() {
	}

	/** Powers' red blood fleck, or null without Powers. */
	static @Nullable ParticleOptions red() {
		if (!looked) {
			looked = true;
			if (BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.fromNamespaceAndPath(OptionalItems.POWERS, "blood_fleck_red"))
				instanceof net.minecraft.core.particles.SimpleParticleType fleck) {
				red = fleck;
			}
		}
		return red;
	}

	/** Sends {@code count} of it at {@code x, y, z} as {@code sendParticles} would (nothing without Powers). */
	public static void send(ServerLevel level, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		ParticleOptions blood = red();
		if (blood != null) {
			level.sendParticles(blood, x, y, z, count, dx, dy, dz, speed);
		}
	}
}
