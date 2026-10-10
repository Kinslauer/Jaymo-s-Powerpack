package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A barbarian camp's buildings, all original designs: hide tents (wool A-frames with a ridge in the camp's colour), the
 * chief's great tent (skull-topped posts at its porch, a seat and his loot chest at the back), the <b>loot pile</b>
 * (barrels and chests under a log lean-to: the camp's {@link Storage}), a log palisade ring with a skull-topped gate,
 * a campfire with log benches; and for its builders to add as it grows ({@link #registerCatalogue}): more tents,
 * palisade sections, watch posts, a totem and pens.
 *
 * <p>Every design is a {@link Blueprint} facing north: its entrance on the front row (z = 0), y = 0 the first layer on
 * the ground. Growth blueprints use only what a camp has: its land's logs, cobblestone, and what {@link Camps} gives it
 * each day (hides of wool, fences, planks, ladders, hay, bone).
 */
final class CampBuildings {
	/** The palisade's radius round the fire, and how far the ground is levelled and cleared. */
	static final int PALISADE_RADIUS = 14, CLEAR_RADIUS = PALISADE_RADIUS + 2, CLEAR_HEIGHT = 12;
	/** The palisade's height (logs), with every other one this much taller; the gate's half-width and its posts' height. */
	static final int PALISADE_HIGH = 3, PALISADE_JAG = 1, GATE_HALF = 1, GATE_POST_HIGH = 5;
	/** Growth: how many of each the builders may add. */
	static final int MAX_TENTS = 5, MAX_PALISADE = 8, MAX_WATCH_POSTS = 2, MAX_TOTEMS = 1, MAX_PENS = 2;

	private CampBuildings() {
	}

	/** Adds the camp's growth order to the {@link Blueprint} catalogue. */
	static void registerCatalogue() {
		Blueprint.register(FactionType.BARBARIAN, new Blueprint.Entry("camp_totem", CampBuildings::totem, MAX_TOTEMS, 0));
		Blueprint.register(FactionType.BARBARIAN, new Blueprint.Entry("camp_tent", CampBuildings::tent, MAX_TENTS, 0));
		Blueprint.register(FactionType.BARBARIAN, new Blueprint.Entry("camp_watch_post", CampBuildings::watchPost, MAX_WATCH_POSTS, 0));
		Blueprint.register(FactionType.BARBARIAN, new Blueprint.Entry("camp_palisade", CampBuildings::palisade, MAX_PALISADE, 0));
		Blueprint.register(FactionType.BARBARIAN, new Blueprint.Entry("camp_pen", CampBuildings::pen, MAX_PENS, 0));
	}

	// ---- materials -----------------------------------------------------------------------------------------------

	static BlockState named(String id) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
		return (block == Blocks.AIR ? Blocks.HAY_BLOCK : block).defaultBlockState();
	}

	static BlockState wool(String colour) {
		return named(colour + "_wool");
	}

	/** The hide its tents are made of (brown or white, by camp). */
	static String hideColour(Faction f) {
		return f.id() % 2 == 0 ? "brown" : "white";
	}

	static Palette palette(ServerLevel level, Faction f) {
		return Palette.of(level.getBiome(f.seat()));
	}

	static BlockState log(ServerLevel level, Faction f) {
		return palette(level, f).log();
	}

	// ---- designs -------------------------------------------------------------------------------------------------

	/** Blocks drawn at their offsets, then laid bottom up. */
	private static final class Draft {
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		void set(int x, int y, int z, BlockState state) {
			this.blocks.put(new BlockPos(x, y, z), state);
		}

		Blueprint done(String id) {
			int w = 0, d = 0, h = 0;
			List<Blueprint.Placement> out = new ArrayList<>();
			for (var e : this.blocks.entrySet()) {
				BlockPos p = e.getKey();
				w = Math.max(w, p.getX() + 1);
				h = Math.max(h, p.getY() + 1);
				d = Math.max(d, p.getZ() + 1);
				out.add(new Blueprint.Placement(p, e.getValue()));
			}
			out.sort(Comparator.<Blueprint.Placement>comparingInt(pl -> pl.offset().getY()).thenComparingInt(pl -> pl.offset().getZ())
				.thenComparingInt(pl -> pl.offset().getX()));
			return new Blueprint(id, w, d, h, List.copyOf(out));
		}
	}

	/** A hide tent (5 x 6): a wool A-frame, its ridge striped in the camp's colour, a doorway at the front, hay to sleep on. */
	static Blueprint tent(ServerLevel level, Faction f) {
		Draft t = new Draft();
		BlockState side = wool(hideColour(f)), accent = wool(f.color().getName()), fence = palette(level, f).fence();
		for (int z = 0; z <= 5; z++) {
			for (int y = 0; y <= 1; y++) {
				t.set(0, y, z, side);
				t.set(4, y, z, side);
			}
			t.set(1, 2, z, side);
			t.set(3, 2, z, side);
			t.set(2, 3, z, z % 2 == 0 ? accent : side);
		}
		for (int x = 1; x <= 3; x++) {
			for (int y = 0; y <= 1; y++) {
				t.set(x, y, 5, side);
				if (x != 2) {
					t.set(x, y, 0, side);
				}
			}
		}
		t.set(2, 2, 5, side);
		t.set(2, 2, 0, side);
		t.set(2, 4, 0, fence);
		t.set(2, 4, 5, fence);
		t.set(1, 0, 4, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(3, 0, 4, Blocks.HAY_BLOCK.defaultBlockState());
		return t.done("camp_tent");
	}

	/**
	 * The chief's tent (7 x 8): a taller wool tent striped in the camp's colour, skulls on fence posts at its porch, a
	 * carpet up the middle to his seat, and his loot chest behind it, lanterns at the back.
	 */
	static Blueprint chiefTent(ServerLevel level, Faction f) {
		Draft t = new Draft();
		BlockState side = wool(hideColour(f)), accent = wool(f.color().getName()), fence = palette(level, f).fence();
		// the porch: two posts with skulls
		for (int x : new int[] {1, 5}) {
			t.set(x, 0, 0, fence);
			t.set(x, 1, 0, fence);
			t.set(x, 2, 0, Blocks.SKELETON_SKULL.defaultBlockState());
		}
		for (int z = 1; z <= 7; z++) {
			for (int y = 0; y <= 1; y++) {
				t.set(0, y, z, side);
				t.set(6, y, z, side);
			}
			t.set(1, 2, z, accent);
			t.set(5, 2, z, accent);
			t.set(2, 3, z, side);
			t.set(4, 3, z, side);
			t.set(3, 4, z, accent);
		}
		// the gables: the back closed, the front with a tall doorway
		for (int x = 1; x <= 5; x++) {
			int top = x == 3 ? 3 : x == 2 || x == 4 ? 2 : 1;
			for (int y = 0; y <= top; y++) {
				t.set(x, y, 7, side);
				if (x != 3 || y > 2) {
					t.set(x, y, 1, side);
				}
			}
		}
		BlockState carpet = named(f.color().getName() + "_carpet");
		for (int z = 2; z <= 4; z++) {
			t.set(3, 0, z, carpet);
		}
		t.set(3, 0, 5, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		t.set(3, 0, 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		t.set(1, 0, 6, Blocks.LANTERN.defaultBlockState());
		t.set(5, 0, 6, Blocks.LANTERN.defaultBlockState());
		t.set(1, 0, 2, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(5, 0, 2, Blocks.BONE_BLOCK.defaultBlockState());
		return t.done("camp_chief_tent");
	}

	/** Where the chief stands in his tent (offset in {@link #chiefTent}). */
	static final BlockPos CHIEF_SPOT = new BlockPos(3, 0, 4);
	/** His loot chest (offset in {@link #chiefTent}). */
	static final BlockPos CHIEF_CHEST = new BlockPos(3, 0, 6);

	/** The loot pile (7 x 4): barrels and chests along a log back wall under a slab lean-to, hay at the ends. */
	static Blueprint lootPile(ServerLevel level, Faction f) {
		Draft t = new Draft();
		Palette p = palette(level, f);
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y <= 2; y++) {
				t.set(x, y, 3, p.log());
			}
			for (int z = 0; z <= 2; z++) {
				t.set(x, 2, z, p.woodSlab());
			}
		}
		for (int x : new int[] {0, 6}) {
			t.set(x, 0, 0, p.log());
			t.set(x, 1, 0, p.log());
			t.set(x, 0, 2, Blocks.HAY_BLOCK.defaultBlockState());
		}
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		for (int x = 1; x <= 5; x++) {
			if (x % 2 == 0) {
				t.set(x, 0, 2, chest);
			} else {
				t.set(x, 0, 2, Blocks.BARREL.defaultBlockState());
				t.set(x, 1, 2, Blocks.BARREL.defaultBlockState());
			}
		}
		return t.done("camp_loot_pile");
	}

	/** Where the camp's builders load: in front of the loot pile's barrels (offset in {@link #lootPile}). */
	static final BlockPos LOOT_DEPOT = new BlockPos(3, 0, 1);

	/** A watch post (3 x 3): four log legs, a ladder up the back, a plank platform with a fence railing. */
	static Blueprint watchPost(ServerLevel level, Faction f) {
		Draft t = new Draft();
		Palette p = palette(level, f);
		for (int y = 0; y <= 4; y++) {
			for (int[] c : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
				t.set(c[0], y, c[1], p.log());
			}
		}
		for (int y = 0; y <= 3; y++) {
			t.set(1, y, 2, p.log());
		}
		for (int y = 0; y <= 4; y++) {
			t.set(1, y, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		t.set(1, 4, 0, p.planks());
		t.set(0, 4, 1, p.planks());
		t.set(2, 4, 1, p.planks());
		t.set(1, 4, 2, p.planks());
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				if (x != 1 || z != 1) {
					t.set(x, 5, z, p.fence());
				}
			}
		}
		return t.done("camp_watch_post");
	}

	/** The totem (3 x 3): a cobble base, a log pole painted with the camp's colour, fence arms, a bone crown. */
	static Blueprint totem(ServerLevel level, Faction f) {
		Draft t = new Draft();
		Palette p = palette(level, f);
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				if (x != 1 || z != 1) {
					t.set(x, 0, z, Blocks.COBBLESTONE.defaultBlockState());
				}
			}
		}
		t.set(1, 0, 1, p.log());
		t.set(1, 1, 1, p.log());
		t.set(1, 2, 1, wool(f.color().getName()));
		t.set(1, 3, 1, p.log());
		t.set(0, 3, 1, p.fence());
		t.set(2, 3, 1, p.fence());
		t.set(1, 4, 1, Blocks.BONE_BLOCK.defaultBlockState());
		t.set(1, 5, 1, p.fence());
		return t.done("camp_totem");
	}

	/** A pen (7 x 7): a fence ring with a gate at the front, hay at the back. */
	static Blueprint pen(ServerLevel level, Faction f) {
		Draft t = new Draft();
		Palette p = palette(level, f);
		for (int i = 0; i <= 6; i++) {
			t.set(i, 0, 0, p.fence());
			t.set(i, 0, 6, p.fence());
			t.set(0, 0, i, p.fence());
			t.set(6, 0, i, p.fence());
		}
		t.set(3, 0, 0, gate(p));
		t.set(1, 0, 5, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(5, 0, 5, Blocks.HAY_BLOCK.defaultBlockState());
		return t.done("camp_pen");
	}

	/** A palisade section (7 x 1): logs three high, every other one taller. */
	static Blueprint palisade(ServerLevel level, Faction f) {
		Draft t = new Draft();
		BlockState log = log(level, f);
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y < PALISADE_HIGH + (x % 2 == 0 ? PALISADE_JAG : 0); y++) {
				t.set(x, y, 0, log);
			}
		}
		return t.done("camp_palisade");
	}

	/** The fence gate that matches its palette's fence (an oak one if there's none by that name). */
	static BlockState gate(Palette p) {
		Identifier fence = BuiltInRegistries.BLOCK.getKey(p.fence().getBlock());
		Block gate = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(fence.getPath().replace("_fence", "_fence_gate")));
		return (gate == Blocks.AIR ? Blocks.OAK_FENCE_GATE : gate).defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH);
	}

	// ---- placing -------------------------------------------------------------------------------------------------

	/**
	 * A local frame: {@code anchor} is the world position of offset ({@code width} / 2, 0, 0), the building's front
	 * faces {@code facing}, x runs to its right and z away from its front.
	 */
	record Placer(BlockPos anchor, Direction facing, int width) {
		BlockPos at(int x, int y, int z) {
			Direction right = this.facing.getClockWise();
			Direction back = this.facing.getOpposite();
			int lx = x - this.width / 2;
			return this.anchor.offset(right.getStepX() * lx + back.getStepX() * z, y, right.getStepZ() * lx + back.getStepZ() * z);
		}

		BlockPos at(BlockPos offset) {
			return this.at(offset.getX(), offset.getY(), offset.getZ());
		}

		BlockState turn(BlockState state) {
			return state.rotate(switch (this.facing) {
				case EAST -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
				case SOUTH -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
				case WEST -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
				default -> net.minecraft.world.level.block.Rotation.NONE;
			});
		}
	}

	/** Places {@code blueprint} at once, turned to {@code placer}'s facing. Returns where its containers went. */
	static List<BlockPos> place(ServerLevel level, Blueprint blueprint, Placer placer) {
		List<BlockPos> containers = new ArrayList<>();
		for (Blueprint.Placement p : blueprint.blocks()) {
			BlockPos at = placer.at(p.offset());
			level.setBlock(at, placer.turn(p.state()), Block.UPDATE_CLIENTS);
			if (p.state().is(Blocks.CHEST) || p.state().is(Blocks.BARREL)) {
				containers.add(at.immutable());
			}
		}
		return containers;
	}

	// ---- the camp --------------------------------------------------------------------------------------------------

	/** What a new camp's founding built: its fire (the seat), the loot pile's containers and depot, the chief's spot and chest. */
	record Layout(BlockPos fire, List<BlockPos> storage, BlockPos depot, BlockPos chiefSpot, BlockPos chiefChest) {
	}

	/**
	 * The lie of the land round {@code center} for a camp: the floor (median ground height), or {@link Integer#MIN_VALUE}
	 * if it can't be built on (unloaded, water, trees, anything built, or ground too uneven).
	 */
	static int floor(ServerLevel level, BlockPos center, int maxSpread) {
		List<Integer> heights = new ArrayList<>();
		for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
			for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
				if (dx * dx + dz * dz > CLEAR_RADIUS * CLEAR_RADIUS) {
					continue;
				}
				BlockPos column = center.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					return Integer.MIN_VALUE;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()
					|| !Storehouse.ground(level.getBlockState(top.below()))) {
					return Integer.MIN_VALUE;
				}
				heights.add(top.getY());
			}
		}
		heights.sort(Integer::compare);
		if (heights.isEmpty() || heights.getLast() - heights.getFirst() > maxSpread) {
			return Integer.MIN_VALUE;
		}
		return heights.get(heights.size() / 2);
	}

	/** Builds a whole camp round {@code center} at {@code floor}, its gate facing {@code gate}. */
	static Layout build(ServerLevel level, Faction f, BlockPos center, int floor, Direction gate) {
		RandomSource random = level.getRandom();
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		Placer camp = new Placer(fire, gate, 0);
		level(level, fire);
		Palette p = palette(level, f);
		BlockState log = p.log();
		// the palisade, jagged, with a gap for the gate between two tall skull-topped posts
		for (int x = -PALISADE_RADIUS - 1; x <= PALISADE_RADIUS + 1; x++) {
			for (int z = -PALISADE_RADIUS - 1; z <= PALISADE_RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < PALISADE_RADIUS - 0.5 || d >= PALISADE_RADIUS + 0.5) {
					continue;
				}
				if (z < 0 && Math.abs(x) <= GATE_HALF) {
					continue;
				}
				boolean post = z < 0 && Math.abs(x) == GATE_HALF + 1;
				int high = post ? GATE_POST_HIGH : PALISADE_HIGH + ((x + z) % 2 == 0 ? PALISADE_JAG : 0);
				for (int y = 0; y < high; y++) {
					level.setBlock(camp.at(x, y, z), log, Block.UPDATE_CLIENTS);
				}
				if (post) {
					level.setBlock(camp.at(x, high, z), Blocks.SKELETON_SKULL.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		// the fire and its benches (logs laid on their sides)
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockState alongZ = log.trySetValue(RotatedPillarBlock.AXIS, gate.getAxis());
		BlockState alongX = log.trySetValue(RotatedPillarBlock.AXIS, gate.getClockWise().getAxis());
		for (int z = 0; z <= 1; z++) {
			level.setBlock(camp.at(-3, 0, z), alongZ, Block.UPDATE_CLIENTS);
			level.setBlock(camp.at(3, 0, z), alongZ, Block.UPDATE_CLIENTS);
		}
		for (int x = -1; x <= 1; x++) {
			level.setBlock(camp.at(x, 0, 3), alongX, Block.UPDATE_CLIENTS);
		}
		// the chief's tent at the back, facing the gate; the loot pile on the right, facing the fire; tents round
		Placer chief = new Placer(camp.at(0, 0, 5), gate, 7);
		place(level, chiefTent(level, f), chief);
		Placer loot = new Placer(camp.at(7, 0, 0), gate.getCounterClockWise(), 7);
		List<BlockPos> storage = place(level, lootPile(level, f), loot);
		Blueprint tent = tent(level, f);
		place(level, tent, new Placer(camp.at(-6, 0, 0), gate.getClockWise(), 5));
		place(level, tent, new Placer(camp.at(-4, 0, -6), gate.getOpposite(), 5));
		place(level, tent, new Placer(camp.at(4, 0, -6), gate.getOpposite(), 5));
		// a few hay bales by the loot pile
		for (int i = 0; i < 3; i++) {
			BlockPos at = camp.at(5 + random.nextInt(2), 0, 5 + random.nextInt(3));
			if (level.getBlockState(at).isAir()) {
				level.setBlock(at, Blocks.HAY_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		BlockPos chest = chief.at(CHIEF_CHEST);
		stockChiefChest(level, chest, random);
		return new Layout(fire, storage, loot.at(LOOT_DEPOT), chief.at(CHIEF_SPOT), chest);
	}

	/** Levels the camp's ground to {@code fire}'s height and clears the air above it (plants, leaves, snow). */
	private static void level(ServerLevel level, BlockPos fire) {
		int floor = fire.getY();
		for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
			for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
				if (dx * dx + dz * dz > CLEAR_RADIUS * CLEAR_RADIUS) {
					continue;
				}
				BlockPos column = fire.offset(dx, 0, dz);
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockState surface = level.getBlockState(top.below());
				if (!Storehouse.ground(surface) || surface.is(BlockTags.BASE_STONE_OVERWORLD)) {
					surface = Blocks.COARSE_DIRT.defaultBlockState();
				}
				int ground = top.getY() - 1;
				// raise low ground with dirt, or cut high ground down, to the floor
				for (int y = ground + 1; y < floor - 1; y++) {
					level.setBlock(column.atY(y), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int y = floor; y <= ground; y++) {
					level.setBlock(column.atY(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (ground != floor - 1) {
					level.setBlock(column.atY(floor - 1), surface, Block.UPDATE_CLIENTS);
				}
				// and clear the air above: plants, leaves, snow, overhangs
				for (int y = floor; y <= floor + CLEAR_HEIGHT; y++) {
					BlockPos at = column.atY(y);
					BlockState there = level.getBlockState(at);
					if (!there.isAir() && (there.canBeReplaced() || Castle.natural(there) || there.is(BlockTags.FLOWERS) || there.is(BlockTags.SAPLINGS))) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** The chief's hoard: emeralds, gold, iron, a saddle, a golden apple, meat. */
	private static void stockChiefChest(ServerLevel level, BlockPos chest, RandomSource random) {
		if (!(level.getBlockEntity(chest) instanceof Container c)) {
			return;
		}
		List<ItemStack> hoard = List.of(new ItemStack(Items.EMERALD, 6 + random.nextInt(9)), new ItemStack(Items.GOLD_INGOT, 4 + random.nextInt(7)),
			new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(11)), new ItemStack(Items.SADDLE), new ItemStack(Items.GOLDEN_APPLE),
			new ItemStack(Items.COOKED_BEEF, 8 + random.nextInt(9)), new ItemStack(Items.LEATHER, 4 + random.nextInt(6)));
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

	/** What rots away once a camp is cleared: its wool, carpets, hay, fences, skulls, lanterns, ladders and fire. */
	static boolean perishable(BlockState state) {
		return state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.HAY_BLOCK) || state.is(BlockTags.FENCES)
			|| state.is(BlockTags.FENCE_GATES) || state.is(Blocks.SKELETON_SKULL) || state.is(Blocks.LANTERN) || state.is(Blocks.LADDER)
			|| state.is(Blocks.CAMPFIRE) || state.is(BlockTags.WOODEN_SLABS);
	}
}
