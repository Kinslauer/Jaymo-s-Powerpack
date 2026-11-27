package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.NightHorrors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
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
 * Mist that slips through ({@link NightHorrors#slippingNow}): doors, trapdoors, gates, bars and panes stop it no more than they
 * would a wisp of smoke. Runs on both sides, so its own movement agrees with the server's.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class MistSlipMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$slip(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext entity) || !(entity.getEntity() instanceof Player player) || cir.getReturnValue().isEmpty()) {
			return;
		}
		BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
		if ((self.is(BlockTags.DOORS) || self.is(BlockTags.TRAPDOORS) || self.is(BlockTags.FENCE_GATES) || self.getBlock() instanceof IronBarsBlock)
			&& NightHorrors.slippingNow(player)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
