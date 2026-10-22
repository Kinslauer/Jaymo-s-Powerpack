package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.145) The Aether's Giants: the Highfield, Magnetic, Arctic and Irradiated Giants, the last of the Aether's remnants
 * on the Overworld, each risen beside a monolith of its element ({@link Monoliths}). All four stamp the ground (everyone
 * near is hurt and thrown); each also has its element's own power ({@link #power}) and an aura round it:
 * <ul>
 * <li>Highfield: a gust that hurls everyone near it into the air; it heals while it stands on grass or moss.</li>
 * <li>Magnetic: it drags everyone near it in, harder the more metal armour they wear.</li>
 * <li>Arctic: its cold freezes whoever stands near it; its breath slows everyone round it to a crawl.</li>
 * <li>Irradiated: its glow sickens whoever stands near it; a flare poisons, and withers those closest.</li>
 * </ul>
 * Badly hurt it rages: faster, and its power comes twice as often. Its boss bar is its element's colour. The first Giant
 * to fall in a world raises the world tier ({@link Monoliths#slain}).
 */
public class AetherGiant extends Monster {
	/**
	 * (0.7.151) Andy: "make all the giants the same size as warrior born great apes". As tall as a Warrior-born's Great
	 * Ape (a player's 1.8 blocks, 17 times over: about 30 blocks), and as broad in proportion. The vanilla Giant too
	 * ({@link Monoliths#sizeVanillaGiant}).
	 */
	public static final float HEIGHT = 30.6F, WIDTH = 10.2F;
	static final double HEALTH = 320.0, ARMOR = 10.0, TOUGHNESS = 4.0, DAMAGE = 14.0, SPEED = 0.23, KNOCKBACK_RESISTANCE = 1.0;
	static final int EXPERIENCE = 250;
	/** Its stamp: how often it may (ticks), how near its target must be, its reach, damage, throw, and wind-up (ticks). */
	static final int STAMP_TICKS = 160, STAMP_WINDUP = 12;
	static final double STAMP_TRIGGER = 12.0, STAMP_RANGE = 14.0, STAMP_THROW = 1.4;
	static final float STAMP_DAMAGE = 12.0F;
	/** Its element's power: how often (ticks; half that when raging) and how far it reaches. */
	static final int POWER_TICKS = 120;
	static final double POWER_RANGE = 24.0;
	/** Its aura's reach (the Arctic's cold, the Irradiated's sickness). */
	static final double AURA_RANGE = 14.0;
	/** Below this share of its health it rages, this much faster. */
	static final float RAGE_AT = 0.3F;
	static final double RAGE_SPEED = 0.25;
	/** Highfield: the gust's lift and push; the healing on grass (health a second). */
	static final double GUST_LIFT = 1.3, GUST_PUSH = 0.9;
	static final float GRASS_HEAL = 2.0F;
	/** Magnetic: the pull, and how much more each piece of metal armour adds. */
	static final double PULL = 0.9, PULL_PER_METAL = 0.35;
	/** Arctic: frost added a second to everyone in its aura (ticks), the most over the freezing line, and its breath's slow. */
	static final int FROST_PER_SECOND = 70, FROST_OVER = 120, BREATH_TICKS = 120, BREATH_SLOW = 2;
	/** Irradiated: the sickness's poison (ticks) a second in its aura, the flare's poison and wither, and the wither's reach. */
	static final int SICKNESS_TICKS = 60, FLARE_POISON_TICKS = 140, FLARE_WITHER_TICKS = 100;
	static final double FLARE_WITHER_RANGE = 12.0;
	/** The flare's flash (Ambrosium gold-green). */
	static final int FLARE_COLOR = 0xFFC8F060;
	/** Where its stamp lands, this far ahead of its middle (blocks). */
	static final double STAMP_AHEAD = 6.0;
	/** Too big to path through woods: with no path to its target it strides straight at it, and every this many ticks the trees it walks into are felled (away from it) and the leaves torn down. */
	static final int TRAMPLE_EVERY = 5;
	/** How high up its legs it fells trees (blocks). */
	static final int TRAMPLE_HEIGHT = 6;
	/** How long a swing shows (ticks). */
	static final int SWING_TICKS = 12;
	public static final int LEFT = 1, RIGHT = 2, BOTH = 3;

	private static final EntityDataAccessor<Byte> SWING = SynchedEntityData.defineId(AetherGiant.class, EntityDataSerializers.BYTE);
	private static final Identifier RAGE_ID = Identifier.fromNamespaceAndPath("jaymoskingdoms", "giant_rage");

	private final Element element;
	/** The monolith it rose beside (null if it was summoned). */
	private @Nullable BlockPos monolith;
	private int stampCooldown = STAMP_TICKS, windup, powerCooldown = POWER_TICKS, swingTicks;
	private boolean raging;
	/** (Client) how far each arm's swing has run. */
	private int leftCounter, rightCounter;
	/** (Server) its boss bar, made when first wanted. */
	private @Nullable ServerBossEvent bar;

	public AetherGiant(EntityType<? extends AetherGiant> type, Level level, Element element) {
		super(type, level);
		this.element = element;
		this.xpReward = EXPERIENCE;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.ATTACK_KNOCKBACK, 2.0).add(Attributes.STEP_HEIGHT, 3.0);
	}

	public Element element() {
		return this.element;
	}

	public @Nullable BlockPos monolith() {
		return this.monolith;
	}

	void bind(BlockPos monolith) {
		this.monolith = monolith.immutable();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(SWING, (byte)0);
	}

	/** Which arm is swinging (both for a stamp), 0 for none. */
	public int swingingArm() {
		return this.entityData.get(SWING);
	}

	public int leftCounter() {
		return this.leftCounter;
	}

	public int rightCounter() {
		return this.rightCounter;
	}

	private void swingArm(int arm) {
		this.entityData.set(SWING, (byte)arm);
		this.swingTicks = SWING_TICKS;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.swingArm(this.random.nextBoolean() ? LEFT : RIGHT);
			if (target instanceof LivingEntity living) {
				this.touch(living, 1);
			}
		}
		return hit;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			if (this.swingTicks > 0 && --this.swingTicks == 0) {
				this.entityData.set(SWING, (byte)0);
			}
			return;
		}
		int arm = this.swingingArm();
		this.leftCounter = (arm & LEFT) != 0 ? this.leftCounter + 1 : 0;
		this.rightCounter = (arm & RIGHT) != 0 ? this.rightCounter + 1 : 0;
		// its element's motes drift off it
		if (this.random.nextInt(3) == 0) {
			this.level().addParticle(this.element.mote, this.getRandomX(1.0), this.getRandomY(), this.getRandomZ(1.0), 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.updateBar(level);
		if (!this.raging && this.getHealth() < this.getMaxHealth() * RAGE_AT) {
			this.rage();
		}
		if (this.tickCount % 20 == 0) {
			this.aura(level);
		}
		if (this.windup > 0) {
			if (--this.windup == 0) {
				this.stamp(level);
			}
			return;
		}
		LivingEntity target = this.getTarget();
		boolean fighting = target != null && target.isAlive();
		if (fighting && this.getNavigation().isDone()
			&& this.distanceToSqr(target) > Mth.square(this.getBbWidth() / 2.0 + STAMP_TRIGGER / 2.0)) {
			this.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.1);
		}
		if (this.tickCount % TRAMPLE_EVERY == 0 && this.horizontalCollision && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			this.trample(level);
		}
		if (this.powerCooldown > 0) {
			this.powerCooldown--;
		} else if (fighting && this.distanceToSqr(target) < POWER_RANGE * POWER_RANGE) {
			this.power(level);
			this.powerCooldown = this.raging ? POWER_TICKS / 2 : POWER_TICKS;
		}
		if (this.stampCooldown > 0) {
			this.stampCooldown--;
		} else if (fighting && this.distanceToSqr(target) < STAMP_TRIGGER * STAMP_TRIGGER + this.getBbWidth()) {
			this.swingArm(BOTH);
			this.windup = STAMP_WINDUP;
			this.stampCooldown = STAMP_TICKS + this.random.nextInt(STAMP_TICKS / 2);
			this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.5F);
		}
	}

	/** The woods it walks into: trees felled away from it (only natural ones, never a building's logs), leaves torn down. */
	private void trample(ServerLevel level) {
		AABB box = this.getBoundingBox().inflate(1.0, 0.0, 1.0);
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), y1 = Mth.floor(box.maxY), z1 = Mth.floor(box.maxZ);
		// the trees first (they need their leaves to count as trees), then whatever leaves are left in its way
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, Math.min(y1, y0 + TRAMPLE_HEIGHT), z1)) {
			if (level.getBlockState(pos).is(BlockTags.LOGS)) {
				Trees.trample(level, pos.immutable(), this);
			}
		}
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				level.destroyBlock(pos, true, this);
			}
		}
	}

	private void rage() {
		this.raging = true;
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(RAGE_ID, RAGE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		this.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.4F);
	}

	// ---- its powers ------------------------------------------------------------------------------------------------

	/** Its foot brought down: everyone near is hurt and thrown, and its element's touch goes with it. */
	private void stamp(ServerLevel level) {
		Vec3 at = this.position().add(this.getLookAngle().multiply(STAMP_AHEAD, 0.0, STAMP_AHEAD));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 3, 1.0, 0.1, 1.0, 0.0);
		level.sendParticles(this.element.mote, at.x, at.y + 0.3, at.z, 50, STAMP_RANGE / 3, 0.2, STAMP_RANGE / 3, 0.05);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.5F);
		for (LivingEntity hit : this.near(level, at, STAMP_RANGE)) {
			hit.hurtServer(level, this.damageSources().mobAttack(this), STAMP_DAMAGE);
			Vec3 away = hit.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize().scale(STAMP_THROW);
			hit.push(away.x, this.element == Element.HIGHFIELD ? 0.9 : 0.6, away.z);
			hit.needsSync = true;
			this.touch(hit, 1);
		}
	}

	/** Its element's own power, on everyone in reach. */
	private void power(ServerLevel level) {
		Vec3 at = this.position();
		switch (this.element) {
			case HIGHFIELD -> {
				// a gust off the sky-meadows: everyone near hurled up and away
				level.sendParticles(this.element.mote, at.x, at.y + 1.0, at.z, 60, POWER_RANGE / 3, 0.5, POWER_RANGE / 3, 0.1);
				this.playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2.0F, 0.6F);
				for (LivingEntity hit : this.near(level, at, POWER_RANGE)) {
					Vec3 away = hit.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize().scale(GUST_PUSH);
					hit.push(away.x, GUST_LIFT, away.z);
					hit.needsSync = true;
				}
			}
			case MAGNETIC -> {
				// the lodestone's pull: everyone dragged in, the armoured hardest
				this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
				for (LivingEntity hit : this.near(level, at, POWER_RANGE)) {
					Vec3 to = at.subtract(hit.position());
					double pull = PULL + PULL_PER_METAL * metal(hit);
					Vec3 in = to.multiply(1.0, 0.0, 1.0).normalize().scale(pull);
					hit.push(in.x, 0.25, in.z);
					hit.needsSync = true;
					for (int i = 1; i < 8; i++) {
						Vec3 p = hit.position().add(to.scale(i / 8.0));
						level.sendParticles(this.element.mote, p.x, p.y + 1.0, p.z, 2, 0.1, 0.1, 0.1, 0.0);
					}
				}
			}
			case ARCTIC -> {
				// its breath: everyone round it slowed to a crawl
				level.sendParticles(this.element.mote, at.x, at.y + 2.0, at.z, 80, POWER_RANGE / 3, 1.0, POWER_RANGE / 3, 0.02);
				this.playSound(SoundEvents.POWDER_SNOW_STEP, 2.0F, 0.5F);
				for (LivingEntity hit : this.near(level, at, POWER_RANGE)) {
					this.touch(hit, 2);
				}
			}
			case IRRADIATED -> {
				// an Ambrosium flare: poison for all near, wither for the closest
				level.sendParticles(this.element.mote, at.x, at.y + 2.0, at.z, 80, POWER_RANGE / 3, 1.0, POWER_RANGE / 3, 0.05);
				level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, FLARE_COLOR), at.x, at.y + 3.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				this.playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
				for (LivingEntity hit : this.near(level, at, POWER_RANGE)) {
					this.touch(hit, 2);
				}
			}
		}
	}

	/** Every second: the Arctic's cold and the Irradiated's sickness on those near; the Highfield's healing on grass. */
	private void aura(ServerLevel level) {
		switch (this.element) {
			case HIGHFIELD -> {
				BlockState under = level.getBlockState(this.blockPosition().below());
				if ((under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.MOSS_BLOCK)) && this.getHealth() < this.getMaxHealth()) {
					this.heal(GRASS_HEAL);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.5, this.getZ(), 4, 1.0, 0.3, 1.0, 0.0);
				}
			}
			case MAGNETIC -> {
			}
			case ARCTIC -> {
				// its own cold doesn't touch it
				this.setTicksFrozen(0);
				for (LivingEntity hit : this.near(level, this.position(), AURA_RANGE)) {
					hit.setTicksFrozen(Math.min(hit.getTicksFrozen() + FROST_PER_SECOND, hit.getTicksRequiredToFreeze() + FROST_OVER));
				}
			}
			case IRRADIATED -> {
				// nor its own sickness
				this.removeEffect(MobEffects.POISON);
				this.removeEffect(MobEffects.WITHER);
				for (LivingEntity hit : this.near(level, this.position(), AURA_RANGE)) {
					this.touch(hit, 0);
				}
			}
		}
	}

	/**
	 * Its element's mark on {@code hit}: {@code strength} 0 for its aura, 1 for a stamp or a blow, 2 for its power. Arctic
	 * slows; Irradiated poisons (and its power withers those closest); Magnetic's blows weigh you down.
	 */
	private void touch(LivingEntity hit, int strength) {
		switch (this.element) {
			case ARCTIC -> {
				if (strength >= 1) {
					hit.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BREATH_TICKS, strength >= 2 ? BREATH_SLOW : 0), this);
				}
			}
			case IRRADIATED -> {
				hit.addEffect(new MobEffectInstance(MobEffects.POISON, strength >= 2 ? FLARE_POISON_TICKS : SICKNESS_TICKS, 0), this);
				if (strength >= 2 && hit.distanceToSqr(this) < FLARE_WITHER_RANGE * FLARE_WITHER_RANGE) {
					hit.addEffect(new MobEffectInstance(MobEffects.WITHER, FLARE_WITHER_TICKS, 0), this);
				}
			}
			case MAGNETIC -> {
				if (strength == 1 && metal(hit) > 0) {
					hit.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, metal(hit) / 2), this);
				}
			}
			case HIGHFIELD -> {
			}
		}
	}

	/** Living things near {@code at} it may touch: not itself, not other Giants, not creative or spectating players. */
	private java.util.List<LivingEntity> near(ServerLevel level, Vec3 at, double range) {
		return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range), e -> e != this && !(e instanceof AetherGiant)
			&& e.isAlive() && e.distanceToSqr(at) < range * range && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
	}

	/** How many pieces of metal armour {@code entity} wears (iron, chainmail, gold, netherite). */
	static int metal(LivingEntity entity) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (path.contains("iron") || path.contains("chainmail") || path.contains("gold") || path.contains("netherite")) {
				n++;
			}
		}
		return n;
	}

	// ---- its bar ---------------------------------------------------------------------------------------------------

	private void updateBar(ServerLevel level) {
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), this.element.bar, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		com.jaymoskingdoms.BossBars.show(level, this, this.bar);
	}

	/** Its bar taken from everyone (it died or was unloaded). */
	void dropBar() {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
	}

	// ---- the rest --------------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// it was made to walk off cliffs
		return !source.is(DamageTypeTags.IS_FALL) && super.hurtServer(level, source, amount);
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
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.5F);
	}

	@Override
	public float getVoicePitch() {
		return 0.45F + this.random.nextFloat() * 0.1F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.monolith != null) {
			output.putLong("monolith", this.monolith.asLong());
		}
		output.putBoolean("raging", this.raging);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long packed = input.getLongOr("monolith", Long.MIN_VALUE);
		this.monolith = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
		if (this.monolith != null) {
			// it keeps to its monolith after a reload too
			this.setHomeTo(this.monolith, Monoliths.HOME_RADIUS);
		}
		this.raging = input.getBooleanOr("raging", false);
	}
}
