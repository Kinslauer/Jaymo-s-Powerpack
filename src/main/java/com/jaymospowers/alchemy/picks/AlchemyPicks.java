package com.jaymospowers.alchemy.picks;

/**
 * The alchemist's picks: skin draughts ({@link SkinDraughts}), the dampening brew ({@link Dampening}), the Draught of
 * Release ({@link GivingUp}) and the two pacts ({@link Pacts}). Each registers its own items, research and events here.
 * Their lang keys are all in {@code lang/parts/alchemy_picks.json}.
 */
public final class AlchemyPicks {
	/** What a hit's size becomes on a victim wearing a skin or bound by a pact (from {@code AlchemyPicksHurtMixin}). */
	public static float incoming(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(victim instanceof net.minecraft.server.level.ServerPlayer)) {
			return amount;
		}
		return Pacts.incoming(victim, source, SkinDraughts.incoming(victim, source, amount));
	}

	private AlchemyPicks() {
	}

	public static void init() {
		SkinDraughts.init();
		Dampening.init();
		GivingUp.init();
		Pacts.init();
	}
}
