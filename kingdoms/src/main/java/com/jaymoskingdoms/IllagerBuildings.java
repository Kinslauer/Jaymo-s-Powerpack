package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * An illager camp's buildings, all original designs in the spirit of a pillager outpost: dark oak, cobblestone and grey
 * wool, the ominous banner flying everywhere. The <b>warlord's pavilion</b> (a stepped wool roof on dark oak posts,
 * banners at its porch, his seat and his hoard inside), bell tents, a <b>watchtower</b> with a railed lookout, an iron
 * <b>cage</b> (an allay held captive), the <b>loot pile</b> (barrels and chests under a dark oak lean-to: the camp's
 * {@link Storage}), banner poles, a practice range, and a dark oak fence ring with lantern-topped posts and a bannered
 * gate. A camp grows by its own hands ({@link IllagerCamps}): each day it may raise the next of {@link #GROWTH}.
 *
 * <p>Designs are {@link Blueprint}s facing north (their front on z = 0, y = 0 the first layer on the ground), placed
 * with {@link CampBuildings.Placer} like a barbarian camp's; every banner placed becomes an ominous banner.
 */
final class IllagerBuildings {
	/** The fence ring's radius round the fire, and how far (and how high) the ground is levelled and cleared (forest camps fell their trees). */
	static final int RING_RADIUS = 20, CLEAR_RADIUS = RING_RADIUS + 2, CLEAR_HEIGHT = 18;
	/** The ring: fences this high, a log post this high every {@code POST_EVERY} degrees, the gate's half-width and its posts' height. */
	static final int RING_HIGH = 2, POST_HIGH = 3, GATE_HALF = 2, GATE_POST_HIGH = 4;
	static final double POST_EVERY = 22.5;
	/** The warlord's pavilion: where it stands (camp frame: x right of the gate, z away from it). */
	static final int PAVILION_Z = 5;
	/** How far round the fire its people keep, and where a sentry stands on the watchtower (offset in {@link #watchtower}). */
	static final int HOME_RADIUS = 16;
	static final BlockPos SENTRY_SPOT = new BlockPos(1, 9, 1);

	private IllagerBuildings() {
	}

	// ---- materials -------------------------------------------------------------------------------------------------

	private static BlockState wool(DyeColor colour) {
		return Blocks.WOOL.pick(colour).defaultBlockState();
	}

	private static BlockState log() {
		return Blocks.DARK_OAK_LOG.defaultBlockState();
	}

	private static BlockState planks() {
		return Blocks.DARK_OAK_PLANKS.defaultBlockState();
	}

	private static BlockState fence() {
		return Blocks.DARK_OAK_FENCE.defaultBlockState();
	}

	private static BlockState slab() {
		return Blocks.DARK_OAK_SLAB.defaultBlockState();
	}

	/** A standing banner, front to the north (made ominous when it's placed). */
	private static BlockState banner() {
		return Blocks.BANNER.pick(DyeColor.WHITE).defaultBlockState();
	}

	// ---- designs ---------------------------------------------------------------------------------------------------

	/**
	 * The warlord's pavilion (9 x 10): a porch of two fence posts flying the banner, dark oak corner posts, grey wool walls
	 * and a stepped roof striped white and grey with a banner on its peak; inside, a carpet to his seat, his hoard chest
	 * behind it, lanterns and barrels.
	 */
	static Blueprint pavilion() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		BlockState grey = wool(DyeColor.GRAY), white = wool(DyeColor.WHITE);
		// the porch
		for (int x : new int[] {2, 6}) {
			t.set(x, 0, 0, fence());
			t.set(x, 1, 0, fence());
			t.set(x, 2, 0, banner());
		}
		// walls (z 1..9), posts at the corners and midway along the sides
		for (int z = 1; z <= 9; z++) {
			for (int y = 0; y <= 2; y++) {
				boolean post = z == 1 || z == 9 || z == 5;
				t.set(0, y, z, post ? log() : grey);
				t.set(8, y, z, post ? log() : grey);
			}
		}
		for (int x = 1; x <= 7; x++) {
			for (int y = 0; y <= 2; y++) {
				t.set(x, y, 9, grey);
				if (x < 3 || x > 5 || y == 2) {
					t.set(x, y, 1, grey);
				}
			}
		}
		// the roof: rings stepping in, striped
		for (int step = 0; step <= 3; step++) {
			BlockState ring = step % 2 == 0 ? white : grey;
			int x0 = step, x1 = 8 - step, z0 = 1 + step, z1 = 9 - step;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (x == x0 || x == x1 || z == z0 || z == z1) {
						t.set(x, 3 + step, z, ring);
					}
				}
			}
		}
		t.set(4, 6, 5, white);
		t.set(4, 7, 5, fence());
		t.set(4, 8, 5, banner());
		// inside
		BlockState carpet = Blocks.CARPET.pick(DyeColor.GRAY).defaultBlockState();
		for (int z = 2; z <= 6; z++) {
			t.set(4, 0, z, carpet);
		}
		t.set(4, 0, 7, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		t.set(4, 0, 8, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		t.set(3, 0, 8, Blocks.LANTERN.defaultBlockState());
		t.set(5, 0, 8, Blocks.LANTERN.defaultBlockState());
		t.set(1, 0, 8, Blocks.BARREL.defaultBlockState());
		t.set(7, 0, 8, Blocks.FLETCHING_TABLE.defaultBlockState());
		t.set(1, 0, 2, Blocks.LANTERN.defaultBlockState());
		t.set(7, 0, 2, Blocks.LANTERN.defaultBlockState());
		return t.done("illager_pavilion");
	}

	/** Where the warlord stands in his pavilion, and his hoard (offsets in {@link #pavilion}). */
	static final BlockPos WARLORD_SPOT = new BlockPos(4, 0, 6), HOARD_CHEST = new BlockPos(4, 0, 8);

	/** A bell tent (5 x 5): grey wool walls on dark oak corners, a two-step pointed roof, a doorway, hay and a barrel inside. */
	static Blueprint tent() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		BlockState grey = wool(DyeColor.GRAY), light = wool(DyeColor.LIGHT_GRAY), white = wool(DyeColor.WHITE);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				if (!edge) {
					continue;
				}
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				for (int y = 0; y <= 1; y++) {
					if (x == 2 && z == 0) {
						continue;
					}
					t.set(x, y, z, corner ? log() : grey);
				}
				t.set(x, 2, z, light);
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x != 2 || z != 2) {
					t.set(x, 3, z, white);
				}
			}
		}
		t.set(2, 3, 2, light);
		t.set(2, 4, 2, fence());
		t.set(1, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(3, 0, 3, Blocks.BARREL.defaultBlockState());
		return t.done("illager_tent");
	}

	/**
	 * A watchtower (5 x 5, 12 high): dark oak legs on a cobblestone footing, a plank spine with a ladder up it, a lookout
	 * platform with a fence railing, a slab roof on corner posts, and the banner over it.
	 */
	static Blueprint watchtower() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int[][] corners = {{0, 0}, {4, 0}, {0, 4}, {4, 4}};
		for (int[] c : corners) {
			t.set(c[0], 0, c[1], Blocks.COBBLESTONE.defaultBlockState());
			for (int y = 1; y <= 10; y++) {
				t.set(c[0], y, c[1], log());
			}
		}
		for (int y = 0; y <= 8; y++) {
			t.set(2, y, 4, planks());
			t.set(2, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		// the platform (the ladder comes up through it)
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				if (!corner && !(x == 2 && (z == 3 || z == 4))) {
					t.set(x, 8, z, planks());
				}
				if (!corner && (x == 0 || x == 4 || z == 0 || z == 4) && !(x == 2 && z == 4)) {
					t.set(x, 9, z, fence());
				}
			}
		}
		t.set(2, 9, 4, fence());
		BlockState bottom = slab().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				t.set(x, 11, z, bottom);
			}
		}
		t.set(2, 12, 2, banner());
		t.set(2, 10, 2, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		return t.done("illager_watchtower");
	}

	/** An iron cage (5 x 5): a plank floor, iron bars on dark oak corners, a slab lid. Something is kept in it. */
	static Blueprint cage() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		BlockState bottom = slab().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				t.set(x, 0, z, planks());
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				for (int y = 1; y <= 3; y++) {
					if (corner) {
						t.set(x, y, z, log());
					} else if (edge) {
						t.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
					}
				}
				t.set(x, 4, z, corner ? planks() : bottom);
			}
		}
		t.set(2, 4, 2, planks());
		t.set(2, 3, 2, Blocks.IRON_CHAIN.defaultBlockState());
		return t.done("illager_cage");
	}

	/** Where the captive is put (offset in {@link #cage}). */
	static final BlockPos CAGE_SPOT = new BlockPos(2, 1, 2);

	/** The loot pile (7 x 4): barrels and chests along a dark oak back wall under a slab lean-to, a banner at its end. */
	static Blueprint lootPile() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y <= 2; y++) {
				t.set(x, y, 3, x == 0 || x == 6 ? log() : planks());
			}
			for (int z = 0; z <= 2; z++) {
				t.set(x, 2, z, slab());
			}
		}
		for (int x : new int[] {0, 6}) {
			t.set(x, 0, 0, log());
			t.set(x, 1, 0, log());
		}
		t.set(0, 3, 3, banner());
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		for (int x = 1; x <= 5; x++) {
			if (x % 2 == 0) {
				t.set(x, 0, 2, chest);
			} else {
				t.set(x, 0, 2, Blocks.BARREL.defaultBlockState());
				t.set(x, 1, 2, Blocks.BARREL.defaultBlockState());
			}
		}
		return t.done("illager_loot_pile");
	}

	/** Where the camp's loot is brought: in front of the loot pile (offset in {@link #lootPile}). */
	static final BlockPos LOOT_DEPOT = new BlockPos(3, 0, 1);

	/** A banner pole (1 x 1): a cobblestone foot, a dark oak fence pole, the ominous banner on top. */
	static Blueprint bannerPole() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		t.set(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
		for (int y = 1; y <= 3; y++) {
			t.set(0, y, 0, fence());
		}
		t.set(0, 4, 0, banner());
		return t.done("illager_banner_pole");
	}

	/** A practice range (5 x 2): targets on hay bales, a fletching table and a barrel of bolts between them. */
	static Blueprint practiceRange() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x : new int[] {0, 4}) {
			t.set(x, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
			t.set(x, 1, 1, Blocks.TARGET.defaultBlockState());
		}
		t.set(2, 0, 1, Blocks.FLETCHING_TABLE.defaultBlockState());
		t.set(3, 0, 1, Blocks.BARREL.defaultBlockState());
		t.set(1, 0, 0, Blocks.HAY_BLOCK.defaultBlockState());
		return t.done("illager_practice_range");
	}

	// ---- the camp ----------------------------------------------------------------------------------------------------

	/** One building of a camp: what, where its anchor is (camp frame), and which way its front faces (turns from the gate). */
	record Spot(String id, java.util.function.Supplier<Blueprint> design, int x, int z, int turns, int width) {
		CampBuildings.Placer placer(BlockPos fire, Direction gate) {
			CampBuildings.Placer camp = new CampBuildings.Placer(fire, gate, 0);
			Direction facing = gate;
			for (int i = 0; i < this.turns; i++) {
				facing = facing.getClockWise();
			}
			return new CampBuildings.Placer(camp.at(this.x, 0, this.z), facing, this.width);
		}
	}

	/** turns: 0 its front to the gate, 1 to the gate's right (a building on the left facing the fire), 3 to its left. */
	static final Spot PAVILION = new Spot("illager_pavilion", IllagerBuildings::pavilion, 0, PAVILION_Z, 0, 9);
	static final Spot LOOT = new Spot("illager_loot_pile", IllagerBuildings::lootPile, 10, 0, 3, 7);
	static final Spot TOWER = new Spot("illager_watchtower", IllagerBuildings::watchtower, -10, -12, 0, 5);
	static final Spot CAGE = new Spot("illager_cage", IllagerBuildings::cage, 10, -12, 0, 5);
	static final List<Spot> START_TENTS = List.of(new Spot("illager_tent", IllagerBuildings::tent, -9, 0, 1, 5),
		new Spot("illager_tent", IllagerBuildings::tent, -9, 7, 1, 5), new Spot("illager_tent", IllagerBuildings::tent, 9, 8, 3, 5));
	static final List<Spot> START_POLES = List.of(new Spot("illager_banner_pole", IllagerBuildings::bannerPole, -3, -4, 0, 1),
		new Spot("illager_banner_pole", IllagerBuildings::bannerPole, 3, -4, 0, 1));

	/** What a camp raises as it grows, in order (one a day at most: see {@link IllagerCamps}). */
	static final List<Spot> GROWTH = List.of(
		new Spot("illager_banner_pole", IllagerBuildings::bannerPole, -6, 3, 0, 1),
		new Spot("illager_practice_range", IllagerBuildings::practiceRange, 5, -16, 0, 5),
		new Spot("illager_tent", IllagerBuildings::tent, -7, 14, 1, 5),
		new Spot("illager_banner_pole", IllagerBuildings::bannerPole, 6, 3, 0, 1),
		new Spot("illager_tent", IllagerBuildings::tent, 7, 14, 3, 5),
		new Spot("illager_watchtower", IllagerBuildings::watchtower, 0, 15, 0, 5),
		new Spot("illager_banner_pole", IllagerBuildings::bannerPole, -15, -5, 0, 1),
		new Spot("illager_banner_pole", IllagerBuildings::bannerPole, 15, -5, 0, 1),
		new Spot("illager_tent", IllagerBuildings::tent, -3, -9, 1, 5));

	/** What a new camp's founding built: its fire (the seat), the loot pile's containers and depot, the warlord's spot, the cage, the sentry's post. */
	record Layout(BlockPos fire, List<BlockPos> storage, BlockPos depot, BlockPos warlordSpot, BlockPos cage, BlockPos sentry) {
	}

	/**
	 * The ground under a camp round {@code center}, trees and all (they're felled when it's built): the floor (median
	 * ground height), or {@link Integer#MIN_VALUE} if it can't be built on (unloaded, water, or too uneven).
	 */
	static int floor(ServerLevel level, BlockPos center, int maxSpread) {
		return floor(level, center, maxSpread, CLEAR_RADIUS);
	}

	/** (0.5.27) The same over {@code radius} (a goblin village's is wider). */
	static int floor(ServerLevel level, BlockPos center, int maxSpread, int radius) {
		List<Integer> heights = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx += 2) {
			for (int dz = -radius; dz <= radius; dz += 2) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				BlockPos column = center.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					return Integer.MIN_VALUE;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				int y = top.getY() - 1;
				// down through trunks, leaves, huge mushrooms and plants to the ground
				while (y > level.getMinY() && woodland(level.getBlockState(column.atY(y)))) {
					y--;
				}
				BlockPos ground = column.atY(y);
				if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty() || !Storehouse.ground(level.getBlockState(ground))) {
					return Integer.MIN_VALUE;
				}
				heights.add(y + 1);
			}
		}
		heights.sort(Integer::compare);
		if (heights.isEmpty() || heights.getLast() - heights.getFirst() > maxSpread) {
			return Integer.MIN_VALUE;
		}
		return heights.get(heights.size() / 2);
	}

	/** What stands on the forest floor: trunks, leaves, huge mushrooms, plants (or nothing). */
	private static boolean woodland(BlockState state) {
		return state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.MUSHROOM_STEM)
			|| state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || (state.canBeReplaced() && state.getFluidState().isEmpty())
			|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS);
	}

	/** Builds a whole camp round {@code center} at {@code floor}, its gate facing {@code gate}. */
	static Layout build(ServerLevel level, Faction f, BlockPos center, int floor, Direction gate) {
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		CampBuildings.Placer camp = new CampBuildings.Placer(fire, gate, 0);
		CampBuildings.level(level, fire, CLEAR_RADIUS, CLEAR_HEIGHT);
		ring(level, camp);
		// the fire, ringed with benches facing it
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockState bench = Blocks.DARK_OAK_STAIRS.defaultBlockState();
		level.setBlock(camp.at(0, 0, -2), camp.turn(bench.setValue(StairBlock.FACING, Direction.NORTH)), Block.UPDATE_CLIENTS);
		level.setBlock(camp.at(-2, 0, 0), camp.turn(bench.setValue(StairBlock.FACING, Direction.WEST)), Block.UPDATE_CLIENTS);
		level.setBlock(camp.at(2, 0, 0), camp.turn(bench.setValue(StairBlock.FACING, Direction.EAST)), Block.UPDATE_CLIENTS);
		List<BlockPos> storage = new ArrayList<>();
		place(level, PAVILION, fire, gate);
		storage.addAll(place(level, LOOT, fire, gate));
		place(level, TOWER, fire, gate);
		place(level, CAGE, fire, gate);
		for (Spot s : START_TENTS) {
			place(level, s, fire, gate);
		}
		for (Spot s : START_POLES) {
			place(level, s, fire, gate);
		}
		// (the pavilion's barrel is the warlord's own, not the loot pile's)
		CampBuildings.Placer pavilion = PAVILION.placer(fire, gate);
		BlockPos chest = pavilion.at(HOARD_CHEST);
		stockHoard(level, chest, level.getRandom());
		return new Layout(fire, storage, LOOT.placer(fire, gate).at(LOOT_DEPOT), pavilion.at(WARLORD_SPOT), CAGE.placer(fire, gate).at(CAGE_SPOT),
			TOWER.placer(fire, gate).at(SENTRY_SPOT));
	}

	/** Where the warlord of a camp built round {@code fire} (its gate facing {@code gate}) keeps. */
	static BlockPos warlordSpot(BlockPos fire, Direction gate) {
		return PAVILION.placer(fire, gate).at(WARLORD_SPOT);
	}

	/** The fence ring: dark oak fences, lantern-topped log posts, and a gate between two tall posts flying the banner. */
	private static void ring(ServerLevel level, CampBuildings.Placer camp) {
		for (int x = -RING_RADIUS - 1; x <= RING_RADIUS + 1; x++) {
			for (int z = -RING_RADIUS - 1; z <= RING_RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < RING_RADIUS - 0.5 || d >= RING_RADIUS + 0.5 || (z < 0 && Math.abs(x) <= GATE_HALF)) {
					continue;
				}
				boolean gatePost = z < 0 && Math.abs(x) == GATE_HALF + 1;
				double angle = Math.toDegrees(Math.atan2(z, x)) + 360.0;
				boolean post = gatePost || angle % POST_EVERY < Math.toDegrees(1.0 / RING_RADIUS);
				if (gatePost) {
					for (int y = 0; y < GATE_POST_HIGH; y++) {
						level.setBlock(camp.at(x, y, z), log(), Block.UPDATE_CLIENTS);
					}
					setBanner(level, camp.at(x, GATE_POST_HIGH, z), camp.turn(banner()));
				} else if (post) {
					for (int y = 0; y < POST_HIGH; y++) {
						level.setBlock(camp.at(x, y, z), log(), Block.UPDATE_CLIENTS);
					}
					level.setBlock(camp.at(x, POST_HIGH, z), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else {
					for (int y = 0; y < RING_HIGH; y++) {
						level.setBlock(camp.at(x, y, z), fence(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		// fences join up with their neighbours once the whole ring stands
		for (int x = -RING_RADIUS - 1; x <= RING_RADIUS + 1; x++) {
			for (int z = -RING_RADIUS - 1; z <= RING_RADIUS + 1; z++) {
				for (int y = 0; y < RING_HIGH; y++) {
					BlockPos at = camp.at(x, y, z);
					BlockState state = level.getBlockState(at);
					if (state.is(BlockTags.FENCES)) {
						level.setBlock(at, Block.updateFromNeighbourShapes(state, level, at), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** Places a building of the camp at once. Returns where its containers went. */
	static List<BlockPos> place(ServerLevel level, Spot spot, BlockPos fire, Direction gate) {
		CampBuildings.Placer placer = spot.placer(fire, gate);
		List<BlockPos> containers = CampBuildings.place(level, spot.design().get(), placer);
		finish(level, spot.design().get(), placer);
		return containers;
	}

	/** {@code spot}'s footprint is clear (nothing but air, plants and snow where it would go). */
	static boolean clear(ServerLevel level, Spot spot, BlockPos fire, Direction gate) {
		CampBuildings.Placer placer = spot.placer(fire, gate);
		for (Blueprint.Placement p : spot.design().get().blocks()) {
			BlockPos at = placer.at(p.offset());
			if (!level.isLoaded(at)) {
				return false;
			}
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	/** After a building is placed: its banners made ominous, its fences joined up. */
	private static void finish(ServerLevel level, Blueprint blueprint, CampBuildings.Placer placer) {
		for (Blueprint.Placement p : blueprint.blocks()) {
			BlockPos at = placer.at(p.offset());
			BlockState state = level.getBlockState(at);
			if (state.is(BlockTags.BANNERS)) {
				ominous(level, at);
			} else if (state.is(BlockTags.FENCES) || state.is(Blocks.IRON_BARS)) {
				level.setBlock(at, Block.updateFromNeighbourShapes(state, level, at), Block.UPDATE_CLIENTS);
			}
		}
	}

	private static void setBanner(ServerLevel level, BlockPos at, BlockState state) {
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
		ominous(level, at);
	}

	/** Paints the banner at {@code at} with the ominous banner's pattern. */
	static void ominous(ServerLevel level, BlockPos at) {
		if (level.getBlockEntity(at) instanceof BannerBlockEntity banner) {
			banner.applyComponentsFromItemStack(Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
			banner.setChanged();
			BlockState state = level.getBlockState(at);
			level.sendBlockUpdated(at, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** The warlord's hoard: emeralds, iron, a crossbow, arrows, an enchanted book's worth of lapis, a golden apple. */
	private static void stockHoard(ServerLevel level, BlockPos chest, RandomSource random) {
		if (!(level.getBlockEntity(chest) instanceof Container c)) {
			return;
		}
		List<ItemStack> hoard = List.of(new ItemStack(Items.EMERALD, 8 + random.nextInt(9)), new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(9)),
			new ItemStack(Items.CROSSBOW), new ItemStack(Items.ARROW, 16 + random.nextInt(17)), new ItemStack(Items.LAPIS_LAZULI, 6 + random.nextInt(10)),
			new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.DARK_OAK_LOG, 8 + random.nextInt(9)), new ItemStack(Items.BREAD, 4 + random.nextInt(6)));
		for (ItemStack stack : hoard) {
			for (int tries = 0; tries < 10; tries++) {
				int slot = random.nextInt(c.getContainerSize());
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, stack);
					break;
				}
			}
		}
		c.setChanged();
	}
}
