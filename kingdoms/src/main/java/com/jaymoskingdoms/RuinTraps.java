package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The trap pass over ruined towers and forts (Andy's pick mb_traps): the first time a player comes near one, the pack's own
 * ({@code jaymoskingdoms}) and Grim Kingdoms', ATi's and Structory's, it is given a few traps, once and for good:
 * <ul>
 * <li>an <b>arrow slit</b>: a stone pressure plate on the floor and, in the wall across, an iron-barred slit that shoots when
 * the plate is stood on;</li>
 * <li>a <b>weak floor</b>: four boards of rotten floor ({@link WeakFloorBlock}) over a pit;</li>
 * <li>a <b>barred door</b>: a plate a few steps inside a door that bars it shut behind whoever steps on it.</li>
 * </ul>
 * Never in a building a settlement keeps clear ({@link Clearing}: any standing settlement whose seat is within its watch).
 */
final class RuinTraps {
	// ---- tuning ----
	/** Looked at this often (ticks), and how far (chunks) round a player. */
	static final int EVERY = 100, CHUNKS = 4;
	/** Plates are watched this often (ticks). */
	static final int WATCH = 5;
	/** A structure gets this many traps at the most, and one for each this many pieces. */
	static final int MOST = 4, PIECES_PER = 3;
	/** How many floor spots are tried for each trap, and how many blocks are read looking for a door. */
	static final int TRIES = 30, DOOR_SCAN = 6000;
	/** How far (blocks) an arrow slit may be from its plate. */
	static final int SLIT_REACH = 6;
	/** The mods whose ruins are trapped, and the words in a structure's name that make it a tower or a fort. */
	static final Set<String> NAMESPACES = Set.of("jaymoskingdoms", "grim_kingdoms", "ati_structures", "structory");
	static final List<String> WORDS = List.of("tower", "fort", "ruin", "keep", "castle", "watch");

	private static final int ARROW = 0, DOOR = 2;

	/** A trap that is waiting for a foot on its plate: its kind, the plate, and the slit or door it works. */
	record Site(int kind, long plate, long source) {
		static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("kind").forGetter(Site::kind),
			Codec.LONG.fieldOf("plate").forGetter(Site::plate),
			Codec.LONG.fieldOf("source").forGetter(Site::source)).apply(i, Site::new));
	}

	private static final AttachmentType<List<String>> DONE = AttachmentRegistry.create(JaymosKingdoms.id("trapped_ruins"),
		b -> b.persistent(Codec.STRING.listOf()));
	private static final AttachmentType<List<Site>> SITES = AttachmentRegistry.create(JaymosKingdoms.id("trap_sites"),
		b -> b.persistent(Site.CODEC.listOf()));

	private RuinTraps() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RuinTraps::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % WATCH == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				List<Site> sites = level.getAttachedOrElse(SITES, List.<Site>of());
				if (!sites.isEmpty()) {
					watch(level, sites);
				}
			}
		}
		if (now % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			look(level, player);
		}
	}

	/** Every ruin in the made chunks round {@code player} that has not been given its traps yet is given them. */
	private static void look(ServerLevel level, ServerPlayer player) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkPos middle = player.chunkPosition();
		List<String> done = null;
		for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
			for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
				ChunkAccess chunk = level.getChunkSource().getChunkNow(middle.x() + dx, middle.z() + dz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
					Identifier id = registry.getKey(e.getKey());
					if (id == null || !NAMESPACES.contains(id.getNamespace()) || WORDS.stream().noneMatch(id.getPath()::contains)) {
						continue;
					}
					for (long ref : e.getValue()) {
						String key = ref + "|" + id;
						if (done == null) {
							done = new ArrayList<>(level.getAttachedOrElse(DONE, List.<String>of()));
						}
						if (done.contains(key)) {
							continue;
						}
						ChunkPos from = ChunkPos.unpack(ref);
						ChunkAccess startChunk = level.getChunkSource().getChunkNow(from.x(), from.z());
						StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(e.getKey());
						if (start == null || !start.isValid()) {
							continue;
						}
						done.add(key);
						level.setAttached(DONE, List.copyOf(done));
						if (!kept(level, start.getBoundingBox().getCenter())) {
							trap(level, start);
						}
					}
				}
			}
		}
	}

	/** A standing settlement keeps the ground round {@code at} clear (its watch covers it), so nothing is laid there. */
	private static boolean kept(ServerLevel level, BlockPos at) {
		for (Faction f : Factions.all(level.getServer())) {
			if (!f.fallen() && f.seat().distSqr(at) <= (double)Clearing.WATCH * Clearing.WATCH) {
				return true;
			}
		}
		return false;
	}

	private static void trap(ServerLevel level, StructureStart start) {
		RandomSource random = RandomSource.create(start.getBoundingBox().getCenter().asLong() ^ level.getSeed());
		List<BoundingBox> boxes = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (!Seats.name(piece).contains("/terrain")) {
				boxes.add(piece.getBoundingBox());
			}
		}
		if (boxes.isEmpty()) {
			return;
		}
		int wanted = Math.min(MOST, 1 + boxes.size() / PIECES_PER);
		List<Site> sites = new ArrayList<>(level.getAttachedOrElse(SITES, List.<Site>of()));
		for (int n = 0; n < wanted; n++) {
			BoundingBox box = boxes.get(random.nextInt(boxes.size()));
			Site site = switch (random.nextInt(3)) {
				case 0 -> arrowSlit(level, box, random);
				case 1 -> {
					weakFloor(level, box, random);
					yield null;
				}
				default -> barredDoor(level, box, random);
			};
			if (site != null) {
				sites.add(site);
			}
		}
		level.setAttached(SITES, List.copyOf(sites));
	}

	/** A floor spot in {@code box}: open air over a sturdy floor with two clear above, in a made chunk. */
	private static @Nullable BlockPos spot(ServerLevel level, BoundingBox box, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			int x = box.minX() + random.nextInt(box.getXSpan()), z = box.minZ() + random.nextInt(box.getZSpan());
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			for (int y = box.minY() + 1; y <= box.maxY(); y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
					&& !level.canSeeSky(p)) {
					return p;
				}
			}
		}
		return null;
	}

	private static @Nullable Site arrowSlit(ServerLevel level, BoundingBox box, RandomSource random) {
		BlockPos plate = spot(level, box, random);
		if (plate == null) {
			return null;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dist = 2; dist <= SLIT_REACH; dist++) {
				BlockPos eye = plate.relative(d, dist).above();
				if (!level.getBlockState(eye).isAir()) {
					if (dist >= 3 && level.getBlockState(eye).isSolidRender()) {
						level.setBlock(plate, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), Block.UPDATE_ALL);
						level.setBlock(eye, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
						return new Site(ARROW, plate.asLong(), eye.asLong());
					}
					break;
				}
			}
		}
		return null;
	}

	/** Four boards of rotten floor over a pit three deep: always a trap, never watched (the boards see to themselves). */
	private static boolean weakFloor(ServerLevel level, BoundingBox box, RandomSource random) {
		BlockPos at = spot(level, box, random);
		if (at == null) {
			return false;
		}
		BlockPos floor = at.below();
		List<BlockPos> boards = new ArrayList<>();
		for (BlockPos p : new BlockPos[] {floor, floor.east(), floor.south(), floor.south().east()}) {
			BlockState s = level.getBlockState(p);
			if (!s.isFaceSturdy(level, p, Direction.UP) || !level.getBlockState(p.above()).isAir()
				|| !level.getBlockState(p.below(4)).isFaceSturdy(level, p.below(4), Direction.UP)) {
				return false;
			}
			boards.add(p);
		}
		for (BlockPos p : boards) {
			level.setBlock(p, Remains.WEAK_FLOOR.defaultBlockState(), Block.UPDATE_ALL);
			for (int dy = 1; dy <= 3; dy++) {
				level.setBlock(p.below(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		return true;
	}

	private static @Nullable Site barredDoor(ServerLevel level, BoundingBox box, RandomSource random) {
		int scanned = 0;
		for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 8); y++) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					if (++scanned > DOOR_SCAN) {
						return null;
					}
					BlockPos p = new BlockPos(x, y, z);
					if (!Loaded.made(level, p) || !level.getBlockState(p).is(BlockTags.DOORS)) {
						continue;
					}
					BlockState door = level.getBlockState(p);
					if (!door.hasProperty(net.minecraft.world.level.block.DoorBlock.HALF) || door.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
						!= net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
						continue;
					}
					Direction facing = door.getValue(net.minecraft.world.level.block.DoorBlock.FACING);
					for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
						BlockPos plate = p.relative(side, 2);
						if (level.getBlockState(plate).isAir() && level.getBlockState(plate.below()).isFaceSturdy(level, plate.below(), Direction.UP)
							&& box.isInside(plate)) {
							level.setBlock(plate, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), Block.UPDATE_ALL);
							return new Site(DOOR, plate.asLong(), p.asLong());
						}
					}
				}
			}
		}
		return null;
	}

	// ---- springing -----------------------------------------------------------------------------------------------------------

	/** A plate stood on springs its trap, once; a plate that is gone takes its trap with it. */
	private static void watch(ServerLevel level, List<Site> sites) {
		List<Site> kept = new ArrayList<>(sites);
		boolean changed = false;
		for (Site site : sites) {
			BlockPos plate = BlockPos.of(site.plate());
			if (!Loaded.made(level, plate)) {
				continue;
			}
			BlockState state = level.getBlockState(plate);
			if (!state.is(Blocks.STONE_PRESSURE_PLATE)) {
				kept.remove(site);
				changed = true;
			} else if (state.getValue(BlockStateProperties.POWERED)) {
				spring(level, site);
				kept.remove(site);
				changed = true;
			}
		}
		if (changed) {
			level.setAttached(SITES, List.copyOf(kept));
		}
	}

	private static void spring(ServerLevel level, Site site) {
		BlockPos plate = BlockPos.of(site.plate()), source = BlockPos.of(site.source());
		if (site.kind() == ARROW) {
			Vec3 from = Vec3.atCenterOf(source);
			Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
			Vec3 to = Vec3.atCenterOf(plate).add(0.0, 1.0, 0.0).subtract(from);
			arrow.shoot(to.x, to.y, to.z, 1.8F, 0.5F);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			level.addFreshEntity(arrow);
			level.playSound(null, source, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 1.0F);
		} else {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos part = source.above(dy);
				if (level.getBlockState(part).is(BlockTags.DOORS)) {
					level.setBlock(part, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			level.playSound(null, source, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
		}
	}
}
