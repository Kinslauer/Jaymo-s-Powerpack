package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A mob that has lost an arm ({@link Dismember}) hits with half its melee damage; with both arms gone it can only kick
 * weakly, a tenth of it. Kept as one attack-damage modifier, checked every {@link #CHECK_TICKS} from the mob's AI step
 * ({@code ArmlessMobMixin}), so an arm that grows back lifts it again. Broken arms weaken a blow their own way
 * ({@link Breaks#ARM_DAMAGE}) and add to this.
 */
public final class ArmlessMobs {
	/** Share of melee damage lost with one arm gone, and with both (a kick). */
	public static final double ONE_ARM = -0.5, NO_ARMS = -0.9;
	/** Ticks between checks. */
	public static final int CHECK_TICKS = 10;
	private static final Identifier ID = JaymosPowers.id("armless_mob_damage");

	private ArmlessMobs() {
	}

	/** From a mob's AI step: puts its attack damage in line with the arms it has. */
	public static void tick(Mob mob) {
		if (mob.tickCount % CHECK_TICKS != 0) {
			return;
		}
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage == null) {
			return;
		}
		int gone = Dismember.missing(mob);
		int arms = ((gone & Dismember.RIGHT_ARM) != 0 ? 1 : 0) + ((gone & Dismember.LEFT_ARM) != 0 ? 1 : 0);
		double amount = arms == 0 ? 0.0 : arms == 1 ? ONE_ARM : NO_ARMS;
		AttributeModifier now = damage.getModifier(ID);
		if (amount == 0.0) {
			if (now != null) {
				damage.removeModifier(ID);
			}
		} else if (now == null || now.amount() != amount) {
			damage.addOrUpdateTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
