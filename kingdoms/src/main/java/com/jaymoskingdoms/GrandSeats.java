package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Grand seats (0.7.276): a kingdom built from nothing no longer raises {@link Citadel}'s blocky walls, but one of the
 * real castles the pack's structure mods already ship, picked by the town's land ({@link Palette}): Grim Kingdoms'
 * keeps and citadels, ATi Structures' chateau, castles and temple, Repurposed Structures' jungle fortress. Each is
 * read from its own mod by structure id and placed the way {@code /place structure} does (nothing of theirs is copied
 * into this mod, so their licences hold), only moved onto the flattest site beside the village. It comes empty: its
 * monsters, spawners and loot are taken out, and its largest room becomes the throne room ({@link Seats#keep}: the
 * throne at the back, the panic room under the floor, archers on the roofs). With none of those mods loaded, the
 * kingdom builds its citadel as before.
 */
final class GrandSeats {
	/** A castle: its structure id, and the land it suits. */
	record Choice(String id, Palette... lands) {
		boolean suits(Palette land) {
			for (Palette p : this.lands) {
				// (by identity: two palettes may hold the same blocks)
				if (p == land) {
					return true;
				}
			}
			return false;
		}
	}

	/** In order of preference per land; among those loaded, the first that suits the land is taken for jungle, a random one else. */
	static final List<Choice> CHOICES = List.of(
		// Repurposed Structures (LGPL-3.0): Andy's favourite, the jungle fortress, when the pack carries the mod
		new Choice("repurposed_structures:fortress_jungle", Palette.JUNGLE),
		// ATi Structures, Vanilla Edition (ARR, "feel free to add to any modpack")
		new Choice("ati_structures:marble_chateau", Palette.PLAINS),
		new Choice("ati_structures:woodland_keep", Palette.TAIGA, Palette.SWAMP),
		new Choice("ati_structures:catalonian_castle", Palette.DESERT, Palette.BADLANDS, Palette.SAVANNA),
		new Choice("ati_structures:granite_fort", Palette.DESERT, Palette.BADLANDS, Palette.SAVANNA),
		new Choice("ati_structures:fortified_temple", Palette.CHERRY, Palette.JUNGLE),
		new Choice("ati_structures:dojo", Palette.CHERRY),
		// Grim Kingdoms, Vanilla Edition (ARR, "feel free to add to any modpack")
		new Choice("grim_kingdoms:grain_sack_keep", Palette.PLAINS, Palette.SWAMP),
		new Choice("grim_kingdoms:glimmervault_fortress_plains", Palette.PLAINS),
		new Choice("grim_kingdoms:winterhold_citadel_taiga", Palette.TAIGA),
		new Choice("grim_kingdoms:whitewarden_keep", Palette.SNOWY),
		new Choice("grim_kingdoms:winterhold_citadel", Palette.SNOWY),
		new Choice("grim_kingdoms:snowgrave_citadel", Palette.SNOWY));

	/** The site: this much clear land round the castle's middle, at least this far (past its own half) from the bell, and as far again. */
	static final int SITE_MARGIN = 2, SITE_CLEARANCE = 18, SITE_REACH = 48;
	/** Cleared above the floor before it goes up (trees, hillside, snow), at most this high. */
	static final int CLEAR_MAX = 48;
	/** Under the castle: filled down to the ground at most this deep. */
	static final int FOOTING = 16;
	/** A castle wider than this (either way) is passed over: it wouldn't fit beside a village. */
	static final int MAX_SIDE = 112;

	private GrandSeats() {
	}

	/** The castles that suit {@code land} and are loaded in this world. */
	static List<Holder.Reference<Structure>> suited(ServerLevel level, Palette land) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder.Reference<Structure>> out = new ArrayList<>();
		for (Choice c : CHOICES) {
			if (c.suits(land)) {
				registry.get(Identifier.parse(c.id())).ifPresent(out::add);
			}
		}
		if (out.isEmpty() && land != Palette.PLAINS) {
			// (a land with nothing of its own takes a plains castle)
			return suited(level, Palette.PLAINS);
		}
		return out;
	}

	/**
	 * Raises a grand seat for {@code kingdom} beside the village at {@code bell}: returns its keep (throne, panic room,
	 * gate, hall, archers' posts), or null if no castle mod is loaded (or none would generate), for the citadel instead.
	 */
	static Castle.@Nullable Keep build(ServerLevel level, Faction kingdom, BlockPos bell) {
		RandomSource random = level.getRandom();
		Palette land = Palette.at(level, bell);
		List<Holder.Reference<Structure>> options = suited(level, land);
		if (options.isEmpty()) {
			return null;
		}
		// the jungle's fortress first; elsewhere any of them
		List<Holder.Reference<Structure>> order = new ArrayList<>(options);
		if (land != Palette.JUNGLE) {
			java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
		}
		for (Holder.Reference<Structure> holder : order) {
			Castle.Keep keep = raise(level, kingdom, bell, holder);
			if (keep != null) {
				return keep;
			}
		}
		return null;
	}

	private static Castle.@Nullable Keep raise(ServerLevel level, Faction kingdom, BlockPos bell, Holder.Reference<Structure> holder) {
		String id = holder.key().identifier().toString();
		StructureStart start = generate(level, holder, ChunkPos.containing(bell));
		if (start == null) {
			JaymosKingdoms.LOGGER.info("The {} wouldn't generate by {}", id, bell);
			return null;
		}
		// the castle and what stands on it or under it; not the streets and houses a few of them grow round themselves
		List<StructurePiece> pieces = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (pieces.isEmpty() || !outlying(Seats.name(piece))) {
				pieces.add(piece);
			}
		}
		List<StructurePiece> built = pieces.stream().filter(piece -> !terrain(Seats.name(piece))).toList();
		BoundingBox core = StructurePiece.createBoundingBox(built.stream());
		if (core.getXSpan() > MAX_SIDE || core.getZSpan() > MAX_SIDE) {
			JaymosKingdoms.LOGGER.info("The {} is too big for a seat ({} by {})", id, core.getXSpan(), core.getZSpan());
			return null;
		}
		// the flattest land beside the village big enough for it; then the castle is moved there, its first piece's foot on the floor
		int half = Math.max(core.getXSpan(), core.getZSpan()) / 2 + SITE_MARGIN;
		Castle.Site site = Castle.site(level, bell, half, half + SITE_CLEARANCE, half + SITE_CLEARANCE + SITE_REACH, 0.0);
		BlockPos mid = core.getCenter();
		int dx = site.center().getX() - mid.getX(), dz = site.center().getZ() - mid.getZ();
		int dy = site.floor() - pieces.getFirst().getBoundingBox().minY();
		for (StructurePiece piece : pieces) {
			piece.move(dx, dy, dz);
		}
		BoundingBox box = StructurePiece.createBoundingBox(pieces.stream());
		core = StructurePiece.createBoundingBox(built.stream());
		List<BoundingBox> buildings = built.stream().map(StructurePiece::getBoundingBox).toList();
		clear(level, buildings, site.floor(), core.maxY());
		place(level, pieces, box);
		empty(level, box);
		footing(level, core, site.floor());
		for (BoundingBox b : buildings) {
			Holds.guard(level.getServer(), kingdom.id(), b);
		}
		// the throne in its largest room (as a village seat's): the throne at the back, the panic room, archers' posts
		List<Seats.Building> rooms = Seats.buildings(start).stream().filter(r -> buildings.contains(r.box())).toList();
		Seats.Pick pick = Seats.pick(level, rooms, bell, false);
		Castle.Keep keep;
		if (pick != null) {
			keep = Seats.keep(level, kingdom, pick.seat(), bell, true, true);
		} else {
			BlockPos hall = Construction.standable(level, core.getCenter().atY(site.floor()), 12);
			BlockPos gate = Construction.standable(level, site.center().relative(site.facing(), half + 2).atY(site.floor()), 8);
			keep = new Castle.Keep(hall, hall, gate, hall, List.of());
		}
		JaymosKingdoms.LOGGER.info("{} takes the {} for its seat at {} ({} by {}); throne at {}", kingdom.name(), id, core.getCenter(), core.getXSpan(),
			core.getZSpan(), keep.throne());
		return keep;
	}

	/** The structure as {@code /place structure} would start it at {@code chunk} (in any biome), or null if it won't. */
	private static @Nullable StructureStart generate(ServerLevel level, Holder.Reference<Structure> holder, ChunkPos chunk) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		var randomState = level.getChunkSource().randomState();
		try {
			StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
				randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState, level.getStructureTemplateManager(), level.getSeed(), chunk,
				0, level, biome -> true);
			return start.isValid() && !start.getPieces().isEmpty() ? start : null;
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("The {} failed to generate", holder.key().identifier(), e);
			return null;
		}
	}

	/** A piece that stands out round the castle (a street, a cottage, a village's golem): left out. */
	static boolean outlying(String piece) {
		return piece.contains("/streets") || piece.contains("street") || piece.contains("houses") || piece.contains("minecraft:village/");
	}

	/** A piece of ground the castle stands on (its rock and earth, not a building). */
	static boolean terrain(String piece) {
		return piece.contains("terrain");
	}

	/** Clears the land where its buildings go, from the floor up: hillside, trees and snow (the castle brings its own air only in places). */
	private static void clear(ServerLevel level, List<BoundingBox> boxes, int floor, int top) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int roof = Math.min(top, floor + CLEAR_MAX);
		for (BoundingBox b : boxes) {
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					for (int y = Math.max(floor, b.minY()); y <= roof; y++) {
						at.set(x, y, z);
						if (!level.getBlockState(at).isAir()) {
							level.setBlock(at, air, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
						}
					}
				}
			}
		}
	}

	/** Places every piece, chunk by chunk, as world generation would ({@code StructureStart#placeInChunk}, for these pieces only). */
	private static void place(ServerLevel level, List<StructurePiece> pieces, BoundingBox box) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomSource random = level.getRandom();
		BoundingBox first = pieces.getFirst().getBoundingBox();
		BlockPos pivot = new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ());
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				BoundingBox slice = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(),
					chunk.getMaxBlockZ());
				for (StructurePiece piece : pieces) {
					if (piece.getBoundingBox().intersects(slice)) {
						piece.postProcess(level, level.structureManager(), generator, random, slice, chunk, pivot);
					}
				}
			}
		}
	}

	/** A seat, not a dungeon: its monsters, loose items and spawners go, and its chests stand empty (the treasury is the panic room's). */
	private static void empty(ServerLevel level, BoundingBox box) {
		for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.of(box).inflate(2.0), e -> e instanceof Enemy || e instanceof ItemEntity)) {
			e.discard();
		}
		List<BlockPos> spawners = new ArrayList<>();
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				for (BlockEntity be : List.copyOf(level.getChunk(cx, cz).getBlockEntities().values())) {
					if (!box.isInside(be.getBlockPos())) {
						continue;
					}
					if (be instanceof SpawnerBlockEntity) {
						spawners.add(be.getBlockPos());
					} else if (be instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
						chest.setLootTable(null);
						chest.setChanged();
					}
				}
			}
		}
		for (BlockPos s : spawners) {
			level.setBlock(s, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Under every column it stands on, solid ground down to the land (no floating corners on a slope). */
	private static void footing(ServerLevel level, BoundingBox box, int floor) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				at.set(x, floor, z);
				if (!level.getBlockState(at).isSolid()) {
					continue;
				}
				for (int d = 1; d <= FOOTING; d++) {
					at.set(x, floor - d, z);
					BlockState state = level.getBlockState(at);
					if (state.isSolid() && !state.is(BlockTags.LEAVES)) {
						break;
					}
					level.setBlock(at, d == 1 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}
}
