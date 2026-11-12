package com.jaymoskingdoms.monsters;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.56) Ogres (after Mo' Creatures'): huge, slow brutes that hammer with their fists, or with a spiked club when they
 * have two heads. Now and then one brings both arms down on the ground: everyone near is hurt and thrown (no blocks are
 * broken). The green ogre roams the hills; the cave ogre (grey, tougher) the mountains by night; the fire ogre the Nether
 * and the badlands, and it sets what it strikes alight.
 * <p>
 * (0.7.623, Rivals patch 8) Andy: "add armored ogres with shields and different skin colors matching orcs". The armoured
 * ogre ({@link #ARMOURED}) lives in orc camps and ogre holds: in plate, its skin one of the orcs' tones, with a great
 * shield it holds up while its foe is near. Blows and shots from in front glance off it; it can't be vaulted (too big).
 * The shield comes off to a heavy blow ({@link #HEAVY_BLOW}) or a blast, a Yank, or a stun ({@link #tearShield}, from
 * Jaymo's Powers' {@code combat/Shields}); then it fights on bare, and angry.
 */
public class Ogre extends LegendMonster {
	public static final int GREEN = 0, CAVE = 1, FIRE = 2, ARMOURED = 3;
	/** The kind of a brute made by a rite ({@link MadeBrute}). */
	public static final int BRUTE = 4;
	/** (0.7.623) The armoured ogre: how many skins (orc tones), its health, plate, pace and blow. */
	public static final int ARMOURED_TONES = 6;
	static final double ARMOURED_HEALTH = 80.0, ARMOURED_ARMOR = 12.0, ARMOURED_TOUGHNESS = 4.0, ARMOURED_SPEED = 0.22, ARMOURED_DAMAGE = 10.0;
	/** Its guard is up while its foe is this near (blocks) and it isn't swinging. */
	static final double GUARD_RANGE = 10.0;
	/** Blows from within this much of where it faces (degrees, either side) land on the shield. */
	static final float GUARD_ARC = 75.0F;
	/** A blow this hard on the shield (or any blast) knocks it off; this share of it still gets through. */
	public static final float HEAVY_BLOW = 10.0F, HEAVY_THROUGH = 0.5F;
	/** Without its shield it comes on faster: its pace times this. */
	static final double BARE_RAGE = 1.15;
	static final double HEALTH = 50.0, CAVE_HEALTH = 70.0, SPEED = 0.24, DAMAGE = 8.0, KNOCKBACK_RESISTANCE = 0.8;
	/** Its ground smash: how often it may (ticks), how near its target must be, its reach, damage and throw. */
	static final int SMASH_TICKS = 140;
	static final double SMASH_TRIGGER = 4.0, SMASH_RANGE = 5.0, SMASH_THROW = 1.2;
	static final float SMASH_DAMAGE = 7.0F;
	/** (0.7.549) Its smash's shake: felt this far (blocks), this hard (degrees). */
	static final double SMASH_SHAKE_RANGE = 18.0;
	static final float SMASH_SHAKE = 1.8F;
	/** How long its fists take to come down in a smash (ticks). */
	static final int SMASH_WINDUP = 8;
	/** One ogre in this many has two heads (and a club). */
	static final int TWO_HEAD_ODDS = 2;
	/** How long the fire ogre's blows burn (seconds). */
	static final float FIRE_SECONDS = 5.0F;

	private static final EntityDataAccessor<Boolean> TWO_HEADS = SynchedEntityData.defineId(Ogre.class, EntityDataSerializers.BOOLEAN);
	/** (0.7.623) An armoured ogre's shield: still has it; has it up. */
	private static final EntityDataAccessor<Boolean> SHIELD = SynchedEntityData.defineId(Ogre.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> GUARD = SynchedEntityData.defineId(Ogre.class, EntityDataSerializers.BOOLEAN);
	private static final net.minecraft.resources.Identifier RAGE_ID = com.jaymoskingdoms.JaymosKingdoms.id("ogre_bare_rage");

	private final int kind;
	private int smashCooldown = SMASH_TICKS, windup;

	public Ogre(EntityType<? extends Ogre> type, Level level, int kind) {
		super(type, level);
		this.kind = kind;
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes(int kind) {
		if (kind == ARMOURED) {
			return createMonsterAttributes().add(Attributes.MAX_HEALTH, ARMOURED_HEALTH).add(Attributes.MOVEMENT_SPEED, ARMOURED_SPEED)
				.add(Attributes.ATTACK_DAMAGE, ARMOURED_DAMAGE).add(Attributes.ARMOR, ARMOURED_ARMOR).add(Attributes.ARMOR_TOUGHNESS, ARMOURED_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.ATTACK_KNOCKBACK, 1.5).add(Attributes.STEP_HEIGHT, 1.0);
		}
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, kind == CAVE ? CAVE_HEALTH : HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5).add(Attributes.STEP_HEIGHT, 1.0);
	}

	public int kind() {
		return this.kind;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && !com.jaymoskingdoms.InBalming.spares(this, target)
				&& !com.jaymoskingdoms.OgreHolds.spares(this, target)));
		// ((0.7.474) never a goblin or an orc: the ogres throw in with the greenskins, OgreHolds)
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true,
			(target, level) -> !com.jaymoskingdoms.OgreHolds.spares(this, target)));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(TWO_HEADS, false);
		entityData.define(SHIELD, false);
		entityData.define(GUARD, false);
	}

	/** (0.7.623) An armoured ogre that still has its shield. */
	public boolean shield() {
		return this.entityData.get(SHIELD);
	}

	/** (0.7.623) An armoured ogre with its shield up just now. */
	public boolean guarding() {
		return this.entityData.get(GUARD);
	}

	@Override
	protected int variants() {
		return this.kind == ARMOURED ? ARMOURED_TONES : 1;
	}

	/**
	 * (0.7.623) Knock {@code target}'s shield off, if it's an armoured ogre that still has one ({@code by}: who did it, or
	 * null). Jaymo's Powers' Yank and stuns call this ({@code combat/Shields}); a heavy blow does it here.
	 */
	public static boolean tearShield(LivingEntity target, @org.jspecify.annotations.Nullable LivingEntity by) {
		if (!(target instanceof Ogre ogre) || !ogre.shield() || !(target.level() instanceof ServerLevel level)) {
			return false;
		}
		ogre.loseShield(level, by);
		return true;
	}

	private void loseShield(ServerLevel level, @org.jspecify.annotations.Nullable LivingEntity by) {
		this.entityData.set(SHIELD, false);
		this.entityData.set(GUARD, false);
		Vec3 at = this.position().add(this.getLookAngle().multiply(1.2, 0.0, 1.2)).add(0.0, this.getBbHeight() * 0.45, 0.0);
		level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS.defaultBlockState()),
			at.x, at.y, at.z, 30, 0.6, 0.8, 0.6, 0.1);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.5, 0.6, 0.5, 0.2);
		this.playSound(SoundEvents.SHIELD_BREAK.value(), 1.6F, 0.6F);
		this.playSound(SoundEvents.ITEM_BREAK.value(), 1.0F, 0.5F);
		this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.8F);
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(RAGE_ID)) {
			speed.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE_ID, BARE_RAGE - 1.0,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (by != null && this.getTarget() == null && by.isAlive()) {
			this.setTarget(by);
		}
	}

	/** (0.7.623) Whether a blow from {@code source} comes from in front, where the shield is. */
	private boolean facing(DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		float to = (float)(Math.atan2(from.z - this.getZ(), from.x - this.getX()) * (180.0 / Math.PI)) - 90.0F;
		return Math.abs(net.minecraft.util.Mth.wrapDegrees(to - this.yBodyRot)) <= GUARD_ARC;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.shield() && this.guarding() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD) && this.facing(source)) {
			if (amount >= HEAVY_BLOW || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
				// (a blow that would fell a tree: the shield goes, and some of the blow through it)
				this.loseShield(level, source.getEntity() instanceof LivingEntity by ? by : null);
				return super.hurtServer(level, source, amount * HEAVY_THROUGH);
			}
			this.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.2F, 0.6F + this.random.nextFloat() * 0.1F);
			if (source.getDirectEntity() instanceof LivingEntity hitter && hitter == source.getEntity()) {
				// (it shoves back off the shield)
				Vec3 away = hitter.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.5);
				hitter.push(away.x, 0.15, away.z);
				hitter.needsSync = true;
			}
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	public boolean twoHeads() {
		return this.entityData.get(TWO_HEADS);
	}

	/** (0.7.426) Gives it two heads (and a club) or one: an outpost's brute, the king's guard. */
	public void setTwoHeads(boolean two) {
		// ((0.7.623) an armoured ogre has the one, under its helm)
		this.entityData.set(TWO_HEADS, two && this.kind != ARMOURED);
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level) {
		if (this.kind == ARMOURED) {
			this.entityData.set(TWO_HEADS, false);
			this.entityData.set(SHIELD, true);
			return this.random.nextInt(ARMOURED_TONES);
		}
		this.entityData.set(TWO_HEADS, this.random.nextInt(TWO_HEAD_ODDS) == 0);
		return 0;
	}

	@Override
	protected int nextArm() {
		if (this.kind == ARMOURED) {
			// ((0.7.623) the shield's on the left)
			return RIGHT;
		}
		// a two-headed ogre favours its club arm
		return this.twoHeads() && this.random.nextInt(3) > 0 ? LEFT : this.random.nextBoolean() ? LEFT : RIGHT;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		// (0.7.433) one Ambrose calmed: at its keeper's side
		com.jaymoskingdoms.InBalming.ogreStep(level, this);
		if (this.shield()) {
			// (0.7.623) its shield up while its foe is near and it isn't striking
			LivingEntity foe = this.getTarget();
			boolean up = foe != null && foe.isAlive() && this.distanceToSqr(foe) < GUARD_RANGE * GUARD_RANGE && this.windup == 0 && this.swingingArm() == 0;
			if (up != this.guarding()) {
				this.entityData.set(GUARD, up);
			}
		}
		if (this.windup > 0) {
			if (--this.windup == 0) {
				this.smash(level);
			}
			return;
		}
		if (this.smashCooldown > 0) {
			this.smashCooldown--;
			return;
		}
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && this.distanceToSqr(target) < SMASH_TRIGGER * SMASH_TRIGGER + this.getBbWidth()) {
			this.swingArm(BOTH);
			this.windup = SMASH_WINDUP;
			this.smashCooldown = SMASH_TICKS + this.random.nextInt(SMASH_TICKS / 2);
			this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.7F);
		}
	}

	/** Both fists into the ground: everything near is hurt and thrown up and away. */
	private void smash(ServerLevel level) {
		Vec3 at = this.position().add(this.getLookAngle().multiply(1.5, 0.0, 1.5));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 2, 0.6, 0.1, 0.6, 0.0);
		level.sendParticles(this.kind == FIRE ? ParticleTypes.FLAME : ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 30, SMASH_RANGE / 3, 0.1, SMASH_RANGE / 3, 0.05);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
		// (0.7.549) the ground jumps under everyone near
		com.jaymoskingdoms.PowersCompat.shakeAround(level, at, SMASH_SHAKE_RANGE, SMASH_SHAKE);
		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SMASH_RANGE),
			e -> e != this && !(e instanceof Ogre) && e.isAlive() && e.distanceToSqr(at) < SMASH_RANGE * SMASH_RANGE
				// ((0.7.474) the greenskins about it are spared the blow, so a fight in an orc camp doesn't turn them on each other)
				&& !com.jaymoskingdoms.OgreHolds.spares(this, e))) {
			if (hit instanceof Player p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			hit.hurtServer(level, this.damageSources().mobAttack(this), SMASH_DAMAGE);
			Vec3 away = hit.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize().scale(SMASH_THROW);
			hit.push(away.x, 0.6, away.z);
			hit.needsSync = true;
			if (this.kind == FIRE) {
				hit.igniteForSeconds(FIRE_SECONDS);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && this.kind == FIRE) {
			target.igniteForSeconds(FIRE_SECONDS);
		}
		return hit;
	}

	@Override
	public boolean fireImmune() {
		return this.kind == FIRE || super.fireImmune();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.4F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.1F;
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("two_heads", this.twoHeads());
		output.putBoolean("shield", this.shield());
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(TWO_HEADS, input.getBooleanOr("two_heads", false));
		this.entityData.set(SHIELD, input.getBooleanOr("shield", false));
	}
}
