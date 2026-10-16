package com.jaymospowers.mixin;

import com.jaymospowers.core.Leaves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leaves are soft: entities (not projectiles or falling blocks) have nothing to bump into, and are dragged while inside. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class LeavesStateMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$softLeaves(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (Leaves.is((BlockState)(Object)this) && Leaves.passesThrough(context)) {
			cir.setReturnValue(Shapes.empty());
		}
	}

	@Inject(method = "entityInside", at = @At("HEAD"))
	private void jaymospowers$leafDrag(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise,
		CallbackInfo ci) {
		if (Leaves.is((BlockState)(Object)this)) {
			Leaves.inside(level, pos, entity);
		}
	}
}
