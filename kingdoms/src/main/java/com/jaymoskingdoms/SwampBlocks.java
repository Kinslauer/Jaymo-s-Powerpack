package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Village mods patch 8 (Andy's picks rb_blocks, rb_band, rb_trades; ideas from Ribbits, whose code and art are not
 * used): the swamp goblins' building blocks and goods, for their stilt villages ({@link SwampGoblins}) and for any
 * player: red and brown toadstool caps, the toadstool stem, mossy oak planks, the swamp lantern (a jar of glowing marsh
 * fungus, standing or hung), the big lily pad (placed on water, stood on), the umbrella leaf (a broad leaf on a stalk);
 * and the gourd shaker (play it at a goblin band's fire and you play along, {@link GoblinBands}), the eel and the cooked
 * eel. Art, models, recipes, loot and names come from {@code kingdoms/tools/gen_swamp_blocks.py}; the band's four sounds
 * from {@code tools/gen_goblin_band.py}. All our own.
 */
public final class SwampBlocks {
	/** The swamp lantern's light. */
	static final int LANTERN_LIGHT = 13;
	/** Burn and spread odds: planks like planks, caps and stems like wood, the leaf like grass. */
	static final int PLANK_BURN = 5, PLANK_SPREAD = 20, CAP_BURN = 5, CAP_SPREAD = 5, LEAF_BURN = 60, LEAF_SPREAD = 100;
	/** The shaker's rest between shakes (ticks). */
	static final int SHAKE_COOLDOWN = 5;

	public static Block RED_CAP, BROWN_CAP, STEM, MOSSY_PLANKS, LANTERN, BIG_LILY_PAD, UMBRELLA_LEAF;
	public static Item SHAKER, EEL, COOKED_EEL;

	/** The band's four sounds: one note each, pitched for the tune. */
	static final Holder<SoundEvent> DRUM = sound("goblin_band.drum"), FLUTE = sound("goblin_band.flute"), SHAKE = sound("goblin_band.shaker"),
		JUG = sound("goblin_band.jug");

	private static final List<Item> BUILDING = new ArrayList<>(), NATURE = new ArrayList<>();

	private SwampBlocks() {
	}

	private static Holder<SoundEvent> sound(String name) {
		Identifier id = JaymosKingdoms.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	static void register() {
		RED_CAP = block("red_toadstool_cap", new Block(copy("red_toadstool_cap", Blocks.RED_MUSHROOM_BLOCK)), CAP_BURN, CAP_SPREAD, BUILDING);
		BROWN_CAP = block("brown_toadstool_cap", new Block(copy("brown_toadstool_cap", Blocks.BROWN_MUSHROOM_BLOCK)), CAP_BURN, CAP_SPREAD, BUILDING);
		STEM = block("toadstool_stem", new RotatedPillarBlock(copy("toadstool_stem", Blocks.MUSHROOM_STEM)), CAP_BURN, CAP_SPREAD, BUILDING);
		MOSSY_PLANKS = block("mossy_oak_planks", new Block(copy("mossy_oak_planks", Blocks.OAK_PLANKS).mapColor(MapColor.COLOR_GREEN)),
			PLANK_BURN, PLANK_SPREAD, BUILDING);
		LANTERN = block("swamp_lantern", new LanternBlock(props("swamp_lantern").mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.5F)
			.sound(SoundType.GLASS).lightLevel(state -> LANTERN_LIGHT).noOcclusion().pushReaction(PushReaction.POPPED)), 0, 0, BUILDING);
		BIG_LILY_PAD = block("big_lily_pad", new Pad(props("big_lily_pad").mapColor(MapColor.PLANT).instabreak().sound(SoundType.LILY_PAD)
			.noOcclusion().pushReaction(PushReaction.POPPED)), 0, 0, NATURE, PlaceOnWaterBlockItem::new);
		UMBRELLA_LEAF = block("umbrella_leaf", new Leaf(props("umbrella_leaf").mapColor(MapColor.PLANT).noCollision().instabreak()
			.sound(SoundType.BIG_DRIPLEAF).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)), LEAF_BURN, LEAF_SPREAD, NATURE);
		SHAKER = item("gourd_shaker", p -> new Shaker(p.stacksTo(1)));
		EEL = item("eel", p -> new Item(p.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));
		COOKED_EEL = item("cooked_eel", p -> new Item(p.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> BUILDING.forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> NATURE.forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(SHAKER));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(EEL);
			output.accept(COOKED_EEL);
		});
	}

	private static BlockBehaviour.Properties props(String name) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)));
	}

	private static BlockBehaviour.Properties copy(String name, Block of) {
		return BlockBehaviour.Properties.ofFullCopy(of).setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)));
	}

	private static Block block(String name, Block block, int burn, int spread, List<Item> tab) {
		return block(name, block, burn, spread, tab, BlockItem::new);
	}

	private static Block block(String name, Block block, int burn, int spread, List<Item> tab, BiFunction<Block, Item.Properties, Item> item) {
		Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name)), block);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		tab.add(Registry.register(BuiltInRegistries.ITEM, key, item.apply(block, new Item.Properties().setId(key).useBlockDescriptionPrefix())));
		if (burn > 0) {
			FlammableBlockRegistry.getDefaultInstance().add(block, burn, spread);
		}
		return block;
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}

	/** The big lily pad: a lily pad grown wide enough to fill its block (vanilla keeps the lily pad's constructor to itself). */
	static final class Pad extends LilyPadBlock {
		Pad(BlockBehaviour.Properties properties) {
			super(properties);
		}
	}

	/** The umbrella leaf: a broad leaf held up on a stalk, growing from earth and mud like any small plant. */
	static final class Leaf extends BushBlock {
		Leaf(BlockBehaviour.Properties properties) {
			super(properties);
		}
	}

	/** The gourd shaker: shake it (use) for a rattle; at a goblin band's fire it plays along ({@link GoblinBands#joined}). */
	static final class Shaker extends Item {
		Shaker(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel server && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				server.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SHAKE, SoundSource.PLAYERS, 1.0F,
					0.9F + player.getRandom().nextFloat() * 0.2F);
				GoblinBands.joined(server, sp);
			}
			player.getCooldowns().addCooldown(player.getItemInHand(hand), SHAKE_COOLDOWN);
			player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			return InteractionResult.SUCCESS;
		}
	}
}
