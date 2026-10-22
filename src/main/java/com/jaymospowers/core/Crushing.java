package com.jaymospowers.core;

import net.minecraft.world.entity.LivingEntity;

/**
 * (0.15.112) Andy: a spider killed by a large blunt impact or a slam is crushed, leaving no body to grab, only a few
 * loose legs. Jaymo's Kitchen does the crushing (its {@code SpiderCrush}); this marks the deaths it should crush, with
 * its scoreboard tag: a heavy blunt blow's kill ({@link BladeBlood#heavyBlunt}), anything {@link BladeBlood#crushed}
 * bursts, a blunt finisher, and the powers' slams. Kitchen itself already knows falls, falling anvils and a mace's smash.
 * Marking a creature that isn't a spider does nothing; nor does it without Kitchen.
 */
public final class Crushing {
	/** Kitchen's tag for a death that crushes (read by Kitchen as it swaps the dying mob for its corpse). */
	public static final String TAG = "jaymoskitchen.crushed";

	private Crushing() {
	}

	/** If the blow that just landed on {@code target} killed it, it was crushed to death. */
	public static void mark(LivingEntity target) {
		if (target.isDeadOrDying() && !target.level().isClientSide()) {
			target.addTag(TAG);
		}
	}
}
