package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The rest of the Verdant World's four woods (balloon mushroom, pulse tree, brain tree, sunroot): a standing and a wall
 * sign, a hanging and a wall hanging sign, a chest, a barrel, a bookshelf and a ladder each. Signs take the crimson wood
 * type (their lettering needs no more), the chests and barrels join vanilla's block entity types where they can; the
 * chests have a block entity of their own so each draws its own wood (see {@code EdenChestClient}), and never join into
 * a double chest. Every model, texture, loot table, recipe and tag comes from {@code tools/gen_eden_leftout.py}.
 */
public final class EdenWoodwork {
	/** The four woods, with the colour of their planks. */
	public static final Map<String, MapColor> WOODS = new LinkedHashMap<>();
	/** Hardness of a sign, a chest or barrel, a bookshelf and a ladder. */
	public static final float SIGN_HARDNESS = 1.0F, CHEST_HARDNESS = 2.5F, BOOKSHELF_HARDNESS = 1.5F, LADDER_HARDNESS = 0.4F;

	public static final Map<String, Block> CHESTS = new LinkedHashMap<>();
	public static BlockEntityType<EdenChestEntity> CHEST_ENTITY;

	private static final List<ItemLike> TAB = new ArrayList<>();

	static {
		WOODS.put("balloon_mushroom", MapColor.COLOR_PINK);
		WOODS.put("pulse_tree", MapColor.COLOR_CYAN);
		WOODS.put("brain_tree", MapColor.COLOR_GRAY);
		WOODS.put("sunroot", MapColor.GOLD);
	}

	private EdenWoodwork() {
	}

	/** A chest that stays single, with the block entity that carries its wood. */
	static final class WoodChest extends ChestBlock {
		WoodChest(BlockBehaviour.Properties properties) {
			super(() -> CHEST_ENTITY, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
		}

		@Override
		public boolean chestCanConnectTo(BlockState state) {
			return false;
		}
	}

	public static final class EdenChestEntity extends ChestBlockEntity {
		public EdenChestEntity(BlockPos pos, BlockState state) {
			super(CHEST_ENTITY, pos, state);
		}
	}

	private static BlockBehaviour.Properties wood(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).sound(SoundType.NETHER_WOOD);
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key).useBlockDescriptionPrefix()));
		TAB.add(item);
		return item;
	}

	public static void init() {
		List<Block> signs = new ArrayList<>(), hanging = new ArrayList<>(), barrels = new ArrayList<>();
		for (Map.Entry<String, MapColor> entry : WOODS.entrySet()) {
			String w = entry.getKey();
			MapColor color = entry.getValue();
			Block sign = block(w + "_sign", p -> new StandingSignBlock(WoodType.CRIMSON, p), wood(color).forceSolidOn().noCollision().strength(SIGN_HARDNESS));
			Block wallSign = block(w + "_wall_sign", p -> new WallSignBlock(WoodType.CRIMSON, p),
				wood(color).forceSolidOn().noCollision().strength(SIGN_HARDNESS));
			item(w + "_sign", p -> new StandingAndWallBlockItem(sign, wallSign, Direction.DOWN, p));
			Block hang = block(w + "_hanging_sign", p -> new CeilingHangingSignBlock(WoodType.CRIMSON, p),
				wood(color).forceSolidOn().noCollision().strength(SIGN_HARDNESS));
			Block wallHang = block(w + "_wall_hanging_sign", p -> new WallHangingSignBlock(WoodType.CRIMSON, p),
				wood(color).forceSolidOn().noCollision().strength(SIGN_HARDNESS));
			item(w + "_hanging_sign", p -> new HangingSignItem(hang, wallHang, p));
			signs.add(sign);
			signs.add(wallSign);
			hanging.add(hang);
			hanging.add(wallHang);

			Block chest = block(w + "_chest", WoodChest::new, wood(color).strength(CHEST_HARDNESS));
			CHESTS.put(w, chest);
			item(w + "_chest", p -> new BlockItem(chest, p));
			Block barrel = block(w + "_barrel", BarrelBlock::new, wood(color).strength(CHEST_HARDNESS));
			item(w + "_barrel", p -> new BlockItem(barrel, p));
			barrels.add(barrel);
			Block shelf = block(w + "_bookshelf", Block::new, wood(color).strength(BOOKSHELF_HARDNESS));
			item(w + "_bookshelf", p -> new BlockItem(shelf, p));
			Block ladder = block(w + "_ladder", LadderBlock::new,
				BlockBehaviour.Properties.of().strength(LADDER_HARDNESS).sound(SoundType.LADDER).noOcclusion().pushReaction(PushReaction.POPPED));
			item(w + "_ladder", p -> new BlockItem(ladder, p));
		}
		CHEST_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("eden_chest"),
			new BlockEntityType<>(EdenChestEntity::new, Set.copyOf(CHESTS.values())));
		signs.forEach(BlockEntityTypes.SIGN::addValidBlock);
		hanging.forEach(BlockEntityTypes.HANGING_SIGN::addValidBlock);
		barrels.forEach(BlockEntityTypes.BARREL::addValidBlock);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> TAB.forEach(output::accept));
	}
}
