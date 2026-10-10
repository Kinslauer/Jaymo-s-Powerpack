package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Swords in stones: pedestals out in the world, each with a legendary blade driven into it. Only a Pocket Arsenal user
 * can draw one (the rift knows its own; for anyone else it won't budge). Drawn, it's a relic: it slips into the Pocket
 * Stash, and Reach brings it out first. The empty stone stays where it was. Each blade has its own home
 * ({@link Variant}): Durandal on the Overworld's surface, Laevateinn in the Nether, Caladbolg on the End's outer
 * islands, Gram in the Deep Dark, Hrunting in the frozen mountains. Placed by {@code worldgen/placed_feature/<id>.json};
 * art from {@code tools/gen_sword_stone.py}. Each blade's gifts are in {@link StoneSwords}.
 */
public final class SwordStone {
	/** Each sword, its stone, where it lies and what it's set in. */
	public enum Variant {
		DURANDAL("sword_in_stone", "durandal", "sword_stone", false, GenerationStep.Decoration.SURFACE_STRUCTURES,
			List.of(Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS)),
		LAEVATEINN("nether_sword_stone", "laevateinn", "sword_stone_nether", true, GenerationStep.Decoration.SURFACE_STRUCTURES,
			List.of(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE)),
		CALADBOLG("end_sword_stone", "caladbolg", "sword_stone_end", false, GenerationStep.Decoration.SURFACE_STRUCTURES,
			List.of(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICKS)),
		GRAM("deep_sword_stone", "gram", "sword_stone_deep", true, GenerationStep.Decoration.UNDERGROUND_DECORATION,
			List.of(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.SCULK)),
		HRUNTING("frost_sword_stone", "hrunting", "sword_stone_frost", false, GenerationStep.Decoration.SURFACE_STRUCTURES,
			List.of(Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK));

		final String block, sword, feature;
		/** Placed underground (scanning down for a floor) rather than on the surface. */
		final boolean underground;
		final GenerationStep.Decoration step;
		final List<Block> footing;
		Block stone;
		Item item;

		Variant(String block, String sword, String feature, boolean underground, GenerationStep.Decoration step, List<Block> footing) {
			this.block = block;
			this.sword = sword;
			this.feature = feature;
			this.underground = underground;
			this.step = step;
			this.footing = footing;
		}

		Predicate<BiomeSelectionContext> biomes() {
			return switch (this) {
				case DURANDAL -> BiomeSelectors.foundInOverworld()
					.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
					.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate())
					.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate())
					.and(BiomeSelectors.includeByKey(Biomes.DEEP_DARK).negate());
				case LAEVATEINN -> BiomeSelectors.foundInTheNether();
				case CALADBOLG -> BiomeSelectors.foundInTheEnd().and(BiomeSelectors.includeByKey(Biomes.THE_END).negate());
				case GRAM -> BiomeSelectors.includeByKey(Biomes.DEEP_DARK);
				case HRUNTING -> BiomeSelectors.includeByKey(Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.GROVE,
					Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES);
			};
		}

		/** Ground the stone may stand on. */
		boolean ground(BlockState state) {
			return switch (this) {
				case DURANDAL -> state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND);
				case LAEVATEINN -> state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.NYLIUM) || state.is(Blocks.SOUL_SOIL)
					|| state.is(Blocks.SOUL_SAND);
				case CALADBOLG -> state.is(Blocks.END_STONE);
				case GRAM -> state.is(Blocks.DEEPSLATE) || state.is(Blocks.SCULK) || state.is(BlockTags.BASE_STONE_OVERWORLD);
				case HRUNTING -> state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.ICE)
					|| state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD);
			};
		}
	}

	/** The first, Durandal's: kept for the code that names it. */
	public static Block BLOCK;
	public static Item DURANDAL;
	private static final Map<Block, Variant> BY_BLOCK = new java.util.HashMap<>();

	private SwordStone() {
	}

	static void register() {
		for (Variant variant : Variant.values()) {
			ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(variant.block));
			variant.stone = Registry.register(BuiltInRegistries.BLOCK, blockKey, new StoneBlock(BlockBehaviour.Properties.of()
				.strength(-1.0F, 3600000.0F)
				.noLootTable()
				.noOcclusion()
				.sound(SoundType.STONE)
				.pushReaction(PushReaction.IMMOVEABLE)
				.setId(blockKey)));
			BY_BLOCK.put(variant.stone, variant);
			ResourceKey<Item> stoneItemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(variant.block));
			Item stoneItem = Registry.register(BuiltInRegistries.ITEM, stoneItemKey, new BlockItem(variant.stone, new Item.Properties().setId(stoneItemKey)
				.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
			ResourceKey<Item> swordKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(variant.sword));
			Item.Properties sword = StoneSwords.properties(variant, new Item.Properties().setId(swordKey).rarity(Rarity.EPIC));
			if (variant != Variant.HRUNTING) {
				sword.fireResistant();
			}
			variant.item = Registry.register(BuiltInRegistries.ITEM, swordKey, new StoneSwords.Blade(sword, variant));
			CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
				output.accept(variant.item);
				output.accept(stoneItem);
			});
			Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id(variant.feature), new Placement(variant).codec());
			BiomeModifications.addFeature(variant.biomes(), variant.step,
				ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(variant.feature)));
		}
		BLOCK = Variant.DURANDAL.stone;
		DURANDAL = Variant.DURANDAL.item;
		StoneSwords.register();
	}

	/** The relic drawn from a stone: its sword, marked as an unclaimed relic, with its enchantment baked in. */
	static ItemStack draw(ServerLevel level, Variant variant) {
		ItemStack sword = new ItemStack(variant.item);
		sword.set(ArsenalRelics.RELIC, true);
		StoneSwords.enchant(level, variant, sword);
		return sword;
	}

	/** A pedestal; {@link #SWORD} while the blade is still in it. */
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
			Variant variant = BY_BLOCK.getOrDefault(state.getBlock(), Variant.DURANDAL);
			serverLevel.setBlock(pos, state.setValue(SWORD, false), Block.UPDATE_ALL);
			ItemStack sword = draw(serverLevel, variant);
			if (!player.getInventory().add(sword) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				serverPlayer.drop(sword, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			serverLevel.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.6F);
			serverLevel.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.7F, 0.6F);
			serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 50, 0.3, 0.8, 0.3, 0.1);
			serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 20, 0.2, 0.6, 0.2, 0.05);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.sword_stone.drawn", sword.getHoverName()).withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS;
		}
	}

	/** Out in the world: the stone on a worn footing of its own blocks, on firm ground of its own kind. */
	public record Placement(Variant variant) implements Feature {
		private static final Map<Variant, MapCodec<Placement>> CODECS = new EnumMap<>(Variant.class);

		@Override
		public MapCodec<Placement> codec() {
			return CODECS.computeIfAbsent(this.variant, v -> MapCodec.unit(new Placement(v)));
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
			BlockPos at = origin;
			if (this.variant.underground) {
				// down to the first floor: air over solid ground
				at = null;
				BlockPos.MutableBlockPos pos = origin.mutable();
				for (int i = 0; i < 32 && pos.getY() > level.getMinY() + 2; i++, pos.move(0, -1, 0)) {
					if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && this.variant.ground(level.getBlockState(pos.below()))) {
						at = pos.immutable();
						break;
					}
				}
				if (at == null) {
					return false;
				}
			}
			BlockPos ground = at.below();
			if (!level.getBlockState(at).isAir() || !level.getFluidState(ground).isEmpty() || !this.variant.ground(level.getBlockState(ground))) {
				return false;
			}
			// the footing: a worn 3x3 set into the ground
			List<Block> footing = this.variant.footing;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = ground.offset(dx, 0, dz);
					if (!level.getBlockState(pos).isSolid()) {
						continue;
					}
					float roll = random.nextFloat();
					Block block = roll < 0.4F ? footing.get(0) : roll < 0.7F ? footing.get(1) : footing.get(2);
					level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			level.setBlock(at, this.variant.stone.defaultBlockState(), Block.UPDATE_CLIENTS);
			return true;
		}
	}
}
