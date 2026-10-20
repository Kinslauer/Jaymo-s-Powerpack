package com.jaymospowers.aether;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cold and golden aerclouds: a soft cloud you walk into. Only a hair-thin plate at the bottom is solid, so you sink into
 * it and stand in it; inside, your fall is cut to almost nothing and forgotten (no fall damage on any aercloud), and
 * you can jump out. Falling fast (past {@link #FAST_FALL} blocks) you meet a 0.9-tall box instead and land on top.
 * Stacked clouds are one solid block. {@link BlueAercloudBlock} bounces.
 */
public class AercloudBlock extends TransparentBlock {
	// ---- tuning ----
	/** A fall this long (blocks) lands on top of the cloud instead of in it. */
	public static final double FAST_FALL = 2.5;
	/** Falling speed kept each tick while inside (the notes: damped by 0.005). */
	public static final double FALL_KEPT = 0.005;

	private static final VoxelShape PLATE = Block.box(0.0, 0.0, 0.0, 16.0, 0.01, 16.0);
	private static final VoxelShape FALLING_BOX = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.9, 1.0);

	public AercloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** The plate (a blue cloud gives another). */
	protected VoxelShape plate(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PLATE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape plate = this.plate(state, level, pos, context);
		if (!plate.isEmpty() && level.getBlockState(pos.above()).getBlock() instanceof AercloudBlock) {
			return Shapes.block();
		}
		if (!plate.isEmpty() && context instanceof EntityCollisionContext entityContext) {
			Entity entity = entityContext.getEntity();
			if (entity != null && entity.fallDistance > FAST_FALL && !(entity instanceof LivingEntity living && living.isFallFlying())) {
				return FALLING_BOX;
			}
		}
		return plate;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		this.settle(entity);
	}

	/** In the cloud: the fall forgotten and almost stopped, and able to jump out. */
	protected void settle(Entity entity) {
		entity.fallDistance = 0;
		Vec3 v = entity.getDeltaMovement();
		if (v.y < 0.0 && !(entity instanceof Projectile)) {
			entity.setDeltaMovement(v.x, v.y * FALL_KEPT, v.z);
		}
		entity.setOnGround(entity instanceof LivingEntity living && !(living instanceof Player player && player.getAbilities().flying));
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
