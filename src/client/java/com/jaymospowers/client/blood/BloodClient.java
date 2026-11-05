package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodParticles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.93) The blood's client side (Iron's Simple Blood's {@code ClientEvents}, ported): each drop kind's sprites, the
 * {@link BloodParticles.Spray} that picks one of them at random, the {@link BloodPuddle}s' own group, and (0.15.109) the
 * flecks (since 0.20.51 small spatters, see {@link #FLECK_KEEP}).
 */
public final class BloodClient {
	/** The red of a drop sent without a colour (a bare {@code /particle jaymospowers:blood_drop}). */
	static final int RED = 0x80000D;
	/**
	 * (0.20.51) Andy (2026-11-05): "the redstone like particle effect needs to go ... it does not ever resemble blood". The
	 * plain drop's sprites (and the still fleck's, the same ones) are a scatter of single pixels, redstone dust by any other
	 * name: a spray no longer picks the plain drop, and a fleck comes as a spatter, spurt or swipe instead (only this share
	 * of them, as flecks were sent by the dozen), this much smaller.
	 */
	static final float FLECK_KEEP = 0.3F, FLECK_SCALE = 0.6F;

	/** One drop kind, made in a spray's colour and size. */
	@FunctionalInterface
	interface Kind {
		Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int color, float scale, boolean stains);
	}

	/** The drop kinds a spray picks from, by type (filled as the particle sprites load). */
	private static final Map<SimpleParticleType, Kind> KINDS = new LinkedHashMap<>();
	/** (0.17.182) The server's lasting stains lying in {@link #stainsIn}, by their mark id. */
	private static final Map<Long, BloodPuddle> STAINS = new HashMap<>();
	private static @Nullable ClientLevel stainsIn;
	/** (0.20.41) Sprites kept to draw with: the pools', a plain drop's, the footprints' and the runs down walls'. */
	private static @Nullable SpriteSet puddles, drops, prints, runs;

	private BloodClient() {
	}

	/** (0.20.86) A cut's drip is drawn this many times its spray's size. */
	static final float DRIP_DROP = 0.5F;

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
			if (!options.stains() && drops != null) {
				// (0.20.86) a cut's drip (the server lays its stain) is one small drop falling, not a splatter: drawn as a
				// spatter's star, at a spray's size, it burst out of the body like blood clipping through it (Jung's clip)
				return new BloodDrop(level, x, y, z, 0.0, 0.0, 0.0, drops, 4, 32, false, options.color(), options.scale() * DRIP_DROP, false).drip();
			}
			Kind kind = shaped(random);
			return kind == null ? null : kind.create(level, x, y, z, xd, yd, zd, options.color(), options.scale(), options.stains());
		});
		registry.register(BloodParticles.FLECK, sprites -> (options, level, x, y, z, xd, yd, zd, random) -> fleck(level, x, y, z, xd, yd, zd,
			random, options.color(), options.scale()));
		registry.register(BloodParticles.RED_FLECK, sprites -> (options, level, x, y, z, xd, yd, zd, random) -> fleck(level, x, y, z, xd, yd, zd,
			random, RED, 1.0F));
		registry.register(BloodParticles.PUDDLE, sprites -> {
			puddles = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> puddle(level, x, y, z, sprites, options);
		});
		// (0.20.41) sprites only: a bare /particle of either lays nothing
		registry.register(BloodParticles.PRINT_SPRITES, sprites -> {
			prints = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> null;
		});
		registry.register(BloodParticles.RUN_SPRITES, sprites -> {
			runs = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> null;
		});
	}

	/** (0.20.51) A drop kind with a shape (a spatter, spurt or swipe: not the plain drop's pixel scatter); null before any load. */
	private static @Nullable Kind shaped(net.minecraft.util.RandomSource random) {
		List<Kind> kinds = new ArrayList<>();
		KINDS.forEach((type, kind) -> {
			if (type != BloodParticles.DROP) {
				kinds.add(kind);
			}
		});
		return kinds.isEmpty() ? null : kinds.get(random.nextInt(kinds.size()));
	}

	/** (0.20.51) A fleck: now and then a small spatter, spurt or swipe, leaving no splash of its own. */
	private static @Nullable Particle fleck(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
		net.minecraft.util.RandomSource random, int color, float scale) {
		if (random.nextFloat() >= FLECK_KEEP) {
			return null;
		}
		Kind kind = shaped(random);
		return kind == null ? null : kind.create(level, x, y, z, xd * 0.5, yd * 0.5, zd * 0.5, color, scale * FLECK_SCALE, false);
	}

	/**
	 * (0.17.182) A pool, or one of the server's lasting stains ({@code BloodTrail}): a stain already lying here is grown
	 * and kept instead of laid twice.
	 */
	private static @Nullable Particle puddle(ClientLevel level, double x, double y, double z, SpriteSet sprites, BloodParticles.Puddle options) {
		if (options.mark() == 0L) {
			return new BloodPuddle(level, x, y, z, sprites, options.color(), options.scale(), 0, 0);
		}
		if (level != stainsIn) {
			STAINS.clear();
			stainsIn = level;
		}
		STAINS.values().removeIf(stain -> !stain.isAlive());
		BloodPuddle known = STAINS.get(options.mark());
		if (known != null) {
			known.restain(options.scale(), options.age(), options.life());
			return null;
		}
		BloodPuddle stain = new BloodPuddle(level, x, y, z, sprites, options.color(), options.scale(), options.age(), options.life(),
			options.shape());
		STAINS.put(options.mark(), stain);
		return stain;
	}

	static @Nullable SpriteSet puddleSprites() {
		return puddles;
	}

	static @Nullable SpriteSet printSprites() {
		return prints;
	}

	static @Nullable SpriteSet runSprites() {
		return runs;
	}

	/** (0.20.41) A drop of {@code color} this big falling straight down from here (off the end of a run on a wall). */
	static void drip(ClientLevel level, double x, double y, double z, int color, float scale) {
		if (drops != null) {
			net.minecraft.client.Minecraft.getInstance().particleEngine.add(new BloodDrop(level, x, y, z, 0.0, 0.0, 0.0, drops, 4, 32, false, color, scale * 0.5F,
				true).drip());
		}
	}

	/** A drop kind: a sheet of {@code frames} frames, each {@code size} pixels, that turns to its flight if {@code directional}. */
	private static void kind(ParticleProviderRegistry registry, SimpleParticleType type, int frames, int size, boolean directional) {
		registry.register(type, sprites -> {
			if (type == BloodParticles.SPAT_1) {
				// ((0.20.51) a wall's drip falls as a spatter's first frame, not the plain drop's pixel scatter)
				drops = sprites;
			}
			Kind kind = (level, x, y, z, xd, yd, zd, color, scale, stains) -> make(level, x, y, z, xd, yd, zd, sprites, frames, size,
				directional, color, scale, stains);
			KINDS.put(type, kind);
			return (options, level, x, y, z, xd, yd, zd, random) -> kind.create(level, x, y, z, xd, yd, zd, RED, 1.0F, true);
		});
	}

	private static Particle make(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
		int frames, int size, boolean directional, int color, float scale, boolean stains) {
		return new BloodDrop(level, x, y, z, xd, yd, zd, sprites, frames, size, directional, color, scale, stains);
	}
}
