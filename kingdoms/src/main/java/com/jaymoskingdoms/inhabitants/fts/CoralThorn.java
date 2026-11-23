package com.jaymoskingdoms.inhabitants.fts;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A thorn off a {@link Bulldrogioth}'s shell (the original's coral thorn; From the Shadows Reborn, Sonmok14 and L_Ender,
 * AGPL v3; credit in licenses/from-the-shadows/CREDITS-nether-beasts.md). It flies at a foe, hurts it, and breaks where it
 * lands. Drawn as a thrown pointed stone, not as the original's model.
 */
public class CoralThorn extends ThrowableItemProjectile {
	/** What it does to what it hits: half the beast's blow of 15 (half hearts), as in the original. */
	static final float DAMAGE = 7.5F;

	public CoralThorn(EntityType<? extends CoralThorn> type, Level level) {
		super(type, level);
	}

	CoralThorn(Level level, LivingEntity thrower) {
		super(NetherBeasts.CORAL_THORN, thrower, level, new ItemStack(Items.POINTED_DRIPSTONE));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.POINTED_DRIPSTONE;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && !(target instanceof NetherBeast)) {
			Entity owner = this.getOwner();
			target.hurtServer(level, owner instanceof LivingEntity living ? this.damageSources().mobProjectile(this, living)
				: this.damageSources().thrown(this, owner), DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRIMSON_NYLIUM.defaultBlockState()), this.getX(), this.getY(),
				this.getZ(), 8, 0.1, 0.1, 0.1, 0.05);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 0.8F, 1.1F);
			this.discard();
		}
	}
}
