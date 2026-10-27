package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.30) The rest of Farmer's Delight's home (MIT, by vectorwing; see LICENSE_farmersdelight), with its models and art:
 * the Skillet (the Frying Pan, {@link SkilletItem}), the {@link StoveBlock}, cabinets ({@link CabinetBlock}), baskets
 * ({@link BasketBlock}), crates, rice bales and bags, the safety net, the canvas rug, tatami ({@link TatamiBlock}) and
 * canvas with its signs ({@link CanvasSign}). All of it is vanilla-tab stock, the way {@link Delight} is.
 */
public final class DelightHome {
	/** How long (ticks) a basket or cabinet burns as fuel, and a safety net. */
	private static final int WOODEN_FUEL = 300;
	private static final int NET_FUEL = 200;
	/** Light a lit stove gives off (see {@link StoveBlock#LIT_LIGHT}). */
	private static final int STOVE_LIGHT = StoveBlock.LIT_LIGHT;
	/** Woods that make a cabinet, in the order of the creative tab (Poplar is vanilla in 26.3). */
	private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
		"pale_oak", "poplar", "bamboo", "crimson", "warped");

	public static final ResourceKey<DamageType> STOVE_BURN = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("stove_burn"));

	public static SoundEvent SKILLET_SIZZLE, SKILLET_ADD_FOOD, SKILLET_ATTACK_STRONG, SKILLET_ATTACK_WEAK;

	public static StoveBlock STOVE;
	public static BasketBlock WOODEN_BASKET, BAMBOO_BASKET;
	public static final List<CabinetBlock> CABINETS = new ArrayList<>();
	public static BlockEntityType<StoveBlockEntity> STOVE_ENTITY;
	public static BlockEntityType<CabinetBlockEntity> CABINET_ENTITY;
	public static BlockEntityType<BasketBlockEntity> BASKET_ENTITY;
	public static Item CANVAS;

	/** Everything for the creative tabs, in order. */
	private static final List<Item> FUNCTIONAL = new ArrayList<>();
	private static final List<Item> BUILDING = new ArrayList<>();

	private DelightHome() {
	}

	public static void register() {
		SKILLET_SIZZLE = sound("block.skillet.sizzle");
		SKILLET_ADD_FOOD = sound("block.skillet.add_food");
		SKILLET_ATTACK_STRONG = sound("item.skillet.attack.strong");
		SKILLET_ATTACK_WEAK = sound("item.skillet.attack.weak");

		// the pan's flip is a click while it cooks; the swing strength is read just before the game resets it
		PayloadTypeRegistry.serverboundPlay().register(SkilletItem.Flip.TYPE, SkilletItem.Flip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkilletItem.Flip.TYPE, (payload, context) -> payload.handle(context.player()));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player.getItemInHand(hand).getItem() instanceof SkilletItem) {
				SkilletItem.noteSwing(player);
			}
			return InteractionResult.PASS;
		});

		// the stove
		STOVE = block("stove", StoveBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS)
			.lightLevel(state -> state.getValue(StoveBlock.LIT) ? STOVE_LIGHT : 0));
		STOVE_ENTITY = blockEntity("stove", new BlockEntityType<>(StoveBlockEntity::new, Set.of(STOVE)));
		item("stove", STOVE, new Item.Properties(), FUNCTIONAL, 0);

		// baskets
		WOODEN_BASKET = block("wooden_basket", BasketBlock::new, BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion());
		BAMBOO_BASKET = block("bamboo_basket", BasketBlock::new, BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.BAMBOO_WOOD).noOcclusion());
		BASKET_ENTITY = blockEntity("basket", new BlockEntityType<>(BasketBlockEntity::new, Set.of(WOODEN_BASKET, BAMBOO_BASKET)));
		item("wooden_basket", WOODEN_BASKET, new Item.Properties(), FUNCTIONAL, WOODEN_FUEL);
		item("bamboo_basket", BAMBOO_BASKET, new Item.Properties(), FUNCTIONAL, WOODEN_FUEL);

		// cabinets
		for (String wood : WOODS) {
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL);
			boolean nether = wood.equals("crimson") || wood.equals("warped");
			switch (wood) {
				case "cherry" -> properties.sound(SoundType.CHERRY_WOOD);
				case "bamboo" -> properties.sound(SoundType.BAMBOO_WOOD);
				case "crimson", "warped" -> properties.sound(SoundType.NETHER_WOOD);
				default -> {
				}
			}
			CabinetBlock cabinet = block(wood + "_cabinet", CabinetBlock::new, properties);
			CABINETS.add(cabinet);
			// nether wood doesn't burn
			item(wood + "_cabinet", cabinet, new Item.Properties(), FUNCTIONAL, nether ? 0 : WOODEN_FUEL);
		}
		CABINET_ENTITY = blockEntity("cabinet", new BlockEntityType<>(CabinetBlockEntity::new, Set.copyOf(CABINETS)));

		// crates of crops
		for (String crop : List.of("carrot", "potato", "beetroot", "cabbage", "tomato", "onion")) {
			Block crate = block(crop + "_crate", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0F, 3.0F).sound(SoundType.WOOD));
			item(crop + "_crate", crate, new Item.Properties(), BUILDING, 0);
		}

		// rice and bedding
		RiceBaleBlock bale = block("rice_bale", RiceBaleBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK));
		FlammableBlockRegistry.getDefaultInstance().add(bale, RiceBaleBlock.FLAMMABILITY, RiceBaleBlock.FIRE_SPREAD);
		item("rice_bale", bale, new Item.Properties(), BUILDING, 0);
		item("rice_bag", block("rice_bag", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WOOL.white())), new Item.Properties(), BUILDING, 0);
		item("safety_net", block("safety_net", SafetyNetBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CARPET.brown())
			.strength(0.2F).sound(SoundType.WOOL).bounceRestitution(0.6F)), new Item.Properties(), BUILDING, NET_FUEL);
		item("canvas_rug", block("canvas_rug", CanvasRugBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CARPET.white())
			.sound(SoundType.GRASS).strength(0.2F)), new Item.Properties(), BUILDING, 0);
		item("tatami", block("tatami", TatamiBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WOOL.white())), new Item.Properties(), BUILDING, 0);
		item("full_tatami_mat", block("full_tatami_mat", TatamiBlock.Mat::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WOOL.white())
			.strength(0.3F)), new Item.Properties(), BUILDING, 0);
		item("half_tatami_mat", block("half_tatami_mat", TatamiBlock.Half::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WOOL.white())
			.strength(0.3F).pushReaction(PushReaction.POPPED)), new Item.Properties(), BUILDING, 0);

		// canvas, and its signs: plain, then each dye
		CANVAS = plain("canvas", new Item.Properties());
		List<Block> signBlocks = new ArrayList<>();
		List<Block> hangingBlocks = new ArrayList<>();
		sign(null, signBlocks, hangingBlocks);
		for (DyeColor color : DyeColor.values()) {
			sign(color, signBlocks, hangingBlocks);
		}
		for (Block block : signBlocks) {
			BlockEntityTypes.SIGN.addValidBlock(block);
		}
		for (Block block : hangingBlocks) {
			BlockEntityTypes.HANGING_SIGN.addValidBlock(block);
		}

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : FUNCTIONAL) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			for (Item item : BUILDING) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(new ItemStack(CANVAS)));
	}

	/** One colour (or plain, null) of sign: standing and wall, hanging from a ceiling and from a wall, and the two items. */
	private static void sign(@Nullable DyeColor color, List<Block> signBlocks, List<Block> hangingBlocks) {
		String prefix = color == null ? "" : color.getName() + "_";
		String standingId = prefix + "canvas_sign";
		String hangingId = prefix + "hanging_canvas_sign";
		Block standing = block(standingId, p -> new CanvasSign.Standing(p, color), BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_SIGN));
		Block wall = block(prefix + "canvas_wall_sign", p -> new CanvasSign.Wall(p, color), BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_SIGN)
			.overrideLootTable(java.util.Optional.of(lootTable(standingId))));
		Block hanging = block(hangingId, p -> new CanvasSign.Hanging(p, color), BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_HANGING_SIGN));
		Block wallHanging = block(prefix + "wall_hanging_canvas_sign", p -> new CanvasSign.WallHanging(p, color), BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_HANGING_SIGN)
			.overrideLootTable(java.util.Optional.of(lootTable(hangingId))));
		signBlocks.add(standing);
		signBlocks.add(wall);
		hangingBlocks.add(hanging);
		hangingBlocks.add(wallHanging);
		ResourceKey<Item> standingKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(standingId));
		FUNCTIONAL.add(Registry.register(BuiltInRegistries.ITEM, standingKey,
			new StandingAndWallBlockItem(standing, wall, Direction.DOWN, new Item.Properties().stacksTo(16).useBlockDescriptionPrefix().setId(standingKey))));
		ResourceKey<Item> hangingKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(hangingId));
		FUNCTIONAL.add(Registry.register(BuiltInRegistries.ITEM, hangingKey,
			new HangingSignItem(hanging, wallHanging, new Item.Properties().stacksTo(16).useBlockDescriptionPrefix().setId(hangingKey))));
	}

	private static ResourceKey<LootTable> lootTable(String id) {
		return ResourceKey.create(Registries.LOOT_TABLE, JaymosKitchen.id("blocks/" + id));
	}

	private static SoundEvent sound(String id) {
		Identifier name = JaymosKitchen.id(id);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, name, SoundEvent.createVariableRangeEvent(name));
	}

	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> blockEntity(String id, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id(id), type);
	}

	/** A block's item; the ones that burn (a burn time over 0, in ticks) are fuel. */
	private static void item(String id, Block block, Item.Properties properties, List<Item> tab, int burnTime) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		if (burnTime > 0) {
			properties.component(DataComponents.COOKING_FUEL, new CookingFuel(new ResolvableInt.Constant(burnTime),
				ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER)));
		}
		tab.add(Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties.setId(key))));
	}

	private static Item plain(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
