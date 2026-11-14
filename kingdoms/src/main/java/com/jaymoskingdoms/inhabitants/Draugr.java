package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.dungeon.TombHall;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The draugr: a long-dead Warrior-born, a Z Fighter of the old raids gone to sinew, in a horned helm, mail and fur, its
 * tail wrapped round its waist, guarding the Stormborn Tomb's halls (placed by the tomb's spawners). Our own model, texture, animations and code (kingdoms/art/draugr.bbmodel); after the old idea of the
 * barrow-dead only, nothing from any other mod.
 *
 * <p>Each one rises with one of two arms (a real item in its hands, so vanilla does the rest): a sword and shield, and
 * then it takes blows and arrows from in front on its shield now and then (an axe knocks its guard down for a while); or
 * a two-handed bearded axe, slow and heavy, which knocks a player's raised shield aside as any axe does. Its blows carry
 * the grave's cold (a short slowness).
 *
 * <p>Every draugr is a Warrior-born ancestor (it wears a Warrior-born's tail, mangled its own way: the client's
 * {@code InhabitantsClient.DraugrRenderer}), bound where it stands: it keeps within {@link #HOME_RADIUS} of the spot it
 * first stood on, gives up a foe who leaves that ground and walks back to it, so the tomb's dead never leave the tomb.
 *
 * <p>Not all of them are the shield-wall's warriors ({@link Rank}). An <b>archer</b> stands off and shoots, and is weak
 * with its bare hands when reached. A <b>wight</b> in a rimed cloak throws a bolt of the grave's cold from its free hand at
 * a foe far off and draws its blade on one that closes. A <b>scourge</b>, hooded, with a staff, throws ki the way it did
 * alive, slower and bursting where it lands; it has no blade, backs away from a foe, and a blow breaks its casting. A
 * <b>hall-guard</b> with two swords and a crest steps aside from blows and arrows. Every bolt is thrown where its mark
 * stood a moment before, so a player who keeps moving is missed.
 *
 * <p>And they read the fight: a warrior raises its shield (and takes everything from in front on it) while its foe draws
 * a bow or readies a throw; a warrior or wight hurt badly backs off behind its guard for a breath; all of them press
 * faster on a foe who is nearly down.
 */
public class Draugr extends Monster implements GeoEntity {
	/** Base damage: a sword (+5) makes it 7, an axe (+8) 10. */
	static final double HEALTH = 40.0, SPEED = 0.22, DAMAGE = 2.0, ARMOR = 8.0, KNOCKBACK_RESISTANCE = 0.3, FOLLOW = 24.0;
	/** Share that rise with the axe; the chance its sword or axe drops. */
	static final float AXE_SHARE = 0.4F, WEAPON_DROP = 0.05F;
	/** With its shield: how often it takes a blow, and an arrow, from in front (degrees either side of its facing). */
	static final float BLOCK_CHANCE = 0.4F, ARROW_BLOCK_CHANCE = 0.7F, BLOCK_ARC = 70.0F;
	/** How long an axe blow on the shield leaves it unable to block, and how long the shield shows raised after a block (ticks). */
	static final int GUARD_BROKEN_TICKS = 100, BLOCK_SHOWN_TICKS = 12;
	/** The cold of its blows: slowness, for this long (ticks), this strong (0 = I). */
	static final int CHILL_TICKS = 50, CHILL_AMPLIFIER = 0;
	/** How far from where it first stood a draugr will go, chasing or wandering (blocks). */
	static final int HOME_RADIUS = 24;
	public static final byte BLOCKED = 94;

	/** What a draugr was in the old raids, and so how it fights now. */
	public enum Rank {
		WARRIOR, ARCHER, WIGHT, SCOURGE, HALL_GUARD,
		/** Fights with its bare hands, stripped to the waist. */
		BRAWLER,
		/** One sword and no shield, in leather. */
		SKIRMISHER;

		/** Out of the mail and plate (the archer, the brawler, the skirmisher): quicker on its feet and easier to cut. */
		public boolean light() {
			return this == ARCHER || this == BRAWLER || this == SKIRMISHER;
		}
	}

	/** Of those that rise: archers, wights, scourges and hall-guards; the rest are the warriors. */
	static final float ARCHER_SHARE = 0.2F, WIGHT_SHARE = 0.12F, SCOURGE_SHARE = 0.1F, HALL_GUARD_SHARE = 0.08F, BRAWLER_SHARE = 0.1F,
		SKIRMISHER_SHARE = 0.12F;
	/** The light ranks, without the weight of mail: their speed and armour (the others' are {@link #SPEED} and {@link #ARMOR}). */
	static final double LIGHT_SPEED = 0.28, LIGHT_ARMOR = 2.0;
	/** The brawler's and the skirmisher's health, and the brawler's base damage (it has no blade to add to it). */
	static final double BRAWLER_HEALTH = 36.0, SKIRMISHER_HEALTH = 32.0, BRAWLER_DAMAGE = 6.0;
	/** Health of the ranks that aren't a warrior's {@link #HEALTH}; the hall-guard's base damage (its sword adds 5) and speed. */
	static final double ARCHER_HEALTH = 30.0, WIGHT_HEALTH = 50.0, SCOURGE_HEALTH = 28.0, HALL_GUARD_HEALTH = 60.0, HALL_GUARD_DAMAGE = 4.0,
		HALL_GUARD_SPEED = 0.27;
	/** How far a shot or bolt reaches; nearer than this an archer fights by hand, a wight draws its blade, a scourge backs away (blocks). */
	static final double SHOT_RANGE = 18.0, ARCHER_CLOSE = 3.0, WIGHT_CLOSE = 6.0, SCOURGE_CLOSE = 7.0;
	/** Ticks from raising the bow or hand to loosing, per rank (archer, wight, scourge), and between shots. */
	static final int ARCHER_WIND_UP = 16, WIGHT_WIND_UP = 14, SCOURGE_WIND_UP = 26, ARCHER_GAP = 40, WIGHT_GAP = 70, SCOURGE_GAP = 60;
	/** A bolt flies at where its mark stood this many ticks before it is loosed; a blow on a scourge stops its casting this long. */
	static final int AIM_LOCK = 6, CAST_BROKEN_TICKS = 30;
	/** The wight's frost bolt and the scourge's ki: damage, how near the line (or the burst) hurts (blocks), the bolt's cold. */
	static final float FROST_DAMAGE = 6.0F, FROST_WIDTH = 0.6F, KI_DAMAGE = 9.0F, KI_BURST = 2.5F, ARROW_POWER = 1.6F, ARROW_SPREAD = 6.0F;
	static final int FROST_SLOW_TICKS = 80, FROST_SLOW_AMPLIFIER = 1, FROST_FREEZE_TICKS = 100;
	static final int FROST_COLOR = 0xDDF6FF, KI_COLOR = 0x7FD4FF;
	/** The hall-guard steps aside from this share of blows, and of arrows, then not again for this long (ticks); how hard it steps. */
	static final float DODGE_CHANCE = 0.45F, ARROW_DODGE_CHANCE = 0.8F;
	static final int DODGE_GAP = 40;
	static final double DODGE_PUSH = 0.9;
	/** A shield stays up this long after its foe was last seen drawing or readying (ticks), within this far (blocks). */
	static final int GUARD_HOLD_TICKS = 15;
	static final double GUARD_WATCH = 16.0;
	/** Below this share of its health a warrior or wight backs off, this long, this often (ticks); guarded, it walks this much slower. */
	static final float BACK_OFF_AT = 0.3F;
	static final int BACK_OFF_TICKS = 50, BACK_OFF_GAP = 300;
	static final double GUARD_SLOW = -0.3;
	/** A foe at or under this share of its health is pressed: this much faster. */
	static final float PRESS_AT = 0.35F;
	static final double PRESS_HASTE = 0.25;
	static final Identifier GUARD_MODIFIER = JaymosKingdoms.id("draugr_guard"), PRESS_MODIFIER = JaymosKingdoms.id("draugr_press");

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation BLOCK = RawAnimation.begin().thenPlayAndHold("block");
	static final RawAnimation ATTACK_SWORD = RawAnimation.begin().thenPlay("attack_sword");
	static final RawAnimation ATTACK_AXE = RawAnimation.begin().thenPlay("attack_axe");
	static final RawAnimation GUARD_BROKEN = RawAnimation.begin().thenPlay("guard_broken");
	static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("shoot");
	static final RawAnimation SIDESTEP = RawAnimation.begin().thenPlay("sidestep");
	static final RawAnimation ATTACK_TWIN = RawAnimation.begin().thenPlay("attack_twin");

	private static final EntityDataAccessor<Boolean> AXE = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.INT);
	/** Its shield is held up (it takes everything from in front on it). */
	private static final EntityDataAccessor<Boolean> GUARD = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int guardBroken;
	/** Ticks its shield stays up yet; ticks until a hall-guard can step aside again; ticks a struck scourge can't cast. */
	private int guardHeld, dodgeWait;
	int castBroken;
	/** Ticks left of backing off, and until it may back off again. */
	int backingOff, backOffWait;
	/** It has been given its arms (a structure may place it without finalizeSpawn, so it arms itself on its first tick). */
	private boolean armed;
	/** (Client, a lore film's actor only) drawn as the living Warrior-born it was, in the same gear in better condition. */
	private boolean living;
	/** (Client) the tick it last took a blow on its shield. */
	private int blockedAt = -1000;

	public Draugr(EntityType<? extends Draugr> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(AXE, false);
		entityData.define(RANK, 0);
		entityData.define(GUARD, false);
	}

	/**
	 * Makes this (a client-only lore film actor, never a real draugr) the living Warrior-born it was: drawn alive, its
	 * tail whole, armed with the axe or the sword and shield.
	 */
	public void stageLiving(boolean axe) {
		this.living = true;
		this.entityData.set(AXE, axe);
	}

	/** (A lore film's actor) what a finisher took off it: {@link #MAIM_HEAD}, {@link #MAIM_ARM} (its weapon arm). */
	public static final int MAIM_HEAD = 1, MAIM_ARM = 2;
	private int maimed;

	/** (A lore film's actor) loses its head or its weapon arm to a finisher: no longer drawn. */
	public void stageMaim(int bits) {
		this.maimed |= bits;
	}

	public int maimed() {
		return this.maimed;
	}

	/** (A lore film's actor) the loops it stands and moves in ({@link com.jaymoskingdoms.films.Moves}); null for a real draugr. */
	private @Nullable String stageStill, stageMoving;
	private net.minecraft.world.phys.Vec3 stageLast = net.minecraft.world.phys.Vec3.ZERO;
	private double stageSpeed;

	/** (A lore film's actor) stands and moves in these loops of the humanoid move library from now on. */
	public void stageGait(String still, String moving) {
		this.stageStill = still;
		this.stageMoving = moving;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.stageStill != null) {
			net.minecraft.world.phys.Vec3 now = this.position();
			this.stageSpeed = Math.sqrt((now.x - this.stageLast.x) * (now.x - this.stageLast.x) + (now.z - this.stageLast.z) * (now.z - this.stageLast.z));
			this.stageLast = now;
		}
	}

	/** Whether this is a lore film's living Warrior-born ({@link #stageLiving}). */
	public boolean living() {
		return this.living;
	}

	/** It fights with the two-handed axe (else sword and shield). */
	public boolean axe() {
		return this.entityData.get(AXE);
	}

	public Rank rank() {
		return Rank.values()[Mth.clamp(this.entityData.get(RANK), 0, Rank.values().length - 1)];
	}

	/** Its shield is up. */
	public boolean guarding() {
		return this.entityData.get(GUARD);
	}

	/** Rises as one of the ranks, by their shares. */
	private void arm() {
		float roll = this.random.nextFloat();
		Rank rank = roll < ARCHER_SHARE ? Rank.ARCHER
			: roll < ARCHER_SHARE + WIGHT_SHARE ? Rank.WIGHT
			: roll < ARCHER_SHARE + WIGHT_SHARE + SCOURGE_SHARE ? Rank.SCOURGE
			: roll < ARCHER_SHARE + WIGHT_SHARE + SCOURGE_SHARE + HALL_GUARD_SHARE ? Rank.HALL_GUARD
			: roll < ARCHER_SHARE + WIGHT_SHARE + SCOURGE_SHARE + HALL_GUARD_SHARE + BRAWLER_SHARE ? Rank.BRAWLER
			: roll < ARCHER_SHARE + WIGHT_SHARE + SCOURGE_SHARE + HALL_GUARD_SHARE + BRAWLER_SHARE + SKIRMISHER_SHARE ? Rank.SKIRMISHER : Rank.WARRIOR;
		this.arm(rank, rank == Rank.WARRIOR && this.random.nextFloat() < AXE_SHARE);
	}

	/** Takes up {@code rank}'s arms (a warrior's the axe, or the sword and shield) and its health. */
	public void arm(Rank rank, boolean axe) {
		this.armed = true;
		this.entityData.set(RANK, rank.ordinal());
		this.entityData.set(AXE, axe);
		// (the archer's bow and the scourge's staff are the model's own: their hands are empty, so they are weak up close)
		boolean blade = rank == Rank.WARRIOR || rank == Rank.WIGHT || rank == Rank.HALL_GUARD || rank == Rank.SKIRMISHER;
		this.setItemSlot(EquipmentSlot.MAINHAND, !blade ? ItemStack.EMPTY : new ItemStack(axe ? Items.IRON_AXE : Items.IRON_SWORD));
		this.setItemSlot(EquipmentSlot.OFFHAND, rank == Rank.WARRIOR && !axe ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
		this.setDropChance(EquipmentSlot.MAINHAND, WEAPON_DROP);
		this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		double health = switch (rank) {
			case ARCHER -> ARCHER_HEALTH;
			case WIGHT -> WIGHT_HEALTH;
			case SCOURGE -> SCOURGE_HEALTH;
			case HALL_GUARD -> HALL_GUARD_HEALTH;
			case BRAWLER -> BRAWLER_HEALTH;
			case SKIRMISHER -> SKIRMISHER_HEALTH;
			default -> HEALTH;
		};
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		this.setHealth((float) health);
		if (rank == Rank.HALL_GUARD) {
			this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(HALL_GUARD_DAMAGE);
			this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HALL_GUARD_SPEED);
		}
		if (rank.light()) {
			this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(LIGHT_SPEED);
			this.getAttribute(Attributes.ARMOR).setBaseValue(LIGHT_ARMOR);
		}
		if (rank == Rank.BRAWLER) {
			this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BRAWLER_DAMAGE);
		}
	}

	/** It would rather shoot or cast at {@code target} than close with it: an archer or wight at a distance, a scourge always. */
	boolean shoots(LivingEntity target) {
		double close = switch (this.rank()) {
			case ARCHER -> ARCHER_CLOSE;
			case WIGHT -> WIGHT_CLOSE;
			case SCOURGE -> 0.0;
			default -> Double.MAX_VALUE;
		};
		return this.distanceToSqr(target) >= close * close;
	}

	int windUp() {
		return switch (this.rank()) {
			case ARCHER -> ARCHER_WIND_UP;
			case WIGHT -> WIGHT_WIND_UP;
			default -> SCOURGE_WIND_UP;
		};
	}

	int shotGap() {
		return switch (this.rank()) {
			case ARCHER -> ARCHER_GAP;
			case WIGHT -> WIGHT_GAP;
			default -> SCOURGE_GAP;
		};
	}

	/** Raises its bow, its hand or its staff: the tell before {@link #loose}. */
	void windUpStart(ServerLevel level) {
		switch (this.rank()) {
			case ARCHER -> {
				this.triggerAnim("action", "shoot");
				this.playSound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.8F, 0.7F);
			}
			case WIGHT -> {
				this.triggerAnim("action", "cast_left");
				this.playSound(SoundEvents.POWDER_SNOW_STEP, 1.2F, 0.6F);
				Fx.orb(level, this.position().add(0.0, 1.5, 0.0), 0.35F, FROST_COLOR, WIGHT_WIND_UP);
			}
			default -> {
				this.triggerAnim("action", "cast_both");
				this.playSound(SoundEvents.BEACON_POWER_SELECT, 0.9F, 1.6F);
				Fx.orb(level, this.position().add(0.0, 2.0, 0.0), 0.6F, KI_COLOR, SCOURGE_WIND_UP);
			}
		}
	}

	/** Looses its arrow at {@code target}, or its bolt at {@code aim} (where the target stood a moment ago). */
	void loose(ServerLevel level, LivingEntity target, Vec3 aim) {
		if (this.rank() == Rank.ARCHER) {
			ItemStack bow = new ItemStack(Items.BOW), ammo = new ItemStack(Items.ARROW);
			AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, 1.0F, bow);
			double xd = target.getX() - this.getX(), zd = target.getZ() - this.getZ();
			double yd = target.getY(0.3333333333333333) - arrow.getY();
			Projectile.spawnProjectileUsingShoot(arrow, level, ammo, xd, yd + Math.sqrt(xd * xd + zd * zd) * 0.2, zd, ARROW_POWER, ARROW_SPREAD);
			this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.7F + this.random.nextFloat() * 0.2F);
			return;
		}
		boolean ki = this.rank() == Rank.SCOURGE;
		int color = ki ? KI_COLOR : FROST_COLOR;
		Vec3 from = this.position().add(0.0, ki ? 2.0 : 1.5, 0.0);
		Vec3 way = aim.subtract(from).normalize();
		Vec3 end = level.clip(new ClipContext(from, from.add(way.scale(SHOT_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
		// the first living thing on the line (never another of the tomb's dead) stops it
		LivingEntity struck = null;
		double reach = end.distanceTo(from);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5), e -> e != this && e.isAlive()
			&& !(e instanceof Draugr) && !e.isSpectator())) {
			Vec3 to = near.getBoundingBox().getCenter().subtract(from);
			double along = to.dot(way);
			if (along > 0.0 && along < reach && to.subtract(way.scale(along)).length() <= FROST_WIDTH + near.getBbWidth() * 0.5) {
				struck = near;
				reach = along;
			}
		}
		end = from.add(way.scale(reach));
		Fx.beam(level, from, end, ki ? 0.3F : 0.15F, color, 8);
		DamageSource source = this.damageSources().mobProjectile(this, this);
		if (ki) {
			Fx.blast(level, end, KI_BURST, color);
			level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7F, 1.5F);
			Vec3 burst = end;
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(KI_BURST), e -> e != this && e.isAlive()
				&& !(e instanceof Draugr) && !e.isSpectator() && e.getBoundingBox().getCenter().distanceTo(burst) <= KI_BURST + 0.5)) {
				near.hurtServer(level, source, KI_DAMAGE);
			}
		} else {
			level.playSound(null, end.x, end.y, end.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 1.4F);
			if (struck != null && struck.hurtServer(level, source, FROST_DAMAGE)) {
				struck.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_SLOW_TICKS, FROST_SLOW_AMPLIFIER), this);
				struck.setTicksFrozen(Math.max(struck.getTicksFrozen(), FROST_FREEZE_TICKS));
			}
		}
	}

	/** A hall-guard's step aside from what came from {@code from}. */
	private void sidestep(Vec3 from) {
		Vec3 to = from.subtract(this.position());
		Vec3 side = new Vec3(-to.z, 0.0, to.x);
		if (side.lengthSqr() < 1.0E-4) {
			side = new Vec3(1.0, 0.0, 0.0);
		}
		side = side.normalize().scale(this.random.nextBoolean() ? DODGE_PUSH : -DODGE_PUSH);
		this.setDeltaMovement(side.x, 0.15, side.z);
		this.needsSync = true;
		this.dodgeWait = DODGE_GAP;
		this.triggerAnim("action", "sidestep");
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.4F);
	}

	/** Adds or takes off a speed change that lasts only while {@code on}. */
	private void haste(Identifier id, double amount, boolean on) {
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (on && !speed.hasModifier(id)) {
			speed.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!on && speed.hasModifier(id)) {
			speed.removeModifier(id);
		}
	}

	/** Reads the fight: shield up against a drawn bow or a readied throw, and while backing off; faster on a foe nearly down. */
	private void read() {
		LivingEntity target = this.getTarget();
		boolean shield = this.rank() == Rank.WARRIOR && !this.axe() && this.guardBroken == 0;
		if (shield && target != null && target.isUsingItem() && this.distanceToSqr(target) <= GUARD_WATCH * GUARD_WATCH) {
			ItemUseAnimation use = target.getUseItem().getUseAnimation();
			if (use == ItemUseAnimation.BOW || use == ItemUseAnimation.CROSSBOW || use == ItemUseAnimation.TRIDENT || use == ItemUseAnimation.SPEAR) {
				this.guardHeld = GUARD_HOLD_TICKS;
			}
		}
		if (this.guardHeld > 0) {
			this.guardHeld--;
		}
		boolean guard = shield && (this.guardHeld > 0 || this.backingOff > 0);
		if (guard != this.guarding()) {
			this.entityData.set(GUARD, guard);
		}
		this.haste(GUARD_MODIFIER, GUARD_SLOW, guard && this.backingOff == 0);
		this.haste(PRESS_MODIFIER, PRESS_HASTE, target != null && target.getHealth() <= target.getMaxHealth() * PRESS_AT);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.arm();
		return data;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DraugrFight.BackOff(this));
		this.goalSelector.addGoal(2, new DraugrFight.Shots(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false) {
			@Override
			public boolean canUse() {
				return Draugr.this.rank() != Rank.SCOURGE && super.canUse();
			}
		});
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.armed) {
			this.arm();
		}
		if (!this.hasHome()) {
			// bound where it first stands (draugr from before this have none yet: theirs is where they are now)
			this.setHomeTo(this.blockPosition(), HOME_RADIUS);
		}
		if (this.guardBroken > 0) {
			this.guardBroken--;
		}
		if (this.dodgeWait > 0) {
			this.dodgeWait--;
		}
		if (this.castBroken > 0) {
			this.castBroken--;
		}
		if (this.backOffWait > 0) {
			this.backOffWait--;
		}
		this.read();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Vec3 came = source.getSourcePosition();
		if (this.rank() == Rank.HALL_GUARD && this.dodgeWait == 0 && came != null && source.getEntity() != null
			&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			&& this.random.nextFloat() < (source.is(DamageTypeTags.IS_PROJECTILE) ? ARROW_DODGE_CHANCE : DODGE_CHANCE)) {
			this.sidestep(came);
			return false;
		}
		if (this.rank() == Rank.SCOURGE) {
			this.castBroken = CAST_BROKEN_TICKS;
		}
		if (this.blocks(source)) {
			Entity attacker = source.getEntity();
			boolean axe = !source.is(DamageTypeTags.IS_PROJECTILE) && attacker instanceof LivingEntity living
				&& living.getMainHandItem().is(ItemTags.AXES);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), axe ? SoundEvents.SHIELD_BREAK.value() : SoundEvents.SHIELD_BLOCK.value(),
				SoundSource.HOSTILE, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
			if (axe) {
				this.guardBroken = GUARD_BROKEN_TICKS;
				this.triggerAnim("action", "guard_broken");
			} else {
				level.broadcastEntityEvent(this, BLOCKED);
			}
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	/** Whether it takes {@code source} on its shield: sword and shield only, from in front, now and then, its guard up. */
	private boolean blocks(DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (this.axe() || from == null || this.guardBroken > 0 || source.getEntity() == null || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		float chance = source.is(DamageTypeTags.IS_PROJECTILE) ? ARROW_BLOCK_CHANCE : BLOCK_CHANCE;
		if (this.rank() != Rank.WARRIOR || !this.guarding() && this.random.nextFloat() >= chance) {
			return false;
		}
		Vec3 to = from.subtract(this.position());
		float angle = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		return Math.abs(Mth.wrapDegrees(angle - this.yBodyRot)) <= BLOCK_ARC;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.triggerAnim("action", switch (this.rank()) {
				case HALL_GUARD -> "attack_twin";
				case ARCHER, BRAWLER -> "swing_across";
				default -> this.axe() ? "attack_axe" : "attack_sword";
			});
			if (target instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, CHILL_AMPLIFIER), this);
			}
		}
		return hit;
	}

	/** A wight, a scourge or a hall-guard carries a shard of the jailer's seal (never one the hall itself raised). */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		Rank rank = this.rank();
		if ((rank == Rank.WIGHT || rank == Rank.SCOURGE || rank == Rank.HALL_GUARD) && !this.entityTags().contains(TombHall.WAVE_TAG)) {
			this.spawnAtLocation(level, new ItemStack(TombHall.SEAL_SHARD));
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BLOCKED) {
			this.blockedAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Draugr>("move", 4, test -> {
			Draugr draugr = test.animatable();
			if (draugr.stageStill != null && draugr.stageMoving != null) {
				// a lore film's Warrior-born: the move library's loops, in time with how fast the film moves it
				boolean going = draugr.stageSpeed > 0.02;
				test.setControllerSpeed(going ? com.jaymoskingdoms.films.Moves.rate(draugr.stageMoving, draugr.stageSpeed, 1.2) : 1.0F);
				return test.setAndContinue(com.jaymoskingdoms.films.Moves.loop(going ? draugr.stageMoving : draugr.stageStill));
			}
			if (draugr.guarding() || draugr.tickCount - draugr.blockedAt < BLOCK_SHOWN_TICKS) {
				return test.setAndContinue(BLOCK);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		// its own blows, and every move of the humanoid library (the lore films' Warrior-born use them)
		controllers.add(com.jaymoskingdoms.films.Moves.actions(new AnimationController<Draugr>("action", 2, test -> PlayState.STOP))
			.triggerableAnim("attack_sword", ATTACK_SWORD).triggerableAnim("attack_axe", ATTACK_AXE).triggerableAnim("guard_broken", GUARD_BROKEN)
			.triggerableAnim("shoot", SHOOT).triggerableAnim("sidestep", SIDESTEP).triggerableAnim("attack_twin", ATTACK_TWIN));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.ZOMBIE_STEP, 0.4F, 0.7F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("axe", this.axe());
		output.putBoolean("armed", this.armed);
		output.putInt("rank", this.rank().ordinal());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(AXE, input.getBooleanOr("axe", false));
		this.armed = input.getBooleanOr("armed", false);
		this.entityData.set(RANK, input.getIntOr("rank", 0));
	}
}
