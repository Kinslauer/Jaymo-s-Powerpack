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

	/** (0.5.86) Powers' {@code BladeBlood.bodyBlood(Entity)} and {@code BloodParticles.fleck(int, float)}, by reflection. */
	private static java.lang.invoke.@Nullable MethodHandle bodyBlood, fleckOf;
	private static boolean lookedForBody;
	/** Powers' {@code BladeBlood.NO_BLOOD}. */
	private static final int NO_BLOOD = -1;

	/**
	 * (0.5.86) Andy: "Kitchen's own blood ... double check that". {@code body}'s own blood (a living one's, or a corpse's: an
	 * Emberborn's magma, a zombie's rotten blood), {@code count} of it at {@code x, y, z}; nothing from a body with no blood
	 * (a skeleton, a Stoneborn) or without Powers. Falls back to plain red with an older Powers.
	 */
	public static void send(ServerLevel level, net.minecraft.world.entity.Entity body, double x, double y, double z, int count, double dx,
		double dy, double dz, double speed) {
		if (!lookedForBody) {
			lookedForBody = true;
			try {
				var lookup = java.lang.invoke.MethodHandles.publicLookup();
				bodyBlood = lookup.findStatic(Class.forName("com.jaymospowers.core.BladeBlood"), "bodyBlood",
					java.lang.invoke.MethodType.methodType(int.class, net.minecraft.world.entity.Entity.class));
				Class<?> particles = Class.forName("com.jaymospowers.core.blood.BloodParticles");
				fleckOf = lookup.findStatic(particles, "fleck", java.lang.invoke.MethodType.methodType(
					Class.forName("com.jaymospowers.core.blood.BloodParticles$Fleck"), int.class, float.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				bodyBlood = null;
				fleckOf = null;
			}
		}
		if (bodyBlood != null && fleckOf != null) {
			try {
				int color = (int) bodyBlood.invoke(body);
				if (color != NO_BLOOD) {
					level.sendParticles((ParticleOptions) fleckOf.invoke(color, 1.0F), x, y, z, count, dx, dy, dz, speed);
				}
				return;
			} catch (Throwable e) {
				bodyBlood = null;
			}
		}
		send(level, x, y, z, count, dx, dy, dz, speed);
	}

	/** Sends {@code count} of it at {@code x, y, z} as {@code sendParticles} would (nothing without Powers). */
	public static void send(ServerLevel level, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		ParticleOptions blood = red();
		if (blood != null) {
			level.sendParticles(blood, x, y, z, count, dx, dy, dz, speed);
		}
	}
}
