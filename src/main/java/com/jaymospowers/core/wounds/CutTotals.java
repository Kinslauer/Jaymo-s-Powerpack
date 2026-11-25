package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Dismember;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Cuts on one limb add up: each blade or claw hit adds its harm to that limb's running total (kept in memory beside
 * {@link Wounds}, easing off as time passes and cleared once the limb is whole). Past {@link #TEAR_AT} times the limb's own
 * pool the limb may tear off ({@link Dismember#tearOff}), though no single blow was big enough.
 */
final class CutTotals {
	/** The total that may tear a limb, as a share of that limb's pool. */
	static final float TEAR_AT = 1.5F;
	/** A tear that fails leaves this share of {@link #TEAR_AT} behind, and an old total eases off by this share of the pool each second. */
	static final float AFTER_FAIL = 0.6F, EASE_PER_SECOND = 0.01F;
	/** Chance a total past the line tears the limb (before armour's guard). */
	static final float TEAR_CHANCE = 0.5F;
	/** How much each depth's cut adds to the total, as a share of its harm. */
	static final float[] ADDS = {0.8F, 1.0F, 1.3F};

	private static final class Total {
		final float[] limbs = new float[4];
		long last;
	}

	private static final Map<LivingEntity, Total> TOTALS = new WeakHashMap<>();

	private CutTotals() {
	}

	/** A cut of {@code loss} (health points) at {@code depth} landed on {@code landed}: adds it up, and may tear the limb off. */
	static synchronized void add(LivingEntity victim, BodyPlan plan, @Nullable BodyPart landed, float loss, int depth, DamageSource source, long now) {
		if (landed == null || plan == BodyPlan.SIMPLE || landed == BodyPart.HEAD) {
			return;
		}
		int limb = Breaks.limb(landed);
		int index = plan.index(landed);
		if (limb == 0 || index < 0) {
			return;
		}
		// (Dismember's limb bits run head 1, right arm 2, left arm 4, right leg 8, left leg 16)
		int slot = Integer.numberOfTrailingZeros(limb) - 1;
		if (slot < 0 || slot > 3) {
			return;
		}
		float pool = Wounds.cap(victim, plan, index);
		if (pool <= 0.0F) {
			return;
		}
		Total total = TOTALS.computeIfAbsent(victim, v -> new Total());
		if (total.last != 0L) {
			float eased = (now - total.last) / 20.0F * EASE_PER_SECOND * pool;
			for (int i = 0; i < 4; i++) {
				total.limbs[i] = Math.max(0.0F, total.limbs[i] - eased);
			}
		}
		total.last = now;
		if (Wounds.fraction(victim, landed) >= WoundMarks.MENDED) {
			total.limbs[slot] = 0.0F;
		}
		total.limbs[slot] += loss * ADDS[depth];
		if (total.limbs[slot] < TEAR_AT * pool) {
			return;
		}
		if (victim.getRandom().nextFloat() < Math.min(1.0F, TEAR_CHANCE * Dismember.armourGuard(victim, limb))
			&& Dismember.tearOff(victim, limb, source.getEntity() instanceof LivingEntity by ? by : null)) {
			total.limbs[slot] = 0.0F;
		} else {
			total.limbs[slot] = TEAR_AT * pool * AFTER_FAIL;
		}
	}
}
