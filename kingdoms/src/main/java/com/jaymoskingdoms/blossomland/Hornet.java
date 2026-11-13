package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.creatures.Looks;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A paper hornet, out from its nest ({@link HornetNest}).
 * <ul>
 * <li><b>What it does.</b> It hunts round the nest ({@link #RANGE} blocks): bees above all, and the small vermin
 * (silverfish, endermites). It leaves people alone until the nest is touched; then every hornet of that nest comes
 * for whoever did it, and its sting poisons.</li>
 * <li><b>What comes back.</b> A nest near beehives empties them. A nest that is broken is gone, and its hornets do
 * not outlive it long.</li>
 * <li><b>Looked after.</b> Smoke under the nest (a campfire below, as for bees) keeps the hornets calm while the
 * paper is cut from it; the nest is left whole and makes more.</li>
 * </ul>
 */
public class Hornet extends PathfinderMob implements Looks {
	static final double HEALTH = 6.0, DAMAGE = 2.0;
	static final int RANGE = 14, POISON_TICKS = 100, ANGER_TICKS = 600, ORPHAN_TICKS = 1200;

	private @Nullable BlockPos nest;
	private int angerLeft, orphaned;

	public Hornet(EntityType<? extends Hornet> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.setNoGravity(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FLYING_SPEED, 0.7).add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanFloat(true);
		return navigation;
	}

	void setNest(BlockPos nest) {
		this.nest = nest;
	}

	/** Set on whoever touched the nest. */
	void anger(LivingEntity at) {
		this.angerLeft = ANGER_TICKS;
		this.setTarget(at);
	}

	boolean angry() {
		return this.angerLeft > 0;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(2, new Home());
		this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Bee.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Silverfish.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Endermite.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.angerLeft > 0 && --this.angerLeft == 0 && this.getTarget() instanceof Player) {
			this.setTarget(null);
		}
		if (this.tickCount % 100 == 0) {
			// a hornet with no nest does not last
			boolean has = this.nest != null && (!level.isLoaded(this.nest) || level.getBlockState(this.nest).getBlock() instanceof HornetNest);
			this.orphaned = has ? 0 : this.orphaned + 100;
			if (this.orphaned >= ORPHAN_TICKS && !this.hasCustomName()) {
				this.discard();
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), this);
		}
		return hit;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public int look() {
		return 0;
	}

	@Override
	public float fullness() {
		return 0.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.angry() ? SoundEvents.BEE_LOOP_AGGRESSIVE : SoundEvents.BEE_LOOP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BEE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BEE_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.nest != null) {
			output.putLong("nest", this.nest.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long at = input.getLongOr("nest", Long.MIN_VALUE);
		this.nest = at == Long.MIN_VALUE ? null : BlockPos.of(at);
	}

	/** Back toward the nest when it has wandered out of its range. */
	private final class Home extends Goal {
		Home() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return Hornet.this.nest != null && Hornet.this.getTarget() == null && Hornet.this.tickCount % 20 == 0
				&& Hornet.this.blockPosition().distSqr(Hornet.this.nest) > RANGE * RANGE;
		}

		@Override
		public boolean canContinueToUse() {
			return Hornet.this.nest != null && !Hornet.this.getNavigation().isDone() && Hornet.this.blockPosition().distSqr(Hornet.this.nest) > 16.0;
		}

		@Override
		public void start() {
			BlockPos to = Hornet.this.nest;
			Hornet.this.getNavigation().moveTo(to.getX() + 0.5, to.getY() - 1.0, to.getZ() + 0.5, 1.0);
		}
	}
}
