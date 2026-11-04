package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) Building from a design yourself, in survival, from your own materials: our own take on a schematic
 * builder, through designs only (saved with the {@link Surveyor}, or uploaded from a schematic file, see {@link Designs}).
 * (0.7.488) Everything is done at the table's screen ({@link MasonNet}, {@code MasonScreen}), no commands:
 * <ul>
 * <li>Use the <b>mason's table</b> empty-handed: its screen lists the schematic files in your game's {@code schematics}
 * folder (a button opens the folder to drop files in) and the designs already on the world. Pick one and draw it on a
 * blank <b>building plan</b> (paper and charcoal) you carry; a file of yours is sent up first. The screen also saves what
 * you marked with a surveyor's rod as a design, offers a design to the settlement you're in ({@link Commissions}), and
 * shows the table's progress and the materials still needed.</li>
 * <li>Use the plan on the ground to lay the building out there (its lowest north-west corner on the block in front of
 * the face you clicked); sneak and use it to turn it a quarter. While you hold it, its outline shows in sparks.
 * (0.7.488) A laid plan stays standing as see-through ghost blocks everyone near can see ({@link PlanSites}): fill them
 * by hand, like a ship's build kit, and once 90% of its blocks are in it's built.</li>
 * <li>Use the plan on the table and it builds it, bottom up, a block at a time, from the chests, barrels and the like
 * touching it: the very block (planks are planks, stairs are stairs, though dirt does for grass and paths; the second
 * half of a door or bed comes with the first). What's already there is left be: a block in the way is skipped, a
 * matching one only turned to match. Nothing comes with the blocks: chests empty, signs blank, no water or fire.</li>
 * <li>Sneak-use the table empty-handed to take the plan back. Breaking the table drops the plan.</li>
 * </ul>
 * Blocks it lays count as placed by whoever put the plan in, so they're theirs to break ({@link Placed}).
 */
public final class MasonTable {
	/** Ticks between blocks laid; between looks in the chests when the last look found nothing needed. */
	static final int PLACE_TICKS = 5, WAIT_TICKS = 60;
	/** Blocks already there (or left out) gone past in one go. */
	static final int SKIP_PER_STEP = 64;
	/** How far from the table the middle of its plan may lie (blocks, across). */
	static final int REACH = 64;
	/** How often a held plan's outline shows (ticks); how far from it its holder may be. */
	static final int SHOW_TICKS = 20, SHOW_RANGE = 128;
	/** The materials listed when the table is asked. */
	static final int LIST_MATERIALS = 8;
	/** How a block is set: clients told, neighbours left as the design has them. */
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** Where a plan's details sit in its custom data. */
	static final String TAG = "jaymoskingdoms_plan";

	public static Item PLAN;
	public static Block TABLE;
	public static Item TABLE_ITEM;
	public static BlockEntityType<Entity> ENTITY;

	private MasonTable() {
	}

	static void register() {
		ResourceKey<Item> planKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("building_plan"));
		PLAN = Registry.register(BuiltInRegistries.ITEM, planKey, new Plan(new Item.Properties().setId(planKey).stacksTo(1)));
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("mason_table"));
		TABLE = Registry.register(BuiltInRegistries.BLOCK, key, new TableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.strength(2.5F).sound(SoundType.STONE).setId(key)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("mason_table"), new BlockEntityType<>(Entity::new, java.util.Set.of(TABLE)));
		ResourceKey<Item> tableKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("mason_table"));
		TABLE_ITEM = Registry.register(BuiltInRegistries.ITEM, tableKey, new BlockItem(TABLE, new Item.Properties().setId(tableKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.CARTOGRAPHY_TABLE, TABLE_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Surveyor.ROD, PLAN));
		ServerTickEvents.END_SERVER_TICK.register(MasonTable::showPlans);
	}

	// ---- the plan ---------------------------------------------------------------------------------------------------

	/** A written plan's details: its design, how it's turned, and where it's laid (if it is). */
	record Laid(String design, Rotation rotation, @Nullable BlockPos anchor, String dimension, int w, int d, int h, int blocks, @Nullable UUID site) {
		static @Nullable Laid of(ItemStack stack) {
			CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
			if (data == null) {
				return null;
			}
			CompoundTag t = data.copyTag().getCompoundOrEmpty(TAG);
			String design = t.getStringOr("design", "");
			if (design.isEmpty()) {
				return null;
			}
			Rotation r = Rotation.values()[Math.floorMod(t.getIntOr("rot", 0), 4)];
			BlockPos anchor = t.getBooleanOr("laid", false) ? new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0)) : null;
			UUID site = t.read("site", UUIDUtil.CODEC).orElse(null);
			return new Laid(design, r, anchor, t.getStringOr("dim", ""), t.getIntOr("w", 0), t.getIntOr("d", 0), t.getIntOr("h", 0), t.getIntOr("n", 0), site);
		}

		void write(ItemStack stack) {
			CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, root -> {
				CompoundTag t = new CompoundTag();
				t.putString("design", this.design);
				t.putInt("rot", this.rotation.ordinal());
				t.putInt("w", this.w);
				t.putInt("d", this.d);
				t.putInt("h", this.h);
				t.putInt("n", this.blocks);
				if (this.anchor != null) {
					t.putBoolean("laid", true);
					t.putInt("x", this.anchor.getX());
					t.putInt("y", this.anchor.getY());
					t.putInt("z", this.anchor.getZ());
					t.putString("dim", this.dimension);
				}
				if (this.site != null) {
					t.store("site", UUIDUtil.CODEC, this.site);
				}
				root.put(TAG, t);
			});
		}

		Laid turned() {
			return new Laid(this.design, this.rotation.getRotated(Rotation.CLOCKWISE_90), this.anchor, this.dimension, this.w, this.d, this.h, this.blocks, this.site);
		}

		Laid at(BlockPos anchor, String dimension) {
			return new Laid(this.design, this.rotation, anchor.immutable(), dimension, this.w, this.d, this.h, this.blocks, this.site != null ? this.site : UUID.randomUUID());
		}

		/** The footprint turned: {width, depth}. */
		int[] footprint() {
			return this.rotation == Rotation.CLOCKWISE_90 || this.rotation == Rotation.COUNTERCLOCKWISE_90 ? new int[] {this.d, this.w} : new int[] {this.w, this.d};
		}

		@Nullable BlockPos max() {
			if (this.anchor == null) {
				return null;
			}
			int[] f = this.footprint();
			return this.anchor.offset(f[0] - 1, this.h - 1, f[1] - 1);
		}
	}

	/**
	 * Draws design {@code name} on a building plan {@code player} carries: a blank one first, else the one in hand
	 * (creative: a new one if none). Tells them how it went.
	 */
	static boolean inscribe(ServerPlayer player, String name) {
		name = name.toLowerCase(java.util.Locale.ROOT);
		Blueprint bp = Designs.blueprint(player.level().getServer(), name);
		if (bp == null) {
			tell(player, Component.translatable("message.jaymoskingdoms.design.unknown", name).withStyle(ChatFormatting.RED));
			return false;
		}
		ItemStack stack = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(PLAN) && Laid.of(s) == null) {
				stack = s;
				break;
			}
		}
		if (stack.isEmpty()) {
			stack = player.getMainHandItem().is(PLAN) ? player.getMainHandItem() : player.getOffhandItem().is(PLAN) ? player.getOffhandItem() : ItemStack.EMPTY;
		}
		Laid laid = new Laid(name, Rotation.NONE, null, "", bp.width(), bp.depth(), bp.height(), bp.blocks().size(), null);
		if (stack.isEmpty()) {
			if (!player.isCreative()) {
				tell(player, Component.translatable("message.jaymoskingdoms.plan.hold").withStyle(ChatFormatting.RED));
				return false;
			}
			stack = new ItemStack(PLAN);
			laid.write(stack);
			player.getInventory().add(stack);
		} else {
			Laid old = Laid.of(stack);
			if (old != null) {
				// (its old building comes down)
				PlanSites.remove(player.level().getServer(), old.site());
			}
			laid.write(stack);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		tell(player, Component.translatable("message.jaymoskingdoms.plan.written", name, bp.width(), bp.depth(), bp.height(), bp.blocks().size())
			.withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ---- the screen -------------------------------------------------------------------------------------------------

	/** While a screen's choice is handled: what the player is told goes on the screen instead of in chat. */
	private static @Nullable List<Component> capture;

	/** Tells {@code player} {@code message}: on the table's screen while a choice of theirs is handled, else in chat. */
	static void tell(ServerPlayer player, Component message) {
		if (capture != null) {
			capture.add(message);
		} else {
			player.sendSystemMessage(message);
		}
	}

	/** Runs {@code work} with what it tells the player kept, and returns that (empty: nothing told). */
	static Component captured(Runnable work) {
		List<Component> before = capture;
		List<Component> got = new ArrayList<>();
		capture = got;
		try {
			work.run();
		} finally {
			capture = before;
		}
		if (got.isEmpty()) {
			return Component.empty();
		}
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < got.size(); i++) {
			if (i > 0) {
				out.append(" ");
			}
			out.append(got.get(i));
		}
		return out;
	}

	/** The most blocks away a player may be from a table to work its screen. */
	static final double SCREEN_REACH = 8.0;

	/** A choice sent from the screen of the table at {@code act.pos()}. */
	static void act(ServerPlayer player, MasonNet.Act act) {
		ServerLevel level = player.level();
		BlockPos pos = act.pos();
		if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > SCREEN_REACH * SCREEN_REACH || !(level.getBlockEntity(pos) instanceof Entity table)) {
			return;
		}
		String name = act.name().toLowerCase(java.util.Locale.ROOT);
		Component note = captured(() -> {
			switch (act.action()) {
				case MasonNet.DRAW -> inscribe(player, name);
				case MasonNet.TAKE -> table.takeOut(player);
				case MasonNet.RETRY -> table.retry(player);
				case MasonNet.SUBMIT -> Commissions.submit(player, name);
				case MasonNet.SAVE -> {
					Surveyor.Selection sel = Surveyor.selection(player);
					if (sel == null || sel.a() == null || sel.b() == null) {
						tell(player, Component.translatable("message.jaymoskingdoms.design.no_selection").withStyle(ChatFormatting.RED));
					} else {
						Designs.save(player, sel.a(), sel.b(), name);
					}
				}
				default -> {
				}
			}
		});
		table.open(player, note);
	}

	/** (0.7.488) A file {@code player} uploaded from the table at {@code pos}'s screen is in: drawn on a plan, and the screen shown again. */
	static void uploaded(ServerPlayer player, BlockPos pos, java.util.function.Supplier<@Nullable String> save) {
		Component note = captured(() -> {
			String name = save.get();
			if (name != null) {
				inscribe(player, name);
			}
		});
		if (player.level().getBlockEntity(pos) instanceof Entity table && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= SCREEN_REACH * SCREEN_REACH) {
			table.open(player, note);
		} else if (!note.getString().isEmpty()) {
			player.sendSystemMessage(note);
		}
	}

	static final class Plan extends Item {
		Plan(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			Laid laid = Laid.of(stack);
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			if (laid == null) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan.blank").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			if (player.isShiftKeyDown()) {
				turn(sp, stack, laid);
			} else {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan.how").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = context.getItemInHand();
			Laid laid = Laid.of(stack);
			if (laid == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan.blank").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			if (player.isShiftKeyDown()) {
				turn(player, stack, laid);
				return InteractionResult.SUCCESS;
			}
			BlockPos anchor = context.getClickedPos().relative(context.getClickedFace());
			Laid now = laid.at(anchor, player.level().dimension().identifier().toString());
			now.write(stack);
			PlanSites.lay(player, now.site(), now.design(), now.rotation(), anchor);
			int[] f = now.footprint();
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan.laid", laid.design(), f[0], f[1], now.h()).withStyle(ChatFormatting.YELLOW));
			player.level().playSound(null, anchor, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
			Surveyor.outline(player.level(), anchor, now.max());
			return InteractionResult.SUCCESS;
		}

		private static void turn(ServerPlayer player, ItemStack stack, Laid laid) {
			Laid now = laid.turned();
			now.write(stack);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan.turned", now.rotation().ordinal() * 90).withStyle(ChatFormatting.YELLOW));
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 1.5F);
			if (now.anchor() != null) {
				Surveyor.outline(player.level(), now.anchor(), now.max());
				if (now.site() != null && now.dimension().equals(player.level().dimension().identifier().toString())) {
					PlanSites.lay(player, now.site(), now.design(), now.rotation(), now.anchor());
				}
			}
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			Laid laid = Laid.of(stack);
			if (laid == null) {
				builder.accept(Component.translatable("item.jaymoskingdoms.building_plan.blank").withStyle(ChatFormatting.GRAY));
				return;
			}
			int[] f = laid.footprint();
			builder.accept(Component.translatable("item.jaymoskingdoms.building_plan.design", laid.design(), f[0], f[1], laid.h(), laid.blocks())
				.withStyle(ChatFormatting.GRAY));
			builder.accept(laid.anchor() == null ? Component.translatable("item.jaymoskingdoms.building_plan.not_laid").withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("item.jaymoskingdoms.building_plan.laid", laid.anchor().toShortString(), laid.rotation().ordinal() * 90)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** Sparks round the plans players hold that are laid out near them. */
	private static void showPlans(MinecraftServer server) {
		if (server.getTickCount() % SHOW_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack stack = player.getMainHandItem().is(PLAN) ? player.getMainHandItem() : player.getOffhandItem().is(PLAN) ? player.getOffhandItem() : null;
			Laid laid = stack == null ? null : Laid.of(stack);
			if (laid == null || laid.anchor() == null || !laid.dimension().equals(player.level().dimension().identifier().toString())
				|| laid.anchor().distSqr(player.blockPosition()) > (double)SHOW_RANGE * SHOW_RANGE) {
				continue;
			}
			Surveyor.outline(player.level(), laid.anchor(), laid.max());
		}
	}

	// ---- the table --------------------------------------------------------------------------------------------------

	static final class TableBlock extends BaseEntityBlock {
		TableBlock(BlockBehaviour.Properties properties) {
			super(properties);
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
			return level.isClientSide() ? null : createTickerHelper(type, ENTITY, (l, p, s, table) -> table.serverTick((ServerLevel)l));
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (!stack.is(PLAN)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Entity table) {
				table.insert(sp, stack);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Entity table) {
				if (player.isShiftKeyDown() && !table.plan.isEmpty()) {
					table.takeOut(sp);
				} else {
					table.open(sp, Component.empty());
				}
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** One block of the building, where it goes and what. */
	record Op(BlockPos pos, BlockState state) {
	}

	/** A table's plan and how far it's got. */
	public static final class Entity extends BlockEntity {
		private ItemStack plan = ItemStack.EMPTY;
		private int index;
		private int skipped;
		private boolean done;
		private @Nullable UUID owner;
		/** Not saved: the building laid out from the plan, its blocks by place, what it waits for and till when. */
		private @Nullable List<Op> ops;
		private @Nullable Map<BlockPos, BlockState> byPos;
		private @Nullable Item waitingFor;
		private long waitUntil;

		public Entity(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		void insert(ServerPlayer player, ItemStack stack) {
			if (!this.plan.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mason_table.full").withStyle(ChatFormatting.RED));
				return;
			}
			Laid laid = Laid.of(stack);
			if (laid == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan.blank").withStyle(ChatFormatting.RED));
				return;
			}
			if (laid.anchor() == null || !laid.dimension().equals(player.level().dimension().identifier().toString())) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mason_table.not_laid").withStyle(ChatFormatting.RED));
				return;
			}
			int[] f = laid.footprint();
			double cx = laid.anchor().getX() + f[0] / 2.0, cz = laid.anchor().getZ() + f[1] / 2.0;
			double dx = cx - this.worldPosition.getX(), dz = cz - this.worldPosition.getZ();
			if (dx * dx + dz * dz > (double)REACH * REACH) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mason_table.too_far", REACH).withStyle(ChatFormatting.RED));
				return;
			}
			if (Designs.blueprint(player.level().getServer(), laid.design()) == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.design.unknown", laid.design()).withStyle(ChatFormatting.RED));
				return;
			}
			this.plan = stack.split(1);
			this.owner = player.getUUID();
			this.index = 0;
			this.skipped = 0;
			this.done = false;
			this.ops = null;
			this.waitUntil = 0L;
			this.setChanged();
			player.level().playSound(null, this.worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.mason_table.started", laid.design()).withStyle(ChatFormatting.GREEN));
		}

		void takeOut(ServerPlayer player) {
			if (this.plan.isEmpty()) {
				return;
			}
			ItemStack out = this.plan;
			this.clear();
			if (!player.getInventory().add(out)) {
				player.drop(out, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			player.level().playSound(null, this.worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
		}

		private void clear() {
			this.plan = ItemStack.EMPTY;
			this.owner = null;
			this.index = 0;
			this.skipped = 0;
			this.done = false;
			this.ops = null;
			this.byPos = null;
			this.waitingFor = null;
			this.setChanged();
		}

		/** Once finished with blocks left out (they were in the way): tries them again. */
		void retry(ServerPlayer player) {
			if (this.done && this.skipped > 0) {
				tell(player, Component.translatable("message.jaymoskingdoms.mason_table.retry", this.skipped).withStyle(ChatFormatting.YELLOW));
				this.index = 0;
				this.skipped = 0;
				this.done = false;
				this.setChanged();
			}
		}

		/** Sends {@code player} the table's screen: the designs, what it's doing, what it still needs, and {@code note}. */
		void open(ServerPlayer player, Component note) {
			ServerLevel level = (ServerLevel)this.level;
			MinecraftServer server = level.getServer();
			List<MasonNet.Design> designs = new ArrayList<>();
			for (String n : Designs.names(server)) {
				Blueprint bp = Designs.blueprint(server, n);
				if (bp != null) {
					designs.add(new MasonNet.Design(n, bp.width(), bp.depth(), bp.height(), bp.blocks().size(), Designs.author(server, n), Designs.forCrews(bp)));
				}
			}
			Laid laid = Laid.of(this.plan);
			List<Op> ops = laid == null ? null : this.ops(level, laid);
			List<MasonNet.Need> needs = new ArrayList<>();
			if (ops != null && !this.done) {
				// what's still to come, most first
				Map<Item, Integer> need = new LinkedHashMap<>();
				for (int i = this.index; i < ops.size(); i++) {
					Op op = ops.get(i);
					BlockState here = level.isLoaded(op.pos()) ? level.getBlockState(op.pos()) : null;
					Item item = cost(op.state());
					if (item != null && !secondHalf(op.state()) && (here == null || !here.is(op.state().getBlock()))) {
						need.merge(item, 1, Integer::sum);
					}
				}
				need.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(LIST_MATERIALS)
					.forEach(e -> needs.add(new MasonNet.Need(new ItemStack(e.getKey()), e.getValue())));
			}
			int blank = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.is(PLAN) && Laid.of(s) == null) {
					blank += s.getCount();
				}
			}
			Surveyor.Selection sel = Surveyor.selection(player);
			ServerPlayNetworking.send(player, new MasonNet.Open(this.worldPosition, designs, laid == null ? "" : laid.design(), this.index,
				ops == null ? 0 : ops.size(), this.done, this.skipped, this.waitingFor == null ? ItemStack.EMPTY : new ItemStack(this.waitingFor), needs, blank,
				sel != null && sel.a() != null && sel.b() != null, note));
		}

		/** The building laid out from {@code laid} (cached), bottom up, solid blocks before what hangs on them; null if its design is gone. */
		private @Nullable List<Op> ops(ServerLevel level, Laid laid) {
			if (this.ops != null) {
				return this.ops;
			}
			Blueprint bp = Designs.blueprint(level.getServer(), laid.design());
			if (bp == null || laid.anchor() == null) {
				return null;
			}
			List<Op> ops = new ArrayList<>();
			Map<BlockPos, BlockState> byPos = new HashMap<>();
			for (Blueprint.Placement pl : bp.blocks()) {
				BlockPos at = laid.anchor().offset(Planner.turn(bp, pl.offset(), laid.rotation()));
				BlockState state = pl.state().rotate(laid.rotation());
				if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
					// (no water comes with a block)
					state = state.setValue(BlockStateProperties.WATERLOGGED, false);
				}
				ops.add(new Op(at, state));
				byPos.put(at, state);
			}
			ops.sort(Comparator.<Op>comparingInt(o -> o.pos().getY()).thenComparingInt(o -> o.state().isCollisionShapeFullBlock(level, o.pos()) ? 0 : 1)
				.thenComparingInt(o -> o.pos().getZ()).thenComparingInt(o -> o.pos().getX()));
			this.ops = List.copyOf(ops);
			this.byPos = byPos;
			return this.ops;
		}

		/** {@code here} with the design's choice of each of its enum properties and sign/banner/head rotation (how it lies); counts and on/off left as they are. */
		private static BlockState turnedLike(BlockState here, BlockState design) {
			BlockState out = here;
			for (net.minecraft.world.level.block.state.properties.Property<?> property : design.getProperties()) {
				if ((property instanceof net.minecraft.world.level.block.state.properties.EnumProperty<?> || property.getName().equals("rotation"))
					&& out.hasProperty(property)) {
					out = copy(out, design, property);
				}
			}
			return out;
		}

		private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from, net.minecraft.world.level.block.state.properties.Property<T> property) {
			return to.setValue(property, from.getValue(property));
		}

		void serverTick(ServerLevel level) {
			if (this.plan.isEmpty() || this.done || level.getGameTime() % PLACE_TICKS != 0 || level.getGameTime() < this.waitUntil) {
				return;
			}
			Laid laid = Laid.of(this.plan);
			if (laid == null || laid.anchor() == null || !laid.dimension().equals(level.dimension().identifier().toString())) {
				return;
			}
			List<Op> ops = this.ops(level, laid);
			if (ops == null) {
				return;
			}
			for (int n = 0; n < SKIP_PER_STEP && this.index < ops.size(); n++) {
				Op op = ops.get(this.index);
				if (!level.isLoaded(op.pos())) {
					return;
				}
				BlockState here = level.getBlockState(op.pos());
				if (here == op.state()) {
					this.index++;
					continue;
				}
				if (here.is(op.state().getBlock())) {
					// the right block, turned or set otherwise: turned to match, free. Only its lie is copied (facing, axis,
					// half, shape...), never numbers or switches, so a crop isn't grown, a frame given its eye or an anchor charged
					BlockState turned = turnedLike(here, op.state());
					if (turned != here) {
						level.setBlock(op.pos(), turned, FLAGS);
					}
					this.index++;
					continue;
				}
				Item item = cost(op.state());
				if (item == null || secondHalf(op.state())) {
					// (water, fire and the like are left out; a door's top or a bed's head came with its other half)
					this.index++;
					continue;
				}
				if (!here.canBeReplaced()) {
					this.skipped++;
					this.index++;
					continue;
				}
				if (!this.take(level, item)) {
					if (this.waitingFor != item) {
						this.waitingFor = item;
					}
					this.waitUntil = level.getGameTime() + WAIT_TICKS;
					this.setChanged();
					return;
				}
				this.waitingFor = null;
				this.lay(level, op.pos(), op.state());
				this.partner(level, op);
				this.index++;
				this.setChanged();
				return;
			}
			if (this.index >= ops.size()) {
				this.done = true;
				this.setChanged();
				level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_WORK_MASON, SoundSource.BLOCKS, 1.0F, 1.0F);
				ServerPlayer owner = this.owner == null ? null : level.getServer().getPlayerList().getPlayer(this.owner);
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable(this.skipped > 0 ? "message.jaymoskingdoms.mason_table.done_skipped" : "message.jaymoskingdoms.mason_table.done",
						laid.design(), this.skipped).withStyle(ChatFormatting.GREEN));
				}
			}
		}

		private void lay(ServerLevel level, BlockPos pos, BlockState state) {
			level.setBlock(pos, state, FLAGS);
			SoundType sound = state.getSoundType();
			level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F * 0.8F, sound.getPitch() * 0.8F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
			if (this.owner != null) {
				Placed.placed(this.owner, level, pos, state);
			}
		}

		/** The second half of a door, tall plant or bed, laid with its first (free). */
		private void partner(ServerLevel level, Op op) {
			BlockState s = op.state();
			BlockPos other = null;
			if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
				other = op.pos().above();
			} else if (s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
				&& s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				other = op.pos().relative(s.getValue(BlockStateProperties.HORIZONTAL_FACING));
			}
			BlockState want = other == null || this.byPos == null ? null : this.byPos.get(other);
			if (want != null && want.is(s.getBlock()) && level.getBlockState(other).canBeReplaced()) {
				this.lay(level, other, want);
			}
		}

		/** Takes one {@code item} (or what stands in for it) from a container touching the table; false if none has it. */
		private boolean take(ServerLevel level, Item item) {
			for (Direction d : Direction.values()) {
				Container c = HopperBlockEntity.getContainerAt(level, this.worldPosition.relative(d));
				if (c == null) {
					continue;
				}
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack in = c.getItem(i);
					if (in.is(item) || SOIL.contains(item) && in.is(Items.DIRT)) {
						c.removeItem(i, 1);
						c.setChanged();
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public void preRemoveSideEffects(BlockPos pos, BlockState state) {
			if (this.level != null && !this.plan.isEmpty()) {
				Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.plan);
				this.plan = ItemStack.EMPTY;
			}
			super.preRemoveSideEffects(pos, state);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			this.plan = input.read("plan", ItemStack.CODEC).orElse(ItemStack.EMPTY);
			this.index = input.getIntOr("index", 0);
			this.skipped = input.getIntOr("skipped", 0);
			this.done = input.getBooleanOr("done", false);
			this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
			this.ops = null;
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			if (!this.plan.isEmpty()) {
				output.store("plan", ItemStack.CODEC, this.plan);
			}
			output.putInt("index", this.index);
			output.putInt("skipped", this.skipped);
			output.putBoolean("done", this.done);
			if (this.owner != null) {
				output.store("owner", UUIDUtil.CODEC, this.owner);
			}
		}
	}

	/** Ground a block of dirt does for (it greens, or is trodden, once laid): no silk touch needed. */
	static final java.util.Set<Item> SOIL = java.util.Set.of(Items.GRASS_BLOCK, Items.DIRT_PATH, Items.FARMLAND, Items.PODZOL, Items.MYCELIUM);

	/** The item a block is laid from, or null for one laid from nothing (air, water, fire, a portal). */
	static @Nullable Item cost(BlockState state) {
		if (state.isAir()) {
			return null;
		}
		Item item = state.getBlock().asItem();
		return item == Items.AIR ? null : item;
	}

	/** The top of a door or tall plant, or a bed's head: laid with the other half. */
	static boolean secondHalf(BlockState s) {
		return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
			|| s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
	}
}
