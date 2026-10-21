package com.jaymoskitchen.mixin;

import com.jaymoskitchen.fishing.Bait;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fishing bait and shallow or roofed water ({@link Bait}). catchingFish rolls a fresh wait for a fish only when nothing
 * is on its way (no nibble, nothing hooking or lured): seen idle at the start, the wait it rolled is adjusted at the end.
 */
@Mixin(FishingHook.class)
abstract class FishingHookMixin {
	@Shadow
	private int nibble;
	@Shadow
	private int timeUntilLured;
	@Shadow
	private int timeUntilHooked;

	@Unique
	private boolean jaymoskitchen$rolling;

	@Inject(method = "catchingFish", at = @At("HEAD"))
	private void jaymoskitchen$beforeCatching(BlockPos pos, CallbackInfo ci) {
		jaymoskitchen$rolling = nibble <= 0 && timeUntilHooked <= 0 && timeUntilLured <= 0;
	}

	@Inject(method = "catchingFish", at = @At("TAIL"))
	private void jaymoskitchen$afterCatching(BlockPos pos, CallbackInfo ci) {
		if (jaymoskitchen$rolling) {
			jaymoskitchen$rolling = false;
			timeUntilLured = Bait.lured((FishingHook) (Object) this, pos, timeUntilLured);
		}
	}

	@ModifyArg(method = "retrieve", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/storage/loot/LootParams$Builder;withLuck(F)Lnet/minecraft/world/level/storage/loot/LootParams$Builder;"))
	private float jaymoskitchen$baitLuck(float luck) {
		return Bait.caught((FishingHook) (Object) this, luck);
	}
}
