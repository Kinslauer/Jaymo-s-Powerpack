package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * A mob that has lost an arm ({@link Dismember}) hits with half its melee damage; with both arms gone it holds and uses
 * no weapons, and can only bite ({@link #BITE}, headed zombies) or kick ({@link #NO_ARMS}, anyone with a leg left) at
 * low damage, or not attack at all when it can do neither. Kept as one attack-damage modifier, updated on sever or
 * regrowth and every {@link #CHECK_TICKS} from the mob's AI step ({@code ArmlessMobMixin}). Broken arms weaken a blow
 * their own way ({@link Breaks#ARM_DAMAGE}) and add to this.
 */
public final class ArmlessMobs {
	/** Share of melee damage lost with one arm gone, with both gone for a biter, with both gone for a kick, and with nothing left to strike with. */
	public static final double ONE_ARM = -0.5, BITE = -0.7, NO_ARMS = -0.9, HELPLESS = -1.0;
	/** Ticks between checks. */
	public static final int CHECK_TICKS = 10;
	private static final Identifier ID = JaymosPowers.id("armless_mob_damage");
	private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private ArmlessMobs() {
	}

	/** From a mob's AI step: keeps an armless mob's hands empty and puts its attack damage in line with its limbs. */
	public static void tick(Mob mob) {
		keepEmpty(mob);
		if (mob.tickCount % CHECK_TICKS == 0) {
			sync(mob);
		}
	}

	/** Puts {@code mob}'s melee damage modifier in line with the arms, head and legs it still has. */
	public static void sync(Mob mob) {
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage == null) {
			return;
		}
		int gone = Dismember.missing(mob) & ~Dismember.standIn(mob);
		int arms = ((gone & Dismember.RIGHT_ARM) != 0 ? 1 : 0) + ((gone & Dismember.LEFT_ARM) != 0 ? 1 : 0);
		double amount = arms == 0 ? 0.0
			: arms == 1 ? ONE_ARM
			: !Dismember.canAttack(mob) ? HELPLESS
			: Dismember.biter(mob) ? BITE : NO_ARMS;
		AttributeModifier now = damage.getModifier(ID);
		if (amount == 0.0) {
			if (now != null) {
				damage.removeModifier(ID);
			}
		} else if (now == null || now.amount() != amount) {
			damage.addOrUpdateTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/**
	 * An armless mob holds nothing in either hand and draws nothing: items picked up off the ground (preserved drop
	 * chance) fall back to the ground, while conjured fight gear (villager kit refreshes, belt sidearms, witch potions)
	 * is cleared in place without duplicating items.
	 */
	public static void keepEmpty(Mob mob) {
		if (!Dismember.armless(mob)) {
			return;
		}
		if (mob.isUsingItem()) {
			mob.stopUsingItem();
		}
		if (mob.isAggressive()) {
			mob.setAggressive(false);
		}
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		DropChances dropChances = mob.getDropChances();
		for (EquipmentSlot slot : HANDS) {
			ItemStack held = mob.getItemBySlot(slot);
			if (held.isEmpty()) {
				continue;
			}
			boolean preserved = dropChances.isPreserved(slot);
			mob.setItemSlot(slot, ItemStack.EMPTY);
			if (preserved) {
				mob.setDropChance(slot, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
				mob.spawnAtLocation(level, held);
			}
		}
	}
}
