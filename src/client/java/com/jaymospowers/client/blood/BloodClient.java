package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodParticles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * (0.15.93) The blood's client side (Iron's Simple Blood's {@code ClientEvents}, ported): each drop kind's sprites, the
 * {@link BloodParticles.Spray} that picks one of them at random, and the {@link BloodPuddle}s' own group.
 */
public final class BloodClient {
	/** The red of a drop sent without a colour (a bare {@code /particle jaymospowers:blood_drop}). */
	static final int RED = 0x80000D;

	/** One drop kind, made in a spray's colour and size. */
	@FunctionalInterface
	interface Kind {
		Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int color, float scale);
	}

	/** The drop kinds a spray picks from, by type (filled as the particle sprites load). */
	private static final Map<SimpleParticleType, Kind> KINDS = new LinkedHashMap<>();

	private BloodClient() {
	}

	public static void init() {
		ParticleGroupRegistry.register(BloodDecals.TYPE, BloodDecals.Group::new);
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		kind(registry, BloodParticles.DROP, 1, 16, false);
		kind(registry, BloodParticles.SPAT_1, 4, 32, false);
		kind(registry, BloodParticles.SPAT_2, 4, 32, false);
		kind(registry, BloodParticles.SPURT, 3, 32, true);
		kind(registry, BloodParticles.SWIPE_1, 7, 32, true);
		kind(registry, BloodParticles.SWIPE_2, 8, 32, true);
		registry.register(BloodParticles.SPRAY, sprites -> (options, level, x, y, z, xd, yd, zd, random) -> {
			List<Kind> kinds = new ArrayList<>(KINDS.values());
			return kinds.isEmpty() ? null : kinds.get(random.nextInt(kinds.size())).create(level, x, y, z, xd, yd, zd, options.color(),
				options.scale());
		});
		registry.register(BloodParticles.PUDDLE,
			sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new BloodPuddle(level, x, y, z, sprites, options.color(), options.scale()));
	}

	/** A drop kind: a sheet of {@code frames} frames, each {@code size} pixels, that turns to its flight if {@code directional}. */
	private static void kind(ParticleProviderRegistry registry, SimpleParticleType type, int frames, int size, boolean directional) {
		registry.register(type, sprites -> {
			Kind kind = (level, x, y, z, xd, yd, zd, color, scale) -> make(level, x, y, z, xd, yd, zd, sprites, frames, size, directional,
				color, scale);
			KINDS.put(type, kind);
			return (options, level, x, y, z, xd, yd, zd, random) -> kind.create(level, x, y, z, xd, yd, zd, RED, 1.0F);
		});
	}

	private static Particle make(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
		int frames, int size, boolean directional, int color, float scale) {
		return new BloodDrop(level, x, y, z, xd, yd, zd, sprites, frames, size, directional, color, scale);
	}
}
