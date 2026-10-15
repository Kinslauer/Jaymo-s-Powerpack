package com.jaymoskingdoms.sailing;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.18) Build kits: a rowboat, a sloop and a brig, each a set of plans that, used on open water, lays out the whole
 * ship in {@link ShipFrameBlock frames} ahead of you, her bow the way you face. Fill every frame with what it wants and
 * she's built (her hold bailed dry), ready to cast off from her helm like any ship of blocks. (AlekiShips, MIT, builds its
 * ships from in-world frames; these designs and how they go together are our own.)
 */
public final class ShipKits {
	public enum Kind {
		ROWBOAT, SLOOP, BRIG;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT) + "_kit";
		}
	}

	/** One block of a design: where (x across, y up from her keel, z along her, forward +z), what, and facing which way. */
	record Cell(int x, int y, int z, ShipFrameBlock.Want want, Direction facing) {
	}

	/** A hull: half her length and beam, how many rows her bow narrows over, her deck's layer (-1: an open boat, walled
	 * up to {@code walls}), and whether her bottom is a row narrower than her sides (a vee). */
	record Shape(int halfLength, int halfBeam, int taper, int deck, int walls, boolean vee) {
		int width(int z) {
			int k = this.halfLength - z;
			if (k < this.taper) {
				return this.halfBeam * (k + 1) / (this.taper + 1);
			}
			if (z == -this.halfLength && this.halfBeam >= 2) {
				return this.halfBeam - 1;
			}
			return this.halfBeam;
		}

		int width(int y, int z) {
			return y == 0 && this.vee ? Math.max(0, this.width(z) - 1) : this.width(z);
		}

		boolean in(int x, int y, int z) {
			return z >= -this.halfLength && z <= this.halfLength && Math.abs(x) <= this.width(y, z);
		}

		int top() {
			return this.deck >= 0 ? this.deck : this.walls;
		}
	}

	/** How deep her keel sits below the sea's top layer when she's laid down. */
	private static final Map<Kind, Integer> KEEL = Map.of(Kind.ROWBOAT, 0, Kind.SLOOP, 1, Kind.BRIG, 1);
	private static final Map<Kind, List<Cell>> DESIGNS = new EnumMap<>(Kind.class);

	private ShipKits() {
	}

	static List<Cell> design(Kind kind) {
		return DESIGNS.computeIfAbsent(kind, ShipKits::draw);
	}

	private static List<Cell> draw(Kind kind) {
		List<Cell> cells = new ArrayList<>();
		LongOpenHashSet holes = new LongOpenHashSet();
		Shape s;
		switch (kind) {
			case ROWBOAT -> {
				s = new Shape(3, 1, 1, -1, 1, false);
				hull(cells, s, holes);
				cells.add(new Cell(0, 1, -1, ShipFrameBlock.Want.HELM, Direction.SOUTH));
				oars(cells, s, 2, 0);
			}
			case SLOOP -> {
				s = new Shape(6, 2, 2, 2, 2, true);
				holes.add(BlockPos.asLong(0, 2, 3));
				hull(cells, s, holes);
				cells.add(new Cell(0, 1, 3, ShipFrameBlock.Want.CHEST, Direction.NORTH));
				rail(cells, s, Map.of(-2, ShipFrameBlock.Want.OAR, 3, ShipFrameBlock.Want.OAR, -1, ShipFrameBlock.Want.CANNON));
				mast(cells, s, 1, 8);
				cells.add(new Cell(0, 3, -4, ShipFrameBlock.Want.HELM, Direction.SOUTH));
			}
			case BRIG -> {
				s = new Shape(9, 3, 3, 3, 3, true);
				holes.add(BlockPos.asLong(-1, 3, 0));
				holes.add(BlockPos.asLong(1, 3, 0));
				hull(cells, s, holes);
				for (int x : new int[] {-1, 1}) {
					cells.add(new Cell(x, 1, 0, ShipFrameBlock.Want.HULL, Direction.NORTH));
					cells.add(new Cell(x, 2, 0, ShipFrameBlock.Want.CHEST, Direction.NORTH));
				}
				rail(cells, s, Map.of(-6, ShipFrameBlock.Want.OAR, 6, ShipFrameBlock.Want.OAR, -4, ShipFrameBlock.Want.CANNON,
					0, ShipFrameBlock.Want.CANNON, 4, ShipFrameBlock.Want.CANNON));
				mast(cells, s, 3, 11);
				mast(cells, s, -3, 9);
				cells.add(new Cell(0, 4, -7, ShipFrameBlock.Want.HELM, Direction.SOUTH));
			}
		}
		return List.copyOf(cells);
	}

	/** Her bottom, her sides and her deck (but its hatches). */
	static void hull(List<Cell> cells, Shape s, LongOpenHashSet holes) {
		for (int y = 0; y <= s.top(); y++) {
			for (int z = -s.halfLength; z <= s.halfLength; z++) {
				int w = s.width(y, z);
				for (int x = -w; x <= w; x++) {
					boolean edge = !s.in(x + 1, y, z) || !s.in(x - 1, y, z) || !s.in(x, y, z + 1) || !s.in(x, y, z - 1);
					if ((y == 0 || edge || y == s.deck) && !holes.contains(BlockPos.asLong(x, y, z))) {
						cells.add(new Cell(x, y, z, ShipFrameBlock.Want.HULL, Direction.NORTH));
					}
				}
			}
		}
	}

	/** Oars on an open boat's gunwales, at row z on both sides. */
	private static void oars(List<Cell> cells, Shape s, int y, int z) {
		int w = s.width(z);
		cells.add(new Cell(w, y, z, ShipFrameBlock.Want.OAR, Direction.EAST));
		cells.add(new Cell(-w, y, z, ShipFrameBlock.Want.OAR, Direction.WEST));
	}

	/** The rail round her deck, with oars and guns in it at the rows given (both sides), facing outboard. */
	static void rail(List<Cell> cells, Shape s, Map<Integer, ShipFrameBlock.Want> slots) {
		int y = s.deck + 1;
		for (int z = -s.halfLength; z <= s.halfLength; z++) {
			int w = s.width(z);
			for (int x = -w; x <= w; x++) {
				int deckY = s.deck;
				boolean edge = !s.in(x + 1, deckY, z) || !s.in(x - 1, deckY, z) || !s.in(x, deckY, z + 1) || !s.in(x, deckY, z - 1);
				if (!edge) {
					continue;
				}
				ShipFrameBlock.Want slot = slots.get(z);
				if (slot != null && Math.abs(x) == w && w > 0) {
					cells.add(new Cell(x, y, z, slot, x > 0 ? Direction.EAST : Direction.WEST));
				} else {
					cells.add(new Cell(x, y, z, ShipFrameBlock.Want.RAIL, Direction.NORTH));
				}
			}
		}
	}

	/** A mast at row z, this tall above her deck, and its sail across her beam (2 blocks clear of the deck). */
	static void mast(List<Cell> cells, Shape s, int z, int height) {
		for (int y = s.deck + 1; y <= s.deck + height; y++) {
			cells.add(new Cell(0, y, z, ShipFrameBlock.Want.MAST, Direction.NORTH));
		}
		for (int y = s.deck + 3; y <= s.deck + height - 1; y++) {
			for (int x = -s.halfBeam; x <= s.halfBeam; x++) {
				if (x != 0) {
					cells.add(new Cell(x, y, z, ShipFrameBlock.Want.SAIL, Direction.SOUTH));
				}
			}
		}
	}

	/** What a design takes, counted. */
	static Map<ShipFrameBlock.Want, Integer> materials(Kind kind) {
		Map<ShipFrameBlock.Want, Integer> count = new EnumMap<>(ShipFrameBlock.Want.class);
		for (Cell c : design(kind)) {
			count.merge(c.want(), 1, Integer::sum);
		}
		return count;
	}

	// ---- laying her down ----

	/**
	 * Lays the frames of a {@code kind} on the water just beyond {@code water}, her bow toward {@code bow}: where they went,
	 * or null if there's no room.
	 */
	static @org.jspecify.annotations.Nullable List<BlockPos> lay(ServerLevel level, Kind kind, BlockPos water, Direction bow) {
		return lay(level, design(kind), KEEL.get(kind), water, bow);
	}

	/** (0.7.28) The same for any design (a kit's, or {@link ShipPlans plans} of a ship of your own), her keel this deep. */
	static @org.jspecify.annotations.Nullable List<BlockPos> lay(ServerLevel level, List<Cell> design, int keel, BlockPos water, Direction bow) {
		List<BlockPos> where = room(level, design.stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).toList(), keel, water, bow);
		if (where == null) {
			return null;
		}
		Rotation turn = turn(bow);
		for (int i = 0; i < design.size(); i++) {
			Cell c = design.get(i);
			BlockPos at = where.get(i);
			BlockState frame = Sailing.FRAME.defaultBlockState().setValue(ShipFrameBlock.WANT, c.want())
				.setValue(ShipFrameBlock.FACING, turn.rotate(c.facing()))
				.setValue(ShipFrameBlock.WATERLOGGED, level.getFluidState(at).is(FluidTags.WATER));
			level.setBlock(at, frame, Block.UPDATE_CLIENTS);
		}
		return where;
	}

	/** The turn that points a design's bow (+z, south) toward {@code bow}. */
	static Rotation turn(Direction bow) {
		for (Rotation r : Rotation.values()) {
			if (r.rotate(Direction.SOUTH) == bow) {
				return r;
			}
		}
		return Rotation.NONE;
	}

	/**
	 * Where each of a design's blocks ({@code cells}, keel at y 0, bow +z) goes on the water just beyond {@code water},
	 * her keel {@code keel} below the sea's top layer and her bow toward {@code bow}; null if any of it has no room.
	 */
	static @org.jspecify.annotations.Nullable List<BlockPos> room(ServerLevel level, List<BlockPos> cells, int keel, BlockPos water, Direction bow) {
		BlockPos.MutableBlockPos top = water.mutable();
		while (level.getFluidState(top.above()).is(FluidTags.WATER) && top.getY() < level.getMaxY()) {
			top.move(Direction.UP);
		}
		// her stern a block beyond where you look, her bow away from you
		int stern = 0;
		for (BlockPos c : cells) {
			stern = Math.min(stern, c.getZ());
		}
		BlockPos origin = top.below(keel).relative(bow, 1 - stern).immutable();
		Rotation turn = turn(bow);
		List<BlockPos> where = new ArrayList<>(cells.size());
		for (BlockPos c : cells) {
			BlockPos at = origin.offset(c.rotate(turn));
			if (!level.hasChunkAt(at)) {
				return null;
			}
			BlockState there = level.getBlockState(at);
			if (!(there.isAir() || there.canBeReplaced() || there.is(Blocks.WATER))) {
				return null;
			}
			where.add(at);
		}
		return where;
	}

	/** What a creative kit fills each frame with: oak planks and rails, spruce masts. */
	private static ItemStack stock(ShipFrameBlock.Want want) {
		return new ItemStack(switch (want) {
			case HULL -> Items.OAK_PLANKS;
			case RAIL -> Items.OAK_FENCE;
			case MAST -> Items.SPRUCE_FENCE;
			case SAIL -> Sailing.SAIL.asItem();
			case HELM -> Sailing.HELM.asItem();
			case OAR -> Sailing.OAR.asItem();
			case CANNON -> Sailing.CANNON.asItem();
			case CHEST -> Items.CHEST;
		});
	}

	/** (0.7.28) In creative: every frame just laid at {@code where} filled at once, and her hold bailed. */
	static void complete(ServerLevel level, List<BlockPos> where, @org.jspecify.annotations.Nullable ServerPlayer player) {
		for (BlockPos at : where) {
			BlockState frame = level.getBlockState(at);
			if (!(frame.getBlock() instanceof ShipFrameBlock)) {
				continue;
			}
			ShipFrameBlock.Want want = frame.getValue(ShipFrameBlock.WANT);
			BlockState next = ShipFrameBlock.fills(want, frame.getValue(ShipFrameBlock.FACING), stock(want));
			if (next != null) {
				level.setBlock(at, next, Block.UPDATE_CLIENTS);
			}
		}
		// then joined up: fences to their neighbours, sails into one sheet
		for (BlockPos at : where) {
			BlockState state = level.getBlockState(at);
			BlockState next = Block.updateFromNeighbourShapes(state, level, at);
			if (next.getBlock() instanceof SailBlock) {
				next = SailBlock.connect(next, level, at);
			}
			if (next != state) {
				level.setBlock(at, next, Block.UPDATE_CLIENTS);
			}
		}
		placed(level, where.get(0), player);
	}

	/** After a frame's filled at {@code pos}: how much is left, and once nothing is, her hold bailed dry. */
	static void placed(ServerLevel level, BlockPos pos, @org.jspecify.annotations.Nullable ServerPlayer player) {
		List<BlockPos> found = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(pos);
		seen.add(pos.asLong());
		int left = 0;
		while (!open.isEmpty() && found.size() <= Ship.MOST_BLOCKS) {
			BlockPos p = open.poll();
			found.add(p);
			if (level.getBlockState(p).getBlock() instanceof ShipFrameBlock) {
				left++;
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (seen.add(q.asLong()) && Ship.part(level.getBlockState(q))) {
					open.add(q);
				}
			}
		}
		if (left > 0) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.frames_left", left));
			}
			return;
		}
		// she's built: her timbers dried (a waterlogged chest in her hold would flood it again) and the water her sides
		// close in bailed out
		List<Ship.Piece> pieces = new ArrayList<>(found.size());
		for (BlockPos p : found) {
			BlockState state = level.getBlockState(p);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
				level.setBlock(p, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
			}
		}
		for (BlockPos p : found) {
			BlockPos d = p.subtract(pos);
			pieces.add(new Ship.Piece(Block.getId(level.getBlockState(p)), d.getX(), d.getY(), d.getZ()));
		}
		Hull hull = new Hull(pieces);
		for (long cell : hull.enclosed) {
			BlockPos at = pos.offset(BlockPos.of(cell));
			BlockState there = level.getBlockState(at);
			if (there.is(Blocks.WATER) || there.canBeReplaced() && !there.getFluidState().isEmpty()) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 0.8F);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.built"));
		}
	}

	/** A kit: its plans, used on open water. */
	public static final class KitItem extends Item {
		private final Kind kind;

		public KitItem(Kind kind, Item.Properties properties) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
			if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
				if (player instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.kit_on_water"));
				}
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			List<BlockPos> where = lay(server, this.kind, hit.getBlockPos(), player.getDirection());
			if (where == null) {
				if (player instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.kit_no_room"));
				}
				return InteractionResult.FAIL;
			}
			Vec3 at = hit.getLocation();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1.2F, 0.8F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (player instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.kit_laid"));
			}
			// (0.7.28) in creative she's built whole at once (sneak for the bare frames)
			if (player.getAbilities().instabuild && !player.isShiftKeyDown()) {
				complete(server, where, player instanceof ServerPlayer p ? p : null);
			}
			player.getItemInHand(hand).consume(1, player);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("ship.jaymoskingdoms.kit_use").withStyle(ChatFormatting.GRAY));
			for (Map.Entry<ShipFrameBlock.Want, Integer> e : materials(this.kind).entrySet()) {
				builder.accept(Component.translatable("ship.jaymoskingdoms.kit_needs", e.getValue(), e.getKey().label()).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}
}
