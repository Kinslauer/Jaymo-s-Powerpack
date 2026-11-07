package com.jaymospowers.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The sky dive (after ParCool's): falling a long way ({@link #MIN_FALL} blocks or more, and fast), press Jump and you
 * spread out flat, belly to the ground, arms and legs wide: you fall a little slower ({@link #MAX_FALL_SPEED}) and steer
 * much better ({@link #STEER}). Jump again folds you up; so does landing, water, or anything else taking the body.
 * <p>It doesn't fight the other airborne Jumps: the client only spreads you if, a moment after the press, you're still
 * plainly falling (a cloud in a bottle's second jump, a power's flight or wings have taken that press first), and never
 * in one of the moves {@link Ledge#canHang} rules out. Leap of Faith's head-first dive ({@code Freerun.dives}) is
 * looking down; a sky dive takes its place while it lasts. Landing in water from one is a belly flop ({@code Dives}).
 * Costs {@link #COST} feathers. Fall damage is as it would be (you still fell as far).
 */
public final class SkyDive {
	// ===== the knobs ================================================================================================
	/** Fallen at least this far (blocks) and falling at least this fast (blocks a tick) before Jump spreads you. */
	public static final double MIN_FALL = 6.0, MIN_SPEED = 0.6;
	/** The client checks this many ticks after the Jump press that nothing else took it. */
	public static final int CHECK_TICKS = 2;
	/** Spread out: the fastest you fall (blocks a tick; a plain fall reaches about 3.9), how hard the keys steer, and the fastest you drift. */
	public static final double MAX_FALL_SPEED = 1.5, STEER = 0.045, MAX_DRIFT = 0.9;
	/** Feathers a sky dive costs. */
	public static final int COST = 2;
	// ================================================================================================================

	/** On a player spread out in a sky dive (synced to everyone, for the pose). */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("sky_dive"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private SkyDive() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SkyDive::tick);
	}

	/** Whether {@code player} is spread out in a sky dive (any side). */
	public static boolean active(Player player) {
		return player.hasAttached(ACTIVE);
	}

	/** Whether the body is free for a sky dive (both sides). */
	public static boolean free(Player player) {
		return !player.onGround() && Ledge.canHang(player) && Freerun.canMove(player);
	}

	static void start(ServerPlayer player) {
		if (active(player) || !free(player) || player.fallDistance < MIN_FALL - 1.0) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		player.setAttached(ACTIVE, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.3F, 1.4F);
		player.addTag(Freerun.XP_TAG);
	}

	static void stop(ServerPlayer player) {
		player.removeAttached(ACTIVE);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (active(player) && !free(player)) {
				player.removeAttached(ACTIVE);
			}
		}
	}
}
