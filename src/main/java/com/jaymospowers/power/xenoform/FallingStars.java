package com.jaymospowers.power.xenoform;

import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.power.web.WebCraterFeature;
import com.jaymospowers.power.web.WebPower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Falling stars: now and then, on a clear night, a glowing rock comes down out of the sky near someone standing under
 * it, the way a Gravity meteor does, and blows a crater. Unlike a meteor it doesn't break up: a
 * {@link FallenStarBlock fallen star} is left in the crater's heart until someone mines it. Nothing to do with any
 * power until then; it's how the Xeno-Band gets into the world (and, more rarely, a symbiote).
 */
public final class FallingStars {
	// ---- tuning ----
	/** Each player under the open night sky is rolled this often (ticks), with this chance of a star. */
	public static final int CHECK_EVERY = 200;
	public static final float STAR_CHANCE = 0.002F;
	/** No two stars in one world closer in time than this (ticks: a day). */
	public static final int GAP_TICKS = 24000;
	/** It lands this far from the player (blocks), from this high, over this many ticks. */
	public static final double RANGE_MIN = 40.0, RANGE_MAX = 100.0;
	public static final double HEIGHT = 110.0;
	public static final int FALL_TICKS = 140;
	/** The blast on landing (hurts and flings; breaks nothing itself: the crater is carved). */
	public static final float BLAST = 2.0F;
	/** The landing shakes every screen this far off, this hard (degrees) close by. */
	public static final double LANDING_SHAKE_RANGE = 64.0;
	public static final float LANDING_SHAKE = 2.5F;
	/** Its trail is sent to everyone in the world this close (blocks), so a star is seen coming down from far off. */
	public static final double SEEN_FROM = 400.0;
	/** How big the rock is drawn while it falls (it glows through hills too). */
	public static final float ROCK_SCALE = 2.5F;
	/** After it lands, a tall smoke plume with sparks marks the crater for this long (ticks: 3 minutes). */
	public static final int PLUME_TICKS = 3600;

	/** What the star looks like coming down. */
	private static final List<BlockState> ROCK = List.of(Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
		Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());

	private record Star(ResourceKey<Level> dimension, int rock, Vec3 from, Vec3 to, long start) {
	}

	private record Plume(ResourceKey<Level> dimension, Vec3 at, long start) {
	}

	private static final List<Star> STARS = new ArrayList<>();
	private static final List<Plume> PLUMES = new ArrayList<>();
	private static final Map<ResourceKey<Level>, Long> LAST = new HashMap<>();

	private FallingStars() {
	}

	static void init() {
	}

	static void clear() {
		STARS.clear();
		PLUMES.clear();
		LAST.clear();
	}

	static void tick(MinecraftServer server) {
		fly(server);
		smoke(server);
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || level.isRaining()) {
				continue;
			}
			long now = level.getGameTime();
			Long last = LAST.get(level.dimension());
			if (last != null && now - last < GAP_TICKS) {
				continue;
			}
			RandomSource random = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || !level.canSeeSky(player.blockPosition()) || random.nextFloat() >= STAR_CHANCE) {
					continue;
				}
				double a = random.nextDouble() * Math.PI * 2.0;
				double r = Mth.lerp(random.nextDouble(), RANGE_MIN, RANGE_MAX);
				Vec3 near = player.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
				if (drop(level, near)) {
					break;
				}
			}
		}
	}

	/**
	 * Starts a star falling onto the ground at {@code near}'s column (a roll, or the debug command). False if there's no
	 * good ground there (water, the void).
	 */
	public static boolean drop(ServerLevel level, Vec3 near) {
		int x = Mth.floor(near.x);
		int z = Mth.floor(near.z);
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos ground = new BlockPos(x, groundY, z);
		if (groundY <= level.getMinY() + 5 || !level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) {
			return false;
		}
		Vec3 to = Vec3.atBottomCenterOf(ground.above());
		RandomSource random = level.getRandom();
		double slant = random.nextDouble() * Math.PI * 2.0;
		Vec3 from = to.add(Math.cos(slant) * HEIGHT * 0.5, HEIGHT, Math.sin(slant) * HEIGHT * 0.5);
		from = new Vec3(from.x, Math.min(from.y, level.getMaxY() - 2), from.z);
		BlockCluster rock = BlockCluster.made(level, ROCK, from);
		rock.script(true, false);
		com.jaymospowers.core.Oddities.mark(rock, com.jaymospowers.core.Oddities.STAR);
		rock.setAttached(BlockCluster.SCALE, ROCK_SCALE);
		rock.setGlowingTag(true);
		STARS.add(new Star(level.dimension(), rock.getId(), from, to, level.getGameTime()));
		LAST.put(level.dimension(), level.getGameTime());
		// a long whistle, and no word: whoever looks up has to spot it and find where it lands
		level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 12.0F, 0.5F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 12.0F, 0.6F);
		return true;
	}

	private static void fly(MinecraftServer server) {
		for (Iterator<Star> it = STARS.iterator(); it.hasNext(); ) {
			Star star = it.next();
			ServerLevel level = server.getLevel(star.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			Entity rock = level.getEntity(star.rock());
			long t = level.getGameTime() - star.start();
			float p = Mth.clamp(t / (float)FALL_TICKS, 0.0F, 1.0F);
			Vec3 at = star.from().lerp(star.to(), p * p);
			if (rock != null) {
				Vec3 was = rock.position();
				rock.setPos(at);
				rock.setDeltaMovement(at.subtract(was));
				rock.syncVelocity = true;
			}
			// a bright tail, and smoke, seen from far off (forced past the usual 32-block particle range)
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(at) > SEEN_FROM * SEEN_FROM) {
					continue;
				}
				level.sendParticles(player, ParticleTypes.END_ROD, true, true, at.x, at.y + 0.8, at.z, 14, 1.0, 1.0, 1.0, 0.03);
				level.sendParticles(player, ParticleTypes.FLAME, true, true, at.x, at.y + 0.8, at.z, 8, 1.0, 1.0, 1.0, 0.05);
				level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y + 0.8, at.z, 6, 0.8, 0.8, 0.8, 0.02);
				level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, at.x, at.y + 0.8, at.z, 3, 0.6, 0.6, 0.6, 0.01);
			}
			if (t % 10 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 4.0F, 0.5F + p * 0.4F);
			}
			if (t < FALL_TICKS) {
				continue;
			}
			it.remove();
			if (rock != null) {
				rock.discard();
			}
			land(level, star.to());
			PLUMES.add(new Plume(level.dimension(), star.to(), level.getGameTime()));
		}
	}

	/** The plume over a fresh crater: signal smoke climbing high, and sparks, so whoever saw it fall can find it. */
	private static void smoke(MinecraftServer server) {
		for (Iterator<Plume> it = PLUMES.iterator(); it.hasNext(); ) {
			Plume plume = it.next();
			ServerLevel level = server.getLevel(plume.dimension());
			long t = level == null ? PLUME_TICKS : level.getGameTime() - plume.start();
			if (t >= PLUME_TICKS) {
				it.remove();
				continue;
			}
			if (t % 4 != 0) {
				continue;
			}
			Vec3 at = plume.at();
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(at) > SEEN_FROM * SEEN_FROM) {
					continue;
				}
				level.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, at.x, at.y + 0.5, at.z, 2, 0.6, 0.3, 0.6, 0.01);
				level.sendParticles(player, ParticleTypes.END_ROD, true, true, at.x, at.y + 1.0, at.z, 2, 0.5, 1.5, 0.5, 0.06);
			}
		}
	}

	/** The crater, and the star in its heart. */
	private static void land(ServerLevel level, Vec3 to) {
		level.explode(null, to.x, to.y, to.z, BLAST, Level.ExplosionInteraction.NONE);
		com.jaymospowers.core.Shakes.around(level, to, LANDING_SHAKE_RANGE, LANDING_SHAKE);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 8.0F, 0.6F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 10.0F, 0.9F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y + 1.0, to.z, 2, 1.0, 0.5, 1.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 1.0, to.z, 60, 1.5, 1.0, 1.5, 0.25);
		level.sendParticles(ParticleTypes.LAVA, to.x, to.y + 1.0, to.z, 20, 1.0, 0.5, 1.0, 0.0);
		BlockPos ground = BlockPos.containing(to).below();
		BlockPos seat = ground.below(WebCraterFeature.DEPTH - 1);
		boolean carved = WebCraterFeature.carve(level, level.getRandom(), ground, false);
		if (carved && WebPower.GOO != null && level.getBlockState(seat).is(WebPower.GOO)) {
			// the crater's heart is where the goo would sit: the star instead
			starAt(level, seat);
			return;
		}
		// odd ground: just set it down on top
		BlockPos.MutableBlockPos pos = ground.above().mutable();
		for (int i = 0; i < 8 && pos.getY() > level.getMinY(); i++, pos.move(0, -1, 0)) {
			if (level.getBlockState(pos.below()).isSolid() && (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())) {
				starAt(level, pos);
				return;
			}
		}
		starAt(level, ground);
	}

	/** (0.18.120) The star set in its crater; one in {@link XenoFinds#WRECK_ONE_IN} came down a wreck. */
	private static void starAt(ServerLevel level, BlockPos pos) {
		BlockPos at = pos.immutable();
		level.setBlock(at, FallenStarBlock.FALLEN_STAR.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getRandom().nextInt(XenoFinds.WRECK_ONE_IN) == 0) {
			StarWreckBlock.wreck(level, at);
		}
	}
}
