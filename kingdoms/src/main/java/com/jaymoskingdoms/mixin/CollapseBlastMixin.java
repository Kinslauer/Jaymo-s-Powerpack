package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Collapse;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A blast that breaks blocks brings down what they held up ({@link Collapse}): what held is looked at before, and after. */
@Mixin(ServerExplosion.class)
abstract class CollapseBlastMixin {
	@Unique
	private Collapse.@Nullable Look jaymoskingdoms$look;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void jaymoskingdoms$before(List<BlockPos> blown, CallbackInfo ci) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		if (!blown.isEmpty() && self.getBlockInteraction() != Explosion.BlockInteraction.KEEP) {
			this.jaymoskingdoms$look = Collapse.before(self.level(), self.center(), self.radius());
		}
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void jaymoskingdoms$after(CallbackInfoReturnable<Integer> cir) {
		Collapse.after(this.jaymoskingdoms$look);
		this.jaymoskingdoms$look = null;
	}
}
