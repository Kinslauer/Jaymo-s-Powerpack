package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Andy (2026-11-25), of a plains village: "plains villages like these REQUIRE [a working windmill], they also require a
 * healer and butcher." Every settlement on plains land ({@link Palette#at}) has all three: a working windmill (Kitchen's
 * sails on a shaft into its grindstone), the butcher's shop and the healer's infirmary ({@link JobHalls}). A village that
 * generated with one already (its structure's pieces: Epic Structures: Villages' windmill towers, which Kitchen's
 * {@code VillageWindmills} makes turn; a butcher's or an infirmary piece) or whose builders put one up keeps it; one
 * without gets it, one building a check ({@link #EVERY}): staked as a project its builders work on, and finished whole as
 * soon as no player is within {@link #HIDE_RANGE} of it, so nobody sees a building appear.
 */
final class TownNeeds {
	/** How often (ticks) each loaded settlement is looked at. */
	static final int EVERY = 600;
	/** A missing building is finished at once only while no player is this near its site (blocks). */
	static final double HIDE_RANGE = 48.0;
	/** A town with no site for a missing building looks again after this long (ticks). */
	static final long NO_SITE_WAIT = 6000L;
	/** The working windmill's catalogue entry (the masons' pattern-book "windmill" is a look only; it never turns). */
	static final String MILL = "grain_mill";
	/** The windmill's hub above its floor, and the floor of its mill room. */
	static final int HUB_Y = 8, MILL_FLOOR_Y = 7;
	/**
	 * Epic Structures: Villages' plains windmill pieces, which Kitchen turns into working windmills (the templates its
	 * {@code data/jaymoskitchen/village_windmills/} lists).
	 */
	static final List<String> WINDMILL_PIECES = List.of("plains_big_house_1", "plains_meeting_point_4");

	/** What a plains settlement must have: the catalogue entry its builders put up, and the words its own pieces go by. */
	private enum Need {
		WINDMILL(MILL, "windmill"),
		BUTCHER("butcher", "butcher"),
		HEALER("infirmary", "infirmary");

		final String entry, word;

		Need(String entry, String word) {
			this.entry = entry;
			this.word = word;
		}
	}

	/** Settlements already seen to have everything this server run (by id). */
	private static final Set<Integer> DONE = new HashSet<>();
	/** When a settlement's builders last found no site for a missing building (by id and entry; game time). */
	private static final Map<String, Long> NO_SITE = new HashMap<>();

	private TownNeeds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownNeeds::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DONE.clear();
			NO_SITE.clear();
		});
	}

	/** The working windmill in every settlement's catalogue, built only when a plains town lacks one ({@link #tick}). */
	static void registerCatalogue() {
		for (FactionType type : new FactionType[] {FactionType.KINGDOM, FactionType.FIEFDOM, FactionType.VILLAGE}) {
			Blueprint.register(type, new Blueprint.Entry(MILL, (level, faction, variant) -> mill(Buildings.Kit.of(level, faction, variant, variant)),
				1, Integer.MAX_VALUE));
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || DONE.contains(f.id()) || !level.isPositionEntityTicking(f.seat())
				|| Palette.at(level, f.seat()) != Palette.PLAINS) {
				continue;
			}
			check(level, f);
		}
	}

	/** Puts up the first thing {@code f} lacks (one a check); remembers it once it lacks nothing. */
	private static void check(ServerLevel level, Faction f) {
		Seats.Found found = Seats.structureAt(level, f.seat(), Seats::village);
		if (found.status() == Seats.Status.UNREADY) {
			return;
		}
		Works.Site works = Works.of(level.getServer(), f.id());
		boolean lacking = false;
		for (Need need : Need.values()) {
			if (has(works, found, need)) {
				continue;
			}
			lacking = true;
			Works.Project project = null;
			for (Works.Project p : works.projects()) {
				if (p.entry().equals(need.entry)) {
					project = p;
				}
			}
			if (project == null) {
				String key = f.id() + "|" + need.entry;
				long now = level.getGameTime();
				if (now - NO_SITE.getOrDefault(key, -NO_SITE_WAIT) < NO_SITE_WAIT) {
					continue;
				}
				project = Planner.plan(level, f, true, need.entry);
				if (project == null) {
					// nowhere for it yet: the next one is looked at, and this again in a while
					NO_SITE.put(key, now);
					continue;
				}
			}
			Planner.Layout l = Construction.layout(level, f, project);
			if (l != null && Loaded.made(level, l.min().getX() - 1, l.min().getZ() - 1, l.max().getX() + 1, l.max().getZ() + 1)
				&& !Raids.playerNear(level, l.min().offset(l.width() / 2, 0, l.depth() / 2), HIDE_RANGE)) {
				Construction.finishNow(level, f, project);
				JaymosKingdoms.LOGGER.info("{} had no {}: one stands there now", f.name(), need.entry);
			}
			return;
		}
		if (!lacking) {
			DONE.add(f.id());
		}
	}

	/** {@code f} has {@code need}: one its builders finished (and still standing), or one of its village's own pieces. */
	private static boolean has(Works.Site works, Seats.Found found, Need need) {
		for (Works.Built b : works.built()) {
			if (b.ruined() == 0L && (b.entry().equals(need.entry) || b.entry().contains(need.word) && !b.entry().equals("windmill"))) {
				return true;
			}
		}
		if (found.start() == null) {
			return false;
		}
		for (StructurePiece piece : found.start().getPieces()) {
			String name = Seats.name(piece);
			if (need == Need.WINDMILL ? WINDMILL_PIECES.stream().anyMatch(name::contains) : name.contains(need.word)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The working windmill (13 x 6, its door north): a stone and timber tower (5 x 5) under a hipped roof, its sails
	 * turning in the back row (z = 5, which the 13-block width keeps clear for their sweep) on a shaft through the back
	 * wall into the grindstone in the room upstairs, a ladder up to it, sacks and hay below. Without Kitchen the hub is a
	 * bare log and the mill a plain grindstone.
	 */
	static Blueprint mill(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch(MILL, 13, 6, 20);
		Palette p = k.p;
		int x0 = 4, x1 = 8, z0 = 0, z1 = 4, mid = 6;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				s.set(x, 0, z, p.floor());
				boolean side = x == x0 || x == x1, end = z == z0 || z == z1;
				if (!side && !end) {
					continue;
				}
				for (int y = 1; y <= 11; y++) {
					s.set(x, y, z, side && end ? p.log() : y <= 4 ? Buildings.Sketch.weathered(p, x, y, z, k.salt) : p.planks());
				}
			}
		}
		// the mill room's floor, the ladder up to it (against the east wall) through a gap in it
		for (int x = x0 + 1; x < x1; x++) {
			for (int z = z0 + 1; z < z1; z++) {
				s.set(x, MILL_FLOOR_Y, z, p.planks());
			}
		}
		for (int y = 1; y <= MILL_FLOOR_Y + 1; y++) {
			s.set(x1 - 1, y, z0 + 1, Buildings.Sketch.facing(Blocks.LADDER, Direction.WEST));
		}
		s.door(mid, 1, z0, Direction.NORTH, k.door);
		// windows: below each side, and in the mill room
		s.set(x0, 2, 2, k.pane);
		s.set(x1, 2, 2, k.pane);
		s.set(x0, 9, 2, k.pane);
		s.set(x1, 9, 2, k.pane);
		s.set(mid, 9, z0, k.pane);
		s.hip(x0, x1, z0, z1, 12, k.roofStairs, k.roofSlab);
		// below: sacks of grain and a barrel
		s.set(x0 + 1, 1, z1 - 1, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(x0 + 1, 2, z1 - 1, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(mid, 1, z1 - 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		s.set(mid, MILL_FLOOR_Y + 4, 2, Buildings.Sketch.hanging(Blocks.LANTERN));
		// the mill: the hub out back, the shaft through the wall, the grindstone at its end
		s.set(mid, HUB_Y, z1, SteamGoods.shaft(Direction.Axis.Z));
		BlockState hub = SteamGoods.windmill(Direction.NORTH);
		s.set(mid, HUB_Y, z1 + 1, hub != null ? hub : SteamGoods.axis(p.log(), Direction.Axis.Z));
		s.set(mid, HUB_Y, z1 - 1, grindstone());
		return s.done();
	}

	/** Kitchen's grindstone (the horse mill a shaft works), facing north; vanilla's grindstone without Kitchen. */
	private static BlockState grindstone() {
		Block block = SteamGoods.block(SteamGoods.KITCHEN, "grindstone");
		if (block == null) {
			return Blocks.GRINDSTONE.defaultBlockState();
		}
		BlockState state = block.defaultBlockState();
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : state;
	}
}
