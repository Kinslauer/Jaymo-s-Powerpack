package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.JoinHush;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks whose advancement is being awarded, so its chat announcement can wait with them ({@link JoinHush#holdAnnouncement}). */
@Mixin(PlayerAdvancements.class)
abstract class JoinHushAwardMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At("HEAD"))
	private void jaymoskingdoms$awarding(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		JoinHush.awarding(this.player);
	}

	@Inject(method = "award", at = @At("RETURN"))
	private void jaymoskingdoms$awarded(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		JoinHush.awarded();
	}
}
