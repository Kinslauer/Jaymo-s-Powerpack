package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wisp holding Phase ({@link XenoformState#PHASE}) is stopped by no block: walls, doors, ceilings. What is under its feet
 * holds until it sneaks, so it walks a floor and sinks through one when it means to; blocks nothing can break (bedrock,
 * barriers) always hold, so it can't sink out of the world. Runs on both sides, so its own movement agrees with the server's.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class WispPhaseMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void jaymospowers$wispPhase(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext entity) || !(entity.getEntity() instanceof Player player) || !XenoformState.phasing(player)) {
			return;
		}
		VoxelShape shape = cir.getReturnValue();
		if (shape.isEmpty() || ((BlockBehaviour.BlockStateBase)(Object)this).getDestroySpeed(level, pos) < 0.0F
			|| !player.isShiftKeyDown() && pos.getY() + shape.max(Direction.Axis.Y) <= player.getY() + 1.0E-3) {
			return;
		}
		cir.setReturnValue(Shapes.empty());
	}
}
