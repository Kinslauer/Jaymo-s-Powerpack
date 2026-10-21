package com.jaymospowers.aether;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Aether II's woods, leaves, saplings and plants (docs/aether.md, "Aether II woods and plants"): the wood sets
 * (skyroot gets the shaped blocks, greatroot, wisproot and amberoot are whole sets), ten kinds of leaves and nine
 * saplings, the ground plants and flowers, the orange tree, the arilum water plants, the three mosses, the magnetic
 * shroom and rotshroom, woven skyroot sticks and the leaf pile. Ids come from {@code tools/gen_aether_flora.py}, which
 * writes every texture, model, loot table, recipe, tag and lang line. Each block's own rules are in the small classes
 * beside this one; this class registers them (and their items), and the saplings look their tree up by feature key.
 */
public final class AetherFlora {
	// ---- tuning ----
	/** Share of animate ticks an irradiated leaf lets a yellow speck drift down, and the speck's colour. */
	public static final float IRRADIATED_LEAF_PARTICLES = 0.08F;
	public static final int IRRADIATED_LEAF_COLOR = 0xD6E04A;
	/** Light of the magnetic shroom and of the blooming arilum. */
	public static final int MAGNETIC_SHROOM_LIGHT = 6, BLOOMING_ARILUM_LIGHT = 5;
	/** A wooden button stays pressed this many ticks (vanilla wood: 30). */
	public static final int BUTTON_TICKS = 30;
	/** Hardness of the aether bush, the mosses, the leaf pile, woven sticks and the mushroom blocks. */
	public static final float BUSH_HARDNESS = 0.65F, MOSS_HARDNESS = 0.1F, LEAF_PILE_HARDNESS = 0.1F, STICKS_HARDNESS = 0.5F, SHROOM_BLOCK_HARDNESS = 0.2F;
	/** Hardness of logs and planks (blast resistance of planks 3.0), doors and trapdoors. */
	public static final float LOG_HARDNESS = 2.0F, PLANKS_BLAST = 3.0F, DOOR_HARDNESS = 3.0F;
	/** Food: orange 4, wyndberry 2, satival bulb 2 (hunger, saturation). */
	public static final int ORANGE_FOOD = 4, WYNDBERRY_FOOD = 2, SATIVAL_BULB_FOOD = 2;
	public static final float ORANGE_SATURATION = 0.5F, WYNDBERRY_SATURATION = 0.3F, SATIVAL_BULB_SATURATION = 0.3F;

	/** One wood set: every shaped block of it (skyroot's first five are AetherBlocks' own). */
	public static final class Wood {
		public Block log, wood, strippedLog, strippedWood, planks;
		public Block stairs, slab, fence, fenceGate, door, trapdoor, button, pressurePlate;
	}

	public static final Wood SKYROOT = new Wood(), GREATROOT = new Wood(), WISPROOT = new Wood(), AMBEROOT = new Wood();
	public static Block MOSSY_WISPROOT_LOG, AMBEROOT_DEPOSIT;

	public static Block SKYPLANE_LEAVES, SKYBIRCH_LEAVES, SKYPINE_LEAVES, WISPROOT_LEAVES, WISPTOP_LEAVES, GREATROOT_LEAVES, GREATOAK_LEAVES,
		GREATBOA_LEAVES, AMBEROOT_LEAVES, IRRADIATED_LEAVES;
	public static Block SKYPLANE_SAPLING, SKYBIRCH_SAPLING, SKYPINE_SAPLING, WISPROOT_SAPLING, WISPTOP_SAPLING, GREATROOT_SAPLING, GREATOAK_SAPLING,
		GREATBOA_SAPLING, AMBEROOT_SAPLING;

	public static Block AETHER_SHORT_GRASS, AETHER_MEDIUM_GRASS, AETHER_LONG_GRASS, BUSH_FERN, AETHER_BUSH, HOLYSTONE_TWIG;
	public static Block HESPEROSE, TARABLOOM, POASPROUT, LILICHIME, PLURACIAN, BLADE_POA, SATIVAL_SHOOT, VALKYRIE_SPROUT, BRETTL_PLANT, BRETTL_GRASS_BUNDLE;
	public static Block ORANGE_TREE, ARILUM, BLOOMING_ARILUM;
	public static Block BRYALINN_MOSS_BLOCK, BRYALINN_MOSS_CARPET, BRYALINN_VINES, SHAYELINN_MOSS_BLOCK, SHAYELINN_MOSS_CARPET, SHAYELINN_VINES,
		AMBRELINN_MOSS_BLOCK, AMBRELINN_MOSS_CARPET;
	public static Block MAGNETIC_SHROOM, MAGNETIC_SHROOM_BLOCK, MAGNETIC_SHROOM_STEM, ROTSHROOM, ROTSHROOM_BLOCK;
	public static Block WOVEN_SKYROOT_STICKS, SKYROOT_LEAF_PILE;
	public static Item ORANGE, WYNDBERRY, ARILUM_BULB, VALKYRIE_WINGS, BRETTL_GRASS, SATIVAL_BULB;

	/** The trees the saplings grow (configured features written by the dimension's worldgen; until then a sapling just does not grow). */
	public static final Map<String, ResourceKey<Feature>> TREES = new LinkedHashMap<>();
	/** The potted version of each pot-able plant, by the plant's id. */
	public static final Map<String, Block> POTTED = new LinkedHashMap<>();

	private static final Map<Block, Block> STRIPPED = new HashMap<>();
	private static final List<ItemLike> TAB = new ArrayList<>();

	private AetherFlora() {
	}

	// ---- properties ----

	private static BlockBehaviour.Properties logProps(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(LOG_HARDNESS).sound(SoundType.WOOD);
	}

	private static BlockBehaviour.Properties planksProps(MapColor color) {
		return logProps(color).strength(LOG_HARDNESS, PLANKS_BLAST);
	}

	private static BlockBehaviour.Properties leavesProps(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
			.isValidSpawn(Blocks::never).isSuffocating(Blocks::never).isRedstoneConductor(Blocks::never);
	}

	private static BlockBehaviour.Properties plant(SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(sound).pushReaction(PushReaction.POPPED)
			.offsetType(BlockBehaviour.OffsetType.XZ);
	}

	private static BlockBehaviour.Properties saplingProps() {
		return plant(SoundType.GRASS).randomTicks().offsetType(BlockBehaviour.OffsetType.NONE);
	}

	private static BlockBehaviour.Properties mossProps(MapColor color, boolean carpet) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(MOSS_HARDNESS).sound(carpet ? SoundType.MOSS_CARPET : SoundType.MOSS)
			.pushReaction(PushReaction.POPPED);
	}

	// ---- registration ----

	/** A block and its block item (the item joins the creative tab). */
	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return block(id, factory, properties, BlockItem::new);
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
		BiFunction<Block, Item.Properties, Item> itemFactory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, itemFactory.apply(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		TAB.add(item);
		return block;
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
		TAB.add(item);
		return item;
	}

	private static Item food(String id, int nutrition, float saturation) {
		return item(id, new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(),
			Consumables.defaultFood().build()));
	}

	private static Block sapling(String tree, MapColor color) {
		ResourceKey<Feature> feature = ResourceKey.create(Registries.FEATURE, JaymosPowers.id(tree + "_tree"));
		TREES.put(tree, feature);
		return block(tree + "_sapling", p -> new AetherSaplingBlock(feature, p), saplingProps().mapColor(color));
	}

	private static Block leaves(String id, MapColor color, float particleChance, int particleColor) {
		return block(id, p -> new UntintedParticleLeavesBlock(particleChance, particleChance > 0.0F ? new DustParticleOptions(particleColor, 1.0F)
			: ParticleTypes.SMOKE, AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leavesProps(color));
	}

	/** Pots a plant: the block shows in the world as the potted plant and drops the pot and the plant. */
	private static void pot(String plantId, Block plant) {
		String id = "potted_" + plantId;
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.POPPED);
		POTTED.put(plantId, Registry.register(BuiltInRegistries.BLOCK, key, new FlowerPotBlock(plant, props.setId(key))));
	}

	/** Planks (and the log family) of a new wood set. */
	private static Wood woodSet(Wood w, String name, MapColor bark, MapColor stripped, MapColor planks) {
		w.log = block(name + "_log", RotatedPillarBlock::new, logProps(bark));
		w.wood = block(name + "_wood", RotatedPillarBlock::new, logProps(bark));
		w.strippedLog = block("stripped_" + name + "_log", RotatedPillarBlock::new, logProps(stripped));
		w.strippedWood = block("stripped_" + name + "_wood", RotatedPillarBlock::new, logProps(stripped));
		w.planks = block(name + "_planks", Block::new, planksProps(planks));
		STRIPPED.put(w.log, w.strippedLog);
		STRIPPED.put(w.wood, w.strippedWood);
		return w;
	}

	/** Stairs, slab, fence, gate, door, trapdoor, button and pressure plate from {@code w.planks}. */
	private static void shaped(Wood w, String name, MapColor color) {
		Block planks = w.planks;
		w.stairs = block(name + "_stairs", p -> new StairBlock(planks.defaultBlockState(), p), planksProps(color));
		w.slab = block(name + "_slab", SlabBlock::new, planksProps(color));
		w.fence = block(name + "_fence", FenceBlock::new, planksProps(color));
		w.fenceGate = block(name + "_fence_gate", p -> new FenceGateBlock(WoodType.OAK, p), planksProps(color).forceSolidOn());
		w.door = block(name + "_door", p -> new DoorBlock(BlockSetType.OAK, p), BlockBehaviour.Properties.of().mapColor(color)
			.strength(DOOR_HARDNESS).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.POPPED), DoubleHighBlockItem::new);
		w.trapdoor = block(name + "_trapdoor", p -> new TrapDoorBlock(BlockSetType.OAK, p), BlockBehaviour.Properties.of().mapColor(color)
			.strength(DOOR_HARDNESS).sound(SoundType.WOOD).noOcclusion().isValidSpawn(Blocks::never));
		w.button = block(name + "_button", p -> new ButtonBlock(BlockSetType.OAK, BUTTON_TICKS, p), BlockBehaviour.Properties.of().noCollision()
			.strength(0.5F).sound(SoundType.WOOD).pushReaction(PushReaction.POPPED));
		w.pressurePlate = block(name + "_pressure_plate", p -> new PressurePlateBlock(BlockSetType.OAK, p), BlockBehaviour.Properties.of()
			.mapColor(color).forceSolidOn().noCollision().strength(0.5F).sound(SoundType.WOOD).pushReaction(PushReaction.POPPED));
	}

	private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Block.box(x1, y1, z1, x2, y2, z2);
	}

	private static Block flora(String id, VoxelShape shape, boolean solidGround, BlockBehaviour.Properties props) {
		return block(id, p -> new FloraPlantBlock(shape, solidGround, p), props);
	}

	static void init() {
		// ---- woods ----
		SKYROOT.log = AetherBlocks.SKYROOT_LOG;
		SKYROOT.wood = AetherBlocks.SKYROOT_WOOD;
		SKYROOT.strippedLog = AetherBlocks.STRIPPED_SKYROOT_LOG;
		SKYROOT.strippedWood = AetherBlocks.STRIPPED_SKYROOT_WOOD;
		SKYROOT.planks = AetherBlocks.SKYROOT_PLANKS;
		shaped(SKYROOT, "skyroot", MapColor.COLOR_LIGHT_GREEN);
		woodSet(GREATROOT, "greatroot", MapColor.COLOR_BROWN, MapColor.DIRT, MapColor.COLOR_BROWN);
		shaped(GREATROOT, "greatroot", MapColor.COLOR_BROWN);
		woodSet(WISPROOT, "wisproot", MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_PURPLE, MapColor.COLOR_LIGHT_GRAY);
		shaped(WISPROOT, "wisproot", MapColor.COLOR_LIGHT_GRAY);
		MOSSY_WISPROOT_LOG = block("mossy_wisproot_log", RotatedPillarBlock::new, logProps(MapColor.COLOR_LIGHT_GRAY));
		STRIPPED.put(MOSSY_WISPROOT_LOG, WISPROOT.strippedLog);
		woodSet(AMBEROOT, "amberoot", MapColor.GOLD, MapColor.COLOR_ORANGE, MapColor.GOLD);
		shaped(AMBEROOT, "amberoot", MapColor.GOLD);
		AMBEROOT_DEPOSIT = block("amberoot_deposit", RotatedPillarBlock::new, logProps(MapColor.GOLD));
		STRIPPED.put(AMBEROOT_DEPOSIT, AMBEROOT.strippedLog);

		// ---- leaves and saplings ----
		SKYPLANE_LEAVES = leaves("skyplane_leaves", MapColor.COLOR_LIGHT_GREEN, 0.0F, 0);
		SKYBIRCH_LEAVES = leaves("skybirch_leaves", MapColor.COLOR_YELLOW, 0.0F, 0);
		SKYPINE_LEAVES = leaves("skypine_leaves", MapColor.WARPED_WART_BLOCK, 0.0F, 0);
		WISPROOT_LEAVES = leaves("wisproot_leaves", MapColor.COLOR_PINK, 0.0F, 0);
		WISPTOP_LEAVES = leaves("wisptop_leaves", MapColor.COLOR_PURPLE, 0.0F, 0);
		GREATROOT_LEAVES = leaves("greatroot_leaves", MapColor.COLOR_GREEN, 0.0F, 0);
		GREATOAK_LEAVES = leaves("greatoak_leaves", MapColor.GRASS, 0.0F, 0);
		GREATBOA_LEAVES = leaves("greatboa_leaves", MapColor.WARPED_NYLIUM, 0.0F, 0);
		AMBEROOT_LEAVES = leaves("amberoot_leaves", MapColor.GOLD, 0.0F, 0);
		IRRADIATED_LEAVES = leaves("irradiated_leaves", MapColor.COLOR_LIGHT_GREEN, IRRADIATED_LEAF_PARTICLES, IRRADIATED_LEAF_COLOR);
		SKYPLANE_SAPLING = sapling("skyplane", MapColor.COLOR_LIGHT_GREEN);
		SKYBIRCH_SAPLING = sapling("skybirch", MapColor.COLOR_YELLOW);
		SKYPINE_SAPLING = sapling("skypine", MapColor.WARPED_WART_BLOCK);
		WISPROOT_SAPLING = sapling("wisproot", MapColor.COLOR_PINK);
		WISPTOP_SAPLING = sapling("wisptop", MapColor.COLOR_PURPLE);
		GREATROOT_SAPLING = sapling("greatroot", MapColor.COLOR_GREEN);
		GREATOAK_SAPLING = sapling("greatoak", MapColor.GRASS);
		GREATBOA_SAPLING = sapling("greatboa", MapColor.WARPED_NYLIUM);
		AMBEROOT_SAPLING = sapling("amberoot", MapColor.GOLD);

		// ---- ground plants (the grasses and the fern are tinted with the biome's grass colour on the client) ----
		AETHER_SHORT_GRASS = flora("aether_short_grass", box(2, 0, 2, 14, 8, 14), false, plant(SoundType.GRASS).replaceable());
		AETHER_MEDIUM_GRASS = flora("aether_medium_grass", box(2, 0, 2, 14, 12, 14), false, plant(SoundType.GRASS).replaceable());
		AETHER_LONG_GRASS = flora("aether_long_grass", box(2, 0, 2, 14, 15, 14), false, plant(SoundType.GRASS).replaceable());
		BUSH_FERN = flora("bush_fern", box(2, 0, 2, 14, 13, 14), false, plant(SoundType.GRASS).replaceable());
		AETHER_BUSH = flora("aether_bush", box(2, 0, 2, 14, 13, 14), false, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
			.strength(BUSH_HARDNESS).sound(SoundType.GRASS).noOcclusion().pushReaction(PushReaction.POPPED).isSuffocating(Blocks::never)
			.isRedstoneConductor(Blocks::never));
		HOLYSTONE_TWIG = flora("holystone_twig", box(2, 0, 2, 14, 1, 14), true, plant(SoundType.WOOD).offsetType(BlockBehaviour.OffsetType.NONE));

		// ---- flowers and shoots ----
		HESPEROSE = flora("hesperose", box(4, 0, 4, 12, 12, 12), false, plant(SoundType.GRASS));
		POASPROUT = flora("poasprout", box(4, 0, 4, 12, 10, 12), false, plant(SoundType.GRASS));
		LILICHIME = flora("lilichime", box(4, 0, 4, 12, 12, 12), false, plant(SoundType.GRASS));
		PLURACIAN = flora("pluracian", box(4, 0, 4, 12, 11, 12), false, plant(SoundType.GRASS));
		BLADE_POA = flora("blade_poa", box(3, 0, 3, 13, 14, 13), false, plant(SoundType.GRASS));
		SATIVAL_SHOOT = flora("satival_shoot", box(3, 0, 3, 13, 12, 13), false, plant(SoundType.CROP));
		VALKYRIE_SPROUT = flora("valkyrie_sprout", box(3, 0, 3, 13, 11, 13), false, plant(SoundType.GRASS));
		BRETTL_PLANT = flora("brettl_plant", box(3, 0, 3, 13, 13, 13), false, plant(SoundType.GRASS));
		TARABLOOM = block("tarabloom", TallFlowerBlock::new, plant(SoundType.GRASS), DoubleHighBlockItem::new);
		BRETTL_GRASS_BUNDLE = block("brettl_grass_bundle", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
			.strength(0.5F).sound(SoundType.GRASS));

		// ---- the orange tree ----
		ORANGE_TREE = block("orange_tree", OrangeTreeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).noCollision()
			.randomTicks().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.POPPED), DoubleHighBlockItem::new);
		ORANGE = food("orange", ORANGE_FOOD, ORANGE_SATURATION);
		WYNDBERRY = food("wyndberry", WYNDBERRY_FOOD, WYNDBERRY_SATURATION);
		SATIVAL_BULB = food("satival_bulb", SATIVAL_BULB_FOOD, SATIVAL_BULB_SATURATION);
		VALKYRIE_WINGS = item("valkyrie_wings", new Item.Properties());
		BRETTL_GRASS = item("brettl_grass", new Item.Properties());

		// ---- water plants ----
		ARILUM = block("arilum", LilyPadBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instabreak().sound(SoundType.LILY_PAD)
			.noOcclusion().pushReaction(PushReaction.POPPED), PlaceOnWaterBlockItem::new);
		BLOOMING_ARILUM = block("blooming_arilum", LilyPadBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).instabreak()
			.sound(SoundType.LILY_PAD).noOcclusion().lightLevel(state -> BLOOMING_ARILUM_LIGHT).pushReaction(PushReaction.POPPED), PlaceOnWaterBlockItem::new);
		ARILUM_BULB = item("arilum_bulb", new Item.Properties());

		// ---- mosses ----
		BRYALINN_MOSS_BLOCK = block("bryalinn_moss_block", Block::new, mossProps(MapColor.COLOR_GREEN, false));
		BRYALINN_MOSS_CARPET = block("bryalinn_moss_carpet", CarpetBlock::new, mossProps(MapColor.COLOR_GREEN, true));
		BRYALINN_VINES = block("bryalinn_vines", VineBlock::new, vineProps(MapColor.COLOR_GREEN));
		SHAYELINN_MOSS_BLOCK = block("shayelinn_moss_block", Block::new, mossProps(MapColor.COLOR_CYAN, false));
		SHAYELINN_MOSS_CARPET = block("shayelinn_moss_carpet", CarpetBlock::new, mossProps(MapColor.COLOR_CYAN, true));
		SHAYELINN_VINES = block("shayelinn_vines", VineBlock::new, vineProps(MapColor.COLOR_CYAN));
		AMBRELINN_MOSS_BLOCK = block("ambrelinn_moss_block", Block::new, mossProps(MapColor.COLOR_YELLOW, false));
		AMBRELINN_MOSS_CARPET = block("ambrelinn_moss_carpet", CarpetBlock::new, mossProps(MapColor.COLOR_YELLOW, true));

		// ---- mushrooms ----
		MAGNETIC_SHROOM = flora("magnetic_shroom", box(5, 0, 5, 11, 7, 11), true, plant(SoundType.FUNGUS).mapColor(MapColor.COLOR_LIGHT_BLUE)
			.lightLevel(state -> MAGNETIC_SHROOM_LIGHT));
		MAGNETIC_SHROOM_BLOCK = block("magnetic_shroom_block", HugeMushroomBlock::new, shroomProps(MapColor.COLOR_LIGHT_BLUE));
		MAGNETIC_SHROOM_STEM = block("magnetic_shroom_stem", HugeMushroomBlock::new, shroomProps(MapColor.COLOR_LIGHT_GRAY));
		ROTSHROOM = flora("rotshroom", box(5, 0, 5, 11, 7, 11), true, plant(SoundType.FUNGUS).mapColor(MapColor.COLOR_BROWN));
		ROTSHROOM_BLOCK = block("rotshroom_block", HugeMushroomBlock::new, shroomProps(MapColor.COLOR_BROWN));

		// ---- misc ----
		WOVEN_SKYROOT_STICKS = block("woven_skyroot_sticks", WovenSticksBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(STICKS_HARDNESS).sound(SoundType.WOOD).noOcclusion());
		SKYROOT_LEAF_PILE = block("skyroot_leaf_pile", CarpetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GRASS)
			.strength(LEAF_PILE_HARDNESS).sound(SoundType.GRASS).noOcclusion().pushReaction(PushReaction.POPPED));

		// ---- flower pots ----
		pot("skyplane_sapling", SKYPLANE_SAPLING);
		pot("skybirch_sapling", SKYBIRCH_SAPLING);
		pot("skypine_sapling", SKYPINE_SAPLING);
		pot("wisproot_sapling", WISPROOT_SAPLING);
		pot("wisptop_sapling", WISPTOP_SAPLING);
		pot("greatroot_sapling", GREATROOT_SAPLING);
		pot("greatoak_sapling", GREATOAK_SAPLING);
		pot("greatboa_sapling", GREATBOA_SAPLING);
		pot("amberoot_sapling", AMBEROOT_SAPLING);
		pot("hesperose", HESPEROSE);
		pot("poasprout", POASPROUT);
		pot("lilichime", LILICHIME);
		pot("pluracian", PLURACIAN);
		pot("bush_fern", BUSH_FERN);
		pot("magnetic_shroom", MAGNETIC_SHROOM);
		pot("rotshroom", ROTSHROOM);

		// ---- the creative tab ("The Aether" is made in AetherItems) ----
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> TAB.forEach(output::accept));

		// ---- an axe strips the new logs (skyroot's is in Aether.init) ----
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Block stripped = STRIPPED.get(state.getBlock());
			ItemStack held = player.getItemInHand(hand);
			if (stripped == null || player.isShiftKeyDown() || !held.is(ItemTags.AXES)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				server.setBlockAndUpdate(pos, stripped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
				server.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
				held.hurtAndBreak(1, sp, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static BlockBehaviour.Properties vineProps(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).replaceable().noCollision().strength(0.2F).sound(SoundType.VINE)
			.pushReaction(PushReaction.POPPED);
	}

	private static BlockBehaviour.Properties shroomProps(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(SHROOM_BLOCK_HARDNESS).sound(SoundType.WOOD);
	}

	/** A block of this track by its id, or null (for other tracks that do not want a field for every block). */
	public static Block get(String id) {
		return BuiltInRegistries.BLOCK.getOptional(JaymosPowers.id(id)).orElse(null);
	}
}
