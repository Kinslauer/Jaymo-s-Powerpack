package com.jaymospowers.power.chrono.future;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A war mech of the future: a towering walker left running in the ruins. It strides at whatever it sees (players and
 * the dead alike), raises its arm cannons and hoses them with bursts, lobs a shell every few seconds that bursts
 * where its target stands (no blocks broken), and smashes anything that gets close. It carries a boss bar. (0.19.32) It
 * leaves a wreck that comes apart like an iron golem's once its scrap is searched out (Kitchen's heavy corpses).
 * <p>(0.17.135) The same class is the smaller <b>android mech</b> ({@link FutureMachines#ANDROID_MECH}): lighter cannons, no
 * shells. One leads a patrol now and then, and the Mad Tinkerer sets one on you as his test unit. Both are drawn on our own
 * mech model in one of four builds (tools/gen_android_mech.py).
 */
public class FutureMech extends Monster {
	static final double HEALTH = 220.0, DAMAGE = 16.0, SPEED = 0.2, FOLLOW = 64.0, ARMOR = 20.0, TOUGHNESS = 8.0;
	/** Its cannons: a heavy machine gun's bursts out to here, and closer than this it smashes instead. */
	static final double CANNON_RANGE = 40.0, CANNON_NEAREST = 5.0;
	/** A shell every this often (ticks), at targets at least this far, bursting this big. */
	static final int SHELL_EVERY = 140;
	static final double SHELL_NEAREST = 8.0;
	static final float SHELL_POWER = 2.2F;
	/** The boss bar shows to players this close. */
	static final double BAR_SEEN = 48.0;
	static final FutureGuns.Gun CANNON = new FutureGuns.Gun("tacz:m249", FutureGuns.MG, "", 0);
	/** The android mech: smaller and lighter than the war mech, with rapid-fire arm guns. */
	static final double ANDROID_HEALTH = 100.0, ANDROID_DAMAGE = 9.0, ANDROID_SPEED = 0.25, ANDROID_ARMOR = 8.0, ANDROID_TOUGHNESS = 2.0;
	static final FutureGuns.Gun ANDROID_CANNON = new FutureGuns.Gun("tacz:p90", FutureGuns.SMG, "", 0);

	private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(FutureMech.class, EntityDataSerializers.BOOLEAN);

	private int attackAnimationTick;
	private int aimingFor;
	private int shellIn = SHELL_EVERY;
	private final ServerBossEvent bar;

	public FutureMech(EntityType<? extends FutureMech> type, Level level) {
		super(type, level);
		this.xpReward = android() ? 25 : 60;
		this.bar = new ServerBossEvent(java.util.UUID.randomUUID(), type.getDescription(), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_10);
	}

	/** The smaller android mech rather than the war mech? */
	public boolean android() {
		return getType() == FutureMachines.ANDROID_MECH;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.5);
	}

	public static AttributeSupplier.Builder createAndroidAttributes() {
		return createAttributes()
			.add(Attributes.MAX_HEALTH, ANDROID_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, ANDROID_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, ANDROID_SPEED)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ARMOR, ANDROID_ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, ANDROID_TOUGHNESS)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_AIMING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, FutureAndroid.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Zombie.class, true));
		// the Omens (manned mechs) are at war with the machines
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, FutureOmen.class, true));
	}

	/** Are its arm cannons up? */
	public boolean aiming() {
		return this.entityData.get(DATA_AIMING);
	}

	public int getAttackAnimationTick() {
		return this.attackAnimationTick;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.attackAnimationTick > 0) {
			this.attackAnimationTick--;
		}
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity foe = getTarget();
		if (aimingFor > 0 && --aimingFor == 0) {
			this.entityData.set(DATA_AIMING, false);
		}
		if (foe != null && foe.isAlive()) {
			double d = distanceTo(foe);
			boolean sight = hasLineOfSight(foe);
			if (sight && d > CANNON_NEAREST && d < CANNON_RANGE) {
				getLookControl().setLookAt(foe, 30.0F, 30.0F);
				this.entityData.set(DATA_AIMING, true);
				aimingFor = 30;
				FutureGuns.tickWith(level, this, foe, android() ? ANDROID_CANNON : CANNON);
				if (!android() && --shellIn <= 0 && d > SHELL_NEAREST) {
					shell(level, foe);
					shellIn = SHELL_EVERY + getRandom().nextInt(40);
				}
			}
		}
		if (getHealth() < getMaxHealth() * 0.3F && this.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.8, getZ(), 2, 0.4, 0.3, 0.4, 0.01);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.62, getZ(), 3, 0.6, 0.6, 0.6, 0.1);
		}
		bar.setProgress(getHealth() / getMaxHealth());
		if (this.tickCount % 20 == 0) {
			for (ServerPlayer player : level.players()) {
				boolean near = player.distanceTo(this) < BAR_SEEN && isAlive();
				if (near && !bar.getPlayers().contains(player)) {
					bar.addPlayer(player);
				} else if (!near && bar.getPlayers().contains(player)) {
					bar.removePlayer(player);
				}
			}
		}
	}

	/** A shell from its shoulder: a smoke trail, then a blast where the target stands (nothing broken). */
	private void shell(ServerLevel level, LivingEntity foe) {
		Vec3 from = position().add(0.0, 3.6, 0.0);
		Vec3 to = foe.position();
		Vec3 step = to.subtract(from);
		double len = step.length();
		Vec3 dir = step.scale(1.0 / Math.max(len, 1.0E-4));
		for (double t = 0.0; t < len; t += 1.0) {
			Vec3 p = from.add(dir.scale(t)).add(0.0, Math.sin(Math.PI * t / len) * len * 0.15, 0.0);
			level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, getX(), getY() + 3.0, getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2.0F, 0.6F);
		level.explode(this, to.x, to.y + 0.2, to.z, SHELL_POWER, Level.ExplosionInteraction.NONE);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.attackAnimationTick = 10;
		level.broadcastEntityEvent(this, (byte) 4);
		float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
		DamageSource source = damageSources().mobAttack(this);
		boolean hurt = target.hurtServer(level, source, damage / 2.0F + getRandom().nextInt(Math.max(1, (int) damage)));
		if (hurt) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.6, 0.0));
		}
		playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.6F);
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) {
			this.attackAnimationTick = 10;
			playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.6F);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (android()) {
			// the android mech: about half the war mech's scrap
			spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 2 + getRandom().nextInt(3)));
			spawnAtLocation(level, new ItemStack(Items.REDSTONE, 3 + getRandom().nextInt(4)));
			spawnAtLocation(level, new ItemStack(Items.COPPER_INGOT, 1 + getRandom().nextInt(2)));
			level.explode(this, getX(), getY() + 1.2, getZ(), 1.0F, Level.ExplosionInteraction.NONE);
			return;
		}
		spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 4 + getRandom().nextInt(6)));
		spawnAtLocation(level, new ItemStack(Items.REDSTONE, 6 + getRandom().nextInt(7)));
		spawnAtLocation(level, new ItemStack(Items.COPPER_INGOT, 2 + getRandom().nextInt(4)));
		if (getRandom().nextFloat() < 0.1F) {
			spawnAtLocation(level, new ItemStack(Items.NETHERITE_SCRAP));
		}
		level.explode(this, getX(), getY() + 1.5, getZ(), 1.5F, Level.ExplosionInteraction.NONE);
	}

	@Override
	public void remove(RemovalReason reason) {
		bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.5F);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_DAMAGE;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return distance > 160.0 * 160.0;
	}
}
