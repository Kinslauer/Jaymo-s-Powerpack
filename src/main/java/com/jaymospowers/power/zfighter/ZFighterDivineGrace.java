package com.jaymospowers.power.zfighter;

import net.minecraft.world.entity.player.Player;

/**
 * (0.17.147, Andy: every heritage gets something as a Z Fighter) A Divine-born Z Fighter's ki is the calm light of the high
 * places: on top of being hidden from ki sense and scouters ({@link ZFighterKiSense#hidesKi}), it comes back faster
 * ({@link #REGEN}), a form costs less to hold ({@link #DRAIN}) and flight comes easier ({@link #FLIGHT}). Still an Earthling's
 * lineage otherwise ({@link ZFighterLineage#fromHeritage}). Hooked from {@link ZFighterPower} (regen, form drain, flight).
 */
final class ZFighterDivineGrace {
	/** Ki regen, a form's drain and flight's cost, as factors. */
	static final float REGEN = 1.25F, DRAIN = 0.75F, FLIGHT = 0.75F;

	private ZFighterDivineGrace() {
	}

	static float regen(Player player) {
		return ZFighterLineage.divine(player) ? REGEN : 1.0F;
	}

	static float drain(Player player) {
		return ZFighterLineage.divine(player) ? DRAIN : 1.0F;
	}

	static float flight(Player player) {
		return ZFighterLineage.divine(player) ? FLIGHT : 1.0F;
	}
}
