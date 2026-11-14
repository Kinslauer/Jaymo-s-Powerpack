package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A settlement in doubt (Andy, 2026-11-14: once someone is found out the villagers are "suspicious as anyone could be a
 * changeling or shifter now. They will try and ask questions only they would know").
 * <p>
 * A face found out near a settlement (a shapeshifter's or a changeling's copy, {@link Doubles#expose}, or a changeling
 * player's, {@link BorrowedFaces#drop}) leaves it in doubt for {@link #DOUBT_TICKS}. While it lasts, a grown villager who
 * comes within {@link #ASK_RANGE} of a player stops them, at most once each {@link #ASK_GAP}, and asks something on the talk
 * card ({@link Menus}):
 * <ul>
 * <li><b>Of a player in their own face</b>: the name of the place they stand in, among two others.</li>
 * <li><b>Of a player wearing a villager's face</b>: that villager's spouse, dog, or the dog's colour ({@link Doubles#wife}),
 * among two wrong ones. A wearer who knew the villager before taking the face ({@link #KNEW}: MCA remembers them meeting)
 * knows the answer, so the wrong ones can't be picked; anyone else must guess.</li>
 * </ul>
 * Right, and that villager's settlement asks them nothing more while the doubt lasts. Wrong is a lie: the face (if any)
 * comes off there and then, and those who see it attack ({@link ShapeshifterAlarm}). Walking off without answering costs
 * nothing; they will ask again.
 */
final class Doubts {
	/** How long a settlement stays in doubt after someone is found out there (ticks: three days). */
	static final long DOUBT_TICKS = 72000L;
	/** A found-out face puts the nearest settlement in doubt if its seat is within this (blocks); its villagers ask within this of the seat. */
	static final double TOWN_RANGE = 128.0;
	/** A villager asks a player this close (blocks), and a player is asked at most this often (ticks). */
	static final double ASK_RANGE = 5.0;
	static final long ASK_GAP = 2400L;
	static final int EVERY = 40;
	static final int ASK_LINES = 3, RIGHT_LINES = 3, WRONG_LINES = 3;
	/** Places that are nowhere, for the wrong answers when the world has too few settlements. */
	static final List<String> NOWHERE = List.of("Harrowmere", "Oldwick", "Stennby", "Marrowfield");

	/** On the overworld: until when each settlement (by id) is in doubt. */
	static final AttachmentType<Map<String, Long>> UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("doubts"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** On a player wearing a face: they knew its owner before they took it. */
	static final AttachmentType<Boolean> KNEW = AttachmentRegistry.create(JaymosKingdoms.id("face_knew"), b -> b.persistent(Codec.BOOL));

	/** When each player was last asked, and which settlements have each player's answer (player, then settlement ids). */
	private static final Map<UUID, Long> ASKED = new HashMap<>();
	private static final Map<UUID, Set<Integer>> CLEARED = new HashMap<>();

	private Doubts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Doubts::tick);
	}

	/** Someone was found out at {@code at}: the settlement there is in doubt from now. */
	static void raise(ServerLevel level, BlockPos at) {
		Faction home = Captives.nearestSettlement(level.getServer(), at);
		if (home == null || home.seat().distSqr(at) > TOWN_RANGE * TOWN_RANGE) {
			return;
		}
		ServerLevel over = level.getServer().overworld();
		Map<String, Long> until = new HashMap<>(over.getAttachedOrElse(UNTIL, Map.of()));
		until.put(Integer.toString(home.id()), over.getGameTime() + DOUBT_TICKS);
		over.setAttached(UNTIL, until);
		// (a new fright: everyone is asked again)
		for (Set<Integer> cleared : CLEARED.values()) {
			cleared.remove(home.id());
		}
	}

	/** Whether {@code home} is in doubt now. */
	static boolean doubting(ServerLevel level, Faction home) {
		ServerLevel over = level.getServer().overworld();
		Long until = over.getAttachedOrElse(UNTIL, Map.of()).get(Integer.toString(home.id()));
		return until != null && over.getGameTime() < until;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (now % EVERY != 0 || level.getAttachedOrElse(UNTIL, Map.of()).isEmpty()) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			Long asked = ASKED.get(player.getUUID());
			if (player.isSpectator() || player.isCreative() || !player.isAlive() || asked != null && now - asked < ASK_GAP) {
				continue;
			}
			Faction home = Captives.nearestSettlement(server, player.blockPosition());
			if (home == null || home.seat().distSqr(player.blockPosition()) > TOWN_RANGE * TOWN_RANGE || !doubting(level, home)
				|| CLEARED.getOrDefault(player.getUUID(), Set.of()).contains(home.id())) {
				continue;
			}
			UUID wears = BorrowedFaces.faceOf(player);
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(ASK_RANGE),
				v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && McaCompat.isMca(v) && !Doubles.isCopy(v)
					&& !v.hasAttached(Captives.CAPTIVE) && !v.getUUID().equals(wears) && v.hasLineOfSight(player))) {
				ASKED.put(player.getUUID(), now);
				ask(level, player, v, home, wears);
				break;
			}
		}
	}

	/** {@code v} stops {@code player} and asks them something only the one they seem to be would know. */
	private static void ask(ServerLevel level, ServerPlayer player, Villager v, Faction home, @org.jspecify.annotations.Nullable UUID wears) {
		var random = level.getRandom();
		String right;
		List<String> wrong = new ArrayList<>();
		Component words;
		if (wears == null) {
			right = home.name();
			for (Faction f : Factions.all(level.getServer())) {
				if (f.id() != home.id() && !f.name().equals(right) && !wrong.contains(f.name())) {
					wrong.add(f.name());
				}
			}
			wrong.addAll(NOWHERE);
			Collections.shuffle(wrong.subList(0, Math.min(wrong.size(), 6)), new java.util.Random(random.nextLong()));
			words = Component.translatable("talk.jaymoskingdoms.doubt.ask.place." + random.nextInt(ASK_LINES));
		} else {
			int what = random.nextInt(3);
			List<String> all = what == 0 ? Doubles.WIVES : what == 1 ? Doubles.DOGS : Doubles.COLOURS;
			right = what == 0 ? Doubles.wife(wears) : what == 1 ? Doubles.dog(wears) : Doubles.colour(wears);
			for (String s : all) {
				if (!s.equals(right)) {
					wrong.add(s);
				}
			}
			Collections.shuffle(wrong, new java.util.Random(random.nextLong()));
			words = Component.translatable("talk.jaymoskingdoms.doubt.ask." + (what == 0 ? "wife" : what == 1 ? "dog" : "colour"), Doubles.dog(wears));
		}
		// (a wearer who knew the one they wear knows the answer: the wrong ones can't be picked)
		boolean knows = wears != null && Boolean.TRUE.equals(player.getAttached(KNEW));
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.literal(right), p -> right(level, p, v, home)));
		for (String s : wrong.subList(0, 2)) {
			choices.add(Menus.Choice.of(Component.literal(s), !knows, p -> wrong(level, p, v)));
		}
		Collections.shuffle(choices, new java.util.Random(random.nextLong()));
		v.getLookControl().setLookAt(player, 30.0F, 30.0F);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, 0.9F);
		Menus.ask(player, v, words, choices);
	}

	private static void right(ServerLevel level, ServerPlayer p, Villager v, Faction home) {
		CLEARED.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(home.id());
		Speech.tell(level, v, p, Component.translatable("talk.jaymoskingdoms.doubt.right." + level.getRandom().nextInt(RIGHT_LINES)), Speech.WHITE);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/** A lie: a face comes off there and then and those who see it attack; a player in their own face is taken for a thing wearing one. */
	private static void wrong(ServerLevel level, ServerPlayer p, Villager v) {
		Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.doubt.wrong." + level.getRandom().nextInt(WRONG_LINES)), Speech.RED, BorrowedFaces.SEEN);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F);
		BorrowedFaces.lied(p);
		if (!BorrowedFaces.drop(p)) {
			Doubts.raise(level, p.blockPosition());
			ShapeshifterAlarm.seen(level, p, p.getName(), null, null);
		}
	}
}
