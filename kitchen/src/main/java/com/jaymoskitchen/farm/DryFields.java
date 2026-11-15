package com.jaymoskitchen.farm;

import com.jaymoskitchen.delight.Delight;
import com.jaymoskitchen.delight.DelightFarm;
import com.jaymoskitchen.herb.PmehFarming;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Dry fields (the GT New Horizons picks, patch 2). A hoe makes farmland only within {@link #WATER_REACH} blocks of
 * water, as far as farmland itself drinks (the horse plow too, {@code CartEntity}); tilling costs a hoe 5 uses
 * ({@code data/minecraft/block_transformer/hoe.json}). Breaking grass and ferns gives no seeds. A hoe worked over grass
 * too far from water for farmland scratches through it for seeds instead: one time in {@link #FORAGE_ODDS} it turns one
 * up, by the land ({@link #FORAGE}), and each try costs {@link #FORAGE_WEAR} of the hoe.
 */
public final class DryFields {
	/** Farmland needs water this near (blocks across; vanilla farmland drinks as far). */
	public static final int WATER_REACH = 4;
	/** A scratch through grass turns up seeds one time in this many, and wears the hoe this much. */
	public static final int FORAGE_ODDS = 4, FORAGE_WEAR = 1;

	/** Breaking these gives no seeds. */
	private static final Set<Identifier> GRASSES = Set.of(Identifier.withDefaultNamespace("blocks/short_grass"),
		Identifier.withDefaultNamespace("blocks/tall_grass"), Identifier.withDefaultNamespace("blocks/fern"),
		Identifier.withDefaultNamespace("blocks/large_fern"));

	/** A seed foraged from grass: how likely against the others, and the lands it's found in (none: anywhere). */
	public record Forage(Supplier<Item> seed, int weight, List<TagKey<Biome>> lands, String where) {
		public boolean growsIn(Holder<Biome> biome) {
			if (lands.isEmpty()) {
				return true;
			}
			for (TagKey<Biome> land : lands) {
				if (biome.is(land)) {
					return true;
				}
			}
			return false;
		}
	}

	/** What grass gives up, land by land (also JEI's page). The wild crops' lands, where there's grass to work. */
	public static final List<Forage> FORAGE = List.of(
		new Forage(() -> Items.WHEAT_SEEDS, 8, List.of(), "anywhere"),
		new Forage(() -> Hemp.SEEDS, 3, List.of(), "anywhere"),
		new Forage(() -> Delight.CABBAGE_SEEDS, 3, List.of(ConventionalBiomeTags.IS_PLAINS), "plains"),
		new Forage(() -> Items.BEETROOT_SEEDS, 2, List.of(ConventionalBiomeTags.IS_PLAINS), "plains"),
		new Forage(() -> Items.PUMPKIN_SEEDS, 2, List.of(ConventionalBiomeTags.IS_TAIGA, ConventionalBiomeTags.IS_FOREST), "woods"),
		new Forage(() -> DelightFarm.TOMATO_SEEDS, 3, List.of(ConventionalBiomeTags.IS_HOT_OVERWORLD), "hot"),
		new Forage(() -> PmehFarming.SEEDS, 2, List.of(ConventionalBiomeTags.IS_JUNGLE, ConventionalBiomeTags.IS_SAVANNA), "jungle_savanna"),
		new Forage(() -> Items.MELON_SEEDS, 2, List.of(ConventionalBiomeTags.IS_JUNGLE), "jungle"),
		new Forage(() -> Delight.RICE, 3, List.of(ConventionalBiomeTags.IS_SWAMP), "swamp"));

	private DryFields() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (player.isSpectator() || !isHoe(held) || hit.getDirection() == Direction.DOWN || !state.is(BlockTags.TURNS_INTO_FARMLAND)
				|| !level.getBlockState(pos.above()).isAir() || nearWater(level, pos)) {
				return InteractionResult.PASS;
			}
			// too dry for farmland: grass is scratched through for seeds, bare dirt left as it is
			if (!state.is(Blocks.GRASS_BLOCK)) {
				return InteractionResult.FAIL;
			}
			if (level instanceof ServerLevel server) {
				server.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 0.6F, 1.4F);
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
					8, 0.3, 0.05, 0.3, 0.0);
				held.hurtAndBreak(FORAGE_WEAR, player, hand);
				Item seed = server.getRandom().nextInt(FORAGE_ODDS) == 0 ? forage(server.getBiome(pos), server.getRandom()) : null;
				if (seed != null) {
					Block.popResourceFromFace(server, pos, Direction.UP, new ItemStack(seed));
				}
			}
			return InteractionResult.SUCCESS;
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().map(key -> GRASSES.contains(key.identifier())).orElse(false)) {
				drops.removeIf(stack -> stack.is(ConventionalItemTags.SEEDS) || stack.is(Sowing.SOWABLE));
			}
		});
	}

	/** A hoe: anything that works the ground as one ({@link BlockTransformers#HOE}). */
	public static boolean isHoe(ItemStack stack) {
		Holder<BlockTransformer> transformer = stack.get(DataComponents.BLOCK_TRANSFORMER);
		return transformer != null && transformer.is(BlockTransformers.HOE);
	}

	/** Water near enough {@code soil} for farmland (vanilla farmland's own reach). */
	public static boolean nearWater(LevelReader level, BlockPos soil) {
		return level.findBlocksIn(soil.offset(-WATER_REACH, 0, -WATER_REACH), soil.offset(WATER_REACH, 1, WATER_REACH))
			.filterState(s -> s.getFluidState().is(FluidTags.WATER)).anyMatched();
	}

	/** One seed from the grass of {@code biome}, by weight. */
	private static @Nullable Item forage(Holder<Biome> biome, RandomSource random) {
		List<Forage> here = new ArrayList<>();
		int total = 0;
		for (Forage f : FORAGE) {
			if (f.growsIn(biome)) {
				here.add(f);
				total += f.weight();
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Forage f : here) {
			roll -= f.weight();
			if (roll < 0) {
				return f.seed().get();
			}
		}
		return null;
	}
}
