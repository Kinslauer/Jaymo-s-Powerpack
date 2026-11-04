package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** (Artifacts) The pearl pouch: a thrown pearl costs hunger instead of the pearl, while you have the food to spare. */
@Mixin(EnderpearlItem.class)
public abstract class ArtifactEnderpearlMixin {
	@Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
	private void jaymospowers$warpDrive(ItemStack stack, int count, LivingEntity user) {
		if (user instanceof Player player && !player.isCreative() && ArtifactEffects.wears(player, ArtifactBaubles.ENDER_POUCH)
			&& player.getFoodData().getFoodLevel() > ArtifactEffects.WARP_HUNGER) {
			if (!player.level().isClientSide()) {
				player.causeFoodExhaustion(ArtifactEffects.WARP_HUNGER * 4.0F);
			}
			return;
		}
		stack.consume(count, user);
	}
}
