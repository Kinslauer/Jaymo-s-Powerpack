package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Trees;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.586) A blast that breaks the ground fells the trees round it, and any it leaves standing on nothing ({@link Trees#blast}). */
@Mixin(ServerExplosion.class)
abstract class TreeBlastMixin {
	/** (0.7.604) A tree the blast reaches isn't holed: it comes down whole instead ({@link Trees#blastStruck}). */
	@ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
	private List<BlockPos> jaymoskingdoms$spareTrees(List<BlockPos> blown) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		return Trees.blastStruck(self.level(), self.center(), blown);
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void jaymoskingdoms$fellTrees(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		if (self.getBlockInteraction() != Explosion.BlockInteraction.KEEP) {
			Trees.blast(self.level(), self.center(), self.radius());
		}
	}
}
