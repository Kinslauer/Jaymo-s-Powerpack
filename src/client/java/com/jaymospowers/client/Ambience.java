package com.jaymospowers.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small touches around the world, all on this side and all from vanilla particles and sounds (our own take on the
 * ideas in Particular and Dynamic Surroundings, which have no 26.3 builds; nothing of theirs is copied):
 * <ul>
 * <li>waterfalls throw up spray and mist where falling water lands in water, more for a taller fall, and rumble;</li>
 * <li>fireflies drift over flowers and grass in warm biomes from dusk until dawn, more on some nights than others;</li>
 * <li>dust hangs in the air deep in dark caves, thicker the deeper you go;</li>
 * <li>anything that drops into water throws up a splash as wide as it is and as tall as its fall was fast;</li>
 * <li>anyone walking in armour clinks with each stride (its own equip sound, quietly).</li>
 * </ul>
 * Off when Particles is set to Minimal (the armour clink stays).
 */
public final class Ambience {
	/** Waterfalls: looked for this far around you (blocks out, blocks up and down), one slice of columns a tick. */
	static final int FALLS_RADIUS = 24, FALLS_HEIGHT = 16, FALLS_SLICES = 20;
	/** A fall counts this many blocks of falling water at most; each block adds this much spray a tick. */
	static final int FALLS_MAX_HEIGHT = 12;
	static final float FALLS_SPRAY_PER_BLOCK = 0.35F;
	/** Mist (cloud) a tick per block of fall, and the chance a tick that a fall rumbles (at this volume per block). */
	static final float FALLS_MIST_PER_BLOCK = 0.06F, FALLS_RUMBLE_CHANCE = 0.02F, FALLS_RUMBLE_VOLUME = 0.08F;
	/** Fireflies: tries a tick within this many blocks, the biome warmth they need, and the clock span they fly in. */
	static final int FIREFLY_RADIUS = 14, FIREFLY_TRIES = 6;
	static final float FIREFLY_WARMTH = 0.7F;
	static final long FIREFLY_FROM = 12500, FIREFLY_UNTIL = 23200;
	/** Each night's firefly count is this share of the tries, between these two (picked by the night). */
	static final float FIREFLY_NIGHT_MIN = 0.15F, FIREFLY_NIGHT_MAX = 1.0F;
	/** Cave dust: starts below this height with no sky above, up to this many motes a tick at the deepest. */
	static final int DUST_BELOW_Y = 40, DUST_FULL_DEPTH = 80;
	static final float DUST_MAX_PER_TICK = 1.6F;
	static final double DUST_RADIUS = 9.0;
	/** Splashes: things this fast downward (blocks a tick) splash; this many drops per block of width, and lift. */
	static final double SPLASH_MIN_SPEED = 0.25, SPLASH_DROPS_PER_WIDTH = 14.0, SPLASH_LIFT = 0.55;
	static final int SPLASH_MAX_DROPS = 60;
	static final double SPLASH_RADIUS = 32.0;
	/** Armour: a clink every this many blocks walked, heard this far, at this volume and pitch. */
	static final double CLINK_STRIDE = 1.7, CLINK_RADIUS = 16.0;
	static final float CLINK_VOLUME = 0.12F, CLINK_PITCH = 1.45F;

	/** The waterfalls found, by the column they land in: the spot spray comes from, and the fall's height. */
	private static final Map<Long, Fall> FALLS = new HashMap<>();
	private static final Map<Entity, Track> TRACKS = new WeakHashMap<>();
	private static int slice;
	private static ClientLevel lastLevel;

	private record Fall(BlockPos base, int height) {
	}

	private static final class Track {
		Vec3 last;
		boolean wet;
		double walked;

		Track(Vec3 at, boolean wet) {
			this.last = at;
			this.wet = wet;
		}
	}

	private Ambience() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Ambience::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null || mc.isPaused()) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			FALLS.clear();
			TRACKS.clear();
		}
		RandomSource random = level.getRandom();
		boolean particles = mc.options.particles().get() != ParticleStatus.MINIMAL;
		if (particles) {
			scanFalls(level, player);
			falls(level, player, random);
			fireflies(level, player, random);
			caveDust(level, player, random);
		}
		entities(level, player, random, particles);
	}

	// ---- waterfalls ----

	/** One slice of the columns around you: where falling water lands on still water, note the fall (or forget it). */
	private static void scanFalls(ClientLevel level, LocalPlayer player) {
		slice = (slice + 1) % FALLS_SLICES;
		BlockPos center = player.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int index = 0;
		for (int dx = -FALLS_RADIUS; dx <= FALLS_RADIUS; dx++) {
			for (int dz = -FALLS_RADIUS; dz <= FALLS_RADIUS; dz++) {
				if (index++ % FALLS_SLICES != slice) {
					continue;
				}
				int x = center.getX() + dx, z = center.getZ() + dz;
				long key = BlockPos.asLong(x, 0, z);
				FALLS.remove(key);
				for (int y = center.getY() + FALLS_HEIGHT; y >= center.getY() - FALLS_HEIGHT; y--) {
					pos.set(x, y, z);
					if (!falling(level.getFluidState(pos))) {
						continue;
					}
					FluidState below = level.getFluidState(pos.below());
					if (!below.isSourceOfType(Fluids.WATER)) {
						continue;
					}
					BlockPos base = pos.immutable();
					int height = 1;
					while (height < FALLS_MAX_HEIGHT && falling(level.getFluidState(base.above(height)))) {
						height++;
					}
					if (height >= 2) {
						FALLS.put(key, new Fall(base, height));
					}
					break;
				}
			}
		}
		// falls left behind as you walk off
		double far = (FALLS_RADIUS + 4.0) * (FALLS_RADIUS + 4.0);
		FALLS.values().removeIf(f -> f.base.distToCenterSqr(player.position()) > far);
	}

	private static boolean falling(FluidState fluid) {
		return fluid.getType() == Fluids.FLOWING_WATER && fluid.getValue(FlowingFluid.FALLING);
	}

	private static void falls(ClientLevel level, LocalPlayer player, RandomSource random) {
		for (Fall fall : FALLS.values()) {
			double x = fall.base.getX() + 0.5, y = fall.base.getY() + 0.05, z = fall.base.getZ() + 0.5;
			float spray = fall.height * FALLS_SPRAY_PER_BLOCK;
			int drops = (int)spray + (random.nextFloat() < spray - (int)spray ? 1 : 0);
			double kick = 0.08 + fall.height * 0.025;
			for (int i = 0; i < drops; i++) {
				double a = random.nextDouble() * Math.PI * 2.0, r = 0.3 + random.nextDouble() * 0.5;
				level.addParticle(ParticleTypes.SPLASH, x + Math.cos(a) * r, y, z + Math.sin(a) * r,
					Math.cos(a) * kick, kick * (1.5 + random.nextDouble()), Math.sin(a) * kick);
			}
			if (random.nextFloat() < fall.height * FALLS_MIST_PER_BLOCK) {
				level.addParticle(ParticleTypes.CLOUD, x + random.nextGaussian() * 0.5, y + 0.2, z + random.nextGaussian() * 0.5,
					random.nextGaussian() * 0.02, 0.02 + random.nextDouble() * 0.03, random.nextGaussian() * 0.02);
			}
			if (random.nextFloat() < 0.4F) {
				level.addParticle(ParticleTypes.BUBBLE, x + random.nextGaussian() * 0.4, y - 0.6, z + random.nextGaussian() * 0.4, 0.0, 0.05, 0.0);
			}
			if (random.nextFloat() < FALLS_RUMBLE_CHANCE) {
				level.playLocalSound(x, y, z, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
					Math.min(1.0F, fall.height * FALLS_RUMBLE_VOLUME), 0.6F + random.nextFloat() * 0.3F, false);
			}
		}
	}

	// ---- fireflies ----

	private static void fireflies(ClientLevel level, LocalPlayer player, RandomSource random) {
		if (!level.dimensionType().hasSkyLight() || level.isRaining()) {
			return;
		}
		long clock = level.getOverworldClockTime();
		long time = Math.floorMod(clock, 24000L);
		if (time < FIREFLY_FROM || time > FIREFLY_UNTIL) {
			return;
		}
		// some nights swarm, some are sparse
		long night = Math.floorDiv(clock - FIREFLY_FROM, 24000L);
		float share = FIREFLY_NIGHT_MIN + (FIREFLY_NIGHT_MAX - FIREFLY_NIGHT_MIN) * RandomSource.create(night * 341873128712L).nextFloat();
		BlockPos center = player.blockPosition();
		for (int i = 0; i < FIREFLY_TRIES; i++) {
			if (random.nextFloat() > share) {
				continue;
			}
			BlockPos pos = center.offset(random.nextInt(FIREFLY_RADIUS * 2 + 1) - FIREFLY_RADIUS, random.nextInt(9) - 4,
				random.nextInt(FIREFLY_RADIUS * 2 + 1) - FIREFLY_RADIUS);
			BlockState state = level.getBlockState(pos);
			if (!(state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN))) {
				continue;
			}
			if (level.getBiome(pos).value().getBaseTemperature() < FIREFLY_WARMTH || !level.canSeeSky(pos.above())) {
				continue;
			}
			level.addParticle(ParticleTypes.FIREFLY, pos.getX() + random.nextDouble(), pos.getY() + 0.6 + random.nextDouble() * 1.4,
				pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
		}
	}

	// ---- cave dust ----

	private static void caveDust(ClientLevel level, LocalPlayer player, RandomSource random) {
		BlockPos at = player.blockPosition();
		if (at.getY() >= DUST_BELOW_Y || level.canSeeSky(at) || player.isUnderWater()) {
			return;
		}
		float depth = Mth.clamp((DUST_BELOW_Y - at.getY()) / (float)DUST_FULL_DEPTH, 0.1F, 1.0F);
		float motes = depth * DUST_MAX_PER_TICK;
		int count = (int)motes + (random.nextFloat() < motes - (int)motes ? 1 : 0);
		for (int i = 0; i < count; i++) {
			double x = player.getX() + random.nextGaussian() * DUST_RADIUS * 0.5;
			double y = player.getEyeY() + random.nextGaussian() * 2.5;
			double z = player.getZ() + random.nextGaussian() * DUST_RADIUS * 0.5;
			if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
				continue;
			}
			level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, random.nextGaussian() * 0.004, -0.002, random.nextGaussian() * 0.004);
		}
	}

	// ---- splashes and armour ----

	private static void entities(ClientLevel level, LocalPlayer player, RandomSource random, boolean particles) {
		AABB around = player.getBoundingBox().inflate(Math.max(SPLASH_RADIUS, CLINK_RADIUS));
		for (Entity entity : level.getEntities((Entity)null, around, e -> e instanceof LivingEntity && !e.isSpectator())) {
			Vec3 now = entity.position();
			boolean wet = entity.isInWater();
			Track track = TRACKS.get(entity);
			if (track == null) {
				TRACKS.put(entity, new Track(now, wet));
				continue;
			}
			Vec3 moved = now.subtract(track.last);
			if (particles && wet && !track.wet && -moved.y >= SPLASH_MIN_SPEED) {
				splash(level, entity, -moved.y, random);
			}
			if (entity.onGround() && !entity.isShiftKeyDown() && entity.distanceToSqr(player) < CLINK_RADIUS * CLINK_RADIUS) {
				track.walked += Math.sqrt(moved.x * moved.x + moved.z * moved.z);
				if (track.walked >= CLINK_STRIDE) {
					track.walked = 0.0;
					clink(level, (LivingEntity)entity, random);
				}
			}
			track.last = now;
			track.wet = wet;
		}
		Iterator<Entity> it = TRACKS.keySet().iterator();
		while (it.hasNext()) {
			if (it.next().isRemoved()) {
				it.remove();
			}
		}
	}

	/** A ring of drops as wide as the thing that fell in, flung as high as it hit fast, and bubbles under it. */
	private static void splash(ClientLevel level, Entity entity, double speed, RandomSource random) {
		float width = entity.getBbWidth();
		double surface = entity.getY();
		BlockPos.MutableBlockPos pos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()).mutable();
		for (int i = 0; i < 3 && level.getFluidState(pos.above()).isSourceOfType(Fluids.WATER); i++) {
			pos.move(0, 1, 0);
		}
		if (level.getFluidState(pos).getType() != Fluids.EMPTY) {
			surface = pos.getY() + level.getFluidState(pos).getHeight(level, pos);
		}
		int drops = Math.min(SPLASH_MAX_DROPS, (int)(SPLASH_DROPS_PER_WIDTH * width * (0.6 + speed)));
		double lift = Math.min(1.2, speed * SPLASH_LIFT);
		for (int i = 0; i < drops; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, r = width * (0.5 + random.nextDouble() * 0.6);
			double out = 0.05 + random.nextDouble() * 0.12 * width;
			level.addParticle(ParticleTypes.SPLASH, entity.getX() + Math.cos(a) * r, surface + 0.05, entity.getZ() + Math.sin(a) * r,
				Math.cos(a) * out, lift * (0.4 + random.nextDouble()), Math.sin(a) * out);
		}
		for (int i = 0; i < drops / 3; i++) {
			level.addParticle(ParticleTypes.BUBBLE, entity.getX() + random.nextGaussian() * width * 0.4, surface - 0.3 - random.nextDouble(),
				entity.getZ() + random.nextGaussian() * width * 0.4, 0.0, 0.04, 0.0);
		}
	}

	/** The chestpiece's own equip sound, soft and quick, as if the plates knock together. */
	private static void clink(ClientLevel level, LivingEntity entity, RandomSource random) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		Equippable equippable = chest.isEmpty() ? null : chest.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != EquipmentSlot.CHEST) {
			return;
		}
		SoundEvent sound = equippable.equipSound().value();
		level.playLocalSound(entity.getX(), entity.getY() + 1.0, entity.getZ(), sound, entity.getSoundSource(),
			CLINK_VOLUME, CLINK_PITCH + (random.nextFloat() - 0.5F) * 0.2F, false);
	}
}
