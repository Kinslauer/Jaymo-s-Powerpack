package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.Placed;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.5.46) Remembers which player placed a block ({@link Placed}), so breaking it later is no crime for them. */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
	private void jaymoskingdoms$markPlaced(Block block, Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack,
			Operation<Void> original) {
		original.call(block, level, pos, state, by, stack);
		if (by instanceof ServerPlayer player && level instanceof ServerLevel server) {
			Placed.placed(player, server, pos, state);
			// (0.7.220) a builder's work in town (and a Journeyman's block from the town's stock)
			Careers.placed(player, server, pos, stack);
		}
	}
}
