package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import com.jaymospowers.stamina.Dodge;
import com.jaymospowers.stamina.Feathers;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.15.5) Web and the pack's {@link Feathers} (in place of Combat Roll's charges): a web slinger gets
 * {@link #BONUS} golden feathers on top of the row, and every Spider-Sense counter ({@link WebCounter}) spends a
 * dodge's worth ({@link Dodge#COST}); without them, a dodge opens no counter.
 */
public final class WebRolls {
	// ===== the knobs ================================================================================================
	/** Golden feathers a web slinger holds (half-feathers: a full golden row, three more dodges). */
	public static final int BONUS = 20;
	// ================================================================================================================

	private WebRolls() {
	}

	static void register() {
		Feathers.addBonus(player -> Powers.has(player, WebPower.INSTANCE) ? BONUS : 0);
	}

	/** Whether {@code player} has the feathers for a counter. */
	static boolean has(ServerPlayer player) {
		return Feathers.canSpend(player, Dodge.COST);
	}

	/** A counter spends them. */
	static void spend(ServerPlayer player) {
		Feathers.spend(player, Dodge.COST);
	}
}
