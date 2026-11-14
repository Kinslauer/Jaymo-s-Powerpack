package com.jaymospowers.freerun.moves;

import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The air roll (after Moves Like Mafuyu's idea; nothing of its code taken): just after a sprint jump, a fresh press of
 * Sprint (within {@link #WINDOW_TICKS} of leaving the ground) rolls you forward through the air with a real push along the
 * way you face ({@code RunMovesClient} moves the body). Unlike {@link com.jaymospowers.freerun.AirFlip}, which is for show
 * (a Sneak tap, no speed), this one carries you further. It is unlocked by the Air Roll perk in Kingdoms' Freerun tree
 * ({@link Freerun.Perk#AIR_ROLL}) and costs {@link #COST} feathers; the server checks the perk, charges and shows the front
 * flip ({@link WebPower#pose}).
 */
public final class AirRoll {
	// ===== the knobs ================================================================================================
	/** Feathers an air roll costs. */
	public static final int COST = 3;
	/** Client: a fresh Sprint press counts this many ticks after a sprint jump leaves the ground. */
	public static final int WINDOW_TICKS = 12;
	/** The push: speed along the way you face is the speed you had times {@link #BOOST} plus {@link #ADD}, at least {@link #MIN_SPEED} and at most {@link #MAX_SPEED} (blocks a tick); the lift it adds (blocks a tick). */
	public static final double BOOST = 1.2, ADD = 0.15, MIN_SPEED = 0.5, MAX_SPEED = 0.9, LIFT = 0.06;
	/** Server: at most one this often (ticks). */
	static final int GAP_TICKS = 15;
	// ================================================================================================================

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private AirRoll() {
	}

	/** Whether this player has unlocked the air roll (both sides: the client reads the synced ranks). */
	public static boolean allowed(Player player) {
		return Freerun.perk(player, Freerun.Perk.AIR_ROLL) > 0;
	}

	static void rolled(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < GAP_TICKS || Freerun.rank(player, Freerun.Perk.AIR_ROLL) < 1 || !Freerun.canMove(player)) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (LAST.size() > 256) {
			LAST.entrySet().removeIf(e -> now - e.getValue() > 1200L);
		}
		WebPower.pose(player, WebPose.FRONT_FLIP, 1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.5F);
		player.addTag(Freerun.XP_TAG);
	}
}
