package com.jaymospowers.core.finisher;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.97) The questioning's talk is Jaymo's Kingdoms' ({@code com.jaymoskingdoms.Questioning}): who can be questioned,
 * the talk screen it opens on them, and what's remembered when it's over. Reached by reflection, so Powers runs without
 * Kingdoms (and then nobody is questioned: {@link Finishers#mayQuestion}).
 */
final class KingdomsQuestioning {
	private static final @Nullable MethodHandle QUESTIONABLE, OPEN, ENDED, PLAYER_ANSWER;

	static {
		MethodHandle questionable = null, open = null, ended = null, playerAnswer = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				Class<?> c = Class.forName("com.jaymoskingdoms.Questioning");
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				questionable = lookup.findStatic(c, "questionable", MethodType.methodType(boolean.class, LivingEntity.class));
				open = lookup.findStatic(c, "open", MethodType.methodType(void.class, ServerPlayer.class, LivingEntity.class, boolean.class));
				ended = lookup.findStatic(c, "ended", MethodType.methodType(void.class, ServerPlayer.class, LivingEntity.class, boolean.class));
				playerAnswer = lookup.findStatic(c, "playerAnswer", MethodType.methodType(Component.class, ServerPlayer.class, ServerPlayer.class,
					String.class, boolean.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				questionable = open = ended = playerAnswer = null;
			}
		}
		QUESTIONABLE = questionable;
		OPEN = open;
		ENDED = ended;
		PLAYER_ANSWER = playerAnswer;
	}

	private KingdomsQuestioning() {
	}

	/** Whether {@code body} (not a player) can be questioned: one who talks, as Kingdoms has it. */
	static boolean questionable(LivingEntity body) {
		if (QUESTIONABLE == null) {
			return false;
		}
		try {
			return (boolean) QUESTIONABLE.invokeExact(body);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Their talk opens for {@code player}: the questions to put to them ({@code unseen}: taken from behind, Quiet Word). */
	static void open(ServerPlayer player, LivingEntity body, boolean unseen) {
		if (OPEN != null) {
			try {
				OPEN.invokeExact(player, body, unseen);
			} catch (Throwable ignored) {
				// (no talk: the hold goes on, and a blow or use still ends it)
			}
		}
	}

	/** It's over: {@code spared}, they were let go (else finished). */
	static void ended(ServerPlayer player, LivingEntity body, boolean spared) {
		if (ENDED != null) {
			try {
				ENDED.invokeExact(player, body, spared);
			} catch (Throwable ignored) {
				// (nothing to remember it by)
			}
		}
	}

	/** (0.18.97) A held player's answer to {@code what} ("leader", "home"), true or ({@code lie}) not; null without Kingdoms. */
	static @Nullable Component playerAnswer(ServerPlayer asker, ServerPlayer held, String what, boolean lie) {
		if (PLAYER_ANSWER == null) {
			return null;
		}
		try {
			return (Component) PLAYER_ANSWER.invokeExact(asker, held, what, lie);
		} catch (Throwable e) {
			return null;
		}
	}
}
