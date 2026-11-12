package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What fighters call out in a fight ({@link Comrades}, docs/Pawn-Plan.md rows D1 and D5): the foe they're taking, a
 * friend's back, a friend down, going to them, being badly hurt, the fight won, an order heard; and from the other side,
 * "they're out of arrows", "get the one tending them", a leader's shout. Words over their heads ({@link Speech}),
 * never chat: gold to those on their side or out of it, red to those they're fighting. In the speaker's tongue
 * ({@link Languages}) and their temperament's voice ({@link Voice}).
 * <p>
 * <b>No repeats</b> (row D5): every speaker waits {@link #SPEAKER_GAP} between lines; one side says a kind of line once
 * per {@link #AREA_GAP} round any spot ({@link #AREA}), so a band doesn't chant; the quiet speak less ({@link #skip});
 * and nobody says one of the last {@link #REMEMBER} lines they said. An order's answer ({@code force}) only keeps the
 * last rule.
 * <p>
 * Lines: {@code comrades.jaymoskingdoms.<kind>[.<voice>].<n>} in lang part {@code comrades.json}; how many each has is
 * {@link #LINES}.
 */
public final class Callouts {
	/** How a temperament sounds. */
	enum Voice {
		BOLD, WARM, STEADY, QUIET;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Ticks between two lines from one speaker; between two of one kind from one side near one spot. */
	static final int SPEAKER_GAP = 120, AREA_GAP = 300;
	/** The spot a kind of line is claimed round (blocks); how far the words are seen (blocks). */
	static final double AREA = 16.0, HEARD = 20.0;
	/** Lines a speaker remembers saying, and won't say again. */
	static final int REMEMBER = 6;
	/** Chance a steady or quiet speaker keeps a line to themselves. */
	static final float STEADY_SKIP = 0.2F, QUIET_SKIP = 0.5F;
	/** Chance one told at the table to talk less keeps a line to themselves ({@link TableTalk#chatter}). */
	static final float LESS_SKIP = 0.4F;
	/** Chance a line comes with the speaker's voice. */
	static final float VOICE_CHANCE = 0.6F;

	/**
	 * How many lines each kind has; a kind marked voiced has that many per {@link Voice}. Every key here is in
	 * {@code lang/parts/comrades.json}.
	 */
	static final Map<String, Integer> LINES = new HashMap<>();
	static final java.util.Set<String> VOICED = new java.util.HashSet<>();

	static {
		for (Comrades.Leaning l : Comrades.Leaning.values()) {
			LINES.put("take." + l.key(), 3);
		}
		for (String k : new String[] {"hurt", "behind", "down", "tending", "dragging", "lifting", "legup", "toss", "ready", "spoils.give", "know.star", "drift"}) {
			LINES.put(k, 2);
			VOICED.add(k);
		}
		LINES.put("won", 3);
		VOICED.add("won");
		for (String o : new String[] {"go", "come", "wait", "help"}) {
			LINES.put("order." + o, 2);
			VOICED.add("order." + o);
		}
		for (String o : new String[] {"go", "come", "help"}) {
			LINES.put("lead." + o, 3);
		}
		LINES.put("foe.arrows", 3);
		LINES.put("foe.medic", 3);
		LINES.put("leaderdown", 3);
		for (String k : new String[] {"hold.grab", "hold.pin", "ambush", "spoils.grab", "know", "know.taught", "knows.shot", "knows.blade",
			"knows.blunt", "knows.magic", "knows.fire", "knows.parry", "knows.other"}) {
			LINES.put(k, 3);
		}
		// talk and table (docs/Pawn-Plan.md patch 4)
		for (String k : new String[] {"thanks", "bond.close", "bond.greet", "table.meal", "table.done"}) {
			LINES.put(k, 2);
			VOICED.add(k);
		}
		for (String k : new String[] {"bond.cool", "spec.known", "spec.learned", "spec.haggle", "spec.overheard", "spec.track", "spec.forage",
			"spec.pack", "spec.pack.full", "spec.unpack", "land.forest", "land.desert", "land.swamp", "land.jungle", "land.snow", "land.mountain",
			"land.coast", "land.plains", "land.trap", "land.ruin", "town.glad", "town.grim", "quest.way", "tip", "foe.ours", "foe.headed",
			"taunt.hurt", "taunt.broken", "taunt.arrow", "supper.confront", "supper.sick", "supper.scramble", "supper.caught", "you.up", "you.hurt",
			"you.hungry", "you.armour", "table.sit"}) {
			LINES.put(k, 3);
		}
		// hire and lend (docs/Pawn-Plan.md patch 5)
		for (String k : new String[] {"loan.errand", "loan.tavern", "loan.wait", "loan.work", "loan.part", "loan.home", "loan.learned", "loan.back",
			"loan.errand.done", "loan.bye", "loan.thanks", "loan.hired", "loan.hired.work", "taken.ours", "taken.ransom", "taken.help", "taken.free",
			"sell.camp", "band.boast", "band.praise", "band.scold", "way.town", "way.chest", "way.here", "way.cut", "party.done", "party.warn",
			"party.won", "party.race", "party.greet", "party.short", "party.hired"}) {
			LINES.put(k, 3);
		}
	}

	private record Claim(String side, String kind, Vec3 at, long until) {
	}

	private static final Map<UUID, Long> SPOKE = new HashMap<>();
	private static final Map<UUID, Deque<String>> SAID = new HashMap<>();
	private static final List<Claim> CLAIMS = new ArrayList<>();

	private Callouts() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPOKE.clear();
			SAID.clear();
			CLAIMS.clear();
		});
	}

	static Voice voice(Entity e) {
		Temperaments.Temperament t = Temperaments.of(e);
		if (t == null) {
			return Voice.STEADY;
		}
		return switch (t) {
			case BRAVE, PROUD, HOTHEADED, SPITEFUL -> Voice.BOLD;
			case KINDHEARTED, CHEERFUL, DEVOUT -> Voice.WARM;
			case DUTIFUL, SUSPICIOUS, GREEDY -> Voice.STEADY;
			case TIMID, GLOOMY -> Voice.QUIET;
		};
	}

	/** Whether a {@code voice} speaker keeps this one to themselves. */
	private static boolean skip(Mob speaker, Voice voice) {
		float roll = speaker.getRandom().nextFloat();
		int chatter = TableTalk.chatter(speaker);
		if (chatter > 0) {
			// told at the table to talk more
			return false;
		}
		if (chatter < 0 && roll < LESS_SKIP) {
			return true;
		}
		if (Specialties.is(speaker, Specialties.Specialty.QUIET) && speaker.isShiftKeyDown()) {
			// the quiet one keeps still when you creep
			return true;
		}
		return voice == Voice.QUIET && roll < QUIET_SKIP || voice == Voice.STEADY && roll < STEADY_SKIP;
	}

	/**
	 * {@code speaker} calls out a line of {@code kind} ({@code about}: whom it's about, for nothing yet but the record),
	 * with {@code args} for its blanks. {@code force} (an order's answer): said even if they spoke a moment ago. True if
	 * it was said.
	 */
	static boolean say(Mob speaker, String kind, @Nullable Entity about, boolean force, Object... args) {
		Integer count = LINES.get(kind);
		if (count == null || !(speaker.level() instanceof ServerLevel level) || !Temperaments.humanoid(speaker)) {
			return false;
		}
		long now = level.getGameTime();
		Voice voice = TableTalk.voice(speaker, kind, voice(speaker));
		String side = Comrades.side(speaker);
		if (!force) {
			Long last = SPOKE.get(speaker.getUUID());
			if (last != null && now - last < SPEAKER_GAP || claimed(side, kind, speaker.position(), now) || skip(speaker, voice)) {
				return false;
			}
		}
		String stem = "comrades.jaymoskingdoms." + kind + (VOICED.contains(kind) ? "." + voice.key() : "");
		Deque<String> said = SAID.computeIfAbsent(speaker.getUUID(), id -> new ArrayDeque<>());
		List<String> fresh = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String key = stem + "." + i;
			if (!said.contains(key)) {
				fresh.add(key);
			}
		}
		if (fresh.isEmpty()) {
			// all said lately: the oldest may come round again
			fresh.add(said.peekFirst() != null && said.peekFirst().startsWith(stem) ? said.peekFirst() : stem + ".0");
		}
		String key = fresh.get(speaker.getRandom().nextInt(fresh.size()));
		said.remove(key);
		said.addLast(key);
		while (said.size() > REMEMBER) {
			said.removeFirst();
		}
		SPOKE.put(speaker.getUUID(), now);
		CLAIMS.add(new Claim(side, kind, speaker.position(), now + AREA_GAP));
		send(level, speaker, Component.translatable(key, args));
		if (speaker.getRandom().nextFloat() < VOICE_CHANCE) {
			speaker.playAmbientSound();
		}
		return true;
	}

	/** One of {@code side} has said a {@code kind} near {@code at} lately. */
	private static boolean claimed(String side, String kind, Vec3 at, long now) {
		CLAIMS.removeIf(c -> c.until <= now);
		for (Claim c : CLAIMS) {
			if (c.side.equals(side) && c.kind.equals(kind) && c.at.distanceToSqr(at) < AREA * AREA) {
				return true;
			}
		}
		return false;
	}

	/** The words over {@code speaker}'s head: red to whom they're fighting, gold to everyone else. */
	private static void send(ServerLevel level, Mob speaker, Component words) {
		String side = Comrades.side(speaker);
		LivingEntity foe = Comrades.target(speaker);
		String foeSide = foe == null ? null : Comrades.side(foe);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(HEARD))) {
			String theirs = Comrades.side(p);
			boolean against = !theirs.equals(side) && (foe == p || theirs.equals(foeSide));
			int colour = against ? Speech.RED : Speech.GOLD;
			Component heard = Languages.heard(p, speaker, words);
			if (ServerPlayNetworking.canSend(p, Speech.Said.TYPE)) {
				ServerPlayNetworking.send(p, new Speech.Said(speaker.getId(), heard, colour));
			}
		}
	}
}
