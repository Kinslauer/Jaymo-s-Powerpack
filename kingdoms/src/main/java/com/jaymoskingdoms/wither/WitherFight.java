package com.jaymoskingdoms.wither;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Legendary;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Temperaments;
import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wither: Reincarnated's fight, written our own way (ideas only; nothing of that mod is copied). While a Wither stands
 * ({@link WitherNight}) it does not hunt at first. Its first act is to tear down what people built, houses first
 * ({@link #ravage}), if mobGriefing lets it; the folk run, the guards go for it, and the dead near it follow and guard it.
 * Then it hunts the nearest player, circling them rather than hovering over them, and faster and angrier in the last
 * third of its health, with four moves, each shown a moment before ({@link WitherMoves}): a beam that kills whoever it
 * sees (hide behind a block), a charge a raised shield stops (and stuns it), a rain of skulls on spots marked on the
 * ground, and blue skulls that a swing sends back ({@link WitherSkulls}).
 */
public final class WitherFight {
	// ---- the wrecking -------------------------------------------------------------------------------------------------
	/** How often it looks for something built (ticks), how many spots it tries each time, and how far from it (blocks). */
	static final int SCAN_EVERY = 40, SCAN_SAMPLES = 120;
	static final double SCAN_RADIUS = 40.0, FOLK_NEAR = 64.0, SITE_RADIUS = 14.0;
	/** How wide it circles a ruin (blocks), how high above it, and how fast (radians a tick). */
	static final double SITE_HOVER = 17.0, SITE_HEIGHT = 11.0, SITE_SPEED = 0.03;
	/** A skull at the ruin every this many ticks, until this many have gone or this long has passed (ticks). */
	static final int RAVAGE_EVERY = 16, RAVAGE_SKULLS = 18, RAVAGE_MAX = 2400;
	/** It turns on whoever has hurt it this much of its health since the wrecking began. */
	static final float RAVAGE_BREAK = 0.08F;

	// ---- the hunt ------------------------------------------------------------------------------------------------------
	/** How far it hunts (blocks); its circle round the quarry (blocks), height above them, and pace (radians a tick). */
	static final double HUNT_RANGE = 64.0, ORBIT_RADIUS = 14.0, ORBIT_HEIGHT = 7.0, ORBIT_SPEED = 0.05;
	/** In the last third of its health: the circle's radius, its pace, and the share it waits less between moves. */
	static final double ANGRY_RADIUS = 11.0, ANGRY_SPEED = 0.085;
	static final float ANGRY_WAIT = 0.55F;
	/** Its top speed round the circle (blocks a tick), and angry. */
	static final double TOP_SPEED = 0.8, ANGRY_TOP_SPEED = 1.2;
	/** Ticks between one move and the next, at least and at most. */
	static final int WAIT_MIN = 100, WAIT_MAX = 180;
	/** The move's range: it won't start one against someone further than this (blocks). */
	static final double MOVE_RANGE = 48.0;

	// ---- the rest ------------------------------------------------------------------------------------------------------
	/** How far the living run from it (blocks), and how far a guard goes for it. */
	static final double FLEE_RANGE = 40.0, GUARD_RANGE = 48.0, RUN = 18.0;
	/** The dead that rise to it: how far they hear it (blocks), how many at most, how close they keep, how far from it they fight. */
	static final double DEAD_RANGE = 40.0, KEEP_CLOSE = 7.0, LEASH = 24.0;
	static final int MAX_DEAD = 30;
	/** Looks about this often (ticks). */
	static final int AMBIENT_EVERY = 20;
	/** The Wither's violet; the cold light in its dead's eyes. */
	static final int VIOLET = 0x6A3FA0, EYES = 0x7AE6FF;
	/** On a risen dead (a plain scoreboard tag). */
	static final String GUARD_TAG = "jaymoskingdoms_wither_guard";

	enum Phase {
		RAVAGE, HUNT
	}

	/** One Wither's fight. */
	static final class Fight {
		Phase phase = Phase.RAVAGE;
		boolean ravageBegun;
		long ravageStart;
		float healthAtStart;
		int skulls;
		long nextScan;
		@Nullable BlockPos site;
		long nextSkull;
		boolean taken;
		double angle;
		int dir = 1;
		long stunUntil;
		final WitherMoves.Move move = new WitherMoves.Move();
	}

	private static final Map<WitherBoss, Fight> FIGHTS = new IdentityHashMap<>();

	private WitherFight() {
	}

	public static void register() {
		WitherNight.register();
		WitherSkulls.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof WitherBoss wither) {
				FIGHTS.put(wither, new Fight());
				// (never left stunned by a world that closed in the middle of it)
				if (wither.isNoAi()) {
					wither.setNoAi(false);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof WitherBoss wither) {
				release(wither, FIGHTS.remove(wither));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(WitherFight::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FIGHTS.forEach((wither, fight) -> release(wither, fight)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIGHTS.clear());
	}

	private static void tick(MinecraftServer server) {
		boolean lives = false;
		for (Map.Entry<WitherBoss, Fight> entry : new ArrayList<>(FIGHTS.entrySet())) {
			WitherBoss wither = entry.getKey();
			Fight fight = entry.getValue();
			if (wither.isRemoved() || !wither.isAlive() || !(wither.level() instanceof ServerLevel level)) {
				release(wither, fight);
				if (wither.isRemoved()) {
					FIGHTS.remove(wither);
				}
				continue;
			}
			lives = true;
			run(level, wither, fight);
		}
		WitherNight.update(server, lives);
	}

	private static void run(ServerLevel level, WitherBoss wither, Fight fight) {
		long now = level.getGameTime();
		if (wither.getInvulnerableTicks() > 0) {
			// (still being raised: the vanilla build-up goes on as it is)
			release(wither, fight);
			return;
		}
		if (now < fight.stunUntil) {
			WitherMoves.dazed(level, wither, now);
			return;
		}
		if (wither.isNoAi()) {
			// the stun is over
			wither.setNoAi(false);
			wither.setNoGravity(true);
			WitherMoves.wake(fight.move, now);
		}
		if (now % AMBIENT_EVERY == 0) {
			scatter(level, wither);
			rise(level, wither);
		}
		if (fight.phase == Phase.RAVAGE && !ravage(level, wither, fight, now)) {
			fight.phase = Phase.HUNT;
			WitherMoves.wake(fight.move, now);
		}
		if (fight.phase == Phase.HUNT) {
			hunt(level, wither, fight, now);
		}
	}

	// ---- wrecking what was built --------------------------------------------------------------------------------------

	/** Phase one: false once it's done (or can't), and the hunt begins. */
	private static boolean ravage(ServerLevel level, WitherBoss wither, Fight fight, long now) {
		if (!fight.ravageBegun) {
			fight.ravageBegun = true;
			fight.ravageStart = now;
			fight.healthAtStart = wither.getHealth();
			fight.nextScan = now;
		}
		// (mobGriefing off: nothing to wreck, so no wrecking)
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || now - fight.ravageStart > RAVAGE_MAX || fight.skulls >= RAVAGE_SKULLS
			|| fight.healthAtStart - wither.getHealth() > wither.getMaxHealth() * RAVAGE_BREAK) {
			return false;
		}
		if (now >= fight.nextScan && (fight.site == null || !built(level.getBlockState(fight.site)))) {
			fight.nextScan = now + SCAN_EVERY;
			fight.site = findSite(level, wither);
			if (fight.site == null) {
				return false;
			}
		}
		if (fight.site == null) {
			return true;
		}
		take(wither, fight);
		Vec3 centre = Vec3.atCenterOf(fight.site);
		fight.angle += SITE_SPEED * fight.dir;
		circle(wither, centre, SITE_HOVER, SITE_HEIGHT, fight.angle, TOP_SPEED * 0.7);
		if (now >= fight.nextSkull) {
			fight.nextSkull = now + RAVAGE_EVERY;
			BlockPos aim = builtNear(level, fight.site, SITE_RADIUS);
			if (aim == null) {
				fight.site = null;
				return true;
			}
			fight.skulls++;
			Vec3 from = head(wither);
			WitherSkulls.throwSkull(level, wither, from, Vec3.atCenterOf(aim), false, WitherSkulls.Kind.RAVAGE, Vec3.atCenterOf(aim));
			level.playSound(null, from.x, from.y, from.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0F, 0.8F);
		}
		return true;
	}

	/** A built thing's block: planks, bricks, doors, glass, walls, beds, wool and the like. */
	static boolean built(BlockState state) {
		return state.is(BlockTags.PLANKS) || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.DOORS) || state.is(BlockTags.STAIRS)
			|| state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES) || state.is(BlockTags.WOOL)
			|| state.is(BlockTags.BEDS) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.CONCRETE) || state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.LANTERNS) || state.is(Blocks.BRICKS) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.GLASS)
			|| state.is(Blocks.GLASS_PANE) || state.is(Blocks.BOOKSHELF);
	}

	/** The nearest built block within a sample of the land around the Wither and around the folk near it (a town). */
	private static @Nullable BlockPos findSite(ServerLevel level, WitherBoss wither) {
		var random = level.getRandom();
		List<Vec3> centres = new ArrayList<>();
		centres.add(wither.position());
		for (PathfinderMob folk : level.getEntitiesOfClass(PathfinderMob.class, wither.getBoundingBox().inflate(FOLK_NEAR),
			m -> m instanceof Villager || m instanceof Folk)) {
			if (centres.size() < 5 && random.nextInt(4) == 0) {
				centres.add(folk.position());
			}
		}
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < SCAN_SAMPLES; i++) {
			Vec3 c = centres.get(i % centres.size());
			int x = Mth.floor(c.x + (random.nextDouble() * 2.0 - 1.0) * SCAN_RADIUS * (i % centres.size() == 0 ? 1.0 : 0.5));
			int z = Mth.floor(c.z + (random.nextDouble() * 2.0 - 1.0) * SCAN_RADIUS * (i % centres.size() == 0 ? 1.0 : 0.5));
			if (!level.hasChunkAt(new BlockPos(x, 64, z))) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			for (int y = top; y > top - 6 && y > level.getMinY(); y--) {
				BlockPos pos = new BlockPos(x, y, z);
				if (built(level.getBlockState(pos))) {
					double d = pos.distSqr(wither.blockPosition());
					if (d < bestDistance) {
						best = pos;
						bestDistance = d;
					}
					break;
				}
			}
		}
		return best;
	}

	/** A built block within {@code radius} of {@code centre}, taken at random from a sample (null if there is none). */
	private static @Nullable BlockPos builtNear(ServerLevel level, BlockPos centre, double radius) {
		var random = level.getRandom();
		for (int i = 0; i < 40; i++) {
			BlockPos pos = centre.offset(Mth.floor((random.nextDouble() * 2.0 - 1.0) * radius), random.nextInt(9) - 3,
				Mth.floor((random.nextDouble() * 2.0 - 1.0) * radius));
			if (level.hasChunkAt(pos) && built(level.getBlockState(pos))) {
				return pos;
			}
		}
		return null;
	}

	// ---- hunting --------------------------------------------------------------------------------------------------------

	private static void hunt(ServerLevel level, WitherBoss wither, Fight fight, long now) {
		ServerPlayer quarry = nearest(level, wither, HUNT_RANGE);
		if (quarry == null) {
			// nobody to hunt: its own way, until someone comes
			release(wither, fight);
			return;
		}
		take(wither, fight);
		boolean angry = wither.getHealth() <= wither.getMaxHealth() / 3.0F;
		if (WitherMoves.busy(fight.move)) {
			WitherMoves.run(level, wither, fight, quarry, now, angry);
			return;
		}
		fight.angle += (angry ? ANGRY_SPEED : ORBIT_SPEED) * fight.dir;
		circle(wither, quarry.position(), angry ? ANGRY_RADIUS : ORBIT_RADIUS, ORBIT_HEIGHT, fight.angle, angry ? ANGRY_TOP_SPEED : TOP_SPEED);
		if (now >= fight.move.nextAt && wither.distanceTo(quarry) < MOVE_RANGE) {
			WitherMoves.start(level, wither, fight, quarry, now, angry);
		}
	}

	static @Nullable ServerPlayer nearest(ServerLevel level, WitherBoss wither, double range) {
		ServerPlayer best = null;
		double bestDistance = range * range;
		for (ServerPlayer player : level.players()) {
			double d = player.distanceToSqr(wither);
			if (d < bestDistance && player.isAlive() && !player.isSpectator()) {
				best = player;
				bestDistance = d;
			}
		}
		return best;
	}

	/** Steers {@code wither} toward the point on a circle round {@code centre}, at no more than {@code top} blocks a tick. */
	static void circle(WitherBoss wither, Vec3 centre, double radius, double height, double angle, double top) {
		Vec3 want = centre.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
		drive(wither, want.subtract(wither.position()).scale(0.18), top);
	}

	/** Sets its flight for the coming tick (vanilla halves the climb, which is made up for here). */
	static void drive(WitherBoss wither, Vec3 velocity, double top) {
		double length = velocity.length();
		Vec3 v = length > top ? velocity.scale(top / length) : velocity;
		wither.setDeltaMovement(v.x, v.y / 0.6, v.z);
		wither.setAlternativeTarget(0, 0);
	}

	/** It's ours to fly: its own goals don't steer it, aim its centre head or pick its quarry. */
	static void take(WitherBoss wither, Fight fight) {
		if (fight.taken) {
			return;
		}
		fight.taken = true;
		MobAccessor mob = (MobAccessor)wither;
		mob.jaymoskingdoms$goals().disableControlFlag(Goal.Flag.MOVE);
		mob.jaymoskingdoms$goals().disableControlFlag(Goal.Flag.LOOK);
		mob.jaymoskingdoms$targets().disableControlFlag(Goal.Flag.TARGET);
		wither.setTarget(null);
		wither.setNoGravity(true);
	}

	/** It's its own again. */
	static void release(WitherBoss wither, @Nullable Fight fight) {
		if (fight == null || !fight.taken && !wither.isNoAi()) {
			return;
		}
		fight.taken = false;
		if (wither.isNoAi()) {
			wither.setNoAi(false);
		}
		MobAccessor mob = (MobAccessor)wither;
		mob.jaymoskingdoms$goals().enableControlFlag(Goal.Flag.MOVE);
		mob.jaymoskingdoms$goals().enableControlFlag(Goal.Flag.LOOK);
		mob.jaymoskingdoms$targets().enableControlFlag(Goal.Flag.TARGET);
		wither.setNoGravity(false);
	}

	/** Where its middle head is. */
	static Vec3 head(WitherBoss wither) {
		return new Vec3(wither.getX(), wither.getY() + 3.0 * wither.getScale(), wither.getZ());
	}

	// ---- everything runs; the dead rise ----------------------------------------------------------------------------------

	/** The living run, the fighters go for it. */
	private static void scatter(ServerLevel level, WitherBoss wither) {
		for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, wither.getBoundingBox().inflate(FLEE_RANGE),
			m -> m.isAlive() && !(m instanceof Enemy) && !m.entityTags().contains(GUARD_TAG))) {
			if (Downed.isGuard(mob) || mob instanceof Folk folk && folk.combatant()) {
				// the guards answer it as they would a raid
				if (!Downed.isDowned(mob) && mob.distanceTo(wither) < GUARD_RANGE && mob.getTarget() != wither) {
					mob.setTarget(wither);
				}
			} else if (mob instanceof Villager villager) {
				// (the brave may stand)
				if (Temperaments.of(villager) != Temperaments.Temperament.BRAVE) {
					com.jaymoskingdoms.Frights.scare(level, villager, wither.position());
				}
			} else if (mob instanceof Animal || mob instanceof Folk) {
				Vec3 to = LandRandomPos.getPosAway(mob, (int)RUN, 7, wither.position());
				if (to != null) {
					mob.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
				}
			}
		}
	}

	/** The dead near it (never the barrow's) follow it and guard it, a cold light in their eyes. */
	private static void rise(ServerLevel level, WitherBoss wither) {
		List<Mob> dead = level.getEntitiesOfClass(Mob.class, wither.getBoundingBox().inflate(DEAD_RANGE), m -> risen(m));
		dead.sort((a, b) -> Double.compare(a.distanceToSqr(wither), b.distanceToSqr(wither)));
		ServerPlayer foe = nearest(level, wither, LEASH);
		for (int i = 0; i < dead.size() && i < MAX_DEAD; i++) {
			Mob mob = dead.get(i);
			mob.addTag(GUARD_TAG);
			double far = mob.distanceTo(wither);
			if (foe != null && mob.getTarget() == null) {
				mob.setTarget(foe);
			} else if (far > KEEP_CLOSE && (mob.getTarget() == null || mob.getTarget().distanceTo(wither) > LEASH)) {
				mob.setTarget(null);
				mob.getNavigation().moveTo(wither.getX(), wither.getY(), wither.getZ(), 1.15);
			}
			level.sendParticles(Fx.mote(EYES, 0.5F), mob.getX(), mob.getEyeY(), mob.getZ(), 2, 0.2, 0.05, 0.2, 0.0);
		}
	}

	/** The dead that answer it: zombies, skeletons and our own old dead, but never the barrow knights, who keep their own oath. */
	static boolean risen(Mob mob) {
		if (!mob.isAlive() || Legendary.boss(mob) || mob.getClass().getName().startsWith("com.jaymoskingdoms.barrow.")) {
			return false;
		}
		return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof com.jaymoskingdoms.inhabitants.Draugr;
	}

	/** A line the beam and the charge stop at: the first solid thing from {@code from} toward {@code to}. */
	static Vec3 clip(ServerLevel level, WitherBoss wither, Vec3 from, Vec3 to) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wither));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** Whether {@code victim} is fair game for its blows: not another of its side. */
	static boolean fair(WitherBoss wither, LivingEntity victim) {
		return victim != wither && victim.isAlive() && !victim.is(EntityTypeTags.WITHER_FRIENDS) && !victim.entityTags().contains(GUARD_TAG)
			&& !(victim instanceof ServerPlayer p && p.isSpectator());
	}

	static AABB around(Vec3 a, Vec3 b, double by) {
		return new AABB(a, b).inflate(by);
	}

	/** Stuns it where it is: it can't act for {@code ticks}, and falls. */
	static void stun(WitherBoss wither, int ticks) {
		Fight fight = FIGHTS.get(wither);
		if (fight == null || !(wither.level() instanceof ServerLevel level)) {
			return;
		}
		fight.stunUntil = level.getGameTime() + ticks;
		WitherMoves.end(fight.move);
		wither.setNoAi(true);
		wither.setNoGravity(false);
		wither.setDeltaMovement(0.0, -0.05, 0.0);
		Vec3 at = wither.position().add(0.0, wither.getBbHeight() * 0.6, 0.0);
		Fx.blast(level, at, 3.0F, 0xFFD060);
		level.playSound(null, wither.getX(), wither.getY(), wither.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.5F);
		level.playSound(null, wither.getX(), wither.getY(), wither.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
		PowersCompat.tellGlint(level, at, false);
	}
}
