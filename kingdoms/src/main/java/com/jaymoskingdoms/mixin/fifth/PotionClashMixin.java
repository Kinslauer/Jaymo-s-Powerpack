package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.PotionClash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A second potion drunk while the first works may clash with it ({@link PotionClash}); a clash that takes the drink's place cancels it. */
@Mixin(PotionContents.class)
abstract class PotionClashMixin {
	@Inject(method = "onConsume", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$clash(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (level instanceof ServerLevel && entity instanceof ServerPlayer player && PotionClash.drinking(player, (PotionContents)(Object)this)) {
			ci.cancel();
		}
	}
}
