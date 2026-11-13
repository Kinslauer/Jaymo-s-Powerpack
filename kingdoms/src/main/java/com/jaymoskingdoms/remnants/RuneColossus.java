package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.beasts.ActingMonster;
import com.jaymoskingdoms.beasts.Beasts;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The rune-bound colossus, drawn from yair's Blockbench model. Andy: "these can spawn among the high field giant as
 * fodder to make it a bit more difficult." They are pieces of the Highfield monolith itself: when its Giant has someone
 * to fight, broken blocks of the pillar get up round it ({@link #rise}), a few at first and more as it is worn down
 * ({@link AetherGiant}), each climbing out of the ground before it can be hurt or hit. A colossus is slow and heavy:
 * an overhead smash with both fists, a hook, and now and then it braces and its runes ring out, throwing back and
 * slowing whoever is close. It goes for whoever the Giant is after. When the Giant falls, or is gone, every one of its
 * colossi goes to pieces with it. One raised alone (a spawn egg) just fights.
 */
public class RuneColossus extends ActingMonster {
	static final double HEALTH = 40.0, ARMOR = 8.0, DAMAGE = 7.0, SPEED = 0.24, SIGHT = 40.0, KNOCKBACK_RESISTANCE = 0.7;
	/** How many get up when the Giant first has someone to fight, and at each later third of its health. */
	static final int FIRST_WAVE = 3, LATER_WAVE = 2;
	/** How far from the Giant's feet they rise (blocks: its legs are wide), and how far one looks for its Giant. */
	static final double RISE_MIN = 9.0, RISE_MAX = 16.0, GIANT_RANGE = 96.0;
	/** Rising (the animation's length, ticks). */
	static final int RISE_TICKS = 72;
	/** The smash and the hook: each animation's length, the tick it lands, its reach, and the ticks between blows. */
	static final int SMASH_TICKS = 32, SMASH_LANDS = 15, HOOK_TICKS = 28, HOOK_LANDS = 13, GAP = 30;
	static final double REACH = 2.2;
	static final float SMASH_BONUS = 3.0F;
	/** Its runes ringing out: the animation's length, when the ring goes, how far, how often, and what it does. */
	static final int RING_TICKS = 68, RING_AT = 30, RING_AGAIN = 300, RING_SLOW_TICKS = 80;
	static final double RING_RANGE = 5.0, RING_THROW = 0.9;
	static final float RING_DAMAGE = 4.0F;
	/** Its runes' gold, for the ring. */
	static final int RUNE = 0xFFC83C;
	/** Flinching (the animation's length), and how long its death takes (the animation's length; vanilla's is 20). */
	static final int HURT_TICKS = 12, DEATH_TICKS = 76;

	private static final String[] ACTIONS = {"spawn", "attack_1", "attack_2", "special", "hurt"};

	/** The Giant it got up for; null for one raised alone. */
	private @Nullable UUID giant;
	private int gap;
	private long ringAfter;

	public RuneColossus(EntityType<? extends RuneColossus> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, SIGHT).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	private static boolean ally(Entity e) {
		return e instanceof RuneColossus || e instanceof AetherGiant;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && ally(target) ? null : target);
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		return !ally(e) && super.strikes(e);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.acting("spawn")) {
			this.getNavigation().stop();
			if (this.actionRun() % 6 == 0) {
				this.rubble(level, 10);
			}
			return;
		}
		if (this.giant != null && this.tickCount % 20 == 0) {
			Entity mine = level.getEntity(this.giant);
			if (!(mine instanceof AetherGiant g) || !g.isAlive()) {
				// (only once its Giant is known gone: one out of sight in unloaded ground is still standing)
				if (mine != null || level.isLoaded(this.blockPosition()) && this.tickCount > 200 && !this.giantNear(level)) {
					this.kill(level);
					return;
				}
			} else if (this.getTarget() == null && g.getTarget() != null) {
				this.setTarget(g.getTarget());
			}
		}
		if (this.gap > 0) {
			this.gap--;
		}
		if (this.acting("attack_1")) {
			if (this.actionRun() == SMASH_LANDS) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + SMASH_BONUS, REACH, true, 0.9, 0.15);
				this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.6F);
				this.rubble(level, 14);
			}
			return;
		}
		if (this.acting("attack_2")) {
			if (this.actionRun() == HOOK_LANDS) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), REACH, true, 1.2, 0.0);
				this.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
			}
			return;
		}
		if (this.acting("special")) {
			this.getNavigation().stop();
			if (this.actionRun() == RING_AT) {
				this.ring(level);
			}
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.acting("hurt")) {
			return;
		}
		this.getLookControl().setLookAt(target, 20.0F, 30.0F);
		this.getNavigation().moveTo(target, 1.0);
		double away = this.distanceTo(target) - target.getBbWidth() / 2.0 - this.getBbWidth() / 2.0;
		if (level.getGameTime() >= this.ringAfter && away < RING_RANGE - 1.0) {
			this.ringAfter = level.getGameTime() + RING_AGAIN + this.getRandom().nextInt(100);
			this.act("special", RING_TICKS);
			this.playSound(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.6F);
		} else if (this.gap <= 0 && away < REACH) {
			this.gap = GAP;
			if (this.getRandom().nextBoolean()) {
				this.act("attack_1", SMASH_TICKS);
			} else {
				this.act("attack_2", HOOK_TICKS);
			}
		}
	}

	/** Whether any living Giant stands within reach of it (one whose own was unloaded and came back under another id). */
	private boolean giantNear(ServerLevel level) {
		return !level.getEntitiesOfClass(AetherGiant.class, this.getBoundingBox().inflate(GIANT_RANGE), AetherGiant::isAlive).isEmpty();
	}

	/** Its runes ring out: everything about it that it would hit is hurt a little, thrown back and slowed. */
	private void ring(ServerLevel level) {
		Fx.ring(level, this.position().add(0.0, 0.3, 0.0), (float) RING_RANGE, RUNE);
		this.playSound(SoundEvents.BELL_RESONATE, 1.6F, 0.5F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RING_RANGE, 2.0, RING_RANGE),
			e -> e != this && e.isAlive() && this.strikes(e) && e.distanceTo(this) <= RING_RANGE + e.getBbWidth() / 2.0)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), RING_DAMAGE)) {
				Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(RING_THROW);
				e.push(away.x, 0.35, away.z);
				e.needsSync = true;
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RING_SLOW_TICKS, 1), this);
			}
		}
	}

	/** Broken stone thrown up about its feet. */
	private void rubble(ServerLevel level, int count) {
		BlockState under = level.getBlockState(this.blockPosition().below());
		BlockState stone = under.isAir() ? Blocks.CALCITE.defaultBlockState() : under;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, stone), this.getX(), this.getY() + 0.2, this.getZ(), count, 0.8, 0.3, 0.8,
			0.1);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (its blows are its moves)
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// still half in the ground, nothing reaches it; and the Giant's own stamps and waves pass it by
		if (this.acting("spawn") && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.getEntity() != null && ally(source.getEntity())) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive() && !this.acting("attack_1") && !this.acting("attack_2") && !this.acting("special")) {
			this.act("hurt", HURT_TICKS);
		}
		return hurt;
	}

	/** Its death takes as long as its falling apart is drawn. */
	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level() instanceof ServerLevel level) {
			if (this.deathTime % 8 == 0) {
				this.rubble(level, 8);
			}
			if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				this.remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return this.giant == null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("giant", UUIDUtil.CODEC, this.giant);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.giant = input.read("giant", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	// ---- getting up ------------------------------------------------------------------------------------------------------

	/** {@code count} colossi get up out of the ground round {@code giant}, facing whoever it is after. */
	static void rise(ServerLevel level, AetherGiant giant, int count) {
		if (Beasts.RUNE_COLOSSUS == null) {
			return;
		}
		RandomSource random = giant.getRandom();
		for (int i = 0; i < count; i++) {
			RuneColossus colossus = Beasts.RUNE_COLOSSUS.create(level, EntitySpawnReason.EVENT);
			if (colossus == null) {
				return;
			}
			BlockPos at = null;
			for (int tries = 0; tries < 8 && at == null; tries++) {
				double turn = random.nextDouble() * Math.PI * 2.0, far = Mth.lerp(random.nextDouble(), RISE_MIN, RISE_MAX);
				BlockPos column = BlockPos.containing(giant.getX() + Math.cos(turn) * far, giant.getY(), giant.getZ() + Math.sin(turn) * far);
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (level.isLoaded(top) && Math.abs(top.getY() - giant.getY()) < 12 && level.getFluidState(top).isEmpty()
					&& level.noCollision(colossus.getType().getSpawnAABB(top.getX() + 0.5, top.getY(), top.getZ() + 0.5))) {
					at = top;
				}
			}
			if (at == null) {
				continue;
			}
			LivingEntity foe = giant.getTarget();
			float yaw = foe == null ? random.nextFloat() * 360.0F
				: (float) (Mth.atan2(foe.getZ() - at.getZ() - 0.5, foe.getX() - at.getX() - 0.5) * Mth.RAD_TO_DEG) - 90.0F;
			colossus.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			colossus.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			colossus.giant = giant.getUUID();
			colossus.setPersistenceRequired();
			level.addFreshEntity(colossus);
			colossus.act("spawn", RISE_TICKS);
			colossus.setTarget(foe);
			colossus.playSound(SoundEvents.DEEPSLATE_BREAK, 2.0F, 0.5F);
			colossus.rubble(level, 30);
		}
	}
}
