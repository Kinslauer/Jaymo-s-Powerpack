package com.jaymoskitchen.cooking.spit;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.butchery.Butchery;
import com.jaymoskitchen.corpse.CorpseData;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.delight.Delight;
import com.jaymoskitchen.kitchen.KitchenBlocks;
import com.jaymoskitchen.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The spit: a frame of forked posts and a crossbar (kitchen/tools/gen_spit_frame.py), crafted from sticks, it goes on top of a lit campfire or another lit heat source
 * ({@link Delight#HEAT_SOURCES}). One roastable stack turns over the flames and cooks from raw to cooked to burnt
 * (see {@link SpitBlockEntity}); right-click with an empty hand to tear a cooked chunk off, or sneak + right-click
 * to take the whole cooked product when it's done.
 */
public class SpitBlock extends BaseEntityBlock {
	public static Block SPIT;
	public static Item ITEM;
	public static BlockEntityType<SpitBlockEntity> ENTITY;
	/**
	 * Not to be had: only drawn, as the 3D look of a piece of meat on the spit (kitchen/tools/gen_great_spit.py): a shank
	 * (mutton, a leg of chicken or rabbit) or a steak (beef, pork, venison and the rest), raw to charred by its custom
	 * model data.
	 */
	public static Item SHANK, STEAK;

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("spit"));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.setId(key);
		SPIT = Registry.register(BuiltInRegistries.BLOCK, key, new SpitBlock(properties));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("spit"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(SPIT, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
		SHANK = look("spit_shank");
		STEAK = look("spit_steak");
		// the great spit, for a whole beast (GreatSpitBlock), shares the spit's block entity
		GreatSpitBlock.registerGreat();
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("spit"),
			new BlockEntityType<>(SpitBlockEntity::new, GreatSpitBlock.blocks()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(output -> output.accept(new ItemStack(ITEM)));
	}

	private static Item look(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).setId(key)));
	}

	public SpitBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** The crossbar and what turns on it: the low half of its block (the frame's posts stand outside, round the fire below). */
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * Nothing stands on it (Andy, on a clip of a camp's cook stood in the smoke on top of the spit: "they should stand to
	 * the side"): it's sticks over a fire, not a step.
	 */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.empty();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return heat(context.getLevel().getBlockState(context.getClickedPos().below())) ? defaultBlockState() : null;
	}

	/** Whether the block below is a lit heat source: a lit campfire, or anything in {@link Delight#HEAT_SOURCES}. */
	static boolean heat(BlockState state) {
		if (state.is(BlockTags.CAMPFIRES)) {
			return state.getValue(CampfireBlock.LIT);
		}
		return state.is(Delight.HEAT_SOURCES);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SpitBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		return !level.isClientSide() ? createTickerHelper(type, ENTITY, SpitBlockEntity::serverTick) : null;
	}

	@Nullable
	private static SpitBlockEntity blockEntity(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof SpitBlockEntity spit ? spit : null;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** What the roast yields: for a carcass, what butchering would get out of it; otherwise the cooked counterpart. */
	static java.util.List<ItemStack> cookedProduct(ItemStack stack, ServerLevel server) {
		if (stack.is(Corpses.CORPSE_ITEM)) {
			CorpseData data = stack.get(Corpses.CORPSE_DATA);
			if (data != null) {
				CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, server);
				corpse.restore(data);
				java.util.List<ItemStack> drops = new java.util.ArrayList<>();
				for (ItemStack drop : corpse.butcheredDrops()) {
					Item cooked = cookedOf(drop.getItem());
					drops.add(cooked == null ? drop : new ItemStack(cooked, drop.getCount()));
				}
				return drops;
			}
		}
		Item cooked = cookedOf(stack.getItem());
		return java.util.List.of(cooked == null ? stack : new ItemStack(cooked, stack.getCount()));
	}

	private static @Nullable Item cookedOf(Item item) {
		if (item == Items.BEEF) return Items.COOKED_BEEF;
		if (item == Items.PORKCHOP) return Items.COOKED_PORKCHOP;
		if (item == Items.CHICKEN) return Items.COOKED_CHICKEN;
		if (item == Items.MUTTON) return Items.COOKED_MUTTON;
		if (item == Items.RABBIT) return Items.COOKED_RABBIT;
		if (item == Items.COD) return Items.COOKED_COD;
		if (item == Items.SALMON) return Items.COOKED_SALMON;
		if (item == Delight.CHICKEN_CUTS) return Delight.COOKED_CHICKEN_CUTS;
		if (item == Delight.MUTTON_CHOPS) return Delight.COOKED_MUTTON_CHOPS;
		if (item == Delight.COD_SLICE) return Delight.COOKED_COD_SLICE;
		if (item == Delight.SALMON_SLICE) return Delight.COOKED_SALMON_SLICE;
		if (item == Delight.RAW_BACON) return KitchenBlocks.FOODS.get("bacon");
		if (item == Butchery.RAW_BLOOD_SAUSAGE) return Butchery.BLOOD_SAUSAGE;
		return null;
	}

	/**
	 * A whole beast's pieces, in the order they're torn off: each piece of meat in it ({@link CorpseData#loot}), cooked as
	 * {@link #cookedOf} or a campfire would cook it, one to a piece. What isn't meat (feathers left on, a bone) isn't a piece.
	 */
	static List<ItemStack> wholePieces(ItemStack stack, ServerLevel server) {
		List<ItemStack> pieces = new ArrayList<>();
		CorpseData data = stack.get(Corpses.CORPSE_DATA);
		if (data == null) {
			return pieces;
		}
		for (var template : data.loot()) {
			ItemStack raw = template.create();
			if (raw.isEmpty()) {
				continue;
			}
			ItemStack one = raw.copyWithCount(1);
			Item cooked = cookedOf(one.getItem());
			ItemStack done = cooked != null ? new ItemStack(cooked) : server.recipeAccess()
				.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING, new net.minecraft.world.item.crafting.SingleRecipeInput(one), server)
				.map(recipe -> recipe.value().assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(one)))
				.orElse(ItemStack.EMPTY);
			if (done.isEmpty() || !done.has(net.minecraft.core.component.DataComponents.FOOD)) {
				continue;
			}
			for (int i = 0; i < raw.getCount(); i++) {
				pieces.add(done.copyWithCount(1));
			}
		}
		return pieces;
	}

	/** Everyone near sees {@code by} reach in and tear a piece off the spit at {@code pos}: the client's tearing (Payloads.SpitRip). */
	static void ripSeen(ServerLevel level, BlockPos pos, net.minecraft.world.entity.@Nullable Entity by) {
		if (by instanceof net.minecraft.world.entity.LivingEntity living) {
			living.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		Payloads.SpitRip rip = new Payloads.SpitRip(pos, by == null ? -1 : by.getId());
		for (ServerPlayer near : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(level, pos)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(near, rip);
		}
	}

	/** How many tears this roast yields: a whole carcass more than a single piece. */
	static int chunksFor(ItemStack stack) {
		return stack.is(Corpses.CORPSE_ITEM) ? SpitBlockEntity.CHUNKS_PER_ANIMAL : SpitBlockEntity.CHUNKS_PER_PIECE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		SpitBlockEntity be = blockEntity(level, pos);
		if (be == null) {
			return InteractionResult.PASS;
		}
		if (!held.isEmpty() && held.is(SpitBlockEntity.ROASTABLE) && be.skewer(held)) {
			server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.on"));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		net.minecraft.world.phys.BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		SpitBlockEntity be = blockEntity(level, pos);
		if (be == null || be.roasting().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (be.throwOutBurnt()) {
			server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.burnt"));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (be.doneness() < SpitBlockEntity.COOKED) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.raw"));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (player.isSecondaryUseActive()) {
			// everything cooked at once (a whole beast's: what meat is left on it, and its bones)
			for (ItemStack piece : be.takeCooked(server)) {
				give(player, piece);
			}
			server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
		} else {
			// tear the most done piece off, or a chunk off a body; the last tear clears the spit
			ItemStack chunk = be.tear(server, player);
			if (!chunk.isEmpty()) {
				give(player, chunk);
			}
			server.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.done"));
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
