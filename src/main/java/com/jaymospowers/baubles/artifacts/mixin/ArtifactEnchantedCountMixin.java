package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** (Artifacts) The soothsayer's hood: one more level of Looting on what you kill. */
@Mixin(EnchantedCountIncreaseFunction.class)
public abstract class ArtifactEnchantedCountMixin {
	@Shadow
	@Final
	private Holder<Enchantment> enchantment;

	@ModifyExpressionValue(method = "run", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int jaymospowers$soothsayerHood(int level, ItemStack stack, LootContext context) {
		if (this.enchantment.is(Enchantments.LOOTING)
			&& ArtifactEffects.wears(context.getOptional(LootContextParams.ATTACKING_ENTITY), ArtifactBaubles.SOOTHSAYER_HOOD)) {
			return level + 1;
		}
		return level;
	}
}
