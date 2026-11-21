package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Word of a crime travels (Andy's Buder's Lanterns picks, ideas only: "that villager is the one that remembers you and is going to
 * spread the rumors about you ... All of that can be gone if you talk him out of it or pay up").
 * <ul>
 * <li><b>The runner.</b> A crime seen with no guard in earshot of the witness's cry ({@link Theft#SHOUT_RANGE}) isn't known to the
 * law yet: the witness runs for the nearest guard or golem of the realm within {@link #RUN_RANGE}, and the law hears of it
 * ({@link Theft#lawKnows}) only when they reach one (or after {@link #RUN_TICKS}, when word gets there anyway). Kill, bind or
 * hush them first and it never does. No guard that near: the town hears the cry, as before.</li>
 * <li><b>Hushing.</b> Catch the runner up ({@link #CATCH_RANGE}) and they stop to hear you out: plead (by how warm they are to you;
 * it costs trust either way), pay them off (by the crime's weight, in the realm's coin; it costs trust and respect, and breaks
 * a commandment), or stare them down (Will's Stare Down helps; it costs warmth, and failing makes them run on).</li>
 * <li><b>House to house.</b> Those who saw it, or were told by the runner, tell up to {@link #TELLS} more of the town over the
 * next day, and they tell others in turn, {@link #HOPS} deep: hearsay ({@link Recollections#rememberHeard}), which counts half
 * and makes folk wary, not hostile.</li>
 * <li><b>Down the road.</b> A heavy crime's news goes along the King's Roads ({@link Roads}) to each realm linked to the one
 * wronged, a day for every {@link #ROAD_BLOCKS_A_DAY} blocks between them (at least {@link #ROAD_MIN_TICKS}): its folk think
 * less of you and will ask about it ({@link Approaches#suspect}).</li>
 * </ul>
 * Runners, tales and road news are not saved: a restart lets them go.
 */
final class CrimeWord {
	// ---- tuning ----
	/** How far a runner looks for a guard (blocks); how long they take at most (ticks); how near counts as there (blocks). */
	static final double RUN_RANGE = 64.0, ARRIVE = 3.0;
	static final long RUN_TICKS = 1200L;
	/** How fast a runner goes (villager / folk speed). */
	static final float RUN_SPEED = 0.7F;
	static final double FOLK_RUN_SPEED = 1.2;
	/** The player catches a runner this close (blocks); they stand still to hear them out this long (ticks). */
	static final double CATCH_RANGE = 2.5;
	static final long HEAR_OUT_TICKS = 200L;
	/** Pleading: the chance at no warmth, and how much each point of warmth adds; what it costs in trust. */
	static final float PLEAD_BASE = 0.35F, PLEAD_PER_WARMTH = 0.005F;
	static final int PLEAD_TRUST = -5;
	/** Paying off: the price by weight (emeralds' worth); what it costs in trust and respect. */
	static final int PAY_LIGHT = 3, PAY_MEDIUM = 8, PAY_HEAVY = 20, PAY_TRUST = -10, PAY_RESPECT = -5;
	/** Staring down: the chance, and what each rank of Stare Down adds (only a weaker witness can be cowed); what it costs. */
	static final float THREAT_BASE = 0.4F, THREAT_PER_RANK = 0.2F;
	static final int THREAT_WARMTH = -20, THREAT_TRUST = -10, THREAT_RESPECT = 5;
	/** House to house: how many each teller tells, how far they look (blocks), how deep it goes, and when (ticks after). */
	static final int TELLS = 2, HOPS = 3;
	static final double TELL_RANGE = 24.0;
	static final int TELL_MIN = 1200, TELL_MAX = 8000;
	/** Down the road: blocks a day, the shortest wait (ticks), and the reputation it costs with each realm it reaches. */
	static final double ROAD_BLOCKS_A_DAY = 600.0;
	static final long ROAD_MIN_TICKS = 24000L;
	static final int ROAD_REP = -15;

	private static final class Runner {
		final ServerLevel level;
		final Mob who;
		final UUID player;
		final int faction;
		final String cry;
		final Theft.Weight weight;
		final long until;
		@Nullable Mob guard;
		boolean asked;
		long holdUntil;

		Runner(ServerLevel level, Mob who, Mob guard, UUID player, int faction, String cry, Theft.Weight weight, long until) {
			this.level = level;
			this.who = who;
			this.guard = guard;
			this.player = player;
			this.faction = faction;
			this.cry = cry;
			this.weight = weight;
			this.until = until;
		}
	}

	/** A teller's turn to pass on what they know of {@code player}'s crime. */
	private record Tale(ServerLevel level, UUID teller, UUID player, int faction, String cry, int hops, long at) {
	}

	/** News of a heavy crime on its way down the road to {@code faction}. */
	private record News(UUID player, int from, int faction, long at) {
	}

	private static final List<Runner> RUNNERS = new ArrayList<>();
	private static final List<Tale> TALES = new ArrayList<>();
	private static final List<News> NEWS = new ArrayList<>();

	private CrimeWord() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrimeWord::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUNNERS.clear();
			TALES.clear();
			NEWS.clear();
		});
	}

	// ---- the runner ----------------------------------------------------------------------------------------------------

	/**
	 * (From {@link Theft#commit}) {@code seen} saw {@code player} at a crime against {@code f}; {@code witness} would cry out. If no
	 * guard of {@code f} saw it or is in earshot, and one is within {@link #RUN_RANGE}, the witness runs to tell them: true (the
	 * law waits). False: the law knows now.
	 */
	static boolean run(ServerLevel level, ServerPlayer player, Faction f, String cry, Theft.Weight w, List<LivingEntity> seen, LivingEntity witness) {
		if (!(witness instanceof Mob runner) || witness instanceof IronGolem) {
			return false;
		}
		for (LivingEntity e : seen) {
			if (guard(level, f, e)) {
				return false;
			}
		}
		Mob nearest = null;
		for (Mob m : level.getEntitiesOfClass(Mob.class, witness.getBoundingBox().inflate(RUN_RANGE), m -> m.isAlive() && guard(level, f, m))) {
			if (m.distanceTo(witness) <= Theft.SHOUT_RANGE) {
				// a cry within earshot counts at once
				return false;
			}
			if (m.distanceTo(witness) <= RUN_RANGE && (nearest == null || m.distanceToSqr(witness) < nearest.distanceToSqr(witness))) {
				nearest = m;
			}
		}
		if (nearest == null) {
			return false;
		}
		RUNNERS.add(new Runner(level, runner, nearest, player.getUUID(), f.id(), cry, w, level.getGameTime() + RUN_TICKS));
		Speech.say(level, runner, Component.translatable("message.jaymoskingdoms.crime_word.off." + level.getRandom().nextInt(3)), Speech.YELLOW, Theft.SHOUT_RANGE);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crime_word.running", runner.getDisplayName()).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	/** {@code e} is one of {@code f}'s guards: a fighting folk of the realm, or a golem where its law holds. */
	private static boolean guard(ServerLevel level, Faction f, LivingEntity e) {
		if (e instanceof Folk folk) {
			return folk.factionId() == f.id() && folk.combatant();
		}
		if (e instanceof IronGolem g && !g.isPlayerCreated()) {
			Faction theirs = Theft.lawAt(level, g.blockPosition());
			return theirs != null && theirs.id() == f.id();
		}
		return false;
	}

	private static void runners(MinecraftServer server, long now) {
		for (Iterator<Runner> it = RUNNERS.iterator(); it.hasNext();) {
			Runner r = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(r.player);
			Faction f = Factions.get(server, r.faction);
			if (player == null || f == null || r.who.isRemoved() || !r.who.isAlive() || Captives.bound(r.who) || PowersCompat.outOfPlay(r.who)) {
				// stopped for good (dead, bound, down), or the one they saw has gone: the word never gets there
				it.remove();
				continue;
			}
			if (r.guard == null || !r.guard.isAlive() || r.guard.isRemoved()) {
				r.guard = null;
				for (Mob m : r.level.getEntitiesOfClass(Mob.class, r.who.getBoundingBox().inflate(RUN_RANGE), m -> m.isAlive() && guard(r.level, f, m))) {
					if (r.guard == null || m.distanceToSqr(r.who) < r.guard.distanceToSqr(r.who)) {
						r.guard = m;
					}
				}
			}
			boolean there = r.guard != null && r.who.distanceTo(r.guard) <= ARRIVE;
			if (there || now >= r.until) {
				it.remove();
				LivingEntity crier = r.guard != null ? r.guard : r.who;
				if (r.guard != null) {
					Recollections.rememberHeard(r.guard, player, Recollections.Kind.CRIME, Component.translatable("recall.jaymoskingdoms.crime." + r.cry),
						r.who.getName().getString());
				}
				spread(r.level, player, f, r.cry, r.guard != null ? List.of(r.who, r.guard) : List.of(r.who));
				Theft.lawKnows(player, r.level, f, r.cry, r.weight, crier);
				continue;
			}
			if (player.level() == r.level && player.distanceTo(r.who) <= CATCH_RANGE && !r.asked) {
				// caught up with: they stop to hear you out
				r.asked = true;
				r.holdUntil = now + HEAR_OUT_TICKS;
				hush(player, r);
			}
			if (now < r.holdUntil) {
				r.who.getNavigation().stop();
				if (r.who instanceof Villager v) {
					v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
				}
				r.who.getLookControl().setLookAt(player);
			} else if (r.guard != null) {
				if (r.who instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(r.guard, true), RUN_SPEED, 1);
				} else {
					r.who.getNavigation().moveTo(r.guard, FOLK_RUN_SPEED);
				}
			}
		}
	}

	// ---- hushing -------------------------------------------------------------------------------------------------------

	private static void hush(ServerPlayer player, Runner r) {
		MinecraftServer server = player.level().getServer();
		Faction f = Factions.get(server, r.faction);
		int price = r.weight == Theft.Weight.HEAVY ? PAY_HEAVY : r.weight == Theft.Weight.MEDIUM ? PAY_MEDIUM : PAY_LIGHT;
		String k = "talk.jaymoskingdoms.crime_word.";
		Menus.ask(player, r.who, Component.translatable(k + "caught"), List.of(
			Menus.Choice.of(Component.translatable(k + "plead"), p -> plead(p, r)),
			Menus.Choice.of(Component.translatable(k + "pay", Currencies.asked(server, f, price)), Currencies.canPay(server, player, f, price), p -> pay(p, r, f, price)),
			Menus.Choice.of(Component.translatable(k + "threaten"), p -> threaten(p, r)),
			Menus.Choice.of(Component.translatable(k + "let_go"), p -> r.holdUntil = 0L)));
	}

	private static void plead(ServerPlayer player, Runner r) {
		float chance = PLEAD_BASE + Bearings.warmth(r.who, player) * PLEAD_PER_WARMTH;
		Bearings.move(r.who, player, PLEAD_TRUST, 0, 0);
		hushed(player, r, "plead", r.level.getRandom().nextFloat() < chance);
	}

	private static void pay(ServerPlayer player, Runner r, @Nullable Faction f, int price) {
		Commandments.act(player, "bribe");
		Currencies.fine(player.level().getServer(), player, f, price);
		Bearings.move(r.who, player, PAY_TRUST, PAY_RESPECT, 0);
		hushed(player, r, "pay", true);
	}

	private static void threaten(ServerPlayer player, Runner r) {
		float chance = Will.weaker(player, r.who) ? THREAT_BASE + THREAT_PER_RANK * Will.rank(player, Skills.Perk.STARE_DOWN) : 0.0F;
		boolean cowed = r.level.getRandom().nextFloat() < chance;
		Bearings.move(r.who, player, THREAT_TRUST, cowed ? THREAT_RESPECT : 0, THREAT_WARMTH);
		hushed(player, r, "threaten", cowed);
	}

	/** How the talk went: hushed, the runner goes home quiet; not, they run on at once. */
	private static void hushed(ServerPlayer player, Runner r, String how, boolean kept) {
		Speech.say(r.level, r.who, Component.translatable("talk.jaymoskingdoms.crime_word." + how + (kept ? ".yes" : ".no")), kept ? Speech.WHITE : Speech.YELLOW,
			Theft.SHOUT_RANGE);
		if (kept) {
			RUNNERS.remove(r);
		} else {
			r.holdUntil = 0L;
		}
	}

	// ---- house to house ------------------------------------------------------------------------------------------------

	/** {@code tellers} know of {@code player}'s crime: over the next day each tells a few more of {@code f}'s people. */
	static void spread(ServerLevel level, ServerPlayer player, Faction f, String cry, List<LivingEntity> tellers) {
		for (LivingEntity t : tellers) {
			if (Recollections.rememberer(t)) {
				TALES.add(new Tale(level, t.getUUID(), player.getUUID(), f.id(), cry, 0, level.getGameTime() + later(level)));
			}
		}
	}

	private static long later(ServerLevel level) {
		return TELL_MIN + level.getRandom().nextInt(TELL_MAX - TELL_MIN);
	}

	private static void tales(MinecraftServer server, long now) {
		List<Tale> next = new ArrayList<>();
		for (Iterator<Tale> it = TALES.iterator(); it.hasNext();) {
			Tale t = it.next();
			if (t.level().getGameTime() < t.at()) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(t.player());
			if (player == null || !(t.level().getEntity(t.teller()) instanceof LivingEntity teller) || !teller.isAlive()) {
				continue;
			}
			int told = 0;
			for (LivingEntity e : t.level().getEntitiesOfClass(LivingEntity.class, teller.getBoundingBox().inflate(TELL_RANGE),
				x -> x != teller && x.isAlive() && !x.isSleeping() && Recollections.rememberer(x) && ours(t.level(), t.faction(), x))) {
				if (told >= TELLS || knows(e, player)) {
					continue;
				}
				Recollections.rememberHeard(e, player, Recollections.Kind.CRIME, Component.translatable("recall.jaymoskingdoms.crime." + t.cry()),
					teller.getName().getString());
				told++;
				if (t.hops() + 1 < HOPS) {
					next.add(new Tale(t.level(), e.getUUID(), t.player(), t.faction(), t.cry(), t.hops() + 1, t.level().getGameTime() + later(t.level())));
				}
			}
		}
		TALES.addAll(next);
	}

	/** {@code e} is one of the faction's people: its folk, or a villager where its law holds. */
	private static boolean ours(ServerLevel level, int faction, LivingEntity e) {
		if (e instanceof Folk folk) {
			return folk.factionId() == faction;
		}
		Faction law = Theft.lawAt(level, e.blockPosition());
		return law != null && law.id() == faction;
	}

	/** {@code who} already knows of a crime of {@code player}'s (saw it or heard). */
	private static boolean knows(LivingEntity who, ServerPlayer player) {
		for (Recollections.Recollection r : Recollections.of(who, player)) {
			if (r.kind().equals(Recollections.Kind.CRIME.key())) {
				return true;
			}
		}
		return false;
	}

	// ---- down the road -------------------------------------------------------------------------------------------------

	/** (From {@link Theft#lawKnows}) a heavy crime against {@code f}: the news sets off down each of its roads. */
	static void road(ServerLevel level, ServerPlayer player, Faction f, String cry) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		for (Roads.Road road : Roads.network(server).roads()) {
			int other = road.a() == f.id() ? road.b() : road.b() == f.id() ? road.a() : -1;
			Faction to = other < 0 ? null : Factions.get(server, other);
			if (to == null) {
				continue;
			}
			double blocks = Math.sqrt(f.seat().distSqr(to.seat()));
			long wait = Math.max(ROAD_MIN_TICKS, Math.round(blocks / ROAD_BLOCKS_A_DAY * 24000.0));
			NEWS.add(new News(player.getUUID(), f.id(), to.id(), now + wait));
		}
	}

	private static void news(MinecraftServer server, long now) {
		for (Iterator<News> it = NEWS.iterator(); it.hasNext();) {
			News n = it.next();
			if (now < n.at()) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(n.player());
			if (player == null) {
				// (it waits for them: the road keeps its news)
				continue;
			}
			it.remove();
			Faction from = Factions.get(server, n.from()), to = Factions.get(server, n.faction());
			if (from == null || to == null || Wars.enemy(player, to)) {
				continue;
			}
			Reputation.change(player, to, ROAD_REP);
			Approaches.suspect(player, to.id(), now);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crime_word.road", from.name(), to.name()).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!RUNNERS.isEmpty() && now % 5 == 0) {
			runners(server, now);
		}
		if (now % 20 == 0) {
			if (!TALES.isEmpty()) {
				tales(server, now);
			}
			if (!NEWS.isEmpty()) {
				news(server, now);
			}
		}
	}

	/** {@code mob} is running to tell on someone (other work leaves them be). */
	static boolean running(Mob mob) {
		for (Runner r : RUNNERS) {
			if (r.who == mob) {
				return true;
			}
		}
		return false;
	}
}
