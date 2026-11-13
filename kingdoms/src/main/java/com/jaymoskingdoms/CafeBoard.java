package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.578) Andy picked Cozy Cafe's cafe (Chakyl's mod, LGPL; remade as our own, nothing copied): a <b>menu board</b>, a
 * small chalkboard on legs that stands on a counter. Whoever sets it down owns it. Its 27 slots hold one of each dish or
 * drink on the menu (a sample: it stays on the board, you serve from your own stock); sneak-use with an empty hand
 * changes its prices (cheap, fair, dear). While its owner is near in opening hours, customers come ({@link Cafe}).
 *
 * <p>A board nobody owns is a town's: the ones in tavern blueprints. It fills itself with the tavern's fare and sells a
 * hot meal to anyone passing while a cook (or, failing that, some townsfolk) keeps the bar, up to {@link #TOWN_SALES} a
 * day, priced by the town's food index ({@link Meals#index}). It's read-only: click a dish to buy one.
 */
public final class CafeBoard {
	/** Slots on a board. */
	static final int SLOTS = 27;
	/** A town board's sales a day. */
	static final int TOWN_SALES = 24;
	/** How far a town board's keeper may be. */
	static final double KEEPER_RANGE = 12.0;
	/** The cheapest a dish ever sells for (milli). */
	static final long LEAST_PRICE = 100;
	/** What a dish without food values sells for (milli). */
	static final long PLAIN_PRICE = 250;
	/** What a drinking potion sells for before its brew grade (milli). */
	static final long TONIC_PRICE = 500;
	/** Jaymo's Powers' brew grade on a potion (Crude to Masterwork), read by id so there's no tie to Powers. */
	static final Identifier BREW_GRADE = Identifier.fromNamespaceAndPath("jaymospowers", "brew_grade");
	/** Freshness (Kitchen's clock): above this share left it's fresh, above the next stale, else nearly off; their price shares. */
	static final float FRESH = 0.6F, STALE = 0.25F;
	static final double STALE_PRICE = 0.75, OFF_PRICE = 0.4;

	/** A tavern's fare, the first of these that exist (Kitchen's own where it's loaded). */
	static final List<String> TAVERN_FARE = List.of("minecraft:bread", "minecraft:baked_potato", "minecraft:cooked_beef", "minecraft:cooked_chicken",
		"minecraft:mushroom_stew", "minecraft:pumpkin_pie", "jaymoskitchen:ale", "jaymoskitchen:cider", "jaymoskitchen:mead", "jaymoskitchen:berry_wine",
		"jaymoskitchen:bacon_and_eggs", "jaymoskitchen:carrot_soup", "jaymoskitchen:borscht", "jaymoskitchen:apple_pie_slice", "jaymoskitchen:beef_patty",
		"jaymoskitchen:baked_cod_stew");

	public static Block BOARD;
	public static Item BOARD_ITEM;
	public static BlockEntityType<Entity> ENTITY;

	private CafeBoard() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("menu_board"));
		BOARD = Registry.register(BuiltInRegistries.BLOCK, key, new BoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(1.0F).sound(SoundType.WOOD).noOcclusion().setId(key)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("menu_board"), new BlockEntityType<>(Entity::new, java.util.Set.of(BOARD)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("menu_board"));
		BOARD_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BOARD, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.SMOKER, BOARD_ITEM));
	}

	// ---- prices ------------------------------------------------------------------------------------------------------

	/** The board's prices: a share of the fair price, and how it sways tips. */
	enum Markup {
		CHEAP(0.75, 1.3), FAIR(1.0, 1.0), DEAR(1.5, 0.6);

		final double price, tips;

		Markup(double price, double tips) {
			this.price = price;
			this.tips = tips;
		}

		Markup next() {
			return values()[(this.ordinal() + 1) % values().length];
		}

		Component title() {
			return Component.translatable("cafe.jaymoskingdoms.markup." + this.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/** A dish's fair price (milli) by how filling it is. */
	static long fair(ItemStack stack) {
		if (stack.is(Items.POTION)) {
			return TONIC_PRICE;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return PLAIN_PRICE;
		}
		return Math.max(LEAST_PRICE, Math.round(30.0 * food.nutrition() + 20.0 * food.saturation()));
	}

	/**
	 * (0.7.578, patch 8) Quality sells: Kitchen's cooking grade (read off the dish's name, so no hard tie to Kitchen) sets
	 * a share of the price. Perfect sells for half again as much; burnt, charred or undercooked for half. A brewed potion
	 * sells by its brew grade instead ({@link #brew}).
	 */
	static double quality(ItemStack stack) {
		String brew = brew(stack);
		if (brew != null) {
			return switch (brew) {
				case "crude" -> 0.6;
				case "fine" -> 1.3;
				case "expert" -> 1.7;
				case "masterwork" -> 2.5;
				default -> 1.0;
			};
		}
		Component name = stack.get(DataComponents.ITEM_NAME);
		if (name != null && name.getContents() instanceof TranslatableContents t && t.getKey().startsWith("quality.jaymoskitchen.")) {
			String grade = t.getKey().substring("quality.jaymoskitchen.".length());
			return switch (grade) {
				case "perfect" -> 1.5;
				case "good" -> 1.0;
				default -> 0.5;
			};
		}
		return 1.0;
	}

	/** The brew grade on a potion ("crude" to "masterwork"), or null: none, or no Powers. */
	static @Nullable String brew(ItemStack stack) {
		net.minecraft.core.component.DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(BREW_GRADE);
		return type != null && stack.get(type) instanceof String grade ? grade : null;
	}

	/**
	 * How fresh it is sells too (Kitchen's freshness clock): fresh at full price, going stale for {@link #STALE_PRICE},
	 * nearly off for {@link #OFF_PRICE}. Undated food (or no Kitchen) counts as fresh.
	 */
	static double freshness(net.minecraft.world.level.Level level, ItemStack stack) {
		float left = KitchenCompat.freshness(level, stack);
		return left < 0.0F || left > FRESH ? 1.0 : left > STALE ? STALE_PRICE : OFF_PRICE;
	}

	static boolean perfect(ItemStack stack) {
		String brew = brew(stack);
		return brew != null ? brew.equals("masterwork") || brew.equals("expert") : quality(stack) > 1.0;
	}

	/** Can go on a menu: something to eat or drink. */
	static boolean dish(ItemStack stack) {
		if (!stack.isEmpty() && stack.is(Items.POTION)) {
			// (a brewed potion is a tonic to serve: graded drinks sell by their grade)
			return brew(stack) != null;
		}
		return !stack.isEmpty() && (stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE)) && !stack.is(Items.SPLASH_POTION)
			&& !stack.is(Items.LINGERING_POTION);
	}

	// ---- the block -----------------------------------------------------------------------------------------------------

	static final class BoardBlock extends BaseEntityBlock {
		static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
		private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, Block.box(2, 0, 6, 14, 15, 10), Direction.SOUTH, Block.box(2, 0, 6, 14, 15, 10),
			Direction.EAST, Block.box(6, 0, 2, 10, 15, 14), Direction.WEST, Block.box(6, 0, 2, 10, 15, 14));

		BoardBlock(BlockBehaviour.Properties properties) {
			super(properties);
			this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			// its face toward whoever sets it down
			return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPES.get(state.getValue(FACING));
		}

		@Override
		protected BlockState rotate(BlockState state, Rotation rotation) {
			return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		}

		@Override
		protected BlockState mirror(BlockState state, Mirror mirror) {
			return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
		public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level.isClientSide() ? null : createTickerHelper(type, ENTITY, (l, p, s, board) -> board.serverTick((ServerLevel)l));
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
			super.setPlacedBy(level, pos, state, by, stack);
			if (by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof Entity board) {
				board.owner = player.getUUID();
				board.ownerName = player.getName().getString();
				board.setChanged();
				player.sendOverlayMessage(Component.translatable("cafe.jaymoskingdoms.placed").withStyle(ChatFormatting.GOLD));
			}
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Entity board) {
				board.use(sp);
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---- the board ---------------------------------------------------------------------------------------------------

	public static final class Entity extends BlockEntity {
		final SimpleContainer menu = new SimpleContainer(SLOTS) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}

			@Override
			public boolean canPlaceItem(int slot, ItemStack stack) {
				return dish(stack);
			}

			@Override
			public void setChanged() {
				super.setChanged();
				Entity.this.setChanged();
			}
		};
		@Nullable UUID owner;
		String ownerName = "";
		Markup markup = Markup.FAIR;
		/** (patch 8) Its name, 0..100. */
		int reputation;
		/** A town board: filled with its fare yet; sold today, and which day. */
		boolean stocked;
		int sold;
		long soldDay = -1;
		/** Not saved: the seats and comfort round it, looked for now and then ({@link Cafe}). */
		@Nullable List<BlockPos> seats;
		int comfort;
		long lookedAt = Long.MIN_VALUE;

		public Entity(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		boolean town() {
			return this.owner == null;
		}

		/** The dishes on it. */
		List<ItemStack> dishes() {
			List<ItemStack> out = new ArrayList<>();
			for (int i = 0; i < SLOTS; i++) {
				ItemStack s = this.menu.getItem(i);
				if (dish(s)) {
					out.add(s);
				}
			}
			return out;
		}

		void serverTick(ServerLevel level) {
			if (level.getGameTime() % 20L != 0L) {
				return;
			}
			if (this.town() && !this.stocked) {
				this.stock();
			}
			if (!this.town()) {
				Cafe.boardTick(level, this);
			}
		}

		/** A town board fills itself with the tavern's fare. */
		private void stock() {
			int slot = 0;
			for (String id : TAVERN_FARE) {
				Identifier rl = Identifier.tryParse(id);
				if (rl == null || slot >= SLOTS) {
					continue;
				}
				Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
				if (item != null && item != Items.AIR) {
					this.menu.setItem(slot++, new ItemStack(item));
				}
			}
			this.stocked = true;
			this.setChanged();
		}

		void use(ServerPlayer player) {
			ServerLevel level = player.level();
			if (this.town()) {
				this.openShop(level, player);
				return;
			}
			if (!player.getUUID().equals(this.owner) && !player.isCreative()) {
				// someone else's cafe: what it is and whether it's open
				player.sendOverlayMessage(Component.translatable("cafe.jaymoskingdoms.theirs", this.ownerName, Cafe.tierName(this.reputation)));
				return;
			}
			if (player.isShiftKeyDown()) {
				this.markup = this.markup.next();
				this.setChanged();
				level.playSound(null, this.worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.2F);
				player.sendOverlayMessage(Component.translatable("cafe.jaymoskingdoms.markup", this.markup.title()).withStyle(ChatFormatting.GOLD));
				return;
			}
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inventory, this.menu, 3),
				Component.translatable("cafe.jaymoskingdoms.menu_title")));
			Cafe.status(level, this, player);
		}

		// ---- a town's board: buying a meal ----------------------------------------------------------------------------

		long townPrice(ServerLevel level, ItemStack stack) {
			Faction f = Factions.around(level.getServer(), level, this.worldPosition, 160.0);
			int index = f == null ? 100 : Meals.index(Factions.level(level.getServer()), f);
			return Math.max(LEAST_PRICE, fair(stack) * index / 100L);
		}

		/** Who keeps a town board's bar just now: a cook near it, else any grown townsfolk who isn't on guard; or null. */
		@Nullable Villager keeper(ServerLevel level) {
			AABB box = new AABB(this.worldPosition).inflate(KEEPER_RANGE);
			Villager best = null;
			double bestD = Double.MAX_VALUE;
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !McaCompat.isGuard(v) && !v.isSleeping())) {
				double d = v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition)) - (Meals.cook(v) ? 1.0e6 : 0.0);
				if (d < bestD) {
					bestD = d;
					best = v;
				}
			}
			return best;
		}

		private void openShop(ServerLevel level, ServerPlayer player) {
			long day = level.getOverworldClockTime() / 24000L;
			if (day != this.soldDay) {
				this.soldDay = day;
				this.sold = 0;
			}
			Villager keeper = this.keeper(level);
			if (keeper == null) {
				player.sendOverlayMessage(Component.translatable("cafe.jaymoskingdoms.town.nobody").withStyle(ChatFormatting.GRAY));
				return;
			}
			if (this.sold >= TOWN_SALES) {
				Speech.say(level, keeper, Component.translatable("cafe.jaymoskingdoms.town.sold_out"), Speech.WHITE, 12.0);
				return;
			}
			Faction f = Factions.around(level.getServer(), level, this.worldPosition, 160.0);
			SimpleContainer shown = new SimpleContainer(SLOTS);
			for (int i = 0; i < SLOTS; i++) {
				ItemStack s = this.menu.getItem(i);
				if (dish(s)) {
					ItemStack copy = s.copyWithCount(1);
					copy.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("cafe.jaymoskingdoms.town.price",
						Money.text(f, this.townPrice(level, s))).withStyle(ChatFormatting.GOLD))));
					shown.setItem(i, copy);
				}
			}
			BlockPos at = this.worldPosition;
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShopMenu(id, inventory, shown, this, at, keeper.getUUID()),
				Component.translatable("cafe.jaymoskingdoms.town.title")));
			Speech.sayTo(level, keeper, player, Component.translatable("cafe.jaymoskingdoms.town.hello." + level.getRandom().nextInt(3)), Speech.WHITE);
		}

		/** {@code player} buys the dish in {@code slot}. */
		void buy(ServerPlayer player, int slot, UUID keeperId) {
			ServerLevel level = player.level();
			ItemStack sample = this.menu.getItem(slot);
			if (!dish(sample) || this.isRemoved()) {
				return;
			}
			if (!(level.getEntity(keeperId) instanceof Villager keeper) || !keeper.isAlive()) {
				player.closeContainer();
				return;
			}
			if (this.sold >= TOWN_SALES) {
				Speech.say(level, keeper, Component.translatable("cafe.jaymoskingdoms.town.sold_out"), Speech.WHITE, 12.0);
				player.closeContainer();
				return;
			}
			Faction f = Factions.around(level.getServer(), level, this.worldPosition, 160.0);
			long price = this.townPrice(level, sample);
			if (!Money.charge(player, f, price)) {
				player.sendOverlayMessage(Component.translatable("cafe.jaymoskingdoms.town.cant_pay", Money.text(f, price)).withStyle(ChatFormatting.RED));
				return;
			}
			if (f != null) {
				Meals.pay(level.getServer(), f, (int)Math.max(1L, price / 100L));
			}
			this.sold++;
			this.setChanged();
			ItemStack meal = new ItemStack(sample.getItem());
			if (!player.getInventory().add(meal)) {
				player.drop(meal, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			keeper.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, this.worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.1F);
			Speech.sayTo(level, keeper, player, Component.translatable("cafe.jaymoskingdoms.town.thanks." + level.getRandom().nextInt(3)), Speech.WHITE);
		}

		// ---- saving ----------------------------------------------------------------------------------------------------

		@Override
		public void preRemoveSideEffects(BlockPos pos, BlockState state) {
			if (this.level != null && !this.town()) {
				Containers.dropContents(this.level, pos, this.menu);
			}
			if (this.level instanceof ServerLevel server) {
				Cafe.closed(server, pos);
			}
			super.preRemoveSideEffects(pos, state);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(input, items);
			for (int i = 0; i < SLOTS; i++) {
				this.menu.setItem(i, items.get(i));
			}
			this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
			this.ownerName = input.getStringOr("owner_name", "");
			this.markup = Markup.values()[Math.floorMod(input.getIntOr("markup", Markup.FAIR.ordinal()), Markup.values().length)];
			this.reputation = input.getIntOr("reputation", 0);
			this.stocked = input.getBooleanOr("stocked", false);
			this.sold = input.getIntOr("sold", 0);
			this.soldDay = input.getLongOr("sold_day", -1L);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
			for (int i = 0; i < SLOTS; i++) {
				items.set(i, this.menu.getItem(i));
			}
			ContainerHelper.saveAllItems(output, items);
			if (this.owner != null) {
				output.store("owner", UUIDUtil.CODEC, this.owner);
			}
			output.putString("owner_name", this.ownerName);
			output.putInt("markup", this.markup.ordinal());
			output.putInt("reputation", this.reputation);
			output.putBoolean("stocked", this.stocked);
			output.putInt("sold", this.sold);
			output.putLong("sold_day", this.soldDay);
		}
	}

	/** A town board's wares, read-only: a click on a dish buys one. */
	static final class ShopMenu extends ChestMenu {
		private final Entity board;
		private final BlockPos at;
		private final UUID keeper;

		ShopMenu(int id, Inventory inventory, SimpleContainer shown, Entity board, BlockPos at, UUID keeper) {
			super(MenuType.GENERIC_9x3, id, inventory, shown, 3);
			this.board = board;
			this.at = at;
			this.keeper = keeper;
		}

		@Override
		public void clicked(int slot, int button, ContainerInput input, Player player) {
			if (slot >= 0 && slot < SLOTS) {
				if (input == ContainerInput.PICKUP && player instanceof ServerPlayer sp) {
					this.board.buy(sp, slot, this.keeper);
				}
				return;
			}
			if (input == ContainerInput.QUICK_MOVE || input == ContainerInput.QUICK_CRAFT || input == ContainerInput.PICKUP_ALL) {
				return;
			}
			super.clicked(slot, button, input, player);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return !this.board.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.at)) <= 64.0;
		}
	}
}
