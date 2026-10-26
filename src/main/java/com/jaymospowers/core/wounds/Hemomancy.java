package com.jaymospowers.core.wounds;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.13) Blood magic's way into a body: Jaymo's Kingdoms' Blood Sorcerer (and its scrolls and blood witches) asks for
 * it here by reflection, as Restoration asks {@link Mending}. Andy (2026-10-26) chose death and blood magic as two new
 * Sorcerer elements. Blood magic counts open cuts, opens them, and bursts them; it never closes one.
 */
public final class Hemomancy {
	private Hemomancy() {
	}

	/** How many of {@code entity}'s parts are bleeding now (stumps too). */
	public static int cuts(LivingEntity entity) {
		return Integer.bitCount(Bleeding.bleeding(entity));
	}

	/**
	 * Opens a clean cut on one of {@code entity}'s parts that isn't bleeding yet (the arms and legs before the body, never
	 * the head). True if a new one opened; false for the bloodless, a body Wounds doesn't part out, or one bleeding everywhere.
	 */
	public static boolean cut(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || !Bleeding.bleeds(entity)) {
			return false;
		}
		int mask = Bleeding.bleeding(entity);
		java.util.List<BodyPart> open = new java.util.ArrayList<>();
		BodyPart body = null;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (part.vital() || (mask & Breaks.bit(part)) != 0 || Breaks.missing(entity, part)) {
				continue;
			}
			if (part == BodyPart.BODY) {
				body = part;
			} else {
				open.add(part);
			}
		}
		BodyPart pick = open.isEmpty() ? body : open.get(entity.getRandom().nextInt(open.size()));
		if (pick == null) {
			return false;
		}
		Bleeding.open(entity, pick, false);
		return (Bleeding.bleeding(entity) & Breaks.bit(pick)) != 0;
	}

	/**
	 * Every open cut on {@code entity} bleeds {@code times} ticks' worth at once ({@code by} the one who burst them, if it
	 * kills). Returns how many cuts burst (0: none were open).
	 */
	public static int hemorrhage(LivingEntity entity, @Nullable Entity by, float times) {
		BodyPlan plan = BodyPlan.of(entity);
		int mask = Bleeding.bleeding(entity);
		if (plan == null || mask == 0 || times <= 0.0F) {
			return 0;
		}
		int burst = 0;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if ((mask & Breaks.bit(part)) == 0) {
				continue;
			}
			float each = Breaks.missing(entity, part) ? Bleeding.STUMP_BLEED : Bleeding.BLEED_DAMAGE;
			Wounds.hurtPart(entity, part, each * times, by, Bleeding.BLEEDING_DAMAGE);
			burst++;
		}
		return burst;
	}
}
