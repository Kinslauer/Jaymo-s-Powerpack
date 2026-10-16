package com.jaymospowers.power.zfighter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A fighter who isn't a player: a tournament's champion ({@link #CHAMPION}), who fights only the one he's set against,
 * or a tyrant ({@link #TYRANT}), a rogue Z Fighter who lands on a kingdom by night and fights everyone in it
 * ({@link ZFighterRaids}). Both punch, fire ki blasts at range and flash-step in behind a foe who runs; the tyrant
 * hovers up to foes above him, glows gold (Super), goes Super 2 at half health and carries a boss bar. Whoever hurt the
 * tyrant shares his prize.
 */
public class ZFighterRogue extends Monster {
	public static final int CHAMPION = 0;
	public static final int TYRANT = 1;

	// ---- tuning ----
	static final double CHAMPION_HEALTH = 80.0;
	static final double TYRANT_HEALTH = 600.0;
	/** Bare-hand damage; the weapon he's given adds its own (iron sword +5, iron axe +8). */
	static final double CHAMPION_DAMAGE = 3.0;
	static final double TYRANT_DAMAGE = 7.0;
	/** Base armour; the armour he wears adds the rest. */
	static final double CHAMPION_ARMOR = 2.0, TYRANT_ARMOR = 6.0;
	/** A champion's armour pieces: each is iron this often, else chainmail, else leather. */
	static final float CHAMPION_IRON = 0.5F, CHAMPION_CHAIN = 0.35F;
	static final float CHAMPION_BLAST = 6.0F;
	static final float TYRANT_BLAST = 14.0F;
	static final int BLAST_EVERY = 70;
	static final int STEP_EVERY = 100;
	static final double BLAST_RANGE = 32.0;
	static final double STEP_FAR = 9.0;
	static final double BOSS_SEEN = 96.0;
	static final int TYRANT_COLOR = 0xFFC830;
	static final int CHAMPION_COLOR = 0x8FD3FF;

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(ZFighterRogue.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(ZFighterRogue.class, EntityDataSerializers.INT);

	/** Who hurt the tyrant, and how much. */
	final Map<UUID, Float> hurtBy = new HashMap<>();
	/** The kingdom a tyrant fell on (its name, for the news). */
	String kingdom = "";
	private ServerBossEvent bar;
	private int blastIn = BLAST_EVERY;
	private int stepIn = STEP_EVERY;

	public ZFighterRogue(EntityType<? extends ZFighterRogue> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, CHAMPION_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, CHAMPION_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, 0.33)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ARMOR, CHAMPION_ARMOR)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, CHAMPION);
		builder.define(DATA_FORM, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// only a tyrant picks his own foes: the people of the kingdom, its golems, everyone
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> tyrant()));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true, (target, level) -> tyrant()));
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true, (target, level) -> tyrant()));
	}

	public int kind() {
		return this.entityData.get(DATA_KIND);
	}

	public boolean tyrant() {
		return kind() == TYRANT;
	}

	/** 0 base, 1 Super, 2 Super 2: the hair. */
	public int form() {
		return this.entityData.get(DATA_FORM);
	}

	/** Makes him a tyrant (named, Super, a boss bar) or a champion. */
	void become(int kind, Component name) {
		this.entityData.set(DATA_KIND, kind);
		boolean tyrant = kind == TYRANT;
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(tyrant ? TYRANT_HEALTH : CHAMPION_HEALTH);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tyrant ? TYRANT_DAMAGE : CHAMPION_DAMAGE);
		this.getAttribute(Attributes.ARMOR).setBaseValue(tyrant ? TYRANT_ARMOR : CHAMPION_ARMOR);
		arm(tyrant);
		this.setHealth(this.getMaxHealth());
		this.entityData.set(DATA_FORM, tyrant ? 1 : 0);
		this.setCustomName(name);
		this.setCustomNameVisible(true);
		this.setPersistenceRequired();
	}

	/**
	 * Armour and a weapon, never dropped: a tyrant in full iron with an iron axe, a champion in a mix of leather,
	 * chainmail and iron with an iron or stone sword.
	 */
	private void arm(boolean tyrant) {
		Item[][] sets = {
			{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
			{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
			{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			float roll = this.random.nextFloat();
			int set = tyrant || roll < CHAMPION_IRON ? 2 : roll < CHAMPION_IRON + CHAMPION_CHAIN ? 1 : 0;
			this.setItemSlot(slots[i], new ItemStack(sets[set][i]));
			this.setDropChance(slots[i], 0.0F);
		}
		Item weapon = tyrant ? Items.IRON_AXE : this.random.nextBoolean() ? Items.IRON_SWORD : Items.STONE_SWORD;
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		boolean tyrant = tyrant();
		if (!tyrant && !ZFighterTournaments.fighting(this.getUUID())) {
			// a champion with no bout to fight (left over from a restart): gone
			this.discard();
			return;
		}
		LivingEntity target = this.getTarget();
		if (tyrant) {
			bar(level);
			if (this.tickCount % 4 == 0) {
				level.sendParticles(new DustParticleOptions(form() >= 2 ? 0xFFF080 : TYRANT_COLOR, 1.5F), this.getX(), this.getY() + 1.0, this.getZ(), 3,
					0.4, 0.8, 0.4, 0.0);
			}
			if (form() < 2 && this.getHealth() < this.getMaxHealth() * 0.5F) {
				// Super 2 at half health: a roar, a flash, faster and harder
				this.entityData.set(DATA_FORM, 2);
				this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42);
				this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(TYRANT_DAMAGE * 1.4);
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
				com.jaymospowers.core.Shakes.around(level, this.position(), 32.0, 1.6F);
			}
			// he flies up to foes above him, and won't fall to his death
			boolean above = target != null && target.getY() > this.getY() + 2.5;
			this.setNoGravity(above);
			if (above) {
				this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(0.0, 0.25, 0.0));
			}
			this.resetFallDistance();
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double distance = this.distanceTo(target);
		if (--this.blastIn <= 0 && distance > 4.0 && distance < BLAST_RANGE && this.hasLineOfSight(target)) {
			this.blastIn = BLAST_EVERY + this.random.nextInt(30);
			blast(level, target, tyrant);
		}
		if (--this.stepIn <= 0 && distance > STEP_FAR && distance < BLAST_RANGE) {
			this.stepIn = STEP_EVERY + this.random.nextInt(40);
			step(level, target);
		}
	}

	/** A ki blast from the palm, straight at his foe. */
	private void blast(ServerLevel level, LivingEntity target, boolean tyrant) {
		Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.6));
		Vec3 to = target.getBoundingBox().getCenter();
		ZFighterKi.showBeam(level, from, to, tyrant ? 0.5F : 0.3F, tyrant ? TYRANT_COLOR : CHAMPION_COLOR);
		target.hurtServer(level, this.damageSources().mobAttack(this), tyrant ? TYRANT_BLAST : CHAMPION_BLAST);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.5F);
	}

	/** Gone in a blink, and behind him. */
	private void step(ServerLevel level, LivingEntity target) {
		Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(2.0));
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
		this.teleportTo(level, behind.x, target.getY(), behind.z, Relative.ROTATION, this.getYRot(), this.getXRot(), false);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 1.8F);
	}

	private void bar(ServerLevel level) {
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		for (ServerPlayer player : level.players()) {
			boolean near = player.distanceToSqr(this) <= BOSS_SEEN * BOSS_SEEN;
			if (near && !this.bar.getPlayers().contains(player)) {
				this.bar.addPlayer(player);
			} else if (!near && this.bar.getPlayers().contains(player)) {
				this.bar.removePlayer(player);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		Entity attacker = source.getEntity();
		if (hurt && attacker instanceof ServerPlayer player) {
			this.hurtBy.merge(player.getUUID(), Math.max(0.0F, before - this.getHealth()), Float::sum);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && tyrant()) {
			ZFighterRaids.defeated(level, this);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
		}
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("rogue_kind", kind());
		output.putInt("rogue_form", form());
		output.putString("rogue_kingdom", this.kingdom);
		output.store("rogue_hurt_by", UUIDUtil.CODEC.listOf(), this.hurtBy.keySet().stream().toList());
		output.store("rogue_hurt", com.mojang.serialization.Codec.FLOAT.listOf(), this.hurtBy.values().stream().toList());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_KIND, input.getIntOr("rogue_kind", CHAMPION));
		this.entityData.set(DATA_FORM, input.getIntOr("rogue_form", 0));
		this.kingdom = input.getStringOr("rogue_kingdom", "");
		var ids = input.read("rogue_hurt_by", UUIDUtil.CODEC.listOf()).orElse(java.util.List.of());
		var amounts = input.read("rogue_hurt", com.mojang.serialization.Codec.FLOAT.listOf()).orElse(java.util.List.of());
		for (int i = 0; i < Math.min(ids.size(), amounts.size()); i++) {
			this.hurtBy.put(ids.get(i), amounts.get(i));
		}
	}
}
