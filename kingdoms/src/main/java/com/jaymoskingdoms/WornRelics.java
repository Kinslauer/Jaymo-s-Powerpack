package com.jaymoskingdoms;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Worn old armour in the ground (the idea from Knights &amp; Heraldry; the finds, places and code are ours). Brushing the
 * suspicious sand and gravel of the old ruins (the desert's wells and pyramids, the trail ruins, the sunk ruins of the
 * sea) now and then turns up a worn piece in place of the usual find ({@link #CHANCES}): mostly iron, sometimes Templar
 * plate, rarely the horned Juggernaut plate nobody forges any more ({@link #COMMON}, {@link #RARE}). Each comes out
 * broken ({@link #worn}): no protection until it's mended with iron at an anvil, or taken apart at Jaymo's Tinkering's
 * Drafting Table to learn its pattern ({@code tinkering/smithing/Patterns}).
 * <p>
 * The <b>old battlefields</b> ({@link OldBattlefield}): here and there on open ground lies a patch of trampled earth and
 * gravel where a line gave way long ago, with a few suspicious blocks under it ({@link #BATTLEFIELD}): bones, arrowheads,
 * nails of old mail, and more often than anywhere a worn piece. Placement: {@code worldgen/placed_feature/old_battlefield.json}.
 */
public final class WornRelics {
	/** Per old ruin's archaeology table, the chance a find is a worn piece instead. */
	static final Map<ResourceKey<LootTable>, Float> CHANCES = Map.of(
		BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY, 0.05F,
		BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, 0.08F,
		BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON, 0.06F,
		BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE, 0.15F,
		BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY, 0.05F,
		BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY, 0.05F);
	/** An old battlefield's own finds, and the chance one is a worn piece. */
	static final ResourceKey<LootTable> BATTLEFIELD = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("archaeology/old_battlefield"));
	static final float BATTLEFIELD_CHANCE = 0.4F;
	/** Out of 100: an iron piece, a Templar piece, a Juggernaut piece; the rare tables (and the battlefields) lean further up. */
	static final int[] COMMON = {70, 22, 8}, RARE = {40, 40, 20};
	private static final String[] SETS = {"iron", "templar", "juggernaut"};
	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

	private WornRelics() {
	}

	static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null) {
				return;
			}
			boolean field = key.equals(BATTLEFIELD);
			Float chance = field ? Float.valueOf(BATTLEFIELD_CHANCE) : CHANCES.get(key);
			RandomSource random = context.getRandom();
			if (chance == null || random.nextFloat() >= chance) {
				return;
			}
			ItemStack piece = worn(random, field || key.equals(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE) ? RARE : COMMON);
			if (!piece.isEmpty()) {
				// (a brush turns up one thing)
				drops.clear();
				drops.add(piece);
			}
		});
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("old_battlefield"), OldBattlefield.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
				.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
				.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate())
				.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
			GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id("old_battlefield")));
	}

	/** A worn piece of the set {@code weights} picks: broken, and saying so. */
	static ItemStack worn(RandomSource random, int[] weights) {
		int roll = random.nextInt(100), set = 0;
		while (set < weights.length - 1 && roll >= weights[set]) {
			roll -= weights[set];
			set++;
		}
		String piece = PIECES[random.nextInt(PIECES.length)];
		Item item = set == 0 ? BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("iron_" + piece))
			: BuiltInRegistries.ITEM.getValue(JaymosKingdoms.id(SETS[set] + "_" + piece));
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		if (stack.getMaxDamage() > 1) {
			stack.setDamageValue(stack.getMaxDamage() - 1);
		}
		stack.set(DataComponents.LORE, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
			.withLineAdded(Component.translatable("item.jaymoskingdoms.worn_relic.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		return stack;
	}

	/**
	 * An old battlefield: on open grass, dirt, sand or gravel, a ragged patch {@link #RADIUS} across of trampled earth and
	 * gravel (sand stays sand), with {@link #MIN_FINDS} to {@link #MAX_FINDS} suspicious blocks just under it.
	 */
	public record OldBattlefield() implements Feature {
		public static final MapCodec<OldBattlefield> CODEC = MapCodec.unit(OldBattlefield::new);
		/** How far the patch reaches from its middle (blocks), how many finds lie under it, and how far down they lie. */
		static final int RADIUS = 4, MIN_FINDS = 2, MAX_FINDS = 5, DEPTH = 2;

		@Override
		public MapCodec<OldBattlefield> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos middle = ground(level, origin);
			if (middle == null) {
				return false;
			}
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					// (ragged at its edge)
					if (dx * dx + dz * dz > RADIUS * RADIUS - random.nextInt(RADIUS * 2) || random.nextInt(4) == 0) {
						continue;
					}
					BlockPos spot = ground(level, middle.offset(dx, 0, dz));
					if (spot != null && !level.getBlockState(spot).is(BlockTags.SAND)) {
						level.setBlock(spot, (random.nextInt(3) == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			boolean placed = false;
			int finds = MIN_FINDS + random.nextInt(MAX_FINDS - MIN_FINDS + 1);
			for (int i = 0; i < finds; i++) {
				BlockPos top = ground(level, middle.offset(random.nextInt(RADIUS * 2 - 1) - RADIUS + 1, 0, random.nextInt(RADIUS * 2 - 1) - RADIUS + 1));
				if (top == null) {
					continue;
				}
				BlockPos spot = top.below(random.nextInt(DEPTH));
				boolean sand = level.getBlockState(spot).is(BlockTags.SAND);
				level.setBlock(spot, (sand ? Blocks.SUSPICIOUS_SAND : Blocks.SUSPICIOUS_GRAVEL).defaultBlockState(), Block.UPDATE_CLIENTS);
				if (level.getBlockEntity(spot) instanceof BrushableBlockEntity brushable) {
					brushable.setLootTable(BATTLEFIELD, random.nextLong());
					placed = true;
				}
			}
			return placed;
		}

		/** The top ground block (grass, dirt, sand or gravel) under open air nearest {@code near}'s height, if there's one. */
		private static BlockPos ground(WorldGenLevel level, BlockPos near) {
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos pos = near.above(dy);
				BlockState here = level.getBlockState(pos);
				BlockState above = level.getBlockState(pos.above());
				boolean open = above.isAir() || above.canBeReplaced();
				if (open && (here.is(BlockTags.DIRT) || here.is(BlockTags.SAND) || here.is(Blocks.GRAVEL)) && !above.is(Blocks.WATER)) {
					return pos;
				}
			}
			return null;
		}
	}
}
