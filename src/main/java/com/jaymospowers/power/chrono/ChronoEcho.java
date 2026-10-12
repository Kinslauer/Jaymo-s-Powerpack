package com.jaymospowers.power.chrono;

import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Chrono's curse: a time remnant of its owner, wearing their face. It walks the routes they walked, holds what they
 * hold, mines what they mine (no drops), places what they place, and attacks every living thing near it, its owner
 * included. Outside the Nether any hurt shatters it, and it comes back later near its owner; only in the Nether can it
 * die, for good, and there it fights with its owner's own abilities ({@link ChronoEchoFight}). See {@link ChronoEchoes} for spawning, returning and following.
 */
public class ChronoEcho extends Monster {
	// ---- tuning ----
	public static final double MAX_HEALTH = 30.0;
	/** Melee damage, fixed (the held item doesn't add to it). */
	public static final float ATTACK_DAMAGE = 5.0F;
	public static final double MOVE_SPEED = 0.26;
	/** How far it notices living things to attack (and how far it keeps chasing). */
	public static final double AGGRO_RANGE = 16.0;
	static final double CHASE_SPEED = 1.2;
	static final double ROUTE_SPEED = 0.9;
	/** One in this many ticks (with nothing to fight) it mines a nearby block of a kind its owner mines. */
	static final int MINE_CHANCE = 300;
	/** One in this many ticks it places its owner's favourite block next to itself. */
	static final int PLACE_CHANCE = 700;
	/** One in this many idle ticks it swings at nothing, the way its owner does. */
	static final int IDLE_SWING_CHANCE = 140;
	/** Mining reach: blocks this far sideways, from its feet up to two above. */
	static final int MINE_RADIUS = 3;
	/** Never mines blocks harder than this (obsidian is 50; bedrock is unbreakable anyway). */
	static final float MAX_MINE_HARDNESS = 50.0F;
	/** How many of the owner's most-mined kinds it remembers. */
	static final int MINED_KINDS = 3;
	/** Owner bookkeeping (bond check, first sighting) runs this often. */
	static final int OWNER_CHECK_INTERVAL = 20;
	/** The owner "sees" their echo for the first time within this range, in line of sight. */
	static final double SIGHT_RANGE = 32.0;
	/** Re-learns its owner's habits this often while they're around. */
	static final int RELEARN_INTERVAL = 2400;
	/** Route points kept on the echo. */
	static final int ROUTE_POINTS = 48;
	/** A recorded route is walked where it was recorded when one of its points is this close; otherwise its shape is walked from here. */
	static final double ROUTE_REACH = 48.0;
	/** Points walked per outing, and the pause between outings. */
	static final int ROUTE_STEPS = 8;
	static final int ROUTE_PAUSE_MIN = 40;
	static final int ROUTE_PAUSE_MAX = 140;
	/** Its look: a pale cyan, half there. ARGB, used by the client renderer. */
	public static final int TINT = 0x9AB8F4FF;
	static final DustParticleOptions SHARD = new DustParticleOptions(0xA8E8FF, 0.7F);
	static final DustParticleOptions SHARD_GREY = new DustParticleOptions(0xC8D0D8, 0.6F);

	private static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(ChronoEcho.class,
		EntityDataSerializers.RESOLVABLE_PROFILE);
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(ChronoEcho.class,
		EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

	// server-side state (saved)
	private @Nullable UUID owner;
	private long bond;
	private List<Identifier> minedKinds = new ArrayList<>();
	private @Nullable Identifier placedKind;
	/** The habits above as blocks, worked out once per change of habit rather than on every try. */
	private List<Block> minedBlocks = List.of();
	private @Nullable List<Identifier> minedBlocksFrom;
	private @Nullable Block placedBlock;
	private @Nullable Identifier placedBlockFrom;
	private List<BlockPos> route = new ArrayList<>();
	/** Shattered, faded or collapsed: it's leaving, ignore anything else that happens to it. */
	private boolean gone;
	/** In the Nether: the real fight, with its owner's abilities (not saved; it picks up again on load). */
	private @Nullable ChronoEchoFight fight;
	/** Back from a Nether blink with this health (0 for none). */
	private float returnHealth;
	// client-side ticking
	private int tickRun;

	public ChronoEcho(EntityType<? extends ChronoEcho> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
			.add(Attributes.FOLLOW_RANGE, AGGRO_RANGE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PROFILE, ResolvableProfile.Static.EMPTY);
		builder.define(DATA_OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, CHASE_SPEED, false));
		this.goalSelector.addGoal(5, new WalkRoutesGoal(this));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, ROUTE_SPEED));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ChronoEcho.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
			(target, level) -> !(target instanceof ChronoEcho) && !(target instanceof ArmorStand)));
	}

	// ---------------------------------------------------------------- owner

	/** Ties a new echo to its owner: their face, their hand, their habits. */
	void bindTo(ServerPlayer player, ChronoMemory memory) {
		this.owner = player.getUUID();
		this.bond = memory.bond;
		this.entityData.set(DATA_PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
		this.entityData.set(DATA_OWNER, Optional.of(EntityReference.of(player.getUUID())));
		this.setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
		this.setPersistenceRequired();
		learn(memory);
	}

	/** Copies the owner's habits as they are now. */
	void learn(ChronoMemory memory) {
		this.minedKinds = ChronoMemory.top(memory.mined, MINED_KINDS);
		List<Identifier> placed = ChronoMemory.top(memory.placed, 1);
		this.placedKind = placed.isEmpty() ? null : placed.get(0);
		List<Identifier> held = ChronoMemory.top(memory.held, 1);
		ItemStack item = held.isEmpty() ? ItemStack.EMPTY
			: BuiltInRegistries.ITEM.getOptional(held.get(0)).map(ItemStack::new).orElse(ItemStack.EMPTY);
		this.setItemSlot(EquipmentSlot.MAINHAND, item);
		List<BlockPos> path = memory.pathIn(this.level().dimension());
		this.route = new ArrayList<>(path.subList(Math.max(0, path.size() - ROUTE_POINTS), path.size()));
	}

	public @Nullable UUID owner() {
		return this.owner;
	}

	long bond() {
		return this.bond;
	}

	boolean isEchoOf(ServerPlayer player) {
		return player.getUUID().equals(this.owner);
	}

	/** Its owner, when they're in the same world (either side). */
	@Nullable Player ownerPlayer() {
		Optional<UUID> id = this.level().isClientSide() ? this.ownerId() : Optional.ofNullable(this.owner);
		return id.map(uuid -> this.level().getPlayerByUUID(uuid)).orElse(null);
	}

	/** The owner's profile (for their skin), synced to clients. */
	public ResolvableProfile getProfile() {
		return this.entityData.get(DATA_PROFILE);
	}

	/** The owner's UUID as the client knows it. */
	public Optional<UUID> ownerId() {
		return this.entityData.get(DATA_OWNER).map(EntityReference::getUUID);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			clientEffects();
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.gone) {
			return;
		}
		if (this.tickCount % OWNER_CHECK_INTERVAL == 0 && !checkOwner(level)) {
			return;
		}
		if (level.dimension() == Level.NETHER) {
			if (this.fight == null) {
				this.fight = new ChronoEchoFight(this);
			}
			this.fight.tick(level);
		} else if (this.fight != null) {
			this.fight.end();
			this.fight = null;
		}
		RandomSource random = this.getRandom();
		boolean idle = this.getTarget() == null;
		if (idle && !this.minedKinds.isEmpty() && random.nextInt(MINE_CHANCE) == 0 && griefing(level)) {
			mineSomething(level);
		}
		if (this.placedKind != null && random.nextInt(PLACE_CHANCE) == 0 && griefing(level)) {
			placeSomething(level);
		}
		if (idle && random.nextInt(IDLE_SWING_CHANCE) == 0) {
			this.swingForAttack(InteractionHand.MAIN_HAND);
		}
	}

	private static boolean griefing(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** Bond check, first sighting, refreshing habits. Returns false if the echo faded. */
	private boolean checkOwner(ServerLevel level) {
		if (this.owner == null) {
			return true;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.owner);
		if (player == null) {
			return true; // offline: nothing to check against, it carries on
		}
		ChronoMemory memory = ChronoMemory.peek(player);
		if (!Powers.has(player, ChronoPower.INSTANCE) || memory == null || memory.bond != this.bond) {
			fade(level);
			return false;
		}
		if (player.level() == level) {
			if (!memory.sighted && player.isAlive() && this.distanceToSqr(player) < SIGHT_RANGE * SIGHT_RANGE && player.hasLineOfSight(this)) {
				memory.sighted = true;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_echo_sighted")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
				ChronoEchoes.soundFor(player, SoundEvents.BELL_RESONATE, 0.6F, 0.6F);
			}
			if (this.tickCount % RELEARN_INTERVAL == 0) {
				learn(memory);
			}
		}
		return true;
	}

	private void mineSomething(ServerLevel level) {
		if (this.minedBlocksFrom != this.minedKinds) {
			List<Block> resolved = new ArrayList<>();
			for (Identifier id : this.minedKinds) {
				BuiltInRegistries.BLOCK.getOptional(id).ifPresent(resolved::add);
			}
			this.minedBlocks = resolved;
			this.minedBlocksFrom = this.minedKinds;
		}
		List<Block> kinds = this.minedBlocks;
		if (kinds.isEmpty()) {
			return;
		}
		BlockPos feet = this.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-MINE_RADIUS, 0, -MINE_RADIUS), feet.offset(MINE_RADIUS, 2, MINE_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (kinds.contains(state.getBlock()) && mineable(level, pos, state)) {
				found.add(pos.immutable());
			}
		}
		if (found.isEmpty()) {
			return;
		}
		BlockPos pos = found.get(this.getRandom().nextInt(found.size()));
		this.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		this.swingForAttack(InteractionHand.MAIN_HAND);
		level.destroyBlock(pos, false, this);
		ChronoFx.particles(level, this.ownerPlayer(), SHARD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
	}

	/** No block entities (chests, furnaces, spawners...), nothing unbreakable or too hard. */
	private static boolean mineable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || state.getBlock() instanceof EntityBlock) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= MAX_MINE_HARDNESS;
	}

	private void placeSomething(ServerLevel level) {
		if (this.placedBlockFrom != this.placedKind) {
			this.placedBlock = this.placedKind == null ? null : BuiltInRegistries.BLOCK.getOptional(this.placedKind).orElse(null);
			this.placedBlockFrom = this.placedKind;
		}
		Block block = this.placedBlock;
		if (block == null || block instanceof EntityBlock) {
			return;
		}
		BlockState state = block.defaultBlockState();
		if (state.isAir()) {
			return;
		}
		BlockPos feet = this.blockPosition();
		List<Direction> sides = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
		java.util.Collections.shuffle(sides, new java.util.Random(this.getRandom().nextLong()));
		for (Direction side : sides) {
			BlockPos pos = feet.relative(side);
			if (!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
				continue;
			}
			if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) || !state.canSurvive(level, pos)) {
				continue;
			}
			if (!level.getEntities((Entity) null, new AABB(pos)).isEmpty()) {
				continue;
			}
			this.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			this.swingForAttack(InteractionHand.MAIN_HAND);
			level.setBlockAndUpdate(pos, state);
			SoundEvent sound = state.getSoundType().getPlaceSound();
			level.playSound(null, pos, sound, SoundSource.HOSTILE, 0.8F, 0.8F);
			ChronoFx.particles(level, this.ownerPlayer(), SHARD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
			return;
		}
	}

	/** Faint shards and, now and then, a few soft ticks. Client side, so it costs the server nothing. */
	private void clientEffects() {
		RandomSource random = this.getRandom();
		Level level = this.level();
		if (this.tickCount % 3 == 0) {
			ChronoFx.particle(level, this.ownerPlayer(), random.nextInt(3) == 0 ? SHARD_GREY : SHARD, this.getX() + (random.nextDouble() - 0.5) * 0.8,
				this.getY() + random.nextDouble() * 1.9, this.getZ() + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.01, 0.0);
		}
		if (random.nextInt(50) == 0) {
			ChronoFx.particle(level, this.ownerPlayer(), ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 1.0 + random.nextDouble() * 0.6, this.getZ(),
				(random.nextDouble() - 0.5) * 0.1, 0.02, (random.nextDouble() - 0.5) * 0.1);
		}
		if (this.tickRun == 0 && random.nextInt(200) == 0) {
			this.tickRun = 4 + random.nextInt(5);
		}
		if (this.tickRun > 0 && this.tickCount % 10 == 0) {
			level.playLocalSound(this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.HOSTILE,
				0.25F, this.tickRun % 2 == 0 ? 1.9F : 1.45F, false);
			this.tickRun--;
		}
	}

	// ---------------------------------------------------------------- fighting

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		DamageSource source = this.damageSources().mobAttack(this);
		boolean hurt = target.hurtServer(level, source, ATTACK_DAMAGE);
		if (hurt) {
			if (target instanceof LivingEntity living) {
				living.knockback(0.4, this.getX() - target.getX(), this.getZ() - target.getZ(), source, ATTACK_DAMAGE);
			}
			this.setLastHurtMob(target);
			if (target instanceof ServerPlayer player) {
				// a blow from out of time: it may pour Time Flow into a Chrono not yet ready for it
				ChronoFlow.echoStruck(player);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.7F, 0.7F);
		}
		return hurt;
	}

	/**
	 * Outside the Nether, any hurt shatters it (it comes back later); in the Nether it takes damage like anything
	 * else and can die for good. {@code /kill} always kills it.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.gone || this.isRemoved()) {
			return false;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || level.dimension() == Level.NETHER) {
			if (this.fight != null && source.getEntity() instanceof ServerPlayer player) {
				// (0.8.50) whoever strikes it (or shoots it, or hits it with a power) sees its bar
				this.fight.hitBy(player);
			}
			return super.hurtServer(level, source, damage);
		}
		if (this.isInvulnerableTo(level, source) || damage <= 0.0F) {
			return false;
		}
		// only an attempt on it (something struck it) or the void shatters it; the world's own hazards (fire, cactus,
		// falls, drowning) just pass through it, so it doesn't flicker in and out on its own
		if (source.getEntity() == null && source.getDirectEntity() == null && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			this.clearFire();
			return false;
		}
		shatter(level);
		return true;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.fight != null) {
			this.fight.end();
			this.fight = null;
		}
		if (!this.gone && this.level() instanceof ServerLevel level) {
			this.gone = true;
			ChronoEchoes.collapse(level, this);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (this.fight != null) {
			// no boss bar left hanging when it's unloaded or discarded
			this.fight.end();
			this.fight = null;
		}
		super.remove(reason);
	}

		/**
	 * In the Nether, below half health: it blinks out like a shattered echo, and comes back around its owner, as hurt as
	 * it left, while they're in the Nether. With its owner offline it simply shatters (and returns the usual way).
	 */
	void blinkAway(ServerLevel level) {
		if (this.gone) {
			return;
		}
		ServerPlayer player = this.owner == null ? null : level.getServer().getPlayerList().getPlayer(this.owner);
		if (player == null) {
			shatter(level);
			return;
		}
		this.gone = true;
		ChronoEchoes.shatterFx(level, this);
		player.setAttached(ChronoEchoes.NETHER_RETURN, new ChronoEchoes.NetherReturn(this.getHealth(), this.bond,
			ChronoEchoes.NETHER_RETURN_MIN + this.getRandom().nextInt(ChronoEchoes.NETHER_RETURN_MAX - ChronoEchoes.NETHER_RETURN_MIN + 1)));
		this.discard();
	}

	/** Back from a blink, to finish the fight: this hurt (once its Nether health is in place). */
	void returnWithHealth(float health) {
		this.returnHealth = health;
	}

	/** The health it comes back with from a blink (0: a fresh echo, at full health). */
	float returnHealth() {
		return this.returnHealth;
	}

	/** Hurt outside the Nether: it breaks apart and is sent back to its owner's future. */
	void shatter(ServerLevel level) {
		if (this.gone) {
			return;
		}
		this.gone = true;
		ChronoEchoes.shatterFx(level, this);
		ChronoEchoes.queueReturn(level.getServer(), this.owner, this.bond);
		this.discard();
	}

	/** Its owner no longer holds Chrono (or holds a new bond): it quietly stops being. */
	void fade(ServerLevel level) {
		if (this.gone) {
			return;
		}
		this.gone = true;
		ChronoEchoes.fadeFx(level, this);
		this.discard();
	}

	/** Nothing drops from an echo: not its owner's item, not loot. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ILLUSIONER_DEATH;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.owner != null) {
			output.store("echo_owner", UUIDUtil.CODEC, this.owner);
		}
		output.putLong("echo_bond", this.bond);
		output.store("echo_profile", ResolvableProfile.CODEC, this.getProfile());
		output.store("echo_mined", Identifier.CODEC.listOf(), this.minedKinds);
		if (this.placedKind != null) {
			output.store("echo_placed", Identifier.CODEC, this.placedKind);
		}
		output.store("echo_route", BlockPos.CODEC.listOf(), this.route);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.owner = input.read("echo_owner", UUIDUtil.CODEC).orElse(null);
		this.bond = input.getLongOr("echo_bond", 0L);
		input.read("echo_profile", ResolvableProfile.CODEC).ifPresent(profile -> this.entityData.set(DATA_PROFILE, profile));
		this.entityData.set(DATA_OWNER, this.owner == null ? Optional.empty() : Optional.of(EntityReference.of(this.owner)));
		this.minedKinds = new ArrayList<>(input.read("echo_mined", Identifier.CODEC.listOf()).orElse(List.of()));
		this.placedKind = input.read("echo_placed", Identifier.CODEC).orElse(null);
		this.route = new ArrayList<>(input.read("echo_route", BlockPos.CODEC.listOf()).orElse(List.of()));
	}

	// ---------------------------------------------------------------- routes

	/** The next stretch of its owner's walking to repeat, as positions to walk to; null if there's nothing to walk. */
	@Nullable List<BlockPos> planRoute() {
		if (this.route.size() < 2) {
			return null;
		}
		RandomSource random = this.getRandom();
		// where it was recorded, if the echo is near one of its points...
		int nearest = -1;
		double best = ROUTE_REACH * ROUTE_REACH;
		for (int i = 0; i < this.route.size(); i++) {
			double d = this.route.get(i).distToCenterSqr(this.position());
			if (d < best) {
				best = d;
				nearest = i;
			}
		}
		List<BlockPos> out = new ArrayList<>(ROUTE_STEPS);
		if (nearest >= 0) {
			boolean forward = nearest + 1 < this.route.size() && (nearest == 0 || random.nextBoolean());
			for (int k = 1; k <= ROUTE_STEPS; k++) {
				int i = forward ? nearest + k : nearest - k;
				if (i < 0 || i >= this.route.size()) {
					break;
				}
				out.add(this.route.get(i));
			}
			return out.isEmpty() ? null : out;
		}
		// ...otherwise the same shape of walk, from right here
		int start = random.nextInt(this.route.size() - 1);
		BlockPos origin = this.route.get(start);
		BlockPos here = this.blockPosition();
		for (int k = 1; k <= ROUTE_STEPS && start + k < this.route.size(); k++) {
			BlockPos p = this.route.get(start + k);
			BlockPos moved = here.offset(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ());
			BlockPos ground = ChronoEchoes.findGround(this.level(), moved.getX(), moved.getY(), moved.getZ(), 4, this.getType());
			if (ground != null) {
				out.add(ground);
			}
		}
		return out.isEmpty() ? null : out;
	}

	/** Walks a stretch of its owner's recorded routes, point by point, when there's nobody to fight. */
	static final class WalkRoutesGoal extends Goal {
		private final ChronoEcho echo;
		private @Nullable List<BlockPos> current;
		private int index;
		private int pause;
		private int stuck;

		WalkRoutesGoal(ChronoEcho echo) {
			this.echo = echo;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.echo.getTarget() != null) {
				return false;
			}
			if (this.pause > 0) {
				this.pause--;
				return false;
			}
			this.current = this.echo.planRoute();
			if (this.current == null) {
				this.pause = ROUTE_PAUSE_MAX;
				return false;
			}
			return true;
		}

		@Override
		public void start() {
			this.index = 0;
			this.stuck = 0;
			moveToCurrent();
		}

		@Override
		public boolean canContinueToUse() {
			return this.echo.getTarget() == null && this.current != null && this.index < this.current.size();
		}

		@Override
		public void tick() {
			if (this.current == null) {
				return;
			}
			if (this.echo.getNavigation().isDone() || ++this.stuck > 200) {
				this.index++;
				this.stuck = 0;
				if (this.index < this.current.size()) {
					moveToCurrent();
				}
			}
		}

		private void moveToCurrent() {
			BlockPos p = this.current.get(this.index);
			this.echo.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ROUTE_SPEED);
		}

		@Override
		public void stop() {
			this.current = null;
			this.pause = ROUTE_PAUSE_MIN + this.echo.getRandom().nextInt(ROUTE_PAUSE_MAX - ROUTE_PAUSE_MIN + 1);
			this.echo.getNavigation().stop();
		}
	}
}
