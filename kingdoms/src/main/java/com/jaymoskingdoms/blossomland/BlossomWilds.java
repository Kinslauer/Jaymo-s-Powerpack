package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.seas.Mohknee;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What grows on Mohknee beside the cherry: the maple and the ginkgo, green until autumn and then red and gold
 * ({@link TurningLeaves}); the red spider lily, which the blossom folk plant on their graves and which is poison;
 * wisteria, hanging from whatever holds it; and bamboo in thickets. The trees, lilies and bamboo are scattered over the far
 * land's groves, forests and meadows by worldgen (placed features kept to Mohknee by {@link OnMohknee}), and a new
 * village has a few maples and ginkgos planted round it ({@link #plantRound}). The blocks' models, loot and names come
 * from {@code kingdoms/tools/gen_blossom_wilds.py}.
 */
public final class BlossomWilds {
	public static Block MAPLE_LEAVES, GINKGO_LEAVES, MAPLE_SAPLING, GINKGO_SAPLING, SPIDER_LILY, WISTERIA;

	public static final ResourceKey<Feature> MAPLE = ResourceKey.create(Registries.FEATURE, JaymosKingdoms.id("maple"));
	public static final ResourceKey<Feature> GINKGO = ResourceKey.create(Registries.FEATURE, JaymosKingdoms.id("ginkgo"));
	/** The far land's scattered growth, in the order it is laid down. */
	static final String[] SCATTERED = {"mohknee_maples", "mohknee_ginkgos", "mohknee_bamboo", "mohknee_spider_lilies"};

	/** Trees planted round a new village: how many, how far from its bell (at least, plus up to), and the tries it gets. */
	static final int PLANTED = 5, PLANT_MIN = 40, PLANT_SPREAD = 24, PLANT_TRIES = 24;
	/** A spider lily's poison in a stew (seconds). */
	static final float LILY_POISON = 8.0F;

	private BlossomWilds() {
	}

	static void register() {
		MAPLE_LEAVES = BlossomBlocks.block("maple_leaves", new TurningLeaves(leaves("maple_leaves", MapColor.COLOR_RED)), 30, 60);
		GINKGO_LEAVES = BlossomBlocks.block("ginkgo_leaves", new TurningLeaves(leaves("ginkgo_leaves", MapColor.COLOR_YELLOW)), 30, 60);
		MAPLE_SAPLING = BlossomBlocks.block("maple_sapling", new SaplingBlock(grower("maple", MAPLE), BlossomBlocks.copy("maple_sapling", Blocks.BIRCH_SAPLING)), 0, 0);
		GINKGO_SAPLING = BlossomBlocks.block("ginkgo_sapling", new SaplingBlock(grower("ginkgo", GINKGO), BlossomBlocks.copy("ginkgo_sapling", Blocks.BIRCH_SAPLING)), 0, 0);
		SPIDER_LILY = BlossomBlocks.block("red_spider_lily", new FlowerBlock(MobEffects.POISON, LILY_POISON, BlossomBlocks.copy("red_spider_lily", Blocks.POPPY)), 60, 100);
		WISTERIA = BlossomBlocks.block("wisteria", new Wisteria(BlossomBlocks.props("wisteria").mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak()
			.sound(SoundType.CAVE_VINES).pushReaction(PushReaction.POPPED).noOcclusion()), 15, 60);

		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, JaymosKingdoms.id("on_mohknee"), OnMohknee.CODEC);
		for (String name : SCATTERED) {
			BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.CHERRY_GROVE, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
					Biomes.MEADOW, Biomes.PLAINS), GenerationStep.Decoration.VEGETAL_DECORATION,
				ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id(name)));
		}
	}

	private static net.minecraft.world.level.block.state.BlockBehaviour.Properties leaves(String name, MapColor color) {
		return BlossomBlocks.copy(name, Blocks.BIRCH_LEAVES).mapColor(color);
	}

	private static TreeGrower grower(String name, ResourceKey<Feature> tree) {
		return new TreeGrower("jaymoskingdoms_" + name, WeightedList.of(tree), WeightedList.of(), WeightedList.of(), tree);
	}

	/** A few maples and ginkgos out round a new village, on open ground. */
	public static void plantRound(ServerLevel level, BlockPos bell, RandomSource random) {
		var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
		int planted = 0;
		for (int tries = 0; tries < PLANT_TRIES && planted < PLANTED; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = PLANT_MIN + random.nextInt(PLANT_SPREAD);
			int x = bell.getX() + (int)Math.round(Math.cos(angle) * r), z = bell.getZ() + (int)Math.round(Math.sin(angle) * r);
			if (!level.isLoaded(new BlockPos(x, bell.getY(), z))) {
				continue;
			}
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (!level.getBlockState(at.below()).is(net.minecraft.tags.BlockTags.DIRT) || !level.getBlockState(at).canBeReplaced()
				|| !level.getFluidState(at).isEmpty()) {
				continue;
			}
			var tree = features.get(random.nextBoolean() ? MAPLE : GINKGO);
			if (tree.isPresent() && tree.get().value().place(level, level.getChunkSource().getGenerator(), random, at)) {
				planted++;
			}
		}
	}

	/** Only on the far land. */
	public static final class OnMohknee implements PlacementFilter {
		static final OnMohknee INSTANCE = new OnMohknee();
		static final MapCodec<OnMohknee> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
			return Mohknee.on(pos.getX(), pos.getZ());
		}

		@Override
		public MapCodec<? extends PlacementFilter> codec() {
			return CODEC;
		}
	}

	/**
	 * Leaves that turn: green through spring and summer, the tree's own colour from autumn until the next spring (and
	 * always, in a world with no seasons). They change a few at a time as the game's random ticks reach them, the way a
	 * tree turns.
	 */
	public static final class TurningLeaves extends LeavesBlock {
		public static final BooleanProperty TURNED = BooleanProperty.create("turned");

		TurningLeaves(Properties properties) {
			super(AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
			this.registerDefaultState(this.defaultBlockState().setValue(TURNED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(TURNED);
		}

		@Override
		protected boolean isRandomlyTicking(BlockState state) {
			return true;
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (this.decaying(state)) {
				super.randomTick(state, level, pos, random);
				return;
			}
			if (Mohknee.on(pos.getX(), pos.getZ())) {
				HornetNest.tryStart(level, pos, random);
			}
			String season = KitchenCompat.season(level);
			boolean turned = season == null || season.equals("autumn") || season.equals("winter");
			if (state.getValue(TURNED) != turned) {
				level.setBlock(pos, state.setValue(TURNED, turned), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Wisteria: a hanging raceme that holds to leaves, to a firm underside or to the wisteria above it. */
	public static final class Wisteria extends Block {
		private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

		Wisteria(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			BlockPos above = pos.above();
			BlockState held = level.getBlockState(above);
			return held.is(this) || held.is(net.minecraft.tags.BlockTags.LEAVES) || held.isFaceSturdy(level, above, Direction.DOWN, net.minecraft.world.level.block.SupportType.CENTER);
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
				BlockState neighbour, RandomSource random) {
			return direction == Direction.UP && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		}

		@Override
		protected boolean propagatesSkylightDown(BlockState state) {
			return true;
		}
	}
}
