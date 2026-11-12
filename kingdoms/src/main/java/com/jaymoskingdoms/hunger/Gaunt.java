package com.jaymoskingdoms.hunger;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.monsters.LegendMonster;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Gaunt: a tall, starved thing with antlers that hunts the snowy woods at night and walks in toward lit campfires and
 * the people sitting at them. It comes up slowly, hunched, keeping to cover while it is watched, and only runs once it is
 * close, hurt, or has been following long enough; its bite may carry the curse of the hungry ({@link HungerCurse}). It
 * does not fear fire (see {@code CampfireWard}), and when day comes it thins to mist and is gone. A monster of legend
 * ({@link com.jaymoskingdoms.monsters.Monsters#legends}), so it gets a great foe's entrance and a line in the journal but
 * never counts toward a world tier. Its heart ({@link Hunger#HEART}) is what it leaves.
 */
public class Gaunt extends LegendMonster {
	// ---- tuning ----
	static final double HEALTH = 70.0, SPEED = 0.27, DAMAGE = 9.0, KNOCKBACK_RESISTANCE = 0.5, ARMOUR = 4.0, FOLLOW = 48.0;
	/** Of its bites that land on a player, the share that carry the curse. */
	static final float CURSE_CHANCE = 0.35F;
	/** How near a lit campfire (blocks) a player is drawn to, how far off it smells the fire, how near any player is noticed, and how far it looks for a fire. */
	static final double CAMP_RADIUS = 10.0, NOTICE_RANGE = 14.0, FIRE_RANGE = 48.0;
	/** How near (blocks) the one it stalks must be for it to rush, and how long (ticks) it will stalk before it rushes regardless. */
	static final double RUSH_RANGE = 9.0;
	static final int STALK_TICKS = 400;
	/** Its pace creeping while watched, creeping unseen, and rushing (a share of its speed). */
	static final double CREEP = 0.45, STEALTH = 0.85, RUSH = 1.5;
	/** How near the fire it stays when it has no one to hunt, and how often (ticks) it looks for one. */
	static final double LOITER = 12.0;
	static final int FIRE_LOOK = 40;
	/** Ticks between its blows. */
	static final int BLOW_EVERY = 24;
	/** How long (ticks) it takes to fade once day has come. */
	static final int FADE_TICKS = 100;
	/** The colour of its mist. */
	static final int MIST = 0xB8C4CC;

	/** Whether it is creeping up on someone (it shows hunched). */
	private static final EntityDataAccessor<Boolean> STALKING = SynchedEntityData.defineId(Gaunt.class, EntityDataSerializers.BOOLEAN);

	private int fading;
	private int stalked;
	private boolean rushing;
	private @Nullable BlockPos fire;

	public Gaunt(EntityType<? extends Gaunt> type, Level level) {
		super(type, level);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.ARMOR, ARMOUR).add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(STALKING, false);
	}

	/** Whether it is creeping up on someone. */
	public boolean stalking() {
		return this.entityData.get(STALKING);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new StalkGoal());
		this.goalSelector.addGoal(4, new FireGoal());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, (target, level) -> this.drawnTo(target)));
	}

	/** A player it goes for: at night, close by, or sat at a lit campfire it can smell. */
	private boolean drawnTo(LivingEntity target) {
		if (!(target instanceof Player player) || player.isCreative() || player.isSpectator() || this.level().isBrightOutside()) {
			return false;
		}
		return this.distanceToSqr(player) <= NOTICE_RANGE * NOTICE_RANGE || litFireNear(this.level(), player.blockPosition(), CAMP_RADIUS) != null;
	}

	/** The nearest lit campfire within {@code radius} blocks (across, and a few up or down) of {@code around}, or null. */
	static @Nullable BlockPos litFireNear(Level level, BlockPos around, double radius) {
		int r = (int)Math.ceil(radius);
		BlockPos best = null;
		double bestD = radius * radius;
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-r, -4, -r), around.offset(r, 4, r))) {
			double d = pos.distSqr(around);
			if (d <= bestD && level.getBlockState(pos).getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(level.getBlockState(pos))) {
				best = pos.immutable();
				bestD = d;
			}
		}
		return best;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		boolean stalking = this.getTarget() != null && !this.rushing && this.isAlive();
		if (stalking != this.stalking()) {
			this.entityData.set(STALKING, stalking);
		}
		if (this.getTarget() == null) {
			this.rushing = false;
			this.stalked = 0;
		}
		// day comes: it thins to mist and is gone
		if (level.isBrightOutside() && level.canSeeSky(this.blockPosition())) {
			this.setTarget(null);
			this.getNavigation().stop();
			if (this.fading == 0) {
				level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
			}
			this.fading++;
			level.sendParticles(Fx.miasma(MIST, 0.9F), this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 3, this.getBbWidth() * 0.5,
				this.getBbHeight() * 0.4, this.getBbWidth() * 0.5, 0.0);
			if (this.fading >= FADE_TICKS) {
				this.discard();
			}
		} else if (this.fading > 0) {
			this.fading = 0;
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof ServerPlayer player && this.random.nextFloat() < CURSE_CHANCE) {
			HungerCurse.curse(player);
		}
		return hit;
	}

	@Override
	protected int nextArm() {
		return BOTH;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WARDEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.WARDEN_STEP, 0.6F, 0.8F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("rushing", this.rushing);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.rushing = input.getBooleanOr("rushing", false);
	}

	/**
	 * Creeps up on its target, slowly while it is watched and a little quicker unseen, then rushes once it is close, has
	 * been hurt, or has followed long enough; strikes with both arms in reach.
	 */
	private final class StalkGoal extends Goal {
		private int cooldown;

		StalkGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Gaunt.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = Gaunt.this.getTarget();
			if (target == null) {
				return;
			}
			Gaunt gaunt = Gaunt.this;
			gaunt.getLookControl().setLookAt(target, 30.0F, 30.0F);
			double distance = gaunt.distanceTo(target);
			gaunt.stalked++;
			if (!gaunt.rushing && (distance <= RUSH_RANGE || gaunt.stalked > STALK_TICKS || gaunt.getLastHurtByMobTimestamp() > gaunt.tickCount - 40)) {
				gaunt.rushing = true;
				gaunt.playSound(SoundEvents.WARDEN_ROAR, 1.6F, 0.8F);
			}
			double pace = gaunt.rushing ? RUSH : watched(target) ? CREEP : STEALTH;
			gaunt.getNavigation().moveTo(target, pace);
			if (this.cooldown > 0) {
				this.cooldown--;
			}
			if (gaunt.rushing && this.cooldown <= 0 && gaunt.isWithinMeleeAttackRange(target) && gaunt.level() instanceof ServerLevel level) {
				this.cooldown = BLOW_EVERY;
				gaunt.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
				gaunt.doHurtTarget(level, target);
			}
		}

		/** Whether {@code target} is looking toward the Gaunt, with it in plain sight. */
		private boolean watched(LivingEntity target) {
			Vec3 toward = Gaunt.this.position().subtract(target.position()).normalize();
			return target.getViewVector(1.0F).dot(toward) > 0.7 && target.hasLineOfSight(Gaunt.this);
		}
	}

	/** With no one to hunt, it walks in toward a lit campfire at night and lingers near it, watching the light. */
	private final class FireGoal extends Goal {
		private int look;

		FireGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Gaunt gaunt = Gaunt.this;
			if (gaunt.getTarget() != null || gaunt.level().isBrightOutside()) {
				return false;
			}
			if (gaunt.fire != null && !CampfireBlock.isLitCampfire(gaunt.level().getBlockState(gaunt.fire))) {
				gaunt.fire = null;
			}
			if (gaunt.fire == null && gaunt.tickCount % FIRE_LOOK == 0) {
				gaunt.fire = litFireNear(gaunt.level(), gaunt.blockPosition(), FIRE_RANGE);
			}
			return gaunt.fire != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public void tick() {
			Gaunt gaunt = Gaunt.this;
			BlockPos fire = gaunt.fire;
			if (fire == null) {
				return;
			}
			gaunt.getLookControl().setLookAt(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5);
			if (gaunt.distanceToSqr(Vec3.atCenterOf(fire)) > LOITER * LOITER && --this.look <= 0) {
				this.look = 10;
				gaunt.getNavigation().moveTo(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5, STEALTH);
			}
		}
	}
}
