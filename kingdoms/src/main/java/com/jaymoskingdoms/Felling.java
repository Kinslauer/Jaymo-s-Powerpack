package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Felling a tree the way a woodsman does (Andy's Timber Tales picks; our own code, nothing of that mod's).
 * <ul>
 * <li>The axe cuts the trunk where it lands, round it log by log ({@link #ring}); a tree comes down only once nothing
 * joins its top to the ground ({@link #joined}), and leaves a carved stump ({@link TreeStump}).</li>
 * <li>No bar on the screen: cracks spread up the trunk as it's cut and it creaks and groans louder as it nears
 * breaking ({@link #chopped}).</li>
 * <li>The side the first strokes came from is the notch: finished from the back, it falls toward the notch, unless it
 * leans badly the other way ({@link #way}).</li>
 * <li>Where it lands the ground answers: dust and leaves fly, the ground thumps underfoot ({@link Thump}), grass and snow
 * flatten under it, birds burst out of its crown as it goes, and the animals near bolt ({@link #landed}).</li>
 * <li>A tree left half cut is a danger: walk where it would fall, or let a storm at it, and it may give way
 * ({@link #tick}).</li>
 * </ul>
 */
public final class Felling {
	/** How far (blocks, each way) round the struck log the trunk is looked through at that height. */
	static final int RING_REACH = 2;
	/** Leaning this far (blocks of its weight off the trunk) the other way from its notch, it goes that way anyway. */
	static final double LEAN_BADLY = 1.2;
	/** How many logs up the trunk its cracks spread, and how much fainter each one up. */
	static final int CRACK_UP = 3, CRACK_FADE = 3;
	/** How far through (0 to 1) it creaks, and groans. */
	static final float CREAK_FROM = 0.4F, GROAN_FROM = 0.7F;
	/** Its creak's loudness at first and at the last stroke. */
	static final float CREAK_QUIET = 0.3F, CREAK_LOUD = 1.5F;

	/** Where it lands: how far off the ground's thump is felt, and the most it shakes the view (degrees) close by. */
	static final double THUMP_REACH = 14.0;
	static final float THUMP_MOST = 1.1F;
	/** A tree this many logs or more thumps at full strength. */
	static final int THUMP_FULL_LOGS = 10;
	/** How far off animals bolt from where it lands, how far they run, and how fast. */
	static final double BOLT_REACH = 16.0, BOLT_SPEED = 1.5;
	static final int BOLT_RUN = 14;
	/** Birds in its crown: the chance a crown has any, one per this many leaves (at most {@link #BIRDS_MOST}). */
	static final float BIRD_CHANCE = 0.6F;
	static final int BIRD_LEAVES = 30, BIRDS_MOST = 3;
	/** No more birds are put up where this many are near already (within {@link #BIRD_REACH}). */
	static final int BIRDS_NEAR_MOST = 6;
	static final double BIRD_REACH = 24.0;
	/** Naturalist's songbird, put up out of a falling crown when it's in the pack. */
	static final Identifier BIRD = Identifier.fromNamespaceAndPath("naturalist", "bird");

	/** A half-cut tree: this far through, it's a danger once nobody has cut at it for this long (ticks). */
	static final float HAZARD_FROM = 0.5F;
	static final int HAZARD_QUIET = 200, HAZARD_CHECK = 20;
	/** The chance a check gives way under someone in its path (by how far through it is), and in a thunderstorm. */
	static final float HAZARD_CHANCE = 0.15F, STORM_CHANCE = 0.03F;
	/** Cuts this close (blocks) together are in the same tree. */
	static final int SAME_TREE = 4;
	/** How wide (blocks, each side) the path it would fall along is. */
	static final double HAZARD_SIDE = 1.5;

	/** A half-cut tree being watched: where its cut is, and when it was last struck. */
	private record Weak(ServerLevel level, BlockPos cut) {
	}

	private static final Map<Weak, Long> WEAK = new HashMap<>();
	private static int clock;

	private Felling() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Thump.TYPE, Thump.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Felling::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WEAK.clear());
	}

	// ---- cutting through ----------------------------------------------------------------------------------------------

	/**
	 * The trunk at {@code cut}'s height: the upright logs there joined to it side by side (within {@link #RING_REACH}),
	 * each with wood of the tree standing on it. {@code cut} first.
	 */
	static List<BlockPos> ring(ServerLevel level, BlockPos cut) {
		List<BlockPos> ring = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		open.add(cut);
		seen.add(cut);
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			ring.add(at);
			// (side by side only: a tree touching this one only at a corner is a tree of its own)
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = at.relative(d);
				if (Math.abs(n.getX() - cut.getX()) > RING_REACH || Math.abs(n.getZ() - cut.getZ()) > RING_REACH || !seen.add(n)) {
					continue;
				}
				if (upright(level.getBlockState(n)) && level.getBlockState(n.above()).is(BlockTags.LOGS)) {
					open.add(n);
				}
			}
		}
		return ring;
	}

	private static boolean upright(BlockState state) {
		return state.is(BlockTags.LOGS)
			&& (!state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y);
	}

	/** Strokes one log of a trunk {@code around} logs round takes to be cut through: its share of the tree's {@link Trees#needed}. */
	static int through(int logs, int around) {
		return Math.max(1, Math.min(ChoppedLog.MOST, Mth.ceil(Trees.needed(logs) / (float)Math.max(1, around))));
	}

	/** The log of {@code ring} not yet cut through nearest {@code cut}, or null if all are. */
	static @Nullable BlockPos nearestUncut(ServerLevel level, List<BlockPos> ring, BlockPos cut) {
		BlockPos best = null;
		for (BlockPos p : ring) {
			if (!ChoppedLog.through(level.getBlockState(p)) && (best == null || p.distSqr(cut) < best.distSqr(cut))) {
				best = p;
			}
		}
		return best;
	}

	/** How far through (0 to 1) the trunk is cut at {@code ring}, each log taking {@code per} strokes. */
	static float progress(ServerLevel level, List<BlockPos> ring, int per) {
		int done = 0;
		for (BlockPos p : ring) {
			BlockState state = level.getBlockState(p);
			done += ChoppedLog.through(state) ? per : Math.min(per, ChoppedLog.chops(state));
		}
		return ring.isEmpty() ? 0.0F : Math.min(1.0F, done / (float)(per * ring.size()));
	}

	/**
	 * Whether wood still joins the tree above the cut at {@code cut}'s height (its trunk there {@code ring}) to the ground:
	 * from the logs over the cut, through wood not cut through (corners too, not far off), to a log at or below that
	 * height. The trunk of another tree standing beside it isn't its wood, even where the two touch. A tree too big to look
	 * through is taken as held.
	 */
	static boolean joined(ServerLevel level, BlockPos cut, List<BlockPos> ring) {
		Set<Long> columns = new HashSet<>();
		for (BlockPos r : ring) {
			columns.add(BlockPos.asLong(r.getX(), 0, r.getZ()));
		}
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		for (BlockPos n : BlockPos.betweenClosed(cut.offset(-1, 1, -1), cut.offset(1, 1, 1))) {
			BlockPos q = n.immutable();
			if (whole(level, q) && !otherTrunk(level, q, cut, columns)) {
				seen.add(q);
				open.add(q);
			}
		}
		while (!open.isEmpty()) {
			if (seen.size() >= Trees.FELL_MOST) {
				return true;
			}
			BlockPos at = open.poll();
			if (at.getY() <= cut.getY()) {
				return true;
			}
			for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				if (Math.abs(n.getX() - cut.getX()) > Trees.FELL_SPREAD || Math.abs(n.getZ() - cut.getZ()) > Trees.FELL_SPREAD) {
					continue;
				}
				BlockPos q = n.immutable();
				if (!seen.contains(q) && whole(level, q) && !otherTrunk(level, q, cut, columns)) {
					seen.add(q);
					open.add(q);
				}
			}
		}
		return false;
	}

	/**
	 * Whether {@code q} is in another tree's trunk: off this trunk's {@code columns}, standing on an unbroken run of logs
	 * straight down to the cut's height.
	 */
	private static boolean otherTrunk(ServerLevel level, BlockPos q, BlockPos cut, Set<Long> columns) {
		if (columns.contains(BlockPos.asLong(q.getX(), 0, q.getZ()))) {
			return false;
		}
		for (int y = q.getY(); y >= cut.getY(); y--) {
			if (!upright(level.getBlockState(new BlockPos(q.getX(), y, q.getZ())))) {
				return false;
			}
		}
		return true;
	}

	/** Wood that holds: a log, or a chopped one not yet cut through. */
	private static boolean whole(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.LOGS) && !ChoppedLog.through(state);
	}

	/**
	 * Which way the tree cut at {@code ring} falls (radians, as {@link FallingTree#HEADING}), {@code logs} and
	 * {@code leaves} being what stands on the cut: toward the notch (the side its first strokes came in from) when the
	 * last stroke came from the opposite side, unless its weight leans {@link #LEAN_BADLY} the other way. Null when it
	 * wasn't cut that way: it falls as it always has (its heavier side, else away from the cutter).
	 */
	static @Nullable Float way(ServerLevel level, BlockPos cut, List<BlockPos> ring, List<BlockPos> logs, List<BlockPos> leaves) {
		Direction notch = null, last = null;
		long first = Long.MAX_VALUE, latest = Long.MIN_VALUE;
		for (BlockPos p : ring) {
			if (!(level.getBlockEntity(p) instanceof ChoppedLog.Entity e)) {
				continue;
			}
			if (e.notch != null && e.firstAt < first) {
				first = e.firstAt;
				notch = e.notch;
			}
			if (e.last != null && e.lastAt > latest) {
				latest = e.lastAt;
				last = e.last;
			}
		}
		if (notch == null || last != notch.getOpposite()) {
			return null;
		}
		double[] lean = FallingTree.offset(level, cut, logs, leaves);
		double along = lean[0] * notch.getStepX() + lean[1] * notch.getStepZ();
		if (along <= -LEAN_BADLY) {
			return FallingTree.heavier(level, cut, logs, leaves, null);
		}
		return heading(notch);
	}

	/** The heading (radians, as {@link FallingTree#HEADING}) toward {@code side}. */
	static float heading(Direction side) {
		return (float)Math.atan2(side.getStepZ(), side.getStepX());
	}

	/** The trunk's cut logs at {@code ring} are stumps now ({@link TreeStump}): what stood on them is loose. */
	static void stumps(ServerLevel level, List<BlockPos> ring) {
		for (BlockPos p : ring) {
			if (level.getBlockState(p).is(BlockTags.LOGS)) {
				TreeStump.cutDown(level, p);
			}
		}
	}

	// ---- cracks and creaks --------------------------------------------------------------------------------------------

	/**
	 * A stroke went into the trunk at {@code ring} (each log taking {@code per}, of a tree of {@code logs} logs): the cracks
	 * spread up it as far as it's cut through, and past {@link #CREAK_FROM} it creaks (and past {@link #GROAN_FROM} groans),
	 * louder the nearer it is to breaking. Far enough through, it's watched as a danger ({@link #tick}).
	 */
	static void chopped(ServerLevel level, BlockPos cut, List<BlockPos> ring, int per, int logs) {
		float p = progress(level, ring, per);
		int stage = Math.round(p * 9.0F);
		for (BlockPos r : ring) {
			BlockState state = level.getBlockState(r);
			if (state.is(BlockTags.LOGS) && !state.is(ChoppedLog.BLOCK)) {
				level.destroyBlockProgress(crackId(r), r, Math.max(0, stage - 2));
			}
			for (int k = 1; k <= CRACK_UP; k++) {
				BlockPos q = r.above(k);
				int s = stage - CRACK_FADE * (k - 1);
				if (s < 0 || !level.getBlockState(q).is(BlockTags.LOGS)) {
					break;
				}
				level.destroyBlockProgress(crackId(q), q, Math.min(9, s));
			}
		}
		if (p >= CREAK_FROM) {
			float near = (p - CREAK_FROM) / (1.0F - CREAK_FROM);
			Vec3 up = Vec3.atCenterOf(cut.above(Math.min(4, Math.max(1, logs / 3))));
			float volume = CREAK_QUIET + (CREAK_LOUD - CREAK_QUIET) * near;
			if (level.getRandom().nextFloat() < 0.5F + 0.5F * near) {
				level.playSound(null, up.x, up.y, up.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, volume, 0.45F + 0.15F * level.getRandom().nextFloat());
			}
			if (p >= GROAN_FROM && level.getRandom().nextFloat() < near) {
				level.playSound(null, up.x, up.y, up.z, SoundEvents.CREAKING_SWAY, SoundSource.BLOCKS, volume, 0.5F + 0.1F * level.getRandom().nextFloat());
			}
		}
		// (someone's at this tree: its other cuts aren't left alone either)
		long now = level.getGameTime();
		for (Map.Entry<Weak, Long> e : WEAK.entrySet()) {
			if (e.getKey().level() == level && e.getKey().cut().distSqr(cut) <= SAME_TREE * SAME_TREE) {
				e.setValue(now);
			}
		}
		if (p >= HAZARD_FROM) {
			WEAK.put(new Weak(level, cut.immutable()), now);
		}
	}

	/** The trunk at {@code ring} came down: its cracks go, and it's no longer watched. */
	static void felled(ServerLevel level, BlockPos cut, List<BlockPos> ring) {
		for (BlockPos r : ring) {
			for (int k = 0; k <= CRACK_UP; k++) {
				level.destroyBlockProgress(crackId(r.above(k)), r.above(k), -1);
			}
			WEAK.remove(new Weak(level, r));
		}
		WEAK.remove(new Weak(level, cut));
	}

	/** Who the cracks at {@code pos} belong to: a number no entity has. */
	private static int crackId(BlockPos pos) {
		return -1000 - Math.floorMod(pos.hashCode(), 1 << 28);
	}

	/** A chopped log at {@code pos} ticked (after a restart): if it's a half-cut tree, it's watched again. */
	static void remember(ServerLevel level, BlockPos pos) {
		Weak w = new Weak(level, pos.immutable());
		if (WEAK.containsKey(w) || !(level.getBlockEntity(pos) instanceof ChoppedLog.Entity e)) {
			return;
		}
		List<BlockPos> logs = Trees.trunk(level, pos);
		List<BlockPos> ring = ring(level, pos);
		if (progress(level, ring, through(logs.size(), ring.size())) >= HAZARD_FROM) {
			WEAK.put(w, e.lastAt);
		}
	}

	/**
	 * Half-cut trees nobody is cutting at: one with someone (anyone, a player, a villager, a sheep) standing in its path
	 * creaks a warning and may give way on them; any of them may give way in a thunderstorm.
	 */
	private static void tick(MinecraftServer server) {
		if (++clock % HAZARD_CHECK != 0 || WEAK.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Weak, Long>> it = WEAK.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Weak, Long> entry = it.next();
			ServerLevel level = entry.getKey().level();
			BlockPos cut = entry.getKey().cut();
			if (!Loaded.made(level, cut)) {
				continue;
			}
			if (!level.getBlockState(cut).is(ChoppedLog.BLOCK)) {
				it.remove();
				continue;
			}
			if (level.getGameTime() - entry.getValue() < HAZARD_QUIET) {
				continue;
			}
			List<BlockPos> logs = Trees.trunk(level, cut);
			List<BlockPos> ring = ring(level, cut);
			float p = progress(level, ring, through(logs.size(), ring.size()));
			if (p < HAZARD_FROM || logs.size() <= 1) {
				it.remove();
				continue;
			}
			List<BlockPos> above = new ArrayList<>(logs);
			above.remove(cut);
			List<BlockPos> leaves = Trees.leavesOf(level, above);
			Float way = way(level, cut, ring, above, leaves);
			float heading = way != null ? way : FallingTree.heavier(level, cut, above, leaves, null);
			int tall = 0;
			for (BlockPos q : above) {
				tall = Math.max(tall, q.getY() - cut.getY());
			}
			boolean under = !inPath(level, cut, heading, tall).isEmpty();
			float chance = (under ? HAZARD_CHANCE * p : 0.0F) + (level.isThundering() ? STORM_CHANCE * p : 0.0F);
			if (under) {
				Vec3 up = Vec3.atCenterOf(cut.above(2));
				level.playSound(null, up.x, up.y, up.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, CREAK_LOUD, 0.45F);
			}
			if (chance > 0.0F && level.getRandom().nextFloat() < chance) {
				it.remove();
				Trees.giveWay(level, cut, ring, heading);
			}
		}
	}

	/** Who stands where a tree on {@code cut}, {@code tall} logs high, would come down going over toward {@code heading}. */
	private static List<LivingEntity> inPath(ServerLevel level, BlockPos cut, float heading, int tall) {
		double cx = cut.getX() + 0.5, cz = cut.getZ() + 0.5;
		double dx = Mth.cos(heading), dz = Mth.sin(heading);
		AABB box = new AABB(cut).inflate(tall + 1.0, 2.0, tall + 1.0).expandTowards(0.0, tall, 0.0);
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> {
			if (!e.isAlive() || e.isSpectator() || e instanceof Player player && player.isCreative()) {
				return false;
			}
			double ox = e.getX() - cx, oz = e.getZ() - cz;
			double along = ox * dx + oz * dz, side = Math.abs(-ox * dz + oz * dx);
			return along > 0.3 && along <= tall + 1.0 && side <= HAZARD_SIDE;
		});
	}

	/** How far through (0 to 1) the trunk on {@code cut} is cut at that height. */
	static float share(ServerLevel level, BlockPos cut) {
		List<BlockPos> logs = Trees.trunk(level, cut);
		List<BlockPos> ring = ring(level, cut);
		return progress(level, ring, through(logs.size(), ring.size()));
	}

	/**
	 * The side of the tree on {@code foot} it's best felled to: the way with the most open ground (no blocks but leaves and
	 * plants at head height) before anything stands in the way, so it doesn't come down on a roof.
	 */
	static Direction clearSide(ServerLevel level, BlockPos foot, int reach) {
		Direction best = Direction.NORTH;
		int most = -1;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int open = 0;
			for (int k = 1; k <= reach; k++) {
				BlockPos at = foot.relative(d, k);
				if (!clear(level, at.above()) || !clear(level, at.above(2))) {
					break;
				}
				open++;
			}
			if (open > most) {
				most = open;
				best = d;
			}
		}
		return best;
	}

	private static boolean clear(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.getBlock() instanceof LeavesBlock || state.canBeReplaced();
	}

	/** Whether the next {@code stroke} strokes at the trunk on {@code cut} would bring the tree down (a woodcutter calls out). */
	static boolean nextFells(ServerLevel level, BlockPos cut, int stroke) {
		BlockState state = level.getBlockState(cut);
		if (!state.is(BlockTags.LOGS)) {
			return false;
		}
		List<BlockPos> logs = Trees.trunk(level, cut);
		List<BlockPos> ring = ring(level, cut);
		int per = through(logs.size(), ring.size()), left = 0;
		for (BlockPos p : ring) {
			BlockState s = level.getBlockState(p);
			left += ChoppedLog.through(s) ? 0 : Math.max(1, per - ChoppedLog.chops(s));
		}
		return left <= stroke;
	}

	// ---- the ground answers -------------------------------------------------------------------------------------------

	/**
	 * A tree is going over: the birds in its crown ({@code leaves}, where they stand now) burst out of it, flapping. With
	 * Naturalist in the pack a few of its songbirds fly up out of a big enough crown (unless plenty are about already);
	 * the birds there already take off.
	 */
	static void flush(ServerLevel level, List<BlockPos> leaves) {
		if (leaves.size() < BIRD_LEAVES / 2) {
			return;
		}
		BlockPos top = leaves.get(0);
		for (BlockPos p : leaves) {
			if (p.getY() > top.getY()) {
				top = p;
			}
		}
		Vec3 crown = Vec3.atCenterOf(top);
		EntityType<?> bird = BuiltInRegistries.ENTITY_TYPE.getOptional(BIRD).orElse(null);
		AABB round = new AABB(crown, crown).inflate(BIRD_REACH);
		List<Mob> near = bird == null ? List.of() : level.getEntitiesOfClass(Mob.class, round, m -> m.getType() == bird);
		for (Mob m : near) {
			if (m.distanceToSqr(crown) < 8.0 * 8.0) {
				m.setDeltaMovement(m.getDeltaMovement().add((level.getRandom().nextFloat() - 0.5F) * 0.6, 0.5, (level.getRandom().nextFloat() - 0.5F) * 0.6));
				m.needsSync = true;
			}
		}
		if (level.getRandom().nextFloat() >= BIRD_CHANCE || !level.isBrightOutside()) {
			return;
		}
		int count = Math.min(BIRDS_MOST, Math.max(1, leaves.size() / BIRD_LEAVES));
		level.playSound(null, crown.x, crown.y, crown.z, SoundEvents.PARROT_FLY, SoundSource.AMBIENT, 1.2F, 1.4F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(top)), crown.x, crown.y, crown.z, 12, 1.0, 0.5, 1.0, 0.1);
		if (bird == null || near.size() >= BIRDS_NEAR_MOST) {
			return;
		}
		for (int i = 0; i < count; i++) {
			BlockPos from = leaves.get(level.getRandom().nextInt(leaves.size()));
			Entity e = bird.create(level, EntitySpawnReason.TRIGGERED);
			if (e == null) {
				continue;
			}
			e.snapTo(from.getX() + 0.5, from.getY() + 1.0, from.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			e.setDeltaMovement((level.getRandom().nextFloat() - 0.5F) * 0.8, 0.6, (level.getRandom().nextFloat() - 0.5F) * 0.8);
			level.addFreshEntity(e);
		}
	}

	/**
	 * A felled tree struck the ground, its wood lying at {@code logs} (where each log's middle is) and its leaves of
	 * {@code leaf} (null: none): dust and leaves burst up along it, grass and flowers under it are crushed and the snow
	 * pressed flat, the players near feel the thump ({@link Thump}, stronger close by and for a bigger tree), and the
	 * animals round it bolt.
	 */
	static void landed(ServerLevel level, List<Vec3> logs, @Nullable BlockState leaf) {
		if (logs.isEmpty()) {
			return;
		}
		Vec3 middle = Vec3.ZERO;
		for (int i = 0; i < logs.size(); i++) {
			Vec3 at = logs.get(i);
			middle = middle.add(at);
			BlockPos pos = BlockPos.containing(at);
			flatten(level, pos);
			flatten(level, pos.below());
			if (i % 2 == 0) {
				BlockState ground = level.getBlockState(pos.below());
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y - 0.3, at.z, 5, 0.5, 0.1, 0.5, 0.15);
				}
				level.sendParticles(ParticleTypes.POOF, at.x, at.y - 0.2, at.z, 2, 0.4, 0.1, 0.4, 0.02);
				if (leaf != null) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, leaf), at.x, at.y + 0.6, at.z, 6, 0.8, 0.5, 0.8, 0.12);
				}
			}
		}
		middle = middle.scale(1.0 / logs.size());
		float size = Math.min(1.0F, logs.size() / (float)THUMP_FULL_LOGS);
		for (ServerPlayer player : level.players()) {
			double d = player.position().distanceTo(middle);
			if (d < THUMP_REACH && player.onGround()) {
				float strength = (float)(1.0 - d / THUMP_REACH) * size * THUMP_MOST;
				if (strength > 0.05F) {
					ServerPlayNetworking.send(player, new Thump(strength));
				}
			}
		}
		for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(middle, middle).inflate(BOLT_REACH),
			a -> a.isAlive() && !a.isPassenger() && !a.isVehicle() && !a.isLeashed())) {
			bolt(animal, middle);
		}
	}

	/** Grass, flowers and the like at {@code pos} crushed under the trunk, snow there pressed down to a skin. */
	private static void flatten(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.SNOW)) {
			if (state.getValue(SnowLayerBlock.LAYERS) > 1) {
				level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, 1), Block.UPDATE_ALL);
			}
			return;
		}
		if (state.getBlock() instanceof LeavesBlock || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return;
		}
		if (state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.FLOWERS)) {
			level.destroyBlock(pos, false);
		}
	}

	/** {@code animal} runs off from {@code from}. */
	private static void bolt(PathfinderMob animal, Vec3 from) {
		Vec3 to = DefaultRandomPos.getPosAway(animal, BOLT_RUN, 4, from);
		if (to != null) {
			animal.getNavigation().moveTo(to.x, to.y, to.z, BOLT_SPEED);
		}
	}

	/** (to the client) A thump through the ground of {@code strength} degrees of shake, easing off ({@code CalamitiesClient}). */
	public record Thump(float strength) implements CustomPacketPayload {
		public static final Type<Thump> TYPE = new Type<>(JaymosKingdoms.id("tree_thump"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Thump> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeFloat(p.strength), buf -> new Thump(buf.readFloat()));

		@Override
		public Type<Thump> type() {
			return TYPE;
		}
	}
}
