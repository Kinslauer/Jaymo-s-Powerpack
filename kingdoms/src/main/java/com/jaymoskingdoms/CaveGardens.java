package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Cave gardens (tools/small_designs/cave_garden.py): chambers of moss, berried vines and lit candles under the old woods,
 * the work of the druids. Most stand empty and run down as people pick them. In the largest, with the nook of bed, barrel
 * and lectern, an old druid may live ({@link Groves#druidAt}): rolled once for each garden when a player first comes near it
 * ({@link #DRUID_CHANCE}), placed beside the lectern, whichever way the jigsaw turned the piece.
 * <p>
 * While that druid lives, the garden is tended. Every {@link #TEND_TICKS}, with a player within {@link #TEND_RANGE}, a few
 * picked spots grow back: cave vines get their berries again (only where there is light, as berries need it), a bare moss
 * block gets a carpet or a mushroom (lit too), a snuffed candle is lit again, and moss creeps onto stone that lies against
 * moss and has air before it. This is the living-things rules: sympathy (light is what berries and moss want, moss spreads to
 * what touches it), a feedback loop (a garden people strip bare gives the druid nothing to tend, and if the druid dies,
 * nothing is tended and the garden runs down), stewardship (a player who brings the druid what she wants, {@link Groves#want},
 * has her tend the garden {@link #GIFT_SPEEDUP} times as fast that day; nobody is punished for leaving it alone).
 */
public final class CaveGardens {
	// ---- tuning ----
	/** The structure tag of the gardens (all three sizes). */
	static final TagKey<Structure> GARDENS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("cave_gardens"));
	/** Players are looked over for a garden near them this often (ticks), out to this many chunks. */
	static final int EVERY = 100, LOOK_CHUNKS = 3;
	/** A garden this wide (blocks) or wider has the druid's nook: the large one is 21 across. */
	static final int LARGE_SPAN = 21;
	/** The share of large gardens a druid lives in. */
	static final float DRUID_CHANCE = 0.6F;
	/** A tended garden is tended this often (ticks), when a player is within this many blocks (flat) of its middle. */
	static final int TEND_TICKS = 1200, TEND_RANGE = 32;
	/** A garden whose druid was brought what she wants today is tended this many times as often. */
	static final int GIFT_SPEEDUP = 2;
	/** How many spots are looked at in a tending, and how many of them may grow something. */
	static final int PICKS = 40, GROWN = 4;
	/** Block light a berry or a carpet needs to come back (a lantern, a candle, another vine's berries). */
	static final int GROW_LIGHT = 7;
	/** The share of carpet spots that grow a mushroom instead, and the chance moss takes a stone it lies against. */
	static final float MUSHROOM = 0.3F, MOSS_SPREAD = 0.4F;
	/** The chamber's height above its floor in the large garden (the dome is 7 high). */
	static final int ROOF_RISE = 8;

	/** The gardens already seen to (their start chunks, packed), with a druid or without. Saved on the Overworld. */
	private static final AttachmentType<List<Long>> SETTLED = AttachmentRegistry.create(JaymosKingdoms.id("cave_gardens_settled"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static Set<Long> settled;
	/** Start chunk → the game time it was last tended (not saved: after a restart a garden waits one more interval). */
	private static final Map<Long, Long> TENDED = new HashMap<>();

	private CaveGardens() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CaveGardens::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			settled = null;
			TENDED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		if (structures.get(GARDENS).isEmpty() || level.players().isEmpty()) {
			return;
		}
		Map<Long, StructureStart> near = new HashMap<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -LOOK_CHUNKS; dx <= LOOK_CHUNKS; dx++) {
				for (int dz = -LOOK_CHUNKS; dz <= LOOK_CHUNKS; dz++) {
					// (the starts kept in this loaded chunk itself: following references could load another chunk)
					var chunk = level.getChunkSource().getChunkNow(middle.x() + dx, middle.z() + dz);
					if (chunk == null) {
						continue;
					}
					for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
						StructureStart start = e.getValue();
						if (start.isValid() && structures.wrapAsHolder(e.getKey()).is(GARDENS) && start.getBoundingBox().getXSpan() >= LARGE_SPAN) {
							near.putIfAbsent(start.getChunkPos().pack(), start);
						}
					}
				}
			}
		}
		for (Map.Entry<Long, StructureStart> garden : near.entrySet()) {
			try {
				BoundingBox box = garden.getValue().getBoundingBox();
				if (!Loaded.made(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
					// (not all of it made and loaded yet: looked at again later)
					continue;
				}
				if (settled == null) {
					settled = new HashSet<>(level.getAttachedOrElse(SETTLED, List.<Long>of()));
				}
				if (settled.add(garden.getKey())) {
					level.setAttached(SETTLED, new ArrayList<>(settled));
					settle(level, box);
				} else {
					tend(level, garden.getKey(), box);
				}
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("cave garden scan failed", e);
			}
		}
	}

	/** The old druid who lives in the garden inside {@code box}, or null. */
	private static @Nullable Villager druid(ServerLevel level, BoundingBox box) {
		List<Villager> found = level.getEntitiesOfClass(Villager.class,
			new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1), d -> d.isAlive() && Groves.is(d));
		return found.isEmpty() ? null : found.get(0);
	}

	/** Rolled once: maybe an old druid moves into the nook, to stand beside its lectern. */
	private static void settle(ServerLevel level, BoundingBox box) {
		if (level.getRandom().nextFloat() >= DRUID_CHANCE || druid(level, box) != null) {
			return;
		}
		BlockPos lectern = null;
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
			if (level.getBlockState(p).is(Blocks.LECTERN)) {
				lectern = p.immutable();
				break;
			}
		}
		if (lectern == null) {
			return;
		}
		// (its front first, where the design keeps a reader's place clear, then its other sides)
		Direction front = level.getBlockState(lectern).getValue(LecternBlock.FACING);
		for (Direction side : new Direction[] {front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()}) {
			BlockPos stand = lectern.relative(side);
			if (standable(level, stand)) {
				Groves.druidAt(level, stand);
				return;
			}
		}
	}

	/** Whether one can stand at {@code p}: a firm floor under it, and room for a body, dry. */
	private static boolean standable(ServerLevel level, BlockPos p) {
		BlockPos below = p.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	/** Tends the garden if its druid lives, a player is near, and its time has come. */
	private static void tend(ServerLevel level, long key, BoundingBox box) {
		BlockPos middle = box.getCenter();
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && Math.hypot(player.getX() - middle.getX(), player.getZ() - middle.getZ()) <= TEND_RANGE) {
				near.add(player);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		Villager druid = druid(level, box);
		if (druid == null) {
			return;
		}
		long now = level.getGameTime();
		int interval = TEND_TICKS;
		for (ServerPlayer player : near) {
			if (Groves.givenToday(player, druid)) {
				interval /= GIFT_SPEEDUP;
				break;
			}
		}
		Long last = TENDED.putIfAbsent(key, now);
		if (last == null || now - last < interval) {
			return;
		}
		TENDED.put(key, now);
		RandomSource random = level.getRandom();
		int floor = druid.getBlockY() - 1;
		int grown = 0;
		for (int pick = 0; pick < PICKS && grown < GROWN; pick++) {
			BlockPos at = new BlockPos(box.minX() + 1 + random.nextInt(box.getXSpan() - 2), floor + random.nextInt(ROOF_RISE + 1),
				box.minZ() + 1 + random.nextInt(box.getZSpan() - 2));
			if (grow(level, at, random)) {
				grown++;
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}

	/** Brings back what was picked at {@code at}, if it is a spot that can grow something; whether it did. */
	private static boolean grow(ServerLevel level, BlockPos at, RandomSource random) {
		BlockState state = level.getBlockState(at);
		if (state.hasProperty(BlockStateProperties.BERRIES)) {
			if (state.getValue(BlockStateProperties.BERRIES) || level.getBrightness(LightLayer.BLOCK, at) < GROW_LIGHT) {
				return false;
			}
			level.setBlock(at, state.setValue(BlockStateProperties.BERRIES, true), Block.UPDATE_ALL);
			return true;
		}
		if (state.getBlock() instanceof AbstractCandleBlock) {
			if (state.getValue(AbstractCandleBlock.LIT) || state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
				return false;
			}
			level.setBlock(at, state.setValue(AbstractCandleBlock.LIT, true), Block.UPDATE_ALL);
			return true;
		}
		if (state.isAir() && level.getBlockState(at.below()).is(Blocks.MOSS_BLOCK) && level.getBrightness(LightLayer.BLOCK, at) >= GROW_LIGHT) {
			BlockState plant = random.nextFloat() < MUSHROOM
				? (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState()
				: Blocks.MOSS_CARPET.defaultBlockState();
			if (!plant.canSurvive(level, at)) {
				return false;
			}
			level.setBlock(at, plant, Block.UPDATE_ALL);
			return true;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) && random.nextFloat() < MOSS_SPREAD) {
			boolean mossy = false, open = false;
			for (Direction side : Direction.values()) {
				BlockState beside = level.getBlockState(at.relative(side));
				mossy |= beside.is(Blocks.MOSS_BLOCK);
				open |= beside.isAir();
			}
			if (mossy && open) {
				level.setBlock(at, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				return true;
			}
		}
		return false;
	}
}
