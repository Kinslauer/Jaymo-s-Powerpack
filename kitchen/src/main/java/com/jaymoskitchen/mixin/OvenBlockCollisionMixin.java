package com.jaymoskitchen.mixin;

import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The oven's outline grows a box over each utensil on top ({@link BaseKitchenBlockMixin}), but nothing should bump
 * into those: vanilla's BlockBehaviour#getCollisionShape would take the outline (state.getShape(level, pos)), so the
 * oven gets its own, which asks for the shape without the world (no block entity, so no utensil boxes). Neither
 * OvenBlock nor BaseKitchenBlock declares getCollisionShape, so this adds an override to OvenBlock. Support and visual
 * shapes go through getCollisionShape too, so they stay the plain oven as well.
 */
@Mixin(OvenBlock.class)
abstract class OvenBlockCollisionMixin {
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getShape(EmptyBlockGetter.INSTANCE, pos);
	}
}
