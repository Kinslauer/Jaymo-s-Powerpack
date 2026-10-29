package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.BossBars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.329) The Piglin King, a boss of the Nether's demon country, drawn as eanncrod's Musclebound Piglin King
 * (Blockbench Workshop, CC BY 4.0). Rare: it sleeps on its feet in the Nether wastes and crimson woods until someone
 * comes near, then wakes with a roar and a boss bar. It rakes with its clawed gauntlet, hurls a volley of embers, lets
 * the fire in it out in a burst that sets everyone close alight, and when hurt badly roars for its brutes. The model's
 * moves were made for ice; the king's are fire (the same motions). Piglins never cross it, and it never strikes them.
 */
public class PiglinKing extends ActingMonster {
	static final double HEALTH = 300.0, ARMOR = 12.0, DAMAGE = 16.0, SPEED = 0.26;
	/** How rare: one natural spawn in this many passes the spawn check. */
	static final int RARITY = 12;
	/** Never another within this many blocks. */
	static final double ALONE_RANGE = 160.0;
	/** How near a player must come to wake it (blocks). */
	static final double WAKE_RANGE = 14.0;
	/** Its waking (ticks). */
	static final int AWAKEN_TICKS = 70;
	/** Its claw: how long, when it lands, its reach. */
	static final int CLAW_TICKS = 42, CLAW_HIT = 24;
	static final double CLAW_REACH = 3.5;
	/** Its ember volley: how long, the ticks each ember flies, how far it throws from, how often. */
	static final int VOLLEY_TICKS = 40, VOLLEY_COOLDOWN = 120;
	static final int[] VOLLEY_SHOTS = {14, 20, 26, 32};
	static final double VOLLEY_RANGE = 24.0;
	/** Its burst: how long, when, its reach, its blow, how long it burns them (seconds), how often. */
	static final int BURST_TICKS = 32, BURST_HIT = 18, BURST_COOLDOWN = 200;
	static final double BURST_REACH = 5.0;
	static final float BURST_DAMAGE = 10.0F, BURST_BURN = 5.0F;
	/** Its roar for its brutes: how long, when they come, how many, the most near it at once, below what share of health, how often. */
	static final int ROAR_TICKS = 50, ROAR_CALL = 24, ROAR_BRUTES = 2, ROAR_MAX_NEAR = 4, ROAR_COOLDOWN = 600;
	static final float ROAR_BELOW = 0.5F;
	static final int GAP = 16;

	private static final String[] ACTIONS = {"awaken", "reaching_claw", "shard_volley", "cold_snap", "lamentation_roar"};

	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.RED,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private boolean awake;
	private int gap, volleyCooldown = VOLLEY_COOLDOWN / 2, burstCooldown = BURST_COOLDOWN / 2, roarCooldown;

	public PiglinKing(EntityType<? extends PiglinKing> type, Level level) {
		super(type, level);
		this.xpReward = 100;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Rare, and only one in {@link #RARITY} of the spots that would do. */
	public static boolean checkSpawn(EntityType<PiglinKing> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return (reason != EntitySpawnReason.NATURAL || random.nextInt(RARITY) == 0 && Beasts.alone(level, pos, PiglinKing.class, ALONE_RANGE)) && checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, AbstractPiglin.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (!this.awake) {
			// asleep on its feet: still until someone comes near or strikes it
			this.getNavigation().stop();
			if (target != null && (this.distanceTo(target) < WAKE_RANGE || this.getLastHurtByMob() != null)) {
				this.wake(level);
			}
			return;
		}
		this.bar.setName(this.getDisplayName());
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		BossBars.show(level, this, this.bar);
		this.volleyCooldown--;
		this.burstCooldown--;
		this.roarCooldown--;
		if (this.acting()) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			this.moveStep(level, target);
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double d = this.distanceTo(target);
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (--this.gap > 0) {
			this.getNavigation().moveTo(target, 1.0);
			return;
		}
		if (this.roarCooldown <= 0 && this.getHealth() < this.getMaxHealth() * ROAR_BELOW) {
			this.roarCooldown = ROAR_COOLDOWN;
			this.act("lamentation_roar", ROAR_TICKS);
			this.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 3.0F, 0.5F);
		} else if (d < BURST_REACH && this.burstCooldown <= 0) {
			this.burstCooldown = BURST_COOLDOWN;
			this.act("cold_snap", BURST_TICKS);
		} else if (d < CLAW_REACH + 1.0) {
			this.act("reaching_claw", CLAW_TICKS);
		} else if (d < VOLLEY_RANGE && this.volleyCooldown <= 0 && this.hasLineOfSight(target)) {
			this.volleyCooldown = VOLLEY_COOLDOWN;
			this.act("shard_volley", VOLLEY_TICKS);
		} else {
			this.getNavigation().moveTo(target, 1.0);
			return;
		}
		this.gap = GAP;
	}

	private void wake(ServerLevel level) {
		this.awake = true;
		this.setPersistenceRequired();
		this.act("awaken", AWAKEN_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 3.0F, 0.4F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5, this.getZ(), 30, 0.8, 1.0, 0.8, 0.02);
	}

	/** Its move lands. */
	private void moveStep(ServerLevel level, LivingEntity target) {
		int t = this.actionRun();
		if (this.acting("reaching_claw") && t == CLAW_HIT) {
			this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), CLAW_REACH, true, 1.0, 0.2);
			this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
		} else if (this.acting("shard_volley") && target != null) {
			for (int shot : VOLLEY_SHOTS) {
				if (t == shot) {
					this.ember(level, target);
				}
			}
		} else if (this.acting("cold_snap") && t == BURST_HIT) {
			this.burst(level);
		} else if (this.acting("lamentation_roar") && t == ROAR_CALL) {
			this.callBrutes(level);
		}
	}

	private void ember(ServerLevel level, LivingEntity target) {
		Vec3 dir = new Vec3(target.getX() - this.getX(), target.getY(0.5) - this.getEyeY(), target.getZ() - this.getZ()).normalize();
		dir = dir.add(this.random.nextGaussian() * 0.05, 0.0, this.random.nextGaussian() * 0.05);
		SmallFireball ember = new SmallFireball(level, this, dir);
		ember.setPos(this.getX() + dir.x * 1.2, this.getEyeY() - 0.4, this.getZ() + dir.z * 1.2);
		level.addFreshEntity(ember);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.7F);
	}

	/** The fire in it bursts out: everyone close burns and is thrown back. */
	private void burst(ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BURST_REACH), e -> e != this && this.strikes(e))) {
			if (e.distanceTo(this) < BURST_REACH + 1.0) {
				e.igniteForSeconds(BURST_BURN);
			}
		}
		this.strike(level, BURST_DAMAGE, BURST_REACH, false, 1.5, 0.3);
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(), 120, BURST_REACH * 0.5, 0.4, BURST_REACH * 0.5, 0.1);
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 1.0, this.getZ(), 20, 1.5, 0.5, 1.5, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.4F);
	}

	/** Its brutes come, out of smoke at its side, up to {@link #ROAR_MAX_NEAR} near it at once. */
	private void callBrutes(ServerLevel level) {
		int near = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(24.0), m -> m.getType() == EntityTypes.PIGLIN_BRUTE).size();
		for (int i = 0; i < ROAR_BRUTES && near < ROAR_MAX_NEAR; i++, near++) {
			if (!(EntityTypes.PIGLIN_BRUTE.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob brute)) {
				return;
			}
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			brute.snapTo(this.getX() + Math.cos(angle) * 3.0, this.getY(), this.getZ() + Math.sin(angle) * 3.0, this.getYRot(), 0.0F);
			if (!level.noCollision(brute)) {
				continue;
			}
			brute.finalizeSpawn(level, level.getCurrentDifficultyAt(brute.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			brute.setTarget(this.getTarget());
			level.addFreshEntity(brute);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, brute.getX(), brute.getY() + 1.0, brute.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		}
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		return !(e instanceof AbstractPiglin) && super.strikes(e);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof AbstractPiglin) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && !this.awake) {
			this.wake(level);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (its blows are its own moves)
		return false;
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		this.bar.removeAllPlayers();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("awake", this.awake);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.awake = input.getBooleanOr("awake", false);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.awake ? SoundEvents.PIGLIN_BRUTE_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIGLIN_BRUTE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIGLIN_BRUTE_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.PIGLIN_BRUTE_STEP, 1.0F, 0.6F);
	}
}
