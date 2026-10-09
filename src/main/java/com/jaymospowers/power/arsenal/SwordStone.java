package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The sword in the stone: a stone pedestal, out in the world, with a blade driven into it, Durandal. Only a Pocket
 * Arsenal user can draw it (the rift knows its own; for anyone else it won't budge). Drawn, it's a relic: it slips into
 * the Pocket Stash, and Reach brings it out first. The empty stone stays where it was.
 *
 * <p>Placed rarely on the overworld surface ({@code worldgen/placed_feature/sword_stone.json}), on a little worn
 * stone-brick footing.
 */
public final class SwordStone {
	public static Block BLOCK;
	public static Item DURANDAL;

	private SwordStone() {
	}

	static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("sword_in_stone"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey, new StoneBlock(BlockBehaviour.Properties.of()
			.strength(-1.0F, 3600000.0F)
			.noLootTable()
			.noOcclusion()
			.sound(SoundType.STONE)
			.pushReaction(PushReaction.IMMOVEABLE)
			.setId(blockKey)));
		ResourceKey<Item> stoneItemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("sword_in_stone"));
		Item stoneItem = Registry.register(BuiltInRegistries.ITEM, stoneItemKey, new BlockItem(BLOCK, new Item.Properties().setId(stoneItemKey)
			.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
		ResourceKey<Item> swordKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("durandal"));
		DURANDAL = Registry.register(BuiltInRegistries.ITEM, swordKey, new Item(new Item.Properties().setId(swordKey)
			.sword(ToolMaterial.NETHERITE, 4.0F, -2.6F)
			.fireResistant()
			.rarity(Rarity.EPIC)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.durandal.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
				Component.translatable("item.jaymospowers.durandal.lore2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(DURANDAL);
			output.accept(stoneItem);
		});
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("sword_stone"), Placement.CODEC);
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
			net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld()
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OCEAN).negate())
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN).negate())
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_RIVER).negate()),
			net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("sword_stone")));
	}

	/** The relic drawn from the stone: Durandal, marked as an unclaimed relic. */
	static ItemStack draw() {
		ItemStack sword = new ItemStack(DURANDAL);
		sword.set(ArsenalRelics.RELIC, true);
		return sword;
	}

	/** The pedestal; {@link #SWORD} while the blade is still in it. */
	public static final class StoneBlock extends Block {
		public static final BooleanProperty SWORD = BooleanProperty.create("sword");
		private static final VoxelShape PEDESTAL = Shapes.or(Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0), Block.box(3.0, 4.0, 3.0, 13.0, 9.0, 13.0));
		private static final VoxelShape WITH_SWORD = Shapes.or(PEDESTAL, Block.box(6.0, 9.0, 6.0, 10.0, 16.0, 10.0));

		public StoneBlock(Properties properties) {
			super(properties);
			this.registerDefaultState(this.stateDefinition.any().setValue(SWORD, true));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(SWORD);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(SWORD) ? WITH_SWORD : PEDESTAL;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return PEDESTAL;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!state.getValue(SWORD)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.SUCCESS;
			}
			if (!Powers.has(player, ArsenalPower.INSTANCE)) {
				// it won't budge
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.sword_stone.stuck").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				serverLevel.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
				return InteractionResult.SUCCESS;
			}
			serverLevel.setBlock(pos, state.setValue(SWORD, false), Block.UPDATE_ALL);
			ItemStack sword = draw();
			if (!player.getInventory().add(sword) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				serverPlayer.drop(sword, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			serverLevel.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.6F);
			serverLevel.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.7F, 0.6F);
			serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 50, 0.3, 0.8, 0.3, 0.1);
			serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 20, 0.2, 0.6, 0.2, 0.05);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.sword_stone.drawn").withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS;
		}
	}

	/** Out in the world: the stone on a worn stone-brick footing, on firm natural ground. */
	public record Placement() implements Feature {
		public static final MapCodec<Placement> CODEC = MapCodec.unit(Placement::new);

		@Override
		public MapCodec<Placement> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
			BlockPos ground = origin.below();
			if (!level.getBlockState(origin).isAir() || !level.getFluidState(ground).isEmpty()
				|| !(level.getBlockState(ground).is(BlockTags.DIRT) || level.getBlockState(ground).is(BlockTags.BASE_STONE_OVERWORLD)
					|| level.getBlockState(ground).is(BlockTags.SAND))) {
				return false;
			}
			// the footing: a worn 3x3 of stone bricks, set into the ground
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos at = ground.offset(dx, 0, dz);
					if (!level.getBlockState(at).isSolid()) {
						continue;
					}
					float roll = random.nextFloat();
					BlockState brick = roll < 0.4F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
						: roll < 0.7F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
					level.setBlock(at, brick, Block.UPDATE_CLIENTS);
				}
			}
			level.setBlock(origin, BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			return true;
		}
	}
}
