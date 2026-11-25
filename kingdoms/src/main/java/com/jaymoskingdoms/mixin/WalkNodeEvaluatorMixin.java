package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Climbing;
import com.jaymoskingdoms.Passages;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The fighting AI's ways through (patch 6): a fighter's paths climb ladders and vines ({@link Climbing}), starting where it
 * hangs when it's on one, and go through shut fence gates, and iron doors with a switch beside them, that it can open
 * ({@link Passages}).
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin {
	@Shadow
	protected abstract Node getStartNode(BlockPos pos);

	@Inject(method = "getNeighbors", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$climbs(Node[] neighbors, Node pos, CallbackInfoReturnable<Integer> cir) {
		int count = cir.getReturnValueI();
		int more = Climbing.neighbors((WalkNodeEvaluator)(Object)this, neighbors, count, pos);
		if (more != count) {
			cir.setReturnValue(more);
		}
	}

	@Inject(method = "getStart", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$startOnLadder(CallbackInfoReturnable<Node> cir) {
		BlockPos at = Climbing.start(((NodeEvaluatorAccessor)(Object)this).jaymoskingdoms$mob());
		if (at != null) {
			cir.setReturnValue(this.getStartNode(at));
		}
	}

	@Inject(method = "getPathTypeOfMob", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$opens(PathfindingContext context, int x, int y, int z, Mob mob, CallbackInfoReturnable<PathType> cir) {
		PathType was = cir.getReturnValue();
		PathType now = Passages.type(context, x, y, z, mob, was);
		if (now != was) {
			cir.setReturnValue(now);
		}
	}
}
