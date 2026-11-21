package com.jaymospowers.buildblocks.mixin;

import com.jaymospowers.buildblocks.PairedDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A door opened by hand (or by anything that calls setOpen: villagers, our compat) opens its paired neighbour too, see {@link PairedDoors}. */
@Mixin(DoorBlock.class)
public abstract class DoorPairMixin {
	@Inject(method = "useWithoutItem", at = @At("RETURN"))
	private void jaymospowers$pairOpens(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction()) {
			PairedDoors.follow(level, pos, player);
		}
	}

	@Inject(method = "setOpen", at = @At("RETURN"))
	private void jaymospowers$pairFollows(Entity entity, Level level, BlockState state, BlockPos pos, boolean open, CallbackInfo ci) {
		PairedDoors.follow(level, pos, entity);
	}
}
