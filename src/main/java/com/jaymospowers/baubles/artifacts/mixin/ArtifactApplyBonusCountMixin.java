package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** (Artifacts) The lucky scarf: one more level of Fortune on whatever you mine with. */
@Mixin(ApplyBonusCount.class)
public abstract class ArtifactApplyBonusCountMixin {
	@Shadow
	@Final
	private Holder<Enchantment> enchantment;

	@ModifyExpressionValue(method = "run", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getItemEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemInstance;)I"))
	private int jaymospowers$luckyScarf(int level, ItemStack stack, LootContext context) {
		if (this.enchantment.is(Enchantments.FORTUNE) && ArtifactEffects.wears(context.getOptional(LootContextParams.THIS_ENTITY), ArtifactBaubles.LUCKY_SCARF)) {
			return level + 1;
		}
		return level;
	}
}
