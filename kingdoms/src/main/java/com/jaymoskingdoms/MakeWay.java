package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27, a mounted guard sat in a doorway while a villager stood outside it, trying to get home): "other
 * villagers should be able to talk to each other and tell them to move out of the way if they are blocking."
 * <p>
 * A villager (or one of our folk) whose walk has stalled ({@link Unstick}) looks at the next few steps of its path. If
 * someone stands on them, it says so, out loud in a bubble ("Excuse me, coming through", "Move that horse, would you?",
 * "Shoo!"), and they give way:
 * <ul>
 * <li>one on foot steps aside, off the path, and stands there a moment (and may say sorry);</li>
 * <li>a realm's rider in a doorway or under a roof gets down and leads the horse out to a fence ({@link Hitching}); any
 * other rider moves the horse aside;</li>
 * <li>an animal (a cow in the door, a loose horse) is shooed aside.</li>
 * </ul>
 * A player in the way is only asked: nobody moves you. Nobody asked gives way while they sleep, are down, are fighting,
 * are at a fire or in a talk with a player; then the asker's walk gives up as before.
 */
public final class MakeWay {
	/** Path steps looked at past the one they're on. */
	static final int AHEAD = 3;
	/** Someone asks at most this often (ticks); the same one is asked at most this often. */
	static final long ASK_EVERY = 200L, ASKED_EVERY = 100L;
	/** How long one who gives way stands aside (ticks); how far aside they go at most; clear of the path by this. */
	static final long ASIDE_TICKS = 80L;
	static final int ASIDE_FAR = 4;
	static final double CLEAR = 1.6;
	/** Words heard this far; the chance the one moving says sorry. */
	static final double SPEECH_RANGE = 12.0;
	static final float REPLY_CHANCE = 0.5F;
	/** Lines each kind has (lang {@code speech.jaymoskingdoms.make_way.<kind>.N}). */
	static final int ASK_LINES = 4, RIDER_LINES = 4, SHOO_LINES = 3, PLAYER_LINES = 3, REPLY_LINES = 4, RIDER_REPLY_LINES = 3;
	/** Their pace stepping aside (navigation's speed). */
	static final double STEP_SPEED = 0.7;

	private record Aside(BlockPos to, long until) {
	}

	/** Who's standing aside: where, and until when. */
	private static final Map<UUID, Aside> ASIDE = new HashMap<>();
	/** When someone last asked; when someone was last asked. */
	private static final Map<UUID, Long> ASKED = new HashMap<>(), ASKED_OF = new HashMap<>();

	private MakeWay() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MakeWay::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ASIDE.clear();
			ASKED.clear();
			ASKED_OF.clear();
		});
	}

	/** From {@link Workers#step}: true while {@code mob} stands aside for someone (their brain waits). */
	static boolean brainStep(PathfinderMob mob) {
		return ASIDE.containsKey(mob.getUUID());
	}

	/**
	 * From {@link Unstick}: {@code mob}'s walk along {@code path} has stalled. If someone stands on the next steps of it,
	 * it asks them to move, and they do (if they can).
	 */
	static void stalled(ServerLevel level, Mob mob, @Nullable Path path) {
		if (path == null || path.isDone() || !(mob instanceof Villager || mob instanceof Folk) || mob.isSleeping() || Downed.isDowned(mob)) {
			return;
		}
		long now = level.getGameTime();
		Long last = ASKED.get(mob.getUUID());
		if (last != null && now - last < ASK_EVERY) {
			return;
		}
		Entity blocker = blocker(level, mob, path);
		if (blocker == null) {
			return;
		}
		Long lastOf = ASKED_OF.get(blocker.getUUID());
		if (lastOf != null && now - lastOf < ASKED_EVERY) {
			return;
		}
		ASKED.put(mob.getUUID(), now);
		ASKED_OF.put(blocker.getUUID(), now);
		RandomSource random = level.getRandom();
		Entity rider = blocker.getFirstPassenger();
		if (blocker instanceof Player) {
			say(level, mob, "player", PLAYER_LINES, random);
			return;
		}
		if (blocker instanceof AbstractHorse horse && rider instanceof PathfinderMob man) {
			say(level, mob, "rider", RIDER_LINES, random);
			if ((Hitching.doorway(level, horse.blockPosition()) || Overgrowth.roofed(level, horse.blockPosition())) && Hitching.getDown(level, man)) {
				say(level, man, "rider_reply", RIDER_REPLY_LINES, random);
				return;
			}
			if (mayMove(man) && stepAside(level, man, horse, mob, path, now) && random.nextFloat() < REPLY_CHANCE) {
				say(level, man, "reply", REPLY_LINES, random);
			}
			return;
		}
		if (blocker instanceof Villager || blocker instanceof Folk) {
			PathfinderMob them = (PathfinderMob) blocker;
			say(level, mob, "ask", ASK_LINES, random);
			if (mayMove(them) && stepAside(level, them, them, mob, path, now) && random.nextFloat() < REPLY_CHANCE) {
				say(level, them, "reply", REPLY_LINES, random);
			}
			return;
		}
		if (blocker instanceof Animal beast && !beast.isLeashed() && !Hitching.hitched(beast)) {
			say(level, mob, "shoo", SHOO_LINES, random);
			stepAside(level, beast, beast, mob, path, now);
		}
	}

	/** Whoever stands on the next {@link #AHEAD} steps of {@code mob}'s path (what they ride, if they're mounted), nearest first; or null. */
	private static @Nullable Entity blocker(ServerLevel level, Mob mob, Path path) {
		int from = Math.max(0, path.getNextNodeIndex() - 1), to = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + AHEAD);
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = from; i <= to; i++) {
			BlockPos at = path.getNodePos(i);
			AABB box = new AABB(at).expandTowards(0.0, 1.0, 0.0).deflate(0.1);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive() && !(e instanceof Enemy) && !e.isSpectator())) {
				Entity root = e.getRootVehicle();
				if (root == mob.getRootVehicle() || root.hasPassenger(mob)) {
					continue;
				}
				double d = root.distanceToSqr(mob);
				if (d < bestD) {
					bestD = d;
					best = root;
				}
			}
		}
		return best;
	}

	/** {@code who} can give way: awake, up, out of a fight, not at the fire, not talking with a player, not at a trade. */
	private static boolean mayMove(PathfinderMob who) {
		if (who.isSleeping() || Downed.isDowned(who) || who.getTarget() != null || Rulers.target(who) != null || who.isNoAi() || Firesides.sitting(who)
			|| PowersCompat.isCarried(who) || McaCompat.busy(who)) {
			return false;
		}
		return !(who instanceof Villager v) || v.getTradingPlayer() == null;
	}

	/**
	 * {@code mover} (whose legs, or whose horse, are {@code body}) steps off {@code asker}'s path to a spot near by, and stands
	 * there a moment. False if there's nowhere to go.
	 */
	private static boolean stepAside(ServerLevel level, PathfinderMob mover, Entity body, Mob asker, Path path, long now) {
		BlockPos spot = aside(level, body, asker, path);
		if (spot == null) {
			return false;
		}
		ASIDE.put(mover.getUUID(), new Aside(spot, now + ASIDE_TICKS));
		walk(mover, spot);
		return true;
	}

	/** A standing spot within {@link #ASIDE_FAR} of {@code body}, at least {@link #CLEAR} off every step of the path left, nearest first. */
	private static @Nullable BlockPos aside(ServerLevel level, Entity body, Mob asker, Path path) {
		BlockPos at = body.blockPosition();
		if (!Loaded.made(level, at.offset(-ASIDE_FAR, 0, -ASIDE_FAR), at.offset(ASIDE_FAR, 0, ASIDE_FAR))) {
			return null;
		}
		boolean big = body.getBbWidth() > 1.0F;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-ASIDE_FAR, -1, -ASIDE_FAR), at.offset(ASIDE_FAR, 1, ASIDE_FAR))) {
			double d = p.distSqr(at);
			if (d >= bestD || d < 2.0 || !standable(level, p) || big && !Hitching.open(level, p) || onPath(p, path) || p.closerThan(asker.blockPosition(), 2.0)) {
				continue;
			}
			bestD = d;
			best = p.immutable();
		}
		return best;
	}

	private static boolean onPath(BlockPos p, Path path) {
		for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < path.getNodeCount(); i++) {
			BlockPos n = path.getNodePos(i);
			double dx = n.getX() - p.getX(), dz = n.getZ() - p.getZ();
			if (dx * dx + dz * dz < CLEAR * CLEAR && Math.abs(n.getY() - p.getY()) <= 2) {
				return true;
			}
		}
		return false;
	}

	private static boolean standable(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
			&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty() && level.getFluidState(at).isEmpty();
	}

	private static void walk(PathfinderMob mover, BlockPos to) {
		if (mover instanceof Villager v && !v.isPassenger()) {
			// (a villager whose brain isn't held by Workers#step still heads there)
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, (float) STEP_SPEED, 0);
		}
		// (a rider's navigation is his horse's)
		mover.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, STEP_SPEED);
	}

	private static void tick(MinecraftServer server) {
		if (ASIDE.isEmpty() && ASKED.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 5 != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Aside>> it = ASIDE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Aside> e = it.next();
			Aside a = e.getValue();
			if (now > a.until() || !(level.getEntity(e.getKey()) instanceof PathfinderMob m) || !m.isAlive() || m.getTarget() != null) {
				it.remove();
				continue;
			}
			Entity body = m.getRootVehicle();
			if (body.blockPosition().closerThan(a.to(), 1.2)) {
				m.getNavigation().stop();
			} else if (m.getNavigation().isDone()) {
				walk(m, a.to());
			}
		}
		if (now % 1200 == 0) {
			ASKED.values().removeIf(t -> now - t > ASK_EVERY);
			ASKED_OF.values().removeIf(t -> now - t > ASKED_EVERY);
		}
	}

	private static void say(ServerLevel level, Entity speaker, String kind, int lines, RandomSource random) {
		Speech.say(level, speaker, Component.translatable("speech.jaymoskingdoms.make_way." + kind + "." + random.nextInt(lines)), Speech.WHITE, SPEECH_RANGE);
	}
}
