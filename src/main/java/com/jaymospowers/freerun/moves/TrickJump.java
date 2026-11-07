package com.jaymospowers.freerun.moves;

import com.jaymospowers.core.Ledge;
import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The automatic trick jump (after ParCool's flipping): a jump out of a fast run ({@link FastRun}) now and then
 * ({@link #CHANCE}) turns over on its own, a front flip mostly, a back flip sometimes, in the air flip's own pose
 * ({@code AirFlip}, {@link WebPose#FRONT_FLIP}, drawn for everyone by {@code WebBodyPose}). It's free and for show; a
 * long jump never flips, and the Sneak-tap air flip stays as it was. The client rolls the chance
 * ({@code RunMovesClient}); here it's checked and shown.
 */
public final class TrickJump {
	// ===== the knobs ================================================================================================
	/** The chance a fast-run jump flips, and that a flip is a back flip. */
	public static final float CHANCE = 0.25F, BACK_SHARE = 0.3F;
	/** The client asks this many ticks into the jump, once the long jump's tap window ({@code LongJump.TAP_AFTER}) is past. */
	public static final int DELAY_TICKS = 4;
	/** At most one this often (ticks), and not over a pose still playing. */
	static final int GAP_TICKS = 20;
	// ================================================================================================================

	private TrickJump() {
	}

	static void flip(ServerPlayer player, int kind) {
		if (kind != WebPose.FRONT_FLIP && kind != WebPose.BACK_FLIP || player.onGround() || !FastRun.active(player) || !Ledge.canHang(player)
			|| WebPower.ragdolled(player)) {
			return;
		}
		long now = player.level().getGameTime();
		WebPose current = player.getAttached(WebPower.POSE);
		if (current != null && now - current.start() < Math.max(current.ticks(), GAP_TICKS)) {
			return;
		}
		WebPower.pose(player, kind, player.getRandom().nextBoolean() ? 1 : -1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.25F, 1.9F);
	}
}
