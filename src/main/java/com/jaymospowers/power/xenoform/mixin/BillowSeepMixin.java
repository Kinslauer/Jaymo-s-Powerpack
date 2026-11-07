package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.forms.Billow;
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
 * (0.19.34) Billow seeps through anything short of a full block, as wholly smoke does: doors, fences, bars, panes,
 * trapdoors. What it's resting on stays solid (a cloud can sit on a slab or a fence top). Runs on both sides, so the
 * cloud's own movement agrees with the server's.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BillowSeepMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void jaymospowers$billowSeep(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext entity) || !(entity.getEntity() instanceof Player player) || !Billow.cloud(player)) {
			return;
		}
		VoxelShape shape = cir.getReturnValue();
		if (shape.isEmpty() || ((BlockBehaviour.BlockStateBase)(Object)this).isCollisionShapeFullBlock(level, pos)
			|| pos.getY() + shape.max(Direction.Axis.Y) <= player.getY() + 1.0E-3) {
			return;
		}
		cir.setReturnValue(Shapes.empty());
	}
}
