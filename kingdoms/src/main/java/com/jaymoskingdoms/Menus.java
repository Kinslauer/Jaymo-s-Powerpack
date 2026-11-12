package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A short menu on the talk card ({@link TalkNet}) that does something on each pick, so a feature can offer a few choices
 * without a screen of its own. One menu is open per player; the next one replaces it, and a pick runs its action only if
 * the speaker is still alive and within {@link #REACH}.
 */
final class Menus {
	/** How far the player may step off from the one they are talking to. */
	static final double REACH = 8.0;

	private record Open(int speaker, List<Consumer<ServerPlayer>> actions) {
	}

	private static final Map<UUID, Open> OPEN = new HashMap<>();

	private Menus() {
	}

	/** A choice: its words, whether it can be picked, and what it does. */
	record Choice(Component label, boolean enabled, Consumer<ServerPlayer> action) {
		static Choice of(Component label, Consumer<ServerPlayer> action) {
			return new Choice(label, true, action);
		}

		static Choice of(Component label, boolean enabled, Consumer<ServerPlayer> action) {
			return new Choice(label, enabled, action);
		}
	}

	/** {@code speaker} says {@code words} to {@code player} and offers {@code choices}. */
	static void ask(ServerPlayer player, LivingEntity speaker, Component words, List<Choice> choices) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		List<Component> labels = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		List<Consumer<ServerPlayer>> actions = new ArrayList<>();
		for (Choice c : choices) {
			labels.add(c.label());
			enabled.add(c.enabled());
			actions.add(c.enabled() ? c.action() : p -> {
			});
		}
		OPEN.put(player.getUUID(), new Open(speaker.getId(), actions));
		ServerPlayNetworking.send(player, new TalkNet.Ask(speaker.getId(), speaker.getDisplayName(), words, labels, enabled));
	}

	/** An answer to one of these menus; false when it was not. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Open open = OPEN.get(player.getUUID());
		if (open == null || open.speaker() != answer.entityId()) {
			return false;
		}
		OPEN.remove(player.getUUID());
		if (answer.option() < 0 || answer.option() >= open.actions().size()
			|| !(player.level().getEntity(open.speaker()) instanceof LivingEntity who) || !who.isAlive() || who.distanceTo(player) > REACH) {
			return true;
		}
		open.actions().get(answer.option()).accept(player);
		return true;
	}
}
