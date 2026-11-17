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
		Discipline.register();
		Fury.register();
		com.jaymoskingdoms.equestrian.Horsemanship.register();
	}

	/** What the trees' perks do to a blow on {@code target}: after the class perks, before the heritages. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		float dealt = TinkerPerks.dealt(target, source, Workshop.dealt(target, source, ClassForks.dealt(target, source, amount)));
		dealt = Fury.dealt(target, source, Discipline.dealt(target, source, dealt));
		return com.jaymoskingdoms.equestrian.Horsemanship.dealt(target, source, dealt);
	}
}
