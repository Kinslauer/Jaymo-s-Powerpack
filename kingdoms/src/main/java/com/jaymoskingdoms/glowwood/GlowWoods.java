package com.jaymoskingdoms.glowwood;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The glow-woods: three giant fungi (glowcap, bouncecap, inkcap), their cap and gill blocks, the three small mushrooms
 * that grow them with bone meal, their fungal wood sets, the glowcap lantern and the fairy ring. Ported from Spore &
 * Stem (NightBeam Studio, Apache-2.0: licenses/spore-and-stem), rewritten under our own ids. This class registers
 * everything and adds the wild worldgen; the havens' share is {@code GlowWoodHavens}.
 * <p>
 * Wild worldgen is sparse, by biome tag (data/jaymoskingdoms/tags/worldgen/biome/has_feature/): giant glowcaps in the
 * mushroom fields and caves, bouncecaps in flower forests, meadows and the mushroom fields, inkcaps in dark forests,
 * mushroom fields and swamps, and fairy rings one chunk in {@value #RING_ONE_IN} in plains and forests.
 */
public final class GlowWoods {
	/** One chunk in this many has a fairy ring (the placed feature's rarity filter says the same; kept here for the notes). */
	public static final int RING_ONE_IN = 48;
	/** Blocks that count as a fairy ring when this many stand within {@link #RING_REACH} of a spot (Powers' pink fairies read the tag). */
	public static final int RING_LEAST = 6, RING_REACH = 6;

	// ---- tags -------------------------------------------------------------------------------------------------------
	/** Where small fungi survive in any light (dirt-like blocks, moss, mycelium). */
	public static final TagKey<Block> FUNGUS_SOIL = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("fungus_soil"));
	/** The small mushrooms a fairy ring is made of; Jaymo's Powers reads this tag by name. */
	public static final TagKey<Block> FAIRY_RING = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("fairy_ring"));
	/** Every giant fungus's cap block. */
	public static final TagKey<Block> CAPS = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("caps"));

	// ---- features (the configured ones are data: data/jaymoskingdoms/worldgen/feature) --------------------------------
	public static final ResourceKey<Feature> HUGE_GLOWCAP = featureKey("huge_glowcap");
	public static final ResourceKey<Feature> HUGE_BOUNCECAP = featureKey("huge_bouncecap");
	public static final ResourceKey<Feature> HUGE_INKCAP = featureKey("huge_inkcap");
	/** Smaller giants, for the havens' gaps between the house-trees. */
	public static final ResourceKey<Feature> HAVEN_GLOWCAP = featureKey("haven_glowcap");
	public static final ResourceKey<Feature> HAVEN_BOUNCECAP = featureKey("haven_bouncecap");
	public static final ResourceKey<Feature> HAVEN_INKCAP = featureKey("haven_inkcap");
	/** A dome small enough to stand under a tree house's platform. */
	public static final ResourceKey<Feature> HAVEN_BOUNCECAP_LOW = featureKey("haven_bouncecap_low");
	public static final ResourceKey<Feature> FAIRY_RING_FEATURE = featureKey("fairy_ring");

	public static SoundEvent BOUNCECAP_BOUNCE;
	/** The small mushrooms. */
	public static Block GLOWCAP, BOUNCECAP, INKCAP;
	public static Block GLOWCAP_CAP, BOUNCECAP_CAP, INKCAP_CAP;
	public static Block GLOWCAP_STEM, BOUNCECAP_STEM, INKCAP_STEM;
	public static Block GLOWCAP_GILLS, GLOWCAP_LANTERN;
	/** Axe stripping: stem to stripped stem, hyphae to stripped hyphae. */
	public static final Map<Block, Block> STRIPPABLES = new LinkedHashMap<>();
	public static final Map<FungusWood, WoodSet> WOOD = new EnumMap<>(FungusWood.class);
	private static final List<Block> NATURAL = new java.util.ArrayList<>(), BUILDING = new java.util.ArrayList<>(), FUNCTIONAL = new java.util.ArrayList<>();

	/** One fungus wood's building set. */
	public record WoodSet(FungusWood wood, Block cap, Block stem, Block strippedStem, Block hyphae, Block strippedHyphae, Block planks,
		Block stairs, Block slab, Block fence, Block fenceGate, Block door, Block trapdoor, Block button, Block pressurePlate) {
	}

	private GlowWoods() {
	}

	public static void register() {
		BOUNCECAP_BOUNCE = Registry.register(BuiltInRegistries.SOUND_EVENT, JaymosKingdoms.id("block.bouncecap.bounce"),
			SoundEvent.createVariableRangeEvent(JaymosKingdoms.id("block.bouncecap.bounce")));
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("huge_glowcap"), HugeGlowcapFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("huge_bouncecap"), HugeBouncecapFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("huge_inkcap"), HugeInkcapFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("fairy_ring"), FairyRingFeature.CODEC);

		// the wood sets first: the small mushrooms and the gills refer to their caps
		WoodSet glow = woodSet(FungusWood.GLOWCAP, props -> new GlowcapCapBlock(props.lightLevel(state -> 10)));
		WoodSet bounce = woodSet(FungusWood.BOUNCECAP, BouncecapCapBlock::new);
		WoodSet ink = woodSet(FungusWood.INKCAP, InkcapCapBlock::new);
		GLOWCAP_CAP = glow.cap();
		BOUNCECAP_CAP = bounce.cap();
		INKCAP_CAP = ink.cap();
		GLOWCAP_STEM = glow.stem();
		BOUNCECAP_STEM = bounce.stem();
		INKCAP_STEM = ink.stem();

		GLOWCAP = fungus("glowcap", HUGE_GLOWCAP, Block.box(2, 0, 4, 12, 9, 12), MapColor.COLOR_CYAN, 9);
		BOUNCECAP = fungus("bouncecap", HUGE_BOUNCECAP, Block.box(3, 0, 3, 13, 10, 13), MapColor.COLOR_PINK, 0);
		INKCAP = fungus("inkcap", HUGE_INKCAP, Block.box(2, 0, 2, 11, 13, 11), MapColor.COLOR_LIGHT_GRAY, 0);
		for (Block fungus : List.of(GLOWCAP, BOUNCECAP, INKCAP)) {
			potted(fungus);
		}
		GLOWCAP_GILLS = block("glowcap_gills", props -> new GlowcapGillsBlock(props.mapColor(MapColor.COLOR_CYAN).noCollision()
			.strength(0.2F).sound(SoundType.SHROOMLIGHT).lightLevel(state -> 14).noOcclusion().pushReaction(PushReaction.POPPED)), 0.65F, NATURAL);
		GLOWCAP_LANTERN = block("glowcap_lantern", props -> new LanternBlock(props.mapColor(MapColor.COLOR_CYAN).forceSolidOn()
			.strength(1.5F).sound(SoundType.SHROOMLIGHT).lightLevel(state -> 15).noOcclusion().pushReaction(PushReaction.POPPED)), 0.0F, FUNCTIONAL);

		UseBlockCallback.EVENT.register(FungusStripping::onUseBlock);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> NATURAL.forEach(b -> output.accept(b.asItem())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> BUILDING.forEach(b -> output.accept(b.asItem())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> FUNCTIONAL.forEach(b -> output.accept(b.asItem())));
		addWorldgen();
	}

	/**
	 * Wild worldgen: each placed feature (data) goes into the biomes of its tag (data), in the vegetal step. A pack can move
	 * any of them by changing the tags.
	 */
	private static void addWorldgen() {
		for (String name : List.of("huge_glowcap_grove", "huge_bouncecap", "huge_bouncecap_grove", "huge_bouncecap_rare", "huge_inkcap",
			"huge_inkcap_grove", "huge_inkcap_sparse", "cave_huge_glowcap", "lush_huge_glowcap", "fairy_ring")) {
			ResourceKey<PlacedFeature> placed = ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id(name));
			TagKey<Biome> biomes = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_feature/" + name));
			BiomeModifications.addFeature(BiomeSelectors.tag(biomes), GenerationStep.Decoration.VEGETAL_DECORATION, placed);
		}
	}

	private static ResourceKey<Feature> featureKey(String name) {
		return ResourceKey.create(Registries.FEATURE, JaymosKingdoms.id(name));
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, float compost, List<Block> tab) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
		item(name, block, compost);
		if (tab != null) {
			tab.add(block);
		}
		return block;
	}

	/** A block no item opens (a flower pot). */
	private static Block blockOnly(String name, Function<BlockBehaviour.Properties, Block> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
	}

	private static void item(String name, Block block, float compost) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item.Properties properties = new Item.Properties().setId(key).useBlockDescriptionPrefix();
		if (compost > 0.0F) {
			properties = compost <= 0.65F ? properties.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM) : properties.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH);
		}
		Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties));
	}

	private static Block fungus(String name, ResourceKey<Feature> huge, VoxelShape shape, MapColor color, int light) {
		Block block = block(name, props -> new FungusBlock(huge, shape, props.mapColor(color).noCollision().instabreak().randomTicks()
			.sound(SoundType.FUNGUS).offsetType(BlockBehaviour.OffsetType.XZ).lightLevel(state -> light).pushReaction(PushReaction.POPPED)), 0.65F, NATURAL);
		return block;
	}

	private static void potted(Block fungus) {
		String name = "potted_" + BuiltInRegistries.BLOCK.getKey(fungus).getPath();
		int light = fungus.defaultBlockState().getLightEmission();
		blockOnly(name, props -> new FlowerPotBlock(fungus, props.instabreak().noOcclusion().lightLevel(state -> light).pushReaction(PushReaction.POPPED)));
	}

	private static boolean never(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
		return false;
	}

	private static BlockBehaviour.Properties stemProps(BlockBehaviour.Properties props, FungusWood wood) {
		return props.mapColor(wood.stemColor()).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.STEM);
	}

	private static WoodSet woodSet(FungusWood wood, Function<BlockBehaviour.Properties, ? extends HugeMushroomBlock> capFactory) {
		String w = wood.id();
		MapColor planksColor = wood.planksColor();
		Block cap = block(w + "_cap", props -> capFactory.apply(props.mapColor(wood.capColor()).instrument(NoteBlockInstrument.BASS)
			.strength(0.3F).sound(SoundType.WART_BLOCK)), 0.85F, BUILDING);
		Block stem = block(w + "_stem", props -> new RotatedPillarBlock(stemProps(props, wood)), 0.0F, BUILDING);
		Block strippedStem = block("stripped_" + w + "_stem", props -> new RotatedPillarBlock(stemProps(props, wood)), 0.0F, BUILDING);
		Block hyphae = block(w + "_hyphae", props -> new RotatedPillarBlock(stemProps(props, wood)), 0.0F, BUILDING);
		Block strippedHyphae = block("stripped_" + w + "_hyphae", props -> new RotatedPillarBlock(stemProps(props, wood)), 0.0F, BUILDING);
		Block planks = block(w + "_planks", props -> new Block(props.mapColor(planksColor).instrument(NoteBlockInstrument.BASS)
			.strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD)), 0.0F, BUILDING);
		Block stairs = block(w + "_stairs", props -> new WoodBlocks.Stairs(planks.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(planks)
			.setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(w + "_stairs")))), 0.0F, BUILDING);
		Block slab = block(w + "_slab", props -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(planks)
			.setId(ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(w + "_slab")))), 0.0F, BUILDING);
		Block fence = block(w + "_fence", props -> new FenceBlock(props.mapColor(planksColor).forceSolidOn().instrument(NoteBlockInstrument.BASS)
			.strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD)), 0.0F, BUILDING);
		Block fenceGate = block(w + "_fence_gate", props -> new FenceGateBlock(wood.woodType(), props.mapColor(planksColor).forceSolidOn()
			.instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F)), 0.0F, BUILDING);
		Block door = block(w + "_door", props -> new WoodBlocks.Door(wood.setType(), props.mapColor(planksColor).instrument(NoteBlockInstrument.BASS)
			.strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED)), 0.0F, BUILDING);
		Block trapdoor = block(w + "_trapdoor", props -> new WoodBlocks.Trapdoor(wood.setType(), props.mapColor(planksColor)
			.instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(GlowWoods::never)), 0.0F, BUILDING);
		Block button = block(w + "_button", props -> new WoodBlocks.Button(wood.setType(), 30, props.noCollision().strength(0.5F)
			.pushReaction(PushReaction.POPPED)), 0.0F, BUILDING);
		Block pressurePlate = block(w + "_pressure_plate", props -> new WoodBlocks.PressurePlate(wood.setType(), props.mapColor(planksColor)
			.forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F).pushReaction(PushReaction.POPPED)), 0.0F, BUILDING);
		STRIPPABLES.put(stem, strippedStem);
		STRIPPABLES.put(hyphae, strippedHyphae);
		WoodSet set = new WoodSet(wood, cap, stem, strippedStem, hyphae, strippedHyphae, planks, stairs, slab, fence, fenceGate, door, trapdoor,
			button, pressurePlate);
		WOOD.put(wood, set);
		return set;
	}

	/** Whether {@code state} is part of a giant fungus (a cap, or a gill). */
	public static boolean isCapOrGill(BlockState state) {
		return state.is(GLOWCAP_CAP) || state.is(BOUNCECAP_CAP) || state.is(INKCAP_CAP) || state.is(GLOWCAP_GILLS);
	}
}
