package com.jaymospowers.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.PotionTags;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A splash of water (a splash water bottle, or a water bottle thrown by sneak-using it, see
 * {@link com.jaymospowers.alchemy.cauldron.CauldronBrewing}) puts out fire all round where it lands, not just the
 * blocks touching it.
 */
@Mixin(AbstractThrownPotion.class)
abstract class SplashWaterDouseMixin {
	/** How far round the landing spot (blocks, each way) a splash of water puts fires out. */
	@Unique
	private static final int DOUSE_RADIUS = 2;

	@Shadow
	private void douseFire(BlockPos pos) {
	}

	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void jaymospowers$douseWide(BlockHitResult hitResult, CallbackInfo ci) {
		AbstractThrownPotion self = (AbstractThrownPotion) (Object) this;
		if (self.level().isClientSide()
			|| !self.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(PotionTags.DOUSES_FIRE)) {
			return;
		}
		BlockPos centre = hitResult.getBlockPos().relative(hitResult.getDirection());
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-DOUSE_RADIUS, -1, -DOUSE_RADIUS), centre.offset(DOUSE_RADIUS, 1, DOUSE_RADIUS))) {
			if (pos.distSqr(centre) <= DOUSE_RADIUS * DOUSE_RADIUS + 1) {
				douseFire(pos.immutable());
			}
		}
	}
}
