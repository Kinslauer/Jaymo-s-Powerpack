package com.jaymoskingdoms.remnants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.jaymoskingdoms.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.phys.shapes.VoxelShape;
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
 * (0.7.261) Every step is a stomp: the ground cracks under the foot and a shockwave runs out along it, hurting everyone it
 * passes who isn't off the ground ({@link #footfall}); it walks with a slow, swaying stride, as fast as ever.
 * Badly hurt it rages: faster, and its power comes twice as often. Its boss bar is its element's colour. The first Giant
 * to fall in a world raises the world tier ({@link Monoliths#slain}).
 */
public class AetherGiant extends Monster {
	/**
	 * (0.7.152) Andy: "make all the giants the same size as warrior born great apes". As tall as a Warrior-born's Great
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
	/** Too big to path through woods: with no path to its target it strides straight at it, and every this many ticks whatever it walks into is knocked down. */
	static final int TRAMPLE_EVERY = 5;
	/** How high up its legs it fells trees whole (blocks); above that, and for anything else, it smashes straight through. */
	static final int TRAMPLE_HEIGHT = 6;
	/** (0.7.470) How far past its body each step knocks trees down (blocks). */
	static final double STEP_TREE_REACH = 2.5;
	/** (0.7.184) Its death: the tick it hits the ground and breaks up, and the bursts of stone along where it lies (to this share of its height ahead). */
	static final int SHATTER_TICK = 10, SHATTER_BURSTS = 6;
	static final double SHATTER_REACH = 0.6;
	/** The share of what it smashes that drops (its own monolith it leaves standing, {@link Monoliths#part}). */
	static final float TRAMPLE_DROPS = 0.25F;
	/** How long a swing shows (ticks). */
	static final int SWING_TICKS = 12;
	public static final int LEFT = 1, RIGHT = 2, BOTH = 3;
	/**
	 * (0.7.261) Andy: "Giant enemies should emit a stomp with every step ... a slow sway, keeping their move speed". Its
	 * stride: how far it walks in one step (blocks; at its walking pace a step every second or so, the model's legs keep to
	 * it), and where each foot comes down (blocks out to the side and ahead of its middle).
	 */
	public static final float STEP_LENGTH = 6.0F;
	static final double FOOT_SIDE = 3.6, FOOT_AHEAD = 3.0;
	/**
	 * Each footfall's shockwave: it runs out along the ground this fast (blocks a tick) to this far, and hurts everyone it
	 * passes under who is standing on the ground (jump it and it misses you), most near the foot, this share of that at the
	 * edge, with a small jolt up and away.
	 */
	static final double WAVE_SPEED = 1.25, WAVE_RANGE = 10.0, WAVE_JOLT_UP = 0.35, WAVE_JOLT_AWAY = 0.4;
	static final float WAVE_DAMAGE = 5.0F, WAVE_EDGE = 0.5F;
	/** How tall a step up or down the wave follows (blocks), and how far apart its ring's dust is (blocks). */
	static final int WAVE_CLIMB = 3;
	static final double RING_GAP = 1.0;
	/**
	 * The ground cracks under each foot ({@code destroyBlockProgress}): the blocks under the foot, and this many cracks
	 * running out from it, up to this long, fainter towards their ends; they fade over this long (ticks). Nothing breaks.
	 */
	static final int CRACK_LINES = 5, CRACK_LENGTH_MIN = 2, CRACK_LENGTH_MAX = 5, CRACK_STAGE = 9, CRACK_END_STAGE = 4, CRACK_TICKS = 100;
	/** Crack ids: one block each, these many per Giant in turn, from this base (clear of any real entity id). */
	static final int CRACK_SLOTS = 160, CRACK_ID_BASE = 0x4A000000;
	/** Entity events: its right or left foot came down. */
	private static final byte STEP_RIGHT = 90, STEP_LEFT = 91;
	/**
	 * (0.7.268) Andy: "a grab attack that is telegraphed and fairly slow, but deals massive damage to a single target, with a
	 * letterboxed animation". Its grab, in ticks: it rears back with a roar, its hand drawn up behind it ({@link #GRAB_WINDUP};
	 * run clear of its reach and the hand closes on nothing), then the hand comes down on its target ({@link #GRAB_SNATCH}),
	 * lifts it to its chest and squeezes ({@link #GRAB_HOLD}, {@link #SQUEEZE_DAMAGE} every {@link #SQUEEZE_EVERY}), and hurls it
	 * into the ground ({@link #GRAB_SLAM}, {@link #SLAM_DAMAGE}, thrown {@link #SLAM_THROW}); then it straightens up
	 * ({@link #GRAB_RECOVER}). How often it may (ticks, plus up to half again), how near its target must be to start, how far
	 * its hand reaches when it closes, and how far round the film's bars are seen.
	 */
	public static final int GRAB_WINDUP = 30, GRAB_SNATCH = 8, GRAB_HOLD = 40, GRAB_SLAM = 8, GRAB_RECOVER = 20;
	public static final int GRAB_TOTAL = GRAB_WINDUP + GRAB_SNATCH + GRAB_HOLD + GRAB_SLAM + GRAB_RECOVER;
	static final int GRAB_TICKS = 400, SQUEEZE_EVERY = 10;
	static final double GRAB_TRIGGER = 9.0, GRAB_REACH = 9.0, GRAB_CLIMB = 6.0, SLAM_THROW = 1.2, FILM_RANGE = 48.0;
	static final float SQUEEZE_DAMAGE = 4.0F, SLAM_DAMAGE = 40.0F;
	/** Where its hand holds its catch: ahead and to its right of its middle (shares of its width), this high (share of its height). */
	static final double HAND_AHEAD = 0.55, HAND_SIDE = 0.45, HAND_UP = 0.55;
	/** The hand's lift into the hold and its drop into the slam run over this many ticks of each. */
	public static final int LIFT_TICKS = 10;
	/** The ground round where its catch is slammed is thrown up this far (blocks). */
	static final double SLAM_DUST = 3.0;

	private static final EntityDataAccessor<Byte> SWING = SynchedEntityData.defineId(AetherGiant.class, EntityDataSerializers.BYTE);
	/** (0.7.268) The game time its grab began (Long.MIN_VALUE: it isn't grabbing), and whom it holds (-1: no one). */
	private static final EntityDataAccessor<Long> GRAB_START = SynchedEntityData.defineId(AetherGiant.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> GRAB_HELD = SynchedEntityData.defineId(AetherGiant.class, EntityDataSerializers.INT);
	private static final Identifier RAGE_ID = Identifier.fromNamespaceAndPath("jaymoskingdoms", "giant_rage");

	private final Element element;
	/** The monolith it rose beside (null if it was summoned). */
	private @Nullable BlockPos monolith;
	private int stampCooldown = STAMP_TICKS, windup, powerCooldown = POWER_TICKS, swingTicks;
	/** (0.7.268) Ticks before it may grab again, where its hand closed, and where its catch is slammed down. */
	private int grabCooldown = GRAB_TICKS;
	private @Nullable Vec3 snatchAt, slamAt;
	private boolean raging;
	/** (Client) how far each arm's swing has run. */
	private int leftCounter, rightCounter;
	/** (Server) its boss bar, made when first wanted. */
	private @Nullable ServerBossEvent bar;
	/** (0.7.261) How far through its stride it is, in steps (right foot down on the even ones), this tick and last. */
	private float stride, strideO;
	/** (Server) the last step it came down on. */
	private int lastStep;
	/** Its footfalls' shockwaves still running out (both sides: the server hurts, the client draws the ring). */
	private final List<Wave> waves = new ArrayList<>();
	/** (Server) its cracks still showing, and the next crack id slot. */
	private final List<Crack> cracks = new ArrayList<>();
	private int crackSlot;

	/** A shockwave from a footfall at {@code (x, y, z)} (y the ground's top), {@code age} ticks out; who it has hit. */
	private static final class Wave {
		final double x, y, z;
		int age;
		final Set<Integer> hit = new HashSet<>();

		Wave(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private record Crack(BlockPos pos, int id, int stage, int born) {
	}

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
		entityData.define(GRAB_START, Long.MIN_VALUE);
		entityData.define(GRAB_HELD, -1);
	}

	/** (0.7.268) How many ticks into its grab it is ({@link #GRAB_TOTAL} long), or -1 when it isn't grabbing (both sides). */
	public int grabTick() {
		long start = this.entityData.get(GRAB_START);
		return start == Long.MIN_VALUE ? -1 : (int)(this.level().getGameTime() - start);
	}

	/** Whether it's in its grab (winding up, holding, slamming or straightening). */
	public boolean grabbing() {
		int t = this.grabTick();
		return t >= 0 && t < GRAB_TOTAL;
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

	/** (0.7.261) How far through its stride it is this frame, in steps: its legs come down on the whole numbers. */
	public float stride(float partialTicks) {
		return Mth.lerp(partialTicks, this.strideO, this.stride);
	}

	@Override
	public void tick() {
		super.tick();
		this.strideO = this.stride;
		if (this.isAlive() && this.onGround()) {
			// its stride runs on with the ground it covers (a teleport doesn't count as many steps)
			double moved = Math.sqrt(Mth.square(this.getX() - this.xo) + Mth.square(this.getZ() - this.zo));
			this.stride += (float)Math.min(moved / STEP_LENGTH, 0.5);
		}
		if (this.level() instanceof ServerLevel level) {
			if (this.swingTicks > 0 && --this.swingTicks == 0) {
				this.entityData.set(SWING, (byte)0);
			}
			int step = Mth.floor(this.stride);
			if (step != this.lastStep) {
				this.lastStep = step;
				this.footfall(level, (step & 1) == 0);
			}
			this.runWaves(level);
			this.fadeCracks(level);
			return;
		}
		this.drawWaves();
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
		if (this.grabbing()) {
			this.grab(level);
			return;
		}
		LivingEntity target = this.getTarget();
		boolean fighting = target != null && target.isAlive();
		if (this.grabCooldown > 0) {
			this.grabCooldown--;
		} else if (fighting && this.getTarget() instanceof LivingEntity prey && this.canGrab(prey)
			&& this.distanceToSqr(prey) < Mth.square(this.getBbWidth() / 2.0 + GRAB_TRIGGER)) {
			this.beginGrab(level, prey);
			return;
		}
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

	/**
	 * Whatever it walks into, it walks through (Andy: "it should knock down any tree, or building. dont get in the way"):
	 * natural trees topple away from it, everything else breakable in its way is smashed (a share of it dropped). Its own
	 * monolith is spared.
	 */
	private void trample(ServerLevel level) {
		AABB box = this.getBoundingBox().inflate(1.0, 0.0, 1.0);
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), y1 = Mth.floor(box.maxY), z1 = Mth.floor(box.maxZ);
		// the trees first (they need their leaves to count as trees and fall whole), then the rest
		this.trampleTrees(level, 1.0);
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()
				|| state.getDestroySpeed(level, pos) < 0.0F || this.monolith != null && Monoliths.part(this.monolith, pos)) {
				continue;
			}
			level.destroyBlock(pos, this.random.nextFloat() < TRAMPLE_DROPS, this);
		}
	}

	/** The natural trees within {@code reach} of its body topple away from it (and crush what they land on). */
	private void trampleTrees(ServerLevel level, double reach) {
		AABB box = this.getBoundingBox().inflate(reach, 0.0, reach);
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), y1 = Mth.floor(box.maxY), z1 = Mth.floor(box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, Math.min(y1, y0 + TRAMPLE_HEIGHT), z1)) {
			if (level.getBlockState(pos).is(BlockTags.LOGS)) {
				Trees.trample(level, pos.immutable(), this);
			}
		}
	}

	// ---- its grab (0.7.268) ----------------------------------------------------------------------------------------

	/** What it may grab: anything living it may hurt that isn't riding, another Giant, or too big to lift. */
	private boolean canGrab(LivingEntity prey) {
		return prey.isAlive() && !(prey instanceof AetherGiant) && !prey.isPassenger() && prey.getBbHeight() < HEIGHT * 0.5
			&& !(prey instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** It rears back for the grab: a roar, its hand drawn up behind it; everyone near gets the warning. */
	private void beginGrab(ServerLevel level, LivingEntity prey) {
		this.getNavigation().stop();
		this.entityData.set(GRAB_START, level.getGameTime());
		this.entityData.set(GRAB_HELD, -1);
		this.snatchAt = null;
		this.slamAt = null;
		this.grabCooldown = GRAB_TICKS + this.random.nextInt(GRAB_TICKS / 2);
		this.playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.45F);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.4F);
	}

	/** Its grab, tick by tick: the wind-up, the snatch, the hold and squeeze, the slam, and straightening up. */
	private void grab(ServerLevel level) {
		int t = this.grabTick();
		this.getNavigation().stop();
		LivingEntity prey = this.held(level);
		if (t < GRAB_WINDUP) {
			// drawn back, watching its target: the warning
			LivingEntity target = this.getTarget();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
				this.yBodyRot = this.yHeadRot;
			}
			if (t % 3 == 0) {
				Vec3 hand = this.hand(0.0);
				level.sendParticles(this.element.mote, hand.x, hand.y, hand.z, 6, 1.5, 1.5, 1.5, 0.05);
			}
			if (t == GRAB_WINDUP - 1) {
				this.snatch(level, target);
			}
			return;
		}
		if (prey == null) {
			// its hand closed on nothing (or its catch is gone): it straightens up
			if (t < GRAB_WINDUP + GRAB_SNATCH + GRAB_HOLD + GRAB_SLAM) {
				this.entityData.set(GRAB_START, level.getGameTime() - (GRAB_WINDUP + GRAB_SNATCH + GRAB_HOLD + GRAB_SLAM));
			}
			return;
		}
		int held = t - GRAB_WINDUP - GRAB_SNATCH;
		if (held < 0) {
			// the hand coming down: its catch is pinned where it stood
			this.carry(prey, this.snatchAt != null ? this.snatchAt : prey.position());
			return;
		}
		if (held < GRAB_HOLD) {
			// lifted to its chest and squeezed
			Vec3 from = this.snatchAt != null ? this.snatchAt : prey.position();
			double lift = Math.min(1.0, held / (double)LIFT_TICKS);
			lift = lift * lift * (3.0 - 2.0 * lift);
			this.carry(prey, from.lerp(this.hand(0.0), lift));
			if (held > 0 && held % SQUEEZE_EVERY == 0) {
				prey.hurtServer(level, this.damageSources().mobAttack(this), SQUEEZE_DAMAGE);
				this.touch(prey, 1);
				this.playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.3F);
				level.sendParticles(ParticleTypes.CRIT, prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ(), 12, 0.6, 0.6, 0.6, 0.1);
			}
			if (held == GRAB_HOLD - 1) {
				this.slamAt = this.groundAhead(level);
			}
			return;
		}
		int slam = held - GRAB_HOLD;
		if (slam < GRAB_SLAM) {
			// hurled down into the ground
			Vec3 to = this.slamAt != null ? this.slamAt : this.groundAhead(level);
			double drop = Math.min(1.0, (slam + 1) / (double)GRAB_SLAM);
			this.carry(prey, this.hand(0.0).lerp(to, drop * drop));
			if (slam == GRAB_SLAM - 1) {
				this.slam(level, prey, to);
			}
			return;
		}
		// straightening up: its catch is let go
		if (this.entityData.get(GRAB_HELD) >= 0) {
			this.entityData.set(GRAB_HELD, -1);
		}
	}

	/** Its hand closes: on its target if it's still within reach of the hand, else on nothing. */
	private void snatch(ServerLevel level, @Nullable LivingEntity target) {
		if (target == null || !this.canGrab(target) || !this.inHandsReach(target)) {
			this.playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.5F);
			Vec3 miss = this.position().add(Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(this.getBbWidth() * 0.5 + GRAB_REACH * 0.5));
			level.sendParticles(ParticleTypes.POOF, miss.x, miss.y + 0.5, miss.z, 20, 2.0, 0.3, 2.0, 0.05);
			return;
		}
		this.entityData.set(GRAB_HELD, target.getId());
		this.snatchAt = target.position();
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.6F);
		if (target instanceof Player p) {
			p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.giant.grabbed", this.getDisplayName())
				.withStyle(net.minecraft.ChatFormatting.RED));
		}
		// the film: its catch (a player) sees the Giant's face come down on them; everyone near gets the bars
		com.jaymoskingdoms.PowersCompat.film(this, target instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null, com.jaymoskingdoms.PowersCompat.FILM_PUSH_IN,
			GRAB_SNATCH + GRAB_HOLD + GRAB_SLAM, this.position(), this, FILM_RANGE);
	}

	/** Whether {@code prey} stands within its hand's reach: round its body, near its own level. */
	private boolean inHandsReach(LivingEntity prey) {
		double dx = prey.getX() - this.getX(), dz = prey.getZ() - this.getZ();
		return dx * dx + dz * dz <= Mth.square(this.getBbWidth() / 2.0 + GRAB_REACH) && Math.abs(prey.getY() - this.getY()) <= GRAB_CLIMB;
	}

	/** Its catch slammed into the ground at {@code at}: the blow, the ground thrown up, and its catch flung clear. */
	private void slam(ServerLevel level, LivingEntity prey, Vec3 at) {
		prey.resetFallDistance();
		prey.hurtServer(level, this.damageSources().mobAttack(this), SLAM_DAMAGE);
		this.touch(prey, 2);
		Vec3 away = Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(SLAM_THROW);
		prey.setDeltaMovement(away.x, 0.5, away.z);
		prey.needsSync = true;
		prey.syncVelocity = true;
		this.entityData.set(GRAB_HELD, -1);
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, 80, SLAM_DUST, 0.3, SLAM_DUST, 0.3);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0.0);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.5F);
		this.crack(level, BlockPos.containing(at.x, at.y - 0.5, at.z));
	}

	/** Whom it holds, if it's still there to hold. */
	private @Nullable LivingEntity held(ServerLevel level) {
		int id = this.entityData.get(GRAB_HELD);
		if (id < 0) {
			return null;
		}
		Entity e = level.getEntity(id);
		if (e instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
			return living;
		}
		this.entityData.set(GRAB_HELD, -1);
		return null;
	}

	/** Its catch carried to {@code to} (its feet), held still and facing it. */
	private void carry(LivingEntity prey, Vec3 to) {
		if (prey instanceof net.minecraft.server.level.ServerPlayer player) {
			player.teleportTo((ServerLevel)this.level(), to.x, to.y, to.z, net.minecraft.world.entity.Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			prey.setPos(to.x, to.y, to.z);
			Vec3 face = this.getEyePosition().subtract(prey.getBoundingBox().getCenter());
			float yaw = (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
			prey.setYRot(yaw);
			prey.setYBodyRot(yaw);
			prey.setYHeadRot(yaw);
		}
		prey.setDeltaMovement(Vec3.ZERO);
		prey.needsSync = true;
		prey.syncVelocity = true;
		prey.resetFallDistance();
	}

	/** Where its right hand holds its catch (the catch's feet), {@code ahead} blocks further forward. */
	private Vec3 hand(double ahead) {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double w = this.getBbWidth(), forward = w * HAND_AHEAD + ahead, side = w * HAND_SIDE;
		return new Vec3(this.getX() + fx * forward - fz * side, this.getY() + this.getBbHeight() * HAND_UP, this.getZ() + fz * forward + fx * side);
	}

	/** The ground ahead of it, under its right hand's side, where its catch is slammed. */
	private Vec3 groundAhead(ServerLevel level) {
		Vec3 over = this.hand(this.getBbWidth() * 0.2);
		double y = groundTop(level, over.x, this.getY(), over.z);
		return new Vec3(over.x, Double.isNaN(y) ? this.getY() : y, over.z);
	}

	// ---- its steps (0.7.261) ---------------------------------------------------------------------------------------

	/** Where its {@code right} (or left) foot comes down, on the ground's top (null if there's no ground under it). */
	private @Nullable Vec3 foot(Level level, boolean right) {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		// its right is the way it faces turned a quarter clockwise
		double side = right ? FOOT_SIDE : -FOOT_SIDE;
		double x = this.getX() + fx * FOOT_AHEAD - fz * side, z = this.getZ() + fz * FOOT_AHEAD + fx * side;
		double y = groundTop(level, x, this.getY(), z);
		return Double.isNaN(y) ? null : new Vec3(x, y, z);
	}

	/** The top of the ground at {@code (x, z)} within {@link #WAVE_CLIMB} of {@code y}, or NaN if there is none. */
	private static double groundTop(Level level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, y + WAVE_CLIMB, z).mutable();
		for (int i = 0; i <= WAVE_CLIMB * 2; i++, pos.move(0, -1, 0)) {
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.max(Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}

	/**
	 * Its foot comes down: a thud and a burst of the ground's dust, the ground cracked round the foot, and a shockwave
	 * runs out from it ({@link #runWaves}). The client's legs are kept in step with it ({@link #handleEntityEvent}).
	 */
	private void footfall(ServerLevel level, boolean right) {
		level.broadcastEntityEvent(this, right ? STEP_RIGHT : STEP_LEFT);
		Vec3 at = this.foot(level, right);
		if (at == null) {
			return;
		}
		BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
		BlockState ground = level.getBlockState(under);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 40, 1.4, 0.1, 1.4, 0.25);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 8, 1.2, 0.1, 1.2, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_STEP, this.getSoundSource(), 3.0F, 0.35F + this.random.nextFloat() * 0.1F);
		level.playSound(null, at.x, at.y, at.z, ground.getSoundType().getBreakSound(), this.getSoundSource(), 2.0F, 0.5F);
		this.crack(level, under);
		if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			// (0.7.470) Andy: "all giants should get this damaging effect": the trees round its feet go down with every step
			this.trampleTrees(level, STEP_TREE_REACH);
		}
		this.waves.add(new Wave(at.x, at.y, at.z));
	}

	/** The ground cracked round {@code under}: the blocks under the foot, and lines of cracks running out from it. */
	private void crack(ServerLevel level, BlockPos under) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				this.crackAt(level, under.offset(dx, 0, dz), CRACK_STAGE);
			}
		}
		for (int line = 0; line < CRACK_LINES; line++) {
			double angle = (line + this.random.nextDouble() * 0.6) * Mth.TWO_PI / CRACK_LINES;
			int length = CRACK_LENGTH_MIN + this.random.nextInt(CRACK_LENGTH_MAX - CRACK_LENGTH_MIN + 1);
			for (int i = 2; i <= length + 1; i++) {
				// each crack wanders a little as it runs out
				angle += (this.random.nextDouble() - 0.5) * 0.5;
				int stage = CRACK_STAGE - (CRACK_STAGE - CRACK_END_STAGE) * (i - 2) / Math.max(length - 1, 1);
				double y = groundTop(level, under.getX() + 0.5 + Math.cos(angle) * i, under.getY() + 1.0, under.getZ() + 0.5 + Math.sin(angle) * i);
				if (!Double.isNaN(y)) {
					this.crackAt(level, BlockPos.containing(under.getX() + 0.5 + Math.cos(angle) * i, y - 0.5,
						under.getZ() + 0.5 + Math.sin(angle) * i), stage);
				}
			}
		}
	}

	private void crackAt(ServerLevel level, BlockPos pos, int stage) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
			return;
		}
		int id = CRACK_ID_BASE + this.getId() * CRACK_SLOTS + this.crackSlot;
		this.crackSlot = (this.crackSlot + 1) % CRACK_SLOTS;
		this.cracks.removeIf(c -> c.id() == id);
		level.destroyBlockProgress(id, pos, stage);
		this.cracks.add(new Crack(pos.immutable(), id, stage, this.tickCount));
	}

	/** Its cracks fade, a stage at a time, and are gone after {@link #CRACK_TICKS}. */
	private void fadeCracks(ServerLevel level) {
		if (this.cracks.isEmpty() || this.tickCount % 10 != 0) {
			return;
		}
		for (Iterator<Crack> it = this.cracks.iterator(); it.hasNext();) {
			Crack crack = it.next();
			int age = this.tickCount - crack.born();
			if (age >= CRACK_TICKS) {
				level.destroyBlockProgress(crack.id(), crack.pos(), -1);
				it.remove();
			} else if (age > CRACK_TICKS / 2) {
				// the second half of its time it fades out
				level.destroyBlockProgress(crack.id(), crack.pos(), Math.max(0, crack.stage() * 2 * (CRACK_TICKS - age) / CRACK_TICKS));
			}
		}
	}

	/** All its cracks gone at once (it was unloaded or killed). */
	private void clearCracks(ServerLevel level) {
		for (Crack crack : this.cracks) {
			level.destroyBlockProgress(crack.id(), crack.pos(), -1);
		}
		this.cracks.clear();
	}

	/**
	 * Its shockwaves run out: everyone the wave front passes this tick who stands on the ground near its level (not
	 * jumping, flying or riding) is hurt, less towards its edge, and jolted up and away.
	 */
	private void runWaves(ServerLevel level) {
		for (Iterator<Wave> it = this.waves.iterator(); it.hasNext();) {
			Wave wave = it.next();
			double from = WAVE_SPEED * wave.age, to = Math.min(WAVE_SPEED * ++wave.age, WAVE_RANGE);
			AABB box = new AABB(wave.x - to, wave.y - WAVE_CLIMB, wave.z - to, wave.x + to, wave.y + WAVE_CLIMB, wave.z + to);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && !(e instanceof AetherGiant) && e.isAlive()
				&& e.onGround() && !e.isPassenger() && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
				double d = Math.sqrt(Mth.square(hit.getX() - wave.x) + Mth.square(hit.getZ() - wave.z));
				if (d > to || d < from - 1.0 || !wave.hit.add(hit.getId())) {
					continue;
				}
				float share = 1.0F - (1.0F - WAVE_EDGE) * (float)(d / WAVE_RANGE);
				hit.hurtServer(level, this.damageSources().mobAttack(this), WAVE_DAMAGE * share);
				Vec3 away = d < 0.01 ? Vec3.ZERO : new Vec3(hit.getX() - wave.x, 0.0, hit.getZ() - wave.z).scale(WAVE_JOLT_AWAY / d);
				hit.push(away.x, WAVE_JOLT_UP, away.z);
				hit.needsSync = true;
			}
			if (to >= WAVE_RANGE) {
				it.remove();
			}
		}
	}

	/** (Client) its shockwaves' rings of dust running out along the ground. */
	private void drawWaves() {
		Level level = this.level();
		for (Iterator<Wave> it = this.waves.iterator(); it.hasNext();) {
			Wave wave = it.next();
			double r = Math.min(WAVE_SPEED * ++wave.age, WAVE_RANGE);
			int points = Math.max(12, (int)(r * Mth.TWO_PI / RING_GAP));
			double turn = this.random.nextDouble();
			for (int i = 0; i < points; i++) {
				double angle = (i + turn) * Mth.TWO_PI / points, cx = Math.cos(angle), cz = Math.sin(angle);
				double x = wave.x + cx * r, z = wave.z + cz * r, y = groundTop(level, x, wave.y, z);
				if (Double.isNaN(y)) {
					continue;
				}
				BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
				if (!ground.isAir()) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y + 0.1, z, cx * 0.3, 0.25, cz * 0.3);
				}
				if ((i & 1) == 0) {
					level.addParticle(ParticleTypes.POOF, x, y + 0.15, z, cx * 0.12, 0.03, cz * 0.12);
				}
			}
			if (r >= WAVE_RANGE) {
				it.remove();
			}
		}
	}

	/** (Client) a foot came down: its legs are put in step with the server's, and the wave's ring starts. */
	@Override
	public void handleEntityEvent(byte id) {
		if (id == STEP_RIGHT || id == STEP_LEFT) {
			// the nearest step of that foot (right on the even ones)
			int step = Math.round(this.stride);
			if (((step & 1) == 0) != (id == STEP_RIGHT)) {
				step += this.stride > step ? 1 : -1;
			}
			float shift = step - this.stride;
			this.stride += shift;
			this.strideO += shift;
			Vec3 at = this.foot(this.level(), id == STEP_RIGHT);
			if (at != null) {
				this.waves.add(new Wave(at.x, at.y, at.z));
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (this.level() instanceof ServerLevel level) {
			this.clearCracks(level);
		}
		super.remove(reason);
	}

	/** Its grab is dropped when it falls: whatever it held is let go. */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		this.entityData.set(GRAB_START, Long.MIN_VALUE);
		this.entityData.set(GRAB_HELD, -1);
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
	private List<LivingEntity> near(ServerLevel level, Vec3 at, double range) {
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

	/**
	 * (0.7.184) Its death: it topples onto its face and, as it hits the ground ({@link #SHATTER_TICK}), breaks apart
	 * (the client's model throws its pieces): its stone bursts up along where it lies, with a crash of breaking rock.
	 */
	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (this.deathTime == SHATTER_TICK && this.level() instanceof ServerLevel level) {
			BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, this.element.stone().defaultBlockState());
			BlockParticleOption band = new BlockParticleOption(ParticleTypes.BLOCK, this.element.band().defaultBlockState());
			Vec3 ahead = Vec3.directionFromRotation(0.0F, this.yBodyRot);
			for (int i = 0; i <= SHATTER_BURSTS; i++) {
				Vec3 at = this.position().add(ahead.scale(HEIGHT * SHATTER_REACH * i / SHATTER_BURSTS));
				level.sendParticles(stone, at.x, at.y + 1.0, at.z, 60, WIDTH * 0.3, 1.5, WIDTH * 0.3, 0.3);
				level.sendParticles(band, at.x, at.y + 1.0, at.z, 20, WIDTH * 0.3, 1.5, WIDTH * 0.3, 0.3);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 6, WIDTH * 0.3, 0.5, WIDTH * 0.3, 0.02);
			}
			this.playSound(this.element.stone().defaultBlockState().getSoundType().getBreakSound(), 4.0F, 0.5F);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.4F);
		}
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

	/** (0.7.261) Its steps sound with each footfall instead ({@link #footfall}). */
	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
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
