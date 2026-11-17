package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Wind the day (Andy's Awakening pick: "happens the first time a chrono attunes, then again as the activation of time flow").
 * <ul>
 * <li>The first time someone ever attunes to Chronokinesis, the day turns once round over them: the sun and moon sweep a whole
 * day forward in {@link #FIRST_TICKS} ticks. It happens once a life, never again (kept through death and through losing the
 * power).</li>
 * <li>After that it is part of entering Time Flow: keep Z held as the flow takes you and the day sweeps forward, faster the
 * longer you hold; sneak and it winds back. Let go and it stops.</li>
 * </ul>
 * The sky is the whole server's, so on a server it needs the others' leave, the way sleeping does: of the other players in the
 * Overworld, at least the sleeping-percentage game rule's share must be asleep or crouching. Until they are, the day waits (the
 * first winding waits with it), and they are told on their action bar that a Chrono would turn it.
 */
public final class ChronoWindDay {
	// ---- tuning ----
	/** The first winding: a whole day (ticks of the clock) over this many ticks. */
	static final int FIRST_TICKS = 200, DAY = 24000;
	/** Time Flow's winding: ticks held before it starts (so a tap only enters the flow), the clock ticks a tick at first, and the most. */
	static final int HOLD_BEFORE = 10, START_RATE = 20, MAX_RATE = 400;
	/** How fast it speeds up: the rate grows this share each tick held. */
	static final double SPEED_UP = 0.04;
	/** How often (ticks) the others are told someone would wind it. */
	static final int ASK_EVERY = 40;
	/** How long (ticks) the first winding waits for the others' leave before it is let go for good. */
	static final int FIRST_WAIT = 20 * 60;

	/**
	 * False: this player has attuned for the first time and the first winding is owed; true: it has happened. Saved, kept through
	 * death, never removed. A Chrono who attuned before this was built has none, and is owed nothing.
	 */
	public static final AttachmentType<Boolean> WOUND = AttachmentRegistry.create(JaymosPowers.id("chrono_wound_day"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** When Z went down (game time), while it is held. The first winding's progress (clock ticks wound so far). */
	private static final Map<UUID, Long> HELD = new HashMap<>();
	private static final Map<UUID, Integer> FIRST = new HashMap<>();
	private static final Map<UUID, Integer> WAITED = new HashMap<>();

	private ChronoWindDay() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HELD.remove(handler.player.getUUID());
			FIRST.remove(handler.player.getUUID());
		});
	}

	/** The power was gained: the first time ever, the first winding is owed. */
	static void gained(ServerPlayer player) {
		if (!player.hasAttached(WOUND)) {
			player.setAttached(WOUND, false);
		}
	}

	/** Z went down or up (after the flow's own toggle). */
	static void mode(ServerPlayer player, boolean down) {
		if (down) {
			HELD.put(player.getUUID(), player.level().getGameTime());
		} else {
			HELD.remove(player.getUUID());
		}
	}

	/** Every tick, for a Chrono. */
	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		if (Boolean.FALSE.equals(player.getAttached(WOUND))) {
			first(player, level);
			return;
		}
		Long from = HELD.get(player.getUUID());
		if (from == null || !ChronoFlow.on(player)) {
			return;
		}
		long held = level.getGameTime() - from - HOLD_BEFORE;
		if (held < 0) {
			return;
		}
		if (!leave(player)) {
			return;
		}
		int rate = (int)Math.min(MAX_RATE, START_RATE * Math.pow(1.0 + SPEED_UP, held));
		wind(player, player.isShiftKeyDown() ? -rate : rate);
		if (held % 10 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.4F,
				player.isShiftKeyDown() ? 0.6F : 1.6F);
			MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 2.0F + Math.min(4.0F, rate / 100.0F), 0xFFE08A);
		}
	}

	/** The first winding, a whole day over the newly attuned, once the others allow it. */
	private static void first(ServerPlayer player, ServerLevel level) {
		if (!leave(player)) {
			// the others aren't asked for ever: after a while the moment has passed
			if (WAITED.merge(player.getUUID(), 1, Integer::sum) >= FIRST_WAIT) {
				WAITED.remove(player.getUUID());
				FIRST.remove(player.getUUID());
				player.setAttached(WOUND, true);
			}
			return;
		}
		WAITED.remove(player.getUUID());
		int done = FIRST.getOrDefault(player.getUUID(), 0);
		if (done == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 0.5F);
		}
		// eased: slow at the edges, quick through the middle
		double t0 = done / (double)FIRST_TICKS, t1 = (done + 1) / (double)FIRST_TICKS;
		int step = (int)Math.round(DAY * (ease(t1) - ease(t0)));
		wind(player, step);
		done++;
		if (done % 20 == 0) {
			MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 3.0F, 0xFFE08A);
		}
		if (done >= FIRST_TICKS) {
			FIRST.remove(player.getUUID());
			player.setAttached(WOUND, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
		} else {
			FIRST.put(player.getUUID(), done);
		}
	}

	private static double ease(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	/** Moves the Overworld's clock {@code ticks} (back, if negative). */
	private static void wind(ServerPlayer player, int ticks) {
		MinecraftServer server = player.level().getServer();
		Optional<Holder.Reference<WorldClock>> clock = player.level().registryAccess().get(WorldClocks.OVERWORLD);
		if (clock.isPresent() && ticks != 0) {
			server.clockManager().addTicks(clock.get(), ticks);
		}
	}

	/** Whether the others let the day turn: enough of the other Overworld players asleep or crouching (the sleeping rule's share). */
	static boolean leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		int others = 0, willing = 0;
		for (ServerPlayer p : overworld.players()) {
			if (p == player || p.isSpectator()) {
				continue;
			}
			others++;
			if (p.isSleeping() || p.isShiftKeyDown()) {
				willing++;
			}
		}
		if (others == 0) {
			return true;
		}
		int percent = overworld.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		if (willing * 100 >= percent * others) {
			return true;
		}
		if (player.level().getGameTime() % ASK_EVERY == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_wind.waiting").withStyle(ChatFormatting.GOLD));
			for (ServerPlayer p : overworld.players()) {
				if (p != player && !p.isSleeping() && !p.isShiftKeyDown()) {
					p.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_wind.ask").withStyle(ChatFormatting.GOLD));
				}
			}
		}
		return false;
	}
}
