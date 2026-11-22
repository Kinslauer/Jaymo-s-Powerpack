package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebGaps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A web slinger isn't slowed by cobwebs (see WebGaps). */
@Mixin(Entity.class)
abstract class WebCobwebMixin {
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$spiderWalk(BlockState state, Vec3 multiplier, CallbackInfo ci) {
		if ((Object)this instanceof Player player && state.is(Blocks.COBWEB) && WebGaps.passesWebs(player)) {
			ci.cancel();
		}
	}
}
