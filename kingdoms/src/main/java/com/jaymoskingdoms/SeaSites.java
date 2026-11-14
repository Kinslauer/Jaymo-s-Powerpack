package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * <li>the <b>small ship</b>: fishers, one of whom sells the catch;</li>
 * <li>the <b>travel ship</b>: a skipper and passengers;</li>
 * <li>the <b>wanderer raft</b>: one drifter, for hire like any other ({@link Drifters#adrift}), who never moves along.</li>
 * </ul>
 * The long ships, the hauling ship and the small ship also get a ship's helm ({@link #helm}), which makes each a ship a
 * player can take, sail, and draw plans of.
 */
final class SeaSites {
	static final String MOD = "mos", PREFIX = "mos/";
	static final String LONG_SHIP = PREFIX + "long_ship", HAULER_SHIP = PREFIX + "hauling_ship_1", SMALL_SHIP = PREFIX + "ship_small",
		TRAVEL_SHIP = PREFIX + "rare_travel_ship", RAFT = PREFIX + "wanderer_raft";
	/** The peaceful folk's roles (MCA villagers of no settlement). */
	static final int SKIPPER = 120, HAULER = 121, FISHER = 122, PASSENGER = 123;
	/** How far each keeps from where they were put (a deck is small). */
	static final int ROAM = 5;
	/** The most deck spots looked for, and how far apart they are kept (blocks). */
	static final int SPOTS = 10, APART = 2;
	/** A helm goes within this share of her length from one end. */
	static final float HELM_END = 0.3F;
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

	/** Whether the boat at {@code path} is one a helm goes on: a long ship, the hauling ship or the small ship. */
	static boolean sails(String path) {
		return path.equals(LONG_SHIP) || path.equals(HAULER_SHIP) || path.equals(SMALL_SHIP);
	}

	/**
	 * Sets a ship's helm ({@link com.jaymoskingdoms.sailing.Sailing#HELM}) on the deck of the boat in {@code box}, so she is
	 * a ship of blocks like any a player builds: cast off from the helm, or draw her with Ship's Plans and build more of
	 * her from the kit. Nothing of hers is copied anywhere; she is Moog's own boat where the mod floated her. The helm
	 * goes on her middle line within {@link #HELM_END} of one end of her length (the end with deck to stand it on),
	 * facing the other, which is her bow from then on. False if she has no such deck.
	 */
	static boolean helm(ServerLevel level, BoundingBox box) {
		boolean alongX = box.getXSpan() >= box.getZSpan();
		int length = alongX ? box.getXSpan() : box.getZSpan();
		int lo = alongX ? box.minX() : box.minZ(), hi = alongX ? box.maxX() : box.maxZ();
		int mid = alongX ? (box.minZ() + box.maxZ()) / 2 : (box.minX() + box.maxX()) / 2;
		int sea = level.getSeaLevel(), reach = Math.max(2, (int) (length * HELM_END));
		for (boolean fromLo : new boolean[] {true, false}) {
			for (int step = 1; step <= reach; step++) {
				int along = fromLo ? lo + step : hi - step;
				for (int off : new int[] {0, 1, -1}) {
					for (int y = sea + 1; y <= Math.min(box.maxY(), sea + 8); y++) {
						BlockPos at = alongX ? new BlockPos(along, y, mid + off) : new BlockPos(mid + off, y, along);
						BlockPos under = at.below();
						if (clear(level, at) && clear(level, at.above()) && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)
							&& level.getFluidState(under).isEmpty()) {
							Direction bow = alongX ? (fromLo ? Direction.EAST : Direction.WEST) : (fromLo ? Direction.SOUTH : Direction.NORTH);
							level.setBlockAndUpdate(at, com.jaymoskingdoms.sailing.Sailing.HELM.defaultBlockState()
								.setValue(com.jaymoskingdoms.sailing.HelmBlock.FACING, bow));
							return true;
						}
					}
				}
			}
		}
		return false;
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
			case SMALL_SHIP -> {
				trader(level, first, VillagerProfession.FISHERMAN);
				crew(level, spots, 1, 2, FISHER);
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
