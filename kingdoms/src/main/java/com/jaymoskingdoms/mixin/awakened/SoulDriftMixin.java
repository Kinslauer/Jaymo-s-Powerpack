package com.jaymoskingdoms.mixin.awakened;

import com.jaymoskingdoms.SoulArts;
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
 * A Reaper's keeper drifting ({@link SoulArts#drifting}) is stopped by no block but the floor under their feet and the blocks
 * nothing can break. Runs on both sides, so their own movement agrees with the server's.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class SoulDriftMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$soulDrift(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext entity) || !(entity.getEntity() instanceof Player player) || !SoulArts.drifting(player)) {
			return;
		}
		VoxelShape shape = cir.getReturnValue();
		if (shape.isEmpty() || ((BlockBehaviour.BlockStateBase) (Object) this).getDestroySpeed(level, pos) < 0.0F
			|| pos.getY() + shape.max(Direction.Axis.Y) <= player.getY() + 1.0E-3) {
			return;
		}
		cir.setReturnValue(Shapes.empty());
	}
}
