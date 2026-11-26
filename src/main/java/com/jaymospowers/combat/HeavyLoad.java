package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/**
 * Andy (2026-11-25): "they should move slower when carrying something, for example the baby zombie was sprinting ...
 * with a giant spear in hand it just didn't make sense". What a mob holds in its hands weighs on it by how big it is:
 * a long weapon (a spear, a halberd) or a two-handed one is a full load, a shield or a one-handed weapon a lighter one
 * ({@link #load}), and a man's size bears {@link #FREE} of it without slowing. Past that it moves slower, by
 * {@link #SLOW_PER} of its speed for each load more, never more than {@link #MOST_SLOW}, and doesn't sprint. So a grown
 * zombie with a spear hardly notices it, and a baby zombie (a quarter of a man) with the same spear drags it along slower
 * than a grown one walks. Players are left to their own legs. Checked every {@link #CHECK_EVERY} ticks from the mob's
 * {@code aiStep} ({@code ArmourWearMobMixin}).
 */
public final class HeavyLoad {
	// ===== the knobs ================================================================================================
	/** Load of a long or two-handed weapon, of a shield, of any other weapon. */
	static final float LONG = 1.0F, SHIELD = 0.5F, ONE_HAND = 0.3F;
	/** Load a man-sized body bears freely; speed lost for each load past it (a share); the most lost. */
	static final float FREE = 0.6F, SLOW_PER = 0.3F, MOST_SLOW = 0.6F;
	/** A body's strength goes by its size against a man's (0.6 by 1.8 blocks), between these. */
	static final float SMALLEST = 0.15F, BIGGEST = 4.0F;
	static final int CHECK_EVERY = 10;
	// ================================================================================================================

	private static final Identifier ID = JaymosPowers.id("heavy_load");

	private HeavyLoad() {
	}

	/** From a mob's {@code aiStep}: slows it under what it holds (or lets it go again). */
	public static void tick(Mob mob) {
		if (mob.level().isClientSide() || (mob.tickCount + mob.getId()) % CHECK_EVERY != 0) {
			return;
		}
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		float carried = load(mob.getMainHandItem()) + load(mob.getOffhandItem());
		float strength = Math.clamp(mob.getBbWidth() * mob.getBbHeight() / (0.6F * 1.8F), SMALLEST, BIGGEST);
		float slow = Math.min(MOST_SLOW, (carried / strength - FREE) * SLOW_PER);
		AttributeModifier now = speed.getModifier(ID);
		if (slow <= 0.0F) {
			if (now != null) {
				speed.removeModifier(ID);
			}
			return;
		}
		// (a hundredth at a time, so a mob that grows a little isn't given a new modifier every check)
		double amount = -Math.round(slow * 100.0F) / 100.0;
		if (now == null || now.amount() != amount) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (mob.isSprinting()) {
			mob.setSprinting(false);
		}
	}

	/** How much {@code stack} in a hand weighs ({@link #LONG}, {@link #SHIELD}, {@link #ONE_HAND}, or nothing). */
	static float load(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		if (stack.getItem() instanceof ShieldItem) {
			return SHIELD;
		}
		Armed.Kind kind = Armed.kind(stack);
		if (kind == null) {
			return 0.0F;
		}
		return kind == Armed.Kind.ONE_HAND ? ONE_HAND : LONG;
	}
}
