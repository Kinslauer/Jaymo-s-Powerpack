package com.jaymoskingdoms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): every magic effect has a type (beam, blast, miasma and so on) and every effect of a type is drawn
 * by the same asset. The types and their drawing live in Jaymo's Powers ({@code core.fx.MagicFx}); this is Kingdoms'
 * door to them (Kingdoms never compiles against Powers). Mana and ki are one well, so a court wizard's bolt and a
 * Z Fighter's wave are the same beam.
 * <ul>
 * <li>{@link #wisp}: a mote of light, or a puff of miasma if the colour is dark. Where a redstone dust was.</li>
 * <li>{@link #beam}, {@link #blast}, {@link #ring}, {@link #orb}, {@link #sigil}: the shapes.</li>
 * <li>{@link #burst}: what a one-off cloud of dust was: a blast with motes thrown out of it.</li>
 * </ul>
 * Without Powers a wisp is the old dust, a beam a line of it, a blast and a ring a cloud of it, and the rest nothing.
 */
public final class Fx {
	private static boolean looked;
	private static @Nullable MethodHandle wisp, mote, miasma, beam, blast, ring, orb, sigil, burst;

	private Fx() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Class<?> fx = Class.forName("com.jaymospowers.core.fx.MagicFx");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodType particle = MethodType.methodType(ParticleOptions.class, int.class, float.class);
			wisp = lookup.findStatic(fx, "wisp", particle);
			mote = lookup.findStatic(fx, "mote", particle);
			miasma = lookup.findStatic(fx, "miasma", particle);
			beam = lookup.findStatic(fx, "beam", MethodType.methodType(void.class, ServerLevel.class, Vec3.class, Vec3.class, float.class, int.class, int.class));
			blast = lookup.findStatic(fx, "blast", MethodType.methodType(void.class, ServerLevel.class, Vec3.class, float.class, int.class));
			ring = lookup.findStatic(fx, "ring", MethodType.methodType(void.class, ServerLevel.class, Vec3.class, float.class, int.class));
			orb = lookup.findStatic(fx, "orb", MethodType.methodType(void.class, ServerLevel.class, Vec3.class, float.class, int.class, int.class));
			sigil = lookup.findStatic(fx, "sigil", MethodType.methodType(void.class, ServerLevel.class, Vec3.class, float.class, int.class, int.class));
			burst = lookup.findStatic(fx, "burst", MethodType.methodType(void.class, ServerLevel.class, double.class, double.class, double.class, int.class,
				float.class, int.class, double.class, double.class, double.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			wisp = mote = miasma = beam = blast = ring = orb = sigil = burst = null;
		}
	}

	private static ParticleOptions particle(@Nullable MethodHandle handle, int color, float size) {
		if (handle != null) {
			try {
				return (ParticleOptions)handle.invoke(color & 0xFFFFFF, size);
			} catch (Throwable e) {
				// fall through to the dust
			}
		}
		return new DustParticleOptions(color & 0xFFFFFF, size);
	}

	/** A mote of light, or miasma if {@code color} (0xRRGGBB) is dark; {@code size} on redstone dust's scale. */
	public static ParticleOptions wisp(int color, float size) {
		look();
		return particle(wisp, color, size);
	}

	/** A mote of light whatever the colour. */
	public static ParticleOptions mote(int color, float size) {
		look();
		return particle(mote, color, size);
	}

	/** A puff of miasma whatever the colour (gas, smoke, blight). */
	public static ParticleOptions miasma(int color, float size) {
		look();
		return particle(miasma, color, size);
	}

	/** A beam from {@code from} to {@code to}, {@code radius} thick, for {@code ticks}. */
	public static void beam(ServerLevel level, Vec3 from, Vec3 to, float radius, int color, int ticks) {
		look();
		if (beam != null) {
			try {
				beam.invoke(level, from, to, radius, color, ticks);
				return;
			} catch (Throwable e) {
				// fall through
			}
		}
		Vec3 step = to.subtract(from);
		int n = Math.max(2, (int)(step.length() * 3.0));
		ParticleOptions dust = new DustParticleOptions(color & 0xFFFFFF, 1.2F);
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(step.scale(i / (double)n));
			level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** A blast at {@code at}, swelling out to {@code radius}. */
	public static void blast(ServerLevel level, Vec3 at, float radius, int color) {
		look();
		if (blast != null) {
			try {
				blast.invoke(level, at, radius, color);
				return;
			} catch (Throwable e) {
				// fall through
			}
		}
		level.sendParticles(new DustParticleOptions(color & 0xFFFFFF, 1.6F), at.x, at.y, at.z, 24, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
	}

	/** A shockwave over the ground at {@code at}, running out to {@code radius}. */
	public static void ring(ServerLevel level, Vec3 at, float radius, int color) {
		look();
		if (ring != null) {
			try {
				ring.invoke(level, at, radius, color);
				return;
			} catch (Throwable e) {
				// fall through
			}
		}
		level.sendParticles(new DustParticleOptions(color & 0xFFFFFF, 1.4F), at.x, at.y + 0.1, at.z, 24, radius * 0.5, 0.05, radius * 0.5, 0.0);
	}

	/** A ball of power held at {@code at} for {@code ticks}. */
	public static void orb(ServerLevel level, Vec3 at, float radius, int color, int ticks) {
		look();
		if (orb != null) {
			try {
				orb.invoke(level, at, radius, color, ticks);
			} catch (Throwable e) {
				// nothing without Powers
			}
		}
	}

	/** A circle of marks on the ground at {@code at}, {@code radius} across, for {@code ticks}. */
	public static void sigil(ServerLevel level, Vec3 at, float radius, int color, int ticks) {
		look();
		if (sigil != null) {
			try {
				sigil.invoke(level, at, radius, color, ticks);
			} catch (Throwable e) {
				// nothing without Powers
			}
		}
	}

	/** What a one-off cloud of {@code count} dusts spread over ({@code dx}, {@code dy}, {@code dz}) was. */
	public static void burst(ServerLevel level, double x, double y, double z, int color, float size, int count, double dx, double dy, double dz) {
		look();
		if (burst != null) {
			try {
				burst.invoke(level, x, y, z, color, size, count, dx, dy, dz);
				return;
			} catch (Throwable e) {
				// fall through
			}
		}
		level.sendParticles(new DustParticleOptions(color & 0xFFFFFF, size), x, y, z, count, dx, dy, dz, 0.0);
	}
}
