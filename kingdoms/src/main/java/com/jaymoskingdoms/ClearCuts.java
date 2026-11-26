package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Ent;
import com.jaymoskingdoms.monsters.MocWild;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Ents wake when the forest is cut (Reference Mods patch 7, Mo' Creatures' ent). Every log a player fells or breaks in an
 * old forest (forest, taiga or jungle woods in the Overworld) is tallied against the 32-block square it fell in
 * ({@link Tally}); the tally eases off as the days go ({@link #EASE_TICKS}) and when saplings are planted. Past
 * {@link #WAKE_AT} logs a great old tree of that wood wakes ({@link #wake}), walks after the cutters
 * ({@link Ent}) and plants as it goes; a wood left alone is no longer cut and the ent roots again.
 * <p>
 * Where it stands in the world: a great tree within sight of the cutters, the one they were nearest to, since the old
 * forests keep their eldest where a clearing is made; it needs the cutters to have made a clearing, never appears in a
 * wood nobody has cut, and never in one that has been replanted. Nothing is saved but the ent itself.
 */
public final class ClearCuts {
	/** Logs cut in one square before an ent wakes; how many of them ease off each {@link #EASE_TICKS}. */
	static final double WAKE_AT = 48.0;
	static final long EASE_TICKS = 100L;
	/** How far from the cutters the ent is looked for (blocks); how close the tree it wakes in is (blocks); how near an awake one is called by a fresh cut. */
	static final int TREE_MIN = 10, TREE_MAX = 26, TRIES = 40;
	static final double AWAKE_RANGE = 160.0;
	/** Planting: a sapling lifts this much off the tally; a player who planted this many in a wood is forgiven; the ent's own planting counts for this much. */
	static final double REPLANT = 2.0, ENT_REPLANT = 1.5;
	static final int FORGIVEN_AFTER = 3;
	/** Logs one tree's trunk is taken for when it is woken (at most). */
	static final int TRUNK_MAX = 12;

	private static final class Tally {
		double logs;
		long last;
		final Map<UUID, Long> cutters = new HashMap<>();
		final Map<UUID, Integer> replanted = new HashMap<>();
	}

	private record Planting(ServerLevel level, UUID player, BlockPos at, long due) {
	}

	private static final Map<Long, Tally> TALLIES = new HashMap<>();
	private static final List<Planting> PLANTINGS = new ArrayList<>();

	private ClearCuts() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && state.is(BlockTags.LOGS) && !sp.isCreative()) {
				cut(server, sp, pos, state, 1);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer && player.getItemInHand(hand).is(ItemTags.SAPLINGS)) {
				PLANTINGS.add(new Planting(server, player.getUUID(), hit.getBlockPos().relative(hit.getDirection()), server.getGameTime() + 2));
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ClearCuts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TALLIES.clear();
			PLANTINGS.clear();
		});
	}

	private static long key(BlockPos pos) {
		return ((long) (pos.getX() >> 5) << 32) ^ ((pos.getZ() >> 5) & 0xFFFFFFFFL);
	}

	/** An old forest: a forest, taiga or jungle in the Overworld, not a home's yard (nothing planted near a seat). */
	private static boolean oldForest(ServerLevel level, BlockPos pos) {
		return level.dimension() == Level.OVERWORLD && (level.getBiome(pos).is(BiomeTags.IS_FOREST) || level.getBiome(pos).is(BiomeTags.IS_TAIGA)
			|| level.getBiome(pos).is(BiomeTags.IS_JUNGLE));
	}

	/** {@code logs} logs of a tree were cut at {@code pos} (felled whole, or broken one by one). */
	public static void cut(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState log, int logs) {
		if (!oldForest(level, pos)) {
			return;
		}
		long now = level.getGameTime();
		Tally t = TALLIES.computeIfAbsent(key(pos), k -> new Tally());
		ease(t, now);
		t.logs += logs;
		t.cutters.put(player.getUUID(), now);
		boolean awake = false;
		for (Ent ent : level.getEntitiesOfClass(Ent.class, new AABB(pos).inflate(AWAKE_RANGE), Ent::isAlive)) {
			ent.provoke(player.getUUID(), now);
			awake = true;
		}
		if (!awake && t.logs >= WAKE_AT) {
			wake(level, player, pos, log, t);
		}
	}

	private static void ease(Tally t, long now) {
		if (t.last != 0L && now > t.last) {
			t.logs = Math.max(0.0, t.logs - (double) (now - t.last) / EASE_TICKS);
		}
		t.last = now;
	}

	/** A sapling went in near {@code at} (an ent's, or a player's seen to): the wood has that much less to answer for. */
	public static void replanted(ServerLevel level, BlockPos at) {
		Tally t = TALLIES.get(key(at));
		if (t != null) {
			t.logs = Math.max(0.0, t.logs - ENT_REPLANT);
		}
	}

	/** Wakes the great tree nearest the cutters, or a clearing's edge if there is none, and sends it after them. */
	private static void wake(ServerLevel level, ServerPlayer player, BlockPos cutAt, BlockState cutLog, Tally t) {
		BlockPos spot = null;
		Block sapling = saplingOf(cutLog.getBlock());
		for (int i = 0; i < TRIES && spot == null; i++) {
			int dx = level.getRandom().nextIntBetweenInclusive(-TREE_MAX, TREE_MAX);
			int dz = level.getRandom().nextIntBetweenInclusive(-TREE_MAX, TREE_MAX);
			if (dx * dx + dz * dz < TREE_MIN * TREE_MIN) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cutAt.offset(dx, 0, dz)).below();
			if (!level.getBlockState(top).is(BlockTags.LOGS) || !oldForest(level, top)) {
				continue;
			}
			BlockPos foot = top;
			int height = 0;
			while (level.getBlockState(foot).is(BlockTags.LOGS) && height < 40) {
				foot = foot.below();
				height++;
			}
			if (height >= 4 && level.getBlockState(foot).is(BlockTags.DIRT)) {
				spot = foot.above();
				sapling = saplingOf(level.getBlockState(spot).getBlock());
				// the tree that walks is that tree: its trunk is taken from the ground
				for (int dy = 0; dy < Math.min(height, TRUNK_MAX); dy++) {
					if (level.getBlockState(spot.above(dy)).is(BlockTags.LOGS)) {
						level.setBlock(spot.above(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
		if (spot == null) {
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cutAt.offset(TREE_MIN, 0, 0));
			spot = ground;
		}
		Ent ent = MocWild.ENT.create(level, EntitySpawnReason.EVENT);
		if (ent == null) {
			return;
		}
		ent.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		ent.setWood(sapling);
		long now = level.getGameTime();
		for (Map.Entry<UUID, Long> c : t.cutters.entrySet()) {
			if (now - c.getValue() < ROOT_FORGET) {
				ent.provoke(c.getKey(), now);
			}
		}
		level.addFreshEntity(ent);
		level.playSound(null, spot, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.4F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 1.5, spot.getZ() + 0.5, 30, 1.0, 1.5, 1.0, 0.0);
		t.logs = WAKE_AT / 2;
	}

	/** Cutters this long gone are forgotten by the ent that wakes (ticks). */
	static final long ROOT_FORGET = 12000L;

	/** The sapling of a log's wood (oak's if none is named for it). */
	private static Block saplingOf(Block log) {
		String name = BuiltInRegistries.BLOCK.getKey(log).getPath();
		if (name.endsWith("_log") || name.endsWith("_wood")) {
			String wood = name.substring(0, name.lastIndexOf('_'));
			if (wood.equals("mangrove")) {
				return Blocks.MANGROVE_PROPAGULE;
			}
			return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(wood + "_sapling")).orElse(Blocks.OAK_SAPLING);
		}
		return Blocks.OAK_SAPLING;
	}

	/** A player's sapling that took: it eases the tally, and three of them in one wood make their peace with the ent. */
	private static void tick(MinecraftServer server) {
		if (PLANTINGS.isEmpty()) {
			return;
		}
		PLANTINGS.removeIf(p -> {
			if (p.level().getGameTime() < p.due()) {
				return false;
			}
			if (p.level().getBlockState(p.at()).is(BlockTags.SAPLINGS)) {
				replant(p.level(), p.player(), p.at());
			}
			return true;
		});
	}

	/** {@code player} planted a sapling at {@code at}. */
	public static void replant(ServerLevel level, UUID player, BlockPos at) {
		Tally t = TALLIES.get(key(at));
		if (t == null) {
			return;
		}
		t.logs = Math.max(0.0, t.logs - REPLANT);
		int n = t.replanted.merge(player, 1, Integer::sum);
		if (n >= FORGIVEN_AFTER) {
			t.cutters.remove(player);
			for (Ent ent : level.getEntitiesOfClass(Ent.class, new AABB(at).inflate(AWAKE_RANGE), Ent::isAlive)) {
				ent.forgive(player);
			}
		}
	}
}
