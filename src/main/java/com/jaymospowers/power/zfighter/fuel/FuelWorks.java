package com.jaymospowers.power.zfighter.fuel;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Fuel Works: a small works that turns coal into fuel cans ({@link PodFuel#FUEL_CAN}), {@link #COAL_PER_CAN} coal to a can,
 * a can every {@link #BREW_TICKS} ticks. It takes coal or charcoal from a hopper above or a hand on it, and a hopper under it (or an empty hand)
 * takes the cans. A can is grade 1 fuel like the coal it came from, worth a third more; blaze powder stays the rich fuel.
 */
public final class FuelWorks {
	// ---- tuning ----
	/** Coal a can takes, ticks a can takes, and how many cans the works holds before it stops. */
	public static final int COAL_PER_CAN = 3, BREW_TICKS = 100, MAX_CANS = 64;
	static final int INPUT = 0, OUTPUT = 1;

	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static Block BLOCK;
	public static BlockEntityType<Works> ENTITY;
	private static boolean ready;

	private FuelWorks() {
	}

	/** Whether {@code stack} is what the works takes. */
	public static boolean takes(ItemStack stack) {
		return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
	}

	/** Registers the block, its item, its block entity and its creative entry. Called once, from {@code PodTravel.init}. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("fuel_works"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new WorksBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL)
			.lightLevel(state -> state.getValue(LIT) ? 8 : 0).setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("fuel_works"));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("fuel_works"), new BlockEntityType<>(Works::new, Set.of(BLOCK)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(item));
	}

	private static final class WorksBlock extends Block implements EntityBlock {
		WorksBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(defaultBlockState().setValue(LIT, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(LIT);
		}

		@Override
		public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new Works(pos, state);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level.isClientSide() || type != ENTITY ? null : (BlockEntityTicker<T>)(BlockEntityTicker<Works>)Works::serverTick;
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
			if (!takes(stack)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level.getBlockEntity(pos) instanceof Works works && !level.isClientSide()) {
				ItemStack in = works.getItem(INPUT);
				int room = in.isEmpty() ? stack.getMaxStackSize() : in.getMaxStackSize() - in.getCount();
				if (room <= 0 || !in.isEmpty() && !ItemStack.isSameItemSameComponents(in, stack)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.fuel_works.full"));
					return InteractionResult.FAIL;
				}
				int moved = Math.min(room, stack.getCount());
				if (in.isEmpty()) {
					works.setItem(INPUT, stack.copyWithCount(moved));
				} else {
					in.grow(moved);
				}
				stack.shrink(moved);
				works.setChanged();
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level.getBlockEntity(pos) instanceof Works works && player instanceof ServerPlayer server) {
				ItemStack cans = works.removeItemNoUpdate(OUTPUT);
				if (cans.isEmpty()) {
					server.sendOverlayMessage(Component.translatable("message.jaymospowers.fuel_works.empty", COAL_PER_CAN));
				} else {
					server.getInventory().placeItemBackInInventory(cans, net.minecraft.util.Prediction.SERVER_ONLY);
					works.setChanged();
				}
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The works' coal and cans, and the brewing between them. Hoppers see it as a container: coal in at any side, cans out. */
	public static final class Works extends BlockEntity implements WorldlyContainer {
		private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
		private int progress;

		public Works(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		static void serverTick(Level level, BlockPos pos, BlockState state, Works works) {
			ItemStack in = works.items.get(INPUT), out = works.items.get(OUTPUT);
			boolean canBrew = in.getCount() >= COAL_PER_CAN && takes(in) && (out.isEmpty() || out.getCount() < MAX_CANS);
			if (canBrew) {
				works.progress++;
				if (works.progress >= BREW_TICKS) {
					works.progress = 0;
					in.shrink(COAL_PER_CAN);
					if (out.isEmpty()) {
						works.items.set(OUTPUT, new ItemStack(PodFuel.FUEL_CAN));
					} else {
						out.grow(1);
					}
					level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
					works.setChanged();
				}
			} else if (works.progress != 0) {
				works.progress = 0;
			}
			if (state.getValue(LIT) != canBrew) {
				level.setBlock(pos, state.setValue(LIT, canBrew), Block.UPDATE_ALL);
			}
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			items.clear();
			ContainerHelper.loadAllItems(input, items);
			progress = input.getIntOr("progress", 0);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			ContainerHelper.saveAllItems(output, items);
			output.putInt("progress", progress);
		}

		@Override
		public int getContainerSize() {
			return items.size();
		}

		@Override
		public boolean isEmpty() {
			return items.stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return items.get(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack taken = ContainerHelper.removeItem(items, slot, count);
			if (!taken.isEmpty()) {
				setChanged();
			}
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return ContainerHelper.takeItem(items, slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			items.set(slot, stack);
			setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(this, player);
		}

		@Override
		public void clearContent() {
			items.clear();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot == INPUT && takes(stack);
		}

		@Override
		public int[] getSlotsForFace(Direction side) {
			return new int[] {INPUT, OUTPUT};
		}

		@Override
		public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
			return canPlaceItem(slot, stack);
		}

		@Override
		public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
			return slot == OUTPUT;
		}
	}
}
