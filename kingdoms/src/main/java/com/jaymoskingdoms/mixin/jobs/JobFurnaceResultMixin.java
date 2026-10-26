package com.jaymoskingdoms.mixin.jobs;

import com.jaymoskingdoms.Careers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.218) Ingots taken from a furnace: a smith's work, and a Master Smith's bonus ingots ({@link Careers#smelted}). */
@Mixin(FurnaceResultSlot.class)
abstract class JobFurnaceResultMixin {
	@Shadow
	@Final
	private Player player;
	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void jaymoskingdoms$smelted(ItemStack carried, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp && removeCount > 0) {
			Careers.smelted(sp, carried, removeCount);
		}
	}
}
