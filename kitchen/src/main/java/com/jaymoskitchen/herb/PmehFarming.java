package com.jaymoskitchen.herb;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.delight.WildCropBlock;
import com.jaymoskitchen.farm.Manure;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.5.29) Growing and drying Pmeh. Andy (2026-10-25), for Jaymo's Kingdoms' speakeasies: Pmeh is a rare commodity
 * ("it does have seeds actually, you just have to be good at growing and drying the plant to have quality enough to
 * sell like the dealers do").
 * <ul>
 * <li>Shearing tall grass no longer gives Pmeh. <b>Wild Pmeh</b> grows only in jungles and savannas, in scattered
 * patches ({@code worldgen/placed_feature/wild_pmeh.json}); picked, it gives fresh Pmeh of {@link #FAIR} grade and
 * now and then a seed.</li>
 * <li><b>Pmeh seeds</b> grow on farmland like wheat. Picked grown, the crop gives fresh Pmeh whose grade is how well it
 * was grown ({@link #grown}): a point each for fertile farmland, wet farmland and a warm biome, so {@link #PRIME} takes
 * all three.</li>
 * <li><b>Fresh Pmeh</b> is hung on a drying rack to dry into Pmeh, keeping its grade; left hanging dry past
 * {@link #OVERDRY_TICKS}, it loses a grade each time ({@code DryingRackEntity}).</li>
 * </ul>
 * The grade is a number on the stack ({@link #GRADE}, 0-3; none counts as fair), which Kingdoms' dealers read: they
 * buy only {@link #FINE} and better, and sell prime.
 */
public final class PmehFarming {
	public static final int SCRAGGLY = 0, FAIR = 1, FINE = 2, PRIME = 3;
	/** A dry Pmeh left on the rack loses a grade each this long (ticks: half a day). */
	public static final int OVERDRY_TICKS = 12000;
	/** A biome this warm or warmer counts as warm for the crop. */
	static final float WARM = 0.95F;

	/** How good a Pmeh (fresh or dried) is: 0 scraggly to 3 prime. */
	public static DataComponentType<Integer> GRADE;
	public static Block WILD_PMEH;
	public static PmehCropBlock CROP;
	public static Item SEEDS, FRESH_PMEH;

	private PmehFarming() {
	}

	public static void register() {
		GRADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("pmeh_grade"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(SCRAGGLY, PRIME)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		WILD_PMEH = block("wild_pmeh", WildCropBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
		CROP = block("pmeh_crop", PmehCropBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks()
			.instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
		ResourceKey<Item> seedsKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("pmeh_seeds"));
		SEEDS = Registry.register(BuiltInRegistries.ITEM, seedsKey, new BlockItem(CROP, new Item.Properties().useItemDescriptionPrefix().setId(seedsKey)));
		ResourceKey<Item> freshKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("fresh_pmeh"));
		FRESH_PMEH = Registry.register(BuiltInRegistries.ITEM, freshKey, new Item(new Item.Properties().setId(freshKey)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(new ItemStack(SEEDS)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(new ItemStack(FRESH_PMEH)));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_JUNGLE).or(BiomeSelectors.tag(BiomeTags.IS_SAVANNA)),
			GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id("wild_pmeh")));

		// a grown crop picked by hand: fresh Pmeh, as good as it was grown (the loot table gives only the seeds)
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && state.is(CROP) && CROP.isMaxAge(state) && !player.getAbilities().instabuild) {
				int count = 2 + server.getRandom().nextInt(3);
				Block.popResource(level, pos, stamp(new ItemStack(FRESH_PMEH, count), grown(server, pos)));
			}
		});
	}

	/** How well a crop at {@code pos} (just picked: the farmland below still there) was grown: 0-3. */
	static int grown(ServerLevel level, BlockPos pos) {
		BlockState soil = level.getBlockState(pos.below());
		int grade = 0;
		if (soil.is(Manure.FERTILE_FARMLAND)) {
			grade++;
		}
		if (soil.hasProperty(FarmlandBlock.MOISTURE) && soil.getValue(FarmlandBlock.MOISTURE) >= FarmlandBlock.MAX_MOISTURE) {
			grade++;
		}
		if (level.getBiome(pos).value().getBaseTemperature() >= WARM) {
			grade++;
		}
		return grade;
	}

	/** {@code stack}'s grade (fair if it has none). */
	public static int grade(ItemStack stack) {
		Integer g = stack.get(GRADE);
		return g == null ? FAIR : g;
	}

	public static ItemStack stamp(ItemStack stack, int grade) {
		stack.set(GRADE, Mth.clamp(grade, SCRAGGLY, PRIME));
		return stack;
	}

	/** Fresh Pmeh dries into Pmeh of the same grade. */
	public static ItemStack dried(ItemStack fresh) {
		return stamp(new ItemStack(ModItems.PMEH), grade(fresh));
	}

	public static boolean isPmeh(ItemStack stack) {
		return stack.is(ModItems.PMEH) || stack.is(FRESH_PMEH);
	}

	/** The grade's lang key ({@code item.jaymoskitchen.pmeh.grade.<n>}). */
	public static String gradeKey(int grade) {
		return "item.jaymoskitchen.pmeh.grade." + grade;
	}

	private static <B extends Block> B block(String name, java.util.function.Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/** Pmeh on farmland, like wheat. */
	public static class PmehCropBlock extends CropBlock {
		public PmehCropBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected ItemLike getBaseSeedId() {
			return SEEDS;
		}
	}
}
