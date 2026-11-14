package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.dungeon.TombHall;
import com.jaymoskingdoms.traps.Runes;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Raid-King: the draugr's chieftain, the lord of the Stormborn Tomb, laid under the Frostborn jailer's seal in the
 * great hall and woken only by that seal mended ({@link TombHall}); he comes last, after the hall's dead. Our own model,
 * texture, animations and code (kingdoms/art/raid_king.bbmodel, tools/gen_tomb_undead.py); after the old idea of a
 * barrow-king only, nothing from any other mod.
 *
 * <p>He fights in moves, each with its tell, and leaves openings:
 * <ul>
 * <li>the <b>cleave</b> (both hands, over and down on what's in front) and the <b>sweep</b> (the whole front, thrown back);</li>
 * <li>the <b>rush</b> at a foe standing off: he sets his shoulder, then goes in a straight line. Stand aside and he
 * buries his axe ({@link #BURIED_TICKS} with his guard down); stand in it and he doesn't;</li>
 * <li>the <b>tail's lash</b> at anyone who gets behind him;</li>
 * <li>under {@link #COMBO_AT} of his health, <b>three in a row</b> (across, back, over), and he is spent a moment after;</li>
 * <li>at {@link #CALL_AT} of his health he <b>roars</b> and more of the hall's dead come up;</li>
 * <li>under {@link #RAGE_AT} he rests less between moves, and his cleave leaves the grave's cold on the floor.</li>
 * </ul>
 * In an opening he takes {@link #OPEN_HARM} times the harm; roaring, {@link #ROAR_HARM}; waking, none. He keeps to the
 * hall, and mends when nobody is left to fight.
 */
public class RaidKing extends Monster implements GeoEntity {
	static final double HEALTH = 360.0, SPEED = 0.26, ARMOR = 10.0, KNOCKBACK_RESISTANCE = 1.0, FOLLOW = 40.0;
	/** How far from the hall's middle he goes (blocks). */
	public static final int HOME_RADIUS = 26;
	/** Each move's length and the tick its blow lands (ticks; its animation's seconds x 20). */
	static final int WAKE_TICKS = 60, CLEAVE_TICKS = 24, CLEAVE_LANDS = 13, SWEEP_TICKS = 20, SWEEP_LANDS = 10, COMBO_TICKS = 46,
		READY_TICKS = 16, RUSH_TICKS = 16, TAIL_TICKS = 16, TAIL_LANDS = 8, ROAR_TICKS = 32, ROAR_CALLS = 14, BURIED_TICKS = 50, SPENT_TICKS = 30;
	static final int[] COMBO_LANDS = {9, 21, 34};
	/** Reach (blocks), half arc (degrees), harm and throw of each blow. */
	static final double CLEAVE_REACH = 5.5, SWEEP_REACH = 5.0, TAIL_REACH = 4.5;
	static final float CLEAVE_ARC = 35.0F, SWEEP_ARC = 100.0F;
	static final float CLEAVE_DAMAGE = 16.0F, SWEEP_DAMAGE = 11.0F, COMBO_SWEEP_DAMAGE = 9.0F, COMBO_CLEAVE_DAMAGE = 14.0F, TAIL_DAMAGE = 8.0F,
		RUSH_DAMAGE = 13.0F;
	static final double SWEEP_THROW = 1.2, TAIL_THROW = 1.5, RUSH_THROW = 1.6, CLEAVE_LIFT = 0.5;
	/** The rush: at a foe this far off (blocks, least and most), this fast (blocks a tick), not again for this long (ticks). */
	static final double RUSH_MIN = 7.0, RUSH_MAX = 18.0, RUSH_SPEED = 0.95;
	static final int RUSH_GAP = 160, TAIL_GAP = 60, COMBO_GAP = 140;
	/** A foe within this of him is fought with the axe (blocks); one more than this many degrees off his facing is behind him. */
	static final double AXE_RANGE = 5.0;
	static final float BEHIND = 110.0F;
	/** Ticks of rest between moves, by how hurt he is; his health's shares for the combo and the rage. */
	static final int REST = 24, REST_COMBO = 16, REST_RAGE = 9;
	static final float COMBO_AT = 0.6F, RAGE_AT = 0.3F;
	/** His roar: at these shares of his health (each once), raising this many each time. */
	static final float[] CALL_AT = {0.6F, 0.3F};
	static final int[] CALL_COUNT = {3, 4};
	/** How much of a blow he takes in an opening, and while roaring. */
	static final float OPEN_HARM = 1.5F, ROAR_HARM = 0.3F;
	/** The cold his cleave leaves in his rage: how wide (blocks), how long (ticks), its slowness. */
	static final float COLD_RADIUS = 3.0F;
	static final int COLD_TICKS = 80, COLD_SLOW = 1;
	/** With nobody to fight he mends this much a tick. */
	static final float MEND = 0.5F;
	/** His fall takes this long (its 2.6 s animation). */
	static final int DEATH_TICKS = 54;
	static final int COLD_COLOR = 0xDDF6FF;
	/** What he leaves: bones, silver, one of each rune. */
	static final int BONES_MIN = 8, BONES_MAX = 12, SILVER_MIN = 8, SILVER_MAX = 12, XP = 150;

	enum Move { NONE, WAKE, CLEAVE, SWEEP, COMBO, READY, RUSH, TAIL, ROAR, BURIED, SPENT }

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUSH = RawAnimation.begin().thenLoop("rush");
	static final String[] ONCE = {"wake", "cleave", "sweep", "combo", "roar", "buried", "spent", "tail_lash"};
	static final String[] HOLD = {"rush_ready", "death"};

	private static final EntityDataAccessor<Boolean> RUSHING = SynchedEntityData.defineId(RaidKing.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), Component.translatable("entity.jaymoskingdoms.raid_king"),
		BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	private Move move = Move.NONE;
	private int moveTicks, rest = REST, rushWait, tailWait, comboWait, calls;
	private boolean engaged, sweepNext;
	private Vec3 rushWay = Vec3.ZERO;
	private final Set<Integer> rushStruck = new HashSet<>();
	private @Nullable Vec3 cold;
	private int coldTicks;

	public RaidKing(EntityType<? extends RaidKing> type, Level level) {
		super(type, level);
		this.bar.setVisible(false);
		this.xpReward = XP;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.ARMOR, ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(RUSHING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return RaidKing.this.move == Move.NONE && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return RaidKing.this.move == Move.NONE && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Woken under the seal: he straightens and roars before he fights, and nothing harms him the while. */
	public void wake() {
		this.begin(Move.WAKE, "wake");
		this.playSound(SoundEvents.WARDEN_EMERGE, 2.0F, 0.6F);
	}

	private void begin(Move move, @Nullable String animation) {
		this.move = move;
		this.moveTicks = 0;
		this.getNavigation().stop();
		this.entityData.set(RUSHING, move == Move.RUSH);
		if (animation != null) {
			this.triggerAnim("action", animation);
		}
	}

	private void end() {
		boolean rage = this.getHealth() <= this.getMaxHealth() * RAGE_AT, combo = this.getHealth() <= this.getMaxHealth() * COMBO_AT;
		this.move = Move.NONE;
		this.rest = rage ? REST_RAGE : combo ? REST_COMBO : REST;
		this.entityData.set(RUSHING, false);
	}

	/** His blows are his moves': a foe in reach is never struck by a plain swing. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.rushWait > 0) {
			this.rushWait--;
		}
		if (this.tailWait > 0) {
			this.tailWait--;
		}
		if (this.comboWait > 0) {
			this.comboWait--;
		}
		this.chill(level);
		LivingEntity target = this.getTarget();
		if (this.move != Move.NONE) {
			this.moveTicks++;
			this.step(level, target);
		} else if (target == null) {
			if (this.getHealth() < this.getMaxHealth()) {
				this.heal(MEND);
			}
		} else if (--this.rest <= 0) {
			this.choose(level, target);
		}
		this.bar.setVisible(this.engaged && this.isAlive());
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
	}

	/** Picks the next move for where {@code target} stands. */
	private void choose(ServerLevel level, LivingEntity target) {
		float health = this.getHealth() / this.getMaxHealth();
		if (this.calls < CALL_AT.length && health <= CALL_AT[this.calls]) {
			this.begin(Move.ROAR, "roar");
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 0.5F);
			return;
		}
		double distance = this.distanceTo(target);
		if (distance <= TAIL_REACH && this.tailWait == 0 && Math.abs(this.off(target)) > BEHIND) {
			this.tailWait = TAIL_GAP;
			this.begin(Move.TAIL, "tail_lash");
			this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
		} else if (distance <= AXE_RANGE) {
			this.face(target);
			if (health <= COMBO_AT && this.comboWait == 0) {
				this.comboWait = COMBO_GAP;
				this.begin(Move.COMBO, "combo");
			} else if (this.sweepNext) {
				this.begin(Move.SWEEP, "sweep");
			} else {
				this.begin(Move.CLEAVE, "cleave");
			}
			this.sweepNext = !this.sweepNext;
			this.playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 0.6F);
		} else if (distance >= RUSH_MIN && distance <= RUSH_MAX && this.rushWait == 0 && this.hasLineOfSight(target)) {
			this.rushWait = RUSH_GAP;
			this.begin(Move.READY, "rush_ready");
			this.playSound(SoundEvents.RAVAGER_STUNNED, 1.6F, 0.5F);
		}
	}

	/** One tick of the move he is in. */
	private void step(ServerLevel level, @Nullable LivingEntity target) {
		int t = this.moveTicks;
		switch (this.move) {
			case WAKE -> {
				if (t == 44) {
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.45F);
					Fx.ring(level, this.position().add(0.0, 0.2, 0.0), 8.0F, COLD_COLOR);
				}
				this.finish(t, WAKE_TICKS, null);
			}
			case CLEAVE -> {
				if (t < CLEAVE_LANDS - 4 && target != null) {
					this.face(target);
				}
				if (t == CLEAVE_LANDS) {
					this.cleave(level, CLEAVE_DAMAGE);
				}
				this.finish(t, CLEAVE_TICKS, null);
			}
			case SWEEP -> {
				if (t < SWEEP_LANDS - 4 && target != null) {
					this.face(target);
				}
				if (t == SWEEP_LANDS) {
					this.sweep(level, SWEEP_DAMAGE);
				}
				this.finish(t, SWEEP_TICKS, null);
			}
			case COMBO -> {
				// he steps in after his foe between the blows
				if (target != null && (t < COMBO_LANDS[0] - 3 || t > COMBO_LANDS[0] + 2 && t < COMBO_LANDS[1] - 3 || t > COMBO_LANDS[1] + 2 && t < COMBO_LANDS[2] - 4)) {
					this.face(target);
					if (this.distanceTo(target) > 3.0) {
						Vec3 way = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.22);
						this.setDeltaMovement(way.x, this.getDeltaMovement().y, way.z);
					}
				}
				if (t == COMBO_LANDS[0] || t == COMBO_LANDS[1]) {
					this.sweep(level, COMBO_SWEEP_DAMAGE);
				} else if (t == COMBO_LANDS[2]) {
					this.cleave(level, COMBO_CLEAVE_DAMAGE);
				}
				this.finish(t, COMBO_TICKS, Move.SPENT);
			}
			case READY -> {
				if (target != null) {
					this.face(target);
				}
				if (t >= READY_TICKS) {
					Vec3 way = target == null ? this.getLookAngle() : target.position().subtract(this.position());
					this.rushWay = way.multiply(1.0, 0.0, 1.0).normalize();
					this.rushStruck.clear();
					this.begin(Move.RUSH, null);
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.8F);
				}
			}
			case RUSH -> this.rush(level, t);
			case TAIL -> {
				if (t == TAIL_LANDS) {
					this.strike(level, TAIL_REACH, 180.0F, TAIL_DAMAGE, TAIL_THROW, 0.3);
				}
				this.finish(t, TAIL_TICKS, null);
			}
			case ROAR -> {
				if (t == ROAR_CALLS) {
					int count = CALL_COUNT[Math.min(this.calls, CALL_COUNT.length - 1)];
					this.calls++;
					Fx.ring(level, this.position().add(0.0, 0.2, 0.0), 10.0F, COLD_COLOR);
					TombHall.call(level, this, count);
				}
				this.finish(t, ROAR_TICKS, null);
			}
			case BURIED -> this.finish(t, BURIED_TICKS, null);
			case SPENT -> this.finish(t, SPENT_TICKS, null);
			default -> {
			}
		}
	}

	/** At {@code length} ticks the move is over; into {@code then} (an opening) if there is one. */
	private void finish(int t, int length, @Nullable Move then) {
		if (t < length) {
			return;
		}
		if (then != null) {
			this.begin(then, then == Move.BURIED ? "buried" : "spent");
			this.playSound(SoundEvents.RAVAGER_STUNNED, 1.2F, 0.7F);
		} else {
			this.end();
		}
	}

	/** The rush: straight on, through whoever stands in it. A wall, or the end of it with nobody struck, buries the axe. */
	private void rush(ServerLevel level, int t) {
		this.setDeltaMovement(this.rushWay.x * RUSH_SPEED, this.getDeltaMovement().y, this.rushWay.z * RUSH_SPEED);
		float yaw = (float) (Mth.atan2(this.rushWay.z, this.rushWay.x) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.7, 0.0, 0.7), this::foe)) {
			if (this.rushStruck.add(near.getId()) && near.hurtServer(level, this.damageSources().mobAttack(this), RUSH_DAMAGE)) {
				near.push(this.rushWay.x * RUSH_THROW, 0.4, this.rushWay.z * RUSH_THROW);
				near.needsSync = true;
				this.engaged |= near instanceof Player;
			}
		}
		if (t % 3 == 0) {
			this.dust(level, this.position(), 6);
		}
		boolean wall = this.horizontalCollision && t > 2;
		if (wall || t >= RUSH_TICKS) {
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
			if (wall || this.rushStruck.isEmpty()) {
				this.dust(level, this.position().add(this.rushWay.scale(1.5)), 40);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
				this.finish(1, 1, Move.BURIED);
			} else {
				this.end();
			}
		}
	}

	/** The axe comes down in front of him; in his rage it leaves the cold where it landed. */
	private void cleave(ServerLevel level, float damage) {
		this.strike(level, CLEAVE_REACH, CLEAVE_ARC, damage, 0.3, CLEAVE_LIFT);
		Vec3 at = this.position().add(this.facing().scale(3.5));
		this.dust(level, at, 50);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
		if (this.getHealth() <= this.getMaxHealth() * RAGE_AT) {
			this.cold = at;
			this.coldTicks = COLD_TICKS;
			Fx.pool(level, at.add(0.0, 0.1, 0.0), COLD_RADIUS, COLD_COLOR, COLD_TICKS);
		}
	}

	private void sweep(ServerLevel level, float damage) {
		this.strike(level, SWEEP_REACH, SWEEP_ARC, damage, SWEEP_THROW, 0.25);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
	}

	/** Hurts and throws every foe within {@code reach} and {@code arc} degrees either side of his facing. */
	private void strike(ServerLevel level, double reach, float arc, float damage, double thrown, double lift) {
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach), this::foe)) {
			if (this.distanceTo(near) > reach + near.getBbWidth() * 0.5 || Math.abs(this.off(near)) > arc) {
				continue;
			}
			if (near.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
				Vec3 away = near.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? this.facing() : away.normalize();
				near.push(away.x * thrown, lift, away.z * thrown);
				near.needsSync = true;
				this.engaged |= near instanceof Player;
			}
		}
	}

	/** The cold a cleave left: slows whoever stands in it. */
	private void chill(ServerLevel level) {
		if (this.cold == null || --this.coldTicks <= 0) {
			this.cold = null;
			return;
		}
		if (this.coldTicks % 10 == 0) {
			Vec3 at = this.cold;
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(COLD_RADIUS, 1.5, COLD_RADIUS),
				this::foe)) {
				near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, COLD_SLOW), this);
				near.setTicksFrozen(Math.max(near.getTicksFrozen(), 80));
			}
		}
	}

	/** Anything living that isn't one of the tomb's dead. */
	private boolean foe(LivingEntity entity) {
		return entity != this && entity.isAlive() && !(entity instanceof Monster) && !entity.isSpectator()
			&& !(entity instanceof Player player && player.isCreative());
	}

	private Vec3 facing() {
		return Vec3.directionFromRotation(0.0F, this.yBodyRot);
	}

	/** How many degrees off his facing {@code other} stands. */
	private float off(Entity other) {
		Vec3 to = other.position().subtract(this.position());
		return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-to.x, to.z)) - this.yBodyRot);
	}

	private void face(Entity other) {
		Vec3 to = other.position().subtract(this.position());
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	private void dust(ServerLevel level, Vec3 at, int count) {
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, 0.8, 0.1, 0.8, 0.15);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.move == Move.WAKE && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (this.move == Move.BURIED || this.move == Move.SPENT) {
			amount *= OPEN_HARM;
		} else if (this.move == Move.ROAR) {
			amount *= ROAR_HARM;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player) {
			this.engaged = true;
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		this.triggerAnim("action", "death");
		if (this.level() instanceof ServerLevel level) {
			TombHall.fell(level, this);
		}
	}

	/** His fall plays out before he is gone; vanilla's is a second. */
	@Override
	protected void tickDeath() {
		if (++this.deathTime >= DEATH_TICKS && this.level() instanceof ServerLevel level && !this.isRemoved()) {
			level.broadcastEntityEvent(this, (byte) 60);
			this.remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(Items.BONE, BONES_MIN + this.random.nextInt(BONES_MAX - BONES_MIN + 1)));
		for (ItemStack coins : Money.stacks(Money.worth(SILVER_MIN + this.random.nextInt(SILVER_MAX - SILVER_MIN + 1)))) {
			this.spawnAtLocation(level, coins);
		}
		for (Runes.Kind kind : Runes.Kind.values()) {
			this.spawnAtLocation(level, new ItemStack(kind.item()));
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<RaidKing>("move", 4, test -> {
			if (test.animatable().entityData.get(RUSHING)) {
				return test.setAndContinue(RUSH);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		AnimationController<RaidKing> action = new AnimationController<>("action", 2, test -> PlayState.STOP);
		for (String name : ONCE) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
		}
		for (String name : HOLD) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlayAndHold(name));
		}
		controllers.add(action);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.6F);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("calls", this.calls);
		output.putBoolean("engaged", this.engaged);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.calls = input.getIntOr("calls", 0);
		this.engaged = input.getBooleanOr("engaged", false);
	}
}
