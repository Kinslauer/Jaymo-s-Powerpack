package com.jaymoskingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * Villagers' scenes with each other, and a town's talk about what a player did (docs/StoryQuests-Plan.md).
 *
 * <p><b>Scenes</b>: now and then two grown villagers near a player stop, turn to each other and have a few lines
 * between them: work, crops, the weather, a quarrel. The scenes are data files ({@code data/<ns>/scenes/*.json}, read
 * by {@link Tale}): who can have it (two trades, {@code *} for anyone), when (day, night, rain, any), and the lines.
 * Speech bubbles in their own tongue, a voice and a small gesture with each line; nothing is said to the player.
 *
 * <p><b>Town talk</b>: when a tale ends, or a town is held through the night, its people have things to say of it
 * ({@link #townTalk}). Each line is said once, by whoever of that town next sees the player near; if it went well the
 * first of them is a cheer from everyone about, if badly a shake of the head.
 */
final class Overheard {
	// ---- tuning ----
	/** How often (ticks) scenes are looked at; each look, for each player, the chance one begins (about one every four minutes among folk). */
	static final int TICKS = 10, LOOK_TICKS = 100;
	static final float SCENE_CHANCE = 0.02F;
	/** Two villagers this near a player and this near each other can have a scene; it's dropped if they end up this far apart. */
	static final double SCENE_RANGE = 20.0, PAIR_RANGE = 10.0, PAIR_LOST = 14.0;
	/** They stand this near each other to talk; a line is heard this far. */
	static final double TALK_NEAR = 3.0, HEARD = 16.0;
	/** A line stays up this long (ticks), and this much longer for each letter; they have this long to walk to each other. */
	static final int LINE_TICKS = 50, LETTER_TICKS = 1, MEET_TICKS = 160;
	/** The same scene isn't had again until this many others have been; the same villager isn't in another for this long (ticks). */
	static final int REMEMBERED = 16, REST_TICKS = 20 * 90;
	/** Town talk: said to a player this near; one line each this long (ticks); a line unsaid after this many days is dropped; at most this many kept. */
	static final double TOWN_NEAR = 10.0, CHEER_RANGE = 14.0;
	static final int TOWN_GAP = 20 * 40, TOWN_DAYS = 12, TOWN_MOST = 24;

	/** A thing a town has to say to a player: whose town, the words, the last day it's worth saying, and whether it went badly. */
	record Talk(int faction, Component words, long until, boolean sour, boolean first) {
		static final Codec<Talk> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("faction").forGetter(Talk::faction),
				ComponentSerialization.CODEC.fieldOf("words").forGetter(Talk::words),
				Codec.LONG.fieldOf("until").forGetter(Talk::until),
				Codec.BOOL.optionalFieldOf("sour", false).forGetter(Talk::sour),
				Codec.BOOL.optionalFieldOf("first", false).forGetter(Talk::first))
			.apply(i, Talk::new));
	}

	static final AttachmentType<List<Talk>> TALK = AttachmentRegistry.create(JaymosKingdoms.id("town_talk"),
		builder -> builder.persistent(Talk.CODEC.listOf()).copyOnDeath());

	private static final class Scene {
		final Villager a, b;
		final List<String[]> lines;
		int line = -1, next;
		final int until;

		Scene(Villager a, Villager b, List<String[]> lines, int now) {
			this.a = a;
			this.b = b;
			this.lines = lines;
			this.next = now + MEET_TICKS;
			this.until = now + MEET_TICKS;
		}
	}

	private static final List<Scene> SCENES = new ArrayList<>();
	private static final Deque<String> HAD = new ArrayDeque<>();
	private static final Map<UUID, Integer> RESTING = new HashMap<>(), TOLD_AT = new HashMap<>();

	private Overheard() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Overheard::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SCENES.clear();
			HAD.clear();
			RESTING.clear();
			TOLD_AT.clear();
		});
	}

	// ---- town talk ---------------------------------------------------------------------------------------------------------

	/**
	 * The folk of settlement {@code faction} have {@code lines} to say to {@code player} about something that ended
	 * ({@code sour}: badly), when they next see them.
	 */
	static void townTalk(ServerPlayer player, int faction, List<Component> lines, boolean sour) {
		if (!(player.level() instanceof ServerLevel level) || lines.isEmpty()) {
			return;
		}
		List<Talk> talk = new ArrayList<>(player.getAttachedOrElse(TALK, List.of()));
		long until = Tales.day(level) + TOWN_DAYS;
		for (int i = 0; i < lines.size(); i++) {
			talk.add(new Talk(faction, lines.get(i), until, sour, i == 0));
		}
		while (talk.size() > TOWN_MOST) {
			talk.remove(0);
		}
		player.setAttached(TALK, List.copyOf(talk));
	}

	/** One who's died for good starts over: nobody has anything to say to them. */
	static void reborn(ServerPlayer player) {
		player.removeAttached(TALK);
	}

	private static boolean free(Villager v) {
		return v.isAlive() && !v.isBaby() && !v.isSleeping() && !v.isTrading() && !Downed.isDowned(v) && !McaCompat.busy(v) && TaleCast.cast(v) == null
			&& Rulers.target(v) == null && v.getVehicle() == null;
	}

	private static void voice(ServerLevel level, Villager v, net.minecraft.sounds.SoundEvent sound) {
		// (each has their own voice: a pitch from who they are)
		float pitch = 0.85F + (Math.floorMod(v.getUUID().hashCode(), 30)) / 100.0F;
		level.playSound(null, v.getX(), v.getY(), v.getZ(), sound, SoundSource.NEUTRAL, 0.7F, pitch);
	}

	private static void townTalk(ServerLevel level, ServerPlayer player, int tick) {
		List<Talk> talk = player.getAttachedOrElse(TALK, List.of());
		if (talk.isEmpty() || tick - TOLD_AT.getOrDefault(player.getUUID(), -TOWN_GAP) < TOWN_GAP || Downed.isDowned(player) || player.isSpectator()) {
			return;
		}
		long today = Tales.day(level);
		List<Talk> kept = new ArrayList<>(talk);
		kept.removeIf(t -> t.until() < today);
		Talk said = null;
		List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(TOWN_NEAR), v -> free(v) && v.hasLineOfSight(player));
		for (Villager v : near) {
			Faction f = Quests.settlement(level, v);
			if (f == null) {
				continue;
			}
			for (Talk t : kept) {
				if (t.faction() == f.id()) {
					said = t;
					break;
				}
			}
			if (said == null) {
				continue;
			}
			v.getNavigation().stop();
			Gestures.look(v, player.getEyePosition());
			Speech.tell(level, v, player, said.words(), said.sour() ? Speech.WHITE : Speech.GOLD);
			if (said.sour()) {
				// (a shake of the head)
				v.setUnhappyCounter(40);
				voice(level, v, SoundEvents.VILLAGER_NO);
			} else {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				voice(level, v, SoundEvents.VILLAGER_YES);
				if (said.first()) {
					// (the first to say it, and everyone about joins in)
					for (Villager other : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(CHEER_RANGE), o -> o != v && free(o))) {
						Gestures.look(other, player.getEyePosition());
						other.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						voice(level, other, SoundEvents.VILLAGER_CELEBRATE);
					}
				}
			}
			break;
		}
		if (said != null) {
			kept.remove(said);
			TOLD_AT.put(player.getUUID(), tick);
		}
		if (kept.size() != talk.size()) {
			if (kept.isEmpty()) {
				player.removeAttached(TALK);
			} else {
				player.setAttached(TALK, List.copyOf(kept));
			}
		}
	}

	// ---- scenes ---------------------------------------------------------------------------------------------------------------

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
	}

	private static boolean suits(String want, Villager v) {
		return want.equals("*") || want.equals(job(v));
	}

	private static boolean now(ServerLevel level, Villager at, String when) {
		return switch (when) {
			case "day" -> level.isBrightOutside() && !level.isRainingAt(at.blockPosition().above(2));
			case "night" -> level.isDarkOutside();
			case "rain" -> level.isRaining();
			default -> true;
		};
	}

	/** A scene {@code a} and {@code b} can have now that hasn't been had lately: its lines, each {who, words} with who {@code a} or {@code b} as they stand. */
	private static @Nullable Scene pick(ServerLevel level, Villager a, Villager b, int tick) {
		RandomSource random = level.getRandom();
		List<Map.Entry<String, JsonObject>> all = new ArrayList<>(Tale.scenes().entrySet());
		for (int n = all.size(); n > 0; n--) {
			Map.Entry<String, JsonObject> e = all.remove(random.nextInt(n));
			JsonObject scene = e.getValue();
			if (HAD.contains(e.getKey()) || !now(level, a, Tale.string(scene, "when", "any"))) {
				continue;
			}
			List<String> jobs = Tale.strings(scene.get("jobs"));
			String ja = jobs.size() == 2 ? jobs.get(0) : "*", jb = jobs.size() == 2 ? jobs.get(1) : "*";
			boolean straight = suits(ja, a) && suits(jb, b);
			if (!straight && !(suits(ja, b) && suits(jb, a))) {
				continue;
			}
			List<String[]> lines = new ArrayList<>();
			JsonElement raw = scene.get("lines");
			if (raw instanceof JsonArray array) {
				for (JsonElement line : array) {
					if (line instanceof JsonArray pair && pair.size() == 2) {
						lines.add(new String[] {pair.get(0).getAsString(), pair.get(1).getAsString()});
					}
				}
			}
			if (lines.isEmpty()) {
				continue;
			}
			HAD.addLast(e.getKey());
			while (HAD.size() > REMEMBERED) {
				HAD.removeFirst();
			}
			return straight ? new Scene(a, b, lines, tick) : new Scene(b, a, lines, tick);
		}
		return null;
	}

	private static void begin(ServerLevel level, ServerPlayer player, int tick) {
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SCENE_RANGE),
			v -> free(v) && tick - RESTING.getOrDefault(v.getUUID(), -REST_TICKS) >= REST_TICKS);
		if (folk.size() < 2) {
			return;
		}
		Villager a = folk.get(level.getRandom().nextInt(folk.size()));
		Villager b = null;
		for (Villager v : folk) {
			if (v != a && v.distanceToSqr(a) <= PAIR_RANGE * PAIR_RANGE && v.hasLineOfSight(a) && (b == null || v.distanceToSqr(a) < b.distanceToSqr(a))) {
				b = v;
			}
		}
		if (b == null) {
			return;
		}
		Scene scene = pick(level, a, b, tick);
		if (scene != null) {
			SCENES.add(scene);
			RESTING.put(a.getUUID(), tick);
			RESTING.put(b.getUUID(), tick);
		}
	}

	/** A scene's turn: they come together, then each line in its time, said to each other. False when it's over. */
	private static boolean play(ServerLevel level, Scene s, int tick) {
		if (!free(s.a) || !free(s.b) || s.a.distanceToSqr(s.b) > PAIR_LOST * PAIR_LOST || s.a.hurtTime > 0 || s.b.hurtTime > 0) {
			return false;
		}
		boolean together = s.a.distanceToSqr(s.b) <= TALK_NEAR * TALK_NEAR;
		if (s.line < 0) {
			if (!together) {
				if (tick > s.until) {
					return false;
				}
				if (tick % 20 == 0) {
					s.a.getNavigation().moveTo(s.b, 0.6);
					s.b.getNavigation().moveTo(s.a, 0.6);
				}
				return true;
			}
			s.next = tick;
		}
		s.a.getNavigation().stop();
		s.b.getNavigation().stop();
		Gestures.look(s.a, s.b.getEyePosition());
		Gestures.look(s.b, s.a.getEyePosition());
		if (tick < s.next) {
			return true;
		}
		s.line++;
		if (s.line >= s.lines.size()) {
			return false;
		}
		String[] line = s.lines.get(s.line);
		Villager speaker = line[0].equals("b") ? s.b : s.a;
		String words = line[1];
		Speech.say(level, speaker, Component.literal(words), Speech.WHITE, HEARD);
		// a voice and a small gesture to go with it: a shake of the head for a no, a nod's sound for a question answered, a hand for the rest
		String low = words.toLowerCase(java.util.Locale.ROOT);
		if (low.startsWith("no") || low.startsWith("never") || low.startsWith("not ")) {
			speaker.setUnhappyCounter(30);
			voice(level, speaker, SoundEvents.VILLAGER_NO);
		} else if (low.startsWith("aye") || low.startsWith("yes") || low.startsWith("then ")) {
			voice(level, speaker, SoundEvents.VILLAGER_YES);
		} else {
			if (level.getRandom().nextBoolean()) {
				speaker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			voice(level, speaker, SoundEvents.VILLAGER_AMBIENT);
		}
		s.next = tick + LINE_TICKS + words.length() * LETTER_TICKS;
		return true;
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		SCENES.removeIf(s -> !play(level, s, tick));
		if (tick % LOOK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			townTalk(level, player, tick);
			if (level.getRandom().nextFloat() < SCENE_CHANCE && !player.isSpectator()
				&& SCENES.stream().noneMatch(s -> s.a.distanceToSqr(player) < SCENE_RANGE * SCENE_RANGE * 4.0)) {
				begin(level, player, tick);
			}
		}
	}
}
