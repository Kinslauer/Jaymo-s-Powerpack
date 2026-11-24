package com.jaymoskingdoms;

import com.jaymoskingdoms.StyleBuildings.House;
import com.jaymoskingdoms.StyleBuildings.Look;
import com.jaymoskingdoms.StyleBuildings.Roof;
import com.jaymoskingdoms.VillageStyles.Ctx;
import com.jaymoskingdoms.VillageStyles.Ground;
import com.jaymoskingdoms.VillageStyles.Role;
import com.jaymoskingdoms.VillageStyles.Style;
import com.jaymoskingdoms.beasts.Gnomes;
import com.jaymoskingdoms.blossomland.BlossomBlocks;
import com.jaymoskingdoms.seas.Mohknee;
import com.jaymoskingdoms.seas.Offshore;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The village styles themselves ({@link VillageStyles}): for each, where it rises and why, the ground it needs, its
 * names, its streets, its pieces and the people who live in it. Each is a few lines of data over the shapes in
 * {@link StyleBuildings}.
 */
final class VillageStyleSet {
	private VillageStyleSet() {
	}

	static void fill(List<Style> out) {
		out.add(hamlet());
		out.add(bog());
		out.add(mud());
		out.add(timber());
		out.add(oasis());
		out.add(harbour());
		out.add(clockwork());
		out.add(inn());
		out.add(border());
		out.add(cabins());
		out.add(mooring());
		out.add(capital());
	}

	// ---- looks ---------------------------------------------------------------------------------------------------------

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static Look bogLook() {
		return Look.of(b(Blocks.MANGROVE_PLANKS), b(Blocks.MANGROVE_LOG), b(Blocks.MANGROVE_PLANKS), StyleBuildings.thatchStairs(), StyleBuildings.thatchSlab())
			.foot(b(Blocks.MANGROVE_LOG)).gable(StyleBuildings.thatch()).fence(b(Blocks.MANGROVE_FENCE)).door(Blocks.MANGROVE_DOOR);
	}

	private static Look mudLook() {
		return Look.of(b(Blocks.MUD_BRICKS), b(Blocks.ACACIA_LOG), b(Blocks.PACKED_MUD), StyleBuildings.thatchStairs(), StyleBuildings.thatchSlab())
			.gable(StyleBuildings.thatch()).fence(b(Blocks.ACACIA_FENCE)).door(Blocks.ACACIA_DOOR);
	}

	private static Look timberLook() {
		BlockState paper = BlossomBlocks.SHOJI != null ? b(BlossomBlocks.SHOJI) : b(Blocks.GLASS_PANE);
		BlockState plaster = BlossomBlocks.PLASTER != null ? b(BlossomBlocks.PLASTER) : b(Blocks.CALCITE);
		return Look.of(paper, b(Blocks.DARK_OAK_LOG), b(Blocks.DARK_OAK_PLANKS), StyleBuildings.thatchStairs(), StyleBuildings.thatchSlab())
			.foot(b(Blocks.COBBLESTONE)).gable(StyleBuildings.thatch()).window(paper).fence(b(Blocks.DARK_OAK_FENCE)).door(Blocks.DARK_OAK_DOOR).framed(plaster, 2);
	}

	private static Look harbourLook() {
		return Look.of(b(Blocks.CALCITE), b(Blocks.SMOOTH_QUARTZ), b(Blocks.SMOOTH_STONE), b(Blocks.BRICK_STAIRS), b(Blocks.BRICK_SLAB))
			.gable(b(Blocks.CALCITE)).window(StyleBuildings.named("light_blue_stained_glass_pane", Blocks.GLASS_PANE)).fence(b(Blocks.WARPED_FENCE)).door(Blocks.WARPED_DOOR);
	}

	private static Look sandLook() {
		return Look.of(b(Blocks.CUT_SANDSTONE), b(Blocks.JUNGLE_LOG), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SANDSTONE_STAIRS), b(Blocks.SANDSTONE_SLAB))
			.foot(b(Blocks.SANDSTONE)).fence(b(Blocks.JUNGLE_FENCE)).door(Blocks.JUNGLE_DOOR);
	}

	private static Look cabinLook() {
		return Look.of(b(Blocks.SPRUCE_LOG), b(Blocks.STRIPPED_SPRUCE_LOG), b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_STAIRS), b(Blocks.SPRUCE_SLAB))
			.foot(b(Blocks.COBBLESTONE)).fence(b(Blocks.SPRUCE_FENCE)).door(Blocks.SPRUCE_DOOR).logs();
	}

	private static Look brickLook() {
		return Look.of(b(Blocks.BRICKS), b(Blocks.STRIPPED_SPRUCE_LOG), b(Blocks.SPRUCE_PLANKS), b(Blocks.DEEPSLATE_TILE_STAIRS), b(Blocks.DEEPSLATE_TILE_SLAB))
			.window(b(Blocks.IRON_BARS)).fence(b(Blocks.SPRUCE_FENCE)).door(Blocks.SPRUCE_DOOR);
	}

	/** Furnishes a house with {@code blocks} along its back row (a trade's stations). */
	private static House.Fit trade(Block... blocks) {
		return (s, floor, front, back, w) -> {
			for (int i = 0; i < blocks.length && 2 + i < w - 2; i++) {
				s.set(2 + i, floor + 1, back - 1, b(blocks[i]));
			}
		};
	}

	private static Roof pick(Ctx c, Roof a, Roof b) {
		return c.random().nextBoolean() ? a : b;
	}

	// ---- V1 hill-hole hamlets ------------------------------------------------------------------------------------------

	/**
	 * V1. Where: old meadows and woodland edges (plains, meadow, forest and flower forest, never snow), on a hillside, at least
	 * {@link VillageStyles#HAMLET_ROAD_CLEAR} blocks from any road. Why: the gnome folk keep away from the big folk's wars,
	 * so they dig in where no road brings an army. Gnomes ({@link Gnomes}) keep the houses and their gardens; the rest of
	 * the folk are Sylvan, the woodland's long-lived small people.
	 */
	private static Style hamlet() {
		Style s = new Style("hill_hamlet", "old meadows and woodland edges far from every road: the gnome folk keep away from the big folk's wars");
		s.ground(Ground.SLOPE, 18, 14, 14).names(new String[] {"Mossy", "Under", "Hob", "Burrow", "Thimble", "Little", "Dimble"},
			new String[] {"hollow", "knoll", "barrow", "dell", "mound", "combe"}).roads(b(Blocks.DIRT_PATH), b(Blocks.DIRT_PATH), b(Blocks.DIRT), new int[] {-1, -14, 1, 8});
		s.apart = 600;
		s.haven = Heritage.Kind.SYLVAN.key();
		s.fits(
			(l, p) -> VillageStyles.has(VillageStyles.biome(l, p), "meadow", "forest", "plains", "grove") && !VillageStyles.has(VillageStyles.biome(l, p), "snow", "frozen", "ice", "dark")
				&& !VillageStyles.roadNear(l, p, VillageStyles.HAMLET_ROAD_CLEAR));
		for (int[] h : new int[][] {{-10, -6, 1}, {10, -6, 3}, {-12, 7, 1}, {12, 7, 3}}) {
			s.piece(Role.HOME, h[0], h[1], h[2], c -> StyleBuildings.hole(5, c.random()));
		}
		s.piece(Role.HALL, 0, 9, 0, c -> StyleBuildings.hole(7, c.random()));
		s.piece(Role.DECOR, -4, -13, 0, c -> StyleBuildings.garden(c.random()));
		s.piece(Role.DECOR, 5, -13, 0, c -> StyleBuildings.garden(c.random()));
		s.extra = (c, bell, gate, homes) -> {
			for (BlockPos home : homes) {
				for (int i = 0; i < 2; i++) {
					Mob g = Gnomes.GNOME.spawn(c.level(), Construction.standable(c.level(), home, 4), EntitySpawnReason.STRUCTURE);
					if (g != null) {
						g.setPersistenceRequired();
					}
				}
			}
		};
		return s;
	}

	// ---- V2 stilt villages in the bog ------------------------------------------------------------------------------------

	/**
	 * V2. Where: swamps and mangrove swamps, on shallow water. Why: a marsh has fish, reeds and herbs, and nobody taxes it, so
	 * the bog folk trade reeds and medicine from houses on legs, joined by boardwalks.
	 */
	private static Style bog() {
		Style s = new Style("bog_stilts", "swamps and mangrove swamps, on shallow water: the bog folk trade reeds and medicine, and nobody taxes a marsh");
		s.ground(Ground.WATER, 22, 4, 12).names(new String[] {"Reed", "Mire", "Sedge", "Heron", "Peat", "Eel", "Fen"}, new String[] {"walk", "stilts", "wick", "holm", "mere", "landing"})
			.roads(b(Blocks.MANGROVE_PLANKS), b(Blocks.MANGROVE_PLANKS), b(Blocks.MANGROVE_LOG), new int[] {-1, -22, 1, 24}, new int[] {-18, -1, 18, 1});
		s.sink = 2;
		s.apart = 700;
		s.fits((l, p) -> VillageStyles.has(VillageStyles.biome(l, p), "swamp", "mangrove") && VillageStyles.waterAt(l, p.getX(), p.getZ()));
		for (int z : new int[] {-12, -2, 8}) {
			s.piece(Role.HOME, -2, z, 1, c -> new House("stilt_house", 7, 9, 3).plinth(2).legs().porch(2).roof(Roof.GABLE).build(bogLook()));
			s.piece(Role.HOME, 2, z, 3, c -> new House("stilt_house", 7, 9, 3).plinth(2).legs().porch(2).roof(Roof.GABLE).build(bogLook()));
		}
		s.piece(Role.HALL, -2, 20, 1, c -> new House("herb_house", 7, 9, 3).plinth(2).legs().porch(2).fit(trade(Blocks.BREWING_STAND, Blocks.CAULDRON, Blocks.COMPOSTER)).build(bogLook()));
		s.piece(Role.HALL, 2, 20, 3, c -> new House("reed_works", 7, 9, 3).plinth(2).legs().porch(2).fit(trade(Blocks.LOOM, Blocks.BARREL, Blocks.COMPOSTER)).build(bogLook()));
		return s;
	}

	// ---- V3 mud-brick river towns ------------------------------------------------------------------------------------------

	/**
	 * V3. Where: hot river plains, on the dry ground a river's width back from the water (savanna, desert and badlands palettes).
	 * Why: clay and reeds are free there, and the river floods every year, so the houses stand on raised plinths.
	 */
	private static Style mud() {
		Style s = new Style("mud_river_town", "hot river plains and savanna banks, back from the water: clay and reeds are free there and the floods come yearly");
		s.ground(Ground.LEVEL, 24, 6, 16).names(new String[] {"Nile", "Tamar", "Kesh", "Amun", "Sebek", "Karnak", "Wadi"}, new String[] {"bend", "ford", "shore", "mere", "wick", "sar"})
			.roads(b(Blocks.PACKED_MUD), b(Blocks.MUD_BRICKS), b(Blocks.MUD_BRICKS), new int[] {-1, -20, 1, 22}, new int[] {-14, -1, 14, 1});
		s.siter = (l, p, r) -> VillageStyles.besideWater(l, p, r, 34);
		s.fits((l, p) -> {
			Palette pal = Palette.at(l, p);
			BlockPos water = VillageStyles.waterRing(l, p, 28, 60);
			return (pal == Palette.SAVANNA || pal == Palette.DESERT || pal == Palette.BADLANDS) && water != null && VillageStyles.has(VillageStyles.biome(l, water), "river");
		});
		for (int z : new int[] {-13, -5, 7, 15}) {
			s.piece(Role.HOME, -3, z, 1, c -> new House("mud_house", 7, 9, 3).plinth(2).porch(3).steps().roof(pick(c, Roof.FLAT, Roof.GABLE_X)).build(mudLook()));
			s.piece(Role.HOME, 3, z, 3, c -> new House("mud_house", 7, 9, 3).plinth(2).porch(3).steps().roof(pick(c, Roof.FLAT, Roof.GABLE_X)).build(mudLook()));
		}
		s.piece(Role.HALL, 0, 24, 0, c -> new House("granary", 11, 9, 4).plinth(1).roof(Roof.GABLE).fit(trade(Blocks.BARREL, Blocks.BARREL, Blocks.BARREL, Blocks.COMPOSTER)).build(mudLook()));
		s.piece(Role.HALL, -15, 0, 1, c -> new House("potters", 9, 9, 3).plinth(1).roof(Roof.FLAT).fit(trade(Blocks.FURNACE, Blocks.CAULDRON, Blocks.FLOWER_POT)).build(mudLook()));
		s.piece(Role.DECOR, 0, -7, 0, c -> flood());
		return s;
	}

	/** The flood gauge (3 x 3): a stone column in the square, notched in courses for each year's water. */
	private static Blueprint flood() {
		Buildings.Sketch s = new Buildings.Sketch("flood_gauge", 3, 3, 9);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				s.set(x, 0, z, b(Blocks.MUD_BRICKS));
			}
		}
		for (int y = 1; y <= 7; y++) {
			s.set(1, y, 1, y % 2 == 0 ? b(Blocks.STONE_BRICK_WALL) : b(Blocks.CHISELED_STONE_BRICKS));
		}
		s.set(1, 8, 1, b(Blocks.LANTERN));
		return s.done();
	}

	// ---- V4 timber-and-paper villages for Mohknee --------------------------------------------------------------------------

	/**
	 * V4. Where: only on Mohknee's dry land (the same reading the Blossom villages use), within 700 blocks of one of its
	 * settlements, on rolling ground. Why: the blossom kingdoms eat rice, and these farm villages work its terraces: each
	 * building is set on the ground it stands on, so the paddies step down the hill.
	 */
	private static Style timber() {
		Style s = new Style("mohknee_farm", "Mohknee's dry land only, beside the blossom settlements: the farm villages that feed the kingdoms from their rice terraces");
		s.ground(Ground.SLOPE, 30, 12, 16).names(new String[] {"Ine", "Tanada", "Mino", "Kome", "Hata", "Nae", "Aze"}, new String[] {"mura", "bata", "gawa", "sato", "dani", "no"})
			.roads(b(Blocks.GRAVEL), b(Blocks.COARSE_DIRT), b(Blocks.PACKED_MUD), new int[] {-1, -18, 1, 18});
		s.mohknee = true;
		s.apart = 500;
		s.fits((l, p) -> {
			if (!VillageStyles.dryMohknee(l, p) || Offshore.island(l, p.getX(), p.getZ()) || !VillageStyles.blossomBlocks()) {
				return false;
			}
			for (Faction f : Factions.all(l.getServer())) {
				if (!f.fallen() && Mohknee.on(l, f.seat()) && Raids.horizontal(f.seat(), p) < 700.0 * 700.0) {
					return true;
				}
			}
			return false;
		});
		for (int[] h : new int[][] {{-8, -8, 1}, {8, -8, 3}, {-8, 6, 1}, {8, 6, 3}, {0, 20, 0}}) {
			s.piece(Role.HOME, h[0], h[1], h[2], c -> new House("farmhouse", 9, 9, 3).plinth(1).roof(Roof.HIP).build(timberLook()));
		}
		s.piece(Role.HALL, 0, -20, 0, c -> new House("kura", 7, 7, 3).plinth(2).legs().roof(Roof.GABLE).fit(trade(Blocks.BARREL, Blocks.BARREL, Blocks.COMPOSTER)).build(timberLook()));
		s.piece(Role.DECOR, 0, 8, 0, c -> Blossom.shrine());
		for (int[] p : new int[][] {{-26, -6}, {26, -6}, {-26, 8}, {26, 8}, {-12, 30}, {12, 30}}) {
			s.piece(Role.DECOR, p[0], p[1], 0, c -> Blossom.paddy());
		}
		return s;
	}

	// ---- V5 oasis villages ----------------------------------------------------------------------------------------------------

	/**
	 * V5. Where: in a harsh desert or badlands (at least one step harsh, {@link HarshLands}) with water at the surface within
	 * about forty blocks, on the dry ground beside it. Why: life clusters at water; the walled well is what they keep, and
	 * the date palms are their living.
	 */
	private static Style oasis() {
		Style s = new Style("oasis", "harsh deserts and badlands where water reaches the surface: life clusters at water, behind a wall");
		s.ground(Ground.LEVEL, 28, 6, 16).names(new String[] {"Ain", "Bir", "Wadi", "Qasr", "Nakhl", "Tamar", "Zerzura"}, new String[] {" al-Ma", " Hadra", " Sahel", "ra", "bad", "far"})
			.roads(b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CUT_SANDSTONE), b(Blocks.SANDSTONE), new int[] {-1, -8, 1, 3});
		s.siter = (l, p, r) -> VillageStyles.besideWater(l, p, r, 30);
		s.fits((l, p) -> VillageStyles.has(VillageStyles.biome(l, p), "desert", "badlands") && HarshLands.at(l, p) >= 1 && VillageStyles.waterRing(l, p, 20, 46) != null);
		for (int[] h : new int[][] {{-8, -4, 1}, {8, -4, 3}, {-8, 6, 1}, {8, 6, 3}}) {
			s.piece(Role.HOME, h[0], h[1], h[2], c -> new House("oasis_house", 7, 7, 3).roof(Roof.FLAT).build(sandLook()));
		}
		s.piece(Role.HALL, 0, -12, 0, c -> new House("date_seller", 9, 7, 3).roof(Roof.FLAT).fit(trade(Blocks.BARREL, Blocks.BARREL, Blocks.BARREL)).build(sandLook()));
		s.piece(Role.DECOR, 0, 2, 0, c -> StyleBuildings.wallyard(sandLook()));
		s.piece(Role.DECOR, -10, 14, 0, c -> StyleBuildings.grove(sandLook(), c.random()));
		s.piece(Role.DECOR, 10, 14, 0, c -> StyleBuildings.grove(sandLook(), c.random()));
		return s;
	}

	// ---- V6 whitewashed harbour towns -------------------------------------------------------------------------------------------

	/**
	 * V6. Where: a warm coast (savanna, desert or badlands land) with open sea in reach, within 700 blocks of a realm. Why:
	 * ships need ports, and a realm's trade goes out by sea. The gate faces the water and the town steps up the shore in
	 * whitewashed houses under terracotta-red roofs, with a quay, a lighthouse and a harbour office.
	 */
	private static Style harbour() {
		Style s = new Style("harbour_town", "warm coasts on a realm's sea trade: ships need ports");
		s.ground(Ground.SLOPE, 26, 16, 18).names(new String[] {"Porto", "Cala", "Santa", "Mar", "Portal", "Vela", "Bianca"}, new String[] {"vecchia", "bella", "mare", "scala", "bianco", "rada"})
			.roads(b(Blocks.STONE_BRICKS), b(Blocks.COBBLESTONE), b(Blocks.STONE), new int[] {-1, -20, 1, 22}, new int[] {-14, 3, 14, 4});
		s.siter = (l, p, r) -> VillageStyles.besideWater(l, p, r, 20);
		s.fits((l, p) -> {
			Palette pal = Palette.at(l, p);
			return (pal == Palette.SAVANNA || pal == Palette.DESERT || pal == Palette.BADLANDS) && PirateCoves.sea(l, p, 64) != null && VillageStyles.realmWithin(l, p, 700.0);
		});
		s.facer = (l, p, toward) -> {
			BlockPos sea = PirateCoves.sea(l, p, 64);
			return sea == null ? toward : Direction.getApproximateNearest(sea.getX() - p.getX(), 0, sea.getZ() - p.getZ());
		};
		for (int z : new int[] {6, 17}) {
			for (int x : new int[] {-12, -3, 6}) {
				s.piece(Role.HOME, x, z, 0, c -> new House("harbour_house", 7, 7, 3).roof(pick(c, Roof.GABLE_X, Roof.FLAT)).chimney().build(harbourLook()));
			}
		}
		s.piece(Role.HALL, -11, -12, 0, c -> new House("harbour_office", 9, 7, 3).roof(Roof.GABLE_X).fit(trade(Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.BARREL)).build(harbourLook()));
		s.piece(Role.HALL, 11, -12, 0, c -> new House("warehouse", 9, 7, 4).roof(Roof.GABLE_X).fit(trade(Blocks.BARREL, Blocks.BARREL, Blocks.BARREL, Blocks.BARREL)).build(harbourLook()));
		s.piece(Role.DECOR, 21, -8, 0, c -> StyleBuildings.lighthouse(harbourLook()));
		s.piece(Role.DECOR, -6, -5, 0, c -> StyleBuildings.stall(harbourLook(), "white", "blue"));
		s.piece(Role.DECOR, 6, -5, 0, c -> StyleBuildings.stall(harbourLook(), "white", "light_blue"));
		s.extra = (c, bell, gate, homes) -> PirateCoves.jetty(c.level(), bell.relative(gate, 18), gate, gate.getClockWise());
		return s;
	}

	// ---- V8 a clockwork town ------------------------------------------------------------------------------------------------------

	/**
	 * V8. Where: mountains with ore in them (iron, copper or coal in the chunks round it), a clear shelf of ground. Why: the
	 * tinkers who built for the Mad Tinkerer, and did not go up into the Cloud Sea, came to the ore for the metal and stayed.
	 * Built in their steam towns' brick and iron ({@link SteamBuildings}) with a clock tower and a guild hall; clockwork
	 * golems walk its streets.
	 */
	private static Style clockwork() {
		Style s = new Style("clockwork_town", "ore mountains: the tinkers came for the metal, and clockwork golems walk their streets");
		s.ground(Ground.LEVEL, 34, 8, 26).names(new String[] {"Cog", "Sprocket", "Tinker's", "Rivet", "Ratchet", "Flywheel", "Escape"}, new String[] {"hold", "works", "gate", "stead", "deep", "rest"})
			.roads(b(Blocks.POLISHED_ANDESITE), b(Blocks.COBBLESTONE), b(Blocks.STONE), new int[] {-1, -32, 1, 23}, new int[] {-19, -1, 19, 1});
		s.fits((l, p) -> {
			var biome = l.getBiome(p);
			return (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || VillageStyles.has(VillageStyles.biome(l, p), "peak", "slope", "windswept", "stony"))
				&& VillageStyles.oreNear(l, p);
		});
		for (int[] h : new int[][] {{-4, -24, 1}, {-4, -14, 1}, {-4, 18, 1}, {4, -24, 3}, {4, -14, 3}, {4, 18, 3}}) {
			s.piece(Role.HOME, h[0], h[1], h[2], c -> SteamBuildings.house(c.random()));
		}
		s.piece(Role.HALL, -4, 8, 1, c -> SteamBuildings.workshop(true));
		s.piece(Role.HALL, 4, 8, 3, c -> SteamBuildings.workshop(false));
		s.piece(Role.HALL, 0, 24, 0, c -> SteamBuildings.boilerHouse());
		s.piece(Role.DECOR, -20, 0, 1, c -> SteamBuildings.windmill());
		s.piece(Role.DECOR, 20, 0, 3, c -> SteamBuildings.cokeYard());
		s.piece(Role.HALL, -9, -34, 0, c -> new House("guild_hall", 11, 9, 4).roof(Roof.GABLE_X).chimney()
			.fit(trade(Blocks.ANVIL, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.BLAST_FURNACE)).build(brickLook()));
		s.piece(Role.DECOR, 14, -6, 0, c -> StyleBuildings.clockTower());
		s.piece(Role.DECOR, -10, -8, 0, c -> StyleBuildings.gear());
		s.extra = (c, bell, gate, homes) -> {
			CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
			for (int[] at : new int[][] {{0, -10}, {-8, 12}, {8, 12}}) {
				ClockworkGolem golem = ClockworkGolem.TYPE.create(c.level(), EntitySpawnReason.STRUCTURE);
				if (golem != null) {
					BlockPos pos = Construction.standable(c.level(), frame.at(at[0], 0, at[1]), 4);
					golem.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
					golem.setPlayerCreated(true);
					golem.setHomeTo(bell, 36);
					golem.setPersistenceRequired();
					c.level().addFreshEntity(golem);
				}
			}
		};
		return s;
	}

	// ---- V9 crossroads inns that grew ------------------------------------------------------------------------------------------------

	/**
	 * V9. Where: only where two of the King's Roads cross, at least {@link VillageStyles#CROSS_CLEAR} blocks from any
	 * settlement. Why: travellers stop where roads meet; an inn and a well came first, and a smith and houses grew round them.
	 */
	private static Style inn() {
		Style s = new Style("crossroads_inn", "where two roads meet: travellers stop there, and a village grew round an inn and a well");
		s.ground(Ground.LEVEL, 26, 6, 16).names(new String[] {"Cross", "Four-Ways", "Wayside", "Halfway", "Meeting", "Traveller's", "Gallows"}, new String[] {"keys", "oak", "rest", "stone", "cross", "corner"})
			.roads(b(Blocks.GRAVEL), b(Blocks.COARSE_DIRT), b(Blocks.DIRT), new int[] {-1, -24, 1, 24}, new int[] {-24, -1, 24, 1});
		s.siter = VillageStyles::crossing;
		s.apart = 500;
		s.piece(Role.HALL, 9, -6, 3, c -> Buildings.tavernInn(c.kit()));
		s.piece(Role.DECOR, -5, -5, 0, c -> Buildings.well(c.kit()));
		for (int[] h : new int[][] {{-9, -12, 1}, {-9, 7, 1}, {9, 10, 3}, {9, 20, 3}}) {
			s.piece(Role.HOME, h[0], h[1], h[2], c -> Buildings.house(c.kit()));
		}
		s.piece(Role.HALL, -9, 17, 1, c -> Buildings.smithy(c.kit()));
		s.piece(Role.DECOR, 0, 14, 0, c -> Buildings.stable(c.kit()));
		return s;
	}

	// ---- V10 border market towns --------------------------------------------------------------------------------------------------------

	/**
	 * V10. Where: midway between two realms at peace ({@link Relations}: not at war, not rivals, not one the other's vassal)
	 * that lie {@link VillageStyles#BORDER_MIN} to {@link VillageStyles#BORDER_MAX} blocks apart. Why: that is where two
	 * peoples trade: stalls and warehouses round a big square, each realm's flag on a pole at the gate.
	 */
	private static Style border() {
		Style s = new Style("border_market", "on the border between two realms at peace: that is where they trade");
		s.ground(Ground.LEVEL, 34, 7, 18).names(new String[] {"Mid", "Marchmarket", "Twin", "Parley", "Tollgate", "Barter", "Peace"}, new String[] {"mart", "fair", "bridge", "gate", "cross", "stead"})
			.roads(b(Blocks.STONE_BRICKS), b(Blocks.COBBLESTONE), b(Blocks.STONE), new int[] {-1, -30, 1, 28}, new int[] {-30, -1, 30, 1});
		s.siter = VillageStyles::between;
		s.apart = 700;
		for (int[] m : new int[][] {{-5, -9}, {5, -9}, {-5, 1}, {5, 1}}) {
			s.piece(Role.DECOR, m[0], m[1], 0, c -> Buildings.market(c.kit()));
		}
		for (int[] w : new int[][] {{-20, -8, 1}, {-20, 8, 1}, {20, -8, 3}, {20, 8, 3}}) {
			s.piece(Role.HALL, w[0], w[1], w[2], c -> new House("warehouse", 11, 9, 4).roof(Roof.GABLE_X).fit(trade(Blocks.BARREL, Blocks.BARREL, Blocks.BARREL, Blocks.BARREL))
				.build(Look.of(c.kit())));
		}
		s.piece(Role.HALL, 0, 16, 0, c -> Buildings.tavernInn(c.kit()));
		s.piece(Role.HOME, -10, 20, 0, c -> Buildings.house(c.kit()));
		s.piece(Role.HOME, 10, 20, 0, c -> Buildings.house(c.kit()));
		s.piece(Role.DECOR, -14, -20, 0, c -> Buildings.stall(c.kit()));
		s.piece(Role.DECOR, 14, -20, 0, c -> Buildings.stall(c.kit()));
		s.extra = (c, bell, gate, homes) -> {
			int[] realms = VillageStyles.bordersAt(bell);
			if (realms == null) {
				return;
			}
			CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
			for (int i = 0; i < 2; i++) {
				Faction f = Factions.get(c.level().getServer(), realms[i]);
				flag(c, frame.at(i == 0 ? -8 : 8, 0, -26), f == null ? DyeColor.WHITE : f.styleColor(c.level().getServer()));
			}
		};
		return s;
	}

	/** A flagpole at {@code at}: a post of fence and a banner-sized flag in {@code colour}. */
	private static void flag(Ctx c, BlockPos at, DyeColor colour) {
		BlockPos base = c.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		for (int y = 0; y < 8; y++) {
			c.level().setBlock(base.above(y), b(Blocks.DARK_OAK_FENCE), Block.UPDATE_ALL);
		}
		for (int dx = 1; dx <= 3; dx++) {
			for (int dy = 0; dy < 2; dy++) {
				c.level().setBlock(base.above(7 - dy).east(dx), StyleBuildings.named(colour.getName() + "_wool", Blocks.HAY_BLOCK), Block.UPDATE_ALL);
			}
		}
	}

	// ---- V11 log-cabin villages ---------------------------------------------------------------------------------------------------------

	/**
	 * V11. Where: taiga and snowy forest (the taiga and snowy palettes, under trees). Why: timber is the trade there, so
	 * the heavy log cabins with stone chimneys stand round a timber yard.
	 */
	private static Style cabins() {
		Style s = new Style("log_cabins", "taiga and snowy forest: timber is the trade");
		s.ground(Ground.LEVEL, 22, 7, 16).names(new String[] {"Spruce", "Timber", "Axe", "Fell", "Birch", "Lumber", "Sawyer's"}, new String[] {"bluff", "cut", "reach", "hollow", "lodge", "camp"})
			.roads(b(Blocks.PODZOL), b(Blocks.COARSE_DIRT), b(Blocks.COBBLESTONE), new int[] {-1, -16, 1, 18});
		s.apart = 500;
		s.fits((l, p) -> {
			Palette pal = Palette.at(l, p);
			return (pal == Palette.TAIGA || pal == Palette.SNOWY) && VillageStyles.has(VillageStyles.biome(l, p), "taiga", "forest", "grove");
		});
		for (int z : new int[] {-12, -2, 8}) {
			s.piece(Role.HOME, -5, z, 1, c -> new House("log_cabin", 7, 9, 3).plinth(1).roof(Roof.GABLE_X).chimney().build(cabinLook()));
			s.piece(Role.HOME, 5, z, 3, c -> new House("log_cabin", 7, 9, 3).plinth(1).roof(Roof.GABLE_X).chimney().build(cabinLook()));
		}
		s.piece(Role.HALL, 0, 16, 0, c -> new House("lodge", 11, 9, 3).plinth(1).roof(Roof.GABLE_X).chimney().fit(trade(Blocks.STONECUTTER, Blocks.BARREL, Blocks.GRINDSTONE)).build(cabinLook()));
		s.piece(Role.DECOR, 14, -14, 0, c -> StyleBuildings.timberYard(cabinLook()));
		return s;
	}

	// ---- V12 an airship mooring -------------------------------------------------------------------------------------------------------------

	/**
	 * V12. Where: high peaks (mountain biomes standing well over the sea), only in a world with a Cloud Sea, where the cloud
	 * towns' airships fly overhead. Why: that is where the sky folk come down to trade: a stone mast tower with an airship
	 * lying alongside, static (nothing sails her), and a couple of traders' huts. Its people are of the sky folk's blood.
	 */
	private static Style mooring() {
		Style s = new Style("airship_mooring", "high peaks under the cloud towns: where the sky folk come down to trade");
		s.ground(Ground.LEVEL, 15, 5, 26).names(new String[] {"Skyhook", "Windward", "Highmast", "Cloudstep", "Aerie", "Tether"}, new String[] {" Landing", " Mast", " Perch", " Quay"})
			.roads(b(Blocks.STONE_BRICKS), b(Blocks.POLISHED_ANDESITE), b(Blocks.STONE), new int[] {-1, -6, 1, 8});
		s.haven = Heritage.Kind.DIVINE.key();
		s.apart = 1200;
		s.fits((l, p) -> Stacked.joined(l.getServer(), Stacked.CLOUD_SEA)
			&& (l.getBiome(p).is(BiomeTags.IS_MOUNTAIN) || VillageStyles.has(VillageStyles.biome(l, p), "peak", "slope", "windswept"))
			&& l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p).getY() >= l.getSeaLevel() + 55);
		Look stone = Look.of(b(Blocks.STONE_BRICKS), b(Blocks.STRIPPED_SPRUCE_LOG), b(Blocks.SPRUCE_PLANKS), b(Blocks.STONE_BRICK_STAIRS), b(Blocks.STONE_BRICK_SLAB))
			.fence(b(Blocks.SPRUCE_FENCE)).door(Blocks.SPRUCE_DOOR);
		s.piece(Role.DECOR, 0, 9, 0, c -> StyleBuildings.mooring(new String[] {"white", "red", "blue", "yellow"}[c.random().nextInt(4)]));
		s.piece(Role.HOME, -6, -7, 0, c -> new House("traders_hut", 7, 7, 3).roof(Roof.GABLE).chimney().build(stone));
		s.piece(Role.HALL, 6, -7, 0, c -> new House("factors_hut", 7, 7, 3).roof(Roof.GABLE).fit(trade(Blocks.BARREL, Blocks.LECTERN, Blocks.BARREL)).build(stone));
		return s;
	}

	// ---- V13 capitals worth the name ---------------------------------------------------------------------------------------------------------

	/**
	 * V13. Where: open, level land (not Mohknee, whose capital is the Blossom town) with no realm's seat within
	 * {@link VillageStyles#CAPITAL_REALM_SPACING} blocks. Why: a country without a capital is one nobody governs, and the
	 * folk of a big empty land raise one where the roads will meet. Styled by the land's own materials ({@link Palette#at}).
	 * Built by {@code VillageStyles.foundCapital}.
	 */
	private static Style capital() {
		Style s = new Style("capital", "open level land far from any other realm: a walled capital with a market quarter, two temples and a keep, in its land's materials");
		s.ground(Ground.LEVEL, VillageStyles.CAPITAL_RADIUS, VillageStyles.CAPITAL_SPREAD, VillageStyles.CAPITAL_CLEAR);
		s.capital = true;
		s.apart = 1100;
		s.fits((l, p) -> !VillageStyles.realmWithin(l, p, VillageStyles.CAPITAL_REALM_SPACING) && !VillageStyles.has(VillageStyles.biome(l, p), "ocean", "river", "beach"));
		return s;
	}
}
