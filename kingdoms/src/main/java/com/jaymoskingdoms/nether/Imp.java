package com.jaymoskingdoms.nether;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The imp: a small flying demon of the Nether, of a piglin-like make, and its zombified kind. It circles whoever it
 * hunts and lunges in from a new side each pass, rakes with its claws up close, and every so often hangs in the air to
 * gather a great fireball over its head for three seconds. The fireball sets trees and everything around where it
 * lands ablaze ({@link ImpFireball}, {@link Imps#blaze}). While the ball throbs, in the last moments of the charge, a
 * blow sets it off in the imp's own hands: the imp dies, and so may the imps beside it.
 *
 * <p>The zombified imp is tougher and slower, and leaves people alone until one of its flight is hurt (as zombified
 * piglins do).
 *
 * <p>Drawn by GeckoLib: model, skins and animations come from kingdoms/tools/gen_imp.py (kingdoms/art/imp.bbmodel,
 * docs/Imp-Model-Spec.md). The looping animation follows what it is doing (fly, walk, idle, death); the server starts
 * the one-off moves (swing, swoop, charge, throw, hurt).
 */
public class Imp extends Monster implements GeoEntity {
	static final double HEALTH = 14.0, DAMAGE = 3.0, FLYING_SPEED = 0.36, FOLLOW_RANGE = 40.0;
	static final double ZOMBIFIED_HEALTH = 22.0, ZOMBIFIED_DAMAGE = 5.0, ZOMBIFIED_FLYING_SPEED = 0.28;
	/** The zombified leave people alone until one of them is hurt. */
	static final boolean ZOMBIFIED_NEUTRAL = true;

	/** Circling: how far out and how high above its target it flies, how fast it goes round (radians a tick), and how long a pass lasts. */
	static final double ORBIT_MIN = 4.5, ORBIT_MAX = 7.5, ORBIT_LOW = 1.5, ORBIT_HIGH = 4.5, ORBIT_TURN = 0.045;
	static final int ORBIT_MIN_TICKS = 25, ORBIT_MAX_TICKS = 60;
	/** The lunge: how much faster than its cruise, how long before it gives a miss up, how far past its last side it comes out (degrees). */
	static final double LUNGE_SPEED = 1.7;
	static final int LUNGE_TICKS = 40;
	static final float LUNGE_EXIT_MIN = 110.0F, LUNGE_EXIT_MAX = 250.0F;
	/** How close its claws reach: hovering beside its target, and at the end of a lunge (beyond touching). */
	static final double SWING_REACH = 2.2, LUNGE_REACH = 0.45;
	static final int SWING_COOLDOWN = 30;
	/** The fireball: the charge (3 s), how long between two, and from how near and far it will throw. */
	static final int CHARGE_TICKS = 60, FIREBALL_MIN_COOLDOWN = 160, FIREBALL_MAX_COOLDOWN = 300, FIRST_FIREBALL = 60;
	static final double FIREBALL_MIN_RANGE = 4.0, FIREBALL_MAX_RANGE = 24.0;
	/** The opening: a blow in the charge's last ticks (the ball throbs, a fuse hisses) sets the ball off. */
	static final int DETONATE_WINDOW = 12;
	/** After the charge, ticks until the ball leaves its hands (still an opening). */
	static final int THROW_RELEASE = 5;
	/** The ball going off in its hands: how far it reaches, what it does at the middle, how long it burns, how many fires it lights. */
	static final double DETONATE_RADIUS = 3.5;
	static final float DETONATE_DAMAGE = 12.0F, DETONATE_BURN_SECONDS = 5.0F;
	static final int DETONATE_FIRES = 6;
	/** Where the ball hangs: above its feet (blocks). */
	static final double BALL_HEIGHT = 1.85;
	/** With no one to hunt: how far it drifts, how often it lands to rest and for how long (ticks), and how fast it sinks to the ground. */
	static final int WANDER = 9, REST_EVERY = 500, REST_MIN = 160, REST_MAX = 420, REST_GROUND_WITHIN = 10;
	static final double REST_SINK = -0.12;
	static final int FLAP_EVERY = 11;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
	static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
	static final RawAnimation SWING = RawAnimation.begin().thenPlay("swing");
	static final RawAnimation SWOOP = RawAnimation.begin().thenPlay("swoop");
	static final RawAnimation CHARGE = RawAnimation.begin().thenPlayAndHold("charge");
	static final RawAnimation THROW = RawAnimation.begin().thenPlay("throw");
	static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");

	/** Above 0: ticks into the charge. Below 0: ticks until the ball leaves its hands. 0: no ball. */
	private static final EntityDataAccessor<Integer> CHARGING = SynchedEntityData.defineId(Imp.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(Imp.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final boolean zombified;
	private int fireballCooldown = FIRST_FIREBALL, swingCooldown, restIn = REST_EVERY, restFor;

	public Imp(EntityType<? extends Imp> type, Level level, boolean zombified) {
		super(type, level);
		this.zombified = zombified;
		this.moveControl = new Wings(this);
		this.setNoGravity(true);
		this.xpReward = zombified ? 8 : 6;
	}

	public static AttributeSupplier.Builder createAttributes(boolean zombified) {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, zombified ? ZOMBIFIED_HEALTH : HEALTH)
			.add(Attributes.ATTACK_DAMAGE, zombified ? ZOMBIFIED_DAMAGE : DAMAGE)
			.add(Attributes.FLYING_SPEED, zombified ? ZOMBIFIED_FLYING_SPEED : FLYING_SPEED).add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	public boolean zombified() {
		return this.zombified;
	}

	/** Above 0: ticks into the charge. Below 0: the ball is about to leave its hands. 0: no ball. */
	public int charging() {
		return this.entityData.get(CHARGING);
	}

	/** (Client) a lore film's imp: how far into its charge it is shown ({@link #charging}; 0 no ball). */
	public void stageCharge(int charge) {
		this.entityData.set(CHARGING, charge);
	}

	/** On the ground with its wings folded. */
	public boolean resting() {
		return this.entityData.get(RESTING);
	}

	/** A blow now would set the ball off. */
	public boolean open() {
		int charge = this.charging();
		return charge < 0 || charge >= CHARGE_TICKS - DETONATE_WINDOW;
	}

	/** Where the ball hangs over its head. */
	public Vec3 ballPosition() {
		return this.position().add(0.0, BALL_HEIGHT, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(CHARGING, 0);
		entityData.define(RESTING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(2, new Harry());
		this.goalSelector.addGoal(8, new Drift());
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		if (!(this.zombified && ZOMBIFIED_NEUTRAL)) {
			this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false,
				(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
			this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
			this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, false));
		}
	}

	// ---------------------------------------------------------------- each tick

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			int charge = this.charging();
			if (charge != 0 && this.isAlive()) {
				Vec3 ball = this.ballPosition();
				double spread = charge < 0 ? 0.35 : 0.1 + 0.25 * charge / CHARGE_TICKS;
				for (int i = 0; i < (this.open() ? 3 : 1); i++) {
					this.level().addParticle(this.open() ? ParticleTypes.LAVA : ParticleTypes.FLAME, ball.x + (this.random.nextDouble() - 0.5) * spread * 2.0,
						ball.y + (this.random.nextDouble() - 0.5) * spread * 2.0, ball.z + (this.random.nextDouble() - 0.5) * spread * 2.0, 0.0, 0.02, 0.0);
				}
			}
			return;
		}
		// (a saved imp comes back with gravity as it was saved: set it from what it is doing)
		this.setNoGravity(!this.resting());
		if (!this.resting() && this.isAlive() && this.level() instanceof net.minecraft.server.level.ServerLevel server) {
			com.jaymoskingdoms.WorldSounds.wingbeat(server, this, "flap", FLAP_EVERY - 3, FLAP_EVERY + 3, this.getSoundSource(), SoundEvents.PHANTOM_FLAP,
				0.25F, 1.65F);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.swingCooldown > 0) {
			this.swingCooldown--;
		}
		if (this.fireballCooldown > 0) {
			this.fireballCooldown--;
		}
		this.rest(level);
	}

	/** With no one to hunt it lands now and then, folds its wings and shuffles about; anything to hunt and it is up again. */
	private void rest(ServerLevel level) {
		boolean hunting = this.getTarget() != null;
		if (this.resting()) {
			if (hunting || --this.restFor <= 0 || this.isInLava() || this.isInWater()) {
				this.entityData.set(RESTING, false);
				this.restIn = REST_EVERY / 2 + this.random.nextInt(REST_EVERY);
				this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.35, 0.0));
			} else if (!this.onGround() && this.getDeltaMovement().y < REST_SINK) {
				// gliding down, not dropping
				Vec3 v = this.getDeltaMovement();
				this.setDeltaMovement(v.x, REST_SINK, v.z);
			}
		} else if (!hunting && --this.restIn <= 0) {
			this.restIn = REST_EVERY / 4;
			if (this.groundBelow(level)) {
				this.entityData.set(RESTING, true);
				this.restFor = REST_MIN + this.random.nextInt(REST_MAX - REST_MIN);
			}
		}
	}

	/** Solid, dry ground close under it. */
	private boolean groundBelow(ServerLevel level) {
		BlockPos.MutableBlockPos pos = this.blockPosition().mutable();
		for (int i = 0; i < REST_GROUND_WITHIN; i++) {
			pos.move(0, -1, 0);
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return false;
			}
			if (state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the fireball

	private void startCharge() {
		this.entityData.set(CHARGING, 1);
		this.triggerAnim("action", "charge");
		this.playSound(SoundEvents.GHAST_WARN, 0.9F, 1.5F);
	}

	private void cancelCharge() {
		if (this.charging() != 0) {
			this.entityData.set(CHARGING, 0);
			this.stopTriggeredAnim("action", "charge");
			this.stopTriggeredAnim("action", "throw");
		}
	}

	/** One tick of the charge; true once the ball has left its hands. */
	private boolean charge(ServerLevel level, LivingEntity target) {
		int charge = this.charging();
		if (charge > 0) {
			charge++;
			if (charge == CHARGE_TICKS - DETONATE_WINDOW) {
				this.playSound(SoundEvents.TNT_PRIMED, 1.0F, 1.6F);
			}
			if (charge >= CHARGE_TICKS) {
				charge = -THROW_RELEASE;
				this.triggerAnim("action", "throw");
			}
			this.entityData.set(CHARGING, charge);
			return false;
		}
		charge++;
		this.entityData.set(CHARGING, charge);
		if (charge < 0) {
			return false;
		}
		Vec3 from = this.ballPosition();
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(from).normalize();
		ImpFireball ball = new ImpFireball(level, this, aim);
		ball.setPos(from.x + aim.x * 0.6, from.y - 0.4, from.z + aim.z * 0.6);
		level.addFreshEntity(ball);
		this.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
		this.fireballCooldown = FIREBALL_MIN_COOLDOWN + this.random.nextInt(FIREBALL_MAX_COOLDOWN - FIREBALL_MIN_COOLDOWN);
		return true;
	}

	/** The ball goes off in its hands: everything near burns, the imps beside it too (never {@code spared}, who struck the blow). */
	private void detonate(ServerLevel level, Entity spared) {
		this.entityData.set(CHARGING, 0);
		Vec3 at = this.ballPosition();
		DamageSource blast = level.damageSources().explosion(this, spared);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(DETONATE_RADIUS),
			e -> e.isAlive() && e != this && e != spared && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double d = e.position().distanceTo(at);
			if (d <= DETONATE_RADIUS + 0.5 && e.hurtServer(level, blast, DETONATE_DAMAGE * (float) Math.max(0.3, 1.0 - d / (DETONATE_RADIUS + 0.5)))) {
				e.igniteForSeconds(DETONATE_BURN_SECONDS);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 50, 0.6, 0.6, 0.6, 0.12);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 1.3F);
		Imps.blaze(level, BlockPos.containing(at), Imps.DETONATE_BLAZE_RADIUS, DETONATE_FIRES);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isAlive() && this.charging() != 0 && !this.isInvulnerableTo(level, source)) {
			// a blow while the ball throbs, or any blast while it holds a ball at all
			boolean struck = this.open() && source.getEntity() instanceof LivingEntity by && !(by instanceof Imp);
			if (struck || source.is(DamageTypeTags.IS_EXPLOSION)) {
				this.detonate(level, struck ? source.getEntity() : null);
				return super.hurtServer(level, source, Math.max(amount, this.getMaxHealth() * 10.0F));
			}
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive() && this.charging() == 0) {
			this.triggerAnim("action", "hurt");
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		this.cancelCharge();
		super.die(source);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && !this.zombified) {
			target.igniteForSeconds(2.0F);
		}
		return hit;
	}

	// ---------------------------------------------------------------- flying

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	/** Flies (or, resting, walks) straight at where it wants to be, easing its speed toward the one asked for. */
	private static final class Wings extends MoveControl<Imp> {
		Wings(Imp imp) {
			super(imp);
		}

		@Override
		public void tick() {
			Imp imp = this.mob;
			Vec3 v = imp.getDeltaMovement();
			LivingEntity target = imp.getTarget();
			if (this.operation != MoveControl.Operation.MOVE_TO) {
				if (!imp.resting()) {
					imp.setDeltaMovement(v.scale(0.8));
				}
				this.face(target != null ? target.position().subtract(imp.position()) : null);
				return;
			}
			Vec3 delta = new Vec3(this.wantedX - imp.getX(), this.wantedY - imp.getY(), this.wantedZ - imp.getZ());
			if (imp.resting()) {
				delta = new Vec3(delta.x, 0.0, delta.z);
			}
			double length = delta.length();
			if (length < 0.4) {
				this.operation = MoveControl.Operation.WAIT;
				return;
			}
			double speed = Math.min(length, this.speedModifier * imp.getAttributeValue(Attributes.FLYING_SPEED));
			Vec3 want = delta.scale(speed / length);
			if (imp.resting()) {
				// on foot: half as fast, hopping up what it walks into
				double up = imp.onGround() && imp.horizontalCollision ? 0.42 : v.y;
				imp.setDeltaMovement(v.x + (want.x * 0.5 - v.x) * 0.3, up, v.z + (want.z * 0.5 - v.z) * 0.3);
			} else {
				imp.setDeltaMovement(v.add(want.subtract(v).scale(0.25)));
			}
			this.face(target != null ? target.position().subtract(imp.position()) : delta);
		}

		private void face(Vec3 toward) {
			if (toward == null || toward.horizontalDistanceSqr() < 1.0E-4) {
				return;
			}
			float yaw = -((float) Mth.atan2(toward.x, toward.z)) * Mth.RAD_TO_DEG;
			this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 25.0F));
			this.mob.yBodyRot = this.mob.getYRot();
		}
	}

	/** The hunt: circle, lunge in from a new side, rake up close, and now and then hang back to gather a fireball. */
	private final class Harry extends Goal {
		private enum Phase { ORBIT, LUNGE, CHARGE }

		private Phase phase = Phase.ORBIT;
		private int ticks;
		private double angle, radius, height;
		private int turn = 1;

		Harry() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Imp.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void start() {
			LivingEntity target = Imp.this.getTarget();
			Vec3 from = target == null ? Vec3.ZERO : Imp.this.position().subtract(target.position());
			this.orbit((float) (Mth.atan2(from.z, from.x) * Mth.RAD_TO_DEG));
		}

		@Override
		public void stop() {
			Imp.this.cancelCharge();
			Imp.this.getMoveControl().setWait();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		/** A new pass, starting on the side at {@code degrees} round its target. */
		private void orbit(float degrees) {
			this.phase = Phase.ORBIT;
			this.angle = degrees * Mth.DEG_TO_RAD;
			this.radius = ORBIT_MIN + Imp.this.random.nextDouble() * (ORBIT_MAX - ORBIT_MIN);
			this.height = ORBIT_LOW + Imp.this.random.nextDouble() * (ORBIT_HIGH - ORBIT_LOW);
			this.turn = Imp.this.random.nextBoolean() ? 1 : -1;
			this.ticks = ORBIT_MIN_TICKS + Imp.this.random.nextInt(ORBIT_MAX_TICKS - ORBIT_MIN_TICKS);
		}

		@Override
		public void tick() {
			LivingEntity target = Imp.this.getTarget();
			if (target == null || !(Imp.this.level() instanceof ServerLevel level)) {
				return;
			}
			Imp.this.getLookControl().setLookAt(target);
			Vec3 chest = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			double distance = Imp.this.position().distanceTo(target.position());
			switch (this.phase) {
				case ORBIT -> {
					this.angle += ORBIT_TURN * this.turn;
					Imp.this.moveControl.setWantedPosition(chest.x + Math.cos(this.angle) * this.radius, chest.y + this.height,
						chest.z + Math.sin(this.angle) * this.radius, 1.0);
					if (Imp.this.horizontalCollision) {
						// a wall in the way: over it, and round the other way
						this.height = Math.min(ORBIT_HIGH + 2.0, this.height + 0.5);
						this.turn = -this.turn;
					}
					if (distance < SWING_REACH && Imp.this.swingCooldown == 0) {
						Imp.this.swingCooldown = SWING_COOLDOWN;
						Imp.this.triggerAnim("action", "swing");
						Imp.this.doHurtTarget(level, target);
					}
					if (--this.ticks <= 0) {
						if (Imp.this.fireballCooldown == 0 && distance >= FIREBALL_MIN_RANGE && distance <= FIREBALL_MAX_RANGE
							&& Imp.this.hasLineOfSight(target)) {
							this.phase = Phase.CHARGE;
							Imp.this.startCharge();
						} else {
							this.phase = Phase.LUNGE;
							this.ticks = LUNGE_TICKS;
							Imp.this.triggerAnim("action", "swoop");
							Imp.this.playSound(Imp.this.zombified ? SoundEvents.ZOMBIFIED_PIGLIN_ANGRY : SoundEvents.PIGLIN_ANGRY, 0.8F, 1.5F);
						}
					}
				}
				case LUNGE -> {
					Imp.this.moveControl.setWantedPosition(chest.x, chest.y - Imp.this.getBbHeight() * 0.4, chest.z, LUNGE_SPEED);
					boolean reached = Imp.this.getBoundingBox().inflate(LUNGE_REACH).intersects(target.getBoundingBox());
					if (reached) {
						Imp.this.swingCooldown = SWING_COOLDOWN;
						Imp.this.doHurtTarget(level, target);
					}
					if (reached || --this.ticks <= 0) {
						// out the far side, somewhere new
						Vec3 from = Imp.this.position().subtract(target.position());
						float here = (float) (Mth.atan2(from.z, from.x) * Mth.RAD_TO_DEG);
						this.orbit(here + LUNGE_EXIT_MIN + Imp.this.random.nextFloat() * (LUNGE_EXIT_MAX - LUNGE_EXIT_MIN));
					}
				}
				case CHARGE -> {
					Imp.this.getMoveControl().setWait();
					if (Imp.this.charging() == 0 || Imp.this.charge(level, target)) {
						Vec3 from = Imp.this.position().subtract(target.position());
						this.orbit((float) (Mth.atan2(from.z, from.x) * Mth.RAD_TO_DEG));
					}
				}
			}
		}
	}

	/** With no one to hunt: drifts about where it is (shuffles, when resting). */
	private final class Drift extends Goal {
		Drift() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return Imp.this.getTarget() == null && !Imp.this.getMoveControl().hasWanted()
				&& Imp.this.random.nextInt(reducedTickDelay(Imp.this.resting() ? 60 : 12)) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			BlockPos home = Imp.this.blockPosition();
			for (int tries = 0; tries < 4; tries++) {
				BlockPos to = home.offset(Imp.this.random.nextInt(WANDER * 2 + 1) - WANDER, Imp.this.resting() ? 0 : Imp.this.random.nextInt(7) - 3,
					Imp.this.random.nextInt(WANDER * 2 + 1) - WANDER);
				if (Imp.this.level().isEmptyBlock(to) && Imp.this.level().getFluidState(to.below()).isEmpty()) {
					Imp.this.moveControl.setWantedPosition(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, 0.45);
					break;
				}
			}
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Imp>("move", 4, test -> {
			Imp imp = test.animatable();
			if (imp.isDeadOrDying()) {
				return test.setAndContinue(DEATH);
			}
			if (!imp.resting() || !imp.onGround()) {
				return test.setAndContinue(FLY);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		controllers.add(new AnimationController<Imp>("action", 2, test -> PlayState.STOP).triggerableAnim("swing", SWING)
			.triggerableAnim("swoop", SWOOP).triggerableAnim("charge", CHARGE).triggerableAnim("throw", THROW).triggerableAnim("hurt", HURT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	// ---------------------------------------------------------------- sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return this.zombified ? SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT : SoundEvents.PIGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.zombified ? SoundEvents.ZOMBIFIED_PIGLIN_HURT : SoundEvents.PIGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.zombified ? SoundEvents.ZOMBIFIED_PIGLIN_DEATH : SoundEvents.PIGLIN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.5F + this.random.nextFloat() * 0.2F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.7F;
	}
}
