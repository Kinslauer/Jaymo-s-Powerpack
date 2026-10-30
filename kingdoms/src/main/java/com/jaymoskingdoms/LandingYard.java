package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.368, Andy's SevTech picks: "Landing Yard") A kingdom's yard for the sky-folk's pods: {@link #PADS_MIN} to
 * {@link #PADS_MAX} pads of smooth stone cornered in copper around a copper beacon post, built by its builders like any
 * other building ({@link Buildings}' growth order). Nothing to craft: a pad is a build, not a recipe.
 * <ul>
 * <li>Jaymo's Powers' Patrol and raider pods that come down within {@link #RANGE} of a yard aim for it: a patrol pod sets
 * down on a free pad, a raiders' band just outside the yard ({@link #padsNear}, asked through Powers' {@code ZFighterKingdoms}).</li>
 * <li>While a pod stands on one of a town's pads, its merchants' prices are {@link #DISCOUNT} lower
 * ({@link #tradeFactor}, used by {@link MerchantOrders}).</li>
 * <li>A patrol pod standing down names the town in the next Tidings ({@link #touched}), and a patrolman tells of it
 * ({@link #nameNear}).</li>
 * <li>It is the Pilot's and the Prospector's workplace ({@link Careers.Job#hall}).</li>
 * </ul>
 * The pads are found again from the finished building's footprint: a pad's middle block is waxed copper, the post's foot
 * waxed chiseled copper.
 */
public final class LandingYard {
	// ---- tuning ----
	public static final String ENTRY = "landing_yard";
	/** The yard's footprint, and how high its post reaches. */
	static final int WIDTH = 15, DEPTH = 19, POST_HEIGHT = 6;
	/** How many pads a yard has, at least and at most; the settlement size a kingdom needs to build one. */
	static final int PADS_MIN = 4, PADS_MAX = 9, MIN_POPULATION = 34;
	/** A yard this near (blocks) a seat or a point is the one pods aim for. */
	public static final double RANGE = 64.0;
	/** How much lower a town's merchants' prices are while a pod stands in its yard (a share). */
	static final float DISCOUNT = 0.10F;
	/** A pod this near a pad's middle (blocks, flat and up) stands on it. */
	static final double STANDS_FLAT = 2.5, STANDS_UP = 3.0;
	/** A yard is named in the news at most this often (ticks). */
	static final long NEWS_GAP = 24000L;
	/** Pad middles (x, z) in a yard's footprint, in the order they are used. */
	private static final int[][] SLOTS = {{3, 3}, {11, 3}, {3, 11}, {11, 11}, {7, 3}, {7, 11}, {3, 7}, {11, 7}, {7, 16}};
	private static final int POST_X = 7, POST_Z = 7;
	/** Tags of Powers' pods that are not standing (in flight, or a raider's). */
	private static final String[] NOT_STANDING = {"jaymospowers.patrol_pod_flying", "jaymospowers.space_pod", "jaymospowers.raider"};

	/** A finished yard: its town, its footprint, the post's foot and the standing place on each pad. */
	record Yard(Faction faction, Works.Built built, BlockPos post, List<BlockPos> pads) {
	}

	private static final Map<Integer, Long> NAMED = new HashMap<>();

	private LandingYard() {
	}

	static void register() {
		for (FactionType type : new FactionType[] {FactionType.KINGDOM, FactionType.FIEFDOM}) {
			Blueprint.register(type, new Blueprint.Entry(ENTRY, (level, faction, variant) -> draw(variant), 1, MIN_POPULATION));
		}
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NAMED.clear());
	}

	// ---- the design --------------------------------------------------------------------------------------------------------

	/** How many pads variant {@code variant} has. */
	static int padCount(long variant) {
		return PADS_MIN + (int)Math.floorMod(variant, (long)(PADS_MAX - PADS_MIN + 1));
	}

	/** The yard (15 x 19): stone-brick paving, the beacon post in the middle, and its pads (which, and how many, by the variant). */
	static Blueprint draw(long variant) {
		Buildings.Sketch s = new Buildings.Sketch(ENTRY, WIDTH, DEPTH, POST_HEIGHT + 2);
		BlockState paving = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState padStone = Blocks.SMOOTH_STONE.defaultBlockState();
		BlockState corner = Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState();
		BlockState marker = Blocks.COPPER_BLOCK.waxed().unaffected().defaultBlockState();
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				s.set(x, 0, z, paving);
			}
		}
		// the road down to the last slot
		for (int z = 12; z <= 14; z++) {
			s.set(POST_X, 0, z, Blocks.GRAVEL.defaultBlockState());
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < SLOTS.length; i++) {
			order.add(i);
		}
		Collections.shuffle(order, new Random(variant));
		int count = padCount(variant);
		for (int n = 0; n < count; n++) {
			int[] slot = SLOTS[order.get(n)];
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean edge = dx != 0 && dz != 0;
					s.set(slot[0] + dx, 0, slot[1] + dz, edge ? corner : dx == 0 && dz == 0 ? marker : padStone);
				}
			}
		}
		// the beacon post
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				s.set(POST_X + dx, 0, POST_Z + dz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			}
		}
		s.set(POST_X, 0, POST_Z, Blocks.CHISELED_COPPER.waxed().unaffected().defaultBlockState());
		BlockState pillar = Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState();
		for (int y = 1; y < POST_HEIGHT; y++) {
			s.set(POST_X, y, POST_Z, pillar);
		}
		s.set(POST_X, POST_HEIGHT, POST_Z, Blocks.LANTERN.defaultBlockState());
		s.set(POST_X, POST_HEIGHT + 1, POST_Z, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		return s.done();
	}

	// ---- finding yards -----------------------------------------------------------------------------------------------------

	/** Every standing yard of the settlements' world (their pads read off the ground). */
	static List<Yard> yards(ServerLevel level) {
		return yards(level, null, 0.0, -1);
	}

	/**
	 * Standing yards, read off the ground only when they could matter: within {@code range} (plus the footprint) of {@code from}
	 * if it is given, and of town {@code only} if that is not -1. Keeps a far town's chunks from being loaded to answer a near one.
	 */
	static List<Yard> yards(ServerLevel level, @Nullable BlockPos from, double range, int only) {
		List<Yard> out = new ArrayList<>();
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return out;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || only != -1 && f.id() != only) {
				continue;
			}
			for (Works.Built b : Works.of(server, f.id()).built()) {
				if (b.entry().equals(ENTRY) && b.ruined() == 0L) {
					if (from != null) {
						double dx = (b.min().getX() + b.max().getX()) / 2.0 - from.getX(), dz = (b.min().getZ() + b.max().getZ()) / 2.0 - from.getZ();
						double reach = range + WIDTH + DEPTH;
						if (dx * dx + dz * dz > reach * reach) {
							continue;
						}
					}
					Yard yard = read(level, f, b);
					if (yard != null) {
						out.add(yard);
					}
				}
			}
		}
		return out;
	}

	private static @Nullable Yard read(ServerLevel level, Faction f, Works.Built b) {
		BlockPos post = null;
		List<BlockPos> pads = new ArrayList<>();
		BlockState padMiddle = Blocks.COPPER_BLOCK.waxed().unaffected().defaultBlockState();
		BlockState postFoot = Blocks.CHISELED_COPPER.waxed().unaffected().defaultBlockState();
		for (int x = b.min().getX(); x <= b.max().getX(); x++) {
			for (int z = b.min().getZ(); z <= b.max().getZ(); z++) {
				level.getChunk(x >> 4, z >> 4);
				for (int y = b.min().getY(); y <= b.min().getY() + 1; y++) {
					BlockPos at = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(at);
					if (state.is(padMiddle.getBlock())) {
						pads.add(at.above());
					} else if (state.is(postFoot.getBlock())) {
						post = at;
					}
				}
			}
		}
		if (pads.isEmpty()) {
			return null;
		}
		BlockPos middle = new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY(), (b.min().getZ() + b.max().getZ()) / 2);
		return new Yard(f, b, post == null ? middle : post, List.copyOf(pads));
	}

	/** The yard nearest {@code from} that is within {@code range} (flat), or null. */
	static @Nullable Yard near(ServerLevel level, BlockPos from, double range) {
		Yard best = null;
		double bestD = range * range;
		for (Yard y : yards(level, from, range, -1)) {
			double dx = y.post().getX() - from.getX(), dz = y.post().getZ() - from.getZ();
			double d = dx * dx + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = y;
			}
		}
		return best;
	}

	/**
	 * (for Jaymo's Powers, by reflection) The yard nearest {@code from} within {@code range}: its beacon post first, then the
	 * block to stand a pod on, for each pad. Empty when there's none.
	 */
	public static List<BlockPos> padsNear(ServerLevel level, BlockPos from, double range) {
		Yard yard = near(level, from, range);
		if (yard == null) {
			return List.of();
		}
		List<BlockPos> out = new ArrayList<>();
		out.add(yard.post());
		out.addAll(yard.pads());
		return out;
	}

	/** (for Powers' patrolmen's talk) The name of the town whose yard is within {@code range} of {@code from}, or "". */
	public static String nameNear(ServerLevel level, BlockPos from, double range) {
		Yard yard = near(level, from, range);
		return yard == null ? "" : yard.faction().name();
	}

	/** (for Powers) Whether a yard stands within {@code range} of {@code from}. */
	public static boolean hasYard(ServerLevel level, BlockPos from, double range) {
		return near(level, from, range) != null;
	}

	/** {@code f} has a finished yard. */
	static boolean has(MinecraftServer server, Faction f) {
		for (Works.Built b : Works.of(server, f.id()).built()) {
			if (b.entry().equals(ENTRY) && b.ruined() == 0L) {
				return true;
			}
		}
		return false;
	}

	// ---- pods in the yard ------------------------------------------------------------------------------------------------

	private static boolean standingPod(Entity e) {
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		if (!key.getNamespace().equals("jaymospowers") || !key.getPath().equals("space_pod")) {
			return false;
		}
		for (String tag : NOT_STANDING) {
			if (e.entityTags().contains(tag)) {
				return false;
			}
		}
		return true;
	}

	/** A pod stands on one of {@code yard}'s pads. */
	static boolean podStands(ServerLevel level, Yard yard) {
		for (BlockPos pad : yard.pads()) {
			AABB box = new AABB(pad).inflate(STANDS_FLAT, STANDS_UP, STANDS_FLAT);
			if (!level.getEntitiesOfClass(Entity.class, box, LandingYard::standingPod).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** What the prices at {@code town}'s boards are multiplied by: lower while a pod stands in its yard. */
	static float tradeFactor(ServerLevel level, @Nullable Faction town) {
		if (town == null) {
			return 1.0F;
		}
		for (Yard y : yards(level, null, 0.0, town.id())) {
			if (y.faction().id() == town.id() && podStands(level, y)) {
				return 1.0F - DISCOUNT;
			}
		}
		return 1.0F;
	}

	/**
	 * (for Powers, by reflection) A patrol pod stood down at {@code at}: the town of the yard there is named in the next
	 * Tidings, at most once a {@link #NEWS_GAP} for a yard.
	 */
	public static void touched(ServerLevel level, BlockPos at) {
		Yard yard = near(level, at, RANGE);
		if (yard == null) {
			return;
		}
		long now = level.getGameTime();
		Long last = NAMED.get(yard.faction().id());
		if (last != null && now - last < NEWS_GAP) {
			return;
		}
		NAMED.put(yard.faction().id(), now);
		Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.landing_yard." + level.getRandom().nextInt(3), yard.faction().name()));
	}
}
