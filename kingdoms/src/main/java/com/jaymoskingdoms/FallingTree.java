package com.jaymoskingdoms;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.9) A felled tree toppling over, the way Dynamic Trees' do (our own code): the moment it's cut, its logs and the
 * leaves they held leave the world and become this, hinged at the cut. It tips away from whoever cut it, slowly at
 * first and faster as it goes over (a falling pole: its swing grows with how far it leans, and a tall tree swings
 * slower than a short one), until its wood strikes the ground or it lies flat. Where it lands it breaks apart with a
 * crash: every log and leaf gives what it would have, where it came to rest (or into a woodcutter's bag), and anything
 * standing where it comes down is struck.
 * <p>
 * (0.7.11) Physical Falling Trees, our own take (the datapack is CC BY-NC-ND, none of it used): it falls to its heavier
 * side ({@link #heavier}), straight along a compass line, and only away from its cutter when it's even; it strikes
 * anything in its path (its cutter too) as hard as the wood there is moving; bees in a nest on it come out angry
 * ({@link #hives}); and down, it lies a moment and becomes real blocks where it came to rest, its logs on their side and
 * its leaves withering away ({@link #settle}). Felled in under half a second, it settles at once. A woodcutter's tree
 * still breaks up into his bag, and a tornado's still smashes apart.
 */
public final class FallingTree extends Entity {
	/** Its first push (degrees a tick), a steady nudge, and the pull of its own weight (scaled by how far it leans). */
	static final float START_SPEED = 0.3F, NUDGE = 0.03F, WEIGHT = 4.0F;
	/** It lies flat at this lean; it never takes longer than this to come down (ticks). */
	static final float FLAT = 90.0F;
	static final int LONGEST = 200;
	/** Down, it lies a moment (ticks) before it settles; down sooner than this after the cut, it settles at once. */
	static final int LIES = 10, QUICK = 10;
	/** (0.7.11) What it strikes: damage per block a tick the wood is moving there, the most, and the slowest that hurts. */
	static final float HIT_PER_SPEED = 7.0F, CRUSH_MOST = 14.0F, HIT_SLOWEST = 0.12F;
	/** (0.7.11) How it picks its side: a log weighs this, a leaf 1; its weight this far (blocks) off the trunk tips it. */
	static final float LOG_WEIGHT = 3.0F, TIPS = 0.35F;
	/** (0.7.11) Settling, a piece with no room where it lies may sink this far onto what's below. */
	static final int SINKS = 3;
	/** (0.7.11) Bees round a nest in it (this far) go for a woodcutter who fells it. */
	static final double BEES_RANGE = 24.0;
	/** Of its blocks, every this-many'th log and leaf breaks with a puff and a sound (the rest only a puff of chips). */
	static final int LOG_SOUNDS = 3, LEAF_SOUNDS = 9;
	/** (0.6.12) A torn-up tree in flight: its fall (blocks/tick²), the air's drag, how long before it can strike the ground
	 * (ticks, to leave its own hole) and the longest it flies. */
	static final double FLY_GRAVITY = 0.05, FLY_DRAG = 0.985;
	static final int FLY_CLEAR = 6, FLY_LONGEST = 600;

	/** One block of the tree: its state, and where it stood relative to the cut. */
	public record Piece(int state, int x, int y, int z) {
		public static final StreamCodec<ByteBuf, Piece> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Piece::state,
			ByteBufCodecs.VAR_INT, Piece::x, ByteBufCodecs.VAR_INT, Piece::y, ByteBufCodecs.VAR_INT, Piece::z, Piece::new);

		public BlockState blockState() {
			return Block.stateById(this.state);
		}
	}

	public static final AttachmentType<List<Piece>> PIECES = AttachmentRegistry.create(JaymosKingdoms.id("falling_tree_pieces"),
		builder -> builder.syncWith(Piece.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));
	/** Which way it falls (radians: 0 is east, along +x; a quarter turn is south, +z). */
	public static final AttachmentType<Float> HEADING = AttachmentRegistry.create(JaymosKingdoms.id("falling_tree_heading"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));
	/** How far over it leans (degrees from upright). */
	private static final EntityDataAccessor<Float> LEAN = SynchedEntityData.defineId(FallingTree.class, EntityDataSerializers.FLOAT);

	public static EntityType<FallingTree> TYPE;

	/** Client side: the lean last tick, to draw it smoothly between ticks. */
	public float leanO;
	private float speed = START_SPEED;
	/** When it came down (-1: still falling). */
	private int downAt = -1;
	private float reach = 2.0F;
	private @Nullable Entity cutter;
	private ItemStack tool = ItemStack.EMPTY;
	private @Nullable Consumer<ItemStack> sink;
	/** (0.6.12) Torn up by a tornado: it flies (ticks flown), tumbling this many degrees a tick. */
	private boolean flying, torn;
	private int flown;
	private float tumble;
	/** (0.7.11) What it has struck already (once each). */
	private final Set<UUID> struck = new HashSet<>();

	public FallingTree(EntityType<? extends FallingTree> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("falling_tree"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<FallingTree>of(FallingTree::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.0F, 1.0F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.build(key));
	}

	/**
	 * The tree whose {@code logs} and {@code leaves} stood on {@code cut} comes down toward {@code heading}: they leave
	 * the world now, and fall as one. What it gives goes to {@code sink} (null: where each block comes to rest).
	 */
	static void topple(ServerLevel level, BlockPos cut, List<BlockPos> logs, List<BlockPos> leaves, float heading, @Nullable Entity cutter,
		ItemStack tool, @Nullable Consumer<ItemStack> sink) {
		FallingTree tree = new FallingTree(TYPE, level);
		tree.setPos(cut.getX() + 0.5, cut.getY(), cut.getZ() + 0.5);
		int top = take(level, tree, cut, logs, leaves);
		if (top <= 0) {
			return;
		}
		tree.setAttached(HEADING, heading);
		tree.reach = Math.max(2.0F, top * 0.55F);
		tree.cutter = cutter;
		tree.tool = tool.copy();
		tree.sink = sink;
		level.addFreshEntity(tree);
		level.playSound(null, cut, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
	}

	/**
	 * (0.6.12) The tree whose {@code logs} and {@code leaves} stood on {@code foot} is torn out of the ground whole (by a
	 * tornado) and flies off at {@code velocity}, tumbling end over end toward {@code heading} by {@code tumble} degrees a
	 * tick (null if nothing was there). It falls as anything does once the winds let it go, and breaks apart where it strikes the ground.
	 */
	public static @Nullable FallingTree fling(ServerLevel level, BlockPos foot, List<BlockPos> logs, List<BlockPos> leaves, Vec3 velocity, float heading, float tumble) {
		int height = 1;
		for (BlockPos p : logs) {
			height = Math.max(height, p.getY() - foot.getY() + 1);
		}
		// it turns about its middle, not its foot
		BlockPos middle = foot.above(height / 2);
		FallingTree tree = new FallingTree(TYPE, level);
		tree.setPos(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
		if (take(level, tree, middle, logs, leaves) <= 0) {
			return null;
		}
		tree.setAttached(HEADING, heading);
		tree.flying = true;
		tree.torn = true;
		tree.tumble = tumble;
		tree.setDeltaMovement(velocity);
		level.addFreshEntity(tree);
		level.playSound(null, foot, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
		level.playSound(null, foot, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		return tree;
	}

	/**
	 * Its {@code logs} and {@code leaves} leave the world and become {@code tree}'s pieces, placed from {@code origin}.
	 * Returns how tall it stands above {@code origin} (0: nothing was there).
	 */
	private static int take(ServerLevel level, FallingTree tree, BlockPos origin, List<BlockPos> logs, List<BlockPos> leaves) {
		List<Piece> pieces = new ArrayList<>();
		int top = 1;
		for (List<BlockPos> part : List.of(logs, leaves)) {
			for (BlockPos p : part) {
				// ((0.5.25) a chopped log falls as the log it was)
				BlockState state = ChoppedLog.real(level, p, level.getBlockState(p));
				if (state.isAir()) {
					continue;
				}
				pieces.add(new Piece(Block.getId(state), p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ()));
				top = Math.max(top, p.getY() - origin.getY() + 1);
			}
		}
		if (pieces.isEmpty()) {
			return 0;
		}
		// they leave the world all at once (the leaves first, so none decay as the wood goes)
		for (int i = pieces.size() - 1; i >= 0; i--) {
			Piece p = pieces.get(i);
			level.setBlock(origin.offset(p.x(), p.y(), p.z()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (Piece p : pieces) {
			BlockPos at = origin.offset(p.x(), p.y(), p.z());
			level.updateNeighborsAt(at, Blocks.AIR);
		}
		tree.setAttached(PIECES, List.copyOf(pieces));
		return top;
	}

	/**
	 * (0.7.11) Which way the tree of {@code logs} and {@code leaves} on {@code cut} falls (radians, as {@link #HEADING}, along
	 * a compass line): to its heavier side, a log weighing {@link #LOG_WEIGHT} and a leaf 1; if it's even (its weight
	 * less than {@link #TIPS} off the trunk), away from {@code cutter}.
	 */
	static float heavier(ServerLevel level, BlockPos cut, List<BlockPos> logs, List<BlockPos> leaves, @Nullable Entity cutter) {
		double x = 0.0, z = 0.0, total = 0.0;
		for (List<BlockPos> part : List.of(logs, leaves)) {
			for (BlockPos p : part) {
				BlockState state = level.getBlockState(p);
				double w = state.is(BlockTags.LOGS) || state.is(ChoppedLog.BLOCK) ? LOG_WEIGHT : 1.0;
				x += w * (p.getX() - cut.getX());
				z += w * (p.getZ() - cut.getZ());
				total += w;
			}
		}
		if (total > 0.0 && Math.max(Math.abs(x), Math.abs(z)) / total >= TIPS) {
			return Math.abs(x) >= Math.abs(z) ? (x > 0 ? 0.0F : Mth.PI) : (z > 0 ? Mth.HALF_PI : -Mth.HALF_PI);
		}
		float away = away(cutter, cut, level);
		return Math.round(away / Mth.HALF_PI) * Mth.HALF_PI;
	}

	/**
	 * (0.7.11) The bee nests and hives on these logs: their bees come out now, angry at {@code cutter} (if near), and
	 * the nests come down with the tree.
	 */
	static List<BlockPos> hives(ServerLevel level, List<BlockPos> logs, @Nullable Entity cutter) {
		Set<BlockPos> found = new HashSet<>();
		for (BlockPos log : logs) {
			for (Direction d : Direction.values()) {
				BlockPos p = log.relative(d);
				if (level.getBlockState(p).is(BlockTags.BEEHIVES)) {
					found.add(p.immutable());
				}
			}
		}
		Player player = cutter instanceof Player p && !p.isCreative() ? p : null;
		for (BlockPos p : found) {
			if (level.getBlockEntity(p) instanceof BeehiveBlockEntity hive) {
				// (the game's own: out, and at a player near unless they're smoked)
				hive.emptyAllLivingFromHive(player, level.getBlockState(p), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
			}
			if (cutter instanceof LivingEntity target && !(cutter instanceof Player)) {
				for (Bee bee : level.getEntitiesOfClass(Bee.class, new AABB(p).inflate(BEES_RANGE / 3.0))) {
					if (bee.getTarget() == null) {
						bee.setPersistentAngerTarget(EntityReference.of(target));
						bee.setTarget(target);
					}
				}
			}
		}
		return new ArrayList<>(found);
	}

	/** The way from {@code from} to {@code cut} (radians, as {@link #HEADING}), or any way if there's no one. */
	static float away(@Nullable Entity from, BlockPos cut, ServerLevel level) {
		if (from == null) {
			return level.getRandom().nextFloat() * Mth.TWO_PI;
		}
		double dx = cut.getX() + 0.5 - from.getX(), dz = cut.getZ() + 0.5 - from.getZ();
		return dx * dx + dz * dz < 1.0E-4 ? from.getYRot() * Mth.DEG_TO_RAD + Mth.HALF_PI : (float)Math.atan2(dz, dx);
	}

	public float lean() {
		return this.entityData.get(LEAN);
	}

	public List<Piece> pieces() {
		List<Piece> pieces = this.getAttached(PIECES);
		return pieces == null ? List.of() : pieces;
	}

	public float heading() {
		Float h = this.getAttached(HEADING);
		return h == null ? 0.0F : h;
	}

	/** The turn of a tree leaning {@code lean} degrees toward {@code heading}: upright tips over toward the heading. */
	public static Quaternionf turn(float lean, float heading) {
		float dx = Mth.cos(heading), dz = Mth.sin(heading);
		return new Quaternionf().rotateAxis(lean * Mth.DEG_TO_RAD, dz, 0.0F, -dx);
	}

	/** Where a piece's middle is with the tree leaning {@code lean}. */
	private Vec3 where(Piece p, Quaternionf turn) {
		Vector3f v = turn.transform(new Vector3f(p.x(), p.y() + 0.5F, p.z()));
		return this.position().add(v.x, v.y, v.z);
	}

	@Override
	public void tick() {
		this.leanO = this.lean();
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		List<Piece> pieces = this.pieces();
		if (pieces.isEmpty()) {
			this.discard();
			return;
		}
		if (this.downAt >= 0) {
			if (this.tickCount - this.downAt >= LIES) {
				if (this.torn || this.sink != null) {
					this.breakApart(level, pieces);
				} else {
					this.settle(level, pieces);
				}
			}
			return;
		}
		if (this.flying) {
			this.fly(level, pieces);
			return;
		}
		float lean = this.lean();
		this.speed += NUDGE + WEIGHT * Mth.sin(lean * Mth.DEG_TO_RAD) / this.reach;
		float next = Math.min(FLAT, lean + this.speed);
		boolean blocked = this.strikes(level, pieces, next, 2);
		boolean landed = next >= FLAT || this.tickCount >= LONGEST || blocked;
		float at = blocked ? lean : next;
		this.hit(level, pieces, at, 2, 0.0F, this.speed * Mth.DEG_TO_RAD);
		if (!landed) {
			this.entityData.set(LEAN, next);
			// the rush of air and creaking as it goes over
			if (this.tickCount % 6 == 0) {
				level.playSound(null, this.blockPosition(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.8F, 0.5F + lean / 180.0F);
			}
			return;
		}
		this.land(level, pieces, at);
		// (down in under half a second: it settles at once)
		this.downAt = this.tickCount < QUICK ? this.tickCount - LIES : this.tickCount;
	}

	/** (0.6.12) In flight: it falls and tumbles, and once clear of its hole breaks apart where its wood strikes the ground. */
	private void fly(ServerLevel level, List<Piece> pieces) {
		Vec3 v = this.getDeltaMovement().add(0.0, -FLY_GRAVITY, 0.0).scale(FLY_DRAG);
		this.setDeltaMovement(v);
		this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
		this.flown++;
		if (this.getY() < level.getMinY()) {
			this.discard();
			return;
		}
		float lean = this.lean(), next = lean + this.tumble;
		if (this.flown > FLY_CLEAR && (this.flown > FLY_LONGEST || this.strikes(level, pieces, next, Integer.MIN_VALUE))) {
			this.flying = false;
			this.hit(level, pieces, lean, Integer.MIN_VALUE, (float)v.length(), this.tumble * Mth.DEG_TO_RAD);
			this.land(level, pieces, lean);
			this.downAt = this.tickCount;
			return;
		}
		this.entityData.set(LEAN, next);
		if (this.flown % 8 == 0) {
			level.playSound(null, this.blockPosition(), SoundEvents.AZALEA_LEAVES_STEP, SoundSource.BLOCKS, 1.2F, 0.6F);
		}
	}

	/** Whether this tree has been torn up by a tornado and is still flying, and for how long (ticks). */
	public int flown() {
		return this.flying ? this.flown : -1;
	}

	/** Whether its wood (from {@code fromY} above its origin up), leaning {@code lean}, runs into anything solid. */
	private boolean strikes(ServerLevel level, List<Piece> pieces, float lean, int fromY) {
		Quaternionf turn = turn(lean, this.heading());
		for (Piece p : pieces) {
			if (p.y() < fromY || !p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			BlockPos at = this.cell(this.where(p, turn));
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced() && !(there.getBlock() instanceof LeavesBlock) && there.isCollisionShapeFullBlock(level, at)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * (0.7.11) Its wood (from {@code fromY} above its origin up), leaning {@code lean}, strikes whatever it passes through
	 * (once each) as hard as it's moving there: {@code flat} blocks a tick, plus {@code turning} for every block from
	 * where it turns. Its cutter is struck too, unless he's a woodcutter filling his bag.
	 */
	private void hit(ServerLevel level, List<Piece> pieces, float lean, int fromY, float flat, float turning) {
		Quaternionf turn = turn(lean, this.heading());
		Map<LivingEntity, Float> worst = new HashMap<>();
		for (Piece p : pieces) {
			if (p.y() < fromY || !p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			float speed = flat + turning * Math.abs(p.y() + 0.5F);
			if (speed < HIT_SLOWEST) {
				continue;
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(this.where(p, turn))).inflate(0.3),
				e -> e.isAlive() && !e.isSpectator() && !this.struck.contains(e.getUUID()) && (this.sink == null || e != this.cutter))) {
				worst.merge(e, speed, Math::max);
			}
		}
		DamageSource source = level.damageSources().fallingBlock(this);
		float dx = Mth.cos(this.heading()), dz = Mth.sin(this.heading());
		for (Map.Entry<LivingEntity, Float> hit : worst.entrySet()) {
			LivingEntity e = hit.getKey();
			float speed = hit.getValue();
			this.struck.add(e.getUUID());
			if (e.hurtServer(level, source, Math.min(CRUSH_MOST, speed * HIT_PER_SPEED))) {
				// (knocked the way it falls)
				e.push(dx * speed * 0.6F, 0.15, dz * speed * 0.6F);
				e.needsSync = true;
			}
		}
	}

	/** It comes down: a thud and a cloud of leaves. */
	private void land(ServerLevel level, List<Piece> pieces, float lean) {
		this.entityData.set(LEAN, lean);
		Quaternionf turn = turn(lean, this.heading());
		Vec3 middle = Vec3.ZERO;
		int count = 0;
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			middle = middle.add(this.where(p, turn));
			count++;
		}
		if (count > 0) {
			middle = middle.scale(1.0 / count);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 0.5F);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4F, 1.7F);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, middle.x, middle.y, middle.z, 6, 1.5, 0.2, 1.5, 0.01);
		}
	}

	/** Lain a moment, it breaks apart where it lies, giving what it would have. */
	private void breakApart(ServerLevel level, List<Piece> pieces) {
		Quaternionf turn = turn(this.lean(), this.heading());
		int logCount = 0, leafCount = 0;
		for (Piece p : pieces) {
			BlockState state = p.blockState();
			Vec3 at = this.where(p, turn);
			BlockPos pos = BlockPos.containing(at);
			boolean log = state.is(BlockTags.LOGS);
			for (ItemStack drop : Block.getDrops(state, level, pos, null, this.cutter, this.tool)) {
				if (this.sink != null) {
					this.sink.accept(drop);
				} else {
					Block.popResource(level, pos, drop);
				}
			}
			if (log ? logCount++ % LOG_SOUNDS == 0 : leafCount++ % LEAF_SOUNDS == 0) {
				level.levelEvent(2001, pos, Block.getId(state));
			} else {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.05);
			}
		}
		this.discard();
	}

	/**
	 * (0.7.11) Lain a moment, it becomes real blocks where it lies: its logs (and nests) turned on their side, its leaves
	 * left to wither (no wood holds them now). A piece with no room there (or two in one place) gives what it would have.
	 */
	private void settle(ServerLevel level, List<Piece> pieces) {
		float lean = this.lean();
		Quaternionf turn = turn(lean, this.heading());
		boolean lying = lean >= 45.0F;
		// (lying, it settles flat along the ground, held up only by what it really came to rest on)
		Quaternionf flat = lying ? turn(FLAT, this.heading()) : turn;
		Direction.Axis along = Math.abs(Mth.cos(this.heading())) >= Math.abs(Mth.sin(this.heading())) ? Direction.Axis.X : Direction.Axis.Z;
		Set<BlockPos> taken = new HashSet<>();
		List<Piece> ordered = new ArrayList<>(pieces);
		// (wood first, lowest first: it takes its place before the leaves do)
		ordered.sort(Comparator.<Piece>comparingInt(p -> p.blockState().is(BlockTags.LOGS) ? 0 : 1).thenComparingInt(Piece::y));
		int logCount = 0;
		for (Piece p : ordered) {
			BlockState state = p.blockState();
			boolean log = state.is(BlockTags.LOGS);
			if (lying && state.hasProperty(BlockStateProperties.AXIS)) {
				Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
				state = state.setValue(BlockStateProperties.AXIS, axis == Direction.Axis.Y ? along : axis == along ? Direction.Axis.Y : axis);
			}
			if (state.getBlock() instanceof LeavesBlock) {
				state = state.setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, LeavesBlock.DECAY_DISTANCE);
			}
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			Vec3 at = this.where(p, turn);
			BlockPos pos = this.cell(this.where(p, flat));
			if (lying) {
				// up onto what it struck (no higher than it really lay), then down onto the ground a little way
				int rested = this.cell(at).getY();
				while (!room(level, pos, taken) && pos.getY() < rested) {
					pos = pos.above();
				}
				for (int i = 0; i < SINKS && room(level, pos, taken) && room(level, pos.below(), taken); i++) {
					pos = pos.below();
				}
			}
			if (room(level, pos, taken)) {
				taken.add(pos);
				// (known shape: its leaves keep their withering, not taking the lying wood for a tree's)
				level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (log && logCount++ % LOG_SOUNDS == 0) {
					level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
				}
				continue;
			}
			for (ItemStack drop : Block.getDrops(p.blockState(), level, pos, null, this.cutter, this.tool)) {
				Block.popResource(level, pos, drop);
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.blockState()), at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.05);
		}
		this.discard();
	}

	/** Whether a settling piece fits at {@code pos}: nothing solid there (water will do) and no other piece. */
	private static boolean room(ServerLevel level, BlockPos pos, Set<BlockPos> taken) {
		BlockState there = level.getBlockState(pos);
		return !taken.contains(pos) && there.canBeReplaced() && (there.getFluidState().isEmpty() || there.getFluidState().is(FluidTags.WATER));
	}

	/**
	 * The block a piece's middle is in. Lying, a tree's pieces sit on block edges (it turns about the foot of its cut),
	 * so they're nudged up and back toward the cut: its wood lies on the ground, from where it stood.
	 */
	private BlockPos cell(Vec3 at) {
		float heading = this.heading();
		return BlockPos.containing(at.x - 0.01 * Mth.cos(heading), at.y + 0.01, at.z - 0.01 * Mth.sin(heading));
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(LEAN, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
