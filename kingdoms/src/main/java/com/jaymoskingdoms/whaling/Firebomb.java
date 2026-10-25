package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.Theft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.206) A firebomb: a bottle of whale oil stopped with a burning rag, thrown. It shatters where it lands into a pool
 * of burning oil ({@link #SPLASH} blocks round): fire on whatever will hold it, and everyone in the splash set alight and
 * scalded. On water the oil burns on the surface for a moment, setting alight whoever's in a boat in it (a swimmer ducks under). The powder
 * firebomb (gunpowder packed in with the oil) bursts as well ({@link #BURST}), throwing the burning oil wider, though it
 * leaves blocks whole as Dynamite does. Thrown by a player where a faction's law holds, it's arson ({@code Theft}).
 */
public final class Firebomb extends ThrowableItemProjectile {
	/** How far the burning oil splashes (blocks), and with the powder in it; the chance each spot in it catches. */
	static final double SPLASH = 2.0, POWDER_SPLASH = 3.0;
	static final float CATCH = 0.7F;
	/** What the splash does to those in it: seconds alight, and the scald. */
	static final float ALIGHT = 6.0F, SCALD = 3.0F;
	/** The powder firebomb's burst. */
	static final float BURST = 2.0F;

	public Firebomb(EntityType<? extends Firebomb> type, Level level) {
		super(type, level);
	}

	public Firebomb(ServerLevel level, LivingEntity owner, ItemStack stack) {
		super(Whaling.FIREBOMB, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Whaling.FIREBOMB_ITEM;
	}

	boolean powder() {
		return this.getItem().is(Whaling.POWDER_FIREBOMB_ITEM);
	}

	/** Its rag burns as it flies. */
	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.15, this.getZ(), 0.0, 0.0, 0.0);
			if (this.random.nextInt(3) == 0) {
				this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.15, this.getZ(), 0.0, 0.02, 0.0);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			this.burst(level, hit.getLocation());
			this.discard();
		}
	}

	private void burst(ServerLevel level, Vec3 at) {
		boolean powder = this.powder();
		double splash = powder ? POWDER_SPLASH : SPLASH;
		BlockPos centre = BlockPos.containing(at);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.0F, 0.9F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.2F, 0.7F);
		if (powder) {
			level.explode(this, at.x, at.y, at.z, BURST, Level.ExplosionInteraction.NONE);
		}
		int r = (int) Math.ceil(splash);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > splash * splash) {
					continue;
				}
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos p = centre.offset(dx, dy, dz);
					if (level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir()) {
						// oil burning on the water
						level.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 4, 0.35, 0.05, 0.35, 0.01);
						level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.01);
						break;
					}
					if (BaseFireBlock.canBePlacedAt(level, p, Direction.UP)) {
						if (this.random.nextFloat() < CATCH) {
							level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
						}
						break;
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, powder ? 40 : 24, splash * 0.4, 0.2, splash * 0.4, 0.04);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(splash + 0.5, 1.5, splash + 0.5),
				e -> e.isAlive() && e.distanceToSqr(at) <= (splash + 0.5) * (splash + 0.5))) {
			if (e.isInWater()) {
				continue;
			}
			e.igniteForSeconds(ALIGHT);
			e.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), SCALD);
		}
		if (this.getOwner() instanceof ServerPlayer player) {
			Theft.firebombed(player, level, centre);
		}
	}
}
