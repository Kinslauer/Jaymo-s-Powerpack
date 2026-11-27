package com.jaymoskingdoms;

import com.jaymoskingdoms.glowwood.FungusWood;
import com.jaymoskingdoms.glowwood.GlowWoods;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The glow-woods round Sylvan havens (a Sylvan haven, and the Sylvan houses of a mixed one; lore: "The glow-woods").
 * <ul>
 * <li><b>Giants.</b> As a haven grows, giant glowcaps, bouncecaps and inkcaps stand between its house-trees: a first at
 * size {@link #GIANTS_FROM}, another for every {@link #SIZE_PER_GIANT} past it, at most {@link #GIANTS_MOST}. Never on a
 * path or inside a building's ground (and the fungus itself only grows where there is room). Small bouncecaps stand
 * under the tree houses' platforms, and a fairy ring on the green.</li>
 * <li><b>Feedback.</b> A new giant only grows on shaded ground ({@link #shaded}), mostly beside one already there (the
 * spores drift), not at all in winter and twice as often in autumn (Kitchen's seasons, by reflection). A haven that is
 * emptied or burned (nobody left, or no house standing, or the haven fallen) goes dark: its giants thin out each check
 * and are gone within one season ({@link #SEASON_TICKS}), faster in winter, each leaving the small mushroom behind.
 * Looked after again before then, the rot stops.</li>
 * <li><b>Houses.</b> Some Sylvan houses are built from the fungal wood the haven has picked ({@link #woodOf}).</li>
 * <li><b>Skeps.</b> The orchards' straw skeps (vanilla bee boxes in {@link HavenBuildings#fields}) are given their bees.</li>
 * </ul>
 * The giants and fungal blocks are ported from Spore &amp; Stem (Apache-2.0); see {@code glowwood.GlowWoods}.
 */
public final class GlowWoodHavens {
	/** How often the havens are looked at (ticks), and a haven grows (or checks its skeps) once in this many looks. */
	static final long CHECK_TICKS = 200L;
	static final int GROW_EVERY = 6;
	/** The size a haven's first giant grows at, another for every this many past it, and the most it has. A mixed haven has half. */
	static final int GIANTS_FROM = 8, SIZE_PER_GIANT = 4, GIANTS_MOST = 12;
	/** A fairy ring on the green once the haven is this big; the bouncecaps under platforms, at most this many. */
	static final int RING_FROM = 6, UNDER_MOST = 6;
	/** How many places a haven tries for one new giant, and how far from the seat (blocks) they may be. */
	static final int TRIES = 24, REACH_MOST = 56, REACH_LEAST = 8;
	/** Most of the time a new giant grows beside one already standing, within this far (blocks); and no two closer than this. */
	static final double SPREAD_SHARE = 0.7;
	static final int SPREAD_REACH = 14, APART = 6;
	/** Clear ground round a building (blocks), and a fairy ring's (it is wider). */
	static final int BUILDING_MARGIN = 4, RING_MARGIN = 7;
	/** Shade: of the nine columns sampled over a spot, this many must have a crown or a cap overhead. */
	static final int SHADE_COLUMNS = 3, SHADE_FROM = 3, SHADE_TO = 14;
	/** What a giant is chosen by: glowcap, bouncecap, inkcap weights. */
	static final int[] KIND_WEIGHT = {5, 2, 2};
	/** A Sylvan house in this many per hundred is built from the haven's fungal wood. */
	static final int FUNGAL_HOUSES = 50;
	/** Whether a platform: of the footprint's cells at platform height, this many must be solid. */
	static final int PLATFORM_CELLS = 60, PLATFORM_HEIGHT = HavenBuildings.BOUGH;
	/** A season is 21 days of Kitchen's year; an unlooked-after haven is dark by then. */
	static final long SEASON_TICKS = 21L * 24000L;
	/** The chance a cap or gill block rots away in a check while the haven is dark (more in winter). */
	static final double ROT_CHANCE = 0.0015;
	/** The most blocks the search for one giant's own caps looks at (its 15x18x15 reach). */
	static final int GROWN_MAX = 15 * 18 * 15;
	/** A skep's bees: this many near it. */
	static final int BEES_PER_SKEP = 2;
	static final double BEE_RANGE = 14.0;

	/** One standing giant: where its stem starts, and which feature it was grown from. */
	record Giant(BlockPos pos, String kind) {
		static final Codec<Giant> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Giant::pos),
				Codec.STRING.fieldOf("kind").forGetter(Giant::kind))
			.apply(i, Giant::new));
	}

	/** A haven's glow: its giants, whether the fairy ring is made, and since when it has gone dark (-1: it hasn't). */
	record Glow(List<Giant> giants, boolean ring, long dark) {
		static final Glow NONE = new Glow(List.of(), false, -1L);
		static final Codec<Glow> CODEC = RecordCodecBuilder.create(i -> i.group(
				Giant.CODEC.listOf().optionalFieldOf("giants", List.of()).forGetter(Glow::giants),
				Codec.BOOL.optionalFieldOf("ring", false).forGetter(Glow::ring),
				Codec.LONG.optionalFieldOf("dark", -1L).forGetter(Glow::dark))
			.apply(i, Glow::new));
	}

	/** Faction id to its glow. */
	static final AttachmentType<Map<String, Glow>> GLOW = AttachmentRegistry.create(JaymosKingdoms.id("glow_woods"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Glow.CODEC)));

	private GlowWoodHavens() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GlowWoodHavens::tick);
	}

	// ---- the fungal wood a haven builds with ---------------------------------------------------------------------------

	/** The fungal wood haven {@code f} has picked (every haven keeps one: its seat decides). */
	static FungusWood woodOf(Faction f) {
		long mix = (f.seat().asLong() * 0x9E3779B97F4A7C15L) ^ ((long)f.id() << 17);
		mix ^= mix >>> 31;
		return FungusWood.values()[(int)Math.floorMod(mix, (long)FungusWood.values().length)];
	}

	/** The fungal wood a Sylvan house of {@code f} is built from ({@code materials} is the house's own roll), or "" for plain timber. */
	static String houseWood(Faction f, long materials) {
		return Math.floorMod(materials >>> 5, 100L) < FUNGAL_HOUSES ? woodOf(f).id() : "";
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.type() == FactionType.BARBARIAN) {
				continue;
			}
			String haven = Havens.of(server, f);
			boolean sylvan = haven.equals(Heritage.Kind.SYLVAN.key()) || haven.equals(Havens.MIXED);
			Glow glow = glowOf(server, f.id());
			if ((!sylvan && glow.giants().isEmpty()) || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (sylvan && !f.fallen() && tended(server, f)) {
				if (glow.dark() >= 0L) {
					glow = put(server, f.id(), new Glow(glow.giants(), glow.ring(), -1L));
				}
				if (Math.floorMod(now / CHECK_TICKS + f.id(), (long)GROW_EVERY) == 0) {
					grow(level, f, glow, haven.equals(Havens.MIXED));
					skeps(level, f);
				}
			} else if (!glow.giants().isEmpty()) {
				rot(level, f, glow, now);
			}
		}
	}

	/** Looked after: someone lives in it and a house of it stands. A haven that is empty, burned or fallen is not. */
	private static boolean tended(MinecraftServer server, Faction f) {
		if (Planner.size(f) <= 0) {
			return false;
		}
		for (Works.Built b : Works.of(server, f.id()).built()) {
			if (b.entry().equals("house") && b.ruined() == 0L) {
				return true;
			}
		}
		return false;
	}

	static Glow glowOf(MinecraftServer server, int faction) {
		return Factions.level(server).getAttachedOrElse(GLOW, Map.<String, Glow>of()).getOrDefault(Integer.toString(faction), Glow.NONE);
	}

	private static Glow put(MinecraftServer server, int faction, Glow glow) {
		ServerLevel store = Factions.level(server);
		Map<String, Glow> next = new HashMap<>(store.getAttachedOrElse(GLOW, Map.<String, Glow>of()));
		next.put(Integer.toString(faction), glow);
		store.setAttached(GLOW, Map.copyOf(next));
		return glow;
	}

	// ---- growth ------------------------------------------------------------------------------------------------------

	/** Autumn's flush is twice as likely; winter grows nothing; Kitchen's seasons (by reflection), else always 1. */
	private static double spreadSeason(ServerLevel level) {
		String season = KitchenCompat.season(level);
		return "winter".equals(season) ? 0.0 : "autumn".equals(season) ? 2.0 : 1.0;
	}

	/** The cold rots them faster. */
	private static double rotSeason(ServerLevel level) {
		String season = KitchenCompat.season(level);
		return "winter".equals(season) ? 2.0 : "summer".equals(season) ? 0.7 : 1.0;
	}

	private static int target(int size, boolean mixed) {
		if (size < GIANTS_FROM) {
			return 0;
		}
		int n = Math.min(GIANTS_MOST, 1 + (size - GIANTS_FROM) / SIZE_PER_GIANT);
		return mixed ? (n + 1) / 2 : n;
	}

	private static void grow(ServerLevel level, Faction f, Glow glow, boolean mixed) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		int size = Planner.size(f);
		List<Giant> giants = new ArrayList<>(glow.giants());
		boolean ring = glow.ring();
		boolean changed = false;
		List<int[]> taken = null;
		// a fairy ring on the green
		if (!ring && size >= RING_FROM) {
			taken = Planner.taken(level, f);
			ring = tryRing(level, f, taken);
			changed |= ring;
		}
		// bouncecaps under the tree houses' platforms
		int under = (int)giants.stream().filter(g -> g.kind().equals(GlowWoods.HAVEN_BOUNCECAP_LOW.identifier().getPath())).count();
		if (under < UNDER_MOST && random.nextInt(2) == 0) {
			for (Works.Built b : Works.of(server, f.id()).built()) {
				if (under >= UNDER_MOST) {
					break;
				}
				if (b.entry().equals("house") && b.ruined() == 0L && hasPlatform(level, b) && !hasGiant(giants, b)) {
					Giant g = placeUnder(level, b, random);
					if (g != null) {
						giants.add(g);
						under++;
						changed = true;
					}
				}
			}
		}
		// the great ones between the trees: only on shaded ground, and not at all in winter
		int standing = (int)giants.stream().filter(g -> !g.kind().equals(GlowWoods.HAVEN_BOUNCECAP_LOW.identifier().getPath())).count();
		if (standing < target(size, mixed) && random.nextDouble() < spreadSeason(level) * 0.5) {
			if (taken == null) {
				taken = Planner.taken(level, f);
			}
			Giant g = placeGiant(level, f, giants, taken, random);
			if (g != null) {
				giants.add(g);
				changed = true;
			}
		}
		if (changed) {
			put(server, f.id(), new Glow(List.copyOf(giants), ring, -1L));
		}
	}

	private static boolean hasGiant(List<Giant> giants, Works.Built b) {
		for (Giant g : giants) {
			BlockPos p = g.pos();
			if (p.getX() >= b.min().getX() && p.getX() <= b.max().getX() && p.getZ() >= b.min().getZ() && p.getZ() <= b.max().getZ()) {
				return true;
			}
		}
		return false;
	}

	/** A tree house: most of its footprint is solid at the platform's height. */
	private static boolean hasPlatform(ServerLevel level, Works.Built b) {
		int w = b.max().getX() - b.min().getX() + 1, d = b.max().getZ() - b.min().getZ() + 1;
		if (w < 8 || d < 8 || w > 12 || d > 12) {
			return false;
		}
		int solid = 0, y = b.min().getY() + PLATFORM_HEIGHT;
		for (int x = b.min().getX(); x <= b.max().getX(); x++) {
			for (int z = b.min().getZ(); z <= b.max().getZ(); z++) {
				if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
					solid++;
				}
			}
		}
		return solid >= PLATFORM_CELLS;
	}

	/** A small bouncecap on the ground under a platform, somewhere in the footprint's middle. */
	private static @Nullable Giant placeUnder(ServerLevel level, Works.Built b, RandomSource random) {
		for (int i = 0; i < 6; i++) {
			int x = b.min().getX() + 2 + random.nextInt(Math.max(1, b.max().getX() - b.min().getX() - 3));
			int z = b.min().getZ() + 2 + random.nextInt(Math.max(1, b.max().getZ() - b.min().getZ() - 3));
			BlockPos pos = new BlockPos(x, b.min().getY() + 1, z);
			if (level.getBlockState(pos).isAir() && isGround(level.getBlockState(pos.below())) && place(level, GlowWoods.HAVEN_BOUNCECAP_LOW, pos, random)) {
				return new Giant(pos, GlowWoods.HAVEN_BOUNCECAP_LOW.identifier().getPath());
			}
		}
		return null;
	}

	private static @Nullable Giant placeGiant(ServerLevel level, Faction f, List<Giant> giants, List<int[]> taken, RandomSource random) {
		Palette palette = Palette.at(level, f.seat());
		int reach = Math.min(REACH_MOST, Planner.landRadius(f));
		List<Giant> great = giants.stream().filter(g -> !g.kind().equals(GlowWoods.HAVEN_BOUNCECAP_LOW.identifier().getPath())).toList();
		for (int i = 0; i < TRIES; i++) {
			BlockPos around = !great.isEmpty() && random.nextDouble() < SPREAD_SHARE ? great.get(random.nextInt(great.size())).pos() : f.seat();
			int r = around.equals(f.seat()) ? reach : SPREAD_REACH;
			double a = random.nextDouble() * Math.PI * 2.0, d = REACH_LEAST + random.nextDouble() * Math.max(1, r - REACH_LEAST);
			if (!around.equals(f.seat())) {
				d = APART + random.nextDouble() * (r - APART);
			}
			int x = around.getX() + (int)Math.round(Math.cos(a) * d), z = around.getZ() + (int)Math.round(Math.sin(a) * d);
			if (!level.hasChunkAt(new BlockPos(x - 7, 64, z - 7)) || !level.hasChunkAt(new BlockPos(x + 7, 64, z + 7)) || !Loaded.made(level, new BlockPos(x, 64, z))) {
				continue;
			}
			if (nearBuilding(taken, x, z, BUILDING_MARGIN) || nearGiant(giants, x, z)) {
				continue;
			}
			BlockPos pos = ground(level, x, z);
			if (pos == null || offRoad(level, pos, palette) || !shaded(level, pos)) {
				continue;
			}
			ResourceKey<Feature> kind = kindFor(random);
			if (place(level, kind, pos, random)) {
				return new Giant(pos, kind.identifier().getPath());
			}
		}
		return null;
	}

	private static ResourceKey<Feature> kindFor(RandomSource random) {
		int roll = random.nextInt(KIND_WEIGHT[0] + KIND_WEIGHT[1] + KIND_WEIGHT[2]);
		return roll < KIND_WEIGHT[0] ? GlowWoods.HAVEN_GLOWCAP : roll < KIND_WEIGHT[0] + KIND_WEIGHT[1] ? GlowWoods.HAVEN_BOUNCECAP : GlowWoods.HAVEN_INKCAP;
	}

	private static boolean tryRing(ServerLevel level, Faction f, List<int[]> taken) {
		RandomSource random = level.getRandom();
		Palette palette = Palette.at(level, f.seat());
		int reach = Math.min(REACH_MOST, Planner.landRadius(f));
		for (int i = 0; i < TRIES; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, d = REACH_LEAST + random.nextDouble() * Math.max(1, reach - REACH_LEAST);
			int x = f.seat().getX() + (int)Math.round(Math.cos(a) * d), z = f.seat().getZ() + (int)Math.round(Math.sin(a) * d);
			if (!level.hasChunkAt(new BlockPos(x - 8, 64, z - 8)) || !level.hasChunkAt(new BlockPos(x + 8, 64, z + 8)) || nearBuilding(taken, x, z, RING_MARGIN)) {
				continue;
			}
			BlockPos pos = ground(level, x, z);
			if (pos != null && !offRoad(level, pos, palette) && place(level, GlowWoods.FAIRY_RING_FEATURE, pos, random)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code x}, {@code z} is within {@code margin} of a building's or plot's footprint (so, not clear). */
	private static boolean nearBuilding(List<int[]> taken, int x, int z, int margin) {
		for (int[] r : taken) {
			if (x >= r[0] - margin && x <= r[2] + margin && z >= r[1] - margin && z <= r[3] + margin) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearGiant(List<Giant> giants, int x, int z) {
		for (Giant g : giants) {
			int dx = g.pos().getX() - x, dz = g.pos().getZ() - z;
			if (dx * dx + dz * dz < APART * APART) {
				return true;
			}
		}
		return false;
	}

	private static boolean isGround(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM) || state.is(Blocks.COARSE_DIRT);
	}

	/** The free block over natural ground at {@code x}, {@code z}, under any crown, or null. */
	private static @Nullable BlockPos ground(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		for (int y = top; y > top - 24 && y > level.getMinY() + 1; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState at = level.getBlockState(pos);
			if (isGround(level.getBlockState(pos.below())) && (at.isAir() || at.is(BlockTags.REPLACEABLE_BY_TREES)) && at.getFluidState().isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	/** Not on a path, or just beside one. */
	private static boolean offRoad(ServerLevel level, BlockPos pos, Palette palette) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Planner.road(level.getBlockState(pos.offset(dx, -1, dz)), palette)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Shaded: a crown, a roof or a cap overhead in several of nine columns round the spot. */
	static boolean shaded(ServerLevel level, BlockPos pos) {
		int covered = 0;
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				for (int y = SHADE_FROM; y <= SHADE_TO; y++) {
					BlockState s = level.getBlockState(pos.offset(dx, y, dz));
					if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || GlowWoods.isCapOrGill(s) || s.is(BlockTags.PLANKS)) {
						covered++;
						break;
					}
				}
			}
		}
		return covered >= SHADE_COLUMNS;
	}

	private static boolean place(ServerLevel level, ResourceKey<Feature> key, BlockPos pos, RandomSource random) {
		return level.registryAccess().lookupOrThrow(Registries.FEATURE).get(key)
			.map(holder -> holder.value().place(level, level.getChunkSource().getGenerator(), random, pos)).orElse(false);
	}

	// ---- going dark ----------------------------------------------------------------------------------------------------

	/**
	 * An emptied, burned or fallen haven's giants lose their caps and gills a few blocks at a time and are gone within a
	 * season; each leaves its small mushroom, and the rest returns to the floor.
	 */
	private static void rot(ServerLevel level, Faction f, Glow glow, long now) {
		MinecraftServer server = level.getServer();
		long since = glow.dark() >= 0L ? glow.dark() : now;
		if (glow.dark() < 0L) {
			glow = put(server, f.id(), new Glow(glow.giants(), glow.ring(), since));
		}
		boolean over = now - since >= SEASON_TICKS;
		// a haven whose allays are still there fades half as fast (Allays)
		double chance = ROT_CHANCE * rotSeason(level) * (Allays.keeping(level, f) ? 0.5 : 1.0);
		RandomSource random = level.getRandom();
		List<Giant> left = new ArrayList<>();
		for (Giant g : glow.giants()) {
			if (!level.hasChunkAt(g.pos())) {
				left.add(g);
				continue;
			}
			if (!decay(level, g, over ? 1.0 : chance, over, random)) {
				left.add(g);
			}
		}
		put(server, f.id(), new Glow(List.copyOf(left), glow.ring(), since));
	}

	/** Rots some of a giant's caps and gills ({@code all}: every one, and its stem); true when none of it is left. */
	private static boolean decay(ServerLevel level, Giant g, double chance, boolean all, RandomSource random) {
		BlockPos o = g.pos();
		boolean any = false;
		for (BlockPos p : grown(level, o)) {
			BlockState s = level.getBlockState(p);
			if (GlowWoods.isCapOrGill(s)) {
				if (all || random.nextDouble() < chance) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				} else {
					any = true;
				}
			}
		}
		if (any && !all) {
			return false;
		}
		// the caps are gone: the stem comes down, and the floor takes it back
		for (int y = 0; y < 24; y++) {
			BlockPos p = o.above(y);
			if (isStem(level.getBlockState(p))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		Block small = g.kind().contains("bounce") ? GlowWoods.BOUNCECAP : g.kind().contains("ink") ? GlowWoods.INKCAP : GlowWoods.GLOWCAP;
		if (level.getBlockState(o).isAir() && small.defaultBlockState().canSurvive(level, o)) {
			level.setBlock(o, small.defaultBlockState(), 3);
		}
		return true;
	}

	/**
	 * The giant's own caps and gills: those joined to its stem through stem, cap and gill blocks, within reach of its
	 * base. Caps a player placed nearby, not touching the giant, are left alone.
	 */
	private static List<BlockPos> grown(ServerLevel level, BlockPos o) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(o.immutable());
		seen.add(o.immutable());
		while (!open.isEmpty() && seen.size() < GROWN_MAX) {
			BlockPos p = open.poll();
			BlockState s = level.getBlockState(p);
			boolean part = isStem(s) || GlowWoods.isCapOrGill(s);
			if (!part && !p.equals(o)) {
				continue;
			}
			if (GlowWoods.isCapOrGill(s)) {
				found.add(p);
			}
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (Math.abs(n.getX() - o.getX()) <= 7 && Math.abs(n.getZ() - o.getZ()) <= 7 && n.getY() >= o.getY() - 1
					&& n.getY() <= o.getY() + 16 && seen.add(n)) {
					open.add(n);
				}
			}
		}
		return found;
	}

	private static boolean isStem(BlockState state) {
		return state.is(GlowWoods.GLOWCAP_STEM) || state.is(GlowWoods.BOUNCECAP_STEM) || state.is(GlowWoods.INKCAP_STEM);
	}

	// ---- skeps ---------------------------------------------------------------------------------------------------------

	/** The orchards' straw skeps (vanilla bee boxes) are given their bees, a couple each, which go home to them. */
	private static void skeps(ServerLevel level, Faction f) {
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (!b.entry().equals(HavenBuildings.FIELDS) || b.ruined() != 0L || !level.hasChunkAt(b.min())) {
				continue;
			}
			for (BlockPos p : BlockPos.betweenClosed(b.min(), new BlockPos(b.max().getX(), b.min().getY() + 4, b.max().getZ()))) {
				if (level.getBlockState(p).is(Blocks.BEEHIVE) && level.getBlockEntity(p) instanceof BeehiveBlockEntity hive) {
					BlockPos hivePos = p.immutable();
					int near = level.getEntitiesOfClass(Bee.class, new AABB(hivePos).inflate(BEE_RANGE)).size() + hive.getOccupantCount();
					for (int i = near; i < BEES_PER_SKEP; i++) {
						Bee bee = EntityTypes.BEE.create(level, EntitySpawnReason.EVENT);
						if (bee != null) {
							bee.setPos(hivePos.getX() + 0.5, hivePos.getY() + 1.2, hivePos.getZ() + 0.5);
							bee.setHivePos(hivePos);
							level.addFreshEntity(bee);
						}
					}
				}
			}
		}
	}
}
