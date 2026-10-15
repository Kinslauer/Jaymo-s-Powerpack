package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The only class that touches Better Combat's own classes: it reads a weapon's attributes from its registry (the
 * server's copy, from the data packs: Better Combat's own and every weapon mod's, Epic Knights' included) and turns them
 * into {@link MobCombat.Moves}. Only ever called when {@link MobCombat#ENABLED}.
 */
final class BetterCombatBridge {
	/** Each weapon's moves, worked out once per attributes object (a data reload makes new ones). */
	private static final Map<WeaponAttributes, MobCombat.Moves> CACHE = new WeakHashMap<>();

	private BetterCombatBridge() {
	}

	/** The weapon's moves, or null if Better Combat doesn't know it (or it has no attacks). */
	static MobCombat.@Nullable Moves moves(ItemStack weapon) {
		if (!MobCombat.ENABLED || weapon.isEmpty()) {
			return null;
		}
		WeaponAttributes attributes;
		try {
			attributes = WeaponRegistry.getAttributes(weapon);
		} catch (RuntimeException e) {
			return null;
		}
		if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) {
			return null;
		}
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(attributes, BetterCombatBridge::convert);
		}
	}

	private static MobCombat.Moves convert(WeaponAttributes attributes) {
		// the reach: the weapon's own if it sets one, else a mob's base and the weapon's bonus
		double reach = PlayerAttackHelper.combineAttackRange(attributes, MobCombat.BASE_REACH);
		if (attributes.attackRange() == 0.0) {
			reach += attributes.rangeBonus();
		}
		if (attributes.isTwoHanded()) {
			reach *= MobCombat.TWO_HANDED_REACH;
		}
		List<MobCombat.Move> moves = new ArrayList<>();
		for (WeaponAttributes.Attack attack : attributes.attacks()) {
			if (attack == null) {
				continue;
			}
			List<WeaponAttributes.Condition> conditions = attack.conditions() == null ? List.of() : Arrays.asList(attack.conditions());
			// mobs neither dual-wield nor fight from the saddle with these
			if (conditions.contains(WeaponAttributes.Condition.DUAL_WIELDING_ANY) || conditions.contains(WeaponAttributes.Condition.DUAL_WIELDING_SAME)
				|| conditions.contains(WeaponAttributes.Condition.DUAL_WIELDING_SAME_CATEGORY) || conditions.contains(WeaponAttributes.Condition.OFF_HAND_ONLY)
				|| conditions.contains(WeaponAttributes.Condition.MOUNTED)) {
				continue;
			}
			int shape = attack.hitbox() == null ? 0 : switch (attack.hitbox()) {
				case FORWARD_BOX -> 0;
				case VERTICAL_PLANE -> 1;
				case HORIZONTAL_PLANE -> 2;
			};
			WeaponAttributes.Sound sound = attack.swingSound();
			moves.add(new MobCombat.Move(shape, attack.angle(), attack.damageMultiplier() <= 0.0 ? 1.0 : attack.damageMultiplier(),
				attack.rangeMultiplier() <= 0.0F ? 1.0 : attack.rangeMultiplier(), attack.upswing() <= 0.0 ? 0.5 : attack.upswing(),
				attack.movementSpeedMultiplier() <= 0.0F ? 1.0 : attack.movementSpeedMultiplier(), attack.animation(),
				sound == null ? null : sound.id(), sound == null || sound.volume() <= 0.0F ? 1.0F : sound.volume(),
				sound == null || sound.pitch() <= 0.0F ? 1.0F : sound.pitch(), sound == null ? 0.0F : sound.randomness(),
				conditions.contains(WeaponAttributes.Condition.OFF_HAND_SHIELD), conditions.contains(WeaponAttributes.Condition.NO_OFFHAND_ITEM)));
		}
		return new MobCombat.Moves(reach, attributes.isTwoHanded(), List.copyOf(moves));
	}
}
