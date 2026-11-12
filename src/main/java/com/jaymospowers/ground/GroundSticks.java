package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.21.53) Andy: "sticks scattered on the ground so starting out doesn't need a tree."
 * Small piles of 1-4 vanilla sticks lie on soil (grass, podzol, dirt, coarse/rooted dirt, mud, moss, mycelium).
 * They generate under trees (leaves within 12 blocks overhead) and rarely in the open.
 * Punch to break the whole pile (drops one stick per stick), right-click to pick up one stick at a time.
 * Assets: tools/gen_loose_sticks.py.
 */
public final class GroundSticks {
	public static Block LOOSE_STICKS;
	/** A full stick pile ringed by rock piles (FirepitBlock). */
	public static Block FIREPIT;

	private GroundSticks() {
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("loose_sticks"));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("loose_sticks"));
		LOOSE_STICKS = Registry.register(BuiltInRegistries.BLOCK, key,
			LooseSticksBlock.create(sticks().setId(key)));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(LOOSE_STICKS, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("loose_sticks"), LooseSticksFeature.CODEC);

		ResourceKey<Block> pitKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("firepit"));
		FIREPIT = Registry.register(BuiltInRegistries.BLOCK, pitKey, new FirepitBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.PODZOL).strength(0.5F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
			.pushReaction(PushReaction.POPPED)
			.lightLevel(s -> s.getValue(FirepitBlock.LIT) ? FirepitBlock.LIGHT : 0).setId(pitKey)));
		// it cooks, smokes and keeps its food in the vanilla campfire block entity
		net.minecraft.world.level.block.entity.BlockEntityTypes.CAMPFIRE.addValidBlock(FIREPIT);

		// a stick laid on the ground starts a pile (sticks on a pile are the pile's own right click)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.STICK) || player.isSpectator() || hit.getDirection() != Direction.UP) {
				return InteractionResult.PASS;
			}
			BlockPos below = hit.getBlockPos();
			BlockState ground = level.getBlockState(below);
			// plain right click only on bare ground, which has nothing else to do with a click; sneak lays one anywhere
			if (!player.isSecondaryUseActive() && !isGround(ground)) {
				return InteractionResult.PASS;
			}
			BlockPos at = below.above();
			BlockState there = level.getBlockState(at);
			boolean water = level.getFluidState(at).is(FluidTags.WATER) && level.getFluidState(at).isSource();
			if (!(there.isAir() || water && there.is(Blocks.WATER) || there.canBeReplaced() && there.getFluidState().isEmpty())
				|| !Block.canSupportCenter(level, below, Direction.UP)
				|| !level.mayInteract(player, at) || !player.mayUseItemAt(at, Direction.UP, stack)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				level.setBlock(at, LOOSE_STICKS.defaultBlockState().setValue(LooseSticksBlock.NATURAL, false)
					.setValue(LooseSticksBlock.WATERLOGGED, water), Block.UPDATE_ALL);
				stack.consume(1, player);
				level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
				level.gameEvent(player, GameEvent.BLOCK_PLACE, at);
			}
			return InteractionResult.SUCCESS;
		});

		var land = BiomeSelectors.foundInOverworld()
			.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate());
		BiomeModifications.addFeature(land, GenerationStep.Decoration.VEGETAL_DECORATION, placed("loose_sticks"));
	}

	/** Bare ground: soil, sand, gravel, stone. None of these does anything on a right click. */
	private static boolean isGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(BlockTags.BASE_STONE_NETHER) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY)
			|| state.is(BlockTags.TERRACOTTA) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
	}

	private static BlockBehaviour.Properties sticks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instabreak().noCollision().noOcclusion()
			.sound(SoundType.GRASS).pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ);
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}