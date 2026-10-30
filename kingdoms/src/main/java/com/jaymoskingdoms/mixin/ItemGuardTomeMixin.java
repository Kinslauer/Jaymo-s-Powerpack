package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Tomes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.396) A Mage's or Sorcerer's tome in the off hand takes right click from the weapon in the main hand (Andy's pick): a
 * weapon's guard (its {@code blocks_attacks}, which Jaymo's Powers gives every melee weapon) steps aside so the use reaches
 * the book. Shields (no {@code weapon} component) keep their block.
 */
@Mixin(Item.class)
abstract class ItemGuardTomeMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$tomeTakesRightClick(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.has(DataComponents.BLOCKS_ATTACKS) && stack.has(DataComponents.WEAPON) && Tomes.isTome(player.getOffhandItem()) && Tomes.offHanded(player)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
