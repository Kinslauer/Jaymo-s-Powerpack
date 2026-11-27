package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Herds and the hunters that live on them (Andy's nature picks, 2026-11-26; {@link Nature}).
 * <ul>
 * <li><b>A lookout</b>: a grazing herd (not the deer, which have their own {@link ShyDeer}) sees a hunter within
 * {@link #SEE_BEAST}, or a player within {@link #SEE_PLAYER} ({@link #SEE_SNEAK} sneaking; farther downwind, nearer
 * upwind, by {@link WolfStalkers#windShare}), and gives the alarm: the whole herd bolts the same way together.</li>
 * <li><b>Water holes</b>: in a dry spell ({@link #DRY_DAYS} days without rain) herds walk to standing water, and hunters
 * and crocodiles learn to wait by the holes they use.</li>
 * <li><b>Crossings</b>: a herd whose way crosses water stops at the bank till {@link #CROSS_TOGETHER} have gathered (or
 * {@link #CROSS_WAIT} has passed), then all plunge in together; crocodiles go to the crossings herds have used.</li>
 * <li><b>The year's moves</b>: reindeer, bison, muskox and the like drift to lower, warmer land in winter and back up
 * in spring and summer, following the grass.</li>
 * <li><b>Grazing</b>: grazers crop the grass where they stand (the plant first, then now and then the turf to dirt,
 * which grass grows back over), and a herd with nothing left round it moves on to greener ground.</li>
 * <li><b>Hunters follow the herds</b>: a hungry hunter with no herd near goes toward the nearest one within
 * {@link #FOLLOW_FAR}; with none at all it wanders off as it always has.</li>
 * </ul>
 */
final class HerdWays {
	static final int STEP_EVERY = 20;
	static final double SEE_BEAST = 20.0, SEE_PLAYER = 14.0, SEE_SNEAK = 7.0, KIN = 16.0;
	static final int BOLT_FAR = 20;
	static final double BOLT_PACE = 1.6;
	static final long ALARM_GAP = 200;
	static final long DRY_DAYS = 5;
	static final int HOLE_LOOK = 40, HOLE_TRIES = 16;
	static final long HOLE_KEPT = 20L * 120;
	static final int CROSS_TOGETHER = 3;
	static final long CROSS_WAIT = 20L * 10, FORD_KEPT = 20L * 60 * 5;
	static final int MOVE_FAR = 40;
	static final int GRAZE_LOOK = 6, GREENER_LOOK = 24;
	static final double FOLLOW_NEAR = 32.0, FOLLOW_FAR = 96.0, FOLLOW_STOP = 20.0, CROC_LOOK = 48.0;

	private static final TagKey<EntityType<?>> SHY_DEER = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("shy_deer"));

	/** A watering place or a crossing, for the hunters and crocodiles. */
	private record Spot(ServerLevel level, BlockPos pos, long until) {
	}

	private static final List<Spot> HOLES = new ArrayList<>(), FORDS = new ArrayList<>();
	private static final Map<Mob, Long> ALARMED = new WeakHashMap<>();

	/** A herd waiting at a bank to cross: where to, and since when. */
	private record Crossing(Vec3 to, double pace, long since) {
	}

	private static final Map<Mob, Crossing> CROSSING = new WeakHashMap<>();

	private HerdWays() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HerdWays::tick);
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % STEP_EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		HOLES.removeIf(s -> s.until < now);
		FORDS.removeIf(s -> s.until < now);
		boolean dry = Nature.dryDays(server.overworld()) >= DRY_DAYS;
		for (Mob g : Nature.of(Nature.GRAZERS)) {
			if (!Nature.free(g) || !(g.level() instanceof ServerLevel level)) {
				continue;
			}
			if (!Nature.is(g, SHY_DEER) && lookout(level, g)) {
				continue;
			}
			crossing(level, g);
			if (t % 200 == 0 && level.getRandom().nextInt(3) == 0) {
				graze(level, g);
			}
			if (dry && t % 200 == 0 && level.getRandom().nextInt(4) == 0) {
				drink(level, g);
			}
		}
		if (t % 1200 == 0) {
			for (Mob m : Nature.of(Nature.SEASONAL_MOVERS)) {
				if (Nature.free(m) && m.level() instanceof ServerLevel level && level.getRandom().nextInt(3) == 0) {
					move(level, m);
				}
			}
		}
		if (t % 200 == 0) {
			for (Mob h : Nature.of(Nature.HERD_HUNTERS)) {
				if (Nature.free(h) && !FedPredators.fed(h) && h.level() instanceof ServerLevel level) {
					if (dry && waitByWater(level, h)) {
						continue;
					}
					if (t % 600 == 0) {
						follow(level, h);
					}
				}
			}
		}
	}

	// ---- the lookout ----------------------------------------------------------------------------------------------

	/** Whether {@code g} saw something and the herd's off. */
	private static boolean lookout(ServerLevel level, Mob g) {
		long now = level.getGameTime();
		if (now - ALARMED.getOrDefault(g, Long.MIN_VALUE / 2) < ALARM_GAP) {
			return false;
		}
		LivingEntity threat = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, g.getBoundingBox().inflate(SEE_BEAST), e -> e.isAlive() && e != g)) {
			if (e instanceof Player p) {
				if (p.isCreative() || p.isSpectator()) {
					continue;
				}
				double reach = (p.isShiftKeyDown() ? SEE_SNEAK : SEE_PLAYER) * WolfStalkers.windShare(level, p.position(), g.position());
				if (g.distanceTo(p) <= reach && g.hasLineOfSight(p)) {
					threat = p;
					break;
				}
			} else if (e instanceof Mob m && (Nature.is(m, Nature.HERD_HUNTERS) || Nature.is(m, Nature.BEARS)) && Nature.wild(m) && g.hasLineOfSight(m)) {
				threat = m;
				break;
			}
		}
		if (threat == null) {
			return false;
		}
		g.playAmbientSound();
		Vec3 off = g.position().subtract(threat.position()).multiply(1.0, 0.0, 1.0);
		off = off.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : off.normalize();
		Vec3 to = g.position().add(off.scale(BOLT_FAR));
		for (Mob k : kin(level, g)) {
			ALARMED.put(k, now);
			CROSSING.remove(k);
			Nature.go(k, to.add(level.getRandom().nextGaussian() * 2.0, 0.0, level.getRandom().nextGaussian() * 2.0), BOLT_PACE);
		}
		return true;
	}

	private static List<Mob> kin(ServerLevel level, Mob g) {
		return level.getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(KIN), m -> m.getType() == g.getType() && m.isAlive() && Nature.wild(m));
	}

	// ---- moving as a herd, and crossing water -----------------------------------------------------------------------

	/** The herd round {@code leader} goes to {@code to}, stopping at the bank first if water lies between. */
	static void herdTo(ServerLevel level, Mob leader, Vec3 to, double pace) {
		Vec3 bank = bankBefore(level, leader.position(), to);
		long now = level.getGameTime();
		for (Mob k : kin(level, leader)) {
			if (bank != null) {
				CROSSING.put(k, new Crossing(to, pace, now));
				Nature.go(k, bank, pace);
			} else {
				Nature.go(k, to.add(level.getRandom().nextGaussian() * 2.0, 0.0, level.getRandom().nextGaussian() * 2.0), pace);
			}
		}
	}

	/** The last dry spot before water on the way from {@code from} to {@code to}, or null if the way is dry. */
	private static @Nullable Vec3 bankBefore(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from).multiply(1.0, 0.0, 1.0);
		double length = step.length();
		if (length < 2.0) {
			return null;
		}
		step = step.scale(2.0 / length);
		Vec3 at = from, dry = from;
		for (int i = 0; i < (int) (length / 2.0); i++) {
			at = at.add(step);
			BlockPos top = Nature.top(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
			if (level.getFluidState(top.below()).is(Fluids.WATER)) {
				return dry;
			}
			dry = Vec3.atBottomCenterOf(top);
		}
		return null;
	}

	/** A herd at a bank: once enough have gathered, or it's waited long enough, all go in together. */
	private static void crossing(ServerLevel level, Mob g) {
		Crossing c = CROSSING.get(g);
		if (c == null) {
			return;
		}
		long now = level.getGameTime();
		List<Mob> waiting = level.getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(5.0), m -> m.getType() == g.getType() && CROSSING.containsKey(m));
		if (waiting.size() < CROSS_TOGETHER && now - c.since < CROSS_WAIT) {
			return;
		}
		for (Mob k : waiting) {
			CROSSING.remove(k);
			Nature.go(k, c.to.add(level.getRandom().nextGaussian() * 2.0, 0.0, level.getRandom().nextGaussian() * 2.0), c.pace * 1.2);
		}
		FORDS.removeIf(s -> s.level == level && s.pos.closerThan(g.blockPosition(), 12.0));
		FORDS.add(new Spot(level, g.blockPosition(), now + FORD_KEPT));
	}

	// ---- water holes ----------------------------------------------------------------------------------------------

	private static void drink(ServerLevel level, Mob g) {
		if (nearWater(level, g.blockPosition(), 4)) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < HOLE_TRIES; i++) {
			BlockPos top = Nature.top(level, g.getBlockX() + random.nextInt(HOLE_LOOK * 2 + 1) - HOLE_LOOK, g.getBlockZ() + random.nextInt(HOLE_LOOK * 2 + 1) - HOLE_LOOK);
			if (level.isLoaded(top) && level.getFluidState(top.below()).is(Fluids.WATER)) {
				double d = top.distSqr(g.blockPosition());
				if (d < bestD) {
					bestD = d;
					best = top.below();
				}
			}
		}
		if (best == null) {
			return;
		}
		herdTo(level, g, Vec3.atCenterOf(best), 1.0);
		BlockPos hole = best;
		HOLES.removeIf(s -> s.level == level && s.pos.closerThan(hole, 12.0));
		HOLES.add(new Spot(level, hole, level.getGameTime() + HOLE_KEPT));
	}

	private static boolean nearWater(ServerLevel level, BlockPos at, int r) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 1, r))) {
			if (level.getFluidState(p).is(Fluids.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** A hungry hunter in a drought waits near a water hole the herds use. */
	private static boolean waitByWater(ServerLevel level, Mob h) {
		Spot hole = nearest(HOLES, level, h.blockPosition(), FOLLOW_FAR);
		if (hole == null) {
			return false;
		}
		if (!hole.pos.closerThan(h.blockPosition(), 10.0)) {
			Nature.go(h, Vec3.atCenterOf(hole.pos).add(level.getRandom().nextGaussian() * 4.0, 0.0, level.getRandom().nextGaussian() * 4.0), 1.0);
		}
		return true;
	}

	/** A crocodile or alligator with nothing in sight goes to a crossing or a water hole the herds use. */
	static void crocodile(ServerLevel level, Mob croc) {
		Spot s = nearest(FORDS, level, croc.blockPosition(), CROC_LOOK);
		if (s == null) {
			s = nearest(HOLES, level, croc.blockPosition(), CROC_LOOK);
		}
		if (s != null && !s.pos.closerThan(croc.blockPosition(), 4.0)) {
			Nature.go(croc, Vec3.atCenterOf(s.pos), 0.9);
		}
	}

	private static @Nullable Spot nearest(List<Spot> spots, ServerLevel level, BlockPos at, double within) {
		Spot best = null;
		double bestD = within * within;
		for (Spot s : spots) {
			double d = s.level == level ? s.pos.distSqr(at) : Double.MAX_VALUE;
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}

	// ---- the year's moves, grazing, hunters following --------------------------------------------------------------

	/** Winter: toward lower, warmer land; spring and summer: back up. */
	private static void move(ServerLevel level, Mob m) {
		String season = Nature.season(level);
		if (season == null || "autumn".equals(season)) {
			return;
		}
		boolean down = "winter".equals(season);
		Vec3 best = null;
		double bestScore = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 4.0 * i;
			int x = m.getBlockX() + (int) (Math.cos(a) * MOVE_FAR), z = m.getBlockZ() + (int) (Math.sin(a) * MOVE_FAR);
			BlockPos top = Nature.top(level, x, z);
			if (!level.isLoaded(top) || level.getFluidState(top.below()).is(Fluids.WATER)) {
				continue;
			}
			float warm = level.getBiome(top).value().getBaseTemperature();
			double score = (down ? -top.getY() + warm * 40.0 : top.getY() - warm * 40.0);
			if (score > bestScore) {
				bestScore = score;
				best = Vec3.atBottomCenterOf(top);
			}
		}
		if (best != null) {
			herdTo(level, m, best, 1.0);
		}
	}

	private static boolean grazeable(BlockState s) {
		return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.SHORT_DRY_GRASS) || s.is(Blocks.TALL_DRY_GRASS);
	}

	/** {@code g} crops what it stands on; with nothing left round it, the herd moves to greener ground. */
	private static void graze(ServerLevel level, Mob g) {
		BlockPos at = g.blockPosition();
		BlockState here = level.getBlockState(at);
		if (grazeable(here)) {
			level.destroyBlock(at, false);
			return;
		}
		if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && here.isAir() && level.getRandom().nextInt(3) == 0) {
			level.setBlockAndUpdate(at.below(), Blocks.DIRT.defaultBlockState());
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < 8; i++) {
			BlockPos p = Nature.top(level, at.getX() + random.nextInt(GRAZE_LOOK * 2 + 1) - GRAZE_LOOK, at.getZ() + random.nextInt(GRAZE_LOOK * 2 + 1) - GRAZE_LOOK);
			if (grazeable(level.getBlockState(p))) {
				Nature.go(g, Vec3.atBottomCenterOf(p), 0.8);
				return;
			}
		}
		for (int i = 0; i < 8; i++) {
			BlockPos p = Nature.top(level, at.getX() + random.nextInt(GREENER_LOOK * 2 + 1) - GREENER_LOOK, at.getZ() + random.nextInt(GREENER_LOOK * 2 + 1) - GREENER_LOOK);
			if (level.isLoaded(p) && grazeable(level.getBlockState(p))) {
				herdTo(level, g, Vec3.atBottomCenterOf(p), 1.0);
				return;
			}
		}
	}

	/** A hungry hunter with no herd near goes toward the nearest one. */
	private static void follow(ServerLevel level, Mob h) {
		Mob near = null;
		double best = FOLLOW_FAR * FOLLOW_FAR;
		for (Mob g : Nature.of(Nature.GRAZERS)) {
			if (g.level() != level) {
				continue;
			}
			double d = g.distanceToSqr(h);
			if (d < FOLLOW_NEAR * FOLLOW_NEAR) {
				return;
			}
			if (d < best) {
				best = d;
				near = g;
			}
		}
		if (near != null) {
			Vec3 off = h.position().subtract(near.position()).multiply(1.0, 0.0, 1.0).normalize().scale(FOLLOW_STOP);
			Nature.go(h, near.position().add(off), 0.9);
		}
	}
}
