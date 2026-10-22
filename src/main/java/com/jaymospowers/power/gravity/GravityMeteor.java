package com.jaymospowers.power.gravity;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Soundscape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Event Horizon's C: the Meteor. Held, it gathers ({@code FULL_CHARGE} to its fullest): let go and, where you aim
 * ({@code RANGE_MIN} out to {@code RANGE_MAX} with the charge, dropped to the first solid ground under it), a rock of
 * the Aether's magnetic region (ferrosite, ichorite, ferrosite sand, arkenium, corrobonite, gravitite) is torn out of the sky
 * at build height and pulled down onto it, {@code FALL_SPEED} blocks a tick on average ({@code FALL_TICKS_MIN} to
 * {@code FALL_TICKS_MAX} ticks), so everyone can watch it come: burning and trailing smoke, its shadow spreading on the
 * ground below, smashing through every block it meets on the way (roofs, trees, overhangs) and anyone in its path
 * ({@code HIT_DAMAGE_MIN}..{@code HIT_DAMAGE_MAX}). A tap brings the small, narrow one; a full charge one {@code SIZE_MAX} times the size (over 30 blocks
 * across). Like the Spirit Bomb it doesn't go off on touching: it slams in and grinds into the ground
 * ({@code EMBED_MIN}..{@code EMBED_MAX} ticks, sinking {@code EMBED_SHARE} of its width), white-hot and shaking the
 * earth harder and harder, the ground spraying up round it, and then it bursts. It goes off like the end of the world: a {@code POWER} blast, up to {@code DAMAGE_MIN}..{@code DAMAGE_MAX} at
 * the centre falling off to nothing at {@code RADIUS_MIN}..{@code RADIUS_MAX}, then a shockwave racing out along the
 * ground ({@code WAVE_SPEED}) that throws everything it passes. A charged one tears a wide, shallow crater: rings of
 * lighter blasts ({@code CRATER_RING_POWER}) go off as the shockwave reaches them, up to {@code CRATER_RINGS} rings
 * {@code CRATER_RING_STEP} apart. {@code COOLDOWN_MIN} (a tap) to {@code COOLDOWN} (a full charge) before another.
 * The rock doesn't vanish when it hits: it bursts apart into {@code WRECK_MIN} to {@code WRECK_MAX} lumps of debris
 * (the rock's own Aether blocks; more the bigger it was) thrown out over the crater, where they
 * lie for {@code WRECK_LINGER_MIN} to {@code WRECK_LINGER_MAX} ticks (Lift can take them) and then crumble away. Once in a long while one of them
 * carries a symbiote, which crawls out (or is left as goo) when it breaks up. The burst throws a storm of the rock's blocks (GravityMeteorFx, on every client within {@code BURST_SEEN}), and dust and smoke hang over the crater a long
 * while before they clear. When the shockwave has run out, whatever the strike cut loose and left hanging in the air over
 * the crater (a tree's crown, a lip of an overhang) comes down too (see {@link GravityMeteorSweep}).
 */
final class GravityMeteor {
	/** A charging meteor: a deep hum, rising, with stone grinding under it at uneven beats. */
	static final Soundscape.Layer CHARGE_HUM = Soundscape.Layer.of(0.9F, 1.1F, 0.96F, 1.04F, SoundEvents.BEACON_AMBIENT);
	static final Soundscape.Layer CHARGE_GRIND = Soundscape.Layer.of(0.3F, 0.5F, 0.4F, 0.6F, SoundEvents.GRAVEL_BREAK, SoundEvents.STONE_BREAK)
		.chance(0.4F);

	static final double RANGE_MIN = 48.0, RANGE_MAX = 128.0;
	static final int COOLDOWN_MIN = 900, COOLDOWN = 3600;
	/**
	 * The fall from build height: its average speed (blocks a tick, faster and faster down the line), never shorter or
	 * longer than these (ticks), and how far it slants in from behind you (blocks out per block down).
	 */
	static final double FALL_SPEED = 2.6, FALL_SLANT = 0.35;
	static final int FALL_TICKS_MIN = 50, FALL_TICKS_MAX = 120;
	/** Anyone it hits on the way down (a tap's, a full charge's); they're hit once. */
	static final float HIT_DAMAGE_MIN = 24.0F, HIT_DAMAGE_MAX = 50.0F;
	/** C held this long for the biggest, furthest meteor; a tap brings the small one at {@code RANGE_MIN}. */
	static final int FULL_CHARGE = 60;
	/** A full charge: the rock drawn this many times bigger (the small one is about 2.4 blocks across). */
	static final float SIZE_MAX = 14.0F;
	/** How far the impact hurts (blocks), and how much at the centre, from a tap to a full charge. */
	static final double RADIUS_MIN = 25.0, RADIUS_MAX = 60.0;
	static final float DAMAGE_MIN = 40.0F, DAMAGE_MAX = 60.0F;
	/** The blast at the centre (kept the same at every size, so the crater widens instead of deepening). */
	static final float POWER = 7.0F;
	/** The shockwave: how fast it runs out (blocks/tick), and how hard it throws (blocks/tick) a tap's and a full charge's. */
	static final double WAVE_SPEED = 1.5, THROW_MIN = 2.2, THROW_MAX = 3.5;
	/** The crater's rings: at most this many, this far apart (blocks), a blast every this many blocks around each. */
	static final int CRATER_RINGS = 3;
	static final double CRATER_RING_STEP = 8.0, CRATER_RING_SPACING = 7.0;
	static final float CRATER_RING_POWER = 5.0F;
	/** Its burning trail sets alight whatever it passes within this reach (blocks) of its surface, for this long (seconds). */
	static final double TRAIL_FIRE_RADIUS = 2.5;
	static final int TRAIL_FIRE_SECONDS = 5;
	/**
	 * On the way down it tears through what it passes: this share of its width (blocks from its middle), at most
	 * {@code CARVE_RADIUS_MAX}, no more than {@code CARVE_MAX_PER_TICK} blocks a tick; this share drop as items.
	 */
	static final double CARVE_SHARE = 0.8, CARVE_RADIUS_MAX = 7.0;
	static final int CARVE_MAX_PER_TICK = 6000;
	/** One broken block in this many shows its break (dust and sound); the rest just go (thousands a tick would flood clients). */
	static final int CARVE_SHOW_ONE_IN = 6;
	static final float CARVE_DROP_CHANCE = 0.1F;
	/** The impact sets alight everything within this reach (blocks) of where it lands (a tap's), for this long (seconds). */
	static final double IMPACT_FIRE_RADIUS = 4.0;
	/** The wreck: how many lumps the rock bursts into (a tap's, a full charge's), how long they lie (ticks), how hard they fly out. */
	static final int WRECK_MIN = 4, WRECK_MAX = 30, WRECK_LINGER_MIN = 100, WRECK_LINGER_MAX = 220;
	static final double WRECK_OUT_MIN = 0.35, WRECK_OUT_MAX = 0.9, WRECK_UP_MIN = 0.45, WRECK_UP_MAX = 1.05;
	/** What the lumps are made of: the rock's own blocks (see {@link #rock}). */
	static final int IMPACT_FIRE_SECONDS = 8;
	/** Embedding: how long it grinds in before it bursts (a tap's, a full charge's), and how much of its width sinks in. */
	static final int EMBED_MIN = 30, EMBED_MAX = 50;
	static final double EMBED_SHARE = 0.6;
	/** Players this near see the burst's debris and its dust cloud. */
	static final double BURST_SEEN = 256.0;
	private static final DustParticleOptions WHITE_HOT = new DustParticleOptions(0xFFE6A8, 4.0F);
	/** Half the small rock's width (blocks), as drawn. */
	private static final double ROCK_HALF = 1.2;
	private static final DustParticleOptions SHADOW = new DustParticleOptions(0x120A1A, 2.0F);

	/**
	 * What the rock is made of, straight from the Aether's magnetic region: its nine blocks, its wreck's lumps and the
	 * burst's debris are all of these (repeats are more common).
	 */
	private static List<BlockState> rock() {
		BlockState ferrosite = AetherBlocks.FERROSITE.defaultBlockState(), ichorite = AetherBlocks.ICHORITE.defaultBlockState();
		return List.of(ferrosite, ichorite, ferrosite, AetherBlocks.ARKENIUM_ORE.defaultBlockState(), ferrosite, ichorite,
			AetherBlocks.FERROSITE_SAND.defaultBlockState(), AetherBlocks.CORROBONITE_ORE.defaultBlockState(), AetherBlocks.GRAVITITE_ORE.defaultBlockState());
	}

	private static final class Meteor {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final int rock;
		final Vec3 from, to;
		final long start;
		final float charge;
		final int fall;
		/** Who it has already hit on the way down. */
		final Set<Integer> hit = new HashSet<>();
		/** Where it was last tick, while it grinds in. */
		Vec3 sunkTo;

		Meteor(ResourceKey<Level> dimension, UUID owner, int rock, Vec3 from, Vec3 to, long start, float charge, int fall) {
			this.dimension = dimension;
			this.owner = owner;
			this.rock = rock;
			this.from = from;
			this.to = to;
			this.start = start;
			this.charge = charge;
			this.fall = fall;
		}

		float size() {
			return 1.0F + (SIZE_MAX - 1.0F) * this.charge;
		}
	}

	/** The shockwave running out from an impact, and the crater rings it sets off. */
	private static final class Wave {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final Vec3 at;
		final long start;
		final float charge;
		final double radius;
		final Set<Integer> hit = new HashSet<>();
		/** What already hung in the air round the strike before it (left be when the wave is done, see GravityMeteorSweep). */
		final Set<Long> floatingBefore;
		int rings;

		Wave(ResourceKey<Level> dimension, UUID owner, Vec3 at, long start, float charge) {
			this.dimension = dimension;
			this.owner = owner;
			this.at = at;
			this.start = start;
			this.charge = charge;
			this.radius = GravityMeteor.radius(charge);
			this.floatingBefore = new HashSet<>();
		}
	}

	private static final List<Meteor> METEORS = new ArrayList<>();
	private static final List<Wave> WAVES = new ArrayList<>();

	private GravityMeteor() {
	}

	/** How long before another, after one called with this much of a charge (0 to 1). */
	static int cooldown(float charge) {
		return Math.round(Mth.lerp(charge, COOLDOWN_MIN, COOLDOWN));
	}

	/** How far it can be called with this much of a charge (0 to 1). */
	static double range(float charge) {
		return Mth.lerp(charge, RANGE_MIN, RANGE_MAX);
	}

	/** How far its impact reaches with this much of a charge. */
	static double radius(float charge) {
		return Mth.lerp(charge, RADIUS_MIN, RADIUS_MAX);
	}

	/** How many crater rings a charge this big tears (a tap: none, just the blast in the middle). */
	private static int craterRings(float charge) {
		return Math.round(charge * CRATER_RINGS);
	}

	/** C still held: where it would land shows (to you), and the hum rises. */
	static void charging(ServerPlayer player, float charge, long held) {
		ServerLevel level = player.level();
		Vec3 to = ground(level, GravityPower.aimPoint(player, range(charge)));
		double r = 1.0 + charge * (radius(1.0F) * 0.25);
		int points = 8 + Math.round(charge * 16);
		for (int i = 0; i < points; i++) {
			double a = i / (double)points * Math.PI * 2.0 + held * 0.15;
			level.sendParticles(player, SHADOW, true, false, to.x + Math.cos(a) * r, to.y + 0.2, to.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (charge < 1.0F && Soundscape.due(player, "gravity.meteor", 8, 13)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, CHARGE_HUM.pitched(0.5F + charge), CHARGE_GRIND);
		}
	}

	/** C let go: call it down where you aim, as big and as far as the charge (0 to 1). Returns false if nothing came. */
	static boolean call(ServerPlayer player, float charge) {
		ServerLevel level = player.level();
		float size = 1.0F + (SIZE_MAX - 1.0F) * charge;
		Vec3 to = ground(level, GravityPower.aimPoint(player, range(charge)));
		// down out of the sky from build height, at a slant from behind you, so it can be watched all the way down
		double height = Math.max(16.0, level.getMaxY() - to.y);
		Vec3 back = player.getLookAngle().horizontal();
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
		Vec3 from = to.add(back.scale(-height * FALL_SLANT)).add(0.0, height, 0.0);
		int fall = Mth.clamp((int)Math.round(from.distanceTo(to) / FALL_SPEED), FALL_TICKS_MIN, FALL_TICKS_MAX);
		BlockCluster rock = BlockCluster.made(level, rock(), from);
		rock.script(true, false);
		com.jaymospowers.core.Oddities.mark(rock, com.jaymospowers.core.Oddities.METEOR);
		// always scaled (even a tap's), so it's drawn from far off: it starts a long way up
		rock.setAttached(BlockCluster.SCALE, size);
		METEORS.add(new Meteor(level.dimension(), player.getUUID(), rock.getId(), from, to, level.getGameTime(), charge, fall));
		level.playSound(null, to.x, to.y, to.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0F + 4.0F * charge, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.4F);
		return true;
	}

	/**
	 * Where it really lands: the first solid ground at or under {@code aim} (aimed at the sky or past a cliff's edge, it
	 * would otherwise go off in the air and leave its wreck hanging there).
	 */
	private static Vec3 ground(ServerLevel level, Vec3 aim) {
		net.minecraft.core.BlockPos.MutableBlockPos pos = net.minecraft.core.BlockPos.containing(aim.x, aim.y - 0.01, aim.z).mutable();
		if (!level.isLoaded(pos)) {
			return aim;
		}
		for (int y = pos.getY(); y >= level.getMinY(); y--) {
			pos.setY(y);
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty()) {
				return new Vec3(aim.x, Math.min(aim.y, y + state.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y)), aim.z);
			}
		}
		return aim;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
			Meteor meteor = it.next();
			ServerLevel level = server.getLevel(meteor.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			Entity rock = level.getEntity(meteor.rock);
			ServerPlayer owner = server.getPlayerList().getPlayer(meteor.owner);
			long t = level.getGameTime() - meteor.start;
			double half = ROCK_HALF * meteor.size();
			Vec3 ground = meteor.to;
			Vec3 down = ground.subtract(meteor.from).normalize();
			// it lands when its leading edge meets the ground, not its middle
			Vec3 contact = ground.subtract(down.scale(half));
			int embed = Math.round(Mth.lerp(meteor.charge, EMBED_MIN, EMBED_MAX));
			int fall = meteor.fall;
			if (t > fall) {
				// grinding in, then the burst
				long sunk = t - fall;
				float g = Mth.clamp(sunk / (float)embed, 0.0F, 1.0F);
				double depth = 2.0 * half * EMBED_SHARE;
				// slammed in fast, then slower and slower as it grinds to a stop, smashing its way into the ground (the
				// burst tears the crater out round it)
				Vec3 at = contact.add(down.scale(depth * (1.0 - (1.0 - g) * (1.0 - g))));
				carve(level, meteor.sunkTo == null ? contact : meteor.sunkTo, at, half, owner);
				meteor.sunkTo = at;
				move(rock, at);
				if (sunk < embed) {
					embedding(level, meteor, owner, rock, at, ground, half, g, sunk);
					continue;
				}
				it.remove();
				List<Integer> debris = debrisStates();
				impact(level, owner, ground, meteor.charge);
				// after the blast, so the wreck isn't caught in it
				wreck(level, ground, meteor.charge);
				burst(level, ground, meteor.charge, debris);
				if (rock != null) {
					rock.discard();
				}
				continue;
			}
			float p = Mth.clamp(t / (float)fall, 0.0F, 1.0F);
			// accelerating down its line
			Vec3 at = meteor.from.lerp(contact, p * p);
			float pWas = Mth.clamp((t - 1) / (float)fall, 0.0F, 1.0F);
			Vec3 was = meteor.from.lerp(contact, pWas * pWas);
			// smashing through whatever's in its way (a roof, a tree, anyone under it) before it ever lands
			carve(level, was, at, half, owner);
			strike(level, meteor, owner, rock, was, at, half);
			move(rock, at);
			// burning and smoking all over its surface; the shadow spreads under it
			int more = 1 + Math.round(meteor.charge * 6.0F);
			double spread = half * 0.7;
			// (sent far, so it can be watched from the ground all the way down)
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, at.x, at.y + half * 0.5, at.z, 8 * more, spread, spread, spread, 0.02);
			level.sendParticles(ParticleTypes.FLAME, true, false, at.x, at.y + half * 0.5, at.z, 4 * more, spread, spread, spread, 0.05);
			level.sendParticles(ParticleTypes.LAVA, true, false, at.x, at.y + half * 0.5, at.z, more, spread, spread, spread, 0.0);
			level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, false, was.x, was.y + half * 0.5, was.z, 1 + more / 2, spread * 0.5, spread * 0.5,
				spread * 0.5, 0.01);
			Flames.area(level, at, TRAIL_FIRE_RADIUS + half, TRAIL_FIRE_SECONDS, owner, e -> e != rock);
			double shadow = 1.0 + p * Math.max(radius(meteor.charge) * 0.35, half);
			int points = 12 + Math.round(meteor.charge * 24);
			for (int i = 0; i < points; i++) {
				double a = i / (double)points * Math.PI * 2.0 + t * 0.1;
				level.sendParticles(SHADOW, ground.x + Math.cos(a) * shadow, ground.y + 0.1, ground.z + Math.sin(a) * shadow, 1, 0.1, 0.0, 0.1, 0.0);
			}
			if (t % 8 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 8.0F + 6.0F * meteor.charge, 0.4F + p * 0.3F);
			}
			if (t == 0 || t == fall / 2) {
				// a rushing roar on the ground as it comes (a long sound, so only twice)
				level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 3.0F + 3.0F * meteor.charge,
					0.5F + p * 0.3F);
			}
			if (t == fall) {
				// it strikes: a thud you feel, and the ground thrown up round it
				float loud = 1.0F + meteor.charge * 1.5F;
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 5.0F * loud, 0.3F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0F * loud, 0.3F);
				GravityPower.shake(level, ground, radius(meteor.charge) * 2.0, 1.2F + meteor.charge);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, ground.x, ground.y + 0.5, ground.z, 40 + Math.round(meteor.charge * 120),
					half, 0.4, half, 0.08);
			}
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			ServerLevel level = server.getLevel(wave.dimension);
			if (level == null) {
				it.remove();
			} else if (wave(level, server.getPlayerList().getPlayer(wave.owner), wave)) {
				it.remove();
				// the crater's done: whatever it cut loose and left hanging over it comes down
				GravityMeteorSweep.sweep(level, wave.at, sweepRadius(wave.charge), wave.floatingBefore);
			}
		}
	}

	private static void move(Entity rock, Vec3 at) {
		if (rock != null) {
			Vec3 was = rock.position();
			rock.setPos(at);
			rock.setDeltaMovement(at.subtract(was));
			rock.syncVelocity = true;
		}
	}

	/**
	 * Grinding into the ground ({@code g}: 0 just struck, 1 about to burst): hotter and hotter, white-hot at the end,
	 * the earth spraying up round it and shaking harder, the rumble quickening.
	 */
	private static void embedding(ServerLevel level, Meteor meteor, ServerPlayer owner, Entity rock, Vec3 at, Vec3 ground, double half, float g, long sunk) {
		var random = level.getRandom();
		float charge = meteor.charge;
		int more = 1 + Math.round(charge * 6.0F);
		double spread = half * 0.7;
		level.sendParticles(ParticleTypes.FLAME, true, false, at.x, at.y + half * 0.5, at.z, Math.round((4 + g * g * 20) * more), spread, spread, spread, 0.08 + g * 0.2);
		level.sendParticles(ParticleTypes.LAVA, true, false, at.x, at.y + half * 0.5, at.z, Math.round((1 + g * 6) * more), spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, at.x, at.y + half, at.z, 4 * more, spread, spread * 0.5, spread, 0.04);
		if (g > 0.5F) {
			level.sendParticles(WHITE_HOT, true, false, at.x, at.y + half * 0.6, at.z, Math.round(g * g * 6 * more), spread, spread * 0.6, spread, 0.0);
		}
		// the ground round it heaves and sprays up
		int sprays = 8 + Math.round(charge * 28);
		for (int i = 0; i < sprays; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = half * (0.7 + random.nextDouble() * 0.6);
			net.minecraft.core.BlockPos spot = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				net.minecraft.core.BlockPos.containing(ground.x + Math.cos(a) * r, ground.y, ground.z + Math.sin(a) * r)).below();
			BlockState state = level.getBlockState(spot);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}
			var dust = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state);
			level.sendParticles(dust, true, false, spot.getX() + 0.5, spot.getY() + 1.2, spot.getZ() + 0.5, 6, 0.5, 0.6, 0.5, 0.4);
			if (random.nextInt(4) == 0) {
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.DUST_PILLAR, state), true, false,
					spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 4, 0.4, 0.2, 0.4, 0.0);
			}
		}
		Flames.area(level, at, TRAIL_FIRE_RADIUS + half, TRAIL_FIRE_SECONDS, owner, e -> e != rock);
		if (sunk % Math.max(2, 6 - (int)(g * 4)) == 1) {
			float loud = 1.0F + charge * 1.5F;
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.5F * loud, 0.35F + g * 0.2F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 3.0F * loud, 0.4F);
			GravityPower.shake(level, ground, radius(charge) * 2.0, 0.6F + 1.8F * g);
		}
		if (sunk == 1) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F + 3.0F * charge, 0.4F);
		}
	}

	/** What its debris is made of: the rock's own blocks, straight from the Aether (the ground's spray is the embedding's). */
	private static List<Integer> debrisStates() {
		List<Integer> states = new ArrayList<>();
		for (BlockState state : rock()) {
			int id = net.minecraft.world.level.block.Block.getId(state);
			if (!states.contains(id)) {
				states.add(id);
			}
		}
		return states;
	}

	/** The burst, for everyone near: a storm of the rock's Aether blocks, and the dust and smoke that hang there after. */
	private static void burst(ServerLevel level, Vec3 at, float charge, List<Integer> states) {
		GravityMeteorBurst burst = new GravityMeteorBurst(at.x, at.y, at.z, charge, (float)radius(charge), states);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) < BURST_SEEN * BURST_SEEN
				&& net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, GravityMeteorBurst.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, burst);
			}
		}
	}

	/**
	 * The falling rock tears through every block it passes ({@code CARVE_SHARE} of its width, at most
	 * {@code CARVE_RADIUS_MAX}), from {@code from} to {@code to}: roofs, trees, overhangs, anything that isn't
	 * unbreakable, nothing left hanging. A few drop as items; the rest go up in dust (only some show it, see
	 * {@code CARVE_SHOW_ONE_IN}). Unloaded ground is left alone.
	 */
	private static void carve(ServerLevel level, Vec3 from, Vec3 to, double half, ServerPlayer owner) {
		double r = Math.min(CARVE_RADIUS_MAX, Math.max(1.2, half * CARVE_SHARE));
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int)Math.ceil(length / Math.max(0.5, r * 0.5)));
		java.util.Set<net.minecraft.core.BlockPos> done = new HashSet<>();
		var random = level.getRandom();
		int broken = 0;
		for (int i = 1; i <= steps && broken < CARVE_MAX_PER_TICK; i++) {
			Vec3 c = from.lerp(to, i / (double)steps);
			if (c.y < level.getMinY() || c.y > level.getMaxY() || !level.isLoaded(net.minecraft.core.BlockPos.containing(c))) {
				continue;
			}
			int ri = Mth.ceil(r);
			net.minecraft.core.BlockPos center = net.minecraft.core.BlockPos.containing(c);
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(center.offset(-ri, -ri, -ri), center.offset(ri, ri, ri))) {
				if (broken >= CARVE_MAX_PER_TICK) {
					break;
				}
				if (pos.distToCenterSqr(c) > r * r || !done.add(pos.immutable())) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (state.isAir() || !state.getFluidState().isEmpty() && state.getFluidState().isSource()) {
					continue;
				}
				if (state.getDestroySpeed(level, pos) < 0.0F) {
					continue;
				}
				if (random.nextFloat() < CARVE_DROP_CHANCE || state.hasBlockEntity()) {
					level.destroyBlock(pos, true, owner);
				} else {
					if (random.nextInt(CARVE_SHOW_ONE_IN) == 0) {
						level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, net.minecraft.world.level.block.Block.getId(state));
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				}
				broken++;
			}
		}
		if (broken > 0) {
			level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5F, 0.7F + random.nextFloat() * 0.3F);
			level.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 2, r * 0.5, r * 0.5, r * 0.5, 0.0);
		}
	}

	/** Anyone in its way on the way down ({@code was} to {@code at}) is struck once: hurt hard and hurled down and out. */
	private static void strike(ServerLevel level, Meteor meteor, ServerPlayer owner, Entity rock, Vec3 was, Vec3 at, double half) {
		double reach = half + 0.8;
		float damage = Mth.lerp(meteor.charge, HIT_DAMAGE_MIN, HIT_DAMAGE_MAX);
		Vec3 down = at.subtract(was);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(was, at).inflate(reach),
			e -> e.isAlive() && !e.isSpectator() && e != owner && e != rock && !(e instanceof ArmorStand))) {
			Vec3 middle = living.getBoundingBox().getCenter();
			// how near its line it came
			double along = down.lengthSqr() < 1.0E-6 ? 0.0 : Mth.clamp(middle.subtract(was).dot(down) / down.lengthSqr(), 0.0, 1.0);
			Vec3 nearest = was.add(down.scale(along));
			if (middle.distanceTo(nearest) > reach + living.getBbWidth() * 0.5 || !meteor.hit.add(living.getId())) {
				continue;
			}
			DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
			living.setInvulnerableTime(0);
			if (owner != null) {
				GravityPower.touch(living, owner);
			}
			living.hurtServer(level, source, damage);
			Vec3 away = middle.subtract(nearest);
			Vec3 out = away.horizontal().lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.horizontal().normalize();
			Vec3 throwVec = out.scale(1.2).add(meteor.to.subtract(meteor.from).normalize().scale(0.8));
			living.setDeltaMovement(throwVec);
			living.syncVelocity = true;
			if (living instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(throwVec));
			}
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0F, 0.5F);
		}
	}

	/** How far round a strike the sweep looks for what it left hanging: the crater rings' reach and a little more. */
	private static double sweepRadius(float charge) {
		return GravityMeteorSweep.REACH_BEYOND + craterRings(charge) * CRATER_RING_STEP + POWER;
	}

	/** It lands: the blast and the hurt; the shockwave sets off from here (see {@link #wave}). */
	private static void impact(ServerLevel level, ServerPlayer owner, Vec3 at, float charge) {
		double radius = radius(charge);
		float damageMax = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX);
		// what already hangs in the air here (an Aether islet, a cloud) is left be by the sweep after
		Wave wave = new Wave(level.dimension(), owner == null ? new UUID(0L, 0L) : owner.getUUID(), at, level.getGameTime(), charge);
		wave.floatingBefore.addAll(GravityMeteorSweep.floating(level, at, sweepRadius(charge)));
		level.explode(owner, at.x, at.y, at.z, POWER, false, Level.ExplosionInteraction.TNT);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> e.isAlive() && !e.isSpectator() && e != owner && !(e instanceof ArmorStand))) {
			double d = living.getBoundingBox().getCenter().distanceTo(at);
			if (d > radius) {
				continue;
			}
			float share = (float)(1.0 - d / radius);
			DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
			living.setInvulnerableTime(0);
			if (owner != null) {
				GravityPower.touch(living, owner);
			}
			living.hurtServer(level, source, damageMax * share * share + 2.0F * share);
		}
		// everything flammable near the strike catches
		Flames.area(level, at, IMPACT_FIRE_RADIUS * radius / RADIUS_MIN, IMPACT_FIRE_SECONDS, owner, e -> true);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0, at.z, 3 + Math.round(charge * 12), 2.0 + charge * 8.0, 1.0 + charge * 3.0,
			2.0 + charge * 8.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1.0, at.z, 60 + Math.round(charge * 140), 4.0 + charge * 10.0, 2.0 + charge * 4.0,
			4.0 + charge * 10.0, 0.05);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 15 + Math.round(charge * 30), 3.0 + charge * 8.0, 1.0, 3.0 + charge * 8.0, 0.0);
		float loud = 1.0F + charge * 1.5F;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0F * loud, 0.4F - charge * 0.1F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F * loud, 0.5F - charge * 0.1F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0F * loud, 0.5F);
		GravityPower.shake(level, at, radius * 2.0, 1.5F + charge * 1.5F);
		WAVES.add(wave);
	}

	/**
	 * The rock bursts apart where it hit: lumps of its ore and stone thrown out over the crater, more the bigger it was,
	 * lying there a while before they crumble. One of them may carry a symbiote (see WebSymbioteBlob.METEOR_CHANCE).
	 */
	private static void wreck(ServerLevel level, Vec3 at, float charge) {
		var random = level.getRandom();
		int lumps = Math.round(Mth.lerp(charge, WRECK_MIN, WRECK_MAX));
		double size = 1.0 + charge * 2.0;
		BlockCluster carrier = null;
		for (int i = 0; i < lumps; i++) {
			int pieces = 1 + random.nextInt(charge > 0.5F ? 4 : 3);
			List<BlockState> states = new ArrayList<>();
			for (int k = 0; k < pieces; k++) {
				List<BlockState> from = rock();
				states.add(from.get(random.nextInt(from.size())));
			}
			double a = random.nextDouble() * Math.PI * 2.0;
			Vec3 from = at.add(Math.cos(a) * random.nextDouble() * size, 1.2 + random.nextDouble() * size, Math.sin(a) * random.nextDouble() * size);
			BlockCluster lump = BlockCluster.made(level, states, from);
			double out = Mth.lerp(random.nextDouble(), WRECK_OUT_MIN, WRECK_OUT_MAX) * (1.0 + charge);
			lump.setDeltaMovement(Math.cos(a) * out, Mth.lerp(random.nextDouble(), WRECK_UP_MIN, WRECK_UP_MAX), Math.sin(a) * out);
			lump.setAttached(BlockCluster.REST, new BlockCluster.Rest(random.nextFloat() * 360.0F, (random.nextFloat() - 0.5F) * 120.0F));
			if (charge > 0.3F && random.nextFloat() < charge * 0.5F) {
				lump.setAttached(BlockCluster.SCALE, 1.0F + random.nextFloat() * charge * 1.2F);
			}
			lump.linger(WRECK_LINGER_MIN + random.nextInt(WRECK_LINGER_MAX - WRECK_LINGER_MIN + 1), true);
			if (carrier == null || random.nextInt(i + 1) == 0) {
				carrier = lump;
			}
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, states.get(0)), from.x, from.y, from.z, 10,
				0.5, 0.5, 0.5, 0.3);
		}
		if (carrier != null) {
			// now and then something came down inside the rock: it crawls out when its lump breaks up
			BlockCluster from = carrier;
			carrier.onGone(() -> com.jaymospowers.power.web.WebSymbioteBlob.fromMeteor(level, from.position()));
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.PLAYERS, 3.0F, 0.5F);
	}

	/**
	 * The shockwave a tick further out: a ring of blasts and dust along the ground, throwing everything it passes outward
	 * and up, and setting off each crater ring as it reaches it. True once it has run out to the impact's reach.
	 */
	private static boolean wave(ServerLevel level, ServerPlayer owner, Wave wave) {
		long t = level.getGameTime() - wave.start + 1;
		double r = Math.min(wave.radius, t * WAVE_SPEED);
		double inner = Math.max(0.0, r - WAVE_SPEED * 1.5);
		Vec3 at = wave.at;
		double push = Mth.lerp(wave.charge, THROW_MIN, THROW_MAX);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 2.0, 8.0, r + 2.0),
			e -> e.isAlive() && !e.isSpectator() && e != owner && !(e instanceof ArmorStand))) {
			Vec3 away = living.position().subtract(at);
			double d = Math.sqrt(away.x * away.x + away.z * away.z);
			if (d < inner || d > r + 1.0 || !wave.hit.add(living.getId())) {
				continue;
			}
			float share = (float)(1.0 - 0.6 * d / wave.radius);
			Vec3 out = d < 0.3 ? Vec3.ZERO : new Vec3(away.x / d, 0.0, away.z / d);
			Vec3 throwVec = out.scale(push * share).add(0.0, 0.5 + 0.4 * share, 0.0);
			living.setDeltaMovement(throwVec);
			living.syncVelocity = true;
			if (living instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(throwVec));
			}
		}
		// the ring itself: blasts and thrown-up dust along the ground
		int points = Math.max(12, (int)(r * 1.6));
		int every = Math.max(1, points / 120);
		for (int i = 0; i < points; i += every) {
			double a = i / (double)points * Math.PI * 2.0;
			double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
			level.sendParticles(ParticleTypes.EXPLOSION, x, at.y + 0.4, z, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % (every * 3) == 0) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, at.y + 0.6, z, 1, 0.3, 0.3, 0.3, 0.02);
			}
		}
		if (t % 5 == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F + 4.0F * wave.charge, 0.5F + (float)(r / wave.radius) * 0.4F);
		}
		// the crater widens as it passes: each ring of lighter blasts goes off when the wave reaches it
		int rings = craterRings(wave.charge);
		while (wave.rings < rings && (wave.rings + 1) * CRATER_RING_STEP <= r) {
			wave.rings++;
			double ringR = wave.rings * CRATER_RING_STEP;
			int blasts = Math.max(4, (int)Math.round(ringR * Math.PI * 2.0 / CRATER_RING_SPACING));
			for (int i = 0; i < blasts; i++) {
				double a = (i + 0.5 * wave.rings) / blasts * Math.PI * 2.0;
				level.explode(owner, at.x + Math.cos(a) * ringR, at.y, at.z + Math.sin(a) * ringR, CRATER_RING_POWER, false, Level.ExplosionInteraction.TNT);
			}
		}
		return r >= wave.radius && wave.rings >= rings;
	}

	static void clear() {
		METEORS.clear();
		WAVES.clear();
	}
}
