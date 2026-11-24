package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "the tree chop should work in the same way, you must cut through the trunk, so the tree is no longer connected,
 * then it leaves a carved stump" (Timber Tales' idea; our own code). What's left where a tree was cut through: the
 * lower part of the log it was, its cut face flat across the top and the bottom of the notch still in its side, drawn
 * with the log's own bark and end grain ({@code ChoppedLogRenderer}). Sit on it, dig it out with a shovel for a few
 * sticks, or leave it: it darkens and softens over a few seasons ({@link #ROT_STAGE_TICKS}) and rots into rich soil.
 */
public final class TreeStump extends BaseEntityBlock {
	/** How tall it stands (sixteenths of a block): where the cut was. */
	public static final int LOWEST = 4, HIGHEST = 14, DEFAULT_HEIGHT = 10;
	public static final IntegerProperty HEIGHT = IntegerProperty.create("height", LOWEST, HIGHEST);
	/** How far it has rotted (0 fresh; past the last it's soil). */
	public static final int ROTTEN = 3;
	public static final IntegerProperty ROT = IntegerProperty.create("rot", 0, ROTTEN);
	/** World time a rot stage takes: three of our seasons ({@code Festivals#SEASON_DAYS} days) over its four stages. */
	static final long ROT_STAGE_TICKS = 3L * Festivals.SEASON_DAYS * 24000L / (ROTTEN + 1);
	/** Sticks a shovel digs out of it: at least this many, and up to this many more; fewer once it's rotting. */
	static final int STICKS = 2, STICKS_MORE = 2;
	/** How much darker each stage of rot draws it. */
	static final float ROT_SHADE = 0.13F;

	public static TreeStump BLOCK;
	public static BlockEntityType<Entity> ENTITY;

	private static final VoxelShape[] SHAPES = new VoxelShape[HIGHEST + 1];

	public TreeStump(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HEIGHT, DEFAULT_HEIGHT).setValue(ROT, 0));
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("tree_stump"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new TreeStump(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(2.0F, 3.0F)
			.requiresCorrectToolForDrops()
			.sound(SoundType.WOOD)
			.ignitedByLava()
			.noOcclusion()
			.randomTicks()
			.noLootTable()
			.setId(key)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("tree_stump"),
			new BlockEntityType<>(Entity::new, java.util.Set.of(BLOCK)));
	}

	/**
	 * The trunk at {@code pos} (a log, or a chopped one) is cut through: what's left of it there is a stump, cut off flat at
	 * the middle of the notch (a clean cut halfway up if it was never carved). Nothing if there's no wood there.
	 */
	static void cutDown(ServerLevel level, BlockPos pos) {
		BlockState there = level.getBlockState(pos);
		BlockState log = ChoppedLog.real(level, pos, there);
		WoodVolume wood = level.getBlockEntity(pos) instanceof ChoppedLog.Entity e ? e.wood().copy() : new WoodVolume();
		place(level, pos, log, wood);
	}

	/** A stump of {@code log} at {@code pos}, from {@code wood} (what the axe left of the log there). */
	static void place(ServerLevel level, BlockPos pos, BlockState log, WoodVolume wood) {
		if (!log.is(BlockTags.LOGS)) {
			return;
		}
		int height = cutHeight(wood);
		BlockState stump = BLOCK.defaultBlockState().setValue(HEIGHT, height);
		level.setBlock(pos, stump, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof Entity e) {
			e.log = log;
			e.wood = wood.below(height);
			e.since = level.getGameTime();
			e.setChanged();
			level.sendBlockUpdated(pos, stump, stump, Block.UPDATE_ALL);
		}
	}

	/** Where the trunk was cut through (sixteenths): halfway down the cuts in its wood, the default if none. */
	static int cutHeight(WoodVolume wood) {
		int lowest = -1, highest = -1;
		for (int y = 0; y < WoodVolume.N; y++) {
			if (!wood.wholeRow(y)) {
				if (lowest < 0) {
					lowest = y;
				}
				highest = y;
			}
		}
		if (lowest < 0) {
			return DEFAULT_HEIGHT;
		}
		return Math.max(LOWEST, Math.min(HIGHEST, (lowest + highest + 1) / 2 + 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEIGHT, ROT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// (an empty model, for its break particles; the stump itself is drawn by its renderer)
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int h = state.getValue(HEIGHT);
		VoxelShape shape = SHAPES[h];
		if (shape == null) {
			shape = SHAPES[h] = Block.box(0.0, 0.0, 0.0, 16.0, h, 16.0);
		}
		return shape;
	}

	/** Sat on, as a stool is. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isPassenger() || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		Vec3 seat = new Vec3(pos.getX() + 0.5, pos.getY() + state.getValue(HEIGHT) / 16.0, pos.getZ() + 0.5);
		return com.jaymoskingdoms.carpentry.BenchSeats.seat(server, seat, player.getYRot(), player) ? InteractionResult.SUCCESS
			: InteractionResult.PASS;
	}

	/** Gone (dug out, burned, rotted away): whoever sat on it stands. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		com.jaymoskingdoms.carpentry.BenchSeats.clearAt(level, new Vec3(pos.getX() + 0.5, pos.getY() + state.getValue(HEIGHT) / 16.0, pos.getZ() + 0.5));
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** Dug out with a shovel, a few sticks of its wood (fewer as it rots); anything else gets nothing out of it. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		net.minecraft.world.item.ItemInstance tool = params.getOptionalParameter(LootContextParams.TOOL);
		if (tool == null || !tool.is(ItemTags.SHOVELS)) {
			return List.of();
		}
		RandomSource random = params.getLevel().getRandom();
		int sticks = Math.max(0, STICKS + random.nextInt(STICKS_MORE + 1) - state.getValue(ROT));
		List<ItemStack> out = new ArrayList<>();
		if (sticks > 0) {
			out.add(new ItemStack(Items.STICK, sticks));
		}
		return out;
	}

	/** It rots by the world's clock, so it goes on rotting while nobody is near (caught up when they come back). */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!(level.getBlockEntity(pos) instanceof Entity e)) {
			return;
		}
		long age = level.getGameTime() - e.since;
		int rot = (int)Math.min(ROTTEN + 1, Math.max(0L, age / ROT_STAGE_TICKS));
		if (rot > ROTTEN) {
			rotAway(level, pos);
		} else if (rot != state.getValue(ROT)) {
			level.setBlock(pos, state.setValue(ROT, rot), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.4F, 0.7F);
		}
	}

	/** Rotted all the way: the ground it stood in is rich soil now (it is, if there's no earth under it). */
	static void rotAway(ServerLevel level, BlockPos pos) {
		BlockState soil = richSoil();
		BlockPos below = pos.below();
		if (level.getBlockState(below).is(BlockTags.DIRT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(below, soil, Block.UPDATE_ALL);
		} else {
			level.setBlock(pos, soil, Block.UPDATE_ALL);
		}
		level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
	}

	/** Kitchen's rich soil, or rooted earth without Kitchen. */
	static BlockState richSoil() {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "rich_soil"))
			.map(Block::defaultBlockState).orElse(Blocks.ROOTED_DIRT.defaultBlockState());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Entity(pos, state);
	}

	/** Which log it was, the wood the axe left of it, and when it was cut. */
	public static final class Entity extends BlockEntity implements ChoppedLog.Carved {
		private BlockState log = Blocks.OAK_LOG.defaultBlockState();
		private WoodVolume wood = new WoodVolume().below(DEFAULT_HEIGHT);
		/** The world time it was cut. */
		private long since;

		public Entity(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		@Override
		public BlockState log() {
			return log;
		}

		@Override
		public WoodVolume wood() {
			return wood;
		}

		@Override
		public float shade() {
			return 1.0F - ROT_SHADE * getBlockState().getValue(ROT);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			log = input.read("log", BlockState.CODEC).orElse(Blocks.OAK_LOG.defaultBlockState());
			wood = input.read("wood", WOOD).map(cells -> new WoodVolume(cells.stream().mapToLong(Long::longValue).toArray()))
				.orElseGet(() -> new WoodVolume().below(DEFAULT_HEIGHT));
			since = input.read("since", Codec.LONG).orElse(0L);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			store(output);
			output.store("since", Codec.LONG, since);
		}

		private void store(ValueOutput output) {
			output.store("log", BlockState.CODEC, log);
			output.store("wood", WOOD, java.util.Arrays.stream(wood.data()).boxed().toList());
		}

		private static final Codec<List<Long>> WOOD = Codec.LONG.listOf();

		@Override
		public ClientboundBlockEntityDataPacket getUpdatePacket() {
			return ClientboundBlockEntityDataPacket.create(this);
		}

		@Override
		public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			store(output);
			return output.buildResult();
		}
	}
}
