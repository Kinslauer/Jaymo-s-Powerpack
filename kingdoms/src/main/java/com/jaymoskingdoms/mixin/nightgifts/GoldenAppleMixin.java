package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.VampirePlayers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.66) A golden apple eaten by the bitten before they turn: they'll wake a dhampir ({@link VampirePlayers#ateGold}). */
@Mixin(ItemStack.class)
abstract class GoldenAppleMixin {
	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void jaymoskingdoms$gold(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (entity instanceof ServerPlayer player && (self.is(Items.GOLDEN_APPLE) || self.is(Items.ENCHANTED_GOLDEN_APPLE))) {
			VampirePlayers.ateGold(player);
		}
	}
}
