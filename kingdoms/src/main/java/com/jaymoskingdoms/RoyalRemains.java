package com.jaymoskingdoms;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.546) Andy (2026-11-05), of a dead king left lying in his diamonds outside his own walls for a passing player to
 * strip: "the royals should never just be left dead like this, unless there was noone at all left in the kingdom to
 * defend, SOMEONE should find the body and get it to safety, remove the armor, and bury or mourn them if not bring them
 * to the cleric".
 * <p>
 * A realm's fallen ruler (its king or queen, a fiefdom's lord or lady) isn't left for the quiet the way {@link Bearers}
 * wait for it with the common dead. As soon as no foe stands right over the body ({@link #DANGER}), one of the realm's
 * own goes for it: a guard if one is free, else any grown townsperson, from anywhere round the seat
 * ({@link #SEARCH}). They take the body up and carry it to safety: to the town's cleric if it has one, else to the seat.
 * There they lay it down and take off its armour and whatever it carried (into the realm's stores, {@link Storage}; what
 * won't fit is left at the seat). A cleric then says the rites over it ({@link #RITE_TICKS}), and the bearer carries it
 * to the graveyard, where it's buried as {@link Bearers} bury their own, under a headstone with a candle lit on it, and
 * mourned by its family, a few of its guards standing, and the townsfolk ({@link Bearers#ROYAL_MOURNERS}). With no
 * ground for a grave it lies in state at the seat, and is mourned there.
 * <p>
 * Only when nobody of the realm is left near enough to come (a realm in ruin whose people scattered) does the body lie
 * where it fell.
 */
public final class RoyalRemains {
	/** How often bodies are looked for (ticks), and duties walked. */
	static final int ASSIGN = 40, STEP = 5;
	/** A ruler's body this close to their seat is seen to; anyone of theirs this close to the body may come for it. */
	static final double SWEEP = 112.0, SEARCH = 96.0;
	/**
	 * A foe (or anyone fighting) this close to the body keeps townsfolk off it, but not a guard: Andy, "guards will
	 * prioritize the body, and flee the fight with it over shoulder". The wait after death before anyone comes (ticks).
	 */
	static final double DANGER = 8.0;
	static final long WAIT = 20L;
	/** A guard's run with the body (navigation speed; a villager guard's brain speed). */
	static final double FLEE_SPEED = 1.25;
	static final float VILLAGER_FLEE_SPEED = 0.85F;
	/** Close enough to take the body up; to the cleric or the seat to lay it down. */
	static final double REACH = 2.2, SAFE_REACH = 3.0;
	/** A cleric this close to the seat (or in the realm's land) is where the body is taken. */
	static final double CLERIC_REACH = 96.0;
	/** How long the cleric's rites take (ticks), a prayer line this often. */
	static final int RITE_TICKS = 120, RITE_LINES = 4, STRIP_LINES = 3, CALL_LINES = 3;
	/** How long a duty may take (ticks) before it's given up; a body given up on is tried again after this. */
	static final long GIVE_UP = 3600L, RETRY = 200L;
	static final double SAY_RANGE = 20.0;

	enum Stage { FETCH, HAUL, STRIP, RITE, INTER }

	private static final class Duty {
		final UUID bearer, corpse;
		final int realm;
		final Bearers.Dead dead;
		final long until;
		@Nullable UUID cleric;
		Stage stage = Stage.FETCH;
		int rite;

		Duty(UUID bearer, UUID corpse, int realm, Bearers.Dead dead, @Nullable UUID cleric, long until) {
			this.bearer = bearer;
			this.corpse = corpse;
			this.realm = realm;
			this.dead = dead;
			this.cleric = cleric;
			this.until = until;
		}
	}

	/** Bearer's UUID → its duty. */
	private static final Map<UUID, Duty> DUTIES = new HashMap<>();
	/** Bodies nobody could come for (or given up on), and when they may be tried again. */
	private static final Map<UUID, Long> LEFT = new HashMap<>();
	/** Rulers who died this session, and whose they were (a body's own saved role may already be cleared). */
	private static final Map<UUID, Integer> FELL = new HashMap<>();

	private RoyalRemains() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RoyalRemains::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DUTIES.clear();
			LEFT.clear();
			FELL.clear();
		});
	}

	/** {@code ruler}, {@code faction}'s ruler, has died ({@link KingdomEvents}). */
	static void fell(UUID ruler, int faction) {
		FELL.put(ruler, faction);
	}

	/** The realm whose ruler {@code dead} was, or -1 if they were nobody's ruler. */
	static int ruled(Bearers.Dead dead) {
		if (!dead.person()) {
			return -1;
		}
		if (dead.id().isPresent()) {
			Integer f = FELL.get(dead.id().get());
			if (f != null) {
				return f;
			}
		}
		return Rulers.leaderRole(dead.role()) ? dead.realm() : -1;
	}

	/** {@code corpse} is being seen to here. */
	static boolean claimed(UUID corpse) {
		for (Duty d : DUTIES.values()) {
			if (d.corpse.equals(corpse)) {
				return true;
			}
		}
		return false;
	}

	/** {@code mob} is bearing a ruler's body (or the cleric saying the rites over it). */
	static boolean busy(Entity mob) {
		UUID id = mob.getUUID();
		if (DUTIES.containsKey(id)) {
			return true;
		}
		for (Duty d : DUTIES.values()) {
			if (id.equals(d.cleric) && d.stage == Stage.RITE) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (!KitchenCompat.burials()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % STEP == 0 && !DUTIES.isEmpty()) {
			for (Iterator<Duty> it = DUTIES.values().iterator(); it.hasNext(); ) {
				Duty d = it.next();
				if (!step(level, d, now)) {
					it.remove();
				}
			}
		}
		if (now % ASSIGN == 0) {
			LEFT.values().removeIf(until -> until < now);
			for (Faction f : Factions.all(server)) {
				// (a realm in ruin too: its townsfolk may still be about)
				if (f.type().isRealm() && level.isLoaded(f.seat())) {
					assign(level, f, now);
				}
			}
		}
	}

	// ---- finding the body ---------------------------------------------------------------------------------------------

	private static void assign(ServerLevel level, Faction f, long now) {
		AABB area = new AABB(f.seat()).inflate(SWEEP, 32.0, SWEEP);
		for (Entity corpse : level.getEntities((Entity)null, area, KitchenCompat::isFreeCorpse)) {
			UUID id = corpse.getUUID();
			if (KitchenCompat.isPlayerCorpse(corpse) || LEFT.containsKey(id) || claimed(id) || Bearers.claimed(id) || Looters.claimed(id)) {
				continue;
			}
			Bearers.Dead dead = Bearers.who(level, corpse);
			if (ruled(dead) != f.id()) {
				continue;
			}
			Faction law = Theft.lawAt(level, corpse.blockPosition());
			if (law != null && law.id() != f.id() || KitchenCompat.sinceDeath(corpse) < WAIT) {
				continue;
			}
			boolean danger = danger(level, corpse.blockPosition());
			PathfinderMob bearer = bearer(level, f, corpse, danger);
			if (bearer == null && danger) {
				// no guard to brave it: the townsfolk wait for the fighting to move off
				continue;
			}
			if (bearer == null) {
				// nobody of theirs left near enough to come
				LEFT.put(id, now + RETRY);
				continue;
			}
			Villager cleric = cleric(level, f, bearer);
			DUTIES.put(bearer.getUUID(), new Duty(bearer.getUUID(), id, f.id(), dead, cleric == null ? null : cleric.getUUID(), now + GIVE_UP));
			Speech.say(level, bearer, Component.translatable("message.jaymoskingdoms.royal_remains.call." + level.getRandom().nextInt(CALL_LINES),
				dead.name()), Speech.GOLD, SAY_RANGE);
			JaymosKingdoms.LOGGER.debug("{} of {} goes for {}'s body at {}", bearer.getName().getString(), f.name(), dead.name().getString(),
				corpse.blockPosition());
		}
	}

	/** A foe, or anyone fighting, right over the body (they'd only be cut down beside it). */
	private static boolean danger(ServerLevel level, BlockPos at) {
		AABB box = new AABB(at).inflate(DANGER, 4.0, DANGER);
		return !level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && (m instanceof Enemy || m.getTarget() != null && m.getTarget().isAlive())).isEmpty();
	}

	/**
	 * One of {@code f}'s own to go for the body: the nearest guard (who drops whatever fight they're in for it), else
	 * (with no fighting over the body) the nearest free grown townsperson.
	 */
	private static @Nullable PathfinderMob bearer(ServerLevel level, Faction f, Entity corpse, boolean danger) {
		AABB box = corpse.getBoundingBox().inflate(SEARCH, 16.0, SEARCH);
		PathfinderMob guard = null, other = null;
		double guardD = Double.MAX_VALUE, otherD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
			boolean sworn = v instanceof Worker w && w.factionId() == f.id();
			boolean guarding = sworn && Rulers.role(v) == Folk.GUARD;
			if (Rulers.is(v) || Downed.isDowned(v) || (guarding ? Bearers.busy(v) || Undertakers.busy(v) || Looters.busy(v) : !Bearers.freeVillager(v))) {
				continue;
			}
			Faction theirs = Theft.lawAt(level, v.blockPosition());
			if (!sworn && (theirs != null ? theirs.id() != f.id() : !f.fallen())) {
				// not one of theirs (in a realm in ruin, anyone living about it is)
				continue;
			}
			double d = v.distanceToSqr(corpse);
			if (guarding) {
				if (d < guardD) {
					guardD = d;
					guard = v;
				}
			} else if (d < otherD) {
				otherD = d;
				other = v;
			}
		}
		for (Folk folk : level.getEntitiesOfClass(Folk.class, box, x -> x.isAlive() && x.factionId() == f.id() && !x.isSleeping() && !x.isBaby())) {
			int role = folk.role();
			if (folk.leader() || Downed.isDowned(folk) || !folk.combatant() && folk.getTarget() != null || Bearers.busy(folk) || Undertakers.busy(folk) || busy(folk)
				|| role == Folk.CARTER || role == Folk.PULLER || role == Folk.STABLEHAND || role == Folk.FARRIER) {
				continue;
			}
			double d = folk.distanceToSqr(corpse);
			if (folk.combatant()) {
				if (d < guardD) {
					guardD = d;
					guard = folk;
				}
			} else if (d < otherD) {
				otherD = d;
				other = folk;
			}
		}
		return guard != null ? guard : danger ? null : other;
	}

	/** {@code d}'s bearer is one of the realm's fighters (who doesn't stop for a fight while they bear the body). */
	private static boolean guard(PathfinderMob bearer) {
		return bearer instanceof Folk folk ? folk.combatant() : Rulers.role(bearer) == Folk.GUARD;
	}

	/** A guard drops the fight they're in, for the body. */
	private static void disengage(PathfinderMob guard) {
		guard.setTarget(null);
		if (guard instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	/** Walks {@code mob} to {@code to}: a guard runs. */
	private static void go(PathfinderMob mob, BlockPos to) {
		if (!guard(mob)) {
			Undertakers.walk(mob, to);
		} else if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, VILLAGER_FLEE_SPEED, 1);
		} else {
			mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, FLEE_SPEED);
		}
	}

	/** The cleric nearest {@code near} who serves {@code f}'s town (in its land, or close by its seat), or null. */
	private static @Nullable Villager cleric(ServerLevel level, Faction f, Entity near) {
		AABB box = new AABB(f.seat()).inflate(CLERIC_REACH, 24.0, CLERIC_REACH);
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && Restoration.cleric(v) && !v.isSleeping())) {
			if (Downed.isDowned(v) || v.getTarget() != null || McaCompat.panicking(v)) {
				continue;
			}
			Faction theirs = Theft.lawAt(level, v.blockPosition());
			if (theirs != null && theirs.id() != f.id()) {
				continue;
			}
			double d = v.distanceToSqr(near);
			if (d < bestD) {
				bestD = d;
				best = v;
			}
		}
		return best;
	}

	// ---- bearing it home --------------------------------------------------------------------------------------------

	/** Walks one duty on; false when it's done (or given up, the body put down where the bearer stands). */
	private static boolean step(ServerLevel level, Duty d, long now) {
		Entity corpse = level.getEntity(d.corpse);
		Entity found = level.getEntity(d.bearer);
		if (!(found instanceof PathfinderMob bearer) || !bearer.isAlive() || bearer.isSleeping() || corpse == null || corpse.isRemoved()) {
			if (corpse != null && found != null && KitchenCompat.carrier(corpse) == found) {
				KitchenCompat.throwAt(corpse, found.position());
			}
			return false;
		}
		boolean holding = KitchenCompat.carrier(corpse) == bearer;
		boolean guard = guard(bearer);
		if (guard && !Downed.isDowned(bearer) && now <= d.until && d.stage.compareTo(Stage.STRIP) <= 0) {
			// (a guard fetching the body or running it home doesn't stop to fight)
			disengage(bearer);
		} else if (bearer.getTarget() != null || Downed.isDowned(bearer) || now > d.until || bearer instanceof Villager v && McaCompat.panicking(v)) {
			// set upon (or it's taking too long): put down, and fetched again once it's safe
			if (holding) {
				KitchenCompat.throwAt(corpse, bearer.position());
			}
			LEFT.put(d.corpse, now + RETRY);
			return false;
		}
		Faction f = Factions.get(level.getServer(), d.realm);
		if (f == null) {
			if (holding) {
				KitchenCompat.throwAt(corpse, bearer.position());
			}
			return false;
		}
		switch (d.stage) {
			case FETCH -> {
				if (KitchenCompat.carrier(corpse) != null || corpse.isPassenger()) {
					// someone else has it (a player bringing it in, a looter making off with it): left to them
					return false;
				}
				if (bearer.distanceToSqr(corpse) > REACH * REACH) {
					go(bearer, corpse.blockPosition());
					return true;
				}
				if (KitchenCompat.carry(corpse, bearer)) {
					d.stage = Stage.HAUL;
				}
			}
			case HAUL -> {
				if (!holding) {
					d.stage = Stage.FETCH;
					return true;
				}
				Villager cleric = cleric(level, d);
				BlockPos safe = cleric != null ? cleric.blockPosition() : f.seat();
				if (Bearers.horizontalDistSqr(bearer, safe) > SAFE_REACH * SAFE_REACH) {
					go(bearer, safe);
					if (cleric != null && bearer.distanceToSqr(cleric) < 24.0 * 24.0) {
						// the cleric comes to meet them
						cleric.getNavigation().stop();
						cleric.getLookControl().setLookAt(bearer);
					}
					return true;
				}
				bearer.getNavigation().stop();
				KitchenCompat.throwAt(corpse, bearer.position().add(bearer.getLookAngle().multiply(1.0, 0.0, 1.0)));
				d.stage = Stage.STRIP;
			}
			case STRIP -> {
				if (!corpse.onGround()) {
					return true;
				}
				bearer.getLookControl().setLookAt(corpse);
				bearer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				store(level, f, KitchenCompat.robBy(corpse, bearer), bearer.position());
				Speech.say(level, bearer, Component.translatable("message.jaymoskingdoms.royal_remains.strip." + level.getRandom().nextInt(STRIP_LINES),
					d.dead.name()), Speech.WHITE, SAY_RANGE);
				if (cleric(level, d) != null) {
					d.stage = Stage.RITE;
					level.playSound(null, corpse.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.2F, 0.7F);
				} else {
					d.stage = Stage.INTER;
				}
			}
			case RITE -> {
				Villager cleric = cleric(level, d);
				if (cleric == null) {
					d.stage = Stage.INTER;
					return true;
				}
				bearer.getNavigation().stop();
				bearer.getLookControl().setLookAt(corpse);
				if (cleric.distanceToSqr(corpse) > SAFE_REACH * SAFE_REACH) {
					Undertakers.walk(cleric, corpse.blockPosition());
					return true;
				}
				cleric.getNavigation().stop();
				cleric.getLookControl().setLookAt(corpse);
				if (d.rite % (RITE_TICKS / 2) == 0) {
					Speech.say(level, cleric, Component.translatable("message.jaymoskingdoms.royal_remains.rite." + level.getRandom().nextInt(RITE_LINES),
						d.dead.name()), Speech.GOLD, SAY_RANGE);
				}
				Vec3 at = corpse.position();
				level.sendParticles(Fx.LIGHT, at.x, at.y + 0.4, at.z, 2, 0.4, 0.2, 0.4, 0.01);
				d.rite += STEP;
				if (d.rite >= RITE_TICKS) {
					d.stage = Stage.INTER;
				}
			}
			case INTER -> {
				if (!holding) {
					if (bearer.distanceToSqr(corpse) > REACH * REACH) {
						go(bearer, corpse.blockPosition());
						return true;
					}
					if (!KitchenCompat.carry(corpse, bearer)) {
						return true;
					}
				}
				// to the graveyard with them, as their own are buried; with no ground for a grave, they lie in state here
				if (!Bearers.entrustRoyal(level, f, bearer, corpse, d.dead, now)) {
					KitchenCompat.throwAt(corpse, bearer.position());
					Bearers.lieInState(level, f, d.dead, corpse.blockPosition(), now);
					LEFT.put(d.corpse, now + GIVE_UP * 4);
				}
				return false;
			}
		}
		return true;
	}

	private static @Nullable Villager cleric(ServerLevel level, Duty d) {
		return d.cleric != null && level.getEntity(d.cleric) instanceof Villager v && v.isAlive() && !v.isSleeping() && !Downed.isDowned(v)
			&& v.getTarget() == null ? v : null;
	}

	/** What was taken off the body goes into the realm's stores; what won't fit is left at its seat. */
	private static void store(ServerLevel level, Faction f, List<ItemStack> taken, Vec3 fallback) {
		BlockPos seat = f.seat();
		Vec3 drop = level.isLoaded(seat) ? Vec3.atCenterOf(seat.above()) : fallback;
		for (ItemStack stack : taken) {
			ItemStack left = f.fallen() ? stack : Storage.put(level, f, stack);
			if (!left.isEmpty()) {
				ItemEntity item = new ItemEntity(level, drop.x, drop.y, drop.z, left);
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		}
	}
}
