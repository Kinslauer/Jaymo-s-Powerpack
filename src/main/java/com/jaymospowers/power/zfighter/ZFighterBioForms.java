package com.jaymospowers.power.zfighter;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.138, Andy: "as a z fighter they gain various buffs per form and are more akin to Cell") A Bio-built Z Fighter
 * grows with the form Jaymo's Kingdoms gives them (its tag {@code jaymoskingdoms_bio_form_<n>}; Powers never compiles
 * against Kingdoms): more ki ({@link #KI}), harder fists ({@link #FISTS}), ki coming back faster ({@link #REGEN}), and
 * flight costing more in the bulky second form and less in the Perfect third ({@link #FLIGHT}). The Perfect form, the
 * steadiest, also knits its wounds back with ki ({@link #tick}: Cell's regeneration). Hooked from {@link ZFighterPower}
 * (max ki, fists, regen, flight) and {@link ZFighterRaces#tick}.
 */
final class ZFighterBioForms {
	/** The tag Kingdoms puts on a Bio-built player for each form. */
	static final String FORM_TAG = "jaymoskingdoms_bio_form_";
	/** By form: more max ki, more fist damage, ki regen and flight cost factors. */
	static final int[] KI = {15, 40, 75};
	static final double[] FISTS = {1.0, 2.5, 4.0};
	static final float[] REGEN = {1.2F, 1.4F, 1.75F}, FLIGHT = {1.0F, 1.15F, 0.8F};
	/** The Perfect form's regeneration: this much health every this many ticks while hurt, for this much ki, above this share of max ki. */
	static final float HEAL = 1.0F, HEAL_KI = 2.0F, HEAL_ABOVE = 0.2F;
	static final int HEAL_EVERY = 40;

	private ZFighterBioForms() {
	}

	/** {@code player}'s Bio-built form (0 to 2), or -1 if they aren't Bio-built. */
	static int form(Player player) {
		Set<String> tags = player.entityTags();
		for (int i = KI.length - 1; i >= 0; i--) {
			if (tags.contains(FORM_TAG + i)) {
				return i;
			}
		}
		return -1;
	}

	static int ki(Player player) {
		int form = form(player);
		return form < 0 ? 0 : KI[form];
	}

	static double fists(Player player) {
		int form = form(player);
		return form < 0 ? 0.0 : FISTS[form];
	}

	static float regen(Player player) {
		int form = form(player);
		return form < 0 ? 1.0F : REGEN[form];
	}

	static float flight(Player player) {
		int form = form(player);
		return form < 0 ? 1.0F : FLIGHT[form];
	}

	/** Every tick: the Perfect form mends itself while it has ki to spare. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, int max) {
		if (player.tickCount % HEAL_EVERY != 0 || form(player) != KI.length - 1 || player.getHealth() >= player.getMaxHealth() || !player.isAlive()
			|| live.ki < max * HEAL_ABOVE + HEAL_KI) {
			return;
		}
		live.ki -= HEAL_KI;
		player.heal(HEAL);
	}
}
