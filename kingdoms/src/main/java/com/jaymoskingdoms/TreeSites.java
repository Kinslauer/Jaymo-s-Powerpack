package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Who lives at the tree structures (village mods patch 6, row rc_trees; drawn by {@code tools/small_designs/trees.py}).
 * Each stands in the world empty, as a design; when a player comes within {@link #LOOK} chunks of one that is made and
 * loaded, it is looked at once, for good (the start chunks seen are kept in {@link #VISITED}), and someone moves in:
 * <ul>
 * <li>The hollow oak ({@code smalls/hollow_oak}): a villager of no work, by the lectern in the room. {@link Hermits} makes
 * them the oak's hermit when it next looks at where they stand ({@code oak_hermitage}).</li>
 * <li>The spruce lookout ({@code smalls/spruce_lookout}): bandits. If a road bandits' camp stands within
 * {@link #CAMP_RANGE} blocks, {@link #WATCHERS} of its men are posted on the platform; if none does, a camp is founded
 * {@link #CAMP_OFFSET} blocks off (where one may be: {@link Camps#open}) and its men post; if neither can be, the
 * platform stands empty.</li>
 * <li>The hanging tree ({@code smalls/hanging_tree}): the sign gets its words, and one or two {@link Crow}s sit on the
 * bough over the chain.</li>
 * </ul>
 * The structures are found where the world placed them, by their marks in the placed blocks (the jigsaw turns each piece a
 * random quarter, so no fixed offsets): the lectern, the platform's chest, the skeleton skull, the chain, the sign.
 */
public final class TreeSites {
	// ---- tuning ----
	/** Looked for this often (ticks), this many chunks round each player. */
	static final int EVERY = 200, LOOK = 5;
	/** A road bandits' camp this near (blocks) the lookout's tree mans it; how many of its men stand on the platform. */
	static final double CAMP_RANGE = 128.0;
	static final int WATCHERS = 2;
	/** How far (blocks) off a new camp is founded, and how far a watcher strays from the spot posted. */
	static final int CAMP_OFFSET = 28, WATCH_RADIUS = 2;
	/** Crows on the bough: at least one, and a second with this chance. */
	static final float SECOND_CROW = 0.5F;
	/** Lines the hanging list has: variants of four lines each ({@code sign.jaymoskingdoms.gibbet.<variant>.<line>}). */
	static final int LIST_VARIANTS = 3;

	static final TagKey<Structure> LOOKOUTS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("spruce_lookouts"));
	static final TagKey<Structure> GIBBETS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("hanging_trees"));

	private enum Kind {
		OAK, LOOKOUT, GIBBET
	}

	private static final AttachmentType<List<Long>> VISITED = AttachmentRegistry.create(JaymosKingdoms.id("tree_sites_visited"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static Set<Long> visited;

	private TreeSites() {
	}

	/** Registers the crow and starts the looking. */
	static void register() {
		Crow.register();
		ServerTickEvents.END_SERVER_TICK.register(TreeSites::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> visited = null);
	}

	private static Map<Structure, Kind> kinds(ServerLevel level) {
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Map<Structure, Kind> kinds = new HashMap<>();
		for (var entry : Map.of(Kind.OAK, Hermits.OAK, Kind.LOOKOUT, LOOKOUTS, Kind.GIBBET, GIBBETS).entrySet()) {
			registry.get(entry.getValue()).ifPresent(set -> set.stream().forEach(holder -> kinds.put(holder.value(), entry.getKey())));
		}
		return kinds;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		Map<Structure, Kind> kinds = null;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			if (kinds == null) {
				kinds = kinds(level);
			}
			if (kinds.isEmpty()) {
				return;
			}
			try {
				look(level, player, kinds);
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("tree site scan failed", e);
			}
		}
	}

	/** The tree sites round {@code player} not seen yet, made and loaded all over: someone moves into each. */
	private static void look(ServerLevel level, ServerPlayer player, Map<Structure, Kind> kinds) {
		if (visited == null) {
			visited = new HashSet<>(level.getAttachedOrElse(VISITED, List.<Long>of()));
		}
		int px = player.blockPosition().getX() >> 4, pz = player.blockPosition().getZ() >> 4;
		Set<Long> seen = new HashSet<>();
		for (int cx = px - LOOK; cx <= px + LOOK; cx++) {
			for (int cz = pz - LOOK; cz <= pz + LOOK; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
					Kind kind = kinds.get(ref.getKey());
					if (kind == null) {
						continue;
					}
					for (long key : ref.getValue()) {
						if (visited.contains(key) || !seen.add(key)) {
							continue;
						}
						ChunkPos at = ChunkPos.unpack(key);
						LevelChunk startChunk = level.getChunkSource().getChunkNow(at.x(), at.z());
						StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(ref.getKey());
						if (start == null || !start.isValid() || !made(level, start.getBoundingBox())) {
							continue;
						}
						if (moveIn(level, start, kind)) {
							visited.add(key);
							level.setAttached(VISITED, new ArrayList<>(visited));
						}
					}
				}
			}
		}
	}

	/** Every chunk under {@code box} is made and loaded. */
	private static boolean made(ServerLevel level, BoundingBox box) {
		return Loaded.made(level, box.minX(), box.minZ(), box.maxX(), box.maxZ());
	}

	/** True once the site is dealt with (nobody more will come); false to look again later (something not loaded, or a player too near). */
	private static boolean moveIn(ServerLevel level, StructureStart start, Kind kind) {
		return switch (kind) {
			case OAK -> hermit(level, start);
			case LOOKOUT -> lookout(level, start);
			case GIBBET -> gibbet(level, start);
		};
	}

	// ---- the marks ----

	private static List<BlockPos> all(ServerLevel level, BoundingBox box, Predicate<BlockState> test) {
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (test.test(level.getBlockState(p))) {
				found.add(p.immutable());
			}
		}
		return found;
	}

	private static @Nullable BlockPos find(ServerLevel level, BoundingBox box, Predicate<BlockState> test) {
		List<BlockPos> found = all(level, box, test);
		return found.isEmpty() ? null : found.get(0);
	}

	/** Whether one can stand at {@code p}: a firm floor under it and room for a body, dry. */
	private static boolean standable(ServerLevel level, BlockPos p) {
		BlockPos below = p.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	/** Up to {@code count} spots to stand on, level with {@code mark} and nearest it (not the mark's own). */
	private static List<BlockPos> spotsNear(ServerLevel level, BlockPos mark, int count) {
		List<BlockPos> spots = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos p = mark.offset(dx, 0, dz);
				if ((dx != 0 || dz != 0) && standable(level, p)) {
					spots.add(p);
				}
			}
		}
		spots.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(mark)));
		return spots.subList(0, Math.min(count, spots.size()));
	}

	// ---- the hollow oak ----

	private static boolean hermit(ServerLevel level, StructureStart start) {
		BlockPos lectern = find(level, start.getBoundingBox(), s -> s.is(Blocks.LECTERN));
		List<BlockPos> spots = lectern == null ? List.of() : spotsNear(level, lectern, 1);
		if (spots.isEmpty()) {
			// (a room that isn't there any more: nobody to move in)
			return true;
		}
		BlockPos at = spots.get(0);
		if (!level.isPositionEntityTicking(at)) {
			return false;
		}
		// (a villager of no work, as the sky and sea hermits are found: Hermits makes them its hermit when it next looks)
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NITWIT).value();
		Villager hermit = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), job, false);
		if (hermit == null) {
			return false;
		}
		hermit.setPersistenceRequired();
		hermit.setHomeTo(at, Hermits.HOME_RADIUS);
		JaymosKingdoms.LOGGER.debug("A hermit moved into the hollow oak at {}", at);
		return true;
	}

	// ---- the spruce lookout ----

	private static boolean lookout(ServerLevel level, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		BlockPos chest = find(level, box, s -> s.is(Blocks.CHEST));
		List<BlockPos> spots = chest == null ? List.of() : spotsNear(level, chest, WATCHERS);
		if (spots.isEmpty()) {
			return true;
		}
		MinecraftServer server = level.getServer();
		// (the tree's foot: its middle at the height of its ground, not of its crown)
		BlockPos foot = new BlockPos(box.getCenter().getX(), box.minY() + 3, box.getCenter().getZ());
		Faction camp = nearestCamp(server, foot);
		if (camp != null) {
			return post(level, camp, spots);
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			// (no band pitches a camp on Peaceful, as BanditCamps.trySpawn keeps it: looked at again once it isn't)
			return false;
		}
		// (no camp near: one is founded off to the side, where a camp may be, and its men man the tree)
		boolean waiting = false;
		int reach = CampBuildings.CLEAR_RADIUS + 1;
		for (int k = 0; k < 8; k++) {
			double angle = k * Math.PI / 4.0;
			BlockPos flat = foot.offset((int) Math.round(Math.cos(angle) * CAMP_OFFSET), 0, (int) Math.round(Math.sin(angle) * CAMP_OFFSET));
			if (!Loaded.made(level, flat.getX() - reach, flat.getZ() - reach, flat.getX() + reach, flat.getZ() + reach)) {
				waiting = true;
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat);
			if (!level.isPositionEntityTicking(at) || Raids.playerNear(level, at, Camps.HIDE_RANGE)) {
				waiting = true;
				continue;
			}
			if (!Camps.open(server, level, at)) {
				continue;
			}
			int floor = CampBuildings.floor(level, at, Camps.MAX_SPREAD);
			if (floor == Integer.MIN_VALUE) {
				continue;
			}
			Direction gate = Direction.getApproximateNearest(foot.getX() - at.getX(), 0, foot.getZ() - at.getZ());
			Faction founded = BanditCamps.found(level, at, floor, gate);
			return post(level, founded, spots);
		}
		// (nowhere to found one: empty, unless the places wait on a player's leaving)
		return !waiting;
	}

	/** The nearest standing road bandits' camp within {@link #CAMP_RANGE} of {@code at}, or null. */
	private static @Nullable Faction nearestCamp(MinecraftServer server, BlockPos at) {
		Faction best = null;
		double bestDistance = CAMP_RANGE * CAMP_RANGE;
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			double d = Raids.horizontal(camp.seat(), at);
			if (!camp.fallen() && BanditCamps.is(server, camp.id()) && d <= bestDistance) {
				best = camp;
				bestDistance = d;
			}
		}
		return best;
	}

	/** {@link #WATCHERS} more of {@code camp}'s bandits, each on one of {@code spots}; false if its seat isn't there to hire at yet. */
	private static boolean post(ServerLevel level, Faction camp, List<BlockPos> spots) {
		BlockPos seat = camp.seat();
		if (!Loaded.made(level, seat) || !level.isPositionEntityTicking(seat)) {
			return false;
		}
		for (BlockPos spot : spots) {
			Faction now = Factions.get(level.getServer(), camp.id());
			Folk bandit = now == null ? null : KingdomEvents.spawn(level, now, Folk.BARBARIAN);
			if (bandit == null) {
				continue;
			}
			IllagerSites.dress(bandit, camp.id(), true);
			bandit.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			bandit.setHomeTo(spot, WATCH_RADIUS);
			bandit.setPersistenceRequired();
		}
		JaymosKingdoms.LOGGER.debug("Bandits took the spruce lookout for {}", camp.name());
		return true;
	}

	// ---- the hanging tree ----

	private static boolean gibbet(ServerLevel level, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		BlockPos skull = find(level, box, s -> s.is(Blocks.SKELETON_SKULL));
		BlockPos chain = null;
		for (BlockPos p : all(level, box, s -> s.is(Blocks.IRON_CHAIN))) {
			if (chain == null || p.getY() > chain.getY()) {
				chain = p;
			}
		}
		if (skull == null || chain == null) {
			return true;
		}
		BlockPos bough = chain.above();
		BlockPos perch = bough.above();
		if (!level.isPositionEntityTicking(perch)) {
			return false;
		}
		list(level, box, (int) Math.floorMod(start.getChunkPos().pack(), (long) LIST_VARIANTS));
		List<BlockPos> perches = new ArrayList<>(List.of(perch));
		for (Direction d : new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
			BlockPos along = bough.relative(d);
			if (level.getBlockState(along).is(BlockTags.LOGS) && level.getBlockState(along.above()).isAir()) {
				perches.add(along.above());
				break;
			}
		}
		int crows = perches.size() > 1 && level.getRandom().nextFloat() < SECOND_CROW ? 2 : 1;
		for (int i = 0; i < crows; i++) {
			Crow crow = Crow.TYPE.create(level, EntitySpawnReason.STRUCTURE);
			if (crow == null) {
				continue;
			}
			BlockPos on = perches.get(i);
			crow.snapTo(on.getX() + 0.5, on.getY(), on.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			crow.settle(skull, on);
			level.addFreshEntity(crow);
		}
		return true;
	}

	/** The gallows post's sign: the Sun's hanging list (one of {@link #LIST_VARIANTS} wordings), sealed. */
	private static void list(ServerLevel level, BoundingBox box, int variant) {
		for (BlockPos at : all(level, box, s -> s.is(BlockTags.STANDING_SIGNS))) {
			if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
				List<Component> lines = new ArrayList<>();
				for (int line = 0; line < 4; line++) {
					Component text = Component.translatable("sign.jaymoskingdoms.gibbet." + variant + "." + line);
					lines.add(line == 0 ? text.copy().withStyle(ChatFormatting.DARK_RED) : text);
				}
				sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
				sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.BACK);
				sign.setWaxed(true);
				sign.setChanged();
				BlockState state = level.getBlockState(at);
				level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
			}
		}
	}
}
