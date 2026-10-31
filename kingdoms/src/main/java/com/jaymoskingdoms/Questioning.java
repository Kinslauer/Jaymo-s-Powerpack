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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
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
 * where home is. Players held down are only told so (Powers' overlay).
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
	// ================================================================================================================

	/** The questions on MCA's {@code main} menu (our dialogue data), shown only to the one questioning them. */
	public static final String LEADER = "jaymoskingdoms_q_leader", HOME = "jaymoskingdoms_q_home", THREATEN = "jaymoskingdoms_q_threaten",
		SPARE = "jaymoskingdoms_q_spare", FINISH = "jaymoskingdoms_q_finish";
	public static final List<String> ANSWERS = List.of(LEADER, HOME, THREATEN, SPARE, FINISH);

	/** Who's questioning whom, and how it's going. */
	private static final class Session {
		final UUID target;
		final boolean talks;
		boolean broken;
		int threats;
		final Set<String> asked = new HashSet<>();

		Session(UUID target, boolean talks) {
			this.target = target;
			this.talks = talks;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private Questioning() {
	}

	/** {@code body} can be questioned (Powers asks; a player can too, but Powers handles that itself): one of MCA's grown villagers. */
	public static boolean questionable(LivingEntity body) {
		return McaCompat.LOADED && McaBridge.questionable(body);
	}

	/** {@code player} holds {@code body} down and turns to question them: MCA's talk screen, with only the questions. */
	public static void open(ServerPlayer player, LivingEntity body) {
		Temperament t = Temperaments.of(body);
		float talks = t == null ? TALKS_DEFAULT : TALKS.getOrDefault(t, TALKS_DEFAULT);
		SESSIONS.put(player.getUUID(), new Session(body.getUUID(), body.getRandom().nextFloat() < talks));
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
		if (!session.asked.isEmpty()) {
			Recollections.remember(body, player, Recollections.Kind.QUESTIONED, Component.empty());
		}
		if (spared) {
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
		if (THREATEN.equals(answer)) {
			return !session.broken && session.threats < THREATS;
		}
		return !session.asked.contains(answer);
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
				Temperament t = Temperaments.of(body);
				float breaks = t == null ? BREAKS_DEFAULT : BREAKS.getOrDefault(t, BREAKS_DEFAULT);
				if (session.talks || body.getRandom().nextFloat() < breaks) {
					session.broken = true;
					return line("threat.broken", body, player);
				}
				return line(session.threats >= THREATS ? "threat.never" : "threat.held", body, player);
			}
			default -> {
			}
		}
		if (!session.talks && !session.broken) {
			return line("refuse", body, player);
		}
		session.asked.add(answer);
		ServerLevel level = (ServerLevel) body.level();
		Faction faction = Factions.get(level.getServer(), Rulers.faction(body));
		if (LEADER.equals(answer)) {
			if (faction == null) {
				return line("leader.none", body, player);
			}
			Component name = leaderName(level, faction);
			return faction.type() == FactionType.BARBARIAN ? line("leader.camp", body, player, name, faction.name())
				: name == null ? line("leader.empty", body, player, faction.name()) : line("leader.realm", body, player, name, faction.name());
		}
		if (HOME.equals(answer)) {
			if (faction == null) {
				return line("home.none", body, player);
			}
			Temperament t = Temperaments.of(body);
			boolean lies = (t == Temperament.PROUD || t == Temperament.SPITEFUL) && body.getRandom().nextFloat() < LIE_CHANCE;
			BlockPos seat = faction.seat();
			double dx = seat.getX() - body.getX(), dz = seat.getZ() - body.getZ();
			int blocks = Math.max(ROUND_TO, Math.round((float) Math.sqrt(dx * dx + dz * dz) / ROUND_TO) * ROUND_TO);
			int way = Math.floorMod(Math.round((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) / 45.0F) + (lies ? 4 : 0), 8);
			Component direction = Component.translatable("dialogue.chat.jaymoskingdoms.question.way." + way);
			return line(faction.type() == FactionType.BARBARIAN ? "home.camp" : "home.realm", body, player, Component.literal(Integer.toString(blocks)),
				direction, faction.name());
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
