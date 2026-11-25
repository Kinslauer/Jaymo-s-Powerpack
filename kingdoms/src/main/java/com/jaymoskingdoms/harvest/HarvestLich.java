package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.KitchenCompat;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Harvest Lich (a port of Seasonal: The Wicked Harvest's, MIT; see {@code licenses/seasonal}): a scythe-bearer in a cape
 * and a crown of spikes, raised at a ruined barn's altar ({@link HarvestAltarBlock}) and riding a {@link LichSteed}. A boss
 * with a bar. Its moves:
 * <ul>
 * <li>It charges down anyone out of reach on its steed ({@link Charge}): whoever the steed rides through is hurt and thrown.</li>
 * <li>It lobs rotten pumpkins ({@link Lob}) that burst and poison where they land.</li>
 * <li>It calls wicked pumpkins ({@link Summon}), up to {@link #FLIGHT} at a time, that float round its foe and spit fire.</li>
 * <li>Close in, it cuts with the scythe.</li>
 * </ul>
 * Beaten, it drops the Spark of the Harvest (Kitchen's) that wakes scarecrows, and wicked resin. Without Kitchen it drops only the resin.
 */
public final class HarvestLich extends Monster {
	// ---- tuning ----
	static final double HEALTH = 320.0, ARMOUR = 6.0, SWING = 8.0, SPEED = 0.22, FOLLOW = 48.0, KNOCK_RESIST = 0.8;
	/** A charge: farthest and nearest target (blocks) it starts from, how long it lasts, how long it waits after (ticks), the steed's speed, what it does. */
	static final double CHARGE_MAX = 22.0, CHARGE_MIN = 8.0, CHARGE_SPEED = 2.2, CHARGE_REACH = 2.4, CHARGE_KNOCK = 1.5;
	static final int CHARGE_TICKS = 50, CHARGE_WAIT = 260;
	static final float CHARGE_DAMAGE = 10.0F;
	/** A lobbed pumpkin: nearest and farthest (blocks), the wind-up and the wait between (ticks). */
	static final double LOB_MIN = 5.0, LOB_MAX = 26.0;
	static final int LOB_WINDUP = 20, LOB_WAIT = 150;
	/** The flight of wicked pumpkins: how many at once, ticks between each new one, how near (blocks) they are counted. */
	static final int FLIGHT = 3, SUMMON_WAIT = 300;
	static final double MUSTER = 32.0;
	/** The Spark of the Harvest it drops (least, most), the wicked resin (least, most) and the experience. */
	static final int SPARKS_MIN = 3, SPARKS_MAX = 5, RESIN_MIN = 2, RESIN_MAX = 4, XP = 500;

	private boolean arrived;
	private final ServerBossEvent bar = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), BossEvent.BossBarColor.YELLOW,
		BossEvent.BossBarOverlay.NOTCHED_6);

	public HarvestLich(EntityType<? extends HarvestLich> type, Level level) {
		super(type, level);
		this.xpReward = XP;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOUR)
			.add(Attributes.ATTACK_DAMAGE, SWING).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCK_RESIST);
	}

	/** On the lich's side: the lich, its steed, its pumpkins and what they throw. None of them hurt each other. */
	public static boolean isOnLichSide(Entity entity) {
		return entity instanceof HarvestLich || entity instanceof LichSteed || entity instanceof WickedPumpkin
			|| entity instanceof WickedOrb || entity instanceof ThrownRottenPumpkin;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new Charge());
		this.goalSelector.addGoal(1, new Lob());
		this.goalSelector.addGoal(1, new Summon());
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
	}

	@Override
	public void stopRiding() {
		// (it will not step off a living steed; the steed's death is what unseats it)
		if (!this.isRemoved() && this.getVehicle() instanceof LichSteed steed && steed.isAlive()) {
			return;
		}
		super.stopRiding();
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.DROWN)
			|| source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
			|| super.isInvulnerableTo(level, source);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.arrived) {
			this.arrived = true;
			this.mount(level);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.getVehicle() instanceof LichSteed steed) {
			// (the steed is kept as long as its rider is: it can't be left to wither)
			steed.setHealth(Math.max(steed.getHealth(), 1.0F));
		}
		if (this.tickCount % 10 == 0) {
			level.sendParticles(Fx.wisp(WickedHarvest.FIRE, 0.7F), this.getX(), this.getY() + 1.8, this.getZ(), 2, 0.4, 0.8, 0.4, 0.0);
		}
	}

	private void mount(ServerLevel level) {
		LichSteed steed = WickedHarvest.STEED.create(level, EntitySpawnReason.TRIGGERED);
		if (steed == null) {
			return;
		}
		steed.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
		steed.setYBodyRot(this.getYRot());
		steed.setYHeadRot(this.getYRot());
		steed.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		steed.setDropChance(EquipmentSlot.SADDLE, 0.0F);
		steed.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
		steed.setDropChance(EquipmentSlot.BODY, 0.0F);
		level.addFreshEntity(steed);
		this.startRiding(steed, true, true);
		level.playSound(null, this.blockPosition(), SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	@Override
	public void die(DamageSource source) {
		if (this.getVehicle() instanceof LichSteed steed) {
			steed.discard();
		}
		if (this.level() instanceof ServerLevel level) {
			Fx.blast(level, this.position().add(0.0, 1.5, 0.0), 3.0F, WickedHarvest.FIRE);
			Fx.ring(level, this.position().add(0.0, 0.3, 0.0), 3.5F, WickedHarvest.SOUL);
		}
		super.die(source);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		Item spark = KitchenCompat.item("harvest_spark");
		if (spark != null) {
			this.spawnAtLocation(level, new ItemStack(spark, SPARKS_MIN + this.random.nextInt(SPARKS_MAX - SPARKS_MIN + 1)));
		}
		this.spawnAtLocation(level, new ItemStack(WickedHarvest.WICKED_RESIN, RESIN_MIN + this.random.nextInt(RESIN_MAX - RESIN_MIN + 1)));
		this.spawnAtLocation(level, new ItemStack(Items.GOLD_INGOT, 4 + this.random.nextInt(5)));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_SKELETON_AMBIENT;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
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
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bar.setName(this.getDisplayName());
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Arrived", this.arrived);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.arrived = input.getBooleanOr("Arrived", false);
		if (this.hasCustomName()) {
			this.bar.setName(this.getDisplayName());
		}
	}

	// ---- moves ----

	/** Down on a foe out of reach, the steed at a gallop: who it rides through is hurt and thrown. */
	private final class Charge extends Goal {
		private int ends, readyAt;
		private final Set<Integer> struck = new HashSet<>();

		Charge() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = HarvestLich.this.getTarget();
			if (target == null || !(HarvestLich.this.getVehicle() instanceof LichSteed) || HarvestLich.this.tickCount < this.readyAt) {
				return false;
			}
			double d = HarvestLich.this.distanceTo(target);
			return d >= CHARGE_MIN && d <= CHARGE_MAX && HarvestLich.this.hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			return HarvestLich.this.tickCount < this.ends && HarvestLich.this.getTarget() != null && HarvestLich.this.getVehicle() instanceof LichSteed;
		}

		@Override
		public void start() {
			this.ends = HarvestLich.this.tickCount + CHARGE_TICKS;
			this.struck.clear();
			HarvestLich.this.level().playSound(null, HarvestLich.this.blockPosition(), SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F);
		}

		@Override
		public void stop() {
			this.readyAt = HarvestLich.this.tickCount + CHARGE_WAIT;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = HarvestLich.this.getTarget();
			if (target == null || !(HarvestLich.this.getVehicle() instanceof LichSteed steed) || !(HarvestLich.this.level() instanceof ServerLevel level)) {
				return;
			}
			HarvestLich.this.getNavigation().moveTo(target, CHARGE_SPEED);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, steed.getBoundingBox().inflate(CHARGE_REACH - 1.0),
				e -> e.isAlive() && !isOnLichSide(e) && !this.struck.contains(e.getId()))) {
				this.struck.add(hit.getId());
				hit.hurtServer(level, level.damageSources().mobAttack(HarvestLich.this), CHARGE_DAMAGE);
				Vec3 away = hit.position().subtract(steed.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : away.normalize().scale(CHARGE_KNOCK);
				hit.push(away.x, 0.5, away.z);
				hit.syncVelocity = true;
				Fx.blast(level, hit.position().add(0.0, 1.0, 0.0), 0.8F, WickedHarvest.FIRE);
			}
		}
	}

	/** A rotten pumpkin lobbed at a foe in the middle distance, after a moment's wind-up. */
	private final class Lob extends Goal {
		private int readyAt, wind;

		Lob() {
			this.setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = HarvestLich.this.getTarget();
			if (target == null || HarvestLich.this.tickCount < this.readyAt) {
				return false;
			}
			double d = HarvestLich.this.distanceTo(target);
			return d >= LOB_MIN && d <= LOB_MAX && HarvestLich.this.hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			return this.wind > 0 && HarvestLich.this.getTarget() != null;
		}

		@Override
		public void start() {
			this.wind = LOB_WINDUP;
			HarvestLich.this.level().playSound(null, HarvestLich.this.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 0.6F);
		}

		@Override
		public void stop() {
			this.readyAt = HarvestLich.this.tickCount + LOB_WAIT;
			this.wind = 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = HarvestLich.this.getTarget();
			if (target == null) {
				return;
			}
			HarvestLich.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.wind <= 0 && HarvestLich.this.level() instanceof ServerLevel level) {
				Vec3 from = HarvestLich.this.getEyePosition().add(0.0, 0.4, 0.0);
				Vec3 to = target.position().add(target.getDeltaMovement().multiply(8.0, 0.0, 8.0)).add(0.0, 0.2, 0.0);
				ThrownRottenPumpkin.lob(level, HarvestLich.this, from, to, LOB_MAX + 2.0);
				level.playSound(null, HarvestLich.this.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0F, 0.5F);
			}
		}
	}

	/** Calls a wicked pumpkin to its side now and then, while a foe is in sight and fewer than {@link #FLIGHT} are out. */
	private final class Summon extends Goal {
		private int readyAt;

		@Override
		public boolean canUse() {
			return HarvestLich.this.getTarget() != null && HarvestLich.this.tickCount >= this.readyAt && HarvestLich.this.level() instanceof ServerLevel;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			this.readyAt = HarvestLich.this.tickCount + SUMMON_WAIT;
			if (!(HarvestLich.this.level() instanceof ServerLevel level)) {
				return;
			}
			int out = level.getEntitiesOfClass(WickedPumpkin.class, HarvestLich.this.getBoundingBox().inflate(MUSTER), p -> p.isAlive() && p.lich(level) == HarvestLich.this).size();
			if (out >= FLIGHT) {
				return;
			}
			WickedPumpkin pumpkin = WickedHarvest.PUMPKIN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (pumpkin == null) {
				return;
			}
			double a = HarvestLich.this.getRandom().nextDouble() * Math.PI * 2.0;
			pumpkin.snapTo(HarvestLich.this.getX() + Math.cos(a) * 3.0, HarvestLich.this.getY() + 2.5 + HarvestLich.this.getRandom().nextFloat(),
				HarvestLich.this.getZ() + Math.sin(a) * 3.0, HarvestLich.this.getRandom().nextFloat() * 360.0F, 0.0F);
			pumpkin.setLich(HarvestLich.this);
			level.addFreshEntity(pumpkin);
			Fx.ring(level, pumpkin.position(), 0.9F, WickedHarvest.FIRE);
			level.playSound(null, pumpkin.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.9F, 0.7F);
		}
	}
}
