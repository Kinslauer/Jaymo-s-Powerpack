package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) Finding a site for each patron's temple (docs/Patrons-C-Plan.md section 4): the surface, deep stone, the sea,
 * high ground, a crypt, a hidden hollow, the Nether, the Aether. Biome tags are data
 * ({@code data/jaymoskingdoms/tags/worldgen/biome/temple/}). Never on a realm's land, never in sight, never near another temple.
 */
final class TempleSites {
	/** The search ring (as the Nether forts'): out this far from the spot, in these steps, and how uneven the ground may be. */
	static final int SEARCH_RADIUS = 32, SEARCH_STEP = 4, MAX_SPREAD = 4, HEADROOM = 6;
	/** The Nether's ground. */
	static final int NETHER_LOW = 33, NETHER_HIGH = 116;
	/** Deep halls: the floor's range, the share of the hall's box that must be solid stone. */
	static final int DEEP_LOW = -52, DEEP_HIGH = -6;
	static final float SOLID_SHARE = 0.85F;
	/** Sea courts: the deepest the floor may be below sea level. */
	static final int SEA_DEEPEST = 14;
	/** Glades: logs among the samples within the radius. */
	static final int FOREST_LOGS = 10, FOREST_SAMPLES = 48, FOREST_RADIUS = 24;
	/** Crypts: how far under the yard; and the least share of solid ground down there. */
	static final int CRYPT_DEPTH = 10;
	static final float CRYPT_SOLID = 0.7F;
	/** Sun-courts on the high ground of the Overworld. */
	static final int HIGH_GROUND = 110;
	/** Hidden hollows: the floor's range, and how far below the surface it must be at least. */
	static final int HIDDEN_LOW = 4, HIDDEN_HIGH = 40, HIDDEN_BELOW = 12;

	enum Spot {
		SURFACE, DEEP, SEA, FOREST, CRYPT, HIGH, AETHER, HIDDEN, NETHER
	}

	private TempleSites() {
	}

	static boolean aether(ServerLevel level) {
		return aether(level.dimension());
	}

	static boolean aether(net.minecraft.resources.ResourceKey<Level> realm) {
		return realm.identifier().toString().equals("jaymospowers:aether");
	}

	/**
	 * The kind of site {@code p} takes in this world, or null if it has none here. (0.7.528) By the realm at the player's height,
	 * so a One Dimension world's stacked Nether and Aether bands count ({@link Stacked}).
	 */
	static @Nullable Spot spot(ServerLevel level, Patron p, BlockPos playerPos, boolean anywhere) {
		net.minecraft.resources.ResourceKey<Level> realm = Stacked.realm(level, playerPos.getY());
		if (realm == Level.NETHER) {
			return p == Patron.ANVIL_FATHER || p == Patron.LANTERN_WARDEN || p == Patron.ASH_WHISPERER ? Spot.NETHER : null;
		}
		if (aether(realm)) {
			return p == Patron.CROWNED_SUN ? Spot.AETHER : null;
		}
		if (realm != Level.OVERWORLD) {
			return null;
		}
		return switch (p) {
			case HEARTHKEEPER, TRUTH -> Spot.SURFACE;
			case ANVIL_FATHER -> anywhere || playerPos.getY() < 0 ? Spot.DEEP : null;
			case DEEP_MOTHER -> Spot.SEA;
			case GREEN_HUNTSMAN -> Spot.FOREST;
			case LANTERN_WARDEN -> Spot.CRYPT;
			case CROWNED_SUN -> Spot.HIGH;
			case ASH_WHISPERER -> Spot.HIDDEN;
		};
	}

	private static TagKey<Biome> tag(String name) {
		return TagKey.create(Registries.BIOME, JaymosKingdoms.id("temple/" + name));
	}

	private static String tagName(Patron p, Spot spot) {
		return spot == Spot.NETHER ? p.key + "_nether" : p.key;
	}

	/** Whether {@code p}'s biome rule fits the spot (by its column and the player's depth). */
	static boolean fits(ServerLevel level, Patron p, BlockPos at, BlockPos playerPos) {
		Spot spot = spot(level, p, playerPos, false);
		if (spot == null) {
			return false;
		}
		if (spot == Spot.AETHER || spot == Spot.DEEP) {
			return true;
		}
		BlockPos probe = spot == Spot.NETHER ? new BlockPos(at.getX(), playerPos.getY(), at.getZ()) : at;
		if (!level.isLoaded(probe)) {
			return false;
		}
		return level.getBiome(probe).is(tag(tagName(p, spot)));
	}

	/** The floor centre for {@code p}'s temple near {@code near}, or null. */
	static @Nullable BlockPos find(ServerLevel level, Patron p, BlockPos near, BlockPos playerPos, boolean anywhere) {
		Spot spot = spot(level, p, playerPos, anywhere);
		if (spot == null) {
			return null;
		}
		for (int ring = 0; ring <= SEARCH_RADIUS; ring += SEARCH_STEP) {
			for (int dx = -ring; dx <= ring; dx += SEARCH_STEP) {
				for (int dz = -ring; dz <= ring; dz += SEARCH_STEP) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					BlockPos found = site(level, p, spot, near.getX() + dx, near.getZ() + dz, playerPos, anywhere);
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null;
	}

	private static @Nullable BlockPos site(ServerLevel level, Patron p, Spot spot, int x, int z, BlockPos playerPos, boolean anywhere) {
		int r = TempleBuildings.radius(p);
		int nearY = playerPos.getY();
		for (int dx : new int[] {-r, r}) {
			for (int dz : new int[] {-r, r}) {
				if (!level.isLoaded(new BlockPos(x + dx, nearY, z + dz))) {
					return null;
				}
			}
		}
		BlockPos column = new BlockPos(x, nearY, z);
		if (p == Patron.ANVIL_FATHER && spot == Spot.DEEP) {
			// the forge-hall's tunnel and ladder shaft can run out this far in any direction
			int reach = r + TempleBuildings.TUNNEL + 2;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dist = r; dist <= reach; dist += 8) {
					BlockPos far = column.relative(d, dist);
					if (!level.isLoaded(far) || Factions.at(level.getServer(), level, far) != null) {
						return null;
					}
				}
				BlockPos tip = column.relative(d, reach);
				if (!level.isLoaded(tip) || Factions.at(level.getServer(), level, tip) != null) {
					return null;
				}
			}
		}
		// the whole footprint, not just the centre: nine samples are never more than a chunk apart
		for (int dx : new int[] {-r, 0, r}) {
			for (int dz : new int[] {-r, 0, r}) {
				if (Factions.at(level.getServer(), level, column.offset(dx, 0, dz)) != null) {
					return null;
				}
			}
		}
		if (!anywhere) {
			for (ServerPlayer sp : level.players()) {
				if (sp.blockPosition().closerThan(column, Temples.HIDE_RANGE)) {
					return null;
				}
			}
			for (Temples.Temple t : Temples.all(level.getServer())) {
				if (t.world().equals(level.dimension().identifier().toString()) && t.center().closerThan(column, Temples.SAME_SPACING / 4.0)) {
					return null;
				}
			}
		}
		BlockPos center = switch (spot) {
			case SURFACE, HIGH, AETHER, CRYPT, FOREST -> surfaceSite(level, p, spot, x, z, r, anywhere);
			case NETHER -> netherSite(level, p, x, z, r, nearY, anywhere);
			case SEA -> seaSite(level, x, z, r);
			case DEEP -> deepSite(level, x, z, r, nearY);
			case HIDDEN -> hiddenSite(level, p, x, z, r, anywhere);
		};
		if (center == null) {
			return null;
		}
		if (anywhere) {
			return center;
		}
		for (ServerPlayer sp : level.players()) {
			if (sp.blockPosition().closerThan(center, Temples.HIDE_RANGE)) {
				return null;
			}
		}
		return center;
	}

	// ---- the surface ---------------------------------------------------------------------------------------------------------

	/** The top solid block's y in the column, or MIN_VALUE if it is not loaded, wet or has none. */
	private static int surface(ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, level.getSeaLevel(), z))) {
			return Integer.MIN_VALUE;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		if (y <= level.getMinY() + 1) {
			return Integer.MIN_VALUE;
		}
		BlockState state = level.getBlockState(new BlockPos(x, y, z));
		return state.isSolid() && state.getFluidState().isEmpty() ? y : Integer.MIN_VALUE;
	}

	private static @Nullable BlockPos surfaceSite(ServerLevel level, Patron p, Spot spot, int x, int z, int r, boolean anywhere) {
		int y = surface(level, x, z);
		if (y == Integer.MIN_VALUE) {
			return null;
		}
		BlockPos center = new BlockPos(x, y, z);
		if (!anywhere) {
			if (spot != Spot.AETHER && !level.getBiome(center).is(tag(tagName(p, spot)))) {
				return null;
			}
			if (spot == Spot.HIGH && y < HIGH_GROUND) {
				return null;
			}
		}
		int[] steps = {-r, 0, r};
		for (int dx : steps) {
			for (int dz : steps) {
				int py = surface(level, x + dx, z + dz);
				if (py == Integer.MIN_VALUE || Math.abs(py - y) > MAX_SPREAD) {
					return null;
				}
			}
		}
		if (spot == Spot.FOREST && !anywhere && !forest(level, x, z)) {
			return null;
		}
		if (spot == Spot.CRYPT && !solidBelow(level, center)) {
			return null;
		}
		return fluidFree(level, center, r, 6) ? center : null;
	}

	private static boolean forest(ServerLevel level, int x, int z) {
		int logs = 0;
		for (int i = 0; i < FOREST_SAMPLES; i++) {
			int sx = x + level.getRandom().nextInt(FOREST_RADIUS * 2 + 1) - FOREST_RADIUS;
			int sz = z + level.getRandom().nextInt(FOREST_RADIUS * 2 + 1) - FOREST_RADIUS;
			if (!level.isLoaded(new BlockPos(sx, level.getSeaLevel(), sz))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) - 1;
			if (level.getBlockState(new BlockPos(sx, y, sz)).is(BlockTags.LOGS)) {
				logs++;
			}
		}
		return logs >= FOREST_LOGS;
	}

	/** The ground under a graveyard is solid enough for a crypt, and dry. */
	private static boolean solidBelow(ServerLevel level, BlockPos center) {
		int solid = 0, total = 0;
		for (int a : new int[] {-8, 0, 8}) {
			for (int b : new int[] {-8, 0, 8}) {
				for (int dy : new int[] {-CRYPT_DEPTH + 2, -CRYPT_DEPTH + 5, -CRYPT_DEPTH + 8}) {
					BlockState state = level.getBlockState(center.offset(a, dy, b));
					total++;
					if (!state.getFluidState().isEmpty()) {
						return false;
					}
					if (state.isSolid()) {
						solid++;
					}
				}
			}
		}
		return solid >= total * CRYPT_SOLID;
	}

	/** No water or lava in a coarse sampling of the box. */
	private static boolean fluidFree(ServerLevel level, BlockPos center, int r, int height) {
		for (int a = -r; a <= r; a += 2) {
			for (int b = -r; b <= r; b += 2) {
				for (int y = 0; y <= height; y += 3) {
					if (!level.getBlockState(center.offset(a, y, b)).getFluidState().isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	// ---- the Nether ----------------------------------------------------------------------------------------------------------

	private static @Nullable BlockPos netherSite(ServerLevel level, Patron p, int x, int z, int r, int nearY, boolean anywhere) {
		int floor = ground(level, x, z, Math.min(NETHER_HIGH, nearY + 24), NETHER_LOW);
		if (floor == Integer.MIN_VALUE) {
			return null;
		}
		BlockPos center = new BlockPos(x, floor, z);
		if (!anywhere && !level.getBiome(center).is(tag(tagName(p, Spot.NETHER)))) {
			return null;
		}
		int[] steps = {-r, 0, r};
		for (int dx : steps) {
			for (int dz : steps) {
				if (ground(level, x + dx, z + dz, floor + MAX_SPREAD, floor - MAX_SPREAD) == Integer.MIN_VALUE) {
					return null;
				}
			}
		}
		return center;
	}

	/** The highest solid ground in the column between top and bottom with headroom over it, or MIN_VALUE (over fluid too). */
	private static int ground(ServerLevel level, int x, int z, int top, int bottom) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = top; y >= bottom; y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (!state.getFluidState().isEmpty()) {
				return Integer.MIN_VALUE;
			}
			if (state.isAir() || !state.isSolid()) {
				continue;
			}
			boolean room = true;
			for (int h = 1; h <= HEADROOM && room; h++) {
				BlockState above = level.getBlockState(pos.set(x, y + h, z));
				room = !above.isSolid() && above.getFluidState().isEmpty();
			}
			if (room) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	// ---- the sea -------------------------------------------------------------------------------------------------------------

	private static @Nullable BlockPos seaSite(ServerLevel level, int x, int z, int r) {
		int sea = level.getSeaLevel();
		int[] steps = {-r, 0, r};
		for (int dx : steps) {
			for (int dz : steps) {
				int sx = x + dx, sz = z + dz;
				if (!level.isLoaded(new BlockPos(sx, sea, sz))) {
					return null;
				}
				int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, sx, sz) - 1;
				if (floor < sea - SEA_DEEPEST || floor > sea - 2 || level.getBlockState(new BlockPos(sx, sea - 1, sz)).getFluidState().isEmpty()) {
					return null;
				}
			}
		}
		return new BlockPos(x, sea, z);
	}

	// ---- deep stone ----------------------------------------------------------------------------------------------------------

	private static boolean stone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD);
	}

	/** A box of solid stone and no fluid near it: the share of nine-by-three samples inside the hall that are stone. */
	private static boolean solidBox(ServerLevel level, int x, int z, int r, int floor, int height, float share) {
		int solid = 0, total = 0;
		for (int a : new int[] {-r, 0, r}) {
			for (int b : new int[] {-r, 0, r}) {
				for (int y : new int[] {floor + 1, floor + height / 2, floor + height - 1}) {
					total++;
					if (stone(level.getBlockState(new BlockPos(x + a, y, z + b)))) {
						solid++;
					}
				}
			}
		}
		if (solid < total * share) {
			return false;
		}
		// no fluid within two blocks of the box
		for (int a = -r - 2; a <= r + 2; a += 4) {
			for (int b = -r - 2; b <= r + 2; b += 4) {
				for (int y = floor - 2; y <= floor + height + 2; y += 4) {
					if (!level.getBlockState(new BlockPos(x + a, y, z + b)).getFluidState().isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static @Nullable BlockPos deepSite(ServerLevel level, int x, int z, int r, int nearY) {
		int base = Math.clamp(nearY, DEEP_LOW, DEEP_HIGH);
		for (int dy : new int[] {0, -4, 4, -8, 8}) {
			int floor = Math.clamp(base + dy, DEEP_LOW, DEEP_HIGH);
			if (solidBox(level, x, z, r, floor, 10, SOLID_SHARE)) {
				return new BlockPos(x, floor, z);
			}
		}
		return null;
	}

	// ---- hidden hollows ------------------------------------------------------------------------------------------------------

	private static @Nullable BlockPos hiddenSite(ServerLevel level, Patron p, int x, int z, int r, boolean anywhere) {
		int surface = surface(level, x, z);
		if (surface == Integer.MIN_VALUE) {
			return null;
		}
		if (!anywhere && !level.getBiome(new BlockPos(x, surface, z)).is(tag(p.key))) {
			return null;
		}
		for (int floor = HIDDEN_HIGH; floor >= HIDDEN_LOW; floor -= 4) {
			if (surface - (floor + 8) >= HIDDEN_BELOW && solidBox(level, x, z, r, floor, 9, SOLID_SHARE)) {
				return new BlockPos(x, floor, z);
			}
		}
		return null;
	}
}
