package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (Artifacts) The fisher's hat: one more level of Lure and of Luck of the Sea on any rod. */
@Mixin(EnchantmentHelper.class)
public abstract class ArtifactEnchantmentHelperMixin {
	@ModifyReturnValue(method = "getFishingTimeReduction", at = @At("RETURN"))
	private static float jaymospowers$fishersLure(float original, ServerLevel level, ItemStack rod, Entity fisher) {
		// Lure past 5 stops fish biting, so never push it over 25 seconds
		if (original >= 25.0F || !ArtifactEffects.wears(fisher, ArtifactBaubles.FISHERS_HAT)) {
			return original;
		}
		return Math.min(25.0F, original + 5.0F);
	}

	@ModifyReturnValue(method = "getFishingLuckBonus", at = @At("RETURN"))
	private static int jaymospowers$fishersLuck(int original, ServerLevel level, ItemStack rod, Entity fisher) {
		return ArtifactEffects.wears(fisher, ArtifactBaubles.FISHERS_HAT) ? original + 1 : original;
	}
}
