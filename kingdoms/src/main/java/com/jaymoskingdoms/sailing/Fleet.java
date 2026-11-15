package com.jaymoskingdoms.sailing;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.42) Sailing, stage 3: ships we design ourselves for the crews that sail the seas on their own, built from
 * blueprints as the kits are ({@link ShipKits}): a pirate's sloop and brigantine, a kingdom's frigate, a merchant's cog.
 * Each is laid on open water out of sight, blocks and all, then cast off at once ({@link Ship#castOff}) with her
 * {@link Crew}. Also what her captain needs to sail her: open water ahead ({@link #heading}), her helm set for a heading
 * and a share of sail ({@link #steer}), the places her crew stand ({@link #stations}), and a wreck laid on the seabed.
 */
public final class Fleet {
	public enum Rig {
		SLOOP, BRIGANTINE, FRIGATE, COG,
		/** (0.7.112) Jaki's ships ({@link JakiShips}): a pirate's galleon and drakkar, a navy's war trireme, a merchant's cargo ship and trader. */
		GALLEON(JakiShips.Design.GALLEON), DRAKKAR(JakiShips.Design.DRAKKAR), WAR_TRIREME(JakiShips.Design.WAR_TRIREME),
		CARGO_SHIP(JakiShips.Design.CARGO_SHIP), TRADER(JakiShips.Design.TRADER),
		/** A realm's fishing smack ({@code Fishers}), drawn as her kit is ({@link ShipKits.Kind#FISHING_SMACK}). */
		SMACK,
		/**
		 * A realm's whaleship (Andy, 2026-11-15: "a dedicated large whaleship ... lots of room for storage and tryworks for
		 * the larger oceans"): our own, long and beamy, three masts, a hold lined with chests below her deck, and two
		 * tryworks amidships ({@link #WHALESHIP_WORKS}).
		 */
		WHALESHIP;

		/** Her design, if she's one of Jaki's (null: one of ours, drawn as the kits are). */
		final JakiShips.@Nullable Design jaki;

		Rig() {
			this(null);
		}

		Rig(JakiShips.@Nullable Design jaki) {
			this.jaki = jaki;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** What she's built of: her planks, her rails, her masts, and her flag (its cloth, and its emblem; null: plain). */
	public record Look(Block planks, Block rail, Block mast, Block flag, @Nullable Block emblem) {
	}

	/** How deep her keel sits below the sea's top layer, and how deep the water must be under her (blocks). */
	static final int KEEL = 1, DEPTH = 5;
	/** How far ahead (blocks) her captain looks for land, and how finely (blocks a step). */
	static final double LOOK = 18.0, LOOK_STEP = 3.0;
	/** (0.7.243) How many headings she looks along for a way out of a bay. */
	static final int OFFINGS = 16;
	/** The headings he tries round a blocked one (degrees off it), nearest first. */
	static final float[] SWINGS = {25.0F, -25.0F, 50.0F, -50.0F, 80.0F, -80.0F, 115.0F, -115.0F, 150.0F, -150.0F};
	/** He's on his heading within this (degrees), and puts the helm over past it. */
	static final float ON_HEADING = 4.0F;
	/** A wreck: the share of her timbers lost, and how much of her hull is left above the sand. */
	static final float WRECK_LOST = 0.3F;

	private Fleet() {
	}

	// ---- the designs ----

	/** The whaleship: her hull (half length, half beam), her deck's layer, her masts (row, height), her tryworks' rows, her hold's chests every this many rows. */
	static final int WHALESHIP_HALF_LENGTH = 21, WHALESHIP_HALF_BEAM = 5, WHALESHIP_DECK = 5, WHALESHIP_CHEST_EVERY = 2;
	static final int[][] WHALESHIP_MASTS = {{13, 16}, {1, 19}, {-11, 14}};
	public static final int[] WHALESHIP_WORKS = {5, 8};

	/** A design's cells, as the kits draw them (x across, y up from her keel, z along her, bow +z). */
	static List<ShipKits.Cell> cells(Rig rig) {
		List<ShipKits.Cell> cells = new ArrayList<>();
		switch (rig) {
			case SLOOP -> cells.addAll(ShipKits.design(ShipKits.Kind.SLOOP));
			case BRIGANTINE -> cells.addAll(ShipKits.design(ShipKits.Kind.BRIG));
			case SMACK -> cells.addAll(ShipKits.design(ShipKits.Kind.FISHING_SMACK));
			case COG -> {
				// a sloop's hull with no guns, and more hold
				for (ShipKits.Cell c : ShipKits.design(ShipKits.Kind.SLOOP)) {
					cells.add(c.want() == ShipFrameBlock.Want.CANNON ? new ShipKits.Cell(c.x(), c.y(), c.z(), ShipFrameBlock.Want.RAIL, Direction.NORTH) : c);
				}
				cells.add(new ShipKits.Cell(-1, 1, 2, ShipFrameBlock.Want.CHEST, Direction.NORTH));
			}
			case WHALESHIP -> whaleship(cells);
			case FRIGATE -> {
				ShipKits.Shape s = new ShipKits.Shape(11, 3, 3, 3, 3, true);
				LongOpenHashSet holes = new LongOpenHashSet();
				holes.add(BlockPos.asLong(-1, 3, 1));
				holes.add(BlockPos.asLong(1, 3, 1));
				ShipKits.hull(cells, s, holes);
				for (int x : new int[] {-1, 1}) {
					cells.add(new ShipKits.Cell(x, 1, 1, ShipFrameBlock.Want.HULL, Direction.NORTH));
					cells.add(new ShipKits.Cell(x, 2, 1, ShipFrameBlock.Want.CHEST, Direction.NORTH));
				}
				ShipKits.rail(cells, s, Map.of(-8, ShipFrameBlock.Want.OAR, 8, ShipFrameBlock.Want.OAR, -6, ShipFrameBlock.Want.CANNON,
					-2, ShipFrameBlock.Want.CANNON, 3, ShipFrameBlock.Want.CANNON, 6, ShipFrameBlock.Want.CANNON));
				ShipKits.mast(cells, s, 5, 12);
				ShipKits.mast(cells, s, -1, 13);
				ShipKits.mast(cells, s, -7, 10);
				cells.add(new ShipKits.Cell(0, 4, -9, ShipFrameBlock.Want.HELM, Direction.SOUTH));
			}
		}
		return cells;
	}

	/**
	 * The whaleship's cells: a deep hull with two hatches down to her hold (steps of planking and a chest), the hold
	 * lined both sides with chests, sweeps fore and aft, three masts, the helm aft. Her tryworks are added with her
	 * blocks ({@link #parts}).
	 */
	private static void whaleship(List<ShipKits.Cell> cells) {
		int deck = WHALESHIP_DECK;
		ShipKits.Shape s = new ShipKits.Shape(WHALESHIP_HALF_LENGTH, WHALESHIP_HALF_BEAM, 5, deck, deck, true);
		LongOpenHashSet holes = new LongOpenHashSet();
		int[] hatches = {-8, WHALESHIP_HALF_LENGTH - 6};
		for (int z : hatches) {
			for (int x : new int[] {-1, 1}) {
				holes.add(BlockPos.asLong(x, deck, z));
				holes.add(BlockPos.asLong(x, deck, z + 1));
			}
		}
		ShipKits.hull(cells, s, holes);
		// each hatch's steps up from her hold, forward: planks one up and one on, a chest last, then her deck
		for (int z : hatches) {
			for (int x : new int[] {-1, 1}) {
				for (int k = 1; k <= deck - 2; k++) {
					cells.add(new ShipKits.Cell(x, k, z - (deck - 2) + k, ShipFrameBlock.Want.HULL, Direction.NORTH));
				}
				cells.add(new ShipKits.Cell(x, deck - 1, z + 1, ShipFrameBlock.Want.CHEST, Direction.NORTH));
			}
		}
		// her hold: chests down both sides, a row apart so none join
		for (int z = -WHALESHIP_HALF_LENGTH + 4; z <= WHALESHIP_HALF_LENGTH - 7; z += WHALESHIP_CHEST_EVERY) {
			if (Math.abs(z - hatches[0]) <= 2 || Math.abs(z - hatches[1]) <= 2) {
				continue;
			}
			int w = s.width(1, z) - 1;
			if (w >= 2) {
				cells.add(new ShipKits.Cell(w, 1, z, ShipFrameBlock.Want.CHEST, Direction.WEST));
				cells.add(new ShipKits.Cell(-w, 1, z, ShipFrameBlock.Want.CHEST, Direction.EAST));
			}
		}
		int sweeps = WHALESHIP_HALF_LENGTH - 4;
		ShipKits.rail(cells, s, Map.of(-sweeps, ShipFrameBlock.Want.OAR, sweeps, ShipFrameBlock.Want.OAR));
		for (int[] m : WHALESHIP_MASTS) {
			ShipKits.mast(cells, s, m[0], m[1]);
		}
		cells.add(new ShipKits.Cell(0, deck + 1, -WHALESHIP_HALF_LENGTH + 2, ShipFrameBlock.Want.HELM, Direction.SOUTH));
	}

	/** One block of a ship as she's built: where (in her frame), what, and (0.7.112) the loot her barrel's filled with (null: none). */
	record Part(BlockPos at, BlockState state, @Nullable ResourceKey<LootTable> loot) {
		Part(BlockPos at, BlockState state) {
			this(at, state, null);
		}
	}

	/** Her blocks, in {@code look}: the design's cells filled, her guns loaded, and her flag flown from her tallest mast. */
	static List<Part> parts(ServerLevel level, Rig rig, Look look) {
		if (rig.jaki != null) {
			return JakiShips.parts(level, rig.jaki, look);
		}
		List<ShipKits.Cell> cells = cells(rig);
		List<Part> parts = new ArrayList<>();
		int mastZ = 0, top = Integer.MIN_VALUE;
		for (ShipKits.Cell c : cells) {
			BlockState state = switch (c.want()) {
				case HULL -> look.planks().defaultBlockState();
				case RAIL -> look.rail().defaultBlockState();
				case MAST -> look.mast().defaultBlockState();
				case SAIL -> Sailing.SAIL.defaultBlockState().setValue(SailBlock.FACING, c.facing());
				case HELM -> Sailing.HELM.defaultBlockState().setValue(HelmBlock.FACING, c.facing());
				case OAR -> Sailing.OAR.defaultBlockState().setValue(OarBlock.FACING, c.facing());
				case CANNON -> Sailing.CANNON.defaultBlockState().setValue(ShipCannonBlock.FACING, c.facing())
					.setValue(ShipCannonBlock.LOAD, ShipCannonBlock.Load.SHOT);
				case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, c.facing());
			};
			if (c.want() == ShipFrameBlock.Want.MAST && c.y() > top) {
				top = c.y();
				mastZ = c.z();
			}
			parts.add(new Part(new BlockPos(c.x(), c.y(), c.z()), state));
		}
		if (rig == Rig.WHALESHIP) {
			// her tryworks, amidships on her centreline
			for (int z : WHALESHIP_WORKS) {
				parts.add(new Part(new BlockPos(0, WHALESHIP_DECK + 1, z),
					com.jaymoskingdoms.whaling.Whaling.TRYWORKS.defaultBlockState().setValue(com.jaymoskingdoms.whaling.TryworksBlock.FACING, Direction.SOUTH)));
			}
		}
		// her colours: three cells aft of the masthead, two deep, the emblem in the middle of the upper row
		for (int dz = 1; dz <= 3; dz++) {
			for (int dy = 0; dy <= 1; dy++) {
				Block cloth = dz == 2 && dy == 0 && look.emblem() != null ? look.emblem() : look.flag();
				parts.add(new Part(new BlockPos(0, top - dy, mastZ - dz), cloth.defaultBlockState()));
			}
		}
		return parts;
	}

	/** A block by its id (wool and the like have no fields). */
	public static Block block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
	}

	// ---- launching ----

	/**
	 * Builds a {@code rig} in {@code look} on the water at {@code water} (her stern just beyond it, her bow toward {@code bow})
	 * and casts her off at once with {@code crew}: the ship, or null if there's no room or water deep enough there.
	 */
	public static @Nullable Ship launch(ServerLevel level, Rig rig, Look look, BlockPos water, Direction bow, Crew crew) {
		List<BlockPos> where = new ArrayList<>();
		BlockPos helm = lay(level, rig, look, water, bow, where);
		if (helm == null) {
			return null;
		}
		return launchLaid(level, helm, where, crew);
	}

	/**
	 * A ship already standing in the world (her blocks at {@code where}, laid by {@link #lay} or built up in a yard,
	 * {@link Slip}), her hold bailed dry and cast off with {@code crew}: the ship, or null if she wouldn't go (her blocks
	 * taken up again).
	 */
	public static @Nullable Ship launchLaid(ServerLevel level, BlockPos helm, List<BlockPos> where, Crew crew) {
		// her hold bailed dry, then away
		ShipKits.placed(level, helm, null);
		crew.born = level.getGameTime();
		Ship ship = Ship.castOff(level, helm, null, crew);
		if (ship == null) {
			// (she wouldn't go: take her up again)
			int sea = level.getSeaLevel() - 1;
			for (BlockPos at : where) {
				level.removeBlockEntity(at);
				level.setBlock(at, at.getY() <= sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
					Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
			}
			return null;
		}
		hold(ship, crew);
		return ship;
	}

	/** Her hold: the first chest aboard ((0.7.112) or barrel, on a ship with no chests). */
	private static void hold(Ship ship, Crew crew) {
		Hull h = ship.hull();
		if (h != null) {
			for (Ship.Piece p : h.pieces) {
				if (p.blockState().getBlock() instanceof ChestBlock) {
					crew.hold = BlockPos.asLong(p.x(), p.y(), p.z());
					break;
				}
				if (p.blockState().getBlock() instanceof BarrelBlock && crew.hold == 0L) {
					crew.hold = BlockPos.asLong(p.x(), p.y(), p.z());
				}
			}
		}
	}

	/** A ship already laid in the world with her helm at {@code helm} (an airship laid in the sky), cast off at once with {@code crew}; null if she wouldn't go. */
	public static @Nullable Ship castOff(ServerLevel level, BlockPos helm, Crew crew) {
		ShipKits.placed(level, helm, null);
		crew.born = level.getGameTime();
		Ship ship = Ship.castOff(level, helm, null, crew);
		if (ship != null) {
			hold(ship, crew);
		}
		return ship;
	}

	/** Whether the one at her wheel is down there, hurt but alive (he may get up and take it again). */
	public static boolean helmsmanDown(Ship ship) {
		Vec3 helm = stations(ship).helm();
		if (helm == null) {
			return false;
		}
		for (net.minecraft.world.entity.LivingEntity e : ship.crewAboard()) {
			Vec3 seat = e.getAttached(Ship.SEAT);
			if (seat != null && seat.distanceToSqr(helm) < 0.75 * 0.75 && e.isAlive() && com.jaymoskingdoms.Downed.isDowned(e)) {
				return true;
			}
		}
		return false;
	}

	/** Whether one of her crew stands at her wheel (a balloon's: let go of it, she drifts and comes down). */
	public static boolean wheelManned(Ship ship) {
		Vec3 helm = stations(ship).helm();
		if (helm == null) {
			return false;
		}
		for (net.minecraft.world.entity.LivingEntity e : ship.crewAboard()) {
			Vec3 seat = e.getAttached(Ship.SEAT);
			if (seat != null && seat.distanceToSqr(helm) < 0.75 * 0.75 && !com.jaymoskingdoms.Downed.isDowned(e)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * (0.7.112) Builds a {@code rig} in {@code look} on the water at {@code water} as {@link #launch} does, her blocks left
	 * in the world (where they went added to {@code where}): her helm, or null if there's no room or water deep enough there.
	 */
	public static @Nullable BlockPos lay(ServerLevel level, Rig rig, Look look, BlockPos water, Direction bow, List<BlockPos> where) {
		Slip slip = slip(level, rig, look, water, bow);
		if (slip == null) {
			return null;
		}
		where.addAll(slip.where);
		for (int i = 0; i < slip.size(); i++) {
			slip.place(level, i);
		}
		return slip.join(level);
	}

	/**
	 * A ship to be built where she'll float: her blocks (her boarding ladders and sweeps fitted, {@link ShipFittings}) and
	 * where each goes, in the order a shipwright lays them: her keel first, then up through her hull and decks layer by
	 * layer from amidships out, her masts and rigging as they come, her helm last of all (nobody casts her off half built).
	 */
	public static final class Slip {
		final List<Part> parts;
		final List<BlockPos> where;
		final Rotation turn;
		/** Where her frame's (0, 0, 0) is in the world, and her bow; with her rig and look, all that's needed to find her again. */
		public final BlockPos origin;
		public final Direction bow;

		private Slip(List<Part> parts, BlockPos origin, Direction bow) {
			this.parts = parts;
			this.origin = origin;
			this.bow = bow;
			this.turn = ShipKits.turn(bow);
			List<BlockPos> at = new ArrayList<>(parts.size());
			for (Part p : parts) {
				at.add(origin.offset(p.at().rotate(this.turn)));
			}
			this.where = at;
		}

		public int size() {
			return this.parts.size();
		}

		public BlockPos at(int i) {
			return this.where.get(i);
		}

		public List<BlockPos> where() {
			return List.copyOf(this.where);
		}

		/** Block {@code i} as it goes in (turned her way, dry). */
		public BlockState state(int i) {
			BlockState state = this.parts.get(i).state().rotate(this.turn);
			return state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
		}

		/** Whether block {@code i} is in already (its block there, however it's joined to its neighbours). */
		public boolean laid(ServerLevel level, int i) {
			return level.getBlockState(this.at(i)).is(this.state(i).getBlock());
		}

		/** Puts block {@code i} in (its barrel filled, if it has loot). */
		public void place(ServerLevel level, int i) {
			BlockPos at = this.at(i);
			level.setBlock(at, this.state(i), Block.UPDATE_CLIENTS);
			if (this.parts.get(i).loot() != null && level.getBlockEntity(at) instanceof RandomizableContainer box) {
				box.setLootTable(this.parts.get(i).loot(), level.getRandom().nextLong());
			}
		}

		/** All of her in (whatever's missing put back), joined up: fences to their neighbours, sails into one sheet. Her helm, or null if she has none. */
		public @Nullable BlockPos join(ServerLevel level) {
			BlockPos helm = null;
			for (int i = 0; i < this.size(); i++) {
				if (!this.laid(level, i)) {
					this.place(level, i);
				}
				if (this.state(i).getBlock() instanceof HelmBlock) {
					helm = this.at(i);
				}
			}
			for (BlockPos at : this.where) {
				BlockState state = level.getBlockState(at);
				BlockState next = Block.updateFromNeighbourShapes(state, level, at);
				if (next.getBlock() instanceof SailBlock) {
					next = SailBlock.connect(next, level, at);
				}
				if (next.hasProperty(BlockStateProperties.WATERLOGGED)) {
					next = next.setValue(BlockStateProperties.WATERLOGGED, false);
				}
				if (next != state) {
					level.setBlock(at, next, Block.UPDATE_CLIENTS);
				}
			}
			return helm;
		}
	}

	/** Her blocks in the order she's built (see {@link Slip}). */
	private static List<Part> building(ServerLevel level, Rig rig, Look look) {
		List<Part> parts = new ArrayList<>(ShipFittings.fit(parts(level, rig, look)));
		parts.sort(java.util.Comparator.<Part>comparingInt(p -> p.state().getBlock() instanceof HelmBlock ? 1 : 0)
			.thenComparingInt(p -> p.at().getY())
			.thenComparingInt(p -> Math.abs(p.at().getZ()))
			.thenComparingInt(p -> Math.abs(p.at().getX()))
			.thenComparingInt(p -> p.at().getZ())
			.thenComparingInt(p -> p.at().getX()));
		return parts;
	}

	/**
	 * A {@code rig} in {@code look} to build on the water at {@code water} (her stern just beyond it, her bow toward
	 * {@code bow}), nothing of her laid yet: null if there's no room or water deep enough there.
	 */
	public static @Nullable Slip slip(ServerLevel level, Rig rig, Look look, BlockPos water, Direction bow) {
		List<Part> parts = building(level, rig, look);
		if (parts.isEmpty()) {
			return null;
		}
		List<BlockPos> room = ShipKits.room(level, parts.stream().map(Part::at).toList(), KEEL, water, bow);
		if (room == null) {
			return null;
		}
		// deep water under all of her (her keel mustn't join the seabed)
		for (int i = 0; i < parts.size(); i++) {
			if (parts.get(i).at().getY() == 0 && !deep(level, room.get(i), 2)) {
				return null;
			}
		}
		return new Slip(parts, room.get(0).subtract(parts.get(0).at().rotate(ShipKits.turn(bow))), bow);
	}

	/** The {@link Slip} of a {@code rig} in {@code look} already begun at {@code origin}, bow toward {@code bow} (as it was saved). */
	public static Slip slipAt(ServerLevel level, Rig rig, Look look, BlockPos origin, Direction bow) {
		return new Slip(building(level, rig, look), origin, bow);
	}

	/** (0.7.112) The ship {@link #lay laid} with her helm at {@code helm}, her hold bailed and cast off with no crew (null: she wouldn't go). */
	public static @Nullable Ship castOff(ServerLevel level, BlockPos helm) {
		ShipKits.placed(level, helm, null);
		return Ship.castOff(level, helm, null, null);
	}

	/** Whether there's water {@code depth} blocks deep (or more) below {@code at}. */
	public static boolean deep(ServerLevel level, BlockPos at, int depth) {
		for (int d = 1; d <= depth; d++) {
			BlockPos p = at.below(d);
			if (!com.jaymoskingdoms.Loaded.made(level, p) || !level.getFluidState(p).is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Open sea round {@code near} (within {@code radius}), loaded, at least {@link #DEPTH} deep and with room for a ship
	 * on every side: the water's top block there, or null.
	 */
	public static @Nullable BlockPos openSea(ServerLevel level, BlockPos near, int radius, int tries, RandomSource random) {
		for (int t = 0; t < tries; t++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = radius * (0.35 + 0.65 * random.nextDouble());
			BlockPos column = near.offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			BlockPos top = surface(level, column);
			if (top != null && roomy(level, top, 14)) {
				return top;
			}
		}
		return null;
	}

	/** The top water block of the sea at {@code column} (null: not loaded, or not deep sea there). */
	public static @Nullable BlockPos surface(ServerLevel level, BlockPos column) {
		if (!level.isPositionEntityTicking(column)) {
			return null;
		}
		int sea = level.getSeaLevel() - 1;
		BlockPos top = new BlockPos(column.getX(), sea, column.getZ());
		if (!level.getFluidState(top).is(FluidTags.WATER) || !level.getBlockState(top.above()).isAir()) {
			return null;
		}
		return deep(level, top, DEPTH) ? top : null;
	}

	/** Deep water every few blocks out to {@code reach} round {@code top}. */
	public static boolean roomy(ServerLevel level, BlockPos top, int reach) {
		for (int dx = -reach; dx <= reach; dx += 7) {
			for (int dz = -reach; dz <= reach; dz += 7) {
				BlockPos p = top.offset(dx, 0, dz);
				if (!com.jaymoskingdoms.Loaded.made(level, p) || !level.getFluidState(p).is(FluidTags.WATER) || !deep(level, p, 3)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Where each of her crew stand: her captain at the helm, one inboard of each gun, and the rest about her deck. */
	public record Stations(@Nullable Vec3 helm, Map<Long, Vec3> guns, List<Vec3> deck) {
	}

	/** Her stations, worked out from her blocks. */
	public static Stations stations(Ship ship) {
		Hull h = ship.hull();
		if (h == null) {
			return new Stations(null, Map.of(), List.of());
		}
		Vec3 helm = null;
		int deckY = Integer.MAX_VALUE;
		if (h.helm >= 0) {
			Ship.Piece p = h.pieces.get(h.helm);
			helm = new Vec3(p.x(), p.y(), p.z() - 1.0);
			deckY = p.y();
		}
		java.util.LinkedHashMap<Long, Vec3> guns = new java.util.LinkedHashMap<>();
		LongOpenHashSet taken = new LongOpenHashSet();
		for (int i : h.cannons) {
			Ship.Piece p = h.pieces.get(i);
			Direction out = p.blockState().getValue(ShipCannonBlock.FACING);
			int x = p.x() - out.getStepX(), z = p.z() - out.getStepZ();
			if (h.at(x, p.y(), z) < 0 && h.at(x, p.y() + 1, z) < 0) {
				guns.put(BlockPos.asLong(p.x(), p.y(), p.z()), new Vec3(x, p.y(), z));
				taken.add(BlockPos.asLong(x, p.y(), z));
			}
		}
		List<Vec3> deck = new ArrayList<>();
		// (0.7.112) and on a ship whose helm is up on her quarterdeck (Jaki's), her open decks below it, above the water
		int lowest = Mth.ceil(h.minY + h.draft) + 1;
		for (int i = 0; i < h.pieces.size(); i++) {
			Ship.Piece p = h.pieces.get(i);
			if (h.hi[i] < 0.9F || h.hi[i] > 1.0F || h.at(p.x(), p.y() + 1, p.z()) >= 0 || h.at(p.x(), p.y() + 2, p.z()) >= 0
				|| p.blockState().is(BlockTags.CLIMBABLE)) {
				continue;
			}
			if (p.y() + 1 != deckY && !(p.y() + 1 < deckY && p.y() + 1 >= lowest && sky(h, p) && !p.blockState().is(BlockTags.WOOL))) {
				continue;
			}
			long cell = BlockPos.asLong(p.x(), p.y() + 1, p.z());
			if (taken.contains(cell) || helm != null && Math.abs(p.x() - helm.x) < 0.5 && Math.abs(p.z() - helm.z) < 0.5) {
				continue;
			}
			deck.add(new Vec3(p.x(), p.y() + h.hi[i], p.z()));
		}
		return new Stations(helm, guns, deck);
	}

	/** (0.7.112) Whether nothing of her is above a piece: open to the sky. */
	private static boolean sky(Hull h, Ship.Piece p) {
		for (int y = p.y() + 1; y <= h.maxY; y++) {
			if (h.at(p.x(), y, p.z()) >= 0) {
				return false;
			}
		}
		return true;
	}

	// ---- sailing her ----

	/** Whether her hull would have water enough under her at (x, z): open water at her waterline and her keel. */
	public static boolean open(ServerLevel level, Ship ship, double x, double z) {
		Hull h = ship.hull();
		if (h == null) {
			return false;
		}
		int keel = Mth.floor(ship.getY() + h.minY), top = Mth.floor(ship.getY() + h.minY + h.draft) + 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = keel; y <= top; y++) {
			p.set(Mth.floor(x), y, Mth.floor(z));
			if (!com.jaymoskingdoms.Loaded.made(level, p)) {
				return false;
			}
			BlockState there = level.getBlockState(p);
			if (!there.isAir() && !there.getCollisionShape(level, p).isEmpty()) {
				return false;
			}
		}
		// (and it's the sea there, not a gap over land)
		return level.getFluidState(p.set(Mth.floor(x), keel, Mth.floor(z))).is(FluidTags.WATER);
	}

	/** Whether the water's open along {@code yaw} for {@code reach} blocks ahead of her bow, her beam wide. */
	public static boolean clear(ServerLevel level, Ship ship, float yaw, double reach) {
		Hull h = ship.hull();
		if (h == null) {
			return false;
		}
		double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		double sx = fz, sz = -fx;
		double beam = Math.max(1.0, (h.maxX - h.minX) / 2.0);
		double bow = h.maxZ;
		for (double d = LOOK_STEP; d <= reach; d += LOOK_STEP) {
			double cx = ship.getX() + fx * (bow + d), cz = ship.getZ() + fz * (bow + d);
			for (double side : new double[] {0.0, beam, -beam}) {
				if (!open(level, ship, cx + sx * side, cz + sz * side)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * (0.7.243) A way out of a bay: of {@link #OFFINGS} headings, how far each runs in open water (her beam wide, up to
	 * {@code reach}); of those running at least half that, the end nearest (gx, gz), else the end of the longest (null:
	 * none runs two looks out).
	 */
	public static @Nullable BlockPos offing(ServerLevel level, Ship ship, double gx, double gz, double reach) {
		Hull h = ship.hull();
		if (h == null) {
			return null;
		}
		double beam = Math.max(1.0, (h.maxX - h.minX) / 2.0);
		BlockPos best = null, longest = null;
		double bestD = Double.MAX_VALUE, longestRun = 0.0;
		for (int i = 0; i < OFFINGS; i++) {
			float yaw = i * 360.0F / OFFINGS;
			double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
			double sx = fz, sz = -fx;
			double run = 0.0;
			runs:
			for (double d = LOOK_STEP; d <= reach; d += LOOK_STEP) {
				double cx = ship.getX() + fx * d, cz = ship.getZ() + fz * d;
				for (double side : new double[] {0.0, beam, -beam}) {
					if (!open(level, ship, cx + sx * side, cz + sz * side)) {
						break runs;
					}
				}
				run = d;
			}
			if (run < LOOK_STEP * 2) {
				continue;
			}
			double ex = ship.getX() + fx * run, ez = ship.getZ() + fz * run;
			BlockPos end = BlockPos.containing(ex, ship.getY(), ez);
			if (run > longestRun) {
				longestRun = run;
				longest = end;
			}
			double d = (ex - gx) * (ex - gx) + (ez - gz) * (ez - gz);
			if (run >= reach / 2.0 && d < bestD) {
				bestD = d;
				best = end;
			}
		}
		return best != null ? best : longest;
	}

	/** The heading nearest {@code want} with open water ahead (null: none, she's embayed and must back off). */
	public static @Nullable Float heading(ServerLevel level, Ship ship, float want) {
		if (clear(level, ship, want, LOOK)) {
			return want;
		}
		for (float swing : SWINGS) {
			if (clear(level, ship, want + swing, LOOK)) {
				return want + swing;
			}
		}
		return null;
	}

	/** The heading (yaw) from her to (x, z). */
	public static float bearing(Ship ship, double x, double z) {
		return (float)(Mth.atan2(-(x - ship.getX()), z - ship.getZ()) * Mth.RAD_TO_DEG);
	}

	/**
	 * Her helm, as a player at her wheel would set it: the rudder over toward {@code yaw}, her sails set toward
	 * {@code sail} (0 furled to 1), rowing if {@code row}, backing water if {@code astern}.
	 */
	public static Input steer(Ship ship, float yaw, double sail, boolean row, boolean astern) {
		float off = Mth.wrapDegrees(yaw - ship.getYRot());
		boolean left = off < -ON_HEADING, right = off > ON_HEADING;
		double set = ship.sailsSet();
		boolean forward = !astern && set < sail - 0.02, backward = astern || set > sail + 0.04;
		return new Input(forward, backward, left, right, row && !astern, false, false);
	}

	// ---- wrecks ----

	/**
	 * Lays a {@code rig} in {@code look} as a wreck on the seabed at {@code floor} (the first water block over the sand),
	 * her bow toward {@code bow}: some of her timbers gone, the rest waterlogged where they can be, and her hold's
	 * chests filled by {@code stock}. Where her chests went (none: there was no room).
	 */
	public static List<BlockPos> wreck(ServerLevel level, Rig rig, Look look, BlockPos floor, Direction bow, Consumer<Container> stock) {
		List<Part> parts = parts(level, rig, look);
		Rotation turn = ShipKits.turn(bow);
		RandomSource random = level.getRandom();
		// she lies a little into the sand, and heeled: her keel one down, the rest as she settled
		BlockPos origin = floor.below();
		List<BlockPos> chests = new ArrayList<>();
		for (Part part : parts) {
			BlockState state = part.state().rotate(turn);
			boolean chest = state.getBlock() instanceof ChestBlock;
			if (!chest && (random.nextFloat() < WRECK_LOST || state.getBlock() instanceof SailBlock && random.nextFloat() < 0.7F)) {
				continue;
			}
			BlockPos at = origin.offset(part.at().rotate(turn));
			if (!com.jaymoskingdoms.Loaded.made(level, at)) {
				continue;
			}
			BlockState there = level.getBlockState(at);
			if (!(there.isAir() || there.canBeReplaced() || there.getFluidState().is(FluidTags.WATER)) && part.at().getY() > 0) {
				continue;
			}
			if (state.getBlock() instanceof SailBlock) {
				state = look.flag().defaultBlockState();
			}
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(at).is(FluidTags.WATER));
			}
			level.setBlock(at, state, Block.UPDATE_CLIENTS);
			if (chest) {
				chests.add(at);
			}
		}
		for (BlockPos at : chests) {
			BlockEntity be = level.getBlockEntity(at);
			if (be instanceof Container box) {
				stock.accept(box);
				be.setChanged();
			}
		}
		return chests;
	}
}
