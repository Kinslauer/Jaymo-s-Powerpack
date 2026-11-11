package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "some of the fighters should have shields so the fight looks realistic". A realm's guards always
 * carried their realm's arms ({@link Outfits#shield}); now the wild's fighters carry shields too, plain ones: a
 * barbarian's bare planks, an orc grunt's daubed black or blood red, a goblin's whatever it scavenged. They raise them
 * as the guards do ({@link MobShields}), wooden ones chip and break apart under enough blows (Jaymo's Powers'
 * {@code ShieldShatter}), and the risen dead carry them more often as the world hardens ({@link MonsterGear}).
 */
public final class ShieldBearers {
	/** The share of each kind that carries a shield beside a one-handed weapon. */
	static final float BARBARIAN = 0.45F, ORC_GRUNT = 0.4F, GOBLIN = 0.25F;

	private ShieldBearers() {
	}

	/**
	 * {@code mob}, armed for melee with its off hand free, takes up a plain wooden shield {@code chance} of the time,
	 * painted {@code paint} (null: bare planks). True if it did.
	 */
	static boolean arm(Mob mob, float chance, @Nullable DyeColor paint) {
		if (!canBear(mob) || mob.getRandom().nextFloat() >= chance) {
			return false;
		}
		ItemStack shield = new ItemStack(Items.SHIELD);
		if (paint != null) {
			shield.set(DataComponents.BASE_COLOR, paint);
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
		return true;
	}

	/** A melee weapon in the main hand (not a bow or the like) and nothing in the other. */
	static boolean canBear(Mob mob) {
		ItemStack main = mob.getMainHandItem();
		return !main.isEmpty() && !(main.getItem() instanceof ProjectileWeaponItem) && !main.is(Items.TRIDENT) && mob.getOffhandItem().isEmpty();
	}
}
