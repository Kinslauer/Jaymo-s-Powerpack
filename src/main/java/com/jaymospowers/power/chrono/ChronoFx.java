package com.jaymospowers.power.chrono;

import com.jaymospowers.core.Fate;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Chrono's particles, marked by its owner's fate. All of Chrono's particles go through here, server ({@link #particles})
 * and client ({@link #particle}) alike, with the style of whoever's power made them ({@link #style}):
 * <ul>
 * <li><b>Corrupted:</b> a fractured timeline. Every effect is shadowed by a black clone of itself a little off where it
 * really is, and dark (tinted) glass splinters off it.</li>
 * <li><b>Ascended:</b> space-time cracking. Clear glass shatters off every effect, and every punch lands with a faint
 * crack of breaking glass ({@link #crackSound}).</li>
 * <li><b>Both:</b> the black clones, with clear and dark glass splintering together.</li>
 * <li>Neither: just the effect.</li>
 * </ul>
 */
public final class ChronoFx {
	/** Style bits: plain, corrupted (black clones, dark glass), ascended (clear glass), both. */
	public static final int PLAIN = 0, CORRUPTED = 1, ASCENDED = 2, BOTH = CORRUPTED | ASCENDED;
	/** The black clones: how far off the real effect they sit (blocks), and their share of its count. */
	static final double CLONE_OFFSET = 0.25;
	static final float CLONE_SHARE = 0.5F;
	/** The glass: its share of the effect's count, at least one in this many single particles, and its speed. */
	static final float CRACK_SHARE = 0.25F;
	static final int CRACK_ONE_IN = 4;
	static final double CRACK_SPEED = 0.05;
	/** How loud an ascended punch's glass crack is. */
	static final float CRACK_VOLUME = 0.35F;

	private static final DustParticleOptions BLACK = new DustParticleOptions(0x0A0A0E, 1.0F);
	private static BlockParticleOption darkGlass;
	private static BlockParticleOption clearGlass;

	private ChronoFx() {
	}

	/** The style of the power {@code owner} holds (plain with no owner). Fate is synced, so this works on either side. */
	public static int style(@Nullable Player owner) {
		if (owner == null) {
			return PLAIN;
		}
		return (Fate.isCorrupted(owner) ? CORRUPTED : 0) | (Fate.isAscended(owner) ? ASCENDED : 0);
	}

	private static BlockParticleOption shards(BlockState state) {
		return new BlockParticleOption(ParticleTypes.BLOCK, state);
	}

	/** Dark glass (tinted glass breaking) for a corrupted power, clear glass for an ascended one, either for both. */
	private static @Nullable BlockParticleOption glass(int style, RandomSource random) {
		if (darkGlass == null) {
			darkGlass = shards(BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("minecraft", "tinted_glass"))
				.orElse(Blocks.OBSIDIAN).defaultBlockState());
			clearGlass = shards(Blocks.GLASS.defaultBlockState());
		}
		return switch (style) {
			case CORRUPTED -> darkGlass;
			case ASCENDED -> clearGlass;
			case BOTH -> random.nextBoolean() ? darkGlass : clearGlass;
			default -> null;
		};
	}

	/** {@link ServerLevel#sendParticles}, marked by {@code owner}'s fate. */
	public static void particles(ServerLevel level, @Nullable Player owner, ParticleOptions particle, double x, double y, double z,
		int count, double dx, double dy, double dz, double speed) {
		particles(level, style(owner), particle, x, y, z, count, dx, dy, dz, speed);
	}

	/** {@link ServerLevel#sendParticles}, in a given style. */
	public static void particles(ServerLevel level, int style, ParticleOptions particle, double x, double y, double z, int count,
		double dx, double dy, double dz, double speed) {
		level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
		if (style == PLAIN) {
			return;
		}
		RandomSource random = level.getRandom();
		if ((style & CORRUPTED) != 0) {
			double ox = (random.nextDouble() - 0.5) * 2.0 * CLONE_OFFSET;
			double oy = (random.nextDouble() - 0.5) * CLONE_OFFSET;
			double oz = (random.nextDouble() - 0.5) * 2.0 * CLONE_OFFSET;
			if (count == 0) {
				// a single particle sent along (dx, dy, dz): its clone flies alongside
				level.sendParticles(BLACK, x + ox, y + oy, z + oz, 0, dx, dy, dz, speed);
			} else {
				level.sendParticles(BLACK, x + ox, y + oy, z + oz, Math.max(1, Math.round(count * CLONE_SHARE)), dx, dy, dz, 0.0);
			}
		}
		int cracks = count == 0 ? 0 : Math.round(count * CRACK_SHARE);
		if (cracks > 0 || random.nextInt(CRACK_ONE_IN) == 0) {
			BlockParticleOption glass = glass(style, random);
			if (glass != null) {
				level.sendParticles(glass, x, y, z, Math.max(1, cracks), count == 0 ? 0.0 : dx, count == 0 ? 0.0 : dy,
					count == 0 ? 0.0 : dz, CRACK_SPEED);
			}
		}
	}

	/** {@link Level#addParticle} (client side), marked by {@code owner}'s fate. */
	public static void particle(Level level, @Nullable Player owner, ParticleOptions particle, double x, double y, double z,
		double vx, double vy, double vz) {
		level.addParticle(particle, x, y, z, vx, vy, vz);
		int style = style(owner);
		if (style == PLAIN) {
			return;
		}
		RandomSource random = level.getRandom();
		if ((style & CORRUPTED) != 0) {
			level.addParticle(BLACK, x + (random.nextDouble() - 0.5) * 2.0 * CLONE_OFFSET, y + (random.nextDouble() - 0.5) * CLONE_OFFSET,
				z + (random.nextDouble() - 0.5) * 2.0 * CLONE_OFFSET, vx, vy, vz);
		}
		if (random.nextInt(CRACK_ONE_IN) == 0) {
			BlockParticleOption glass = glass(style, random);
			if (glass != null) {
				level.addParticle(glass, x, y, z, 0.0, 0.0, 0.0);
			}
		}
	}

	/** An ascended power's punch cracks space-time: a faint breaking of glass where it lands. */
	public static void crackSound(ServerLevel level, @Nullable Player owner, double x, double y, double z) {
		if ((style(owner) & ASCENDED) != 0) {
			level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, CRACK_VOLUME, 1.4F + level.getRandom().nextFloat() * 0.4F);
		}
	}
}
