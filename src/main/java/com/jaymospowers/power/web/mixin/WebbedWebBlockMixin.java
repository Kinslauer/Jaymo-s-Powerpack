package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.Webbed;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Andy: "emberborn burn any webs they contact") A cobweb or web an Emberborn walks into burns away, and doesn't hold them. */
@Mixin(WebBlock.class)
abstract class WebbedWebBlockMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$emberburns(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
		boolean isPrecise, CallbackInfo ci) {
		if (entity instanceof LivingEntity living && Webbed.emberborn(living)) {
			if (level instanceof ServerLevel server) {
				Webbed.burnBlock(server, pos);
			}
			ci.cancel();
		}
	}
}
