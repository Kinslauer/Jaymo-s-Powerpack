package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) Andy: "you can also request an audience from a guard and they will escort you to the king/queen as long as
 * your rep isnt too low." A player asks one of a realm's guards ("Seek an audience" in an MCA guard's menu, or the line a
 * folk guard's audience offers, {@code /kingdom audience}). If their good name with the realm is at least
 * {@link #MIN_REPUTATION}, they're not thrown out of the city ({@link Retinue}) and the ruler is about and awake, the
 * guard walks them to the ruler, waiting when they fall behind, and announces them. While they're led, and for
 * {@link #GRACE_TICKS} after, the ruler's guards let them be, whatever the ruler's temper ({@link #passing}).
 */
public final class Audiences {
	/** The least good name with the realm a guard will take you in on. */
	static final int MIN_REPUTATION = -20;
	/** How far from you the guard you ask may stand, and how far the ruler may be from him. */
	static final double ASK_RANGE = 6.0, RULER_RANGE = 160.0;
	/** The guard waits once you're this far behind, and gives up once you're this far; you're there this close to the ruler. */
	static final double WAIT_RANGE = 6.0, LOSE_RANGE = 40.0, ARRIVE_RANGE = 4.0;
	/** How fast he walks you over. */
	static final double LEAD_SPEED = 0.6;
	/** The longest a walk may take, how often he tells you to keep up, and how long the ruler's guards let you be after (ticks). */
	static final int LEAD_TICKS = 20 * 180, HURRY_TICKS = 20 * 6, GRACE_TICKS = 20 * 90;
	/** (0.7.198) He waits this long for you to close his menu and come to him before he sets off (ticks). */
	static final int START_TICKS = 30;
	/** (0.7.198) He's walking but has got no closer to the ruler for this long (ticks): he points you the rest of the way. */
	static final int STUCK_TICKS = 20 * 12;
	/** How far his words carry. */
	static final double SPEECH_RANGE = 16.0;

	private static final class Lead {
		final UUID player;
		final int faction;
		final long until, started;
		long hurried = Long.MIN_VALUE / 2;
		/** (0.7.198) The nearest he's been to the ruler, and when he last got nearer. */
		double nearest = Double.MAX_VALUE;
		long nearer;

		Lead(UUID player, int faction, long started, long until) {
			this.player = player;
			this.faction = faction;
			this.started = started;
			this.nearer = started;
			this.until = until;
		}
	}

	/** Guard → whom he's taking to the ruler. */
	private static final Map<UUID, Lead> LEADS = new HashMap<>();
	/** "faction/player" → until when (game time) the realm's guards let them be. */
	private static final Map<String, Long> PASSES = new HashMap<>();

	private Audiences() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LEADS.clear();
			PASSES.clear();
		});
	}

	private static String key(int faction, UUID player) {
		return faction + "/" + player;
	}

	/** {@code mob} is one of a realm's guards whose ruler holds court: someone to ask for an audience. */
	public static boolean canAsk(@Nullable Entity mob) {
		if (!(mob instanceof Worker w) || w.role() != Folk.GUARD || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction f = Factions.get(level.getServer(), w.factionId());
		return f != null && !f.fallen() && f.type().isRealm() && f.leader().isPresent();
	}

	/** {@code player} is being led to {@code faction}'s ruler, or was just now. */
	static boolean passing(int faction, UUID player, long now) {
		Long until = PASSES.get(key(faction, player));
		return until != null && until > now;
	}

	/** {@code guard} is walking someone to his ruler. */
	static boolean leading(Entity guard) {
		return LEADS.containsKey(guard.getUUID());
	}

	/** (0.7.198) Whom {@code guard} is walking to the ruler (they follow him through the doors he opens, {@link Doorways}). */
	static @Nullable ServerPlayer follower(ServerLevel level, Entity guard) {
		Lead lead = LEADS.get(guard.getUUID());
		return lead == null ? null : level.getServer().getPlayerList().getPlayer(lead.player);
	}

	/** {@code /kingdom audience}: the nearest of a realm's guards to {@code player} is asked. */
	static boolean ask(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		PathfinderMob best = null;
		for (Worker w : Workers.near(level, player.getBoundingBox().inflate(ASK_RANGE), w -> canAsk(w.mob()))) {
			if (best == null || w.mob().distanceToSqr(player) < best.distanceToSqr(player)) {
				best = w.mob();
			}
		}
		if (best == null) {
			return false;
		}
		request(player, best);
		return true;
	}

	/** {@code player} asks {@code guard} to take them to his ruler. */
	public static void request(ServerPlayer player, PathfinderMob guard) {
		if (!canAsk(guard) || !(guard.level() instanceof ServerLevel level) || !(guard instanceof Worker w)) {
			return;
		}
		Faction f = Factions.get(level.getServer(), w.factionId());
		if (f == null) {
			return;
		}
		long now = level.getGameTime();
		guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Lead lead = LEADS.get(guard.getUUID());
		if (lead != null && lead.player.equals(player.getUUID())) {
			say(level, guard, "already", f, null);
			return;
		}
		if (lead != null || Arrests.busy(guard) || Rulers.target(guard) != null || Retinue.sent(guard)) {
			say(level, guard, "busy", f, null);
			return;
		}
		if (Retinue.banned(f, player, now)) {
			say(level, guard, "banned", f, null);
			return;
		}
		if (Reputation.get(player, f.id()) < MIN_REPUTATION) {
			say(level, guard, "refused", f, null);
			level.playSound(null, guard.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
			return;
		}
		PathfinderMob ruler = Rulers.of(level, f);
		if (ruler == null || ruler.distanceTo(guard) > RULER_RANGE) {
			say(level, guard, "away", f, null);
			return;
		}
		if (ruler.isSleeping()) {
			say(level, guard, "asleep", f, ruler);
			return;
		}
		if (ruler.distanceTo(player) <= ARRIVE_RANGE) {
			// (already before them)
			say(level, guard, "here", f, ruler);
			return;
		}
		LEADS.put(guard.getUUID(), new Lead(player.getUUID(), f.id(), now, now + LEAD_TICKS));
		PASSES.put(key(f.id(), player.getUUID()), now + LEAD_TICKS + GRACE_TICKS);
		guard.getNavigation().stop();
		say(level, guard, "follow", f, ruler);
		level.playSound(null, guard.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/**
	 * {@code guard}'s step while he's walking someone to the ruler (a folk guard's {@link Retinue.Harass} goal, an MCA
	 * guard's {@link Rulers#guardStep}): true while that's what he's doing, false once it's over.
	 */
	static boolean lead(PathfinderMob guard) {
		Lead lead = LEADS.get(guard.getUUID());
		if (lead == null || !(guard.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		Faction f = Factions.get(level.getServer(), lead.faction);
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(lead.player);
		PathfinderMob ruler = f == null ? null : Rulers.of(level, f);
		if (f == null || player == null || player.level() != level || !player.isAlive() || !guard.isAlive() || Rulers.target(guard) != null
			|| Arrests.busy(guard)) {
			end(guard, lead, now);
			return false;
		}
		if (ruler == null || ruler.isSleeping()) {
			say(level, guard, "away", f, null);
			end(guard, lead, now);
			return false;
		}
		if (now > lead.until || player.distanceTo(guard) > LOSE_RANGE) {
			say(level, guard, "lost", f, ruler);
			end(guard, lead, now);
			return false;
		}
		if (player.distanceTo(ruler) <= ARRIVE_RANGE && guard.distanceTo(ruler) <= ARRIVE_RANGE * 2) {
			arrive(level, f, guard, ruler, player);
			end(guard, lead, now);
			return true;
		}
		if (now - lead.started < START_TICKS) {
			// (0.7.198) a moment for them to put his menu away before he sets off (he used to be gone before they'd turned)
			guard.getNavigation().stop();
			guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
			lead.nearer = now;
			return true;
		}
		if (player.distanceTo(guard) > WAIT_RANGE) {
			// they've fallen behind: he waits for them
			guard.getNavigation().stop();
			guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
			if (now - lead.hurried >= HURRY_TICKS) {
				lead.hurried = now;
				say(level, guard, "keep_up", f, ruler);
			}
			// (waiting for them isn't being stuck)
			lead.nearer = now;
			return true;
		}
		double d = guard.distanceTo(ruler);
		if (d < lead.nearest - 1.0) {
			lead.nearest = d;
			lead.nearer = now;
		} else if (d > ARRIVE_RANGE * 2 && now - lead.nearer > STUCK_TICKS) {
			// (0.7.198) no way through for him (a wall, a ladder, a locked door): he points them the rest of the way, and
			// the ruler's guards still let them be ({@link #GRACE_TICKS})
			guard.getNavigation().stop();
			guard.getLookControl().setLookAt(ruler, 30.0F, 30.0F);
			say(level, guard, "point", f, ruler);
			end(guard, lead, now);
			return false;
		}
		if (d > ARRIVE_RANGE - 1.0) {
			if (guard.getNavigation().isDone() || guard.tickCount % 10 == 0) {
				guard.getNavigation().moveTo(ruler, LEAD_SPEED);
			}
		} else {
			// there: he waits at the ruler's side for them to come up
			guard.getNavigation().stop();
			guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
		return true;
	}

	/** At the ruler's side: the guard announces them, and the ruler turns to them. */
	private static void arrive(ServerLevel level, Faction f, PathfinderMob guard, PathfinderMob ruler, ServerPlayer player) {
		guard.getNavigation().stop();
		guard.getLookControl().setLookAt(ruler, 30.0F, 30.0F);
		ruler.getNavigation().stop();
		ruler.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.say(level, guard, Component.translatable("speech.jaymoskingdoms.audience.announce", Rulers.title(Rulers.role(ruler), Rulers.queen(f)),
			Identities.shownName(player)), Speech.GOLD, SPEECH_RANGE);
		Tempers.Temper t = Tempers.of(f);
		Speech.say(level, ruler, Component.translatable("speech.jaymoskingdoms.audience.receive." + (t == null ? "plain" : t.key()), Identities.shownName(player)),
			t == Tempers.Temper.TYRANT || t == Tempers.Temper.PARANOID ? Speech.RED : Speech.GOLD, SPEECH_RANGE);
		level.playSound(null, ruler.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 0.7F);
		// (0.7.254) something to thank him for ({@link SharedHistory})
		Recollections.remember(guard, player, Recollections.Kind.LED_YOU, Component.empty());
	}

	/** The walk's over: the ruler's guards let them be a while longer, and he goes back to his post. */
	private static void end(PathfinderMob guard, Lead lead, long now) {
		LEADS.remove(guard.getUUID());
		PASSES.put(key(lead.faction, lead.player), now + GRACE_TICKS);
		guard.getNavigation().stop();
		for (Iterator<Map.Entry<String, Long>> it = PASSES.entrySet().iterator(); it.hasNext();) {
			if (it.next().getValue() <= now) {
				it.remove();
			}
		}
	}

	private static void say(ServerLevel level, PathfinderMob guard, String what, Faction f, @Nullable PathfinderMob ruler) {
		Component title = Rulers.title(ruler != null ? Rulers.role(ruler) : f.type() == FactionType.KINGDOM ? Folk.KING : Folk.LORD, Rulers.queen(f));
		boolean good = List.of("follow", "here", "keep_up", "already", "point").contains(what);
		Speech.say(level, guard, Component.translatable("speech.jaymoskingdoms.audience." + what, title), good ? Speech.WHITE : Speech.YELLOW, SPEECH_RANGE);
	}

	/** A guard ({@code guard}) in a quest carrier's way: he steps aside for them, off the line from them to {@code to}. */
	static void stepAside(PathfinderMob guard, Entity carrier, Vec3 to) {
		Vec3 along = to.subtract(carrier.position()).multiply(1.0, 0.0, 1.0);
		if (along.lengthSqr() < 1.0E-4) {
			return;
		}
		along = along.normalize();
		Vec3 off = guard.position().subtract(carrier.position()).multiply(1.0, 0.0, 1.0);
		// to whichever side he's already a little on
		Vec3 side = new Vec3(-along.z, 0.0, along.x);
		if (off.dot(side) < 0.0) {
			side = side.scale(-1.0);
		}
		guard.getNavigation().stop();
		guard.push(side.x * 0.25, 0.0, side.z * 0.25);
		guard.getLookControl().setLookAt(carrier, 30.0F, 30.0F);
	}
}
