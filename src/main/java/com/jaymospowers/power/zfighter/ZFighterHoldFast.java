package com.jaymospowers.power.zfighter;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Hold Fast, a passive every Z Fighter has: the nearer he is to going down, the harder he hits. Nothing under half health
 * is lost and nothing above it gained; from {@link #FROM} of his health the bonus climbs on a smooth curve to
 * {@link #MAX_BONUS} (+35%) at {@link #TO} of it, and stays there below. It multiplies every blow of his (fists, ki, beams),
 * after the punch cap, since a cap would swallow it.
 * <p>
 * Beside <i>No foe fells him twice</i> ({@link ZFighterBattle}: +50% against a foe that once beat him): the two do not simply
 * multiply. Together they may not take his damage above {@link #COMBINED_CAP} times, so against a rematch foe the brink gives
 * at most about +17% on top of the +50%.
 */
final class ZFighterHoldFast {
	/** The health share where it starts, the share where it is full, and the most it adds. */
	static final float FROM = 0.5F, TO = 0.1F, MAX_BONUS = 0.35F;
	/** The most his damage may be multiplied by, rematch edge and Hold Fast together. */
	static final float COMBINED_CAP = 1.75F;

	private ZFighterHoldFast() {
	}

	/** The bonus now, 0 to {@link #MAX_BONUS}. */
	static float bonus(Player player) {
		float share = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
		float t = Mth.clamp((FROM - share) / (FROM - TO), 0.0F, 1.0F);
		return MAX_BONUS * t * t * (3.0F - 2.0F * t);
	}

	/** What his damage is multiplied by now; {@code rematch}: the target is a foe that once beat him (it already gets its own edge). */
	static float factor(Player player, boolean rematch) {
		float factor = 1.0F + bonus(player);
		if (rematch) {
			factor = Math.min(factor, COMBINED_CAP / (1.0F + ZFighterBattle.FOE_BONUS));
		}
		return Math.max(1.0F, factor);
	}
}
