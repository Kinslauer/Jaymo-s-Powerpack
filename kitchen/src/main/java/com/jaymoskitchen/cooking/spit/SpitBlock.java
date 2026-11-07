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
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The spit: crafted from sticks, it goes on top of a lit campfire or another lit heat source
 * ({@link Delight#HEAT_SOURCES}). One roastable stack turns over the flames and cooks from raw to cooked to burnt
 * (see {@link SpitBlockEntity}); right-click with an empty hand to tear a cooked chunk off, or sneak + right-click
 * to take the whole cooked product when it's done.
 */
public class SpitBlock extends BaseEntityBlock {
	public static Block SPIT;
	public static Item ITEM;
	public static BlockEntityType<SpitBlockEntity> ENTITY;

	public static void register() {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.0F)
			.sound(SoundType.WOOD);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("spit"));
		SPIT = Registry.register(BuiltInRegistries.BLOCK, key, new SpitBlock(properties));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("spit"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(SPIT, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("spit"),
			new BlockEntityType<>(SpitBlockEntity::new, Set.of(SPIT)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(output -> output.accept(new ItemStack(ITEM)));
	}

	public SpitBlock(BlockBehaviour.Properties properties) {
		super(properties);
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
	private static java.util.List<ItemStack> cookedProduct(ItemStack stack, ServerLevel server) {
		if (stack.is(Corpses.CORPSE_ITEM)) {
			CorpseData data = stack.get(Corpses.CORPSE_DATA);
			if (data != null) {
				CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, server);
				corpse.restore(data);
				return corpse.butcheredDrops();
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

	/** How many tears this roast yields: a whole carcass more than a single piece. */
	private static int chunksFor(ItemStack stack) {
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
		if (!held.isEmpty() && held.is(SpitBlockEntity.ROASTABLE) && be.items.get(0).isEmpty()) {
			be.items.set(0, held.copyWithCount(1));
			held.shrink(1);
			be.doneness = 0.0F;
			be.chunks = chunksFor(held);
			be.markUpdated();
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
		if (be == null || be.items.get(0).isEmpty()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = be.items.get(0);
		if (be.doneness >= SpitBlockEntity.BURNT) {
			be.items.set(0, ItemStack.EMPTY);
			be.doneness = 0.0F;
			be.markUpdated();
			server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.burnt"));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (be.doneness >= SpitBlockEntity.COOKED) {
			if (player.isSecondaryUseActive()) {
				// the whole cooked product
				for (ItemStack piece : cookedProduct(stack, server)) {
					give(player, piece);
				}
				be.items.set(0, ItemStack.EMPTY);
				be.doneness = 0.0F;
				be.markUpdated();
				server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
			} else {
				// tear a cooked chunk off: one cooked piece per tear, counting down the roast's chunks;
				// the last tear clears the spit
				if (be.chunks > 0) {
					ItemStack chunk = cookedProduct(stack, server).get(0).copyWithCount(1);
					give(player, chunk);
					be.chunks -= 1;
					be.doneness = Mth.clamp(be.doneness - SpitBlockEntity.CHUNK, 0.0F, SpitBlockEntity.BURNT);
					if (be.chunks <= 0) {
						be.items.set(0, ItemStack.EMPTY);
					}
					be.markUpdated();
					server.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					if (player instanceof ServerPlayer serverPlayer) {
						net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, new Payloads.SpitRip(pos));
					}
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.done"));
				}
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.spit.raw"));
		return InteractionResult.SUCCESS_SERVER;
	}
}
