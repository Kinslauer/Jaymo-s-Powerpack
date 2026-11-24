package com.jaymoskingdoms.monsters;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The big golem (after Mo' Creatures' golem, GPL-3, see {@code licenses/mocreatures}): the guardian of the old age's ruins
 * (see {@link MocBeasts} for where it stands and why). It starts as a head, a chest and one cube of earth, and as someone
 * comes near it pulls the ground's loose blocks (dirt, stone, sand, gravel, ice, clay) up out of the earth round it
 * ({@link GolemRock}) and builds itself a body of them, chest first, twenty-three cubes in all. It walks on its legs, takes
 * blows on its cubes (each knocks one off, now and then) and hurls its arms' cubes at whoever stays out of its reach.
 * <p>
 * Its core is the weak place: a hit only hurts it (never more than {@link #HIT_CAP}) when its chest is bare, or open while
 * it is taking in more earth; a hit on a covered chest only shakes cubes off. Worn down it grows fiercer, and at the end it
 * stops, spins up its cubes and falls apart ({@link #dropBody}): every block it took comes back as an item. When it is
 * hurt and has no foe it goes back to gathering, so a golem left alone rebuilds, and the ground it took from stays
 * taken.
 */
public class BigGolem extends Monster {
	public static final int SLOTS = 23, EMPTY = 30;
	/** Its states, as the original golem's: asleep until someone comes near, gathering a body, whole, worn down, falling apart. */
	public static final int ASLEEP = 0, GATHERING = 1, WHOLE = 2, WORN = 3, DYING = 4;
	static final double HEALTH = 60.0, SPEED = 0.25, DAMAGE = 3.0, FOLLOW = 24.0;
	/** Someone this near (blocks) wakes it; it looks this far (blocks) for a block to pull. */
	static final double WAKE_RANGE = 8.0, PULL_RANGE = 12.0;
	/** A hit on its core never hurts for more than this. */
	static final float HIT_CAP = 5.0F;
	/** The share of health at which it is worn and at which it is dying. */
	static final float WORN_AT = 0.6F, DYING_AT = 0.2F;
	/** Ticks between pulls: while gathering, and (less often) while whole, worn (a shorter time on harder levels) and dying. */
	static final int GATHER_EVERY = 10, REGATHER_BASE = 21;
	/** Throwing: it throws from further than this, winds up for this long (ticks) and is done after this. */
	static final double THROW_FROM = 6.0;
	static final int THROW_WIND = 70, THROW_END = 90, THROW_RAISED = 25;
	/** Falling apart: how long it takes (ticks). */
	static final int DYING_TICKS = 140;
	/** The glowing dust its core leaves. */
	static final int CORE_MIN = 2, CORE_MORE = 4;

	/** The cube textures, by number, and the blocks they stand for. */
	static final Block[] BLOCKS = {Blocks.STONE, Blocks.DIRT, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.SAND, Blocks.GRAVEL, Blocks.OAK_LOG,
		Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.LAPIS_BLOCK, Blocks.OAK_LEAVES, Blocks.IRON_BLOCK, Blocks.BRICKS, Blocks.GRASS_BLOCK, Blocks.OBSIDIAN,
		Blocks.DIAMOND_BLOCK, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.ICE, Blocks.CACTUS, Blocks.CLAY, Blocks.EMERALD_BLOCK, Blocks.PUMPKIN,
		Blocks.NETHERRACK, Blocks.DIAMOND_ORE, Blocks.GLOWSTONE, Blocks.STONE_BRICKS, Blocks.NETHER_BRICKS};
	/** What it conjures out of nothing when the ground it stands on can't be taken from (mobGriefing off): dirt, cobblestone, planks, ice. */
	static final int[] CHEAP = {1, 2, 3, 18};
	/** The cubes the starting body has: one of stone, clay, ice-and-the-like for its back. */
	static final int[] BACKS = {0, 20, 2, 4};

	private static final EntityDataAccessor<String> CUBES = SynchedEntityData.defineId(BigGolem.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BigGolem.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(BigGolem.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> RAISED = SynchedEntityData.defineId(BigGolem.class, EntityDataSerializers.BOOLEAN);

	private final byte[] cubes = new byte[SLOTS];
	private String seen = "";
	private int pullIn, throwTicks, dyingTicks;

	public BigGolem(EntityType<? extends BigGolem> type, Level level) {
		super(type, level);
		this.xpReward = 40;
		java.util.Arrays.fill(this.cubes, (byte)EMPTY);
		this.cubes[4] = (byte)BACKS[this.random.nextInt(BACKS.length)];
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.ATTACK_KNOCKBACK, 1.2).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && this.state() != ASLEEP));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(CUBES, "");
		entityData.define(STATE, ASLEEP);
		entityData.define(OPEN, false);
		entityData.define(RAISED, false);
	}

	// ---- reading it ----

	public int state() {
		return this.entityData.get(STATE);
	}

	/** Its chest is open: it is taking in earth. */
	public boolean chestOpen() {
		return this.entityData.get(OPEN);
	}

	/** Its arms are raised to throw. */
	public boolean raised() {
		return this.entityData.get(RAISED);
	}

	/** Which cube is in {@code slot} (a texture number), {@link #EMPTY} for none: the client reads what the server sent. */
	public int cube(int slot) {
		String text = this.entityData.get(CUBES);
		if (this.level().isClientSide() && !text.equals(this.seen)) {
			this.seen = text;
			for (int i = 0; i < SLOTS; i++) {
				this.cubes[i] = i < text.length() ? (byte)(text.charAt(i) - '0') : (byte)EMPTY;
			}
		}
		return this.cubes[slot];
	}

	private void store(int slot, int value) {
		this.cubes[slot] = (byte)value;
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < SLOTS; i++) {
			text.append((char)('0' + this.cubes[i]));
		}
		this.entityData.set(CUBES, text.toString());
		this.speedFromLegs();
	}

	private boolean missing() {
		for (int i = 0; i < SLOTS; i++) {
			if (this.cubes[i] == EMPTY) {
				return true;
			}
		}
		return false;
	}

	private boolean chestBare() {
		for (int i = 0; i < 4; i++) {
			if (this.cubes[i] != EMPTY) {
				return false;
			}
		}
		return true;
	}

	private int legBlocks() {
		int n = 0;
		for (int i = 15; i < 21; i++) {
			if (this.cubes[i] != EMPTY) {
				n++;
			}
		}
		return n;
	}

	/** It walks as fast as it has legs for. */
	private void speedFromLegs() {
		double speed = this.state() == ASLEEP || this.state() == DYING ? 0.0 : SPEED * Math.max(0.15, this.legBlocks() / 6.0);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
	}

	/** Where its chest is, for the blocks flying to it. */
	Vec3 chestPosition() {
		return this.position().add(0.0, 2.4, 0.0);
	}

	// ---- the world gives it blocks ----

	/** The cube a block of the world makes, -1 for one it won't take (only loose ground goes into it). */
	static int typeOf(BlockState state) {
		if (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK)) {
			return 1;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return 0;
		}
		if (state.is(BlockTags.SAND)) {
			return 4;
		}
		if (state.is(Blocks.GRAVEL)) {
			return 5;
		}
		if (state.is(Blocks.CLAY) || state.is(BlockTags.TERRACOTTA)) {
			return 20;
		}
		if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK)) {
			return 18;
		}
		if (state.is(Blocks.NETHERRACK)) {
			return 23;
		}
		return -1;
	}

	/** It reaches for a block: one of the ground within reach (taken from the world), or, with no mobGriefing, a cheap one it makes. */
	private void pull(ServerLevel level) {
		boolean take = level.getGameRules().get(GameRules.MOB_GRIEFING);
		for (int tries = 0; tries < 8; tries++) {
			BlockPos at = this.blockPosition().offset(this.random.nextInt(25) - 12, this.random.nextInt(7) - 4, this.random.nextInt(25) - 12);
			BlockState state = level.getBlockState(at);
			int type = typeOf(state);
			if (type < 0 || !level.getBlockState(at.above()).isAir() && !level.getBlockState(at.above()).canBeReplaced()) {
				continue;
			}
			if (take) {
				level.removeBlock(at, false);
				level.addFreshEntity(GolemRock.pulled(level, at, state, this));
			} else {
				int cheap = CHEAP[this.random.nextInt(CHEAP.length)];
				level.addFreshEntity(GolemRock.pulled(level, at, BLOCKS[cheap].defaultBlockState(), this));
			}
			return;
		}
	}

	/** A pulled block reaches its chest: it takes the block into its body if there's room (the golem heals a little). */
	boolean receiveRock(BlockState state) {
		if (this.state() == DYING || !(this.level() instanceof ServerLevel level)) {
			return false;
		}
		int type = typeOf(state);
		if (type < 0) {
			type = CHEAP[this.random.nextInt(CHEAP.length)];
		}
		int slot = this.freeSlot();
		if (slot < 0) {
			return false;
		}
		this.store(slot, type);
		this.heal(Math.max(1, level.getDifficulty().getId()));
		level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.8F, 0.6F);
		return true;
	}

	/** An empty slot to fill: the four of its chest first, then any; an arm or leg's outer cube brings the inner ones with it. */
	private int freeSlot() {
		List<Integer> chest = new ArrayList<>(), rest = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			if (this.cubes[i] == EMPTY) {
				(i < 4 ? chest : rest).add(i);
			}
		}
		List<Integer> pick = chest.isEmpty() ? rest : chest;
		if (pick.isEmpty()) {
			return -1;
		}
		int i = pick.get(this.random.nextInt(pick.size()));
		// a limb grows from its inner end: shoulder (9, 12) then arm, hand; thigh (15, 18) then knee, foot
		if (i == 10 || i == 13 || i == 16 || i == 19) {
			if (this.cubes[i - 1] != EMPTY) {
				return i;
			}
			return i - 1;
		}
		if (i == 11 || i == 14 || i == 17 || i == 20) {
			if (this.cubes[i - 1] != EMPTY) {
				return i;
			}
			return this.cubes[i - 2] != EMPTY ? i - 1 : i - 2;
		}
		return i;
	}

	// ---- its life ----

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		int state = this.state();
		if (state == ASLEEP) {
			if (level.getNearestPlayer(this, WAKE_RANGE) != null && level.getDifficulty() != Difficulty.PEACEFUL) {
				this.setState(GATHERING);
			}
			return;
		}
		if (state == GATHERING && !this.missing()) {
			this.setState(WHOLE);
		}
		LivingEntity target = this.getTarget();
		if (state > WHOLE && state != DYING && target == null) {
			this.setState(GATHERING);
		}
		if (state > GATHERING && target != null && this.random.nextInt(20) == 0) {
			float share = this.getHealth() / this.getMaxHealth();
			this.setState(share >= WORN_AT ? WHOLE : share >= DYING_AT ? WORN : DYING);
		}
		state = this.state();
		if (state == DYING) {
			this.dying(level);
			return;
		}
		if (state != ASLEEP && this.missing()) {
			int every = state == GATHERING ? GATHER_EVERY : Math.max(2, REGATHER_BASE - state * level.getDifficulty().getId());
			if (this.random.nextInt(every) == 0) {
				this.pull(level);
			}
		}
		if (this.tickCount % 2 == 0) {
			this.entityData.set(OPEN, this.missing() && !level.getEntitiesOfClass(GolemRock.class, this.getBoundingBox().inflate(2.0)).isEmpty());
		}
		this.throwing(level, target);
	}

	private void setState(int state) {
		if (state != this.state()) {
			this.entityData.set(STATE, state);
			this.speedFromLegs();
			if (state == GATHERING) {
				this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.5F, 0.5F);
			}
		}
	}

	/** Throws one arm cube at a foe out of reach, after a long, slow windup. */
	private void throwing(ServerLevel level, LivingEntity target) {
		boolean arms = false;
		for (int i = 9; i < 15; i++) {
			arms |= this.cubes[i] != EMPTY;
		}
		if (this.throwTicks == 0 && target != null && arms && this.state() != GATHERING && this.distanceTo(target) > THROW_FROM) {
			this.throwTicks = 1;
		}
		if (this.throwTicks == 0) {
			return;
		}
		this.throwTicks++;
		this.entityData.set(RAISED, this.throwTicks > THROW_RAISED);
		if (this.throwTicks == THROW_WIND && target != null && target.isAlive() && this.hasLineOfSight(target)) {
			this.hurl(level, target);
		} else if (this.throwTicks > THROW_END) {
			this.throwTicks = 0;
			this.entityData.set(RAISED, false);
		}
	}

	private void hurl(ServerLevel level, LivingEntity target) {
		List<Integer> arm = new ArrayList<>();
		for (int i = 9; i < 15; i++) {
			if (this.cubes[i] != EMPTY) {
				arm.add(i);
			}
		}
		if (arm.isEmpty()) {
			return;
		}
		int slot = arm.get(this.random.nextInt(arm.size()));
		// the outer cube of that arm goes first
		int outer = slot;
		if (slot == 9 || slot == 12) {
			outer = this.cubes[slot + 2] != EMPTY ? slot + 2 : this.cubes[slot + 1] != EMPTY ? slot + 1 : slot;
		} else if (slot == 10 || slot == 13) {
			outer = this.cubes[slot + 1] != EMPTY ? slot + 1 : slot;
		}
		BlockState block = BLOCKS[this.cubes[outer]].defaultBlockState();
		Vec3 from = this.chestPosition().add(0.0, 0.8, 0.0);
		Vec3 aim = target.getEyePosition().subtract(from);
		double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
		// aimed with a lob, flatter the closer it is
		Vec3 velocity = new Vec3(aim.x, aim.y + flat * 0.12, aim.z).normalize().scale(1.1);
		level.addFreshEntity(GolemRock.hurled(level, from, block, this, velocity));
		level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
		this.store(outer, EMPTY);
	}

	/** Falling apart: it stands still while the ground's blocks spin off it, then it comes apart. */
	private void dying(ServerLevel level) {
		this.getNavigation().stop();
		this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
		this.setTarget(null);
		this.dyingTicks++;
		if (this.dyingTicks % 6 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 2.0, this.getZ(), 8, 0.7, 1.2, 0.7, 0.05);
			level.playSound(null, this.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.2F, 0.5F);
		}
		if (this.dyingTicks > DYING_TICKS) {
			level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
			level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.5, this.getZ(), 6, 1.0, 1.0, 1.0, 0.0);
			this.die(level.damageSources().generic());
			this.discard();
		}
	}

	// ---- taking a blow ----

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.state() == DYING) {
			return false;
		}
		boolean exposed = this.chestBare() || this.chestOpen();
		if (this.state() == GATHERING || !exposed) {
			// a covered chest: the blow only shakes a cube loose (less often, the harder the level), and nothing hurts it
			if (this.state() != GATHERING && this.random.nextInt(Math.max(1, level.getDifficulty().getId())) == 0) {
				this.knockOff(level);
			} else {
				level.playSound(null, this.blockPosition(), SoundEvents.TURTLE_HURT, SoundSource.HOSTILE, 1.5F, 0.6F);
			}
			if (source.getEntity() instanceof LivingEntity attacker && attacker != this && level.getDifficulty() != Difficulty.PEACEFUL) {
				this.setTarget(attacker);
			}
			return false;
		}
		boolean hurt = super.hurtServer(level, source, Math.min(damage, HIT_CAP));
		if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this && level.getDifficulty() != Difficulty.PEACEFUL) {
			this.setTarget(attacker);
		}
		return hurt;
	}

	/** A random cube comes off (never the back one), outer cubes first, and drops. */
	private void knockOff(ServerLevel level) {
		List<Integer> used = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			if (this.cubes[i] != EMPTY && i != 4) {
				used.add(i);
			}
		}
		if (used.isEmpty()) {
			return;
		}
		int i = used.get(this.random.nextInt(used.size()));
		int outer = i;
		if (i == 10 || i == 13 || i == 16 || i == 19) {
			outer = this.cubes[i + 1] != EMPTY ? i + 1 : i;
		} else if (i == 9 || i == 12 || i == 15 || i == 18) {
			outer = this.cubes[i + 2] != EMPTY ? i + 2 : this.cubes[i + 1] != EMPTY ? i + 1 : i;
		}
		this.drop(level, this.cubes[outer]);
		this.store(outer, EMPTY);
		level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0F, 0.6F);
	}

	private void drop(ServerLevel level, int type) {
		ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 1.5, this.getZ(), new ItemStack(BLOCKS[type].asItem()));
		item.setDefaultPickUpDelay();
		item.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.3, 0.3, (this.random.nextDouble() - 0.5) * 0.3);
		level.addFreshEntity(item);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.dropBody(level);
	}

	/** Every cube it holds comes back as the block it was, and its core leaves glowing dust. */
	private void dropBody(ServerLevel level) {
		for (int i = 0; i < SLOTS; i++) {
			if (this.cubes[i] != EMPTY) {
				this.drop(level, this.cubes[i]);
				this.cubes[i] = (byte)EMPTY;
			}
		}
		int dust = CORE_MIN + this.random.nextInt(CORE_MORE + 1);
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 2.0, this.getZ(), new ItemStack(Items.GLOWSTONE_DUST, dust)));
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return this.state() == ASLEEP && super.removeWhenFarAway(distance);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("golem_state", this.state());
		output.putIntArray("cubes", java.util.stream.IntStream.range(0, SLOTS).map(i -> this.cubes[i]).toArray());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getIntArray("cubes").ifPresent(array -> {
			for (int i = 0; i < SLOTS && i < array.length; i++) {
				this.cubes[i] = (byte)array[i];
			}
		});
		this.store(4, this.cubes[4]);
		this.setState(input.getIntOr("golem_state", ASLEEP));
		this.speedFromLegs();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.state() == ASLEEP ? null : SoundEvents.IRON_GOLEM_REPAIR;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.7F);
	}
}
