package com.jaymospowers.alchemy.herbs;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.hunter.Leans.Lean;
import com.jaymospowers.alchemy.hunter.Pastes;
import com.jaymospowers.ground.StoneAge;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LevelReader;

/**
 * The herbs (Hunter's Road, patch 7, "D7") and the poison garden ("T-B1", "T-B2"; ideas from Toxony, written clean-room, real
 * plant names only). Each grows where it does for a reason, and each is read by the Scrying Lens for its essences
 * ({@code data/jaymospowers/alchemy/essences/herbs.json}):
 * <ul>
 * <li><b>Feverfew</b> and <b>valerian</b>, the two meadow crop-flowers: open flowery ground (meadows, flower forests); also
 * sown on farmland from seed.</li>
 * <li><b>Juniper</b>, a berry bush of the taiga ({@link JuniperBushBlock}): evergreen woods on poor, cold soil.</li>
 * <li><b>Cave ivy</b>, a vine on cave walls and ceilings: damp stone under the ground, where water seeps.</li>
 * <li><b>Bracken</b>, a tall fern: the shaded floor of taiga and dark forest.</li>
 * <li><b>Cardinal flower</b>, a red flower of the swamp: wet banks.</li>
 * <li><b>Earthball</b> (a puffball) and <b>honey fungus</b> (a colony mushroom): both spread on random ticks like mushrooms and
 * both grow into huge ones with bone meal; earthballs under woodland, honey fungus where old wood rots.</li>
 * <li><b>Mandrake root</b>: dug up by sniffers now and then ({@link #SNIFFER_CHANCE}), for they smell it.</li>
 * <li><b>Ergot</b>: a purple fungus that grows in the ears of wheat in a wet season, so wheat reaped in rain may bear it
 * ({@link #ERGOT_CHANCE}). Kingdoms makes a poison of it.</li>
 * </ul>
 * <b>The poison garden</b>: jungle mint (jungle), nightshade (dark forest), water hemlock (swamp), monkshood (the cold taiga)
 * and bloodroot (the crimson forest) each have a lean ({@code Leans}). Their seeds are made by grinding the herb in the mortar,
 * and they grow only on farmland poisoned with paste ({@link PoisonedFarmlandBlock}). Wild ones stand where they belong
 * ({@link HerbCropBlock#WILD}). {@link Grafting} grows five more strains from them.
 */
public final class Garden {
	/** Chance (out of 1) a sniffer's dig turns up mandrake as well, and a rain-wet wheat harvest bears ergot. */
	public static final float SNIFFER_CHANCE = 0.12F, ERGOT_CHANCE = 0.15F;

	/** The nine herbs (a data tag), for tonics and the herbalist to ask. */
	public static final TagKey<Item> HERBS = TagKey.create(Registries.ITEM, JaymosPowers.id("garden_herbs"));
	/** The poison plants of the garden, base kinds and strains alike. */
	public static final TagKey<Item> POISON_PLANTS = TagKey.create(Registries.ITEM, JaymosPowers.id("poison_plants"));

	/** One crop: its id, whether it is a poison plant, and the lean it is known by (null for the meadow flowers). */
	public record Crop(String id, boolean poison, Lean lean) {
		public Block block() {
			return CROPS.get(this.id);
		}

		public Item seeds() {
			return SEEDS.get(this.id);
		}

		public Item herb() {
			return PRODUCE.get(this.id);
		}
	}

	/** The five base poison plants and the five strains grafting grows. */
	public static final Crop FEVERFEW = new Crop("feverfew", false, null), VALERIAN = new Crop("valerian", false, null),
		JUNGLE_MINT = new Crop("jungle_mint", true, Lean.FOREST), NIGHTSHADE = new Crop("nightshade", true, Lean.NETHER),
		WATER_HEMLOCK = new Crop("water_hemlock", true, Lean.SEA), MONKSHOOD = new Crop("monkshood", true, Lean.COLD),
		BLOODROOT = new Crop("bloodroot", true, Lean.HEAT),
		DUSK_HEMLOCK = new Crop("dusk_hemlock", true, Lean.NETHER), SUNBANE = new Crop("sunbane", true, Lean.HEAT),
		WARWORT = new Crop("warwort", true, Lean.DECAY), RIME_MINT = new Crop("rime_mint", true, Lean.COLD),
		EDDY_BELL = new Crop("eddy_bell", true, Lean.SEA);
	public static final Crop[] ALL = {FEVERFEW, VALERIAN, JUNGLE_MINT, NIGHTSHADE, WATER_HEMLOCK, MONKSHOOD, BLOODROOT, DUSK_HEMLOCK, SUNBANE,
		WARWORT, RIME_MINT, EDDY_BELL};

	public static final Map<String, Block> CROPS = new LinkedHashMap<>();
	public static final Map<String, Item> SEEDS = new LinkedHashMap<>(), PRODUCE = new LinkedHashMap<>();

	public static Block POISONED_FARMLAND, JUNIPER_BUSH, CAVE_IVY, BRACKEN, CARDINAL_FLOWER, EARTHBALL, HONEY_FUNGUS;
	public static Block EARTHBALL_CAP, EARTHBALL_STEM, HONEY_FUNGUS_CAP, HONEY_FUNGUS_STEM;
	public static Item JUNIPER_BERRIES, CAVE_IVY_ITEM, BRACKEN_ITEM, CARDINAL_FLOWER_ITEM, EARTHBALL_ITEM, HONEY_FUNGUS_ITEM, MANDRAKE_ROOT, ERGOT;

	private Garden() {
	}

	public static void init() {
		POISONED_FARMLAND = block("poisoned_farmland", PoisonedFarmlandBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
			.strength(0.6F).sound(SoundType.GRAVEL).randomTicks());
		for (Crop crop : ALL) {
			String id = crop.id();
			Block block = block(id + "_crop", p -> new HerbCropBlock(p, () -> SEEDS.get(id), crop.poison()), BlockBehaviour.Properties.of()
				.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
			CROPS.put(id, block);
			PRODUCE.put(id, Alchemy.item(id, Item::new));
			SEEDS.put(id, Alchemy.item(id + "_seeds", p -> new BlockItem(block, p)));
			if (crop.poison()) {
				// seeds by breaking the plant down
				StoneAge.grinds().put(PRODUCE.get(id), SEEDS.get(id));
			}
		}
		BlockBehaviour.Properties plant = BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS)
			.pushReaction(PushReaction.POPPED);
		JUNIPER_BUSH = block("juniper_bush", JuniperBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks()
			.instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.POPPED));
		JUNIPER_BERRIES = Alchemy.item("juniper_berries", p -> new BlockItem(JUNIPER_BUSH, p));
		CAVE_IVY = block("cave_ivy", GlowLichenBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).replaceable().noCollision()
			.strength(0.2F).sound(SoundType.GLOW_LICHEN).pushReaction(PushReaction.POPPED));
		CAVE_IVY_ITEM = Alchemy.item("cave_ivy", p -> new BlockItem(CAVE_IVY, p));
		BRACKEN = block("bracken", DoublePlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak()
			.sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
		BRACKEN_ITEM = Alchemy.item("bracken", p -> new DoubleHighBlockItem(BRACKEN, p));
		CARDINAL_FLOWER = block("cardinal_flower", p -> new FlowerBlock(MobEffects.FIRE_RESISTANCE, 6.0F, p), plant.mapColor(MapColor.COLOR_RED)
			.offsetType(BlockBehaviour.OffsetType.XZ));
		CARDINAL_FLOWER_ITEM = Alchemy.item("cardinal_flower", p -> new BlockItem(CARDINAL_FLOWER, p));
		EARTHBALL_CAP = block("earthball_cap", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.2F)
			.sound(SoundType.WOOD).ignitedByLava());
		EARTHBALL_STEM = block("earthball_stem", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.2F)
			.sound(SoundType.WOOD).ignitedByLava());
		HONEY_FUNGUS_CAP = block("honey_fungus_cap", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.2F)
			.sound(SoundType.WOOD).ignitedByLava());
		HONEY_FUNGUS_STEM = block("honey_fungus_stem", HugeMushroomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.2F)
			.sound(SoundType.WOOD).ignitedByLava());
		EARTHBALL = block("earthball", p -> new GrowingMushroomBlock(featureKey("huge_earthball"), p), mushroom(MapColor.COLOR_BROWN));
		HONEY_FUNGUS = block("honey_fungus", p -> new GrowingMushroomBlock(featureKey("huge_honey_fungus"), p), mushroom(MapColor.GOLD));
		EARTHBALL_ITEM = Alchemy.item("earthball", p -> new BlockItem(EARTHBALL, p));
		HONEY_FUNGUS_ITEM = Alchemy.item("honey_fungus", p -> new BlockItem(HONEY_FUNGUS, p));
		MANDRAKE_ROOT = Alchemy.item("mandrake_root", Item::new);
		ERGOT = Alchemy.item("ergot", Item::new);

		pasteOnFarmland();
		loot();
		world();
		Grafting.init();
	}

	private static BlockBehaviour.Properties mushroom(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).noCollision().instabreak().randomTicks().sound(SoundType.FUNGUS)
			.pushReaction(PushReaction.POPPED);
	}

	private static ResourceKey<Feature> featureKey(String id) {
		return ResourceKey.create(Registries.FEATURE, JaymosPowers.id(id));
	}

	private static <B extends Block> B block(String id, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/**
	 * A small mushroom that spreads on random ticks like any, stands on soil in any light or on any solid ground in the dark,
	 * and grows into its huge kind with bone meal (the feature named in {@code data/jaymospowers/worldgen/feature}).
	 */
	public static class GrowingMushroomBlock extends MushroomBlock {
		public GrowingMushroomBlock(ResourceKey<Feature> huge, Properties properties) {
			super(huge, properties);
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			BlockState below = level.getBlockState(pos.below());
			return below.is(BlockTags.DIRT) || below.is(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT) || super.canSurvive(state, level, pos);
		}
	}

	/** Poison paste (any paste) worked into ploughed farmland poisons it. */
	private static void pasteOnFarmland() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (player.isSpectator() || !held.is(Pastes.PASTES)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!state.is(Blocks.FARMLAND) || !level.getBlockState(pos.above()).isAir()) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				server.setBlock(pos, POISONED_FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, state.getValue(FarmlandBlock.MOISTURE)), Block.UPDATE_ALL);
				server.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
				server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.01);
				if (!player.hasInfiniteMaterials()) {
					held.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Mandrake from a sniffer's dig, ergot from wheat reaped in rain. */
	private static void loot() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
			if (path.equals("gameplay/sniffer_digging") && context.getRandom().nextFloat() < SNIFFER_CHANCE) {
				drops.add(new ItemStack(MANDRAKE_ROOT));
			} else if (path.equals("blocks/wheat") && context.getLevel().isRaining() && context.getRandom().nextFloat() < ERGOT_CHANCE
				&& drops.stream().anyMatch(s -> s.is(Items.WHEAT))) {
				drops.add(new ItemStack(ERGOT));
			}
		});
	}

	/** Where each wild herb grows (the placed features are data/jaymospowers/worldgen/placed_feature/herb_*). */
	@SafeVarargs
	private static void grow(String placed, GenerationStep.Decoration step, ResourceKey<net.minecraft.world.level.biome.Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), step,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(placed)));
	}

	private static void world() {
		GenerationStep.Decoration veg = GenerationStep.Decoration.VEGETAL_DECORATION;
		grow("herb_feverfew", veg, Biomes.MEADOW, Biomes.FLOWER_FOREST);
		grow("herb_valerian", veg, Biomes.MEADOW, Biomes.CHERRY_GROVE);
		grow("herb_juniper", veg, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA);
		grow("herb_bracken", veg, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.TAIGA, Biomes.DARK_FOREST);
		grow("herb_cardinal_flower", veg, Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
		grow("herb_earthball", veg, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.TAIGA);
		grow("herb_honey_fungus", veg, Biomes.DARK_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.SWAMP);
		grow("herb_jungle_mint", veg, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);
		grow("herb_nightshade", veg, Biomes.DARK_FOREST);
		grow("herb_water_hemlock", veg, Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
		grow("herb_monkshood", veg, Biomes.SNOWY_TAIGA, Biomes.GROVE, Biomes.SNOWY_SLOPES);
		grow("herb_bloodroot", veg, Biomes.CRIMSON_FOREST);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("herb_cave_ivy")));
	}
}
