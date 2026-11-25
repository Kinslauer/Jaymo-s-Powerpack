package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Input;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
 * still breaks up into his bag; a tree a tornado threw lands on its side the same way, wherever it comes down.
 * <p>
 * (0.7.232) From Tree Physics' ideas (ARR, none of its code): coming down, its wood breaks through other trees' leaves
 * in its way ({@link #sweep}); landing, some of its own leaves snap off, more the harder they struck ({@link #snaps});
 * and lying, its logs were a fallen trunk you could push or roll (0.7.588: no more; it stays a fallen tree, see below).
 * <p>
 * (0.7.456) Felled into another tree, it never stands back up. (0.7.588) A tree it strikes is knocked over with it
 * ({@link #knock}); one too big to knock over holds it leaning there (no more glancing off to the side, Andy: it
 * "took an unexpected swapped trajectory and hit me").
 * <p>
 * (0.7.588) Down, it stays as it is (never blocks again) until it's chopped up: an axe breaks out the log it strikes,
 * and a tree broken through comes apart in two, the part no longer held falling free ({@link #skipAttackInteraction}).
 * Its leaves drop off as it lies. It's kept with the world.
 * <p>
 * (0.7.586) It knocks over standing trees it strikes ({@link #knock}) and smashes through blocks weaker than
 * its wood ({@link #smash}).
 */
public final class FallingTree extends Entity {
	/** Its first push (degrees a tick), a steady nudge, and the pull of its own weight (scaled by how far it leans). */
	static final float START_SPEED = 0.3F, NUDGE = 0.03F, WEIGHT = 4.0F;
	/** It lies flat at this lean; it never takes longer than this to come down (ticks). */
	static final float FLAT = 90.0F;
	static final int LONGEST = 200;
	/** Down, it lies a moment (ticks) before it settles; down sooner than this after the cut, it settles at once. */
	static final int LIES = 10;
	/** (0.7.11) What it strikes: damage per block a tick the wood is moving there, the most, and the slowest that hurts. */
	static final float HIT_PER_SPEED = 7.0F, CRUSH_MOST = 14.0F, HIT_SLOWEST = 0.12F;
	/** (0.7.11) How it picks its side: a log weighs this, a leaf 1; its weight this far (blocks) off the trunk tips it. */
	static final float LOG_WEIGHT = 3.0F, TIPS = 0.35F;
	/**
	 * (0.7.588) Andy: "keep the trees as physics items until they are broken by the player ... a leaning tree broken in
	 * the middle will break into two". Down, it stays this (never blocks): an axe takes {@link #LOG_CHOPS} strokes at a
	 * log to break it out, a leaf one. Its leaves drop off as it lies, up to {@link #LEAF_FALL} every
	 * {@link #LEAF_FALL_TICKS}; a leaf further than {@link #LEAF_HOLD} from any of its wood falls at once.
	 */
	static final int LOG_CHOPS = 3, LEAF_FALL = 2, LEAF_FALL_TICKS = 10, LEAF_HOLD = 3;
	/**
	 * (0.7.588) A tree that's down looks again this often (ticks) whether what holds it up is gone: free to turn further
	 * about its foot, it falls on; with none of its wood resting on anything, it drops.
	 */
	static final int PROP_CHECK = 20;
	/** (0.7.588) A part broken off a leaning tree falls free, turning this many degrees a tick. */
	static final float SPLIT_TUMBLE = 3.0F;
	/**
	 * (0.7.588) Andy: "giants should also be able to grab and throw the trees with their grabs". Held, it lies this far over
	 * (degrees) along the giant's facing; let go of for this long (ticks) without a word from its giant, it drops.
	 */
	static final float HELD_LEAN = 75.0F;
	static final int HELD_LONGEST = 5;
	/** (0.7.588) A thrown tree turns end over end this fast (degrees a tick). */
	public static final float THROWN_TUMBLE = 6.0F;
	/** (0.7.11) Bees round a nest in it (this far) go for a woodcutter who fells it. */
	static final double BEES_RANGE = 24.0;
	/** Of its blocks, every this-many'th log and leaf breaks with a puff and a sound (the rest only a puff of chips). */
	static final int LOG_SOUNDS = 3, LEAF_SOUNDS = 9;
	/** (0.6.12) A torn-up tree in flight: its fall (blocks/tick²), the air's drag, how long before it can strike the ground
	 * (ticks, to leave its own hole) and the longest it flies. */
	public static final double FLY_GRAVITY = 0.05, FLY_DRAG = 0.985;
	static final int FLY_CLEAR = 6, FLY_LONGEST = 600;
	/** (0.7.232) Its leaves snap off as it lands: this chance at the most, for leaves striking the ground this fast (blocks a tick) or faster. */
	static final float LEAF_SNAP = 0.5F, LEAF_SNAP_FULL = 1.5F;
	/** (0.7.232) The most standing leaves (other trees') its wood breaks through in a tick as it comes down. */
	static final int SWEEP_MOST = 12;
	/** (0.7.456) Under this lean (degrees) it isn't lying yet. */
	static final float LYING = 45.0F;
	/**
	 * (0.7.586) Andy: "the trees that fall should knock each other over". Andy: "they should crash through what they hit,
	 * one time and straight to the ground. dealing damage the whole way". Any standing tree its wood reaches (trunk or
	 * branch) goes over its way, however slowly it's turning and however big that tree is, already turning as fast as it
	 * is ({@link #carried}); each of those knocks on in turn. A falling tree only comes to rest leaning on what isn't a
	 * tree (a wall, a rise of ground), and stays there ({@link #rest}).
	 */
	/** (0.7.586) The most blocks weaker than its wood ({@link #weaker}) it smashes through in a tick. */
	static final int SMASH_MOST = 12;
	/**
	 * Andy: "trees dont seem to be solid after they fall?" Down, each of its logs is a block's worth of solid wood where
	 * it lies ({@link #collisions}): to stand on, walk along, walk into. Leaves stay soft (pushed through, as they drop
	 * off anyway). A mob that walks into a log no higher than this (blocks over its feet) hops it.
	 */
	static final double HOPS = 1.2;
	/**
	 * Andy: only strong characters and giants shove a fallen tree. Lying flat, it slides and swings (about its middle)
	 * under anyone with this much brawn ({@link #brawn}: Strength II, twice a man's size, Might and the like) walking
	 * into one of its logs. Each point of brawn moves {@link #LOGS_PER_BRAWN} logs at {@link #SHOVE_SPEED} (blocks a
	 * tick at the log pushed); a heavier tree goes slower, and one moving slower than {@link #SHOVE_SLOWEST} doesn't move.
	 */
	static final int SHOVE_BRAWN = 2, LOGS_PER_BRAWN = 4;
	static final double SHOVE_SPEED = 0.08, SHOVE_SLOWEST = 0.006;
	/** A person's height (blocks): each half again as tall as this is a point of brawn, as for hauling. */
	static final float MAN_HEIGHT = 1.8F, HEIGHT_PER_BRAWN = 0.5F;

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
	/**
	 * On someone leaning into a fallen tree's log with both hands (Andy: "your character should animate both hands on it
	 * and pushing, only moving it when you have sufficient strength"): the middle of the log they push, for every screen
	 * to draw them at it. The weak push as hard as the strong; only the strong move it ({@link #shove}).
	 */
	public static final AttachmentType<Vec3> PUSHING = AttachmentRegistry.create(JaymosKingdoms.id("pushing_tree"),
		builder -> builder.syncWith(Vec3.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Who pushed a fallen tree, and the last tick they did. */
	private static final Map<LivingEntity, Long> PUSHERS = new WeakHashMap<>();
	/** How far it has to move (blocks) before the log pushed is told every screen again. */
	static final double PUSH_RESYNC = 0.25;
	/** A log pushed is within this of straight ahead (the cosine of the angle off the way they go). */
	static final double AHEAD = 0.6;
	/** How far over it leans (degrees from upright). */
	private static final EntityDataAccessor<Float> LEAN = SynchedEntityData.defineId(FallingTree.class, EntityDataSerializers.FLOAT);
	/** Lying still on the ground (down, not flying, held or going into a woodcutter's bag): its logs are solid. */
	private static final EntityDataAccessor<Boolean> SOLID = SynchedEntityData.defineId(FallingTree.class, EntityDataSerializers.BOOLEAN);

	public static EntityType<FallingTree> TYPE;

	/** Client side: the lean last tick, to draw it smoothly between ticks. */
	public float leanO;
	private float speed = START_SPEED;
	/** (0.7.588) Down and at rest, and since when. */
	private boolean down;
	private int downAt = -1;
	private float reach = 2.0F;
	private @Nullable Entity cutter;
	private ItemStack tool = ItemStack.EMPTY;
	private @Nullable Consumer<ItemStack> sink;
	/** (0.6.12) Torn up by a tornado: it flies (ticks flown), tumbling this many degrees a tick. */
	private boolean flying;
	private int flown;
	private float tumble;
	/** (0.7.11) What it has struck already (once each). */
	private final Set<UUID> struck = new HashSet<>();
	/** (0.7.232) How fast it struck the ground: its whole speed (blocks a tick), and its turn (per block from its turning point). */
	private float landFlat, landTurning;
	/** (0.7.456) Whether it came to rest still half standing. */
	private boolean propped;
	/** How far (blocks) it was raised as it came to lie, so its wood rests on the ground; taken back off if it falls on. */
	private float lift;
	/** (0.7.588) Held in a giant's hand (and when it was last held there), and who threw it (never struck by it). */
	private boolean held;
	private int heldAt;
	/** (0.7.588) Thrown by a giant: it strikes what it flies through, not only where it lands. */
	private boolean hurled;
	/** (0.7.588) Axe strokes each of its logs has taken. */
	private final Map<Piece, Integer> chops = new HashMap<>();
	/** (0.7.588) What its bounding box was last worked out from. */
	private @Nullable List<Piece> boxPieces;
	private float boxLean = Float.NaN, boxHeading = Float.NaN;

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
		// those who stopped pushing take their hands off
		ServerTickEvents.END_SERVER_TICK.register(server -> PUSHERS.entrySet().removeIf(e -> {
			LivingEntity who = e.getKey();
			if (who.isAlive() && e.getValue() >= who.level().getGameTime()) {
				return false;
			}
			who.removeAttached(PUSHING);
			return true;
		}));
	}

	/**
	 * The tree whose {@code logs} and {@code leaves} stood on {@code cut} comes down toward {@code heading}: they leave
	 * the world now, and fall as one. What it gives goes to {@code sink} (null: where each block comes to rest).
	 */
	static @Nullable FallingTree topple(ServerLevel level, BlockPos cut, List<BlockPos> logs, List<BlockPos> leaves, float heading, @Nullable Entity cutter,
		ItemStack tool, @Nullable Consumer<ItemStack> sink) {
		FallingTree tree = new FallingTree(TYPE, level);
		tree.setPos(cut.getX() + 0.5, cut.getY(), cut.getZ() + 0.5);
		// the birds in its crown burst out as it goes
		Felling.flush(level, leaves);
		int top = take(level, tree, cut, logs, leaves);
		if (top <= 0) {
			return null;
		}
		tree.setAttached(HEADING, heading);
		tree.reach = Math.max(2.0F, top * 0.55F);
		tree.cutter = cutter;
		tree.tool = tool.copy();
		tree.sink = sink;
		level.addFreshEntity(tree);
		level.playSound(null, cut, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		return tree;
	}

	/** Knocked over by a tree turning {@code speed} degrees a tick: it goes over at least that fast. */
	void carried(float speed) {
		this.speed = Math.max(this.speed, speed);
	}

	/** How far (degrees) it turns on its next tick falling from where it leans now, starting from rest. */
	private float firstStep() {
		return START_SPEED + NUDGE + WEIGHT * Mth.sin(this.lean() * Mth.DEG_TO_RAD) / this.reach;
	}

	/**
	 * (0.6.12) The tree whose {@code logs} and {@code leaves} stood on {@code foot} is torn out of the ground whole (by a
	 * tornado) and flies off at {@code velocity}, tumbling end over end toward {@code heading} by {@code tumble} degrees a
	 * tick (null if nothing was there). It falls as anything does once the winds let it go, and breaks apart where it strikes the ground.
	 */
	public static @Nullable FallingTree fling(ServerLevel level, BlockPos foot, List<BlockPos> logs, List<BlockPos> leaves, Vec3 velocity, float heading, float tumble) {
		FallingTree tree = loose(level, foot, logs, leaves, velocity, heading, tumble);
		if (tree != null) {
			level.playSound(null, foot, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
			level.playSound(null, foot, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		}
		return tree;
	}

	/**
	 * Wood left hanging on nothing ({@link LooseWood}): the {@code logs} and {@code leaves} whose lowest log is
	 * {@code foot} drop from where they hang, tumbling toward {@code heading} by {@code tumble} degrees a tick, and land
	 * and crash as any falling tree does. Null if nothing was there.
	 */
	static @Nullable FallingTree drop(ServerLevel level, BlockPos foot, List<BlockPos> logs, List<BlockPos> leaves, float heading, float tumble) {
		FallingTree tree = loose(level, foot, logs, leaves, Vec3.ZERO, heading, tumble);
		if (tree != null) {
			// (it falls at once, and lands on the first thing under it: there's no hole of its own to clear)
			hurl(tree, Vec3.ZERO, tumble, null);
			level.playSound(null, foot, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		}
		return tree;
	}

	/** {@link #fling}'s tree, without its sounds. */
	private static @Nullable FallingTree loose(ServerLevel level, BlockPos foot, List<BlockPos> logs, List<BlockPos> leaves, Vec3 velocity, float heading, float tumble) {
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
		tree.tumble = tumble;
		tree.setDeltaMovement(velocity);
		level.addFreshEntity(tree);
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
		double[] off = offset(level, cut, logs, leaves);
		double x = off[0], z = off[1];
		if (Math.max(Math.abs(x), Math.abs(z)) >= TIPS) {
			return Math.abs(x) >= Math.abs(z) ? (x > 0 ? 0.0F : Mth.PI) : (z > 0 ? Mth.HALF_PI : -Mth.HALF_PI);
		}
		float away = away(cutter, cut, level);
		return Math.round(away / Mth.HALF_PI) * Mth.HALF_PI;
	}

	/**
	 * How far (blocks, east and south) the weight of the tree of {@code logs} and {@code leaves} on {@code cut} lies off
	 * it, a log weighing {@link #LOG_WEIGHT} and a leaf 1 ({@link Felling#way} reads it too).
	 */
	static double[] offset(ServerLevel level, BlockPos cut, List<BlockPos> logs, List<BlockPos> leaves) {
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
		return total > 0.0 ? new double[] {x / total, z / total} : new double[] {0.0, 0.0};
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
		this.fitBox();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(SOLID, this.down && this.sink == null && !this.held && !this.flying);
		List<Piece> pieces = this.pieces();
		if (pieces.isEmpty()) {
			this.discard();
			return;
		}
		if (this.held) {
			// (0.7.588) let go of (its giant gone): it falls from where it was held
			if (this.tickCount - this.heldAt > HELD_LONGEST) {
				hurl(this, Vec3.ZERO, SPLIT_TUMBLE, null);
			}
			return;
		}
		if (this.down) {
			if (this.sink != null) {
				// a woodcutter's tree breaks up into his bag
				if (this.tickCount - this.downAt >= LIES) {
					this.breakApart(level, pieces);
				}
				return;
			}
			this.shove(level, pieces);
			this.rest(level, pieces);
			return;
		}
		if (this.flying) {
			this.fly(level, pieces);
			return;
		}
		float lean = this.lean();
		this.speed += NUDGE + WEIGHT * Mth.sin(lean * Mth.DEG_TO_RAD) / this.reach;
		float next = Math.min(FLAT, lean + this.speed);
		// (0.7.586) what's weaker than its wood breaks, and a standing tree in its way goes over with it
		this.smash(level, pieces, next, 0.0F, this.speed * Mth.DEG_TO_RAD);
		this.knock(level, pieces, next);
		boolean blocked = this.strikes(level, pieces, next, 2);
		boolean landed = next >= FLAT || this.tickCount >= LONGEST || blocked;
		float at = blocked ? lean : next;
		this.hit(level, pieces, at, 2, 0.0F, this.speed * Mth.DEG_TO_RAD);
		this.sweep(level, pieces, at);
		if (!landed) {
			this.entityData.set(LEAN, next);
			// the rush of air and creaking as it goes over
			WorldSounds.treeFalling(level, this, lean);
			return;
		}
		this.landTurning = this.speed * Mth.DEG_TO_RAD;
		this.land(level, pieces, at);
		// (0.7.456) caught half standing, it stays leaning there (0.7.588: until what holds it goes, or it's chopped)
		this.propped = at < LYING;
		if (!this.propped) {
			// (0.7.588) lying, its wood rests on the ground, not half sunk in it (it turned about the foot of its cut)
			this.lift = 0.5F * Mth.sin(at * Mth.DEG_TO_RAD);
			this.setPos(this.getX(), this.getY() + this.lift, this.getZ());
		}
		this.comeDown(level);
	}

	/** (0.7.588) It's down: it rests from now on, losing the leaves that snapped off as it struck. */
	private void comeDown(ServerLevel level) {
		this.down = true;
		this.downAt = this.tickCount;
		if (this.sink == null) {
			this.snapLeaves(level);
		}
	}

	/**
	 * (0.7.588) At rest: its leaves drop off a few at a time, and it stays until it's chopped up
	 * ({@link #skipAttackInteraction}). Andy: "unsupported fallen trees should fall, there shouldnt be hanging trees like
	 * this". Whatever its lean, once nothing stops it turning further about its foot it falls on, and once none of its
	 * wood rests on anything (what held it chopped, burned or knocked away, a part broken off it, or it lay flat out over
	 * a drop) it falls free until it strikes something.
	 */
	private void rest(ServerLevel level, List<Piece> pieces) {
		int since = this.tickCount - this.downAt;
		if (since % LEAF_FALL_TICKS == 0) {
			this.shedLeaves(level, pieces);
		}
		if (since % PROP_CHECK != 0) {
			return;
		}
		float lean = this.lean();
		// it looks as far as it would really turn next tick: looking only a degree on found room its first tick didn't
		// have, so a propped tree fell on, landed again where it was with a crash, and did so every second for ever
		float next = Math.min(FLAT, lean + this.firstStep());
		if (lean >= 0.0F && lean < LYING) {
			// propped on a tree (one left standing before trees always went over): that tree goes over now
			this.knock(level, pieces, next);
		}
		if (lean >= 0.0F && lean < FLAT && this.free(level, pieces, next)) {
			this.fallOn();
		} else if (!this.rests(level, pieces)) {
			hurl(this, Vec3.ZERO, lean >= 0.0F && lean < FLAT ? SPLIT_TUMBLE : 0.0F, null);
		}
	}

	/**
	 * Whether it could turn on to {@code next} from where it would start falling: lowered back onto its foot if it was
	 * raised to lie ({@link #fallOn}), as its next tick sees it, or it'd fall on and land again where it lay.
	 */
	private boolean free(ServerLevel level, List<Piece> pieces, float next) {
		if (this.lift == 0.0F) {
			return !this.strikes(level, pieces, next, 2);
		}
		double y = this.getY();
		this.setPos(this.getX(), y - this.lift, this.getZ());
		boolean free = !this.strikes(level, pieces, next, 2);
		this.setPos(this.getX(), y, this.getZ());
		return free;
	}

	/** Whether any of its wood rests on something: a block under a log, or one it lies sunk in. Leaves hold nothing up. */
	private boolean rests(ServerLevel level, List<Piece> pieces) {
		Quaternionf turn = turn(this.lean(), this.heading());
		boolean wood = false;
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			wood = true;
			BlockPos at = this.cell(this.where(p, turn));
			if (holds(level, at) || holds(level, at.below())) {
				return true;
			}
		}
		// (no wood left, nothing of it could strike the ground to land again)
		return !wood;
	}

	/** Whether the block at {@code pos} can hold up a fallen tree's wood: anything solid to stand on but leaves. */
	private static boolean holds(ServerLevel level, BlockPos pos) {
		BlockState there = level.getBlockState(pos);
		return !there.isAir() && !(there.getBlock() instanceof LeavesBlock) && !there.getCollisionShape(level, pos).isEmpty();
	}

	/** (0.7.588) Down no longer: it starts over from where it leans (lowered back onto its foot if it was raised to lie). */
	private void fallOn() {
		this.down = false;
		this.propped = false;
		this.downAt = -1;
		this.speed = START_SPEED;
		if (this.lift != 0.0F) {
			this.setPos(this.getX(), this.getY() - this.lift, this.getZ());
			this.lift = 0.0F;
		}
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
		if (this.flown > FLY_CLEAR) {
			this.smash(level, pieces, next, (float)v.length(), this.tumble * Mth.DEG_TO_RAD);
		}
		if (this.flown > FLY_CLEAR && (this.flown > FLY_LONGEST || this.strikes(level, pieces, next, Integer.MIN_VALUE))) {
			this.flying = false;
			this.hit(level, pieces, lean, Integer.MIN_VALUE, (float)v.length(), this.tumble * Mth.DEG_TO_RAD);
			this.landFlat = (float)v.length();
			this.landTurning = this.tumble * Mth.DEG_TO_RAD;
			this.land(level, pieces, lean);
			this.comeDown(level);
			return;
		}
		this.entityData.set(LEAN, next);
		this.sweep(level, pieces, next);
		if (this.hurled) {
			// (0.7.588) thrown, it strikes whatever it flies through
			this.hit(level, pieces, next, Integer.MIN_VALUE, (float)v.length(), this.tumble * Mth.DEG_TO_RAD);
		}
		WorldSounds.treeFlying(level, this);
	}

	/** Whether this tree has been torn up by a tornado and is still flying, and for how long (ticks). */
	public int flown() {
		return this.flying ? this.flown : -1;
	}

	/**
	 * (0.7.586) Its wood, leaning {@code lean}, smashes through blocks weaker than itself ({@link #weaker}) where it's moving
	 * at least {@link #HIT_SLOWEST} ({@code flat} blocks a tick, plus {@code turning} for every block from where it turns),
	 * dropping what they would: glass, wool, crops, ladders, pumpkins, ice and the like. Planks, stone and earth stop it.
	 */
	private void smash(ServerLevel level, List<Piece> pieces, float lean, float flat, float turning) {
		Quaternionf turn = turn(lean, this.heading());
		int broken = 0;
		for (Piece p : pieces) {
			if (broken >= SMASH_MOST) {
				return;
			}
			if (p.y() < 1 || !p.blockState().is(BlockTags.LOGS) || flat + turning * Math.abs(p.y() + 0.5F) < HIT_SLOWEST) {
				continue;
			}
			BlockPos at = this.cell(this.where(p, turn));
			if (weaker(level, at, level.getBlockState(at), p.blockState()) && level.destroyBlock(at, true, this)) {
				broken++;
			}
		}
	}

	/**
	 * (0.7.586) A block a falling log breaks through: both softer and less sturdy than the {@code wood} itself. Never what
	 * it would simply lie over (grass, snow), the ground (anything dug with a shovel), wood, water, or a block holding
	 * something (chests, signs, beds).
	 */
	static boolean weaker(ServerLevel level, BlockPos pos, BlockState there, BlockState wood) {
		if (there.isAir() || there.canBeReplaced() || there.is(BlockTags.LOGS) || there.hasBlockEntity() || !there.getFluidState().isEmpty()
			|| there.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return false;
		}
		float hardness = there.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness < wood.getDestroySpeed(level, pos)
			&& there.getBlock().getExplosionResistance() < wood.getBlock().getExplosionResistance();
	}

	/**
	 * (0.7.586) Its wood, leaning {@code lean}, strikes standing trees: every one it reaches is knocked over the same way
	 * ({@link Trees#knock}), as fast as this one is turning, so nothing natural holds it up and it goes on to the ground.
	 */
	private void knock(ServerLevel level, List<Piece> pieces, float lean) {
		Quaternionf turn = turn(lean, this.heading());
		for (Piece p : pieces) {
			if (p.y() < 1 || !p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			BlockPos at = this.cell(this.where(p, turn));
			if (!level.getBlockState(at).is(BlockTags.LOGS)) {
				continue;
			}
			if (Trees.knock(level, at, this.heading(), this, Trees.FELL_MOST, this.speed) > 0) {
				level.playSound(null, at, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.4F, 0.5F);
				level.playSound(null, at, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
			}
		}
	}

	/** Whether its wood (from {@code fromY} above its origin up), leaning {@code lean}, runs into anything solid. */
	private boolean strikes(ServerLevel level, List<Piece> pieces, float lean, int fromY) {
		return this.strikes(level, pieces, lean, fromY, this.heading());
	}

	/** As {@link #strikes(ServerLevel, List, float, int)}, falling toward {@code heading} instead. */
	private boolean strikes(ServerLevel level, List<Piece> pieces, float lean, int fromY, float heading) {
		Quaternionf turn = turn(lean, heading);
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

	/**
	 * (0.7.232) Its wood, leaning {@code lean}, breaks through the standing leaves of other trees in its way (a few a
	 * tick), dropping what they would.
	 */
	private void sweep(ServerLevel level, List<Piece> pieces, float lean) {
		Quaternionf turn = turn(lean, this.heading());
		int broken = 0;
		for (Piece p : pieces) {
			if (broken >= SWEEP_MOST) {
				return;
			}
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			BlockPos at = this.cell(this.where(p, turn));
			BlockState there = level.getBlockState(at);
			if (there.getBlock() instanceof LeavesBlock && !there.getValue(LeavesBlock.PERSISTENT) && level.destroyBlock(at, true, this)) {
				broken++;
			}
		}
	}

	/**
	 * Strong characters and giants walking into one of its logs shove it: it slides the way they push and swings about
	 * its middle (pushed at one end, that end goes most), as long as its logs don't run into anything solid. Pushed off
	 * what holds it up, it falls on or drops at its next look ({@link #rest}).
	 */
	private void shove(ServerLevel level, List<Piece> pieces) {
		boolean lying = this.lean() >= LYING && !this.propped;
		Quaternionf turn = turn(this.lean(), this.heading());
		List<Vec3> logs = new ArrayList<>();
		Vec3 middle = Vec3.ZERO;
		BlockState wood = null;
		for (Piece p : pieces) {
			if (p.blockState().is(BlockTags.LOGS)) {
				Vec3 at = this.where(p, turn);
				logs.add(at);
				middle = middle.add(at);
				wood = p.blockState();
			}
		}
		if (logs.isEmpty()) {
			return;
		}
		int n = logs.size();
		middle = middle.scale(1.0 / n);
		double inertia = 0.0;
		for (Vec3 at : logs) {
			inertia += (at.x - middle.x) * (at.x - middle.x) + (at.z - middle.z) * (at.z - middle.z);
		}
		inertia = Math.max(inertia, 1.0);
		double vx = 0.0, vz = 0.0, spin = 0.0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.5),
			e -> e.isAlive() && !e.isSpectator() && !e.noPhysics && !e.isPassenger() && e.onGround() && !e.isSwimming() && !e.isSleeping())) {
			Vec3 way = pushing(e);
			if (way == null) {
				continue;
			}
			// the log it's up against, ahead of it
			AABB reach = e.getBoundingBox().inflate(0.15, 0.0, 0.15);
			Vec3 touched = null;
			double nearest = Double.MAX_VALUE;
			for (Vec3 at : logs) {
				double dx = at.x - e.getX(), dz = at.z - e.getZ();
				// (ahead of them, not one they're only brushing past)
				if (dx * way.x + dz * way.z <= AHEAD * Math.sqrt(dx * dx + dz * dz) || !new AABB(at, at).inflate(0.5).intersects(reach)) {
					continue;
				}
				double d = dx * dx + dz * dz;
				if (d < nearest) {
					nearest = d;
					touched = at;
				}
			}
			if (touched == null) {
				continue;
			}
			// hands on it: everyone strains at it, and only the strong move it
			PUSHERS.put(e, level.getGameTime());
			Vec3 was = e.getAttached(PUSHING);
			if (was == null || was.distanceToSqr(touched) > PUSH_RESYNC * PUSH_RESYNC) {
				e.setAttached(PUSHING, touched);
			}
			int brawn = brawn(e);
			if (!lying || brawn < SHOVE_BRAWN) {
				continue;
			}
			double speed = SHOVE_SPEED * Math.min(1.0, (double)brawn * LOGS_PER_BRAWN / n);
			if (speed < SHOVE_SLOWEST) {
				continue;
			}
			// a push at that log: it moves there at that speed, shared between sliding and swinging as a plank would
			double rx = touched.x - middle.x, rz = touched.z - middle.z;
			double arm = rx * way.z - rz * way.x;
			double impulse = speed / (1.0 / n + arm * arm / inertia);
			vx += way.x * impulse / n;
			vz += way.z * impulse / n;
			spin += arm * impulse / inertia;
		}
		double slide = Math.sqrt(vx * vx + vz * vz);
		if (slide > SHOVE_SPEED) {
			vx *= SHOVE_SPEED / slide;
			vz *= SHOVE_SPEED / slide;
		}
		spin = Mth.clamp(spin, -0.03, 0.03);
		if (slide < 1.0E-4 && Math.abs(spin) < 1.0E-4) {
			return;
		}
		// turned about its middle, and slid: where its foot goes
		double fx = this.getX() - middle.x, fz = this.getZ() - middle.z;
		double cos = Math.cos(spin), sin = Math.sin(spin);
		Vec3 from = this.position();
		float heading = this.heading() + (float)spin;
		// (logs already lying sunk in something may stay so: it only mayn't be pushed further into anything solid)
		int sunk = this.sunk(level, pieces, this.lean(), this.heading());
		this.setPos(middle.x + fx * cos - fz * sin + vx, this.getY(), middle.z + fx * sin + fz * cos + vz);
		if (this.sunk(level, pieces, this.lean(), heading) > sunk) {
			this.setPos(from);
			return;
		}
		this.setAttached(HEADING, heading);
		if (this.tickCount % 6 == 0) {
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.9F, 0.5F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wood), middle.x, middle.y - 0.4, middle.z, 4, 1.0, 0.05, 1.0, 0.02);
		}
	}

	/** How many of its logs, leaning {@code lean} toward {@code heading}, are in something solid. */
	private int sunk(ServerLevel level, List<Piece> pieces, float lean, float heading) {
		Quaternionf turn = turn(lean, heading);
		int sunk = 0;
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			BlockPos at = this.cell(this.where(p, turn));
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced() && !(there.getBlock() instanceof LeavesBlock) && there.isCollisionShapeFullBlock(level, at)) {
				sunk++;
			}
		}
		return sunk;
	}

	/** How strong {@code e} is: Kitchen's hauling brawn for a player; for anything else its Strength and its size. */
	static int brawn(LivingEntity e) {
		if (e instanceof Player player) {
			int brawn = KitchenCompat.brawn(player);
			if (brawn >= 0) {
				return brawn;
			}
		}
		int brawn = Math.max(0, (int)((e.getBbHeight() / MAN_HEIGHT - 1.0F) / HEIGHT_PER_BRAWN));
		MobEffectInstance strength = e.getEffect(MobEffects.STRENGTH);
		return strength == null ? brawn : brawn + strength.getAmplifier() + 1;
	}

	/** The way (level, of length 1) {@code e} is trying to go: a player's keys as they face, a mob's walk; null standing. */
	private static @Nullable Vec3 pushing(LivingEntity e) {
		double x, z;
		if (e instanceof ServerPlayer player) {
			Input in = player.getLastClientInput();
			float f = (in.forward() ? 1.0F : 0.0F) - (in.backward() ? 1.0F : 0.0F), side = (in.left() ? 1.0F : 0.0F) - (in.right() ? 1.0F : 0.0F);
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			x = side * Mth.cos(yaw) - f * Mth.sin(yaw);
			z = f * Mth.cos(yaw) + side * Mth.sin(yaw);
		} else if (e instanceof Mob mob && mob.getMoveControl().hasWanted()) {
			x = mob.getMoveControl().getWantedX() - mob.getX();
			z = mob.getMoveControl().getWantedZ() - mob.getZ();
		} else {
			return null;
		}
		double d = Math.sqrt(x * x + z * z);
		return d < 1.0E-3 ? null : new Vec3(x / d, 0.0, z / d);
	}

	/** Whether its logs are solid now (it lies still). */
	public boolean solid() {
		return this.entityData.get(SOLID);
	}

	/**
	 * The shapes {@code entity}, moving through {@code area}, runs into: {@code others}, and the logs of every fallen
	 * tree lying there (a block round each log's middle, as it's drawn). Something already inside a log (the tree came
	 * down on it) isn't held or pushed by it, only kept from going further in, so nobody is trapped. A mob that walks into
	 * a log it can clear ({@link #HOPS}) hops it, since its path finding sees only blocks.
	 */
	public static List<VoxelShape> collisions(Entity entity, AABB area, List<VoxelShape> others) {
		if (entity instanceof FallingTree || entity.noPhysics || entity.isSpectator()) {
			return others;
		}
		List<FallingTree> trees = entity.level().getEntitiesOfClass(FallingTree.class, area, FallingTree::solid);
		if (trees.isEmpty()) {
			return others;
		}
		List<VoxelShape> out = null;
		boolean hop = false;
		double feet = entity.getY();
		for (FallingTree tree : trees) {
			Quaternionf turn = turn(tree.lean(), tree.heading());
			for (Piece p : tree.pieces()) {
				if (!p.blockState().is(BlockTags.LOGS)) {
					continue;
				}
				Vec3 at = tree.where(p, turn);
				AABB box = new AABB(at, at).inflate(0.5);
				if (!box.intersects(area)) {
					continue;
				}
				if (out == null) {
					out = new ArrayList<>(others);
				}
				out.add(Shapes.create(box));
				hop |= box.maxY > feet + entity.maxUpStep() + 1.0E-3 && box.maxY <= feet + HOPS && box.minY < feet + entity.getBbHeight();
			}
		}
		if (hop && entity instanceof Mob mob && !mob.level().isClientSide() && (mob.onGround() || mob.isInWater())) {
			mob.getJumpControl().jump();
		}
		return out == null ? others : out;
	}

	/** It comes down: a thud and a cloud of leaves. */
	private void land(ServerLevel level, List<Piece> pieces, float lean) {
		this.entityData.set(LEAN, lean);
		Quaternionf turn = turn(lean, this.heading());
		Vec3 middle = Vec3.ZERO;
		int count = 0;
		List<Vec3> wood = new ArrayList<>();
		BlockState leaf = null;
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				if (leaf == null && p.blockState().getBlock() instanceof LeavesBlock) {
					leaf = p.blockState();
				}
				continue;
			}
			Vec3 at = this.where(p, turn);
			wood.add(at);
			middle = middle.add(at);
			count++;
		}
		// the ground answers: dust and leaves, the thump, flattened grass and snow, the animals bolting
		Felling.landed(level, wood, leaf);
		if (count > 0) {
			middle = middle.scale(1.0 / count);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 0.5F);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4F, 1.7F);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, middle.x, middle.y, middle.z, 6, 1.5, 0.2, 1.5, 0.01);
		}
		this.splash(level, pieces, turn);
	}

	/** (Hardrock picks) Logs most that strike water within this many blocks make a splash this wide, at the most. */
	private static final double SPLASH_REACH = 3.0, SPLASH_MOST = 7.0, SPLASH_PUSH = 0.6, SPLASH_LIFT = 0.22;

	/**
	 * (Hardrock picks) Andy: a felled trunk that lands in water splashes. The logs that came down in water (in the block,
	 * or the one just under it) throw up a spray along their length, and the wave they make, wider for a longer trunk, shoves
	 * swimmers, boats and floating things away from it and rocks them.
	 */
	private void splash(ServerLevel level, List<Piece> pieces, Quaternionf turn) {
		List<Vec3> wet = new ArrayList<>();
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			Vec3 at = this.where(p, turn);
			BlockPos pos = BlockPos.containing(at);
			if (level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
				wet.add(at);
			}
		}
		if (wet.isEmpty()) {
			return;
		}
		Vec3 centre = Vec3.ZERO;
		for (Vec3 at : wet) {
			centre = centre.add(at);
			level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.4, at.z, 10, 0.5, 0.2, 0.5, 0.25);
			level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.2, at.z, 3, 0.5, 0.1, 0.5, 0.05);
		}
		centre = centre.scale(1.0 / wet.size());
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 2.0F, 0.5F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 1.0F, 0.6F);
		double reach = Math.min(SPLASH_MOST, SPLASH_REACH + wet.size() * 0.4);
		AABB box = new AABB(centre, centre).inflate(reach, 2.5, reach);
		for (Entity e : level.getEntities(this, box, x -> x.isInWater() || x instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat
			|| x instanceof net.minecraft.world.entity.item.ItemEntity)) {
			double dx = e.getX() - centre.x, dz = e.getZ() - centre.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > reach || d < 1.0E-3) {
				continue;
			}
			double f = (1.0 - d / reach) * SPLASH_PUSH;
			e.push(dx / d * f, SPLASH_LIFT * (1.0 - d / reach), dz / d * f);
			e.needsSync = true;
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

	/** (0.7.232) Whether this leaf snaps off as the tree lands: likelier the faster it struck the ground. */
	private boolean snaps(ServerLevel level, Piece p) {
		float speed = this.landFlat + this.landTurning * Math.abs(p.y() + 0.5F);
		return level.getRandom().nextFloat() < LEAF_SNAP * Math.min(1.0F, speed / LEAF_SNAP_FULL);
	}

	/**
	 * The block a piece's middle is in. Lying, a tree's pieces sit on block edges (it turns about the foot of its cut),
	 * so they're nudged up and back toward the cut: its wood lies on the ground, from where it stood.
	 */
	private BlockPos cell(Vec3 at) {
		float heading = this.heading();
		return BlockPos.containing(at.x - 0.01 * Mth.cos(heading), at.y + 0.01, at.z - 0.01 * Mth.sin(heading));
	}

	// ---- (0.7.588) giants' hands (Powers' GiantTrees calls these by name) ----------------------------------------------

	/** Whether a giant's hand at {@code at} could close on a tree within {@code reach}: a fallen one, or one standing. */
	public static boolean treeNear(ServerLevel level, Vec3 at, double reach, int mostLogs) {
		return fallenNear(level, at, reach, mostLogs) != null || FelledCompat.near(level, at, reach, mostLogs) != null
			|| standingNear(level, at, reach) != null;
	}

	/**
	 * A giant's hand at {@code at} closes on the nearest tree within {@code reach} of no more than {@code mostLogs} logs:
	 * a fallen (or falling) one, else a standing one torn out whole. It's held now ({@link #hold}); null if there's none.
	 */
	public static @Nullable Entity take(ServerLevel level, Vec3 at, double reach, Entity giant, int mostLogs) {
		FallingTree tree = fallenNear(level, at, reach, mostLogs);
		if (tree == null) {
			// (0.7.628) one Felled brought down: it becomes ours in the giant's hand
			Entity felled = FelledCompat.near(level, at, reach, mostLogs);
			tree = felled == null ? null : FelledCompat.adopt(level, felled);
		}
		if (tree == null) {
			BlockPos log = standingNear(level, at, reach);
			tree = log == null ? null : Trees.pluck(level, log, mostLogs);
		}
		if (tree == null) {
			return null;
		}
		tree.held = true;
		tree.heldAt = tree.tickCount;
		tree.down = false;
		tree.propped = false;
		tree.flying = false;
		tree.hurled = false;
		tree.cutter = giant;
		tree.struck.add(giant.getUUID());
		level.playSound(null, tree.getX(), tree.getY(), tree.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
		level.playSound(null, tree.getX(), tree.getY(), tree.getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		return tree;
	}

	/**
	 * (0.7.628) A tree of these {@code pieces} (upright, as it stood, {@code x}/{@code z} from its trunk and {@code y} from its
	 * middle) made ours at {@code at}, heading {@code heading}: for a tree another mod brought down ({@link FelledCompat}).
	 */
	static @Nullable FallingTree adopt(ServerLevel level, Vec3 at, List<Piece> pieces, float heading) {
		if (pieces.isEmpty()) {
			return null;
		}
		FallingTree tree = new FallingTree(TYPE, level);
		tree.setPos(at);
		tree.setAttached(PIECES, List.copyOf(pieces));
		tree.setAttached(HEADING, heading);
		tree.flying = true;
		level.addFreshEntity(tree);
		return tree;
	}

	/** Held at {@code at} (where it turns), lying {@link #HELD_LEAN} over the way {@code yaw} faces (the game's yaw, degrees). */
	public static void hold(Entity entity, Vec3 at, float yaw) {
		if (!(entity instanceof FallingTree tree)) {
			return;
		}
		tree.held = true;
		tree.heldAt = tree.tickCount;
		tree.setPos(at.x, at.y, at.z);
		tree.setDeltaMovement(Vec3.ZERO);
		float r = yaw * Mth.DEG_TO_RAD;
		tree.setAttached(HEADING, (float)Math.atan2(Mth.cos(r), -Mth.sin(r)));
		tree.entityData.set(LEAN, HELD_LEAN);
	}

	/** Thrown at {@code velocity} (blocks a tick), turning {@code tumble} degrees a tick: it flies, strikes and lands. */
	public static void hurl(Entity entity, Vec3 velocity, float tumble, @Nullable Entity thrower) {
		if (!(entity instanceof FallingTree tree)) {
			return;
		}
		tree.held = false;
		tree.down = false;
		tree.propped = false;
		tree.lift = 0.0F;
		tree.flying = true;
		tree.hurled = velocity.lengthSqr() > 1.0E-4;
		tree.flown = FLY_CLEAR + 1;
		tree.tumble = tumble;
		if (thrower != null) {
			tree.struck.add(thrower.getUUID());
		}
		tree.setDeltaMovement(velocity);
	}

	/** How many logs it has. */
	public static int logs(Entity entity) {
		int n = 0;
		if (entity instanceof FallingTree tree) {
			for (Piece p : tree.pieces()) {
				if (p.blockState().is(BlockTags.LOGS)) {
					n++;
				}
			}
		}
		return n;
	}

	private static @Nullable FallingTree fallenNear(ServerLevel level, Vec3 at, double reach, int mostLogs) {
		FallingTree best = null;
		double bestD = Double.MAX_VALUE;
		for (FallingTree t : level.getEntitiesOfClass(FallingTree.class, new AABB(at, at).inflate(reach),
			t -> t.isAlive() && !t.held && t.sink == null && logs(t) <= mostLogs)) {
			double d = t.getBoundingBox().distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	private static @Nullable BlockPos standingNear(ServerLevel level, Vec3 at, double reach) {
		BlockPos c = BlockPos.containing(at);
		int r = Mth.ceil(reach);
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			BlockState s = level.getBlockState(p);
			if (!s.is(BlockTags.LOGS) || !s.hasProperty(BlockStateProperties.AXIS) || s.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
				continue;
			}
			double d = p.distToCenterSqr(at);
			if (d <= reach * reach && d < bestD && Trees.natural(level, p)) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	// ---- (0.7.588) at rest: chopped up, split, its leaves dropping -------------------------------------------------------

	/** Its leaves that snapped off as it struck the ground fall now, likelier the harder they struck ({@link #snaps}). */
	private void snapLeaves(ServerLevel level) {
		List<Piece> pieces = this.pieces();
		List<Piece> kept = new ArrayList<>(pieces.size());
		Quaternionf turn = turn(this.lean(), this.heading());
		int leafCount = 0;
		for (Piece p : pieces) {
			if (p.blockState().getBlock() instanceof LeavesBlock && this.snaps(level, p)) {
				this.drop(level, p, turn, null, ItemStack.EMPTY, leafCount++ % LEAF_SOUNDS == 0);
			} else {
				kept.add(p);
			}
		}
		if (kept.size() != pieces.size()) {
			this.setAttached(PIECES, List.copyOf(kept));
		}
	}

	/** A few of its leaves drop off, giving what they would (saplings, sticks, apples). */
	private void shedLeaves(ServerLevel level, List<Piece> pieces) {
		List<Integer> leaves = new ArrayList<>();
		for (int i = 0; i < pieces.size(); i++) {
			if (!pieces.get(i).blockState().is(BlockTags.LOGS)) {
				leaves.add(i);
			}
		}
		if (leaves.isEmpty()) {
			return;
		}
		Quaternionf turn = turn(this.lean(), this.heading());
		Set<Integer> gone = new HashSet<>();
		for (int n = 0; n < LEAF_FALL && !leaves.isEmpty(); n++) {
			int i = leaves.remove(level.getRandom().nextInt(leaves.size()));
			gone.add(i);
			this.drop(level, pieces.get(i), turn, null, ItemStack.EMPTY, n == 0);
		}
		List<Piece> kept = new ArrayList<>(pieces.size());
		for (int i = 0; i < pieces.size(); i++) {
			if (!gone.contains(i)) {
				kept.add(pieces.get(i));
			}
		}
		this.setAttached(PIECES, List.copyOf(kept));
	}

	/** {@code p} breaks off: what it gives, where it is, with a puff (and its breaking sound, if {@code loud}). */
	private void drop(ServerLevel level, Piece p, Quaternionf turn, @Nullable Entity by, ItemStack tool, boolean loud) {
		BlockState state = p.blockState();
		Vec3 at = this.where(p, turn);
		BlockPos pos = BlockPos.containing(at);
		for (ItemStack drop : Block.getDrops(state, level, pos, null, by, tool)) {
			Block.popResource(level, pos, drop);
		}
		if (loud) {
			level.levelEvent(2001, pos, Block.getId(state));
		} else {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.05);
		}
	}

	/**
	 * Struck by a player (the attack goes no further): down, the piece they're aiming at takes a stroke. A log takes
	 * {@link #LOG_CHOPS} from an axe (none from anything else), a leaf one; broken out, it gives what it would and the
	 * tree comes apart where it's broken through ({@link #split}).
	 */
	@Override
	public boolean skipAttackInteraction(Entity attacker) {
		if (this.level() instanceof ServerLevel level && attacker instanceof Player player && this.down && this.sink == null && this.isAlive()) {
			this.chop(level, player);
		}
		return true;
	}

	private void chop(ServerLevel level, Player player) {
		List<Piece> pieces = this.pieces();
		Quaternionf turn = turn(this.lean(), this.heading());
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.entityInteractionRange() + 2.0));
		Piece aimed = null;
		double best = Double.MAX_VALUE;
		for (Piece p : pieces) {
			Vec3 at = this.where(p, turn);
			Optional<Vec3> hit = new AABB(at, at).inflate(0.5).clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
				best = hit.get().distanceToSqr(eye);
				aimed = p;
			}
		}
		if (aimed == null) {
			return;
		}
		BlockState state = aimed.blockState();
		ItemStack tool = player.getMainHandItem();
		Vec3 at = this.where(aimed, turn);
		if (state.is(BlockTags.LOGS) && !player.isCreative()) {
			if (Trees.needsAxe(player, state)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.needs_axe").withStyle(ChatFormatting.GRAY));
				return;
			}
			int strokes = this.chops.merge(aimed, 1, Integer::sum);
			if (tool.isDamageableItem()) {
				tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			}
			player.causeFoodExhaustion(0.005F);
			if (strokes < LOG_CHOPS) {
				level.playSound(null, at.x, at.y, at.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.1);
				return;
			}
		}
		this.chops.remove(aimed);
		if (player.isCreative()) {
			level.levelEvent(2001, BlockPos.containing(at), Block.getId(state));
		} else {
			this.drop(level, aimed, turn, player, tool, true);
		}
		List<Piece> rest = new ArrayList<>(pieces);
		rest.remove(aimed);
		this.split(level, rest, turn);
	}

	/**
	 * What's left of it once a piece is broken out: its wood in parts that still hold together (logs touching, corners
	 * too), each leaf with the nearest part that holds it ({@link #LEAF_HOLD}), others falling off. One part, it's simply
	 * smaller. Broken through, each part goes its own way: lying, they lie where they are; still leaning, the part on its
	 * stump leans on (or falls the rest of the way, now it's lighter) and the rest falls free.
	 */
	private void split(ServerLevel level, List<Piece> rest, Quaternionf turn) {
		List<Piece> logs = new ArrayList<>();
		List<Piece> leaves = new ArrayList<>();
		for (Piece p : rest) {
			(p.blockState().is(BlockTags.LOGS) ? logs : leaves).add(p);
		}
		if (logs.isEmpty()) {
			for (Piece p : leaves) {
				this.drop(level, p, turn, null, ItemStack.EMPTY, false);
			}
			this.discard();
			return;
		}
		List<List<Piece>> parts = parts(logs);
		for (Piece leaf : leaves) {
			int holder = -1, nearest = LEAF_HOLD + 1;
			for (int i = 0; i < parts.size(); i++) {
				for (Piece log : parts.get(i)) {
					int d = Math.max(Math.abs(leaf.x() - log.x()), Math.max(Math.abs(leaf.y() - log.y()), Math.abs(leaf.z() - log.z())));
					if (d < nearest) {
						nearest = d;
						holder = i;
					}
				}
			}
			if (holder < 0) {
				this.drop(level, leaf, turn, null, ItemStack.EMPTY, false);
			} else {
				parts.get(holder).add(leaf);
			}
		}
		boolean leaning = this.lean() < LYING;
		// (the part still on its stump: a log at the foot of the cut)
		int footed = -1;
		for (int i = 0; i < parts.size() && footed < 0; i++) {
			for (Piece p : parts.get(i)) {
				if (p.blockState().is(BlockTags.LOGS) && Math.abs(p.x()) <= 1 && Math.abs(p.z()) <= 1 && p.y() <= 1 && p.y() >= 0) {
					footed = i;
					break;
				}
			}
		}
		int keep = footed >= 0 ? footed : 0;
		for (int i = 0; i < parts.size(); i++) {
			if (i != keep) {
				this.spawnPart(level, parts.get(i), turn, leaning);
			}
		}
		this.setAttached(PIECES, List.copyOf(parts.get(keep)));
		if (leaning) {
			if (footed >= 0) {
				// lighter now: it may fall on, or lean where it was
				this.fallOn();
			} else {
				this.loose(parts.get(keep), turn);
			}
		}
	}

	/** Its logs in parts that hold together (touching, corners too). */
	private static List<List<Piece>> parts(List<Piece> logs) {
		List<List<Piece>> parts = new ArrayList<>();
		Set<Piece> left = new HashSet<>(logs);
		for (Piece start : logs) {
			if (!left.remove(start)) {
				continue;
			}
			List<Piece> part = new ArrayList<>();
			Deque<Piece> open = new ArrayDeque<>();
			open.add(start);
			while (!open.isEmpty()) {
				Piece at = open.poll();
				part.add(at);
				for (Iterator<Piece> it = left.iterator(); it.hasNext();) {
					Piece q = it.next();
					if (Math.abs(q.x() - at.x()) <= 1 && Math.abs(q.y() - at.y()) <= 1 && Math.abs(q.z() - at.z()) <= 1) {
						it.remove();
						open.add(q);
					}
				}
			}
			parts.add(part);
		}
		return parts;
	}

	/** A part broken off this tree, as a tree of its own where it is: falling free if {@code free}, else lying there. */
	private void spawnPart(ServerLevel level, List<Piece> part, Quaternionf turn, boolean free) {
		FallingTree tree = new FallingTree(TYPE, level);
		Piece anchor = lowest(part);
		Vector3f off = turn.transform(new Vector3f(anchor.x(), anchor.y(), anchor.z()));
		tree.setPos(this.getX() + off.x, this.getY() + off.y, this.getZ() + off.z);
		tree.setAttached(PIECES, rebase(part, anchor));
		tree.setAttached(HEADING, this.heading());
		tree.entityData.set(LEAN, this.lean());
		tree.leanO = this.lean();
		tree.reach = this.reach;
		if (free) {
			tree.flying = true;
			tree.flown = FLY_CLEAR + 1;
			tree.tumble = SPLIT_TUMBLE;
		} else {
			tree.down = true;
			tree.downAt = -1;
		}
		level.addFreshEntity(tree);
	}

	/** This tree, off its stump while still leaning: it falls free from where it is. */
	private void loose(List<Piece> part, Quaternionf turn) {
		Piece anchor = lowest(part);
		Vector3f off = turn.transform(new Vector3f(anchor.x(), anchor.y(), anchor.z()));
		this.setPos(this.getX() + off.x, this.getY() + off.y, this.getZ() + off.z);
		this.setAttached(PIECES, rebase(part, anchor));
		this.down = false;
		this.propped = false;
		this.flying = true;
		this.flown = FLY_CLEAR + 1;
		this.tumble = SPLIT_TUMBLE;
		this.setDeltaMovement(Vec3.ZERO);
	}

	/** Its lowest log (else its lowest piece). */
	private static Piece lowest(List<Piece> part) {
		Piece best = null;
		for (Piece p : part) {
			boolean log = p.blockState().is(BlockTags.LOGS);
			if (best == null || log && !best.blockState().is(BlockTags.LOGS) || log == best.blockState().is(BlockTags.LOGS) && p.y() < best.y()) {
				best = p;
			}
		}
		return best;
	}

	/** {@code part}'s pieces placed from {@code anchor} instead. */
	private static List<Piece> rebase(List<Piece> part, Piece anchor) {
		List<Piece> moved = new ArrayList<>(part.size());
		for (Piece p : part) {
			moved.add(new Piece(p.state(), p.x() - anchor.x(), p.y() - anchor.y(), p.z() - anchor.z()));
		}
		return List.copyOf(moved);
	}

	/** Its box (what you aim at and strike): round its wood, as it leans now. */
	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		List<Piece> pieces = this.pieces();
		if (pieces.isEmpty()) {
			return super.makeBoundingBox(pos);
		}
		Quaternionf turn = turn(this.lean(), this.heading());
		double x0 = pos.x, y0 = pos.y, z0 = pos.z, x1 = pos.x, y1 = pos.y, z1 = pos.z;
		boolean any = false;
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			Vector3f v = turn.transform(new Vector3f(p.x(), p.y() + 0.5F, p.z()));
			double x = pos.x + v.x, y = pos.y + v.y, z = pos.z + v.z;
			if (!any) {
				x0 = x1 = x;
				y0 = y1 = y;
				z0 = z1 = z;
				any = true;
			}
			x0 = Math.min(x0, x);
			y0 = Math.min(y0, y);
			z0 = Math.min(z0, z);
			x1 = Math.max(x1, x);
			y1 = Math.max(y1, y);
			z1 = Math.max(z1, z);
		}
		return any ? new AABB(x0 - 0.5, y0 - 0.5, z0 - 0.5, x1 + 0.5, y1 + 0.5, z1 + 0.5) : super.makeBoundingBox(pos);
	}

	/** Its box worked out again when its lean, heading or pieces change (on every screen). */
	private void fitBox() {
		List<Piece> pieces = this.pieces();
		float lean = this.lean(), heading = this.heading();
		if (pieces != this.boxPieces || lean != this.boxLean || heading != this.boxHeading) {
			this.boxPieces = pieces;
			this.boxLean = lean;
			this.boxHeading = heading;
			this.setBoundingBox(this.makeBoundingBox(this.position()));
		}
	}

	@Override
	public boolean shouldBeSaved() {
		// (a woodcutter's tree goes into his bag, not the world)
		return this.sink == null;
	}

	@Override
	public boolean isPickable() {
		return this.isAlive();
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(LEAN, 0.0F);
		entityData.define(SOLID, false);
	}

	/** (0.7.588) Kept with the world: its pieces (by block, not number), lean, heading, and whether it's down or flying. */
	static final Codec<Piece> PIECE_CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockState.CODEC.fieldOf("state").forGetter(Piece::blockState),
		Codec.INT.fieldOf("x").forGetter(Piece::x),
		Codec.INT.fieldOf("y").forGetter(Piece::y),
		Codec.INT.fieldOf("z").forGetter(Piece::z)
	).apply(i, (state, x, y, z) -> new Piece(Block.getId(state), x, y, z)));

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("pieces", PIECE_CODEC.listOf()).ifPresent(list -> this.setAttached(PIECES, List.copyOf(list)));
		this.setAttached(HEADING, input.getFloatOr("heading", 0.0F));
		this.entityData.set(LEAN, input.getFloatOr("lean", 0.0F));
		this.leanO = this.lean();
		this.down = input.getBooleanOr("down", false);
		this.downAt = -1;
		this.propped = input.getBooleanOr("propped", false);
		this.lift = input.getFloatOr("lift", 0.0F);
		this.flying = input.getBooleanOr("flying", false);
		this.flown = input.getIntOr("flown", 0);
		this.tumble = input.getFloatOr("tumble", 0.0F);
		this.reach = input.getFloatOr("reach", 2.0F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("pieces", PIECE_CODEC.listOf(), this.pieces());
		output.putFloat("heading", this.heading());
		output.putFloat("lean", this.lean());
		output.putBoolean("down", this.down);
		output.putBoolean("propped", this.propped);
		output.putFloat("lift", this.lift);
		output.putBoolean("flying", this.flying || this.held);
		output.putInt("flown", this.flown);
		output.putFloat("tumble", this.tumble);
		output.putFloat("reach", this.reach);
	}
}
