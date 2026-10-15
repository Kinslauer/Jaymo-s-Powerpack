package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.core.BlockPos;
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
 * Wholly smoke (fully mastered Vapour Form, cruising) seeps through anything short of a full block: doors, fences,
 * bars, panes, trapdoors, slabs. Runs on both sides, so the flier's own movement agrees with the server's.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class SmokeSeepMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void jaymospowers$smokeSeep(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player player && SmokeVapour.wholly(player)
			&& !cir.getReturnValue().isEmpty() && !((BlockBehaviour.BlockStateBase)(Object)this).isCollisionShapeFullBlock(level, pos)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
