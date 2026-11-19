package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.brawl.Brawl;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
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
 * ({@link ZFighterRaids}). Both fight bare-handed in strings of quick, light blows (the players' hold-combo moves,
 * {@link ZFighterCombo}) ending in a finisher that sends the foe flying into whatever is behind it, then rush after it
 * ({@link ZFighterImpact}); they fire volleys of small ki blasts at range and flash-step in behind a foe who runs. The
 * tyrant hovers up to foes above him, glows gold (Super), goes Super 2 at half health and carries a boss bar. Whoever
 * hurt the tyrant shares his prize.
 * <p>
 * (0.16.10, Andy: tyrants "one shot everything, they should use far more weaker punches") No more iron axe: a tyrant's
 * blow is about a heart, a dozen of them to a string, so a villager gets a few seconds and a fighter can guard or break
 * away.
 */
public class ZFighterRogue extends Monster {
	public static final int CHAMPION = 0;
	public static final int TYRANT = 1;

	// ---- tuning ----
	static final double CHAMPION_HEALTH = 80.0;
	static final double TYRANT_HEALTH = 600.0;
	/** Bare-hand damage (his attack attribute): each blow of a string is a share of it ({@link #BLOW_SHARE}). */
	static final double CHAMPION_DAMAGE = 3.0;
	static final double TYRANT_DAMAGE = 7.0;
	/**
	 * A string's blows: this share of his damage each (a tyrant about 2.5, 3.4 in Super 2; a champion 1.5), the finisher
	 * this share (6 / 8.5 / 3.9), and the slam into the wall or ground after it this share more. By kind.
	 */
	static final float[] BLOW_SHARE = {0.5F, 0.35F};
	static final float[] FINISHER_SHARE = {1.3F, 0.9F};
	static final float[] SLAM_SHARE = {0.5F, 0.4F};
	/** Ticks a blow takes (a champion, a tyrant, a tyrant in Super 2); the finisher takes {@link ZFighterCombo#FINISHER_LENGTH} times as long. */
	static final int CHAMPION_BLOW_TICKS = 8, TYRANT_BLOW_TICKS = 6, SUPER_2_BLOW_TICKS = 5;
	/** Ticks between strings (a breath), by kind. */
	static final int[] REST_TICKS = {20, 14};
	/** The strings each kind throws (one picked at random each time), from {@link ZFighterCombo#STRINGS}. */
	static final int[][] KIND_STRINGS = {{1, 2}, {3, 4}};
	/** (NPC fights 2) The reach, lunge, throw and rush are the shared runner's ({@link ZFighterStrings}); his rush speed is his own (blocks a tick, by kind; Super 2 faster). */
	static final double[] RUSH_SPEED = {0.9, 1.3};
	static final double SUPER_2_RUSH = 1.6;
	/** Base armour; the armour he wears adds the rest. */
	static final double CHAMPION_ARMOR = 2.0, TYRANT_ARMOR = 6.0;
	/** A champion's armour pieces: each is iron this often, else chainmail, else leather. */
	static final float CHAMPION_IRON = 0.5F, CHAMPION_CHAIN = 0.35F;
	/** (0.16.10) A volley of small blasts, {@code BLAST_GAP} ticks apart (was one 14-damage blast from a tyrant). */
	static final float CHAMPION_BLAST = 2.0F;
	static final float TYRANT_BLAST = 4.0F;
	static final int BLAST_VOLLEY = 3;
	static final int BLAST_GAP = 5;
	static final int BLAST_EVERY = 70;
	static final int STEP_EVERY = 100;
	static final double BLAST_RANGE = 32.0;
	static final double STEP_FAR = 9.0;
	static final double BOSS_SEEN = 96.0;
	static final int TYRANT_COLOR = 0xFFC830;
	static final int CHAMPION_COLOR = 0x8FD3FF;
	/** His rank for the fighter brain ({@link Brawl}), before the world tier: a tyrant, one of a raider band, a champion. */
	static final Brawl.Profile TYRANT_BRAWL = Brawl.Profile.ki(0.85F, TYRANT_COLOR);
	static final Brawl.Profile RAIDER_BRAWL = Brawl.Profile.ki(0.6F, TYRANT_COLOR);
	static final Brawl.Profile CHAMPION_BRAWL = Brawl.Profile.ki(0.15F, CHAMPION_COLOR);

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(ZFighterRogue.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(ZFighterRogue.class, EntityDataSerializers.INT);

	/** Who hurt the tyrant, and how much. */
	final Map<UUID, Float> hurtBy = new HashMap<>();
	/** The kingdom a tyrant fell on (its name, for the news). */
	String kingdom = "";
	private ServerBossEvent bar;
	private int blastIn = BLAST_EVERY;
	private int volleyLeft;
	private int stepIn = STEP_EVERY;
	// the string he's throwing (not saved: a reload starts him fresh)

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
		// the goal only closes in: the blows are his own strings (combat)
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true) {
			@Override
			protected void checkAndPerformAttack(LivingEntity target) {
			}
		});
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// only a tyrant picks his own foes: the people of the kingdom, its golems, everyone
		// sworn foes: a tyrant, or one of a raider band's men, goes for a Galactic Patrolman before anyone else
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
			(target, level) -> (tyrant() || raider()) && ZFighterPatrol.patrolman(target)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> tyrant()));
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true, (target, level) -> tyrant()));
		this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true, (target, level) -> tyrant()));
	}

	/** (0.20.36) One of a raider band's men (on the ground by a town, or up in the high sky), not a tournament champion. */
	boolean raider() {
		return this.entityTags().contains(ZFighterRaiders.TAG) || this.entityTags().contains(ZFighterHighSky.TAG);
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
	 * Armour, never dropped: a tyrant in full iron, a champion in a mix of leather, chainmail and iron. (0.16.10) Bare
	 * hands: no more weapon.
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
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		boolean tyrant = tyrant();
		if (!tyrant && !ZFighterTournaments.fighting(this.getUUID()) && !raider()) {
			// a champion with no bout to fight (left over from a restart): gone; (0.20.36) a raider band's men are not champions
			// of a bout, so they stay (their bands clear away their own leftovers)
			this.discard();
			return;
		}
		if (!this.getMainHandItem().isEmpty()) {
			// one from before 0.16.10 still holding his axe or sword: put away
			this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		LivingEntity target = this.getTarget();
		if (tyrant) {
			bar(level);
			if (this.tickCount % 4 == 0) {
				level.sendParticles(MagicFx.wisp(form() >= 2 ? 0xFFF080 : TYRANT_COLOR, 1.5F), this.getX(), this.getY() + 1.0, this.getZ(), 3,
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
			boolean brawling = Brawl.flying(this);
			this.setNoGravity(above || brawling);
			if (above && !brawling) {
				this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(0.0, 0.25, 0.0));
			}
			this.resetFallDistance();
		}
		// the fighter brain: flight round a foe in the air, dashes and sidesteps, the vanish dodge, guard and parry
		Brawl.tick(level, this, target, tyrant ? TYRANT_BRAWL : raider() ? RAIDER_BRAWL : CHAMPION_BRAWL, ZFighterStrings.engaged(this),
			ZFighterStrings.rushing(this) || ZFighterHighSky.airborne(this));
		if (Brawl.takeVanish(this)) {
			// he blinked behind whoever struck: the next string starts at once
			ZFighterStrings.rested(this);
		}
		if (ZFighterStrings.rushing(this)) {
			return;
		}
		if (ZFighterHighSky.airborne(this)) {
			// (0.20.36) up in the high sky there is nothing to stand on: they fly at their foe, and hang in the air without one
			ZFighterHighSky.steer(this, target);
		}
		if (target == null || !target.isAlive()) {
			ZFighterStrings.release(this);
			return;
		}
		ZFighterStrings.engage(level, this, target, strings(tyrant));
		if (tyrant) {
			// (NPC fights 2) a tyrant's beam, seeking volley and charged blast
			ZFighterNpcKi.decide(level, this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE), TYRANT_COLOR);
		}
		double distance = this.distanceTo(target);
		if (this.volleyLeft > 0 && this.tickCount % BLAST_GAP == 0 && this.hasLineOfSight(target)) {
			this.volleyLeft--;
			blast(level, target, tyrant);
		}
		if (--this.blastIn <= 0 && !ZFighterStrings.engaged(this) && distance > 4.0 && distance < BLAST_RANGE && this.hasLineOfSight(target)) {
			this.blastIn = Brawl.tempo(this, BLAST_EVERY + this.random.nextInt(30));
			this.volleyLeft = BLAST_VOLLEY;
		}
		if (--this.stepIn <= 0 && distance > STEP_FAR && distance < BLAST_RANGE) {
			this.stepIn = STEP_EVERY + this.random.nextInt(40);
			step(level, target);
		}
	}

	/** What his strings are made of ({@link ZFighterStrings}, the runner a Patrolman and a gifted fighter share): by kind, his blow now. */
	private ZFighterStrings.Cfg strings(boolean tyrant) {
		int k = tyrant ? TYRANT : CHAMPION;
		int ticks = tyrant ? form() >= 2 ? SUPER_2_BLOW_TICKS : TYRANT_BLOW_TICKS : CHAMPION_BLOW_TICKS;
		double rush = tyrant && form() >= 2 ? SUPER_2_RUSH : RUSH_SPEED[k];
		return new ZFighterStrings.Cfg(this.getAttributeValue(Attributes.ATTACK_DAMAGE), BLOW_SHARE[k], FINISHER_SHARE[k], SLAM_SHARE[k], ticks,
			REST_TICKS[k], KIND_STRINGS[k], tyrant ? form() >= 2 ? 0xFFF080 : TYRANT_COLOR : CHAMPION_COLOR, rush, tyrant);
	}

	/** A ki blast from the palm, straight at his foe. */
	private void blast(ServerLevel level, LivingEntity target, boolean tyrant) {
		// (0.19.60) out of his palm, thrown with a thrust of that arm
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 aim = to.subtract(this.getEyePosition());
		Vec3 from = ZFighterHands.mainPalm(this, aim.lengthSqr() < 1.0E-6 ? this.getLookAngle() : aim.normalize());
		ZFighterHands.thrust(this, ZFighterHands.hand(this, true), ZFighterHands.JAB);
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
