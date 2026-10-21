package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blue aercloud: nothing solid to stand on; anything that sinks into it is flung up hard (and its fall forgotten),
 * unless sneaking, which settles into it like a cold cloud. Runs on both sides, so the player's own client sees the throw.
 */
public class BlueAercloudBlock extends AercloudBlock {
	// ---- tuning ----
	/** Upward speed (blocks a tick) a bounce sets. */
	public static final double BOUNCE_SPEED = 2.0;
	/** Splash particles per tick inside, and when the bounce isn't moving anything (stuck, flying). */
	public static final int SPLASHES = 50, SPLASHES_STILL = 10;

	public BlueAercloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** Nothing to stand on (only the placement checks, which ask with the empty context, see a plate). */
	@Override
	protected VoxelShape plate(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return context == CollisionContext.empty() ? super.plate(state, level, pos, context) : Shapes.empty();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (entity.isShiftKeyDown()) {
			this.settle(entity);
			return;
		}
		Vec3 v = entity.getDeltaMovement();
		entity.fallDistance = 0;
		entity.setDeltaMovement(v.x, BOUNCE_SPEED, v.z);
		if (!(entity instanceof Projectile)) {
			entity.setOnGround(false);
		}
		if (level.isClientSide()) {
			if (v.y < 1.0) {
				level.playSound(entity instanceof Player player ? player : null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F,
					0.5F + level.getRandom().nextFloat() * 0.5F);
			}
			int count = entity.getY() == entity.yOld ? SPLASHES_STILL : SPLASHES;
			for (int i = 0; i < count; i++) {
				level.addParticle(ParticleTypes.SPLASH, pos.getX() + level.getRandom().nextDouble(), pos.getY() + level.getRandom().nextDouble(),
					pos.getZ() + level.getRandom().nextDouble(), 0.0, 0.0, 0.0);
			}
		}
	}
}
