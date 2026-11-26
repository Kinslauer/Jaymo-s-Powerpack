package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A fire is cried out and the cry is passed on until the whole settlement turns out for it. Andy (2026-11-26), after a
 * mine fire took half a city: "NPCs should be far more alarmed during disasters like fire and Tornadoes, it should
 * spread from person to person until everyone has a hand in fighting the fire if possible. there was a raging mine fire
 * that could have been caught and stopped early".
 * <ul>
 * <li><b>Spotting.</b> Any of the townsfolk (villagers, MCA's too, and the settlement's own folk, a mine's diggers among
 * them) who can see a watched fire ({@link Firefighting}) within {@link #SPOT}, or smells its smoke within {@link #SMELL}
 * through any wall, cries "Fire!" (a fire with stone overhead is "fire in the mine"). A sleeper who smells it wakes.</li>
 * <li><b>Passing it on.</b> Everyone within {@link #SHOUT} of someone crying it hears it, wakes if asleep, and a moment
 * later ({@link #PASS_MIN} to {@link #PASS_MAX}) cries it on in turn, so the word runs from house to house. The
 * settlement's bell is rung while it burns (its sound only: a swung bell would send vanilla villagers to hide), and
 * everyone near the bell hears that too. The iron golems hear it and come, saying nothing.</li>
 * <li><b>Turning out.</b> Everyone who has the word and is able heads for the nearest of the fires there; once they're
 * within {@link Firefighting#REACH} of it, {@link Firefighting} has them fight it (beating it out, throwing water, a
 * bucket line from the nearest water), or take a child away from it. Children, the downed, those in a fight or a brawl
 * and anyone running from a monster or a twister don't go.</li>
 * <li><b>Standing down.</b> Once no watched fire is left within {@link #GATHER} of where it started for
 * {@link #CALM_TICKS}, someone says it's out and everyone goes back to their day.</li>
 * </ul>
 * Nothing here is saved: a restart ends an alarm (its fires are watched again as they burn on).
 */
public final class FireAlarm {
	/** How far the townsfolk see a fire (blocks, with a clear line), and smell its smoke through anything. */
	static final double SPOT = 32.0, SMELL = 8.0;
	/** How far a cry is heard; how far round its start an alarm's fires are; how far a rung bell is heard (blocks). */
	static final double SHOUT = 20.0, GATHER = 48.0, BELL_HEARD = 40.0;
	/** How often the alarms are kept (ticks); the most fires looked over for watchers each time. */
	static final int EVERY = 10, MOST_LOOKED = 24;
	/** How long before one who heard it cries it on (ticks, at least and at most); how long the word lasts unless renewed. */
	static final int PASS_MIN = 15, PASS_MAX = 40;
	static final long WORD_TICKS = 20L * 60;
	/** How often the bell is rung while an alarm stands; an alarm stands down after this long with nothing burning (ticks). */
	static final long BELL_EVERY = 50L, CALM_TICKS = 20L * 5;
	/** The most people given the word each time (so a city turning out doesn't all happen in one tick). */
	static final int MOST_RAISED = 64;
	/** How fast they go to the fire: villagers, folk, golems. */
	static final float VILLAGER_RUN = 0.85F;
	static final double FOLK_RUN = 1.3, GOLEM_RUN = 1.1;
	/** Lines each (lang {@code talk.jaymoskingdoms.fire_alarm.<key>.<n>}). */
	static final int SPOT_LINES = 6, MINE_LINES = 4, PASS_LINES = 8, OUT_LINES = 5;

	/** Where an alarm started, its settlement (or -1), its bell, and when its fires were last seen burning. */
	private static final class Front {
		final BlockPos at;
		final int faction;
		@Nullable BlockPos bell;
		long rang = Long.MIN_VALUE, burning;

		Front(BlockPos at, int faction, long now) {
			this.at = at;
			this.faction = faction;
			this.burning = now;
		}
	}

	/** Who has the word: of which front, when they cry it on (or Long.MAX_VALUE once they have), and till when it holds. */
	private static final class Word {
		final Front front;
		long passAt;
		long until;

		Word(Front front, long passAt, long until) {
			this.front = front;
			this.passAt = passAt;
			this.until = until;
		}
	}

	private static final List<Front> FRONTS = new ArrayList<>();
	private static final Map<UUID, Word> WORD = new HashMap<>();
	/** Where the fire-watching left off, so a big blaze's fires are all looked over in turn. */
	private static int lookFrom;

	private FireAlarm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FireAlarm::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FRONTS.clear();
			WORD.clear();
			lookFrom = 0;
		});
	}

	/** {@code who} has had word of a fire and is turning out for it (or taking a child away from it). */
	static boolean alarmed(Entity who) {
		return WORD.containsKey(who.getUUID());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		List<BlockPos> fires = Firefighting.watched();
		if (fires.isEmpty() && FRONTS.isEmpty()) {
			return;
		}
		watch(level, fires, now);
		int raised = spread(level, now);
		for (Front front : FRONTS) {
			if (burningNear(fires, front.at)) {
				front.burning = now;
			}
			ring(level, front, now, raised);
		}
		turnOut(level, fires, now);
		standDown(level, now);
	}

	// ---- spotting --------------------------------------------------------------------------------------------------

	/** The townsfolk near each fire nobody has cried yet look about them: whoever sees it (or smells it) cries out. */
	private static void watch(ServerLevel level, List<BlockPos> fires, long now) {
		int n = fires.size();
		if (n == 0) {
			return;
		}
		int looked = 0;
		for (int i = 0; i < n && looked < MOST_LOOKED; i++) {
			BlockPos fire = fires.get((lookFrom + i) % n);
			if (frontNear(fire) != null) {
				continue;
			}
			looked++;
			LivingEntity spotter = spotter(level, fire);
			if (spotter != null) {
				Faction f = Factions.around(level.getServer(), level, fire, Firefighting.WATCHED);
				Front front = new Front(fire, f == null ? -1 : f.id(), now);
				FRONTS.add(front);
				raise(level, spotter, front, now, mine(level, fire) ? "mine" : "spot");
			}
		}
		lookFrom = (lookFrom + Math.max(1, looked)) % n;
	}

	/** The nearest of the townsfolk who sees {@code fire} or smells it; null if nobody does. */
	private static @Nullable LivingEntity spotter(ServerLevel level, BlockPos fire) {
		Vec3 at = Vec3.atCenterOf(fire);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(fire).inflate(SPOT), e -> e.isAlive() && !(e instanceof IronGolem) && townsfolk(e))) {
			double d = e.distanceToSqr(at);
			if (d >= bestD || d > SPOT * SPOT) {
				continue;
			}
			// a sleeper smells it close by, and wakes; one awake sees it (or smells it through the wall)
			if (d <= SMELL * SMELL || !e.isSleeping() && sees(level, e, fire)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** {@code e} has a clear line from its eyes to {@code fire}. */
	private static boolean sees(ServerLevel level, LivingEntity e, BlockPos fire) {
		BlockHitResult hit = level.clip(new ClipContext(e.getEyePosition(), Vec3.atCenterOf(fire), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().distManhattan(fire) <= 1;
	}

	/** {@code fire} burns with no sky over it (a mine, a cellar, a cave): its cry is the mine's. */
	private static boolean mine(ServerLevel level, BlockPos fire) {
		return !level.canSeeSky(fire.above()) && fire.getY() < level.getSeaLevel() + 8;
	}

	// ---- the word passed on ----------------------------------------------------------------------------------------

	/**
	 * {@code who} has the word of {@code front}'s fire: it wakes, cries it ({@code key}: "spot", "mine" or "pass"; a golem
	 * never speaks) and passes it on in a moment.
	 */
	private static void raise(ServerLevel level, LivingEntity who, Front front, long now, String key) {
		int lines = switch (key) {
			case "spot" -> SPOT_LINES;
			case "mine" -> MINE_LINES;
			default -> PASS_LINES;
		};
		WORD.put(who.getUUID(), new Word(front, now + PASS_MIN + level.getRandom().nextInt(PASS_MAX - PASS_MIN + 1), now + WORD_TICKS));
		if (who.isSleeping()) {
			who.stopSleeping();
		}
		if (who instanceof IronGolem) {
			return;
		}
		Speech.say(level, who, Component.translatable("talk.jaymoskingdoms.fire_alarm." + key + "." + level.getRandom().nextInt(lines)), Speech.RED, SHOUT * 2.0);
		if (who instanceof Villager) {
			level.playSound(null, who.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.2F);
		}
	}

	/** Each one whose moment has come cries the word on to everyone in earshot who hasn't had it. Returns how many got it. */
	private static int spread(ServerLevel level, long now) {
		int raised = 0;
		List<Map.Entry<UUID, Word>> due = new ArrayList<>();
		for (Map.Entry<UUID, Word> e : WORD.entrySet()) {
			if (e.getValue().passAt <= now) {
				due.add(e);
			}
		}
		for (Map.Entry<UUID, Word> e : due) {
			Word word = e.getValue();
			word.passAt = Long.MAX_VALUE;
			Entity crier = level.getEntity(e.getKey());
			if (!(crier instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			raised += hear(level, living.position(), SHOUT, word.front, now, MOST_RAISED - raised);
			if (raised >= MOST_RAISED) {
				break;
			}
		}
		return raised;
	}

	/** Everyone of the townsfolk within {@code range} of {@code at} who hasn't the word yet gets it (at most {@code most}). */
	private static int hear(ServerLevel level, Vec3 at, double range, Front front, long now, int most) {
		int raised = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range),
			e -> e.isAlive() && !WORD.containsKey(e.getUUID()) && e.distanceToSqr(at) <= range * range && (e instanceof IronGolem || townsfolk(e)))) {
			if (raised >= most) {
				break;
			}
			raise(level, e, front, now, "pass");
			raised++;
		}
		return raised;
	}

	/** {@code e} is one of a settlement's people who'd cry a fire: a villager (MCA's too) or our folk, not an attacker. */
	private static boolean townsfolk(LivingEntity e) {
		if (Downed.isDowned(e) || Theft.rioting(e)) {
			return false;
		}
		if (e instanceof Folk folk) {
			return !Assault.attacker(folk) && folk.role() != Folk.BARBARIAN && folk.role() != Folk.CHIEF;
		}
		if (e instanceof Villager v) {
			return !(v instanceof Worker w && WarParties.isSoldier(w));
		}
		return false;
	}

	/** The settlement's bell is rung while the alarm stands; whoever's near the bell hears it. */
	private static void ring(ServerLevel level, Front front, long now, int raised) {
		if (now - front.burning > CALM_TICKS || now - front.rang < BELL_EVERY) {
			return;
		}
		boolean first = front.rang == Long.MIN_VALUE;
		front.rang = now;
		if (first && front.faction >= 0) {
			Faction f = Factions.get(level.getServer(), front.faction);
			front.bell = f == null ? null : WarAlert.findBell(level, f.seat());
		}
		BlockPos at = front.bell != null ? front.bell : null;
		if (at == null) {
			return;
		}
		level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 1.1F);
		hear(level, Vec3.atCenterOf(at), BELL_HEARD, front, now, Math.max(0, MOST_RAISED - raised));
	}

	// ---- turning out -----------------------------------------------------------------------------------------------

	/**
	 * Everyone with the word who's able goes to the nearest fire of its alarm; within {@link Firefighting#REACH} of it
	 * {@link Firefighting} takes over. The word holds while the fire burns.
	 */
	private static void turnOut(ServerLevel level, List<BlockPos> fires, long now) {
		for (Iterator<Map.Entry<UUID, Word>> it = WORD.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Word> e = it.next();
			Word word = e.getValue();
			Entity who = level.getEntity(e.getKey());
			if (!(who instanceof Mob mob) || !mob.isAlive()) {
				if (word.until < now) {
					it.remove();
				}
				continue;
			}
			BlockPos fire = nearest(fires, word.front.at, mob);
			if (fire == null) {
				continue;
			}
			word.until = Math.max(word.until, now + WORD_TICKS / 2);
			if (mob.distanceToSqr(Vec3.atCenterOf(fire)) <= Firefighting.REACH * Firefighting.REACH || !able(mob)) {
				// (close enough: Firefighting answers it; or not one to go)
				continue;
			}
			BlockPos to = Firefighting.beside(mob, fire);
			if (mob instanceof Villager v) {
				Firefighting.turnOut(level, v, fire, to, VILLAGER_RUN, now);
			} else {
				if (mob instanceof net.minecraft.world.entity.PathfinderMob pm) {
					Undertakers.calledAway(level, pm);
				}
				mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, mob instanceof IronGolem ? GOLEM_RUN : FOLK_RUN);
			}
		}
	}

	/** {@code mob} can turn out: grown, not fighting, not running from something worse, not held. */
	private static boolean able(Mob mob) {
		if (mob.isBaby() || mob.getTarget() != null || Downed.isDowned(mob) || Theft.rioting(mob) || mob.isPassenger() || mob.isLeashed()) {
			return false;
		}
		return !(mob instanceof Villager v) || !Frights.afraid(v.getUUID()) && !McaCompat.panicking(v);
	}

	/** The watched fire nearest {@code who} within {@link #GATHER} of {@code at}; null if none burns there. */
	private static @Nullable BlockPos nearest(List<BlockPos> fires, BlockPos at, Entity who) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos f : fires) {
			if (f.distSqr(at) > GATHER * GATHER) {
				continue;
			}
			double d = who.distanceToSqr(Vec3.atCenterOf(f));
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	private static boolean burningNear(List<BlockPos> fires, BlockPos at) {
		for (BlockPos f : fires) {
			if (f.distSqr(at) <= GATHER * GATHER) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Front frontNear(BlockPos fire) {
		for (Front front : FRONTS) {
			if (front.at.distSqr(fire) <= GATHER * GATHER) {
				return front;
			}
		}
		return null;
	}

	// ---- standing down ---------------------------------------------------------------------------------------------

	/** An alarm whose fires are all out: someone who turned out for it says so, and the word is dropped. */
	private static void standDown(ServerLevel level, long now) {
		for (Iterator<Front> it = FRONTS.iterator(); it.hasNext(); ) {
			Front front = it.next();
			if (now - front.burning <= CALM_TICKS) {
				continue;
			}
			it.remove();
			LivingEntity speaker = null;
			double best = Double.MAX_VALUE;
			for (Iterator<Map.Entry<UUID, Word>> w = WORD.entrySet().iterator(); w.hasNext(); ) {
				Map.Entry<UUID, Word> e = w.next();
				if (e.getValue().front != front) {
					continue;
				}
				w.remove();
				if (level.getEntity(e.getKey()) instanceof Villager v && v.isAlive() && !v.isBaby()) {
					double d = v.distanceToSqr(Vec3.atCenterOf(front.at));
					if (d < best) {
						best = d;
						speaker = v;
					}
				}
			}
			if (speaker != null) {
				Speech.say(level, speaker, Component.translatable("talk.jaymoskingdoms.fire_alarm.out." + level.getRandom().nextInt(OUT_LINES)), Speech.GOLD, SHOUT * 2.0);
			}
		}
	}
}
