package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Who is aboard the boats Moog's Ocean Structures floats (LGPL-3.0, by FinnDog; the mod is shipped unchanged and builds
 * them itself; without it nothing here happens). {@link IllagerSites} finds them and moves people in as it does for its
 * own places ({@link IllagerSites#PLANS}, paths under {@link #PREFIX}); this class says where on a deck they stand and
 * who the peaceful ones are:
 * <ul>
 * <li>the <b>long ships</b>: barbarians, who hold their ship and never raid from it (a camp of {@link IllagerSites});</li>
 * <li>the <b>hauling ship</b>: a skipper who trades (a villager with a trade) and his haulers;</li>
 * <li>the <b>small ship</b>: a realm's fishing smack, cast off with its fishermen aboard ({@link #smack});</li>
 * <li>the <b>travel ship</b>: a skipper and passengers;</li>
 * <li>the <b>wanderer raft</b>: one drifter, for hire like any other ({@link Drifters#adrift}), who never moves along.</li>
 * </ul>
 * Every boat with a crew (all but the raft) also gets a ship's helm ({@link #helm}), so whoever skippers her has a wheel
 * to steer by, and a player can take her, sail her, and draw plans of her. Andy (2026-11-26), spawned on a long ship that
 * had none: "all ships should at this point". So she's rigged the moment the last of her chunks is made ({@link #rig}),
 * not when her people move in: the helm, and on a long ship her guns in the rails ({@link #guns}), each with a barrel of
 * powder and round shot behind its breech that her pillagers load it from ({@link DeckGuns}).
 */
final class SeaSites {
	static final String MOD = "mos", PREFIX = "mos/";
	static final String LONG_SHIP = PREFIX + "long_ship", HAULER_SHIP = PREFIX + "hauling_ship_1", SMALL_SHIP = PREFIX + "ship_small",
		TRAVEL_SHIP = PREFIX + "rare_travel_ship", RAFT = PREFIX + "wanderer_raft";
	/** The peaceful folk's roles (MCA villagers of no settlement). */
	static final int SKIPPER = 120, HAULER = 121, FISHER = 122, PASSENGER = 123;
	/** A small ship's site once she's a realm's smack ({@link #smack}): sailed away, or left empty. */
	static final String AWAY = "fishers", MOORED = "moored";
	/** How far each keeps from where they were put (a deck is small). */
	static final int ROAM = 5;
	/** The most deck spots looked for, and how far apart they are kept (blocks). */
	static final int SPOTS = 10, APART = 2;
	/** A helm goes within this share of her length from one end. */
	static final float HELM_END = 0.3F;
	/**
	 * A long ship's guns: this many a side, at these shares of the stretch her rails run (the nearest place in them that
	 * takes one, never nearer another than this), and the powder and round shot in the barrel behind each one's breech.
	 */
	static final int GUNS_A_SIDE = 3, GUN_GAP = 4, GUN_POWDER = 8, GUN_SHOT = 8;
	static final float[] GUN_AT = {0.5F, 0.1F, 0.9F};

	/** What one of Moog's boats was rigged with when she was made: her helm (none: no deck for one) and her guns (packed). */
	record Rig(Optional<BlockPos> helm, List<Long> guns) {
		static final Codec<Rig> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.optionalFieldOf("helm").forGetter(Rig::helm),
				Codec.LONG.listOf().optionalFieldOf("guns", List.of()).forGetter(Rig::guns))
			.apply(i, Rig::new));
	}

	/** On the Overworld: every boat rigged so far (her start chunk, packed → what she got). Saved, so none is rigged twice. */
	static final AttachmentType<Map<String, Rig>> RIGGED = AttachmentRegistry.create(JaymosKingdoms.id("sea_sites_rigged"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Rig.CODEC)));
	/** Boats whose chunks were loaded this tick (start chunk, packed → structure), looked at at the tick's end. */
	private static final Map<Long, Structure> TO_RIG = new HashMap<>();

	/** What a hauling ship's skipper might deal in. */
	private static final List<ResourceKey<VillagerProfession>> GOODS = List.of(VillagerProfession.FARMER, VillagerProfession.MASON,
		VillagerProfession.SHEPHERD, VillagerProfession.LEATHERWORKER, VillagerProfession.TOOLSMITH, VillagerProfession.CARTOGRAPHER);

	private SeaSites() {
	}

	static void register() {
		for (int role : new int[] {SKIPPER, HAULER, FISHER, PASSENGER}) {
			Folk.defineRole(new Folk.Role(role, roleName(role), false, false, null, (folk, faction) -> {
			}));
		}
		// a boat is rigged as soon as all of her is made: each of her chunks, as it loads, has her looked at
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level != Factions.level(level.getServer())) {
				return;
			}
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
				Identifier id = registry.getKey(e.getKey());
				String path = id == null ? null : path(id);
				if (path != null && rigged(path)) {
					for (long ref : e.getValue()) {
						TO_RIG.put(ref, e.getKey());
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TO_RIG.isEmpty()) {
				return;
			}
			ServerLevel level = Factions.level(server);
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			Map<Long, Structure> due = Map.copyOf(TO_RIG);
			TO_RIG.clear();
			Map<String, Rig> done = rigs(server);
			for (Map.Entry<Long, Structure> e : due.entrySet()) {
				if (done.containsKey(Long.toString(e.getKey()))) {
					continue;
				}
				net.minecraft.world.level.ChunkPos from = net.minecraft.world.level.ChunkPos.unpack(e.getKey());
				ChunkAccess startChunk = level.getChunkSource().getChunkNow(from.x(), from.z());
				StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(e.getValue());
				Identifier id = registry.getKey(e.getValue());
				String path = id == null ? null : path(id);
				if (start == null || !start.isValid() || path == null) {
					continue;
				}
				BoundingBox box = start.getBoundingBox();
				// (not yet all made: the last of her chunks to load looks again)
				if (Loaded.made(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
					rig(level, e.getKey(), path, box);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TO_RIG.clear());
		DeckGuns.register();
	}

	/** Whether the boat at {@code path} is rigged when she's made: one a helm goes on ({@link #sails}). */
	static boolean rigged(String path) {
		return sails(path);
	}

	static Map<String, Rig> rigs(net.minecraft.server.MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(RIGGED, Map.of());
	}

	/**
	 * The boat at {@code path} in {@code box}, started in the chunk {@code ref}, rigged (once: what she got the first time
	 * after that): her helm ({@link #helm}), and a long ship's guns ({@link #guns}).
	 */
	static Rig rig(ServerLevel level, long ref, String path, BoundingBox box) {
		Map<String, Rig> all = rigs(level.getServer());
		Rig had = all.get(Long.toString(ref));
		if (had != null) {
			return had;
		}
		BlockPos helm = sails(path) ? helm(level, box) : null;
		List<Long> guns = path.equals(LONG_SHIP) ? guns(level, box) : List.of();
		Rig rig = new Rig(Optional.ofNullable(helm), guns);
		Map<String, Rig> next = new HashMap<>(all);
		next.put(Long.toString(ref), rig);
		Factions.level(level.getServer()).setAttached(RIGGED, Map.copyOf(next));
		JaymosKingdoms.LOGGER.info("Rigged the {} at {}: helm {}, {} guns", path, box.getCenter().toShortString(),
			helm == null ? "none" : helm.toShortString(), guns.size());
		return rig;
	}

	/** Her helm as rigged, if it still stands there; else one set now ({@link #helm}), or null. */
	static @Nullable BlockPos helmOf(ServerLevel level, long ref, String path, BoundingBox box) {
		BlockPos at = rig(level, ref, path, box).helm().orElse(null);
		if (at != null && level.getBlockState(at).is(com.jaymoskingdoms.sailing.Sailing.HELM)) {
			return at;
		}
		return helm(level, box);
	}

	/**
	 * A long ship's guns, in her rails. Her main deck is the floor most of her middle line stands at ({@link #floor});
	 * every place along her where her rail meets that deck ({@link #railAt}) could take one, and {@link #GUNS_A_SIDE} a
	 * side go in, spread along her waist (at {@link #GUN_AT} of the stretch her rails run, never nearer each other than
	 * {@link #GUN_GAP}): a ship's cannon set in the rail, facing outboard over open water, and a barrel of
	 * {@link #GUN_POWDER} powder and {@link #GUN_SHOT} round shot on the deck behind its breech, which is what she's loaded
	 * from (real powder and shot: when it's spent, she's quiet, and whoever takes her has what's left). Where they went (packed).
	 */
	static List<Long> guns(ServerLevel level, BoundingBox box) {
		boolean alongX = box.getXSpan() >= box.getZSpan();
		int lo = alongX ? box.minX() : box.minZ(), hi = alongX ? box.maxX() : box.maxZ();
		int mid = alongX ? (box.minZ() + box.maxZ()) / 2 : (box.minX() + box.maxX()) / 2;
		int half = (alongX ? box.getZSpan() : box.getXSpan()) / 2;
		int top = Math.min(box.maxY(), level.getSeaLevel() + 12);
		// her main deck
		Map<Integer, Integer> floors = new HashMap<>();
		for (int along = lo; along <= hi; along++) {
			for (int off = -1; off <= 1; off++) {
				int f = floor(level, alongX ? new BlockPos(along, top, mid + off) : new BlockPos(mid + off, top, along), level.getSeaLevel());
				if (f != Integer.MIN_VALUE) {
					floors.merge(f, 1, Integer::sum);
				}
			}
		}
		int deck = floors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Integer.MIN_VALUE);
		List<Long> out = new ArrayList<>();
		if (deck == Integer.MIN_VALUE) {
			return out;
		}
		for (int side : new int[] {1, -1}) {
			Direction way = alongX ? (side > 0 ? Direction.SOUTH : Direction.NORTH) : (side > 0 ? Direction.EAST : Direction.WEST);
			List<BlockPos[]> rails = new ArrayList<>();
			for (int along = lo; along <= hi; along++) {
				BlockPos[] r = railAt(level, alongX, along, mid, half, side, way, top, deck);
				if (r != null) {
					rails.add(r);
				}
			}
			if (rails.isEmpty()) {
				continue;
			}
			int first = coord(rails.get(0)[0], alongX), last = coord(rails.get(rails.size() - 1)[0], alongX);
			List<BlockPos[]> picked = new ArrayList<>();
			for (float share : GUN_AT) {
				int want = first + Math.round((last - first) * share);
				BlockPos[] best = null;
				for (BlockPos[] r : rails) {
					int at = coord(r[0], alongX);
					boolean near = false;
					for (BlockPos[] p : picked) {
						near |= Math.abs(coord(p[0], alongX) - at) < GUN_GAP;
					}
					if (!near && (best == null || Math.abs(at - want) < Math.abs(coord(best[0], alongX) - want))) {
						best = r;
					}
				}
				if (best != null && picked.size() < GUNS_A_SIDE) {
					picked.add(best);
				}
			}
			for (BlockPos[] r : picked) {
				level.setBlockAndUpdate(r[0], com.jaymoskingdoms.sailing.Sailing.CANNON.defaultBlockState()
					.setValue(com.jaymoskingdoms.sailing.ShipCannonBlock.FACING, way));
				level.setBlockAndUpdate(r[1], Blocks.BARREL.defaultBlockState());
				if (level.getBlockEntity(r[1]) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
					barrel.setItem(0, new ItemStack(Items.GUNPOWDER, GUN_POWDER));
					barrel.setItem(1, new ItemStack(com.jaymoskingdoms.sailing.Sailing.ROUND_SHOT, GUN_SHOT));
					barrel.setChanged();
				}
				out.add(r[0].asLong());
			}
		}
		return out;
	}

	private static int coord(BlockPos at, boolean alongX) {
		return alongX ? at.getX() : at.getZ();
	}

	/**
	 * Her rail across her at {@code along}, on {@code side}: walking out from her middle line over her main deck (the
	 * columns open from above down to a floor at {@code deck}), the first column that isn't. Its block at deck height must
	 * be a rail a gun can take the place of (solid and waist high or more, not a chest, a gate, a door or a hatch), her
	 * side under it, clear air beyond it; the deck just inboard of it takes the barrel. {gun, barrel}, or null.
	 */
	private static BlockPos @Nullable [] railAt(ServerLevel level, boolean alongX, int along, int mid, int half, int side, Direction out, int top,
		int deck) {
		BlockPos inboard = null;
		for (int off = 0; off <= half + 1; off++) {
			int across = mid + side * off;
			BlockPos column = alongX ? new BlockPos(along, top, across) : new BlockPos(across, top, along);
			int floor = floor(level, column, level.getSeaLevel());
			if (inboard == null) {
				if (floor == deck && off <= 3) {
					inboard = column.atY(deck);
				}
				if (off >= 3 && inboard == null) {
					return null;
				}
				continue;
			}
			if (floor == deck) {
				inboard = column.atY(deck);
				continue;
			}
			BlockPos rail = column.atY(deck);
			BlockPos beyond = rail.relative(out);
			BlockState there = level.getBlockState(rail);
			net.minecraft.world.phys.shapes.VoxelShape shape = there.getCollisionShape(level, rail);
			if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.45 || there.canBeReplaced() || level.getBlockEntity(rail) instanceof RandomizableContainerBlockEntity
				|| there.is(net.minecraft.tags.BlockTags.FENCE_GATES) || there.is(net.minecraft.tags.BlockTags.DOORS)
				|| there.is(net.minecraft.tags.BlockTags.TRAPDOORS) || there.is(com.jaymoskingdoms.sailing.Sailing.HELM)
				|| there.is(com.jaymoskingdoms.sailing.Sailing.CANNON)
				|| level.getBlockState(rail.below()).getCollisionShape(level, rail.below()).isEmpty()
				|| !clear(level, beyond) || !clear(level, beyond.above())) {
				return null;
			}
			return new BlockPos[] {rail, inboard};
		}
		return null;
	}

	/**
	 * The floor of the column {@code column} (its x and z; its y the top looked from): the height of the first place,
	 * coming down through clear air, with something solid under it, dry, above the sea; MIN_VALUE if there's none.
	 */
	private static int floor(ServerLevel level, BlockPos column, int sea) {
		for (int y = column.getY(); y > sea; y--) {
			BlockPos at = column.atY(y);
			if (!clear(level, at)) {
				return Integer.MIN_VALUE;
			}
			BlockPos under = at.below();
			if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
				return clear(level, at.above()) && level.getFluidState(under).isEmpty() ? y : Integer.MIN_VALUE;
			}
		}
		return Integer.MIN_VALUE;
	}

	static String roleName(int role) {
		return switch (role) {
			case SKIPPER -> "sea_skipper";
			case HAULER -> "sea_hauler";
			case FISHER -> "sea_fisher";
			default -> "sea_passenger";
		};
	}

	static boolean isRole(int role) {
		return role >= SKIPPER && role <= PASSENGER;
	}

	/** The plan path of one of Moog's boats we put people on (null: not one). */
	static @Nullable String path(Identifier id) {
		if (!id.getNamespace().equals(MOD)) {
			return null;
		}
		String path = PREFIX + id.getPath();
		return IllagerSites.PLANS.containsKey(path) ? path : null;
	}

	static boolean is(String path) {
		return path.startsWith(PREFIX);
	}

	/**
	 * Places to stand on the boat in {@code box}: above the waterline, something solid underfoot, two blocks of room, dry;
	 * the highest such spot of each column, spread out, at most {@link #SPOTS}.
	 */
	static List<BlockPos> deck(ServerLevel level, BoundingBox box, RandomSource random) {
		List<BlockPos> all = new ArrayList<>();
		int sea = level.getSeaLevel();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = Math.min(box.maxY(), sea + 12); y >= sea; y--) {
					BlockPos at = new BlockPos(x, y, z);
					if (!clear(level, at)) {
						// (a mast, a rail, a cabin wall: nothing under it counts as deck)
						break;
					}
					BlockPos under = at.below();
					if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
						if (clear(level, at.above()) && level.getFluidState(under).isEmpty() && y > sea) {
							all.add(at);
						}
						break;
					}
				}
			}
		}
		List<BlockPos> picked = new ArrayList<>();
		while (!all.isEmpty() && picked.size() < SPOTS) {
			BlockPos at = all.remove(random.nextInt(all.size()));
			boolean crowded = false;
			for (BlockPos p : picked) {
				crowded |= p.closerThan(at, APART);
			}
			if (!crowded) {
				picked.add(at);
			}
		}
		return picked;
	}

	/**
	 * Whether the boat at {@code path} is one a helm goes on: every one with a crew (a long ship, the hauling ship, the small
	 * ship, the travel ship). Andy (2026-11-22), meeting a ship's folk at sea: the ship "should spawn with a helm placed, so
	 * the villager i met out there has a realistic reason".
	 */
	static boolean sails(String path) {
		return path.equals(LONG_SHIP) || path.equals(HAULER_SHIP) || path.equals(SMALL_SHIP) || path.equals(TRAVEL_SHIP);
	}

	/**
	 * Sets a ship's helm ({@link com.jaymoskingdoms.sailing.Sailing#HELM}) on the deck of the boat in {@code box}, so she is
	 * a ship of blocks like any a player builds: cast off from the helm, or draw her with Ship's Plans and build more of
	 * her from the kit. Nothing of hers is copied anywhere; she is Moog's own boat where the mod floated her. The helm
	 * goes on her middle line within {@link #HELM_END} of one end of her length (the end with deck to stand it on),
	 * facing the other, which is her bow from then on; if neither end has such deck (a cabin aft, a forecastle), anywhere
	 * along her middle line, facing the further end. Where it went, or null if she has no such deck at all. Her deck is
	 * looked for as high as {@link #deck} looks (the small ship's stands nine above the sea).
	 */
	static @Nullable BlockPos helm(ServerLevel level, BoundingBox box) {
		boolean alongX = box.getXSpan() >= box.getZSpan();
		int length = alongX ? box.getXSpan() : box.getZSpan();
		int lo = alongX ? box.minX() : box.minZ(), hi = alongX ? box.maxX() : box.maxZ();
		int mid = alongX ? (box.minZ() + box.maxZ()) / 2 : (box.minX() + box.maxX()) / 2;
		int sea = level.getSeaLevel(), halfway = (lo + hi) / 2;
		for (int reach : new int[] {Math.max(2, (int) (length * HELM_END)), length - 2}) {
			for (boolean fromLo : new boolean[] {true, false}) {
				for (int step = 1; step <= reach; step++) {
					int along = fromLo ? lo + step : hi - step;
					for (int off : new int[] {0, 1, -1}) {
						for (int y = sea + 1; y <= Math.min(box.maxY(), sea + 12); y++) {
							BlockPos at = alongX ? new BlockPos(along, y, mid + off) : new BlockPos(mid + off, y, along);
							BlockPos under = at.below();
							if (clear(level, at) && clear(level, at.above()) && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)
								&& level.getFluidState(under).isEmpty()) {
								// (she sails away from the end the helm stands nearer)
								boolean aftLo = along <= halfway;
								Direction bow = alongX ? (aftLo ? Direction.EAST : Direction.WEST) : (aftLo ? Direction.SOUTH : Direction.NORTH);
								level.setBlockAndUpdate(at, com.jaymoskingdoms.sailing.Sailing.HELM.defaultBlockState()
									.setValue(com.jaymoskingdoms.sailing.HelmBlock.FACING, bow));
								return at;
							}
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Andy (2026-11-22), on a small ship met at sea with two jobless villagers aboard: "they should be fishers belonging
	 * to some land and out here to get it and go back. thats it." The small ship is a realm's fishing smack: the nearest
	 * standing realm with a harbour ({@link Fishers#harbourRealm}) owns her, a helm goes on her deck and she is cast off
	 * with its fishermen aboard, found fishing where she floats, and sails home with the catch ({@link Fishers#think}).
	 * {@link #AWAY} if she is; {@link #MOORED} if she has no deck for a helm or won't cast off (left empty, her helm
	 * there for a player to try); null, nothing done, if no realm's harbour is in reach yet or she isn't where things
	 * move (looked at again later).
	 */
	static @Nullable String smack(ServerLevel level, long ref, BoundingBox box) {
		BlockPos center = box.getCenter();
		if (!level.isPositionEntityTicking(center)) {
			return null;
		}
		Faction realm = Fishers.harbourRealm(level.getServer(), center);
		if (realm == null) {
			return null;
		}
		BlockPos helm = helmOf(level, ref, SMALL_SHIP, box);
		return helm != null && Fishers.atSea(level, realm, helm, center) != null ? AWAY : MOORED;
	}

	private static boolean clear(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getFluidState(at).isEmpty();
	}

	/** The peaceful people of the boat at {@code path}, at {@code spots} (the first is the skipper's). */
	static void people(ServerLevel level, String path, List<BlockPos> spots) {
		RandomSource random = level.getRandom();
		BlockPos first = spots.get(0);
		switch (path) {
			case RAFT -> McaCompat.adrift(level, first);
			case HAULER_SHIP -> {
				trader(level, first, GOODS.get(random.nextInt(GOODS.size())));
				crew(level, spots, 1, 3, HAULER);
			}
			default -> {
				crew(level, spots, 0, 1, SKIPPER);
				crew(level, spots, 1, 5, PASSENGER);
			}
		}
	}

	private static void crew(ServerLevel level, List<BlockPos> spots, int from, int to, int role) {
		for (int i = from; i < to; i++) {
			BlockPos at = spots.get(i % spots.size());
			PathfinderMob mob = McaCompat.stranger(level, at, level.getRandom().nextFloat() * 360.0F, role, at, ROAM);
			if (mob != null) {
				mob.setPersistenceRequired();
			}
		}
	}

	/** A villager with a trade who lives aboard. */
	private static void trader(ServerLevel level, BlockPos at, ResourceKey<VillagerProfession> job) {
		Holder<VillagerProfession> holder = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(job);
		Villager v = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), holder.value(), false);
		if (v != null) {
			v.setPersistenceRequired();
			v.setHomeTo(at, ROAM);
		}
	}

	/** The greeting of one of the sea's peaceful folk (lang {@code talk.jaymoskingdoms.sea_sites.<this>.<n>}). */
	static String greeting(int role) {
		return switch (role) {
			case SKIPPER -> "skipper";
			case HAULER -> "hauler";
			case FISHER -> "fisher";
			default -> "passenger";
		};
	}
}
