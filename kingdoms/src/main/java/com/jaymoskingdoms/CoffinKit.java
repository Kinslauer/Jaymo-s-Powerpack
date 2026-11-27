package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.OldRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Move your coffin and shrine (Night's Blood, patch 6; Andy's plan: "you can move your coffin and shrine (they break into an
 * item)"). A player who breaks a coffin ({@link OldRules#coffin}: a bed boxed in by dark oak slabs) in survival gets the
 * {@link #COFFIN} item instead of a bed and slabs, and a coffin dressed as a shrine ({@link BloodPage#dressed}) takes its
 * skull, candles and framed blood bottle in with it (the bottle kept inside the item, the rest remade). Set down on the
 * ground it lays the black bed, its slabs and, if it was dressed, the shrine again. Hunters ({@link LairHunts}) find a coffin only
 * where it was last seen, so a moved one is out of their reach.
 */
public final class CoffinKit {
	/** The item. */
	public static Item COFFIN;
	/** The most slabs round the bed (two at each end), and the key of the count kept on the item. */
	static final int SLABS_MOST = 4;
	static final String SLABS = "slabs", DRESSED = "dressed";

	private CoffinKit() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("coffin"));
		COFFIN = Registry.register(BuiltInRegistries.ITEM, key, new Kit(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(COFFIN));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || sp.isCreative() || !state.is(BlockTags.BEDS)
				|| !OldRules.coffin(server, pos)) {
				return true;
			}
			ItemStack kit = pack(server, pos);
			if (kit.isEmpty()) {
				return true;
			}
			sp.getInventory().placeItemBackInInventory(kit, net.minecraft.util.Prediction.SERVER_ONLY);
			return false;
		});
	}

	// ---- what a coffin is made of ---------------------------------------------------------------------------------------

	/** The head of the bed that {@code part} is half of, or null if it isn't a whole bed. */
	static @Nullable BlockPos head(ServerLevel level, BlockPos part) {
		BlockState s = level.getBlockState(part);
		if (!s.is(BlockTags.BEDS) || !s.hasProperty(AbstractBedBlock.PART)) {
			return null;
		}
		Direction facing = s.getValue(AbstractBedBlock.FACING);
		return s.getValue(AbstractBedBlock.PART) == BedPart.HEAD ? part : part.relative(facing);
	}

	private static Direction facing(ServerLevel level, BlockPos head) {
		return level.getBlockState(head).getValue(AbstractBedBlock.FACING);
	}

	/** The dark oak slabs touching the bed (not counting any further off). */
	private static List<BlockPos> slabs(ServerLevel level, BlockPos head, BlockPos foot) {
		Set<BlockPos> found = new LinkedHashSet<>();
		for (BlockPos cell : new BlockPos[] {head, foot}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = cell.relative(d);
				if (level.getBlockState(p).is(Blocks.DARK_OAK_SLAB)) {
					found.add(p);
				}
			}
		}
		return new ArrayList<>(found);
	}

	private static AABB shrineBox(BlockPos head) {
		return new AABB(head).inflate(BloodPage.SHRINE_SPAN + 0.5).expandTowards(0.0, 2.0, 0.0);
	}

	private static @Nullable ItemFrame bottleFrame(ServerLevel level, BlockPos head) {
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, shrineBox(head))) {
			if (!frame.getItem().isEmpty() && BuiltInRegistries.ITEM.getKey(frame.getItem().getItem()).equals(BloodPage.BLOOD_BOTTLE)) {
				return frame;
			}
		}
		return null;
	}

	private static List<BlockPos> shrineBlocks(ServerLevel level, BlockPos head) {
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(head.offset(-BloodPage.SHRINE_SPAN, -1, -BloodPage.SHRINE_SPAN), head.offset(BloodPage.SHRINE_SPAN, 3, BloodPage.SHRINE_SPAN))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.CANDLES) || s.is(BlockTags.SKULLS)) {
				found.add(p.immutable());
			}
		}
		return found;
	}

	private static void clear(ServerLevel level, BlockPos p) {
		level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	// ---- breaking it into an item ---------------------------------------------------------------------------------------

	/** Takes the coffin whose bed {@code part} is half of down into one item (empty if it can't be). */
	static ItemStack pack(ServerLevel level, BlockPos part) {
		BlockPos head = head(level, part);
		if (head == null || !level.getBlockState(head).is(BlockTags.BEDS)) {
			return ItemStack.EMPTY;
		}
		BlockPos foot = head.relative(facing(level, head).getOpposite());
		List<BlockPos> slabs = slabs(level, head, foot);
		boolean dressed = BloodPage.dressed(level, head);
		ItemStack kit = new ItemStack(COFFIN);
		CompoundTagBuilder data = new CompoundTagBuilder();
		data.put(SLABS, Math.min(SLABS_MOST, Math.max(slabs.size(), 2)));
		if (dressed) {
			data.put(DRESSED, 1);
			ItemFrame frame = bottleFrame(level, head);
			if (frame != null) {
				kit.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(frame.getItem().copy())));
				frame.discard();
				Block.popResource(level, frame.blockPosition(), new ItemStack(Items.ITEM_FRAME));
			}
			boolean skull = false;
			int candles = 0;
			for (BlockPos p : shrineBlocks(level, head)) {
				BlockState s = level.getBlockState(p);
				if (s.is(BlockTags.SKULLS) && !skull) {
					skull = true;
					clear(level, p);
				} else if (s.is(BlockTags.CANDLES) && candles < 2) {
					candles++;
					clear(level, p);
				} else {
					level.destroyBlock(p, true);
				}
			}
		}
		kit.set(DataComponents.CUSTOM_DATA, CustomData.of(data.build()));
		for (BlockPos p : slabs) {
			clear(level, p);
		}
		clear(level, head);
		clear(level, foot);
		level.playSound(null, head, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
		return kit;
	}

	/** Hunters tear the coffin at {@code part} down: everything drops as it would if broken piece by piece. */
	static void wreck(ServerLevel level, BlockPos part) {
		BlockPos head = head(level, part);
		if (head == null) {
			return;
		}
		BlockPos foot = head.relative(facing(level, head).getOpposite());
		ItemFrame frame = bottleFrame(level, head);
		if (frame != null) {
			Block.popResource(level, frame.blockPosition(), frame.getItem().copy());
			Block.popResource(level, frame.blockPosition(), new ItemStack(Items.ITEM_FRAME));
			frame.discard();
		}
		for (BlockPos p : shrineBlocks(level, head)) {
			level.destroyBlock(p, true);
		}
		for (BlockPos p : slabs(level, head, foot)) {
			level.destroyBlock(p, true);
		}
		level.destroyBlock(head, true);
		level.destroyBlock(foot, true);
	}

	// ---- laying it down ---------------------------------------------------------------------------------------------------

	private static boolean open(Level level, BlockPos p) {
		return level.getBlockState(p).canBeReplaced() && level.getFluidState(p).isEmpty();
	}

	private static boolean floor(Level level, BlockPos p) {
		return level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
	}

	/** The coffin item: laid on the ground it sets the black bed, its slabs, and the shrine it carried. */
	static final class Kit extends Item {
		Kit(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos foot = context.getClickedPos().relative(context.getClickedFace());
			if (context.getClickedFace().getAxis().isVertical() && !open(level, context.getClickedPos())) {
				foot = context.getClickedPos().relative(context.getClickedFace());
			} else if (open(level, context.getClickedPos())) {
				foot = context.getClickedPos();
			}
			Direction dir = context.getHorizontalDirection();
			BlockPos head = foot.relative(dir);
			if (!open(level, foot) || !open(level, head) || !floor(level, foot) || !floor(level, head)) {
				return InteractionResult.FAIL;
			}
			Direction left = dir.getCounterClockWise(), right = dir.getClockWise();
			List<BlockPos> sides = new ArrayList<>();
			for (BlockPos cell : new BlockPos[] {head, foot}) {
				for (Direction d : new Direction[] {left, right}) {
					if (open(level, cell.relative(d))) {
						sides.add(cell.relative(d));
					}
				}
			}
			ItemStack stack = context.getItemInHand();
			CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
			int want = Math.max(2, data.copyTag().getIntOr(SLABS, 2));
			if (sides.size() < Math.min(2, want)) {
				return InteractionResult.FAIL;
			}
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			Block bed = Blocks.BED.pick(DyeColor.BLACK);
			server.setBlock(foot, bed.defaultBlockState().setValue(AbstractBedBlock.FACING, dir).setValue(AbstractBedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
			server.setBlock(head, bed.defaultBlockState().setValue(AbstractBedBlock.FACING, dir).setValue(AbstractBedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
			for (int i = 0; i < Math.min(want, sides.size()); i++) {
				server.setBlock(sides.get(i), Blocks.DARK_OAK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
			}
			if (data.copyTag().getIntOr(DRESSED, 0) > 0) {
				dress(server, context.getPlayer() instanceof ServerPlayer sp ? sp : null, head, dir, stack);
			}
			server.playSound(null, head, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
			if (context.getPlayer() != null && !context.getPlayer().hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		/** The skull and two lit candles at the bed's head, and the framed bottle above; what can't go back is handed over. */
		private static void dress(ServerLevel level, @Nullable ServerPlayer player, BlockPos head, Direction dir, ItemStack kit) {
			BlockPos shrine = head.relative(dir);
			Direction left = dir.getCounterClockWise(), right = dir.getClockWise();
			List<ItemStack> spare = new ArrayList<>();
			if (open(level, shrine) && floor(level, shrine)) {
				level.setBlock(shrine, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rotation(dir)), Block.UPDATE_ALL);
			} else {
				spare.add(new ItemStack(Items.SKELETON_SKULL));
			}
			for (Direction d : new Direction[] {left, right}) {
				BlockPos c = shrine.relative(d);
				if (open(level, c) && floor(level, c)) {
					level.setBlock(c, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true), Block.UPDATE_ALL);
				} else {
					spare.add(new ItemStack(Items.CANDLE, 2));
				}
			}
			ItemContainerContents held = kit.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			ItemStack bottle = held.nonEmptyItemCopyStream().findFirst().orElse(ItemStack.EMPTY);
			BlockPos at = shrine.above();
			if (!bottle.isEmpty()) {
				if (open(level, at) && level.getBlockState(at.relative(dir)).isFaceSturdy(level, at.relative(dir), dir.getOpposite())) {
					ItemFrame frame = new ItemFrame(level, at, dir.getOpposite());
					frame.setItem(bottle);
					level.addFreshEntity(frame);
				} else {
					spare.add(bottle);
				}
			}
			for (ItemStack s : spare) {
				if (player != null) {
					player.getInventory().placeItemBackInInventory(s, net.minecraft.util.Prediction.SERVER_ONLY);
				} else {
					Block.popResource(level, head, s);
				}
			}
		}

		private static int rotation(Direction dir) {
			return switch (dir) {
				case NORTH -> 8;
				case EAST -> 12;
				case WEST -> 4;
				default -> 0;
			};
		}
	}

	/** A small writer for the item's data. */
	private static final class CompoundTagBuilder {
		private final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();

		void put(String key, int value) {
			this.tag.putInt(key, value);
		}

		net.minecraft.nbt.CompoundTag build() {
			return this.tag;
		}
	}
}
