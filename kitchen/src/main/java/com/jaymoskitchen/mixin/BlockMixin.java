package com.jaymoskitchen.mixin;

import com.jaymoskitchen.cooking.PlacedSlabs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers slabs that players put down: only those work as prep boards (see {@link PlacedSlabs}). */
@Mixin(Block.class)
abstract class BlockMixin {
	@Inject(method = "setPlacedBy", at = @At("HEAD"))
	private void jaymoskitchen$rememberSlab(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack, CallbackInfo ci) {
		if (!level.isClientSide() && by instanceof Player && (Object) this instanceof SlabBlock) {
			PlacedSlabs.placed(level, pos);
		}
	}
}
