package com.jaymospowers.core.wounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.16.34) Healing magic's way into a body: Jaymo's Kingdoms' Restoration (a Sorcerer's Mend and Renewal, the Mending
 * and Restoration scrolls, court wizards and clerics) asks for it here by reflection, so it heals the parts as a dressing
 * does instead of only the hearts. Andy (2026-10-26): magic closes cuts and eases a fever; stumps still want cautery, and
 * only Restoration, the strongest spell, sets bones. (0.17.54) Every healing spell cures festering now ({@link #easeFever}). Nothing here reaches the downed except
 * {@link #restore} and {@link #rekindle}: a plain heal on the downed takes, as ever, nothing.
 */
public final class Mending {
	private Mending() {
	}

	/**
	 * Heals {@code amount} into {@code entity}'s most hurt parts, the worst first, as far as each may heal (a break or a
	 * festering part holds it down). A body Wounds doesn't part out just gets the hearts. Returns what it took.
	 */
	public static float mend(LivingEntity entity, float amount) {
		if (!entity.isAlive() || amount <= 0.0F || Downed.isDowned(entity)) {
			return 0.0F;
		}
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null) {
			float before = entity.getHealth();
			entity.heal(amount);
			return entity.getHealth() - before;
		}
		float left = amount;
		// the worst part first, as far as it may heal; what it can't take goes on to the next worst
		java.util.List<BodyPart> parts = new java.util.ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			if (!Breaks.missing(entity, plan.part(i))) {
				parts.add(plan.part(i));
			}
		}
		parts.sort(java.util.Comparator.comparingDouble(part -> Wounds.fraction(entity, part)));
		for (BodyPart part : parts) {
			if (left <= 0.01F) {
				break;
			}
			left -= Wounds.healPart(entity, part, left);
		}
		return amount - left;
	}

	/** Closes the worst open cut (never a stump: that's cautery's). True if one closed. */
	public static boolean closeCut(LivingEntity entity) {
		int open = Bleeding.bleeding(entity) & ~Bleeding.stumps(entity);
		if (open == 0) {
			return false;
		}
		BodyPart worst = null;
		float lowest = Float.MAX_VALUE;
		for (BodyPart part : BodyPart.values()) {
			if ((open & Breaks.bit(part)) != 0) {
				float fraction = Wounds.fraction(entity, part);
				if (fraction < lowest) {
					lowest = fraction;
					worst = part;
				}
			}
		}
		return worst != null && Bleeding.stop(entity, worst, false);
	}

	/**
	 * A fever eased: every festering part cured, the dirt washed from every cut, and the fever's weakness lifted. (0.17.54)
	 * Andy (2026-10-27): "restoration magic should clear festering": it took only the weakness and the dirt before, and
	 * left the festering for the Restoration spell. True if there was any.
	 */
	public static boolean easeFever(LivingEntity entity) {
		int festering = Bleeding.festering(entity);
		int foul = festering | Bleeding.dirty(entity);
		boolean any = false;
		for (BodyPart part : BodyPart.values()) {
			if ((foul & Breaks.bit(part)) != 0) {
				any |= Bleeding.clean(entity, part);
			}
		}
		if (festering != 0) {
			entity.removeEffect(MobEffects.WEAKNESS);
		}
		return any;
	}

	/**
	 * Restoration: every bone set, every festering part cured, every cut closed (not stumps), a downed one up on their feet,
	 * and the whole body healed.
	 */
	public static void restore(LivingEntity entity) {
		if (!entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Breaks.mendAll(entity);
		int festering = Bleeding.festering(entity);
		for (BodyPart part : BodyPart.values()) {
			if ((festering & Breaks.bit(part)) != 0) {
				Bleeding.clean(entity, part);
			}
		}
		Bleeding.stopAll(entity, false);
		if (Downed.isDowned(entity)) {
			Downed.rouse(level, entity, entity.getMaxHealth());
		}
		mend(entity, entity.getMaxHealth());
	}

	/** Rekindle: a downed one brought round from afar, up with {@code share} of their health. True if they were down. */
	public static boolean rekindle(LivingEntity entity, float share) {
		return entity.level() instanceof ServerLevel level && Downed.rouse(level, entity, entity.getMaxHealth() * share);
	}
}
