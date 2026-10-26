package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Flux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * Thrown Purifying Salts: where they land they flash white and cleanse everything blighted within {@link #RADIUS} blocks
 * (Blighted Soil back to grass or dirt, Blight Growth, Flux Goo and Withered Leaves gone or restored), take
 * {@link #TAKES} flux out of the chunk and lift the Blighted sickness from animals.
 */
public class PurifyingSalts extends ThrowableItemProjectile {
	// ---- tuning ----
	/** Radius (blocks) cleansed, and the flux taken from the chunk where it lands. */
	public static final int RADIUS = 5, TAKES = 400;
	/** Ticks it may fly. */
	public static final int LIFETIME = 120;

	public PurifyingSalts(EntityType<? extends PurifyingSalts> type, Level level) {
		super(type, level);
	}

	public PurifyingSalts(Level level, LivingEntity owner, ItemStack stack) {
		super(FluxEffects.SALTS_ENTITY, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return FluxEffects.PURIFYING_SALTS;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel && this.tickCount > LIFETIME) {
			this.discard();
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			cleanse(level, BlockPos.containing(hit.getLocation()));
			this.discard();
		}
	}

	/** The cleansing itself, also usable by anything else that wants to purify a spot. */
	public static void cleanse(ServerLevel level, BlockPos centre) {
		int r2 = RADIUS * RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -RADIUS, -RADIUS), centre.offset(RADIUS, RADIUS, RADIUS))) {
			if (pos.distSqr(centre) > r2) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(FluxEffects.BLIGHTED_SOIL)) {
				boolean open = level.getBlockState(pos.above()).isAir() && level.canSeeSky(pos.above());
				level.setBlockAndUpdate(pos, (open ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
			} else if (state.is(FluxEffects.BLIGHT_GROWTH) || state.is(FluxEffects.FLUX_GOO)) {
				level.removeBlock(pos, false);
			} else if (state.is(FluxEffects.WITHERED_LEAVES)) {
				level.setBlockAndUpdate(pos.immutable(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			}
		}
		Flux.take(level, centre, TAKES);
		AABB box = new AABB(centre).inflate(RADIUS);
		for (Animal animal : level.getEntitiesOfClass(Animal.class, box)) {
			animal.removeEffect(FluxEffects.BLIGHTED);
		}
		double x = centre.getX() + 0.5, y = centre.getY() + 0.5, z = centre.getZ() + 0.5;
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 40, RADIUS * 0.4, RADIUS * 0.3, RADIUS * 0.4, 0.05);
		level.playSound(null, centre, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.8F);
		level.playSound(null, centre, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
	}
}
