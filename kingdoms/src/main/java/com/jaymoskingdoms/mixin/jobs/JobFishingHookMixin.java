package com.jaymoskingdoms.mixin.jobs;

import com.jaymoskingdoms.Careers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.220) A Fisher's bites come sooner and their catches are luckier ({@link Careers#lure}, {@link Careers#luck}), as the
 * rod's own Lure and Luck of the Sea do. The wait for a fish is cut as it's rolled (seen idle at the start of
 * catchingFish, as Jaymo's Kitchen's bait does).
 */
@Mixin(FishingHook.class)
abstract class JobFishingHookMixin {
	/** Ticks a level of lure takes off the wait (the enchantment's five seconds). */
	@Unique
	private static final int LURE_TICKS = 100;

	@Shadow
	private int nibble;
	@Shadow
	private int timeUntilLured;
	@Shadow
	private int timeUntilHooked;

	@Unique
	private boolean jaymoskingdoms$rolling;

	@Shadow
	public abstract Player getPlayerOwner();

	@Inject(method = "catchingFish", at = @At("HEAD"))
	private void jaymoskingdoms$beforeCatching(BlockPos pos, CallbackInfo ci) {
		jaymoskingdoms$rolling = nibble <= 0 && timeUntilHooked <= 0 && timeUntilLured <= 0;
	}

	@Inject(method = "catchingFish", at = @At("TAIL"))
	private void jaymoskingdoms$afterCatching(BlockPos pos, CallbackInfo ci) {
		if (jaymoskingdoms$rolling) {
			jaymoskingdoms$rolling = false;
			Player owner = getPlayerOwner();
			if (owner != null && timeUntilLured > 0) {
				timeUntilLured = Math.max(20, timeUntilLured - Careers.lure(owner) * LURE_TICKS);
			}
		}
	}

	@ModifyArg(method = "retrieve", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/storage/loot/LootParams$Builder;withLuck(F)Lnet/minecraft/world/level/storage/loot/LootParams$Builder;"))
	private float jaymoskingdoms$fishersLuck(float luck) {
		Player owner = getPlayerOwner();
		return owner == null ? luck : luck + Careers.luck(owner);
	}
}
