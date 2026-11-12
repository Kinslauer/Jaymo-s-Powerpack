package com.jaymospowers.client.sound;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Steam, fire and bubble jets (Andy's ImmersiveFX pick, built as our own from vanilla particles and our own sounds):
 * <ul>
 * <li><b>Steam</b> hisses up off water wherever it touches lava;</li>
 * <li><b>fire spouts</b> burst now and then from some lava pools open to the air, a column of flame and embers;</li>
 * <li><b>bubble jets</b> rise from a few spots of the sea floor (sand, gravel, clay or mud at least {@link #VENT_DEPTH}
 * blocks under the surface), a steady stream with a gurgle.</li>
 * </ul>
 * The blocks around you are looked over a slice at a time ({@link #SLICES} ticks for the whole of them), the way
 * Ambience finds waterfalls, so it costs about {@link #RADIUS}² x {@link #HEIGHT} x 2 / {@link #SLICES} block reads a
 * tick. Which pools spout and which sea floor bubbles is fixed by the spot, so they stay where they are. The particles go
 * when Particles is set to Minimal; the sounds stay, within {@link ImmersiveSounds}' room.
 */
public final class Jets {
	/** Looked for this far around you (blocks out, blocks up and down), one slice of columns a tick. */
	static final int RADIUS = 14, HEIGHT = 6, SLICES = 24;
	/** One lava pool spot in this many spouts; one sea floor spot in this many bubbles. */
	static final int SPOUT_RARITY = 48, VENT_RARITY = 160;
	/** Bubbles rise only from this deep, or more. */
	static final int VENT_DEPTH = 3;
	/** A spout bursts once in this many ticks on average, for this long. */
	static final int SPOUT_EVERY = 160, SPOUT_TICKS = 24;
	/** The chance a tick a jet makes its sound (spouts: as each burst starts), and how loud. */
	static final float STEAM_SOUND = 0.025F, VENT_SOUND = 0.02F;
	static final float STEAM_VOLUME = 0.35F, FIRE_VOLUME = 0.6F, VENT_VOLUME = 0.3F;
	/** At most this many jets are kept. */
	static final int MAX = 64;

	enum Kind { STEAM, FIRE, BUBBLES }

	private static final class Jet {
		final BlockPos pos;
		final Kind kind;
		int burst;

		Jet(BlockPos pos, Kind kind) {
			this.pos = pos;
			this.kind = kind;
		}
	}

	private static final Map<Long, Jet> JETS = new HashMap<>();
	private static int slice;
	private static ClientLevel lastLevel;

	private Jets() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Jets::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null || mc.isPaused()) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			JETS.clear();
		}
		scan(level, player);
		boolean particles = mc.options.particles().get() != ParticleStatus.MINIMAL;
		RandomSource random = level.getRandom();
		for (Jet jet : JETS.values()) {
			switch (jet.kind) {
				case STEAM -> steam(level, jet, random, particles);
				case FIRE -> spout(level, jet, random, particles);
				case BUBBLES -> vent(level, jet, random, particles);
			}
		}
	}

	// ---- finding them ----

	private static void scan(ClientLevel level, LocalPlayer player) {
		slice = (slice + 1) % SLICES;
		BlockPos center = player.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int index = 0;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (index++ % SLICES != slice) {
					continue;
				}
				int x = center.getX() + dx, z = center.getZ() + dz;
				for (int y = center.getY() - HEIGHT; y <= center.getY() + HEIGHT; y++) {
					pos.set(x, y, z);
					long key = pos.asLong();
					JETS.remove(key);
					Kind kind = kindAt(level, pos);
					if (kind != null && JETS.size() < MAX) {
						JETS.put(key, new Jet(pos.immutable(), kind));
					}
				}
			}
		}
		double far = (RADIUS + 4.0) * (RADIUS + 4.0);
		JETS.values().removeIf(j -> j.pos.distToCenterSqr(player.position()) > far);
	}

	/** What jet, if any, starts at {@code pos}: the water block for steam, the open lava for a spout, the sea floor for bubbles. */
	static Kind kindAt(ClientLevel level, BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		if (fluid.is(FluidTags.WATER)) {
			// steam where water lies against lava (only the top water block of a column counts, so a wall of water steams once)
			if (!level.getFluidState(pos.above()).is(FluidTags.WATER)) {
				for (Direction d : Direction.values()) {
					if (d != Direction.UP && level.getFluidState(pos.relative(d)).is(FluidTags.LAVA)) {
						return Kind.STEAM;
					}
				}
			}
			return null;
		}
		if (fluid.isSourceOfType(Fluids.LAVA)) {
			return level.getBlockState(pos.above()).isAir() && spot(pos, SPOUT_RARITY) ? Kind.FIRE : null;
		}
		BlockState state = level.getBlockState(pos);
		if ((state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD)) && spot(pos, VENT_RARITY)) {
			for (int i = 1; i <= VENT_DEPTH; i++) {
				if (!level.getFluidState(pos.above(i)).isSourceOfType(Fluids.WATER)) {
					return null;
				}
			}
			return Kind.BUBBLES;
		}
		return null;
	}

	/** One spot in {@code rarity}, fixed by where it is. */
	private static boolean spot(BlockPos pos, int rarity) {
		return Math.floorMod(Mth.getSeed(pos.getX(), 7, pos.getZ()) >> 8, rarity) == 0;
	}

	// ---- what each does ----

	private static void steam(ClientLevel level, Jet jet, RandomSource random, boolean particles) {
		double x = jet.pos.getX() + 0.5, y = jet.pos.getY() + 1.0, z = jet.pos.getZ() + 0.5;
		if (particles && random.nextFloat() < 0.5F) {
			level.addParticle(ParticleTypes.WHITE_SMOKE, x + random.nextGaussian() * 0.3, y, z + random.nextGaussian() * 0.3,
				random.nextGaussian() * 0.01, 0.06 + random.nextDouble() * 0.05, random.nextGaussian() * 0.01);
			if (random.nextFloat() < 0.3F) {
				level.addParticle(ParticleTypes.CLOUD, x + random.nextGaussian() * 0.25, y + 0.1, z + random.nextGaussian() * 0.25,
					0.0, 0.05 + random.nextDouble() * 0.04, 0.0);
			}
		}
		if (random.nextFloat() < STEAM_SOUND) {
			ImmersiveSounds.play(level, x, y, z, "jet.steam", SoundSource.BLOCKS, STEAM_VOLUME, 0.9F + random.nextFloat() * 0.2F);
		}
	}

	private static void spout(ClientLevel level, Jet jet, RandomSource random, boolean particles) {
		double x = jet.pos.getX() + 0.5, y = jet.pos.getY() + 0.9, z = jet.pos.getZ() + 0.5;
		if (jet.burst <= 0) {
			if (random.nextInt(SPOUT_EVERY) == 0) {
				jet.burst = SPOUT_TICKS;
				ImmersiveSounds.play(level, x, y, z, "jet.fire", SoundSource.BLOCKS, FIRE_VOLUME, 0.85F + random.nextFloat() * 0.3F);
			}
			return;
		}
		jet.burst--;
		if (!particles) {
			return;
		}
		float strength = jet.burst / (float)SPOUT_TICKS;
		for (int i = 0; i < 3; i++) {
			level.addParticle(ParticleTypes.FLAME, x + random.nextGaussian() * 0.15, y, z + random.nextGaussian() * 0.15,
				random.nextGaussian() * 0.02, 0.15 + strength * 0.25 * random.nextDouble(), random.nextGaussian() * 0.02);
		}
		if (random.nextFloat() < 0.4F) {
			level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
		}
		if (random.nextFloat() < 0.3F) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, x + random.nextGaussian() * 0.2, y + 0.8 + strength, z + random.nextGaussian() * 0.2,
				0.0, 0.04, 0.0);
		}
	}

	private static void vent(ClientLevel level, Jet jet, RandomSource random, boolean particles) {
		double x = jet.pos.getX() + 0.5, y = jet.pos.getY() + 1.05, z = jet.pos.getZ() + 0.5;
		if (particles && random.nextFloat() < 0.7F) {
			level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + random.nextGaussian() * 0.15, y, z + random.nextGaussian() * 0.15,
				0.0, 0.1 + random.nextDouble() * 0.1, 0.0);
		}
		if (random.nextFloat() < VENT_SOUND) {
			ImmersiveSounds.play(level, x, y, z, "jet.bubbles", SoundSource.BLOCKS, VENT_VOLUME, 0.85F + random.nextFloat() * 0.3F);
		}
	}
}
