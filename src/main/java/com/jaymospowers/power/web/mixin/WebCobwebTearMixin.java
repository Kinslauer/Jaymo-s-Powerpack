package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebGaps;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A web slinger tears a cobweb down by hand at once (see WebGaps); what it drops is still up to the tool, so bare hands farm no string. */
@Mixin(Player.class)
abstract class WebCobwebTearMixin {
	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
	private float jaymospowers$tearWebs(float original, BlockState state) {
		return state.is(Blocks.COBWEB) && WebGaps.passesWebs((Player)(Object)this) ? Math.max(original, WebGaps.WEB_TEAR_SPEED) : original;
	}
}
