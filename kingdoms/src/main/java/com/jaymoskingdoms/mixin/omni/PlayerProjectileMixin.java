package com.jaymoskingdoms.mixin.omni;

import com.jaymoskingdoms.OmniCrossbows;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.103) What a player's crossbow takes from a hand is Make Do's to say ({@link OmniCrossbows#heldProjectiles}). The
 * priority puts this after OmniCrossbow's own Omni mixin on the same call, so it sees (and can undo) the enchantment's
 * "anything goes".
 */
@Mixin(value = Player.class, priority = 1500)
abstract class PlayerProjectileMixin {
	@ModifyExpressionValue(method = "getProjectile", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ProjectileWeaponItem;getSupportedHeldProjectiles()Ljava/util/function/Predicate;"))
	private Predicate<ItemStack> jaymoskingdoms$makeDo(Predicate<ItemStack> allowed, ItemStack heldWeapon) {
		return OmniCrossbows.heldProjectiles((Player) (Object) this, heldWeapon, allowed);
	}
}
