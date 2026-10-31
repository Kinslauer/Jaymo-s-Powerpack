package com.jaymoskingdoms;

import com.jaymoskingdoms.Temperaments.Temperament;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.424) Andy: "you can question downed humanoids, unlocked in the tree, holding [the execution] now has you select
 * either question or kill dragging either left or right. goblins, orcs and players count. different animations for each
 * weapon. and it opens the talk menu with the relevant options."
 *
 * <p>Jaymo's Powers' finisher hold asks {@link #questionable} and, once a player with {@link Skills.Perk#INTERROGATION}
 * turns to question, calls {@link #open} (MCA's talk screen on the one held down, showing only the questions) and
 * {@link #ended} when it's over: let go ({@link Recollections.Kind#SPARED}) or finished. Whether they talk depends on their
 * temperament ({@link #TALKS}); a threat may break them ({@link #BREAKS}), and the proud and spiteful sometimes lie about
 * where home is. Players held down get Powers' own answer screen, and their answers come from {@link #playerAnswer}.
 * <p>(0.7.424) Andy: "it should be early on the tree, and available in stealth and strength as well". Any one of three
 * perks unlocks it, each with its own edge: Strength's {@link Skills.Perk#ROUGH_QUESTIONS} (threats bite: twice as likely
 * to break them, one more try, and it hurts), Stealth's {@link Skills.Perk#QUIET_WORD} (Powers lets you take the unaware
 * from behind in a choke hold; they never see who held them, so they keep no memory of you; and "Who else is about?"),
 * and Will's {@link Skills.Perk#INTERROGATION} (no lie gets past you, a stare after the threats fail, and "Swear you'll
 * never raise a hand to me").
 */
public final class Questioning {
	// ================================================================================================================
	// Tuning
	/** How likely one of each temperament is to answer at all, before any threat. */
	static final Map<Temperament, Float> TALKS = Map.ofEntries(
		Map.entry(Temperament.TIMID, 0.9F), Map.entry(Temperament.KINDHEARTED, 0.7F), Map.entry(Temperament.CHEERFUL, 0.7F),
		Map.entry(Temperament.GREEDY, 0.6F), Map.entry(Temperament.GLOOMY, 0.6F), Map.entry(Temperament.DEVOUT, 0.5F),
		Map.entry(Temperament.SUSPICIOUS, 0.4F), Map.entry(Temperament.HOTHEADED, 0.3F), Map.entry(Temperament.DUTIFUL, 0.25F),
		Map.entry(Temperament.PROUD, 0.2F), Map.entry(Temperament.SPITEFUL, 0.2F), Map.entry(Temperament.BRAVE, 0.15F));
	/** How likely a threat is to break one of each temperament (and they'll answer from then on). */
	static final Map<Temperament, Float> BREAKS = Map.ofEntries(
		Map.entry(Temperament.TIMID, 1.0F), Map.entry(Temperament.KINDHEARTED, 0.6F), Map.entry(Temperament.CHEERFUL, 0.6F),
		Map.entry(Temperament.GREEDY, 0.7F), Map.entry(Temperament.GLOOMY, 0.5F), Map.entry(Temperament.DEVOUT, 0.3F),
		Map.entry(Temperament.SUSPICIOUS, 0.5F), Map.entry(Temperament.HOTHEADED, 0.4F), Map.entry(Temperament.DUTIFUL, 0.35F),
		Map.entry(Temperament.PROUD, 0.3F), Map.entry(Temperament.SPITEFUL, 0.35F), Map.entry(Temperament.BRAVE, 0.15F));
	/** For one with no temperament. */
	static final float TALKS_DEFAULT = 0.5F, BREAKS_DEFAULT = 0.5F;
	/** Threats that fail before they won't be moved by any more. */
	static final int THREATS = 2;
	/** How likely the proud and the spiteful are to send you the wrong way when they do answer. */
	static final float LIE_CHANCE = 0.5F;
	/** Distances told are rounded to this many blocks. */
	static final int ROUND_TO = 50;
	/** Lines for each answer (lang {@code dialogue.chat.jaymoskingdoms.question.<what>.<n>}). */
	static final int LINES = 3;
	/** (Rough Questions) Threats this many times as likely to break them, and this many more tries. */
	static final float ROUGH_BREAKS = 2.0F;
	static final int ROUGH_THREATS = 1;
	/** (Interrogation) A stare after the threats fail adds this to their chance of breaking. */
	static final float STARE_BONUS = 0.35F;
	/** (Quiet Word) How far "who else is about?" counts their own (blocks). */
	static final double ABOUT_RANGE = 48.0;
	// ================================================================================================================

	/** The questions on MCA's {@code main} menu (our dialogue data), shown only to the one questioning them. */
	public static final String LEADER = "jaymoskingdoms_q_leader", HOME = "jaymoskingdoms_q_home", THREATEN = "jaymoskingdoms_q_threaten",
		SPARE = "jaymoskingdoms_q_spare", FINISH = "jaymoskingdoms_q_finish", NEARBY = "jaymoskingdoms_q_nearby", STARE = "jaymoskingdoms_q_stare",
		SWEAR = "jaymoskingdoms_q_swear", MOOD = "jaymoskingdoms_q_mood", CAPTAIN = "jaymoskingdoms_q_captain";
	public static final List<String> ANSWERS = List.of(LEADER, HOME, NEARBY, MOOD, CAPTAIN, THREATEN, STARE, SWEAR, SPARE, FINISH);
	/** The perks that unlock questioning (any one), by key: Strength's, Stealth's, Will's. */
	public static final String ROUGH = "rough_questions", QUIET = "quiet_word", WILL = "interrogation";

	/** Who's questioning whom, and how it's going. */
	private static final class Session {
		final UUID target;
		final boolean talks;
		/** (Quiet Word) Taken from behind: they never saw who held them. */
		final boolean unseen;
		boolean broken, stared;
		int threats;
		final Set<String> asked = new HashSet<>();

		Session(UUID target, boolean talks, boolean unseen) {
			this.target = target;
			this.talks = talks;
			this.unseen = unseen;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private Questioning() {
	}

	/** {@code body} can be questioned (Powers asks; a player can too, but Powers handles that itself): one of MCA's grown villagers. */
	public static boolean questionable(LivingEntity body) {
		return McaCompat.LOADED && McaBridge.questionable(body);
	}

	/** {@code player}'s rank in the questioning perk {@code key}. */
	private static boolean has(ServerPlayer player, String key) {
		return Skills.rank(player, key) > 0;
	}

	/**
	 * {@code player} holds {@code body} down and turns to question them ({@code unseen}: taken from behind, Quiet Word):
	 * MCA's talk screen, with only the questions.
	 */
	public static void open(ServerPlayer player, LivingEntity body, boolean unseen) {
		Temperament t = Temperaments.of(body);
		float talks = t == null ? TALKS_DEFAULT : TALKS.getOrDefault(t, TALKS_DEFAULT);
		SESSIONS.put(player.getUUID(), new Session(body.getUUID(), body.getRandom().nextFloat() < talks, unseen));
		if (McaCompat.LOADED) {
			McaBridge.openQuestioning(player, body);
		}
	}

	/** The questioning of {@code body} by {@code player} is over: they were let go ({@code spared}) or finished. */
	public static void ended(ServerPlayer player, LivingEntity body, boolean spared) {
		Session session = SESSIONS.remove(player.getUUID());
		if (McaCompat.LOADED) {
			McaBridge.closeQuestioning(player, body);
		}
		if (session == null || !session.target.equals(body.getUUID()) || !body.isAlive()) {
			return;
		}
		if (session.unseen) {
			// (Quiet Word) they never saw who it was
			return;
		}
		if (!session.asked.isEmpty()) {
			Recollections.remember(body, player, Recollections.Kind.QUESTIONED, Component.empty());
		}
		if (spared) {
			// (0.7.431) a captain remembers being let go
			Captains.spared(player, body, !session.asked.isEmpty());
			Recollections.remember(body, player, Recollections.Kind.SPARED, Component.empty());
		}
	}

	/** {@code player} is questioning {@code body} now. */
	public static boolean active(ServerPlayer player, LivingEntity body) {
		Session session = SESSIONS.get(player.getUUID());
		return session != null && session.target.equals(body.getUUID());
	}

	/** {@code answer} is one of the questions, and {@code player} may still ask it of {@code body}. */
	public static boolean offers(ServerPlayer player, LivingEntity body, String answer) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || !session.target.equals(body.getUUID())) {
			return false;
		}
		return switch (answer) {
			case THREATEN -> !session.broken && session.threats < threats(player);
			case STARE -> has(player, WILL) && !session.broken && !session.stared && session.threats >= threats(player);
			case NEARBY -> has(player, QUIET) && !session.asked.contains(answer);
			case SWEAR -> has(player, WILL) && !session.asked.contains(answer);
			// (0.7.425) one of a tribe's: what their king thinks of you (Tribes)
			case MOOD -> Tribes.of(body) != null && !session.asked.contains(answer);
			// (0.7.431) one of a tribe's: what their captain fears, while there's something you don't know (Captains)
			case CAPTAIN -> !session.asked.contains(answer) && Captains.asks(player, body);
			default -> !session.asked.contains(answer);
		};
	}

	/** Threats {@code player} may try (Rough Questions: one more). */
	private static int threats(ServerPlayer player) {
		return THREATS + (has(player, ROUGH) ? ROUGH_THREATS : 0);
	}

	/** How likely a threat is to break {@code body} (Rough Questions: twice). */
	private static float breaks(ServerPlayer player, LivingEntity body) {
		Temperament t = Temperaments.of(body);
		float breaks = t == null ? BREAKS_DEFAULT : BREAKS.getOrDefault(t, BREAKS_DEFAULT);
		return Math.min(1.0F, has(player, ROUGH) ? breaks * ROUGH_BREAKS : breaks);
	}

	/** The opening line of the one held down: they beg, glare or spit, as their temperament has it. */
	public static Component opening(ServerPlayer player, LivingEntity body) {
		Session session = SESSIONS.get(player.getUUID());
		String mood = session != null && session.talks ? "afraid" : "defiant";
		return line("opening." + mood, body, player);
	}

	/**
	 * {@code player} picked {@code answer} on {@code body}'s menu while questioning them: what they say, or null for
	 * nothing more (spared or finished: Powers ends the hold).
	 */
	static @Nullable Component answer(ServerPlayer player, LivingEntity body, String answer) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || !session.target.equals(body.getUUID())) {
			return null;
		}
		switch (answer) {
			case SPARE -> {
				PowersCompat.questionDone(player, false);
				return null;
			}
			case FINISH -> {
				PowersCompat.questionDone(player, true);
				return null;
			}
			case THREATEN -> {
				session.threats++;
				boolean rough = has(player, ROUGH);
				if (rough && body.level() instanceof ServerLevel level) {
					// (Rough Questions) it bites: a cry, and the hurt shows (no harm done that could finish them)
					level.broadcastEntityEvent(body, (byte) 2);
					level.playSound(null, body.getX(), body.getY(), body.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_WEAK,
						net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.8F);
				}
				if (session.talks || body.getRandom().nextFloat() < breaks(player, body)) {
					session.broken = true;
					return line(rough ? "threat.rough_broken" : "threat.broken", body, player);
				}
				return line(session.threats >= threats(player) ? "threat.never" : "threat.held", body, player);
			}
			case STARE -> {
				// (Interrogation) the threats failed; your eyes don't
				session.stared = true;
				if (body.getRandom().nextFloat() < Math.min(1.0F, breaks(player, body) + STARE_BONUS)) {
					session.broken = true;
					return line("stare.broken", body, player);
				}
				return line("stare.held", body, player);
			}
			default -> {
			}
		}
		if (!session.talks && !session.broken) {
			return line("refuse", body, player);
		}
		session.asked.add(answer);
		ServerLevel level = (ServerLevel) body.level();
		// (Interrogation) no lie gets past you
		Temperament temper = Temperaments.of(body);
		boolean lies = !has(player, WILL) && (temper == Temperament.PROUD || temper == Temperament.SPITEFUL) && body.getRandom().nextFloat() < LIE_CHANCE;
		if (SWEAR.equals(answer)) {
			Recollections.remember(body, player, Recollections.Kind.SWORE, Component.empty());
			return line("swear", body, player);
		}
		if (body instanceof Worker w) {
			// (0.7.425) what they let slip of their kind goes in your bestiary
			Bestiary.note(player, w.role(), Bestiary.TOLD);
		}
		if (CAPTAIN.equals(answer)) {
			// (0.7.431) a fear or weakness of one of their captains, now in your journal (a lie isn't)
			return Captains.questioned(player, body, lies);
		}
		if (MOOD.equals(answer)) {
			// (0.7.425) the tribe's grudge, as they'd put it
			String tribe = Tribes.of(body);
			return tribe == null ? line("leader.none", body, player) : Tribes.mood(player, tribe, body);
		}
		Faction faction = Factions.get(level.getServer(), Rulers.faction(body));
		if (LEADER.equals(answer)) {
			if (faction == null) {
				return line("leader.none", body, player);
			}
			Component name = leaderName(level, faction);
			return name == null ? line("leader.empty", body, player, faction.name())
				: line(faction.type() == FactionType.BARBARIAN ? "leader.camp" : "leader.realm", body, player, name, faction.name());
		}
		if (HOME.equals(answer)) {
			if (faction == null) {
				return line("home.none", body, player);
			}
			return line(faction.type() == FactionType.BARBARIAN ? "home.camp" : "home.realm", body, player, distance(body, faction.seat()),
				way(body, faction.seat(), lies), faction.name());
		}
		if (NEARBY.equals(answer)) {
			// (Quiet Word) their own within shouting distance, and how many of them guards
			int faction_ = Rulers.faction(body);
			int folk = 0, guards = 0;
			if (faction_ >= 0) {
				for (Entity e : level.getEntities(body, new AABB(body.blockPosition()).inflate(ABOUT_RANGE), e -> e instanceof Worker w && e.isAlive()
					&& w.factionId() == faction_)) {
					folk++;
					if (((Worker) e).role() == Folk.GUARD || ((Worker) e).role() == Folk.ARCHER) {
						guards++;
					}
				}
			}
			if (lies) {
				folk = folk * 3 + 4;
				guards = guards * 3 + 2;
			}
			return folk == 0 ? line("nearby.none", body, player)
				: line("nearby.some", body, player, Component.literal(Integer.toString(folk)), Component.literal(Integer.toString(guards)));
		}
		return null;
	}

	/** About how far {@code to} is from {@code from}, rounded to {@link #ROUND_TO}. */
	private static Component distance(Entity from, BlockPos to) {
		double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
		return Component.literal(Integer.toString(Math.max(ROUND_TO, Math.round((float) Math.sqrt(dx * dx + dz * dz) / ROUND_TO) * ROUND_TO)));
	}

	/** The compass way from {@code from} to {@code to} ({@code lies}: the opposite way). */
	private static Component way(Entity from, BlockPos to, boolean lies) {
		double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
		int way = Math.floorMod(Math.round((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) / 45.0F) + (lies ? 4 : 0), 8);
		return Component.translatable("dialogue.chat.jaymoskingdoms.question.way." + way);
	}

	/**
	 * (0.7.424) A player held down for questioning by {@code asker} answers {@code what} ("leader" or "home") from Powers'
	 * answer screen: the truth of their realm, or ({@code lie}) a likely lie. Null for anything else.
	 */
	public static @Nullable Component playerAnswer(ServerPlayer asker, ServerPlayer held, String what, boolean lie) {
		ServerLevel level = held.level();
		Faction faction = Factions.of(level.getServer(), held.getUUID());
		if (lie) {
			// (another realm's, or none at all)
			List<Faction> others = new java.util.ArrayList<>();
			for (Faction f : Factions.all(level.getServer())) {
				if (!f.fallen() && f.type() != FactionType.BARBARIAN && (faction == null || f.id() != faction.id())) {
					others.add(f);
				}
			}
			faction = others.isEmpty() ? null : others.get(held.getRandom().nextInt(others.size()));
		}
		String key = "dialogue.chat.jaymoskingdoms.question.player.";
		if ("leader".equals(what)) {
			if (faction == null) {
				return Component.translatable(key + "leader.none");
			}
			Component name = leaderName(level, faction);
			return name == null ? Component.translatable(key + "leader.empty", faction.name()) : Component.translatable(key + "leader.realm", name, faction.name());
		}
		if ("home".equals(what)) {
			if (faction == null) {
				return Component.translatable(key + "home.none");
			}
			return Component.translatable(key + "home.realm", distance(held, faction.seat()), way(held, faction.seat(), lie && held.getRandom().nextBoolean()),
				faction.name());
		}
		return null;
	}

	/** (From MCA's menu) {@code player} picked {@code answer} while questioning {@code body}: their reply on the screen. */
	public static void select(ServerPlayer player, LivingEntity body, String answer) {
		Component line = answer(player, body, answer);
		if (line != null && McaCompat.LOADED && active(player, body)) {
			McaBridge.questioningSay(player, body, line);
		}
	}

	/** Who rules {@code faction}: a player by the name they go by, or the ruler in the world, or null. */
	private static @Nullable Component leaderName(ServerLevel level, Faction faction) {
		if (faction.leader().isPresent()) {
			return Post.nameOf(level.getServer(), faction.leader().get());
		}
		PathfinderMob ruler = Rulers.of(level, faction);
		return ruler != null ? ruler.getDisplayName() : null;
	}

	private static Component line(String what, LivingEntity body, ServerPlayer player, Object... args) {
		Object[] all = new Object[args.length + 1];
		all[0] = Recollections.nameTo(player, body);
		System.arraycopy(args, 0, all, 1, args.length);
		return Component.translatable("dialogue.chat.jaymoskingdoms.question." + what + "." + body.getRandom().nextInt(LINES), all);
	}
}
