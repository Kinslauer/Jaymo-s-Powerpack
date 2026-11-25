package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.Fx;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A wicked pumpkin: one of the Harvest Lich's flying lanterns. It floats round whoever the lich fights, a few blocks up, and
 * spits {@link WickedOrb}s at them. It belongs to the lich that called it and is gone when that lich is (it won't outlast
 * the harvest that raised it).
 */
public final class WickedPumpkin extends PathfinderMob implements Enemy {
	// ---- tuning ----
	static final double HEALTH = 20.0, FLY_SPEED = 0.6;
	/** How far (blocks) it circles its target, how high above them, and how far it shoots. */
	static final double CIRCLE = 7.0, ABOVE = 3.5, SHOOT_RANGE = 24.0;
	/** Ticks between its shots (least, most). */
	static final int SHOT_MIN = 50, SHOT_MAX = 90;

	private @Nullable UUID lichId;

	public WickedPumpkin(EntityType<? extends WickedPumpkin> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 10, true);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FLYING_SPEED, FLY_SPEED)
			.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		return nav;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new CircleAndShoot());
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new InheritTarget());
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void setLich(HarvestLich lich) {
		this.lichId = lich.getUUID();
	}

	@Nullable HarvestLich lich(ServerLevel level) {
		return this.lichId != null && level.getEntity(this.lichId) instanceof HarvestLich lich && lich.isAlive() ? lich : null;
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 20 == 0 && this.lichId != null && this.lich(level) == null) {
			Fx.blast(level, this.position().add(0.0, 0.4, 0.0), 0.8F, WickedHarvest.FIRE);
			this.discard();
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.VEX_AMBIENT;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PUMPKIN_CARVE;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return this.lichId == null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.lichId != null) {
			output.putString("Lich", this.lichId.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lichId = input.getString("Lich").map(s -> {
			try {
				return UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}

	/** Takes up its lich's fight. */
	private final class InheritTarget extends Goal {
		InheritTarget() {
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (!(WickedPumpkin.this.level() instanceof ServerLevel level) || WickedPumpkin.this.getTarget() != null) {
				return false;
			}
			HarvestLich lich = WickedPumpkin.this.lich(level);
			return lich != null && lich.getTarget() != null && lich.getTarget().isAlive();
		}

		@Override
		public void start() {
			if (WickedPumpkin.this.level() instanceof ServerLevel level && WickedPumpkin.this.lich(level) != null) {
				WickedPumpkin.this.setTarget(WickedPumpkin.this.lich(level).getTarget());
			}
		}
	}

	/** Round its target, up, and spits now and then. */
	private final class CircleAndShoot extends Goal {
		private int shot;
		private double angle;

		CircleAndShoot() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = WickedPumpkin.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void start() {
			this.angle = WickedPumpkin.this.getRandom().nextDouble() * Math.PI * 2.0;
			this.shot = WickedPumpkin.this.tickCount + SHOT_MIN;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = WickedPumpkin.this.getTarget();
			if (target == null) {
				return;
			}
			this.angle += 0.03;
			Vec3 goal = target.position().add(Math.cos(this.angle) * CIRCLE, ABOVE + target.getBbHeight(), Math.sin(this.angle) * CIRCLE);
			WickedPumpkin.this.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 1.0);
			WickedPumpkin.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (WickedPumpkin.this.tickCount >= this.shot && WickedPumpkin.this.distanceToSqr(target) < SHOOT_RANGE * SHOOT_RANGE
				&& WickedPumpkin.this.hasLineOfSight(target) && WickedPumpkin.this.level() instanceof ServerLevel level) {
				Vec3 from = WickedPumpkin.this.getEyePosition();
				Vec3 at = target.getEyePosition().subtract(from);
				WickedOrb.shoot(level, WickedPumpkin.this, from, at);
				level.playSound(null, WickedPumpkin.this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8F, 0.5F);
				this.shot = WickedPumpkin.this.tickCount + SHOT_MIN + WickedPumpkin.this.getRandom().nextInt(SHOT_MAX - SHOT_MIN);
			}
		}
	}
}
