package com.jaymoskingdoms.sailing;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.28) Ship's Plans: kits of your own. Sneak-use blank (or old) plans on the helm of a ship you've built (lying in
 * the world: before she's launched, or at anchor) and they take her down, block for block, bow the way her helm faces.
 * Then use them on open water like any build kit: in survival she's laid out in {@link ShipFrameBlock frames} to fill
 * (planks for her hull, fences for rails and masts, sails, helm, oars, guns, chests; her lanterns, doors and other
 * fittings you put back yourself); in creative she's built exactly as she was (sneak for the bare frames). The plans
 * aren't used up, so one ship can be laid again and again.
 */
public final class ShipPlans {
	/** Where the design sits in the item's custom data. */
	private static final String TAG = "jaymoskingdoms_ship_plans";
	/** How deep her keel sits below the sea's top layer if she wasn't afloat when drawn. */
	private static final int DEFAULT_DRAFT = 1;
	/** A fence or log counts as a mast with at least this many stacked (else a rail, or a hull timber). */
	private static final int MAST_RUN = 3;

	private ShipPlans() {
	}

	/** A drawn ship: each block's place (keel at y 0, bow +z) and state, and how deep her keel sat. */
	record Design(List<BlockPos> at, List<BlockState> states, int draft) {
	}

	// ---- drawing her ----

	/** Draws the ship whose helm is at {@code helm}, or tells {@code player} why not. */
	static @Nullable Design draw(ServerLevel level, BlockPos helm, ServerPlayer player) {
		BlockState helmState = level.getBlockState(helm);
		if (!(helmState.getBlock() instanceof HelmBlock)) {
			return null;
		}
		List<BlockPos> found = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(helm);
		seen.add(helm.asLong());
		int x0 = helm.getX(), x1 = x0, y0 = helm.getY(), y1 = y0, z0 = helm.getZ(), z1 = z0;
		while (!open.isEmpty()) {
			BlockPos p = open.poll();
			found.add(p);
			if (found.size() > Ship.MOST_BLOCKS) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.too_big", Ship.MOST_BLOCKS));
				return null;
			}
			x0 = Math.min(x0, p.getX());
			x1 = Math.max(x1, p.getX());
			y0 = Math.min(y0, p.getY());
			y1 = Math.max(y1, p.getY());
			z0 = Math.min(z0, p.getZ());
			z1 = Math.max(z1, p.getZ());
			if (x1 - x0 >= Ship.LONGEST || y1 - y0 >= Ship.LONGEST || z1 - z0 >= Ship.LONGEST) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.too_long", Ship.LONGEST));
				return null;
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				BlockState there = level.getBlockState(q);
				if (seen.add(q.asLong()) && Ship.part(there)) {
					if (there.getBlock() instanceof ShipFrameBlock) {
						player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.finish_frame"));
						return null;
					}
					if (there.getDestroySpeed(level, q) < 0.0F) {
						player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.land"));
						return null;
					}
					open.add(q);
				}
			}
		}
		// how deep she sat: the sea's level beside her, over her keel
		LongOpenHashSet mine = new LongOpenHashSet();
		for (BlockPos p : found) {
			mine.add(p.asLong());
		}
		int sea = Integer.MIN_VALUE;
		for (BlockPos p : found) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = p.relative(d);
				if (!mine.contains(q.asLong()) && level.getFluidState(q).is(FluidTags.WATER)) {
					sea = Math.max(sea, q.getY());
				}
			}
		}
		int draft = sea == Integer.MIN_VALUE ? DEFAULT_DRAFT : Math.clamp(sea - y0, 0, Ship.LONGEST);
		// into her own frame: the helm's facing becomes forward (+z), her keel y 0
		Rotation turn = ShipKits.turn(helmState.getValue(HelmBlock.FACING));
		turn = turn.getRotated(turn).getRotated(turn);
		List<BlockPos> at = new ArrayList<>(found.size());
		List<BlockState> states = new ArrayList<>(found.size());
		for (BlockPos p : found) {
			BlockState state = level.getBlockState(p);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			BlockPos local = new BlockPos(p.getX() - helm.getX(), p.getY() - y0, p.getZ() - helm.getZ()).rotate(turn);
			at.add(local);
			states.add(state.rotate(turn));
		}
		return new Design(at, states, draft);
	}

	// ---- what her frames want ----

	/** The frames she's laid out in for survival (her fittings left out). */
	static List<ShipKits.Cell> frames(Design design) {
		Map<Long, BlockState> by = new HashMap<>();
		for (int i = 0; i < design.at().size(); i++) {
			by.put(design.at().get(i).asLong(), design.states().get(i));
		}
		List<ShipKits.Cell> cells = new ArrayList<>();
		for (int i = 0; i < design.at().size(); i++) {
			BlockPos p = design.at().get(i);
			BlockState state = design.states().get(i);
			ShipFrameBlock.Want want = want(state, p, by);
			if (want == null) {
				continue;
			}
			Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
				: Direction.NORTH;
			cells.add(new ShipKits.Cell(p.getX(), p.getY(), p.getZ(), want, facing));
		}
		return cells;
	}

	/** What frame a block of hers is drawn as (null: a fitting you put back yourself). */
	private static ShipFrameBlock.@Nullable Want want(BlockState state, BlockPos p, Map<Long, BlockState> by) {
		Block block = state.getBlock();
		if (block instanceof SailBlock) {
			return ShipFrameBlock.Want.SAIL;
		}
		if (block instanceof HelmBlock) {
			return ShipFrameBlock.Want.HELM;
		}
		if (block instanceof OarBlock) {
			return ShipFrameBlock.Want.OAR;
		}
		if (block instanceof ShipCannonBlock) {
			return ShipFrameBlock.Want.CANNON;
		}
		if (block instanceof ChestBlock || block instanceof BarrelBlock) {
			return ShipFrameBlock.Want.CHEST;
		}
		boolean fence = block instanceof FenceBlock, log = state.is(BlockTags.LOGS);
		if (fence || log) {
			if (run(p, by) >= MAST_RUN) {
				return ShipFrameBlock.Want.MAST;
			}
			if (fence) {
				return ShipFrameBlock.Want.RAIL;
			}
		}
		if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)) {
			return ShipFrameBlock.Want.HULL;
		}
		return null;
	}

	/** How many fences or logs stand stacked in the column through {@code p}. */
	private static int run(BlockPos p, Map<Long, BlockState> by) {
		int n = 1;
		for (Direction d : new Direction[] {Direction.UP, Direction.DOWN}) {
			BlockPos q = p.relative(d);
			while (true) {
				BlockState s = by.get(q.asLong());
				if (s == null || !(s.getBlock() instanceof FenceBlock || s.is(BlockTags.LOGS))) {
					break;
				}
				n++;
				q = q.relative(d);
			}
		}
		return n;
	}

	// ---- building her whole (creative) ----

	/** Every block of her put down as she was, at {@code where} turned {@code turn}; her hold bailed. */
	static void build(ServerLevel level, Design design, List<BlockPos> where, Rotation turn, @Nullable ServerPlayer player) {
		// the solid blocks first, so what hangs off them has something to hang from
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < where.size(); i++) {
			order.add(i);
		}
		order.sort((a, b) -> Boolean.compare(!full(design.states().get(a)), !full(design.states().get(b))));
		int helm = 0;
		for (int i : order) {
			BlockPos at = where.get(i);
			BlockState state = design.states().get(i).rotate(turn);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(at).is(FluidTags.WATER));
			}
			level.setBlock(at, state, Block.UPDATE_CLIENTS);
			if (state.getBlock() instanceof HelmBlock) {
				helm = i;
			}
		}
		ShipKits.placed(level, where.get(helm), player);
	}

	private static boolean full(BlockState state) {
		return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	// ---- on the item ----

	static void write(ItemStack stack, Design design) {
		ListTag palette = new ListTag();
		Map<BlockState, Integer> index = new HashMap<>();
		IntArrayList blocks = new IntArrayList();
		for (int i = 0; i < design.at().size(); i++) {
			BlockState state = design.states().get(i);
			Integer k = index.get(state);
			if (k == null) {
				k = palette.size();
				index.put(state, k);
				palette.add(NbtUtils.writeBlockState(state));
			}
			BlockPos p = design.at().get(i);
			blocks.add(p.getX());
			blocks.add(p.getY());
			blocks.add(p.getZ());
			blocks.add(k);
		}
		CompoundTag plans = new CompoundTag();
		plans.put("Palette", palette);
		plans.putIntArray("Blocks", blocks.toIntArray());
		plans.putInt("Draft", design.draft());
		// what her frames want, counted, for the tooltip (the client needn't read her blocks)
		CompoundTag needs = new CompoundTag();
		int fittings = design.at().size();
		for (ShipKits.Cell c : frames(design)) {
			needs.putInt(c.want().getSerializedName(), needs.getIntOr(c.want().getSerializedName(), 0) + 1);
			fittings--;
		}
		plans.put("Needs", needs);
		plans.putInt("Fittings", fittings);
		plans.putInt("Count", design.at().size());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG, plans));
	}

	static @Nullable CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getCompound(TAG).orElse(null);
	}

	static @Nullable Design read(ItemStack stack, HolderGetter<Block> blocks) {
		CompoundTag plans = tag(stack);
		if (plans == null) {
			return null;
		}
		ListTag paletteTag = plans.getListOrEmpty("Palette");
		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i)));
		}
		int[] raw = plans.getIntArray("Blocks").orElse(new int[0]);
		List<BlockPos> at = new ArrayList<>(raw.length / 4);
		List<BlockState> states = new ArrayList<>(raw.length / 4);
		for (int i = 0; i + 3 < raw.length; i += 4) {
			if (raw[i + 3] < 0 || raw[i + 3] >= palette.size()) {
				continue;
			}
			at.add(new BlockPos(raw[i], raw[i + 1], raw[i + 2]));
			states.add(palette.get(raw[i + 3]));
		}
		return at.isEmpty() ? null : new Design(at, states, plans.getIntOr("Draft", DEFAULT_DRAFT));
	}

	/** Blank plans, or a ship's: sneak-use on her helm to draw her, use on open water to lay her. */
	public static final class PlansItem extends Item {
		public PlansItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			if (!(level.getBlockState(pos).getBlock() instanceof HelmBlock)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			Design design = draw(server, pos, player);
			if (design == null) {
				return InteractionResult.FAIL;
			}
			write(context.getItemInHand(), design);
			level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
			level.playSound(null, pos, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8F, 1.0F);
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.plans_drawn", design.at().size()));
			return InteractionResult.SUCCESS;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (tag(stack) == null) {
				if (player instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.plans_blank"));
				}
				return InteractionResult.PASS;
			}
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
			ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
			Design design = read(stack, server.registryAccess().lookupOrThrow(Registries.BLOCK));
			if (design == null) {
				return InteractionResult.FAIL;
			}
			Direction bow = player.getDirection();
			boolean whole = player.getAbilities().instabuild && !player.isShiftKeyDown();
			List<BlockPos> where;
			if (whole) {
				where = ShipKits.room(server, design.at(), design.draft(), hit.getBlockPos(), bow);
				if (where != null) {
					build(server, design, where, ShipKits.turn(bow), sp);
				}
			} else {
				where = ShipKits.lay(server, frames(design), design.draft(), hit.getBlockPos(), bow);
			}
			if (where == null) {
				if (sp != null) {
					sp.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.kit_no_room"));
				}
				return InteractionResult.FAIL;
			}
			level.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1.2F,
				0.8F);
			level.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F,
				1.0F);
			if (sp != null && !whole) {
				sp.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.kit_laid"));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			CompoundTag plans = tag(stack);
			if (plans == null) {
				builder.accept(Component.translatable("ship.jaymoskingdoms.plans_blank").withStyle(ChatFormatting.GRAY));
				return;
			}
			builder.accept(Component.translatable("ship.jaymoskingdoms.plans_use", plans.getIntOr("Count", 0)).withStyle(ChatFormatting.GRAY));
			CompoundTag needs = plans.getCompoundOrEmpty("Needs");
			Map<ShipFrameBlock.Want, Integer> count = new EnumMap<>(ShipFrameBlock.Want.class);
			for (ShipFrameBlock.Want want : ShipFrameBlock.Want.values()) {
				int n = needs.getIntOr(want.getSerializedName(), 0);
				if (n > 0) {
					count.put(want, n);
				}
			}
			for (Map.Entry<ShipFrameBlock.Want, Integer> e : count.entrySet()) {
				builder.accept(Component.translatable("ship.jaymoskingdoms.kit_needs", e.getValue(), e.getKey().label()).withStyle(ChatFormatting.DARK_GRAY));
			}
			int fittings = plans.getIntOr("Fittings", 0);
			if (fittings > 0) {
				builder.accept(Component.translatable("ship.jaymoskingdoms.plans_fittings", fittings).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}
}
