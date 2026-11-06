package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.21.22, Kingdoms Rivals patch 8) The hit streak (the Arkham and Shadow of War games' idea; ideas only): blows you land
 * in a fight without being hit back count up. At {@link #READY} a quick kill is yours: with Kingdoms' Executioner perk and
 * a weapon in hand, sneak and hold attack on a foe still on his feet and he's dropped where he stands and finished at once
 * ({@code Finishers}, {@link #fell}). A blow taken from anyone ends the streak, as does {@link #LAPSE_TICKS} without
 * landing one; the quick kill spends it.
 */
public final class HitStreak {
	// ================================================================================================================
	// Tuning
	/** Blows landed in a row for a quick kill. */
	public static final int READY = 8;
	/** (0.21.23) The count runs on to this (Chrono's and Web's takedowns take two foes at {@link #DOUBLE}). */
	public static final int MAX = 32, DOUBLE = 16;
	/** The count shows from this many on. */
	public static final int SHOW_FROM = 3;
	/** The streak runs out after this long without a blow landed (ticks). */
	public static final int LAPSE_TICKS = 100;
	/** A quick kill drops a foe this long (ticks; it's finished long before). */
	public static final int FELL_TICKS = 60;
	/** Nothing this big or this hardy is dropped by a quick kill (blocks wide, tall; health). */
	public static final float MAX_WIDTH = 2.0F, MAX_HEIGHT = 3.0F, MAX_HEALTH = 100.0F;
	// ================================================================================================================

	/** A player's streak, for their own screen (and the quick kill's hold, client side). */
	public static final AttachmentType<Integer> STREAK = AttachmentRegistry.create(JaymosPowers.id("hit_streak"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** When each player last landed a blow in their streak. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private HitStreak() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken <= 0.0F || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			if (victim instanceof ServerPlayer hit && source.getEntity() instanceof LivingEntity && source.getEntity() != hit) {
				// (hit back: the streak's over)
				end(hit);
			}
			if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && victim != player && !victim.isSpectator()) {
				landed(level, player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (LAST.isEmpty() || server.getTickCount() % 10 != 0) {
				return;
			}
			for (UUID id : java.util.List.copyOf(LAST.keySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player == null) {
					LAST.remove(id);
				} else if (player.level().getGameTime() - LAST.get(id) > LAPSE_TICKS) {
					end(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
		// (0.21.23, Rivals patch 9) a roll keeps the count
		com.jaymospowers.stamina.Dodge.onDodge((player, dir) -> kept(player));
	}

	/** {@code player}'s streak just now. */
	public static int count(Player player) {
		return player.getAttachedOrElse(STREAK, 0);
	}

	/** Whether {@code player}'s streak is long enough for a quick kill (either side). */
	public static boolean ready(Player player) {
		return count(player) >= READY;
	}

	/** Whether {@code body} is small and frail enough for a quick kill to drop it (either side). */
	public static boolean droppable(LivingEntity body) {
		return !(body instanceof Player) && body.getBbWidth() <= MAX_WIDTH && body.getBbHeight() <= MAX_HEIGHT && body.getMaxHealth() < MAX_HEALTH;
	}

	/** (0.21.23) A blow landed by other means (a web takedown's ground finish): counted like any. */
	public static void add(ServerPlayer player) {
		landed(player.level(), player);
	}

	/**
	 * (0.21.23, Rivals patch 9) {@code player} dodged, vaulted or used a gadget mid-fight: the streak (and a Chrono's Time
	 * Flow combo) holds, its lapse put off as if a blow had landed.
	 */
	public static void kept(ServerPlayer player) {
		if (count(player) > 0) {
			LAST.put(player.getUUID(), player.level().getGameTime());
		}
		com.jaymospowers.power.chrono.ChronoFlow.keepCombo(player);
	}

	/** (0.21.23) Spends {@code n} of the streak (all of it if it hasn't that many). */
	public static void spend(ServerPlayer player, int n) {
		int left = count(player) - n;
		if (left <= 0) {
			end(player);
		} else {
			player.setAttached(STREAK, left);
		}
	}

	private static void landed(ServerLevel level, ServerPlayer player) {
		LAST.put(player.getUUID(), level.getGameTime());
		int was = count(player);
		if (was >= MAX) {
			return;
		}
		int now = was + 1;
		player.setAttached(STREAK, now);
		if (now == READY || now == DOUBLE) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.hit_streak.ready", now).withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 1.8F);
		} else if (now >= SHOW_FROM) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.hit_streak.count", now).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** The streak's over (hit, run out, or spent). */
	public static void end(ServerPlayer player) {
		LAST.remove(player.getUUID());
		player.removeAttached(STREAK);
	}

	/**
	 * (From {@code Finishers}) The quick kill: {@code body}, still standing, is dropped where he stands so the finisher can
	 * take him, and the streak is spent. False if he can't be (too big, or nothing to drop).
	 */
	public static boolean fell(ServerLevel level, ServerPlayer player, LivingEntity body) {
		if (!ready(player) || !droppable(body) || !(Downed.downable(body) || Downed.stunnable(body))
			|| !Downed.knockOut(level, body, FELL_TICKS, true, "message.jaymospowers.hit_streak.felled")) {
			return false;
		}
		end(player);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}
}
