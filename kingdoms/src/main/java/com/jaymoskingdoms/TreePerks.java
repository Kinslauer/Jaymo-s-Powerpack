package com.jaymoskingdoms;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The class trees' own perks at work (docs/ClassTrees-Plan.md): each class's are in a file of their own, registered here,
 * and what they do to a blow is asked here once ({@link #dealt}, from the hurt mixin).
 */
public final class TreePerks {
	private TreePerks() {
	}

	static void register() {
		Sigils.register();
		ClassForks.register();
		Workshop.register();
		TinkerPerks.register();
	}

	/** What the trees' perks do to a blow on {@code target}: after the class perks, before the heritages. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		return TinkerPerks.dealt(target, source, Workshop.dealt(target, source, ClassForks.dealt(target, source, amount)));
	}
}
