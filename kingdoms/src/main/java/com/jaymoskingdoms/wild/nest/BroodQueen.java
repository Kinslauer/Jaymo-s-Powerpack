package com.jaymoskingdoms.wild.nest;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Hearing;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.wild.SilkShots;
import com.jaymoskingdoms.wild.SpiderKinds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, de-broodqueen) The brood queen: a spider grown huge on the cave's life, asleep over her egg in the deepest
 * chamber of a spider nest ({@link BroodEggBlock}; she can't be harmed asleep). Strike the egg three times and she wakes
 * (her entrance plays, {@code BossIntros}, and her bar shows, {@link BossBars}); then:
 * <ul>
 * <li><b>Leap</b> at prey well off, coming down with a blast that throws everyone near off their feet ({@link #LAND_RADIUS}).</li>
 * <li><b>Leg slams</b>: with prey close she rears and slams her forelegs down twice, and a shockwave runs out over the
 * ground from each ({@link #WAVE_RANGE}); jump it.</li>
 * <li><b>Web spit</b> at prey out of reach: a glob of silk that wraps whoever it meets in two layers ({@link SilkShots}).</li>
 * <li><b>Fang grab</b>: she seizes prey right in front of her in her fangs and holds them up. Struggle (fresh presses of the
 * walking keys and jump, or blows on her: {@link #STRUGGLE_NEEDED}) and she drops them; held out the hold
 * ({@link #HOLD_TICKS}) and she bites, poisoning them.</li>
 * <li><b>Spiders off her back</b>: her brood spills off her as she fights, and at three quarters, half and a quarter health
 * ({@link SpiderKinds#hatch}).</li>
 * <li><b>Grows</b>: the more she's hurt, the bigger and faster she gets ({@link #GROW}, {@link #FASTER}), and the quicker her
 * moves come.</li>
 * </ul>
 * She keeps to her nest ({@link #LEASH}): with nobody to fight she goes home, and once her egg has grown back (an hour) she
 * settles on it to sleep again, whole. Fire hurts her more ({@link #FIRE_HARM}). Our own name and model
 * (kingdoms/tools/gen_spider_nests.py); Derelict's arachne (mim1q, MIT) was the reference for what such a boss does, none of
 * its code or art is used.
 */
public class BroodQueen extends Monster implements GeoEntity {
	// ---- tuning ----
	static final double HEALTH = 220.0, ARMOUR = 8.0, SPEED = 0.26, DAMAGE = 7.0, FOLLOW = 40.0;
	/** Hurt to nothing she'd be this much bigger and faster (times); her moves wind up this much quicker. */
	static final double GROW = 0.45, FASTER = 0.4, QUICKER = 0.35;
	/** She keeps within this of her egg (blocks), and settles to sleep after this long idle near it (ticks). */
	static final double LEASH = 32.0, HOME_NEAR = 6.0;
	static final int SETTLE_IDLE = 600;
	/** Waking takes this long (ticks): she can't be harmed and doesn't strike. */
	static final int WAKE_TICKS = 40;
	/** Between one move and the next (ticks). */
	static final int REST = 30;
	/** The leap: at prey between LEAP_MIN and LEAP_MAX (blocks), wound up LEAP_WINDUP ticks, once per LEAP_COOLDOWN; up this fast. */
	static final double LEAP_MIN = 7.0, LEAP_MAX = 18.0, LEAP_UP = 0.85;
	static final int LEAP_WINDUP = 14, LEAP_COOLDOWN = 180, LEAP_AIR_MOST = 60;
	/** The landing blast: how wide (blocks), how hard at its heart, how far it throws. */
	static final double LAND_RADIUS = 5.0, LAND_PUSH = 1.1;
	static final float LAND_DAMAGE = 9.0F;
	/** Leg slams: with prey within SLAM_REACH, wound up SLAM_WINDUP ticks, the second SLAM_SECOND after; each wave runs out to
	 * WAVE_RANGE at WAVE_SPEED blocks a tick, hitting what's on the ground within WAVE_WIDTH of its front. */
	static final double SLAM_REACH = 7.0, WAVE_RANGE = 11.0, WAVE_SPEED = 0.55, WAVE_WIDTH = 0.9;
	static final int SLAM_WINDUP = 18, SLAM_SECOND = 12, SLAM_COOLDOWN = 130;
	static final float WAVE_DAMAGE = 6.0F;
	/** Web spit: at prey between SPIT_MIN and SPIT_RANGE, wound up SPIT_WINDUP; SPIT_LAYERS layers holding SPIT_HOLD. */
	static final double SPIT_MIN = 5.0, SPIT_RANGE = 22.0, SPIT_SPEED = 0.9, SPIT_CATCH = 1.0;
	static final int SPIT_WINDUP = 12, SPIT_COOLDOWN = 100, SPIT_LAYERS = 2, SPIT_HOLD = 100;
	/** The fang grab: prey within GRAB_REACH, a lunge of GRAB_WINDUP ticks; held HOLD_TICKS unless they struggle STRUGGLE_NEEDED
	 * times (a blow on her counts STRUGGLE_BLOW); then the bite and its poison. */
	static final double GRAB_REACH = 3.4;
	static final int GRAB_WINDUP = 8, GRAB_COOLDOWN = 240, HOLD_TICKS = 70, STRUGGLE_NEEDED = 10, STRUGGLE_BLOW = 2, POISON_TICKS = 160,
		POISON_LEVEL = 1;
	static final float BITE_DAMAGE = 6.0F, MOB_STRUGGLE = 0.12F;
	/** Her brood: every BROOD_COOLDOWN ticks in a fight (and at these shares of her health), BROOD_LEAST-BROOD_MOST spiderlings,
	 * unless BROOD_CAP are about her already. */
	static final int BROOD_COOLDOWN = 400, BROOD_LEAST = 3, BROOD_MOST = 5, BROOD_CAP = 10;
	static final double BROOD_NEAR = 24.0;
	static final float[] BROOD_AT = {0.75F, 0.5F, 0.25F};
	/** Fire's harm to her, times. */
	static final float FIRE_HARM = 1.5F;
	/** Silk white, and the dust of the floor, for her blasts. */
	static final int SILK = 0xE8E4D8, DUST = 0x8A7F70;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"), WALK = RawAnimation.begin().thenLoop("walk"),
		SLEEP = RawAnimation.begin().thenLoop("sleep"), HOLD = RawAnimation.begin().thenLoop("hold"),
		STIR = RawAnimation.begin().thenPlay("stir"), WAKE = RawAnimation.begin().thenPlay("wake"), LEAP = RawAnimation.begin().thenPlay("leap"),
		LAND = RawAnimation.begin().thenPlay("land"), SLAM = RawAnimation.begin().thenPlay("slam"), SPIT = RawAnimation.begin().thenPlay("spit"),
		GRAB = RawAnimation.begin().thenPlay("grab"), BROOD = RawAnimation.begin().thenPlay("brood"), DEATH = RawAnimation.begin().thenPlayAndHold("death");

	private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(BroodQueen.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> HOLDING = SynchedEntityData.defineId(BroodQueen.class, EntityDataSerializers.BOOLEAN);

	enum Move {
		NONE, LEAP, SLAM, SPIT, GRAB
	}

	/** A shockwave running out over the ground. */
	private static final class Wave {
		final Vec3 from;
		double radius = 1.0;
		final Set<LivingEntity> hit = new HashSet<>();

		Wave(Vec3 from) {
			this.from = from;
		}
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.PURPLE,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private @Nullable BlockPos home;
	private Move move = Move.NONE;
	private int moveTicks, wakeTicks, idleTicks, airTicks, holdTicks, struggles, broodDone;
	private boolean airborne;
	private long leapReady, slamReady, spitReady, grabReady, broodReady, restUntil;
	private @Nullable LivingEntity held;
	private Input lastInput = Input.EMPTY;
	private final List<Wave> waves = new ArrayList<>();

	public BroodQueen(EntityType<? extends BroodQueen> type, Level level) {
		super(type, level);
		this.xpReward = 80;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOUR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SLEEPING, false);
		builder.define(HOLDING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new BusyGoal());
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** While she's in a move, holding someone or waking, nothing else moves her. */
	private final class BusyGoal extends Goal {
		BusyGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return BroodQueen.this.busy();
		}

		@Override
		public void tick() {
			BroodQueen.this.getNavigation().stop();
			LivingEntity target = BroodQueen.this.getTarget();
			if (target != null && BroodQueen.this.held == null) {
				BroodQueen.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
		}
	}

	boolean busy() {
		return this.move != Move.NONE || this.held != null || this.wakeTicks > 0;
	}

	// ---- asleep, awake ----

	public boolean sleeping() {
		return this.entityData.get(SLEEPING);
	}

	public @Nullable BlockPos home() {
		return this.home;
	}

	/** Laid asleep over her egg at {@code egg}. */
	void sleepOn(BlockPos egg) {
		this.home = egg.immutable();
		this.settle();
	}

	/** Asleep (again), whole, at her egg. */
	void settle() {
		this.entityData.set(SLEEPING, true);
		this.setNoAi(true);
		this.setTarget(null);
		this.setHealth(this.getMaxHealth());
		this.broodDone = 0;
		this.idleTicks = 0;
		this.grow();
		this.bar.removeAllPlayers();
	}

	/** A blow on her egg: she stirs in her sleep. */
	void stir() {
		if (this.sleeping() && this.level() instanceof ServerLevel level) {
			this.triggerAnim("action", "stir");
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.35F);
		}
	}

	/** Her egg split: she wakes, and goes for whoever struck it. */
	void wake(ServerPlayer striker) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(SLEEPING, false);
		this.setNoAi(false);
		this.wakeTicks = WAKE_TICKS;
		this.restUntil = level.getGameTime() + WAKE_TICKS + REST;
		this.triggerAnim("action", "wake");
		this.setTarget(striker);
		BossBars.hunting(this, striker);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.3F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.7F);
		Hearing.noise(level, this.position(), 40.0, null, Hearing.Kind.BLAST);
		PowersCompat.shakeAround(level, this.position(), 20.0, 0.6F);
	}

	/** Whether she's idle at home long enough to settle back onto her regrown egg. */
	boolean settles() {
		return this.getTarget() == null && this.idleTicks >= SETTLE_IDLE && this.home != null
			&& this.distanceToSqr(Vec3.atBottomCenterOf(this.home)) <= (HOME_NEAR + 4.0) * (HOME_NEAR + 4.0);
	}

	// ---- the fight ----

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		if (this.tickCount % 5 == 0) {
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
		if (this.tickCount % 10 == 0) {
			this.grow();
		}
		this.waves(level);
		if (this.wakeTicks > 0) {
			this.wakeTicks--;
			return;
		}
		this.holding(level);
		LivingEntity target = this.getTarget();
		this.idleTicks = target == null ? this.idleTicks + 1 : 0;
		// her nest's bounds: she won't follow far, and goes home with no one to fight
		if (this.home != null) {
			Vec3 at = Vec3.atBottomCenterOf(this.home);
			if (target != null && target.distanceToSqr(at) > (LEASH + 8.0) * (LEASH + 8.0)) {
				this.setTarget(null);
				target = null;
			}
			if (target == null && this.move == Move.NONE && this.distanceToSqr(at) > HOME_NEAR * HOME_NEAR && this.tickCount % 20 == 0) {
				this.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
			}
		}
		if (target != null) {
			this.brood(level, target, now);
		}
		if (this.move != Move.NONE) {
			this.moving(level, target, now);
			return;
		}
		if (target == null || this.held != null || now < this.restUntil || !target.isAlive()) {
			return;
		}
		double d = this.distanceTo(target);
		if (d <= GRAB_REACH && now >= this.grabReady && this.onGround()) {
			this.begin(Move.GRAB, GRAB_WINDUP, "grab");
		} else if (d <= SLAM_REACH && now >= this.slamReady && this.onGround() && this.random.nextFloat() < 0.5F) {
			this.begin(Move.SLAM, SLAM_WINDUP + SLAM_SECOND, "slam");
		} else if (d >= LEAP_MIN && d <= LEAP_MAX && now >= this.leapReady && this.onGround()) {
			this.begin(Move.LEAP, LEAP_WINDUP, "leap");
		} else if (d >= SPIT_MIN && d <= SPIT_RANGE && now >= this.spitReady && this.hasLineOfSight(target)) {
			this.begin(Move.SPIT, SPIT_WINDUP, "spit");
		}
	}

	/** How hurt she is: 0 whole, 1 at death's door. */
	private float hurt() {
		return Mth.clamp(1.0F - this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F);
	}

	/** Bigger and faster the more she's hurt (none of it asleep). */
	private void grow() {
		float g = this.sleeping() ? 0.0F : this.hurt();
		modify(Attributes.SCALE, "brood_queen_growth", g * GROW);
		modify(Attributes.MOVEMENT_SPEED, "brood_queen_haste", g * FASTER);
	}

	private void modify(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String name, double amount) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier was = instance.getModifier(JaymosKingdoms.id(name));
		if (was == null || Math.abs(was.amount() - amount) > 0.02) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id(name), amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private void begin(Move move, int windup, String anim) {
		this.move = move;
		this.moveTicks = Math.max(4, Math.round(windup * (1.0F - (float) QUICKER * this.hurt())));
		this.airborne = false;
		this.airTicks = 0;
		this.getNavigation().stop();
		this.triggerAnim("action", anim);
	}

	private void end(long now) {
		this.move = Move.NONE;
		this.restUntil = now + Math.round(REST * (1.0F - (float) QUICKER * this.hurt()));
	}

	private void moving(ServerLevel level, @Nullable LivingEntity target, long now) {
		switch (this.move) {
			case LEAP -> this.leaping(level, target, now);
			case SLAM -> {
				this.moveTicks--;
				if (this.moveTicks == SLAM_SECOND || this.moveTicks == 0) {
					this.slam(level);
				}
				if (this.moveTicks <= 0) {
					this.slamReady = now + SLAM_COOLDOWN;
					this.end(now);
				}
			}
			case SPIT -> {
				if (--this.moveTicks <= 0) {
					if (target != null && target.isAlive()) {
						Vec3 mouth = this.mouth();
						double d = mouth.distanceTo(target.position());
						Vec3 aim = target.position().add(target.getDeltaMovement().scale(d / SPIT_SPEED * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
						SilkShots.loose(level, this, mouth, aim, SPIT_SPEED, SPIT_CATCH, SPIT_LAYERS, SPIT_HOLD, false);
					}
					this.spitReady = now + SPIT_COOLDOWN;
					this.end(now);
				}
			}
			case GRAB -> {
				if (--this.moveTicks <= 0) {
					if (target != null && target.isAlive() && this.distanceTo(target) <= GRAB_REACH + 1.0 && !target.isSpectator()) {
						this.seize(level, target);
					}
					this.grabReady = now + GRAB_COOLDOWN;
					this.end(now);
				} else if (target != null && this.moveTicks == 3) {
					// the lunge
					Vec3 to = target.position().subtract(this.position()).normalize().scale(0.5);
					this.setDeltaMovement(to.x, 0.1, to.z);
					this.needsSync = true;
				}
			}
			default -> this.end(now);
		}
	}

	// the leap and its landing

	private void leaping(ServerLevel level, @Nullable LivingEntity target, long now) {
		if (!this.airborne) {
			if (--this.moveTicks > 0) {
				return;
			}
			if (target == null || !target.isAlive()) {
				this.leapReady = now + LEAP_COOLDOWN;
				this.end(now);
				return;
			}
			Vec3 to = target.position().subtract(this.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			// about as long in the air as a jump this high lasts
			double air = 2.0 * LEAP_UP / 0.08;
			double reach = Math.min(flat, LEAP_MAX) / air * 1.1;
			this.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
			this.setDeltaMovement(to.x / Math.max(flat, 0.01) * reach, LEAP_UP, to.z / Math.max(flat, 0.01) * reach);
			this.needsSync = true;
			this.airborne = true;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
			return;
		}
		this.airTicks++;
		this.resetFallDistance();
		if (this.onGround() && this.airTicks > 3 || this.airTicks > LEAP_AIR_MOST) {
			this.land(level);
			this.leapReady = now + LEAP_COOLDOWN;
			this.end(now);
		}
	}

	private void land(ServerLevel level) {
		Vec3 at = this.position();
		this.triggerAnim("action", "land");
		float size = 1.0F + (float) GROW * this.hurt();
		double radius = LAND_RADIUS * size;
		Fx.blast(level, at.add(0.0, 0.5, 0.0), (float) radius, DUST);
		Fx.ring(level, at.add(0.0, 0.1, 0.0), (float) radius, SILK);
		this.groundDust(level, at, radius, 40);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 0.55F);
		Hearing.noise(level, at, 36.0, null, Hearing.Kind.BLAST);
		PowersCompat.shakeAround(level, at, 18.0, 1.2F);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius, 2.0, radius),
				e -> e != this && e.isAlive() && !SilkShots.kin(e))) {
			double d = Math.sqrt(near.distanceToSqr(this));
			if (d > radius + this.getBbWidth() * 0.5) {
				continue;
			}
			float share = (float) Math.max(0.35, 1.0 - d / (radius + this.getBbWidth() * 0.5));
			near.hurtServer(level, SpiderNests.struck(level, this), LAND_DAMAGE * share);
			Vec3 away = near.position().subtract(at).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			near.setDeltaMovement(near.getDeltaMovement().add(away.scale(LAND_PUSH * share)).add(0.0, 0.45 * share, 0.0));
			near.needsSync = true;
		}
	}

	// the leg slams' shockwaves

	private void slam(ServerLevel level) {
		Vec3 front = this.position().add(this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(this.getBbWidth() * 0.6));
		this.waves.add(new Wave(front));
		Fx.ring(level, front.add(0.0, 0.1, 0.0), (float) WAVE_RANGE, DUST);
		this.groundDust(level, front, 1.5, 20);
		level.playSound(null, front.x, front.y, front.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.4F);
		Hearing.noise(level, front, 28.0, null, Hearing.Kind.IMPACT);
		PowersCompat.shakeAround(level, front, 14.0, 0.7F);
	}

	private void waves(ServerLevel level) {
		for (Iterator<Wave> it = this.waves.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			wave.radius += WAVE_SPEED;
			if (wave.radius > WAVE_RANGE) {
				it.remove();
				continue;
			}
			if (this.tickCount % 2 == 0) {
				int points = Mth.ceil(wave.radius * 3.0);
				for (int i = 0; i < points; i++) {
					double a = Math.PI * 2.0 * i / points;
					this.groundDust(level, wave.from.add(Math.cos(a) * wave.radius, 0.0, Math.sin(a) * wave.radius), 0.2, 1);
				}
			}
			double lo = wave.radius - WAVE_WIDTH, hi = wave.radius + WAVE_WIDTH;
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(wave.from, wave.from).inflate(hi, 2.0, hi),
					e -> e != this && e.isAlive() && e.onGround() && !SilkShots.kin(e) && !wave.hit.contains(e))) {
				double dx = near.getX() - wave.from.x, dz = near.getZ() - wave.from.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < lo || d > hi || Math.abs(near.getY() - wave.from.y) > 1.5) {
					continue;
				}
				wave.hit.add(near);
				near.hurtServer(level, SpiderNests.struck(level, this), WAVE_DAMAGE);
				Vec3 away = d < 1.0E-3 ? Vec3.ZERO : new Vec3(dx / d, 0.0, dz / d);
				near.setDeltaMovement(near.getDeltaMovement().add(away.scale(0.5)).add(0.0, 0.5, 0.0));
				near.needsSync = true;
			}
		}
	}

	/** The floor's own dust thrown up round {@code at}. */
	private void groundDust(ServerLevel level, Vec3 at, double spread, int count) {
		BlockPos floor = BlockPos.containing(at.x, at.y - 0.2, at.z);
		BlockState state = level.getBlockState(floor);
		if (state.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.1, at.z, count, spread * 0.4, 0.1, spread * 0.4, 0.15);
	}

	// the fang grab

	private Vec3 mouth() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		double reach = this.getBbWidth() * 0.55 + 0.3;
		return this.position().add(-Mth.sin(yaw) * reach, this.getBbHeight() * 0.45, Mth.cos(yaw) * reach);
	}

	private void seize(ServerLevel level, LivingEntity prey) {
		this.held = prey;
		this.holdTicks = HOLD_TICKS;
		this.struggles = 0;
		this.lastInput = prey instanceof ServerPlayer p ? p.getLastClientInput() : Input.EMPTY;
		this.entityData.set(HOLDING, true);
		level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.6F, 0.4F);
		if (prey instanceof ServerPlayer p) {
			PowersCompat.shake(p, 0.8F);
		}
	}

	private void holding(ServerLevel level) {
		LivingEntity prey = this.held;
		if (prey == null) {
			return;
		}
		if (!prey.isAlive() || prey.level() != level || prey.isSpectator()) {
			this.release(level, false);
			return;
		}
		Vec3 fangs = this.mouth().subtract(0.0, prey.getBbHeight() * 0.5, 0.0);
		prey.teleportTo(fangs.x, fangs.y, fangs.z);
		prey.setDeltaMovement(Vec3.ZERO);
		prey.resetFallDistance();
		// struggling: fresh presses of the walking keys and jump
		if (prey instanceof ServerPlayer p) {
			Input in = p.getLastClientInput();
			if (in.forward() && !this.lastInput.forward() || in.backward() && !this.lastInput.backward() || in.left() && !this.lastInput.left()
				|| in.right() && !this.lastInput.right() || in.jump() && !this.lastInput.jump()) {
				this.struggled(level, 1);
			}
			this.lastInput = in;
		} else if (this.random.nextFloat() < MOB_STRUGGLE) {
			this.struggled(level, 1);
		}
		if (this.held == null) {
			return;
		}
		if (--this.holdTicks <= 0) {
			// held out the hold: the bite
			prey.hurtServer(level, SpiderNests.struck(level, this), BITE_DAMAGE);
			prey.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_LEVEL), this);
			level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.6F);
			this.release(level, false);
		}
	}

	private void struggled(ServerLevel level, int by) {
		LivingEntity prey = this.held;
		if (prey == null) {
			return;
		}
		this.struggles += by;
		level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.COBWEB_STEP, SoundSource.PLAYERS, 0.8F, 0.8F + this.random.nextFloat() * 0.4F);
		if (prey instanceof ServerPlayer p) {
			PowersCompat.shake(p, 0.25F);
		}
		if (this.struggles >= STRUGGLE_NEEDED) {
			this.release(level, true);
		}
	}

	/** She lets go: {@code tornFree}, they struggled loose and she reels back a moment. */
	private void release(ServerLevel level, boolean tornFree) {
		LivingEntity prey = this.held;
		this.held = null;
		this.entityData.set(HOLDING, false);
		this.grabReady = level.getGameTime() + GRAB_COOLDOWN;
		if (prey == null || !prey.isAlive()) {
			return;
		}
		Vec3 away = prey.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? this.getLookAngle() : away.normalize();
		prey.setDeltaMovement(away.scale(tornFree ? 0.8 : 0.4).add(0.0, 0.35, 0.0));
		prey.needsSync = true;
		if (tornFree) {
			this.restUntil = level.getGameTime() + REST * 2L;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.6F, 0.5F);
		}
	}

	// spiders off her back

	private void brood(ServerLevel level, LivingEntity target, long now) {
		float health = this.getHealth() / this.getMaxHealth();
		boolean due = now >= this.broodReady && this.broodReady > 0;
		for (int i = 0; i < BROOD_AT.length; i++) {
			if (health <= BROOD_AT[i] && (this.broodDone & 1 << i) == 0) {
				this.broodDone |= 1 << i;
				due = true;
			}
		}
		if (this.broodReady == 0) {
			this.broodReady = now + BROOD_COOLDOWN;
		}
		if (!due) {
			return;
		}
		this.broodReady = now + BROOD_COOLDOWN;
		int near = level.getEntitiesOfClass(Spider.class, this.getBoundingBox().inflate(BROOD_NEAR),
			s -> s.isAlive() && SpiderKinds.of(s) == SpiderKinds.Kind.SPIDERLING).size();
		if (near >= BROOD_CAP) {
			return;
		}
		this.triggerAnim("action", "brood");
		int n = Math.min(BROOD_CAP - near, BROOD_LEAST + this.random.nextInt(BROOD_MOST - BROOD_LEAST + 1));
		SpiderKinds.hatch(level, this.position().add(0.0, this.getBbHeight() * 0.9, 0.0), n, target);
	}

	// ---- what she is ----

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if ((this.sleeping() || this.wakeTicks > 0) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.held != null && source.getEntity() == this.held) {
			// a blow from the one in her fangs is a struggle too
			this.struggled(level, STRUGGLE_BLOW);
		}
		return super.hurtServer(level, source, source.is(DamageTypeTags.IS_FIRE) ? amount * FIRE_HARM : amount);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return this.wakeTicks <= 0 && !this.sleeping() && super.doHurtTarget(level, target);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			this.release(level, false);
			this.triggerAnim("action", "death");
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !this.sleeping() && super.isPushable();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.putLong("home", this.home.asLong());
		}
		output.putBoolean("asleep", this.sleeping());
		output.putInt("brood_done", this.broodDone);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long home = input.getLongOr("home", Long.MIN_VALUE);
		this.home = home == Long.MIN_VALUE ? null : BlockPos.of(home);
		this.entityData.set(SLEEPING, input.getBooleanOr("asleep", false));
		this.broodDone = input.getIntOr("brood_done", 0);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.sleeping() ? null : SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.6F, 0.5F);
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F + this.random.nextFloat() * 0.1F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<BroodQueen>("move", 5, test -> {
			BroodQueen queen = test.animatable();
			return test.setAndContinue(queen.sleeping() ? SLEEP : queen.entityData.get(HOLDING) ? HOLD : test.isMoving() ? WALK : IDLE);
		}));
		controllers.add(new AnimationController<BroodQueen>("action", test -> PlayState.STOP).triggerableAnim("stir", STIR).triggerableAnim("wake", WAKE)
			.triggerableAnim("leap", LEAP).triggerableAnim("land", LAND).triggerableAnim("slam", SLAM).triggerableAnim("spit", SPIT)
			.triggerableAnim("grab", GRAB).triggerableAnim("brood", BROOD).triggerableAnim("death", DEATH));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
