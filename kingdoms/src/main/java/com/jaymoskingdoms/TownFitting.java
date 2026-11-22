package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Every trade in every town (Andy, 2026-11-21, the villages plan): an Epic town's houses get our blocks for their trades
 * in place of the game's job sites, the first time a player comes into it (so every town, the ones world generation
 * makes, the ones a kingdom is raised in ({@link EpicTowns}) and the ones already in a world, is refitted once; each
 * town's start is marked in the Overworld's {@link #FITTED} list). Nothing of Epic Structures' is copied or shipped: its
 * buildings stand as generated, and only the job blocks in them are changed after placement.
 * <ul>
 * <li>the butcher's smoker: Kitchen's butcher block, with meat hooks under the beams beside it (a butcher's shop built
 * without a smoker, as the taiga's and the snow's are, gets the block indoors);</li>
 * <li>any other smoker or furnace in a house (not a smithy's): Kitchen's stove (a cook's, and a home cook's, heat);</li>
 * <li>the temple's brewing stand: the shrine ({@link Shrine}), or a shrine indoors in a temple without one;</li>
 * <li>the healer: one bed (an infirmary's, else one by a brewing stand) becomes an infirmary cot with Powers' mortar beside it;</li>
 * <li>the tanner's cauldron: filled with water to soak hides, with Kitchen's drying rack beside it (a tannery without a
 * cauldron, as the desert's and the plains' are, gets one indoors);</li>
 * <li>the farmer keeps the composter, and the animal pen gets Kitchen's feeding trough;</li>
 * <li>the woodcutters: Powers' chopping block outside a house.</li>
 * </ul>
 * A block of a mod that isn't loaded leaves the game's in place. A trade the town has no house for (no smoker, no bed, no
 * cauldron, no fishery by its water, no kitchen) gets a job hall ({@link JobHalls}) at the town's edge, its front to the
 * town; miners and woodcutters work the mine and tree farm a realm's builders make. {@link TownTrades} keeps the people.
 */
public final class TownFitting {
	/** How often (ticks) players' towns are looked for; how long (ticks) a town's land may take to load before it is fitted as it stands. */
	static final int EVERY = 40, WAIT_TICKS = 600;
	/** The most chunks round a town's middle kept loaded while it is fitted. */
	static final int LOAD_RADIUS_MOST = 12;
	/** Meat hooks hung by each butcher block, looked for this far round it. */
	static final int HOOKS = 2, HOOK_REACH = 3;
	/** How far out from a house's walls its chopping block may go. */
	static final int YARD = 3;
	/** A hall stands this far out from the town's edge, tried every {@link #HALL_STEP} blocks along it, up to {@link #HALL_SLIDE} either way of the middle. */
	static final int HALL_GAP = 4, HALL_STEP = 8, HALL_SLIDE = 48;
	/** The most the ground under a hall may rise and fall (blocks). */
	static final int HALL_SPREAD = 2;
	/** How high above a piece's floor a spot is looked for indoors, and how far up its roof may be. */
	static final int INDOORS_HIGH = 6;

	private static final String[] SMITH = {"smith", "armor", "armour", "forge", "weapon", "tool"};
	private static final String[] BUTCHER = {"butcher", "slaughter", "meat"};
	private static final String[] TANNER = {"tann", "leather"};
	private static final String[] TEMPLE = {"temple", "church", "chapel", "cleric", "shrine"};
	private static final String[] HEALER = {"infirm", "hospital", "healer", "apothecar"};
	private static final String[] FISHER = {"fisher", "fishing", "dock", "pier", "harbo"};

	/** The towns fitted, by their start chunk (packed), in the Overworld. */
	static final AttachmentType<List<Long>> FITTED = AttachmentRegistry.create(JaymosKingdoms.id("towns_fitted"),
		builder -> builder.persistent(Codec.LONG.listOf()));
	/** Keeps a town's chunks loaded (made, not run) while it is fitted; lapses on its own. */
	private static final TicketType LOADING = new TicketType(300L, TicketType.FLAG_LOADING);

	/** The fitted towns, read from {@link #FITTED} once a run; the town being loaded to fit, and since when. */
	private static @Nullable Set<Long> fitted;
	private static @Nullable Pending pending;

	private record Pending(long key, StructureStart start, long since) {
	}

	private TownFitting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownFitting::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			fitted = null;
			pending = null;
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}
		Set<Long> done = fitted(level);
		if (pending == null) {
			for (ServerPlayer player : level.players()) {
				Pending found = unfitted(level, player.blockPosition(), done);
				if (found != null) {
					pending = found;
					break;
				}
			}
		}
		Pending p = pending;
		if (p == null) {
			return;
		}
		BoundingBox box = p.start().getBoundingBox();
		ChunkPos middle = ChunkPos.containing(box.getCenter());
		int radius = Math.min(LOAD_RADIUS_MOST, (Math.max(box.getXSpan(), box.getZSpan()) / 2 + HALL_GAP + 16) / 16 + 1);
		boolean ready = Loaded.made(level, box.minX() - 16, box.minZ() - 16, box.maxX() + 16, box.maxZ() + 16);
		if (!ready && level.getGameTime() - p.since() < WAIT_TICKS) {
			level.getChunkSource().addTicketWithRadius(LOADING, middle, radius);
			return;
		}
		pending = null;
		done.add(p.key());
		level.setAttached(FITTED, List.copyOf(done));
		fit(level, p.start());
	}

	private static Set<Long> fitted(ServerLevel level) {
		if (fitted == null) {
			fitted = new HashSet<>(level.getAttachedOrElse(FITTED, List.<Long>of()));
		}
		return fitted;
	}

	/** An Epic town at {@code pos} (by its chunk's references) not yet fitted, or null. */
	private static @Nullable Pending unfitted(ServerLevel level, BlockPos pos, Set<Long> done) {
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
		if (level.getChunkSource().getChunkNow(cx, cz) == null) {
			return null;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkAccess chunk = level.getChunk(cx, cz);
		for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> e : chunk.getAllReferences().entrySet()) {
			Identifier id = registry.getKey(e.getKey());
			if (id == null || !id.toString().startsWith("minecraft:village_")) {
				continue;
			}
			for (long ref : e.getValue()) {
				if (done.contains(ref)) {
					continue;
				}
				ChunkPos from = ChunkPos.unpack(ref);
				if (level.getChunkSource().getChunkNow(from.x(), from.z()) == null) {
					continue;
				}
				StructureStart start = level.getChunk(from.x(), from.z()).getStartForStructure(e.getKey());
				if (start != null && start.isValid() && EpicTowns.surface(level, start)) {
					JaymosKingdoms.LOGGER.info("Fitting the trades of the {} at {}", id, start.getBoundingBox().getCenter());
					return new Pending(ref, start, level.getGameTime());
				}
			}
		}
		return null;
	}

	// ---- fitting a town ---------------------------------------------------------------------------------------------

	/** What the town's pieces hold of the trades' blocks: where, and in which piece (its name). */
	private static final class Found {
		final Map<BlockPos, String> smokers = new LinkedHashMap<>(), furnaces = new LinkedHashMap<>(), stands = new LinkedHashMap<>(),
			cauldrons = new LinkedHashMap<>(), composters = new LinkedHashMap<>(), beds = new LinkedHashMap<>();
		final Set<String> names = new HashSet<>();
		/** The loaded pieces' boxes, by name. */
		final Map<String, List<BoundingBox>> boxes = new LinkedHashMap<>();
	}

	/** Swaps the job blocks in {@code start}'s houses for ours and builds a hall for any trade it has no house for. */
	static void fit(ServerLevel level, StructureStart start) {
		long began = System.nanoTime();
		keepTrades(level, start);
		Found found = new Found();
		for (StructurePiece piece : start.getPieces()) {
			BoundingBox box = piece.getBoundingBox();
			String name = Seats.name(piece);
			found.names.add(name);
			if (!Loaded.made(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
				continue;
			}
			found.boxes.computeIfAbsent(name, n -> new ArrayList<>()).add(box);
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState state = level.getBlockState(p);
				Map<BlockPos, String> into = state.is(Blocks.SMOKER) ? found.smokers : state.is(Blocks.FURNACE) ? found.furnaces
					: state.is(Blocks.BREWING_STAND) ? found.stands : state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) ? found.cauldrons
					: state.is(Blocks.COMPOSTER) ? found.composters
					: state.getBlock() instanceof BedBlock && !(state.getBlock() instanceof InfirmaryCot) && state.getValue(BedBlock.PART) == BedPart.HEAD
						? found.beds : null;
				if (into != null) {
					into.putIfAbsent(p.immutable(), name);
				}
			}
		}
		BlockPos middle = start.getBoundingBox().getCenter();
		Palette land = Palette.at(level, middle);
		String wood = wood(land);
		List<String> housed = new ArrayList<>();
		if (butcher(level, found, wood)) {
			housed.add("butcher");
		}
		if (cook(level, found)) {
			housed.add("cook");
		}
		if (cleric(level, found)) {
			housed.add("cleric");
		}
		if (healer(level, found)) {
			housed.add("healer");
		}
		if (tanner(level, found)) {
			housed.add("tanner");
		}
		if (!found.composters.isEmpty()) {
			housed.add("farmer");
			trough(level, start, found);
		}
		if (woodcutter(level, start, wood)) {
			housed.add("woodcutter");
		}
		boolean water = TownTrades.watered(level, middle);
		boolean fishery = found.names.stream().anyMatch(n -> any(n, FISHER));
		if (fishery) {
			housed.add("fisher");
		}
		Buildings.Kit kit = Buildings.Kit.wild(land, start.getChunkPos().pack());
		List<String> halls = new ArrayList<>();
		if (!housed.contains("butcher")) {
			halls.add(hall(level, start, JobHalls.butcher(kit), "butcher", wood));
		}
		if (!housed.contains("healer")) {
			halls.add(hall(level, start, JobHalls.infirmary(kit), "infirmary", wood));
		}
		if (!housed.contains("tanner")) {
			halls.add(hall(level, start, JobHalls.tannery(kit), "tannery", wood));
		}
		if (!housed.contains("cook")) {
			halls.add(hall(level, start, Buildings.cookhouse(kit), "cookhouse", wood));
		}
		if (water && !fishery) {
			halls.add(hall(level, start, JobHalls.fishery(kit), "fishery", wood));
		}
		JaymosKingdoms.LOGGER.info("Fitted the town at {} in {} ms: housed {}; halls {}", middle, (System.nanoTime() - began) / 1_000_000L, housed,
			halls);
	}

	/**
	 * The townsfolk at the game's trades whose job sites are swapped keep their trade (a little experience, as
	 * {@link TownTrades} gives anyone it takes on): without it the game takes the trade off them for want of the site.
	 */
	private static void keepTrades(ServerLevel level, StructureStart start) {
		BoundingBox b = start.getBoundingBox();
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1))) {
			Holder<VillagerProfession> p = v.getVillagerData().profession();
			if (p.is(VillagerProfession.BUTCHER) || p.is(VillagerProfession.CLERIC) || p.is(VillagerProfession.LEATHERWORKER)) {
				v.setVillagerXp(Math.max(1, v.getVillagerXp()));
			}
		}
	}

	/**
	 * The butcher's smoker becomes a butcher block with hooks beside it, and a butcher's shop built without a smoker gets
	 * one indoors all the same. With no butcher's shop at all, the town's first smoker is the butcher's.
	 */
	private static boolean butcher(ServerLevel level, Found found, String wood) {
		BlockState block = butcherBlock(wood);
		boolean housed = false;
		List<BlockPos> theirs = new ArrayList<>();
		found.smokers.forEach((p, name) -> {
			if (any(name, BUTCHER)) {
				theirs.add(p);
			}
		});
		for (BoundingBox shop : boxes(found, BUTCHER)) {
			if (block == null || theirs.stream().anyMatch(shop::isInside)) {
				continue;
			}
			BlockPos spot = indoors(level, shop);
			if (spot != null) {
				level.setBlock(spot, block, Block.UPDATE_CLIENTS);
				hooks(level, spot);
				housed = true;
			}
		}
		if (theirs.isEmpty() && !housed && !found.smokers.isEmpty()) {
			theirs.add(found.smokers.keySet().iterator().next());
		}
		for (BlockPos p : theirs) {
			found.smokers.remove(p);
			housed = true;
			if (block != null) {
				level.setBlock(p, facing(block, level.getBlockState(p)), Block.UPDATE_CLIENTS);
				hooks(level, p);
			}
		}
		return housed;
	}

	/** Kitchen's butcher block of {@code wood} (oak's where there's none of that wood), or null without Kitchen. */
	private static @Nullable BlockState butcherBlock(String wood) {
		BlockState block = JobHalls.byId(wood.equals("oak") ? "jaymoskitchen:butcher_block" : "jaymoskitchen:" + wood + "_butcher_block");
		return block != null ? block : JobHalls.byId("jaymoskitchen:butcher_block");
	}

	/** Up to {@link #HOOKS} meat hooks near {@code p}, each under something solid with room under it to stand. */
	private static void hooks(ServerLevel level, BlockPos p) {
		BlockState hook = JobHalls.byId("jaymoskitchen:meat_hook");
		if (hook == null) {
			return;
		}
		int hung = 0;
		for (BlockPos q : nearest(p.offset(-HOOK_REACH, 1, -HOOK_REACH), p.offset(HOOK_REACH, 3, HOOK_REACH), p)) {
			if (hung >= HOOKS) {
				return;
			}
			if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isAir() && hook.canSurvive(level, q)) {
				level.setBlock(q, hook, Block.UPDATE_CLIENTS);
				hung++;
			}
		}
	}

	/**
	 * A temple's brewing stand becomes the shrine; a temple with none gets a shrine indoors; with no temple, the town's
	 * first brewing stand is the shrine. Brewing stands elsewhere stay (an infirmary cot beside one treats you).
	 */
	private static boolean cleric(ServerLevel level, Found found) {
		BlockState shrine = Healing.SHRINE != null ? Healing.SHRINE.defaultBlockState() : null;
		List<BlockPos> theirs = new ArrayList<>();
		found.stands.forEach((p, name) -> {
			if (any(name, TEMPLE)) {
				theirs.add(p);
			}
		});
		boolean housed = false;
		if (theirs.isEmpty() && shrine != null) {
			for (BoundingBox temple : boxes(found, TEMPLE)) {
				BlockPos spot = indoors(level, temple);
				if (spot != null) {
					level.setBlock(spot, shrine, Block.UPDATE_CLIENTS);
					housed = true;
					break;
				}
			}
		}
		if (theirs.isEmpty() && !housed && !found.stands.isEmpty()) {
			theirs.add(found.stands.keySet().iterator().next());
		}
		for (BlockPos p : theirs) {
			found.stands.remove(p);
			housed = true;
			if (shrine != null) {
				level.setBlock(p, shrine, Block.UPDATE_CLIENTS);
			}
		}
		return housed;
	}

	/** Every other smoker, and every furnace that isn't a smithy's, becomes Kitchen's stove. */
	private static boolean cook(ServerLevel level, Found found) {
		BlockState stove = JobHalls.byId("jaymoskitchen:stove");
		boolean any = false;
		Map<BlockPos, String> hot = new LinkedHashMap<>(found.smokers);
		found.furnaces.forEach((p, name) -> {
			if (!any(name, SMITH)) {
				hot.put(p, name);
			}
		});
		for (BlockPos p : hot.keySet()) {
			if (stove != null) {
				level.setBlock(p, facing(stove, level.getBlockState(p)), Block.UPDATE_CLIENTS);
			}
			any = true;
		}
		return any;
	}

	/**
	 * One bed becomes an infirmary cot with a mortar beside it: an infirmary's, else one in a house with a brewing stand
	 * left (so the cot treats you with the healer away), a temple's, one in a house of several beds, any.
	 */
	private static boolean healer(ServerLevel level, Found found) {
		if (found.beds.isEmpty() || Healing.COT == null) {
			return false;
		}
		Set<String> stands = new HashSet<>(found.stands.values());
		Map<String, Integer> perHouse = new LinkedHashMap<>();
		found.beds.values().forEach(n -> perHouse.merge(n, 1, Integer::sum));
		BlockPos bed = found.beds.entrySet().stream()
			.min(Comparator.comparingInt((Map.Entry<BlockPos, String> e) -> any(e.getValue(), HEALER) ? 0 : stands.contains(e.getValue()) ? 1
				: any(e.getValue(), TEMPLE) ? 2 : perHouse.get(e.getValue()) > 1 ? 3 : 4))
			.map(Map.Entry::getKey).orElseThrow();
		BlockState head = level.getBlockState(bed);
		Direction facing = head.getValue(BedBlock.FACING);
		BlockPos foot = bed.relative(facing.getOpposite());
		BlockState cot = Healing.COT.defaultBlockState().setValue(BedBlock.FACING, facing);
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		level.setBlock(foot, cot.setValue(BedBlock.PART, BedPart.FOOT), flags);
		level.setBlock(bed, cot.setValue(BedBlock.PART, BedPart.HEAD), flags);
		BlockState mortar = JobHalls.byId("jaymospowers:mortar");
		if (mortar != null) {
			for (BlockPos half : List.of(bed, foot)) {
				BlockPos spot = beside(level, half);
				if (spot != null) {
					level.setBlock(spot, mortar, Block.UPDATE_CLIENTS);
					break;
				}
			}
		}
		return true;
	}

	/**
	 * The tanner's cauldrons are filled to soak hides, a drying rack beside each, and a tannery built without one gets one
	 * indoors. With no tannery at all, the town's first cauldron outside a temple is the tanner's.
	 */
	private static boolean tanner(ServerLevel level, Found found) {
		List<BlockPos> theirs = new ArrayList<>();
		found.cauldrons.forEach((p, name) -> {
			if (any(name, TANNER)) {
				theirs.add(p);
			}
		});
		for (BoundingBox tannery : boxes(found, TANNER)) {
			if (theirs.stream().noneMatch(tannery::isInside)) {
				BlockPos spot = indoors(level, tannery);
				if (spot != null) {
					theirs.add(spot);
				}
			}
		}
		if (theirs.isEmpty()) {
			found.cauldrons.entrySet().stream().filter(e -> !any(e.getValue(), TEMPLE)).findFirst().ifPresent(e -> theirs.add(e.getKey()));
		}
		BlockState rack = JobHalls.byId("jaymoskitchen:drying_rack");
		for (BlockPos p : theirs) {
			level.setBlock(p, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3), Block.UPDATE_CLIENTS);
			BlockPos spot = rack == null ? null : beside(level, p);
			if (spot != null) {
				level.setBlock(spot, rack, Block.UPDATE_CLIENTS);
			}
		}
		return !theirs.isEmpty();
	}

	/** A feeding trough in the animal pen nearest the first composter (or, with no pen, beside the composter). */
	private static void trough(ServerLevel level, StructureStart start, Found found) {
		BlockState trough = JobHalls.byId("jaymoskitchen:feeding_trough");
		if (trough == null) {
			return;
		}
		BlockPos composter = found.composters.keySet().iterator().next();
		BoundingBox pen = start.getPieces().stream().filter(p -> Seats.name(p).contains("animal_pen")).map(StructurePiece::getBoundingBox)
			.min(Comparator.comparingDouble(b -> b.getCenter().distSqr(composter))).orElse(null);
		BlockPos spot = null;
		if (pen != null && Loaded.made(level, pen.minX(), pen.minZ(), pen.maxX(), pen.maxZ())) {
			for (BlockPos q : nearest(new BlockPos(pen.minX(), pen.minY(), pen.minZ()), new BlockPos(pen.maxX(), pen.maxY(), pen.maxZ()), pen.getCenter())) {
				if (standable(level, q)) {
					spot = q;
					break;
				}
			}
		}
		if (spot == null) {
			spot = beside(level, composter);
		}
		if (spot != null) {
			level.setBlock(spot, trough, Block.UPDATE_CLIENTS);
		}
	}

	/** Powers' chopping block (of the land's wood) on open ground just outside the town's biggest house that isn't a temple. */
	private static boolean woodcutter(ServerLevel level, StructureStart start, String wood) {
		BlockState block = JobHalls.byId("jaymospowers:" + wood + "_chopping_block");
		if (block == null) {
			block = JobHalls.byId("jaymospowers:oak_chopping_block");
		}
		if (block == null) {
			return false;
		}
		List<BoundingBox> pieces = start.getPieces().stream().map(StructurePiece::getBoundingBox).toList();
		for (Seats.Building house : Seats.buildings(start)) {
			if (house.church()) {
				continue;
			}
			BoundingBox b = house.box();
			for (int out = 1; out <= YARD; out++) {
				for (int x = b.minX() - out; x <= b.maxX() + out; x++) {
					for (int z = b.minZ() - out; z <= b.maxZ() + out; z++) {
						boolean ring = x == b.minX() - out || x == b.maxX() + out || z == b.minZ() - out || z == b.maxZ() + out;
						if (!ring || !Loaded.made(level, x, z)) {
							continue;
						}
						BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
						BlockPos at = top;
						if (standable(level, at) && pieces.stream().noneMatch(pb -> pb != b && pb.isInside(at) && pb.getYSpan() > 2)
							&& Math.abs(at.getY() - b.minY()) <= 3) {
							level.setBlock(at, block, Block.UPDATE_CLIENTS);
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	// ---- halls ------------------------------------------------------------------------------------------------------

	/**
	 * {@code hall} built just outside the town, on a side nearest its middle that is dry, near level and clear of the
	 * town's pieces, its front toward the town. Returns what was built and where, or why not.
	 */
	private static String hall(ServerLevel level, StructureStart start, Blueprint hall, String what, String wood) {
		BoundingBox town = start.getBoundingBox();
		BlockPos middle = town.getCenter();
		List<BoundingBox> pieces = new ArrayList<>(start.getPieces().stream().map(StructurePiece::getBoundingBox).toList());
		for (int slide = 0; slide <= HALL_SLIDE; slide = slide <= 0 ? -slide + HALL_STEP : -slide) {
			for (Direction out : Direction.Plane.HORIZONTAL) {
				Direction along = out.getClockWise();
				int edge = switch (out) {
					case NORTH -> town.minZ();
					case SOUTH -> town.maxZ();
					case WEST -> town.minX();
					default -> town.maxX();
				};
				BlockPos anchor = out.getAxis() == Direction.Axis.Z
					? new BlockPos(middle.getX() + along.getStepX() * slide, 0, edge + out.getStepZ() * HALL_GAP)
					: new BlockPos(edge + out.getStepX() * HALL_GAP, 0, middle.getZ() + along.getStepZ() * slide);
				CampBuildings.Placer placer = new CampBuildings.Placer(anchor, out.getOpposite(), hall.width());
				Integer floor = floor(level, placer, hall, pieces);
				if (floor == null) {
					continue;
				}
				CampBuildings.Placer at = new CampBuildings.Placer(anchor.atY(floor), out.getOpposite(), hall.width());
				clear(level, at, hall);
				CampBuildings.place(level, hall, at);
				refit(level, hall, at, what, wood);
				BlockPos a = at.at(0, 0, 0), b = at.at(hall.width() - 1, hall.height(), hall.depth() - 1);
				pieces.add(BoundingBox.fromCorners(a, b));
				return what + " at " + anchor.atY(floor).toShortString();
			}
		}
		return what + " (no ground for it)";
	}

	/**
	 * A hall's own job blocks made ours as the town's are: a butcher's shop's smoker the butcher block, any other smoker
	 * or furnace a stove, a mortar by an infirmary's first cot (its brewing stand stays, so its cots treat you).
	 */
	private static void refit(ServerLevel level, Blueprint hall, CampBuildings.Placer at, String what, String wood) {
		BlockState stove = JobHalls.byId("jaymoskitchen:stove");
		BlockState block = butcherBlock(wood);
		BlockState mortar = what.equals("infirmary") ? JobHalls.byId("jaymospowers:mortar") : null;
		for (Blueprint.Placement p : hall.blocks()) {
			BlockPos pos = at.at(p.offset());
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.SMOKER) && what.equals("butcher") && block != null) {
				level.setBlock(pos, facing(block, state), Block.UPDATE_CLIENTS);
				hooks(level, pos);
			} else if ((state.is(Blocks.SMOKER) || state.is(Blocks.FURNACE)) && stove != null) {
				level.setBlock(pos, facing(stove, state), Block.UPDATE_CLIENTS);
			} else if (mortar != null && state.getBlock() instanceof InfirmaryCot && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				BlockPos spot = beside(level, pos);
				if (spot != null) {
					level.setBlock(spot, mortar, Block.UPDATE_CLIENTS);
					mortar = null;
				}
			}
		}
	}

	/** The floor's height (the ground's own, replaced) for {@code hall} at {@code placer}, or null if the land won't do. */
	private static @Nullable Integer floor(ServerLevel level, CampBuildings.Placer placer, Blueprint hall, List<BoundingBox> pieces) {
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
		List<Integer> heights = new ArrayList<>();
		for (int x = 0; x < hall.width(); x++) {
			for (int z = 0; z < hall.depth(); z++) {
				BlockPos column = placer.at(x, 0, z);
				if (!Loaded.made(level, column.getX(), column.getZ())) {
					return null;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockPos ground = top.below();
				if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(top).isEmpty() || !Storehouse.ground(level.getBlockState(ground))) {
					return null;
				}
				for (BoundingBox b : pieces) {
					if (b.inflatedBy(1).isInside(top)) {
						return null;
					}
				}
				heights.add(ground.getY());
				low = Math.min(low, ground.getY());
				high = Math.max(high, ground.getY());
				if (high - low > HALL_SPREAD) {
					return null;
				}
			}
		}
		heights.sort(Integer::compare);
		return heights.get(heights.size() / 2);
	}

	/** Clears the hall's footprint above its floor and fills any gap under it. */
	private static void clear(ServerLevel level, CampBuildings.Placer placer, Blueprint hall) {
		for (int x = 0; x < hall.width(); x++) {
			for (int z = 0; z < hall.depth(); z++) {
				for (int y = -3; y < 0; y++) {
					BlockPos p = placer.at(x, y, z);
					BlockState there = level.getBlockState(p);
					if (there.isAir() || there.canBeReplaced()) {
						level.setBlock(p, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				for (int y = 1; y <= hall.height(); y++) {
					BlockPos p = placer.at(x, y, z);
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	// ---- small things -----------------------------------------------------------------------------------------------

	/** The spots in the box from {@code a} to {@code b}, nearest {@code to} first. */
	private static List<BlockPos> nearest(BlockPos a, BlockPos b, BlockPos to) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			out.add(p.immutable());
		}
		out.sort(Comparator.comparingDouble(p -> p.distSqr(to)));
		return out;
	}

	/** The loaded boxes of the pieces whose names hold any of {@code words}. */
	private static List<BoundingBox> boxes(Found found, String[] words) {
		List<BoundingBox> out = new ArrayList<>();
		found.boxes.forEach((name, boxes) -> {
			if (any(name, words)) {
				out.addAll(boxes);
			}
		});
		return out;
	}

	/** An open spot on a floor inside {@code box}, under a roof and against a wall, lowest and nearest its middle first; or null. */
	private static @Nullable BlockPos indoors(ServerLevel level, BoundingBox box) {
		BlockPos c = box.getCenter();
		BlockPos from = new BlockPos(box.minX() + 1, box.minY(), box.minZ() + 1);
		BlockPos to = new BlockPos(box.maxX() - 1, Math.min(box.maxY(), box.minY() + INDOORS_HIGH), box.maxZ() - 1);
		for (BlockPos q : nearest(from, to, new BlockPos(c.getX(), box.minY(), c.getZ()))) {
			if (standable(level, q) && roofed(level, q) && walled(level, q)) {
				return q;
			}
		}
		return null;
	}

	/** Something over {@code p}'s head within {@link #INDOORS_HIGH} blocks. */
	private static boolean roofed(ServerLevel level, BlockPos p) {
		for (int y = 2; y <= INDOORS_HIGH; y++) {
			if (!level.getBlockState(p.above(y)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** A wall on one side of {@code p}. */
	private static boolean walled(ServerLevel level, BlockPos p) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos q = p.relative(d);
			if (level.getBlockState(q).isFaceSturdy(level, q, d.getOpposite())) {
				return true;
			}
		}
		return false;
	}

	/** An open spot beside {@code pos} (the same height, solid underfoot), or null. */
	private static @Nullable BlockPos beside(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos q = pos.relative(d);
			if (standable(level, q)) {
				return q;
			}
		}
		return null;
	}

	/** Air with air over it and something solid under it, no water. */
	private static boolean standable(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
	}

	/** {@code state} turned the way {@code was} faced, if both face a way. */
	private static BlockState facing(BlockState state, BlockState was) {
		if (state.hasProperty(HorizontalDirectionalBlock.FACING) && was.hasProperty(HorizontalDirectionalBlock.FACING)) {
			return state.setValue(HorizontalDirectionalBlock.FACING, was.getValue(HorizontalDirectionalBlock.FACING));
		}
		return state;
	}

	/** The land's wood ("spruce" from its planks), or oak. */
	private static String wood(Palette land) {
		String planks = BuiltInRegistries.BLOCK.getKey(land.planks().getBlock()).getPath();
		return planks.endsWith("_planks") ? planks.substring(0, planks.length() - "_planks".length()) : "oak";
	}

	private static boolean any(String name, String[] words) {
		String n = name.toLowerCase(Locale.ROOT);
		for (String w : words) {
			if (n.contains(w)) {
				return true;
			}
		}
		return false;
	}
}
