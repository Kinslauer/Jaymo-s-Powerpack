package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Windfall;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A block that goes with nobody breaking it (a leaf decaying, a crop washed off, a cactus with nothing under it) drops
 * nothing unless a player is near ({@link Windfall#unwatched}). Only what it would drop is left out: an ore's experience
 * and an infested block's silverfish come as before. A block broken by someone (a player, a mob, a falling tree) drops as
 * it always did.
 */
@Mixin(Block.class)
abstract class UnwatchedDropsMixin {
	/** The two ways a block drops with no one behind it: by itself (leaf decay) and with what it held (water, pistons). */
	@WrapOperation(
		method = {
			"dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
			"dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
		},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"))
	private static List<ItemStack> jaymoskingdoms$unwatched(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
		Operation<List<ItemStack>> original) {
		return Windfall.unwatched(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) ? List.of() : original.call(state, level, pos, blockEntity);
	}

	/** A block destroyed with drops but no one named as breaking it ({@code level.destroyBlock(pos, true)}): the same. */
	@WrapOperation(
		method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;"))
	private static List<ItemStack> jaymoskingdoms$unwatchedNoBreaker(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
		@Nullable Entity breaker, ItemInstance tool, Operation<List<ItemStack>> original) {
		return breaker == null && Windfall.unwatched(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
			? List.of()
			: original.call(state, level, pos, blockEntity, breaker, tool);
	}
}
