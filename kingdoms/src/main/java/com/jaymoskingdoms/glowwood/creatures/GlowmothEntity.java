package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.glowwood.GlowWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A soft glowing moth. It flutters from perch to perch and is drawn to light: torches, lanterns and above all
 * glowcaps. Harmless; drops Lumen Spores.
 */
public class GlowmothEntity extends Mob {
	@Nullable
	private BlockPos target;
	private int retarget;

	public GlowmothEntity(EntityType<? extends GlowmothEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0);
	}

	/** Spawns in the dark (caves, night under the canopy) on solid ground or in the air above it. */
	public static boolean checkSpawnRules(EntityType<GlowmothEntity> type, LevelAccessor level, EntitySpawnReason spawnType,
										  BlockPos pos, RandomSource random) {
		return level.getMaxLocalRawBrightness(pos) <= 7 && random.nextInt(3) == 0
				&& Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (target == null || --retarget <= 0 || this.distanceToSqr(Vec3.atCenterOf(target)) < 1.5
				|| !this.level().isEmptyBlock(target)) {
			target = pickTarget();
			retarget = 60 + this.random.nextInt(80);
		}
		if (target == null) {
			return;
		}
		double dx = target.getX() + 0.5 - this.getX();
		double dy = target.getY() + 0.3 - this.getY();
		double dz = target.getZ() + 0.5 - this.getZ();
		Vec3 v = this.getDeltaMovement();
		Vec3 steer = v.add((Math.signum(dx) * 0.3 - v.x) * 0.08, (Math.signum(dy) * 0.4 - v.y) * 0.08,
				(Math.signum(dz) * 0.3 - v.z) * 0.08);
		this.setDeltaMovement(steer);
		float yaw = (float) (Mth.atan2(steer.z, steer.x) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(this.getYRot() + Mth.wrapDegrees(yaw - this.getYRot()) * 0.25F);
		this.zza = 0.4F;
	}

	/** A spot near a light source when there is one in reach (glowcaps first), otherwise somewhere close by. */
	@Nullable
	private BlockPos pickTarget() {
		BlockPos origin = this.blockPosition();
		BlockPos best = null;
		int bestScore = 0;
		for (int i = 0; i < 16; i++) {
			BlockPos probe = origin.offset(this.random.nextInt(21) - 10, this.random.nextInt(9) - 4, this.random.nextInt(21) - 10);
			BlockState state = this.level().getBlockState(probe);
			int score = this.level().getBrightness(LightLayer.BLOCK, probe);
			if (state.is(GlowWoods.GLOWCAP) || state.is(GlowWoods.GLOWCAP_CAP) || state.is(GlowWoods.GLOWCAP_GILLS)
					|| state.is(GlowWoods.GLOWCAP_LANTERN)) {
				score += 10;
			}
			if (score > bestScore && score >= 10) {
				bestScore = score;
				best = probe;
			}
		}
		if (best != null) {
			BlockPos near = best.offset(this.random.nextInt(3) - 1, 1 + this.random.nextInt(2), this.random.nextInt(3) - 1);
			if (this.level().isEmptyBlock(near)) {
				return near;
			}
		}
		for (int i = 0; i < 8; i++) {
			BlockPos pos = origin.offset(this.random.nextInt(11) - 5, this.random.nextInt(5) - 2, this.random.nextInt(11) - 5);
			if (this.level().isEmptyBlock(pos) && pos.getY() > this.level().getMinY()) {
				return pos;
			}
		}
		return null;
	}

	@Override
	public void tick() {
		super.tick();
		this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
		if (this.level().isClientSide() && this.random.nextInt(14) == 0) {
			this.level().addParticle(GlowFx.glowSpore(), this.getX(), this.getY() + 0.15, this.getZ(),
					0.0, -0.01, 0.0);
		}
	}

	/** Ambient like a bat: never leashed. */
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return GlowSounds.GLOWMOTH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GlowSounds.GLOWMOTH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GlowSounds.GLOWMOTH_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}
}
