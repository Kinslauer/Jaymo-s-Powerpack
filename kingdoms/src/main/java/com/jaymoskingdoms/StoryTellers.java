package com.jaymoskingdoms;

import com.jaymoskingdoms.spirits.SpiritVoice;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Retaking's tellers ({@link Retaking}): who has something new to say once a milestone is done.
 * <ul>
 * <li><b>The Keeper, the Judge, the Omen pilots and the Tinkerer</b> (Powers' characters, as MCA villagers, {@link ZFighterFolk}):
 * on "Talk about work" they first say what the milestone has given them to say, if they have not said it yet ({@link #offer}):
 * one line each for every act, unlocked by a milestone of it ({@link #TELLS}), plus the Tinkerer's for the Bio-built. The words
 * are on the talk screen, with a reply and the way on to their work.</li>
 * <li><b>Your spirit</b> ({@link SpiritVoice#sayAct}) remarks on each king's fall, in its own voice (not the Quiet One).</li>
 * <li><b>Heritage</b>: the Warrior-born are recognised on setting foot in the Maw, the Frostborn hear "home" when the lights in
 * the sky first touch them (Act III). Both on the action bar, once ({@link #look}, {@link #paid}).</li>
 * </ul>
 * The village, priest, Truth's cleric and court-wizard tellers are {@link TalkBook}'s. Every line says what happened and never
 * what it means.
 */
final class StoryTellers {
	/** One thing a teller has to say: their kind ({@link ZFighterFolk#kind}), an id, and when they have it. Lines: {@code retaking.tell.<kind>.<id>.open|ask|said}. */
	record Tell(String kind, String id, Predicate<ServerPlayer> when) {
	}

	private static Predicate<ServerPlayer> done(String milestone) {
		return p -> Retaking.done(p, milestone);
	}

	/** In the order a teller says them: the earlier acts first. */
	static final List<Tell> TELLS = List.of(
		new Tell("keeper", "story", done("story")), new Tell("keeper", "giant", done("giant")), new Tell("keeper", "star", done("star")),
		new Tell("keeper", "spirit", done("spirit")), new Tell("keeper", "dragon", done("dragon")),
		new Tell("judge", "rank", done("first_rank")), new Tell("judge", "stones", done("monolith")), new Tell("judge", "raiders", done("raiders")),
		new Tell("judge", "weighed", done("weighed")), new Tell("judge", "wither", done("wither")),
		new Tell("omen_pilot", "goblin", done("goblin_king")), new Tell("omen_pilot", "ogre", done("ogre_king")), new Tell("omen_pilot", "maw", done("maw")),
		new Tell("omen_pilot", "bane", done("bane")), new Tell("omen_pilot", "dragon", done("dragon")),
		new Tell("tinkerer", "biobuilt", p -> Heritage.is(p, Heritage.Kind.BIOBUILT)));

	/** What a player has been told ({@code kind.id}) and the heritage lines they have had. Saved, kept through death. */
	static final AttachmentType<List<String>> TOLD = AttachmentRegistry.create(JaymosKingdoms.id("retaking_told"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Action-bar lines waiting for the pay line to clear. */
	private static final Map<UUID, Deque<Component>> LATER = new HashMap<>();

	private StoryTellers() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("retaking tellers attachment: {}", TOLD);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LATER.clear());
	}

	private static boolean told(ServerPlayer player, String id) {
		return player.getAttachedOrElse(TOLD, List.of()).contains(id);
	}

	private static boolean note(ServerPlayer player, String id) {
		List<String> now = player.getAttachedOrElse(TOLD, List.of());
		if (now.contains(id)) {
			return false;
		}
		List<String> more = new ArrayList<>(now);
		more.add(id);
		player.setAttached(TOLD, List.copyOf(more));
		return true;
	}

	/**
	 * {@code kind} ({@link ZFighterFolk#kind}) has something new to say to {@code player}: it is put on the talk screen, with
	 * the way on to {@code work}. False if they have nothing new (or the talk screen is not there), and {@code work} is then
	 * the caller's to run.
	 */
	static boolean offer(ServerPlayer player, LivingEntity speaker, String kind, Runnable work) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return false;
		}
		for (Tell tell : TELLS) {
			if (!tell.kind().equals(kind) || told(player, kind + "." + tell.id()) || !tell.when().test(player)) {
				continue;
			}
			note(player, kind + "." + tell.id());
			String base = "retaking.tell." + kind + "." + tell.id();
			Component onward = Component.translatable("retaking.tell.work");
			ZFighterFolk.ask(player, speaker, Component.translatable(base + ".open"),
				List.of(Component.translatable(base + ".ask"), onward), List.of(true, true), pick -> {
					if (pick == 1) {
						work.run();
					} else if (pick == 0) {
						ZFighterFolk.ask(player, speaker, Component.translatable(base + ".said"), List.of(onward), List.of(true), next -> {
							if (next == 0) {
								work.run();
							}
						});
					}
				});
			return true;
		}
		return false;
	}

	/** Every few seconds: a line waiting its turn, and the Warrior-born in the Maw. */
	static void look(ServerPlayer player) {
		Deque<Component> waiting = LATER.get(player.getUUID());
		if (waiting != null && !waiting.isEmpty()) {
			player.sendOverlayMessage(waiting.poll().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			if (waiting.isEmpty()) {
				LATER.remove(player.getUUID());
			}
		}
		if (player.level().dimension() == Retaking.MAW && Retaking.open(player, "maw") && Heritage.is(player, Heritage.Kind.WARRIORBORN)
			&& note(player, "heritage.warriorborn_maw")) {
			later(player, Component.translatable("message.jaymoskingdoms.retaking.warriorborn_maw"));
		}
	}

	private static void later(ServerPlayer player, Component line) {
		LATER.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).add(line);
	}

	/** {@code m} has just been paid to {@code player}: a king's fall is remarked on by their spirit; the lights touch a Frostborn. */
	static void paid(ServerPlayer player, Retaking.Milestone m, boolean remark) {
		if (remark) {
			SpiritVoice.sayAct(player, m.act().ordinal());
		}
		if (m.act() == Retaking.Act.LIGHTS && Heritage.is(player, Heritage.Kind.FROSTBORN) && note(player, "heritage.frostborn_home")) {
			later(player, Component.translatable("message.jaymoskingdoms.retaking.frostborn_home"));
		}
	}
}
