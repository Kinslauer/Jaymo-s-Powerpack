package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fallen trees that have lain still become real blocks, and wake back into a {@link FallingTree} when something moves them.
 * <p>
 * A {@link FallingTree} is drawn block by block every frame; a forest of them after a storm or a fire was the biggest single
 * cost in Andy's frame (spark profile, 2026-11-28: a quarter of the render thread with 96 of them about). Andy chose that a
 * tree lying still for {@link FallingTree#SETTLE_AFTER} becomes its logs as real blocks where they lie (drawn with the land,
 * for free), its leaves dropping off, and that it wakes, every log back where it was, when:
 * <ul>
 * <li>someone strong enough to shove it ({@link FallingTree#SHOVE_BRAWN}) walks into one of its logs ({@link #shoves}),</li>
 * <li>a giant's hand closes on it ({@link FallingTree#take}),</li>
 * <li>a player breaks one of its logs or the block under one ({@link #broken}): broken through, it comes apart in two as a
 * lying tree does, and with what held it up gone, it falls,</li>
 * <li>a tornado's winds reach one of its logs (they carry it off as they would any fallen tree).</li>
 * </ul>
 * Each tree is kept on the chunk its foot is in: where it lay, its lean and heading, and each log's piece, block and where
 * it was put (a log with no room where it lay, sunk in the ground, is kept but not put). A log since broken or changed comes
 * back no more. Its blocks are known to {@link Trees#natural} as no standing tree's.
 */
public final class SettledTrees {
	/** How often (ticks) those walking into settled logs are looked for. */
	static final int SHOVE_CHECK = 4;

	/** One log of a settled tree: its piece of the tree, and the block put for it at {@code pos} (null: no room there). */
	record Log(FallingTree.Piece piece, BlockPos pos, @Nullable BlockState placed) {
		static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
			FallingTree.PIECE_CODEC.fieldOf("piece").forGetter(Log::piece),
			BlockPos.CODEC.fieldOf("pos").forGetter(Log::pos),
			BlockState.CODEC.optionalFieldOf("placed").forGetter(l -> java.util.Optional.ofNullable(l.placed()))
		).apply(i, (piece, pos, placed) -> new Log(piece, pos, placed.orElse(null))));
	}

	/** A settled tree: where it lay (its foot), how it lay, and its logs. */
	record Tree(String id, Vec3 at, float heading, float lean, float lift, float reach, boolean byPlayer, List<Log> logs) {
		static final Codec<Tree> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Tree::id),
			Vec3.CODEC.fieldOf("at").forGetter(Tree::at),
			Codec.FLOAT.fieldOf("heading").forGetter(Tree::heading),
			Codec.FLOAT.fieldOf("lean").forGetter(Tree::lean),
			Codec.FLOAT.fieldOf("lift").forGetter(Tree::lift),
			Codec.FLOAT.fieldOf("reach").forGetter(Tree::reach),
			Codec.BOOL.fieldOf("by_player").forGetter(Tree::byPlayer),
			Log.CODEC.listOf().fieldOf("logs").forGetter(Tree::logs)
		).apply(i, Tree::new));
	}

	/** The settled trees whose foot is in a chunk, by id. */
	static final AttachmentType<Map<String, Tree>> SETTLED = AttachmentRegistry.create(JaymosKingdoms.id("settled_trees"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Tree.CODEC)));

	/** Which settled tree a put log belongs to: its chunk, its id, and how many logs it has. */
	private record Key(long chunk, String id, int logs) {
	}

	/** Every put log of a settled tree in a loaded chunk, by where it is. */
	private static final Map<ServerLevel, Long2ObjectMap<Key>> INDEX = new WeakHashMap<>();

	private SettledTrees() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			Map<String, Tree> trees = chunk.getAttached(SETTLED);
			if (trees != null) {
				trees.values().forEach(t -> index(level, chunk.getPos().pack(), t));
			}
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			Map<String, Tree> trees = chunk.getAttached(SETTLED);
			if (trees != null) {
				trees.values().forEach(t -> unindex(level, t));
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel server) {
				broken(server, pos);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % SHOVE_CHECK == 0) {
				shoves(level);
			}
		});
	}

	private static Long2ObjectMap<Key> index(ServerLevel level) {
		return INDEX.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
	}

	private static void index(ServerLevel level, long chunk, Tree tree) {
		Long2ObjectMap<Key> index = index(level);
		Key key = new Key(chunk, tree.id(), tree.logs().size());
		for (Log log : tree.logs()) {
			if (log.placed() != null) {
				index.put(log.pos().asLong(), key);
			}
		}
	}

	private static void unindex(ServerLevel level, Tree tree) {
		Long2ObjectMap<Key> index = INDEX.get(level);
		if (index == null) {
			return;
		}
		for (Log log : tree.logs()) {
			Key key = index.get(log.pos().asLong());
			if (key != null && key.id().equals(tree.id())) {
				index.remove(log.pos().asLong());
			}
		}
	}

	/** Keeps a tree that has just settled (its logs already put) with the chunk its foot is in. */
	static void keep(ServerLevel level, Vec3 at, float heading, float lean, float lift, float reach, boolean byPlayer, List<Log> logs) {
		Tree tree = new Tree(UUID.randomUUID().toString(), at, heading, lean, lift, reach, byPlayer, List.copyOf(logs));
		LevelChunk chunk = level.getChunkAt(BlockPos.containing(at));
		Map<String, Tree> trees = new HashMap<>(chunk.getAttachedOrElse(SETTLED, Map.of()));
		trees.put(tree.id(), tree);
		chunk.setAttached(SETTLED, trees);
		chunk.markUnsaved();
		index(level, chunk.getPos().pack(), tree);
	}

	/** Whether {@code pos} is a log of a settled tree (not a standing tree's, whatever leaves are about it). */
	public static boolean settled(ServerLevel level, BlockPos pos) {
		Long2ObjectMap<Key> index = INDEX.get(level);
		return index != null && !index.isEmpty() && index.containsKey(pos.asLong());
	}

	/** The settled tree with a log at {@code pos} wakes ({@link FallingTree#wake}); null if there's none, or none of it is left. */
	public static @Nullable FallingTree wake(ServerLevel level, BlockPos pos) {
		Long2ObjectMap<Key> index = INDEX.get(level);
		Key key = index == null ? null : index.get(pos.asLong());
		if (key == null) {
			return null;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key.chunk()), ChunkPos.getZ(key.chunk()));
		Map<String, Tree> trees = chunk == null ? null : chunk.getAttached(SETTLED);
		Tree tree = trees == null ? null : trees.get(key.id());
		if (tree == null) {
			index.remove(pos.asLong());
			return null;
		}
		Map<String, Tree> left = new HashMap<>(trees);
		left.remove(key.id());
		if (left.isEmpty()) {
			chunk.removeAttached(SETTLED);
		} else {
			chunk.setAttached(SETTLED, left);
		}
		chunk.markUnsaved();
		unindex(level, tree);
		return FallingTree.wake(level, tree);
	}

	/** The nearest log within {@code reach} of {@code at} of a settled tree of no more than {@code mostLogs} logs (for a giant's hand). */
	static @Nullable BlockPos near(ServerLevel level, Vec3 at, double reach, int mostLogs) {
		Long2ObjectMap<Key> index = INDEX.get(level);
		if (index == null || index.isEmpty()) {
			return null;
		}
		BlockPos c = BlockPos.containing(at);
		int r = Mth.ceil(reach);
		BlockPos best = null;
		double bestD = reach * reach;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			Key key = index.get(p.asLong());
			if (key == null || key.logs() > mostLogs) {
				continue;
			}
			double d = p.distToCenterSqr(at);
			if (d <= bestD) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** A player broke a settled log, or the block one rests on: the tree wakes, and comes apart or falls as it must. */
	private static void broken(ServerLevel level, BlockPos pos) {
		if (settled(level, pos)) {
			wake(level, pos);
		} else if (settled(level, pos.above())) {
			wake(level, pos.above());
		}
	}

	/** Those strong enough to shove a fallen tree, walking into a settled log: it wakes, to be shoved. */
	private static void shoves(ServerLevel level) {
		Long2ObjectMap<Key> index = INDEX.get(level);
		if (index == null || index.isEmpty()) {
			return;
		}
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof LivingEntity e) || !e.isAlive() || e.isSpectator() || e.noPhysics || e.isPassenger() || !e.onGround()
				|| e.isSwimming() || e.isSleeping()) {
				continue;
			}
			Vec3 way = FallingTree.pushing(e);
			if (way == null || FallingTree.brawn(e) < FallingTree.SHOVE_BRAWN) {
				continue;
			}
			double ahead = e.getBbWidth() * 0.5 + 0.4;
			double x = e.getX() + way.x * ahead, z = e.getZ() + way.z * ahead;
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos at = BlockPos.containing(x, e.getY() + 0.5 + dy, z);
				if (index.containsKey(at.asLong())) {
					wake(level, at);
					break;
				}
			}
		}
	}
}
