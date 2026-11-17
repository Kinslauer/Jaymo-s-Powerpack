package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.25) A log a few axe strokes into a tree (our own code): it stands where the log stood, drawn as the log it was
 * ({@code ChoppedLogRenderer}), and gives that log's wood when it comes down with the tree or is broken. (0.7.510) Andy,
 * after Timber Tales: it no longer thins; it keeps the whole log's size and its cracks run deeper with every chop
 * ({@link #CHOPS}), while the chopper sees how far the tree is from coming down ({@link Trees#chop}). It's one of the game's logs (the {@code logs} tag), so leaves
 * keep to it and the tree is still a tree. See {@link Trees#chop}. Andy (2026-11-16): the trunk carves out where the
 * axe lands, the wood's own grain showing inside the cut ({@link WoodVolume}, from Immersive Tree Chop, MIT).
 */
public final class ChoppedLog extends BaseEntityBlock {
	/** The most chops one log shows; more go into the log above ({@link Trees#chop}). */
	public static final int MOST = 6;
	public static final IntegerProperty CHOPS = IntegerProperty.create("chops", 1, MOST);
	public static final EnumProperty<Direction.Axis> AXIS = RotatedPillarBlock.AXIS;

	public static ChoppedLog BLOCK;
	public static BlockEntityType<Entity> ENTITY;

	public ChoppedLog(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CHOPS, 1).setValue(AXIS, Direction.Axis.Y));
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("chopped_log"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new ChoppedLog(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(2.0F)
			.sound(SoundType.WOOD)
			.ignitedByLava()
			.noOcclusion()
			.noLootTable()
			.setId(key)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("chopped_log"),
			new BlockEntityType<>(Entity::new, java.util.Set.of(BLOCK)));
	}

	/** {@code log} with {@code chops} cut into it, standing at {@code pos} (the log it was is kept). */
	static void set(Level level, BlockPos pos, BlockState log, int chops) {
		BlockState original = log.is(BLOCK) && level.getBlockEntity(pos) instanceof Entity e ? e.log : log;
		Direction.Axis axis = original.hasProperty(AXIS) ? original.getValue(AXIS) : Direction.Axis.Y;
		BlockState chopped = BLOCK.defaultBlockState().setValue(CHOPS, Math.max(1, Math.min(MOST, chops))).setValue(AXIS, axis);
		level.setBlock(pos, chopped, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof Entity e) {
			e.log = original;
			e.setChanged();
			level.sendBlockUpdated(pos, chopped, chopped, Block.UPDATE_ALL);
		}
	}

	/** How hard an ordinary axe stroke bites ({@link WoodVolume#carve}; 0 is Immersive Tree Chop's lightest). */
	static final float CARVE_POWER = 0.0F;

	/**
	 * An axe stroke lands on the chopped log at {@code pos}: a bite is cut out of its wood where it struck ({@code point},
	 * inside the block, 0 to 1 each way) from the side it came in from, and everyone near sees it.
	 */
	static void carve(Level level, BlockPos pos, Vec3 point, Direction face) {
		if (level.getBlockEntity(pos) instanceof Entity e) {
			e.wood.carve(point, face, CARVE_POWER, e.strokes++);
			e.setChanged();
			BlockState state = level.getBlockState(pos);
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		}
	}

	/** The chops in the log at {@code pos} (0 for a whole one). */
	static int chops(BlockState state) {
		return state.is(BLOCK) ? state.getValue(CHOPS) : 0;
	}

	/** What really stands at {@code pos}: the log a chopped one was, else the block itself. */
	public static BlockState real(BlockGetter level, BlockPos pos, BlockState state) {
		return state.is(BLOCK) && level.getBlockEntity(pos) instanceof Entity e ? e.log : state;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CHOPS, AXIS);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// (an empty model, for its break particles; the log itself is drawn by its renderer)
		return RenderShape.MODEL;
	}

	/** Broken (on its own, or as a tree comes down): the wood of the log it was. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof Entity e) {
			return e.log.getDrops(params);
		}
		return List.of();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Entity(pos, state);
	}

	/** Which log it was, and the wood the axe has left of it. */
	public static final class Entity extends BlockEntity {
		private BlockState log = Blocks.OAK_LOG.defaultBlockState();
		private WoodVolume wood = new WoodVolume();
		/** How many strokes have bitten into it (each slants the other way from the last). */
		private int strokes;

		public Entity(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		public BlockState log() {
			return log;
		}

		/** The wood left of it ({@link WoodVolume}). */
		public WoodVolume wood() {
			return wood;
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			log = input.read("log", BlockState.CODEC).orElse(Blocks.OAK_LOG.defaultBlockState());
			// (a log chopped before it could be carved has no wood saved: it stands whole)
			wood = input.read("wood", WOOD).map(cells -> new WoodVolume(cells.stream().mapToLong(Long::longValue).toArray()))
				.orElseGet(WoodVolume::new);
			strokes = input.read("strokes", Codec.INT).orElse(0);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			store(output);
		}

		private void store(ValueOutput output) {
			output.store("log", BlockState.CODEC, log);
			if (!wood.whole()) {
				output.store("wood", WOOD, java.util.Arrays.stream(wood.data()).boxed().toList());
				output.store("strokes", Codec.INT, strokes);
			}
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
