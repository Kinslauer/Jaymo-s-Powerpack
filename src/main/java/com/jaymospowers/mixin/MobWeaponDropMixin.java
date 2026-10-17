package com.jaymospowers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.13.13) A weapon looted off a mob (a drowned's trident, a skeleton's bow, a zombie's sword or axe) is nearly always
 * worn out: {@link #WORN_CHANCE} of the time it drops with at most {@link #WORN_LEFT} of its durability left; otherwise
 * vanilla's own wear. Only the equipment a mob drops by chance (vanilla's damaged drop); what it's sure to drop (a
 * guard's picked-up blade, a named mob's gear) comes as it was.
 */
@Mixin(Mob.class)
abstract class MobWeaponDropMixin {
	@Unique
	private static final float WORN_CHANCE = 0.84F, WORN_LEFT = 0.05F;

	@WrapOperation(method = "dropCustomDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
	private void jaymospowers$wornWeapon(ItemStack stack, int damage, Operation<Void> original) {
		Mob self = (Mob) (Object) this;
		if (jaymospowers$weapon(stack) && self.getRandom().nextFloat() < WORN_CHANCE) {
			int max = stack.getMaxDamage();
			int left = 1 + self.getRandom().nextInt(Math.max(1, Math.round(max * WORN_LEFT)));
			original.call(stack, Math.max(0, max - left));
			return;
		}
		original.call(stack, damage);
	}

	@Unique
	private static boolean jaymospowers$weapon(ItemStack stack) {
		return stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.TRIDENT) || stack.is(Items.MACE)
			|| stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(ItemTags.WEAPON_ENCHANTABLE);
	}
}
