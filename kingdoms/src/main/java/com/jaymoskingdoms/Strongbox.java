package com.jaymoskingdoms;

import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.jaymoskingdoms.money.MoneyItems;
import org.jspecify.annotations.Nullable;

/**
 * A strongbox at home (Andy's pick from Numismatic Overhaul, 2026-11-24; our own block, model and art). A small iron-bound
 * box of nine slots that takes only coin (and coin purses). It is locked to whoever set it down: they open it at a touch,
 * anyone else has to pick its lock (the Rotary game, {@link #LOCK_LEVEL}), and picking one where a realm's law holds and
 * its people see is burglary ({@link Theft#burgled}). It is slow to break for anyone but its owner, and hoppers can't
 * reach in. Night burglars go for a strongbox first ({@link NightBurglars}).
 */
public final class Strongbox {
	/** Its lock's level (Lockpicking's 1-5): a good one. */
	static final int LOCK_LEVEL = 4;
	/** Breaking it, for anyone but its owner: progress times this. */
	static final float BREAK_FACTOR = 0.05F;
	static final int SLOTS = 9;

	public static Block BLOCK;
	public static Item ITEM;
	public static BlockEntityType<Entity> ENTITY;

	private Strongbox() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("strongbox"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new BoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(5.0F, 50.0F).sound(SoundType.WOOD).setId(key)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("strongbox"), new BlockEntityType<>(Entity::new, java.util.Set.of(BLOCK)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("strongbox"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.BARREL, ITEM));
	}

	/** What a strongbox takes: coin, and purses of it. */
	static boolean takes(ItemStack stack) {
		return Currencies.isMoney(stack) || stack.is(MoneyItems.COIN_PURSE);
	}

	// ---- the block --------------------------------------------------------------------------------------------------

	static final class BoxBlock extends BaseEntityBlock {
		static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
		/** A low box, a little in from the block's sides. */
		static final VoxelShape SHAPE = Block.box(2.0, 0.0, 3.0, 14.0, 9.0, 13.0), SHAPE_X = Block.box(3.0, 0.0, 2.0, 13.0, 9.0, 14.0);

		BoxBlock(BlockBehaviour.Properties properties) {
			super(properties);
			this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new Entity(pos, state);
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.MODEL;
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
			super.setPlacedBy(level, pos, state, by, stack);
			if (by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof Entity box) {
				box.owner = player.getUUID();
				box.setChanged();
			}
		}

		@Override
		protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
			float progress = super.getDestroyProgress(state, player, level, pos);
			return level.getBlockEntity(pos) instanceof Entity box && !box.opens(player) ? progress * BREAK_FACTOR : progress;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof Entity box) || !(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			if (box.owner == null && !player.isCreative()) {
				// (one set down before it knew its owner, or by a machine: the first to open it has it)
				box.owner = player.getUUID();
				box.setChanged();
			}
			if (box.opens(player)) {
				sp.openMenu(box);
				return InteractionResult.SUCCESS;
			}
			server.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 0.9F);
			long seed = pos.asLong() ^ (box.owner == null ? 0L : box.owner.getMostSignificantBits());
			boolean playing = Lockpicking.pickLock(sp, pos, Lockpicking.Kind.ROTARY, LOCK_LEVEL, seed, () -> {
				if (server.getBlockEntity(pos) == box) {
					Theft.burgled(sp, server, pos, server.getBlockState(pos));
					sp.openMenu(box);
				}
			});
			if (!playing) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.strongbox.locked").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---- the box ----------------------------------------------------------------------------------------------------

	public static final class Entity extends BaseContainerBlockEntity implements WorldlyContainer {
		private static final int[] NO_SLOTS = new int[0];
		private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		@Nullable UUID owner;

		public Entity(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		/** {@code player} opens it at a touch: its owner (or anyone in creative). */
		boolean opens(Player player) {
			return player.isCreative() || player.getUUID().equals(this.owner);
		}

		@Override
		public int getContainerSize() {
			return SLOTS;
		}

		@Override
		protected NonNullList<ItemStack> getItems() {
			return this.items;
		}

		@Override
		protected void setItems(NonNullList<ItemStack> items) {
			this.items = items;
		}

		@Override
		protected Component getDefaultName() {
			return Component.translatable("container.jaymoskingdoms.strongbox");
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return takes(stack);
		}

		@Override
		protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
			return new Menu(id, inventory, this);
		}

		// (hoppers and the like can't reach in or out)
		@Override
		public int[] getSlotsForFace(Direction side) {
			return NO_SLOTS;
		}

		@Override
		public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
			return false;
		}

		@Override
		public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
			return false;
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(input, this.items);
			this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			ContainerHelper.saveAllItems(output, this.items);
			if (this.owner != null) {
				output.store("owner", UUIDUtil.CODEC, this.owner);
			}
		}
	}

	/**
	 * The box's screen: a one-row chest's (the client shows it as one), with slots that take only coin. The server's
	 * menu is the judge, so anything else put in on the client goes straight back.
	 */
	static final class Menu extends ChestMenu {
		Menu(int id, Inventory inventory, Container box) {
			super(MenuType.GENERIC_9x1, id, inventory, box, 1);
			for (int i = 0; i < SLOTS; i++) {
				Slot old = this.slots.get(i);
				Slot coin = new Slot(box, i, old.x, old.y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return takes(stack);
					}
				};
				coin.index = i;
				this.slots.set(i, coin);
			}
		}
	}
}
