package com.jaymospowers.aether;

import com.jaymospowers.core.Leaves;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cold and golden aerclouds: soft as leaves (Andy: "i should fall through and punch through them like leaves"). Players,
 * mobs and animals have nothing to stand on: they sink slowly down through a cloud, wade through it, climb up through it
 * holding Jump, and a hard fall or a fast flight crashes through it ({@link Leaves#inside}); the fall is forgotten inside
 * (no fall damage on any aercloud). Arrows and falling blocks still meet the thin plate at the bottom (stacked clouds, a
 * whole block). Broken, a cloud comes apart into nothing: there is no cloud to carry off. {@link BlueAercloudBlock} bounces.
 */
public class AercloudBlock extends TransparentBlock {
	// ---- tuning ----
	/** Brightness of faces next to a cloud (the notes: shade 0.25). */
	public static final float SHADE = 0.25F;

	private static final VoxelShape PLATE = Block.box(0.0, 0.0, 0.0, 16.0, 0.01, 16.0);

	public AercloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** Skylight does not pass down through a cloud. */
	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return false;
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return SHADE;
	}

	/** The plate (a blue cloud gives another). */
	protected VoxelShape plate(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PLATE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (Leaves.passesThrough(context)) {
			// soft as leaves: whoever walks, falls or flies (a space pod too) goes into it and through it
			return Shapes.empty();
		}
		VoxelShape plate = this.plate(state, level, pos, context);
		if (!plate.isEmpty() && level.getBlockState(pos.above()).getBlock() instanceof AercloudBlock) {
			return Shapes.block();
		}
		return plate;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		this.settle(level, pos, entity);
	}

	/** In the cloud: dragged on as in leaves (crashing through it if it hit hard), and the fall forgotten. */
	protected void settle(Level level, BlockPos pos, Entity entity) {
		Leaves.inside(level, pos, entity);
		entity.fallDistance = 0;
	}

	/** Landing on or in any aercloud never hurts. */
	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}
			BlockPos feet = entity.blockPosition();
			return !(entity.level().getBlockState(feet).getBlock() instanceof AercloudBlock)
				&& !(entity.level().getBlockState(feet.below()).getBlock() instanceof AercloudBlock);
		});
	}
}
