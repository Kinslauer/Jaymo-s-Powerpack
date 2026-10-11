package com.jaymospowers.power.resonance.mixin;

import com.jaymospowers.power.resonance.ResonanceFate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resonance's corruption: who set a shrieker off, and whether a Warden came of it. */
@Mixin(SculkShriekerBlockEntity.class)
abstract class ResonanceShriekerMixin {
	@Inject(method = "shriek", at = @At("HEAD"))
	private void jaymospowers$resonanceShriek(ServerLevel level, @Nullable Entity entity, CallbackInfo ci) {
		SculkShriekerBlockEntity self = (SculkShriekerBlockEntity) (Object) this;
		ResonanceFate.shrieked(level, self.getBlockPos(), entity == null ? null : SculkShriekerBlockEntity.tryGetPlayer(entity));
	}

	@Inject(method = "trySummonWarden", at = @At("RETURN"))
	private void jaymospowers$resonanceWarden(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			ResonanceFate.wardenSummoned(level, ((SculkShriekerBlockEntity) (Object) this).getBlockPos());
		}
	}
}
