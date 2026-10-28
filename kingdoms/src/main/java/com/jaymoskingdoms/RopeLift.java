package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (batch 4) The rope lift, after Better With Mods' pulley, anchor, rope and platform (MIT; its idea, our own code).
 *
 * <p>Rope hangs straight down from an anchor (set under a block or on its side, as at a cliff's edge), a pulley or
 * other rope, and is climbed like a ladder (jump to go up, sneak to hold on). Rope in hand on a hanging rope or an anchor
 * lets more down. The pulley keeps a stock of rope (rope in hand on it adds to it) and winds its rope in or out a block
 * at a time: by hand (right click: up; sneaking: down; each turn costs a little hunger), or, with Jaymo's Kitchen, by a
 * turning shaft against it (up; down while it has a redstone signal). A layer of platform blocks (up to
 * {@link #MOST_PLATFORM}) tied under the rope's end goes up and down with it, carrying whoever stands on it.
 *
 * <p>The platform moves as blocks, a step at a time, its riders moved with it the same tick: the world keeps it as it
 * stands (nothing in flight to lose on a chunk unload or a restart), mobs path over it as over any floor, and nobody is
 * left standing in the air. A rope cut (or its pulley broken) drops what hangs from it: the platform falls as falling
 * blocks, and whoever rode it takes the fall as they would anywhere.
 */
public final class RopeLift {
	/** A pulley turned by a shaft moves a block this often (ticks); by hand, this soon after the last turn. */
	static final int SHAFT_STEP_TICKS = 10, CRANK_STEP_TICKS = 4;
	/** The hunger a turn of the pulley by hand costs (exhaustion; 4 is a food point). */
	static final float CRANK_EXHAUSTION = 0.3F;
	/** The most rope a pulley holds, and the most platform blocks one rope lifts. */
	static final int MOST_ROPE = 256, MOST_PLATFORM = 25;
	/** How often (ticks) a pulley looks for a turning shaft beside it. */
	static final int SHAFT_CHECK_TICKS = 20;

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public static Block ROPE, ANCHOR, PULLEY, PLATFORM;
	public static Item ROPE_ITEM, ANCHOR_ITEM, PULLEY_ITEM, PLATFORM_ITEM;
	public static BlockEntityType<Pulley> PULLEY_ENTITY;

	/** Set while a pulley takes in its own rope's end (nothing hanging from it falls). */
	private static boolean winding;

	private RopeLift() {
	}

	static void register() {
		ROPE = block("rope", RopeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.4F).sound(SoundType.WOOL)
			.noCollision().noOcclusion().pushReaction(PushReaction.POPPED).ignitedByLava());
		ANCHOR = block("anchor", AnchorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F)
			.sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.POPPED));
		PULLEY = block("pulley", PulleyBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
		PLATFORM = block("platform", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
		PULLEY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("pulley"),
			new BlockEntityType<>(Pulley::new, java.util.Set.of(PULLEY)));
		ROPE_ITEM = item("rope", ROPE);
		ANCHOR_ITEM = item("anchor", ANCHOR);
		PULLEY_ITEM = item("pulley", PULLEY);
		PLATFORM_ITEM = item("platform", PLATFORM);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.insertAfter(Items.LADDER, ROPE_ITEM);
			output.insertAfter(ROPE_ITEM, ANCHOR_ITEM);
			output.insertAfter(ANCHOR_ITEM, PULLEY_ITEM);
			output.insertAfter(PULLEY_ITEM, PLATFORM_ITEM);
		});
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item item(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** Rope (or a wind chime) may hang under this: rope, an anchor or a pulley. */
	public static boolean hangsFrom(BlockState above) {
		return ROPE != null && (above.is(ROPE) || above.is(ANCHOR) || above.is(PULLEY));
	}

	/** The lowest rope hanging from {@code top} (an anchor, a pulley or a rope), or {@code top} itself if none hangs. */
	static BlockPos bottom(Level level, BlockPos top) {
		BlockPos.MutableBlockPos at = top.mutable();
		while (level.getBlockState(at.below()).is(ROPE) && at.getY() > level.getMinY()) {
			at.move(Direction.DOWN);
		}
		return at.immutable();
	}

	/** Room for rope or a platform here: air, or something that gives way (grass, snow), without water. */
	static boolean room(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.isAir() || state.canBeReplaced()) && level.getFluidState(pos).isEmpty() && !level.isOutsideBuildHeight(pos);
	}

	/** Rope in hand on hanging rope or an anchor: one more length let down below the end. */
	static InteractionResult letDown(ItemStack stack, Level level, BlockPos top, Player player) {
		BlockPos end = bottom(level, top).below();
		if (!room(level, end)) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			level.setBlock(end, ROPE.defaultBlockState(), Block.UPDATE_ALL);
			level.playSound(null, end, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	// ---- the blocks ----------------------------------------------------------------------------------------------------

	/** A length of rope: hangs from what's above it, climbed like a ladder (the {@code climbable} tag). */
	static final class RopeBlock extends Block {
		private static final VoxelShape SHAPE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);

		RopeBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			return hangsFrom(level.getBlockState(pos.above()));
		}

		/** Cut above: it falls away (with whatever hangs below), a length at a time, each dropping its rope. */
		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			if (direction == Direction.UP && !canSurvive(state, level, pos)) {
				ticks.scheduleTick(pos, this, 1);
			}
			return state;
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (!canSurvive(state, level, pos)) {
				level.destroyBlock(pos, true);
			}
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			return stack.is(ROPE_ITEM) ? letDown(stack, level, pos, player) : InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		/** The rope's end gone (cut, not wound in): the platform tied under it falls. */
		@Override
		protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
			super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
			if (!winding && level.getBlockState(pos.below()).is(PLATFORM)) {
				drop(level, platform(level, pos.below()));
			}
		}
	}

	/** An iron anchor fixed under a block or on its side; rope hangs from it. */
	static final class AnchorBlock extends Block {
		private static final VoxelShape DOWN = Shapes.or(Block.box(4, 13, 4, 12, 16, 12), Block.box(6.5, 8, 6.5, 9.5, 13, 9.5));
		private static final VoxelShape NORTH = Shapes.or(Block.box(4, 4, 0, 12, 12, 3), Block.box(6.5, 4, 3, 9.5, 7, 9.5));
		private static final VoxelShape SOUTH = Shapes.or(Block.box(4, 4, 13, 12, 12, 16), Block.box(6.5, 4, 6.5, 9.5, 7, 13));
		private static final VoxelShape WEST = Shapes.or(Block.box(0, 4, 4, 3, 12, 12), Block.box(3, 4, 6.5, 9.5, 7, 9.5));
		private static final VoxelShape EAST = Shapes.or(Block.box(13, 4, 4, 16, 12, 12), Block.box(6.5, 4, 6.5, 13, 7, 9.5));

		AnchorBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		/** FACING is the side it's fixed to: up for under a block, a wall's side for on it. */
		@Override
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			Direction clicked = context.getClickedFace();
			Direction to = clicked == Direction.UP ? null : clicked.getOpposite();
			for (Direction d : to == null ? context.getNearestLookingDirections() : new Direction[] {to}) {
				if (d == Direction.DOWN) {
					continue;
				}
				BlockState state = defaultBlockState().setValue(FACING, d);
				if (canSurvive(state, context.getLevel(), context.getClickedPos())) {
					return state;
				}
			}
			return null;
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			Direction to = state.getValue(FACING);
			BlockPos on = pos.relative(to);
			return to == Direction.UP ? Block.canSupportCenter(level, on, Direction.DOWN) : level.getBlockState(on).isFaceSturdy(level, on, to.getOpposite());
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			return direction == state.getValue(FACING) && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(FACING)) {
				case NORTH -> NORTH;
				case SOUTH -> SOUTH;
				case WEST -> WEST;
				case EAST -> EAST;
				default -> DOWN;
			};
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			return stack.is(ROPE_ITEM) ? letDown(stack, level, pos, player) : InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		@Override
		protected BlockState rotate(BlockState state, Rotation rotation) {
			return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		}

		@Override
		protected BlockState mirror(BlockState state, Mirror mirror) {
			return state.rotate(mirror.getRotation(state.getValue(FACING)));
		}
	}

	/** The pulley: winds its rope in and out, by hand or by a turning shaft. */
	static final class PulleyBlock extends BaseEntityBlock {
		PulleyBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new Pulley(pos, state);
		}

		@Override
		public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level.isClientSide() ? null : createTickerHelper(type, PULLEY_ENTITY, (l, p, s, pulley) -> pulley.serverTick((ServerLevel)l));
		}

		/** Rope in hand: into its stock. */
		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (!stack.is(ROPE_ITEM)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level.getBlockEntity(pos) instanceof Pulley pulley && player instanceof ServerPlayer p) {
				int take = Math.min(stack.getCount(), MOST_ROPE - pulley.rope);
				if (take > 0) {
					pulley.rope += take;
					pulley.setChanged();
					stack.consume(take, p);
					level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
				}
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pulley.stock", pulley.rope));
			}
			return InteractionResult.SUCCESS;
		}

		/** Turned by hand: winds up (sneaking: lets down) a block, for a little hunger. */
		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof Pulley pulley
				&& pulley.cooldown == 0) {
				String problem = pulley.step(server, !p.isShiftKeyDown());
				if (problem == null) {
					pulley.cooldown = CRANK_STEP_TICKS;
					p.causeFoodExhaustion(CRANK_EXHAUSTION);
					p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pulley.stock", pulley.rope));
				} else {
					p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pulley." + problem));
				}
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** A pulley's rope stock and its work. */
	public static final class Pulley extends BlockEntity {
		/** Rope wound on it (not counting what hangs). */
		int rope;
		/** Ticks till it may move again; whether a shaft turned it at the last look. */
		int cooldown;
		private boolean shaft;

		public Pulley(BlockPos pos, BlockState state) {
			super(PULLEY_ENTITY, pos, state);
		}

		void serverTick(ServerLevel level) {
			if (cooldown > 0) {
				cooldown--;
			}
			if ((level.getGameTime() + worldPosition.asLong()) % SHAFT_CHECK_TICKS == 0) {
				shaft = KitchenShafts.turning(level, worldPosition);
			}
			if (shaft && cooldown == 0) {
				step(level, !level.hasNeighborSignal(worldPosition));
				cooldown = SHAFT_STEP_TICKS;
			}
		}

		/** Winds the rope up (or lets it down) a block, with the platform tied to its end. Null if it moved, else why not. */
		@Nullable String step(ServerLevel level, boolean up) {
			BlockPos end = bottom(level, worldPosition);
			if (up) {
				if (end.equals(worldPosition)) {
					return "wound";
				}
				if (rope >= MOST_ROPE) {
					return "full";
				}
				List<BlockPos> platform = level.getBlockState(end.below()).is(PLATFORM) ? platform(level, end.below()) : List.of();
				if (!platform.isEmpty() && !movable(level, platform, 1, end)) {
					return "blocked";
				}
				winding = true;
				try {
					level.setBlock(end, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				} finally {
					winding = false;
				}
				move(level, platform, 1);
				rope++;
			} else {
				if (rope <= 0) {
					return "no_rope";
				}
				BlockPos next = end.below();
				if (level.getBlockState(next).is(PLATFORM)) {
					List<BlockPos> platform = platform(level, next);
					if (!movable(level, platform, -1, null)) {
						return "blocked";
					}
					move(level, platform, -1);
				} else if (!room(level, next)) {
					return "blocked";
				}
				level.setBlock(next, ROPE.defaultBlockState(), Block.UPDATE_ALL);
				rope--;
			}
			setChanged();
			level.playSound(null, worldPosition, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.5F, up ? 1.1F : 0.8F);
			return null;
		}

		/** Taken down: its stock drops (what hangs from it falls on its own). */
		@Override
		public void preRemoveSideEffects(BlockPos pos, BlockState state) {
			if (level != null && !level.isClientSide()) {
				for (int left = rope; left > 0; left -= 64) {
					Block.popResource(level, pos, new ItemStack(ROPE_ITEM, Math.min(64, left)));
				}
				rope = 0;
			}
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			rope = input.getIntOr("rope", 0);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			output.putInt("rope", rope);
		}
	}

	// ---- the platform -------------------------------------------------------------------------------------------------

	/** The platform blocks joined side by side to the one at {@code start} (one layer, {@link #MOST_PLATFORM} at most). */
	static List<BlockPos> platform(Level level, BlockPos start) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(start);
		seen.add(start);
		while (!open.isEmpty() && found.size() < MOST_PLATFORM) {
			BlockPos p = open.poll();
			found.add(p);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = p.relative(d);
				if (seen.add(q) && level.getBlockState(q).is(PLATFORM)) {
					open.add(q);
				}
			}
		}
		return found;
	}

	/** Whether every block of the platform can move {@code dy}: room there ({@code freed} is about to be). */
	private static boolean movable(Level level, List<BlockPos> platform, int dy, @Nullable BlockPos freed) {
		for (BlockPos p : platform) {
			BlockPos to = p.above(dy);
			if (!to.equals(freed) && !room(level, to)) {
				return false;
			}
		}
		return true;
	}

	/** Moves the platform {@code dy} (one block), and everyone standing on it with it. */
	private static void move(ServerLevel level, List<BlockPos> platform, int dy) {
		if (platform.isEmpty()) {
			return;
		}
		List<Entity> riders = riders(level, platform);
		BlockState state = PLATFORM.defaultBlockState();
		for (BlockPos p : platform) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (BlockPos p : platform) {
			level.setBlock(p.above(dy), state, Block.UPDATE_ALL);
		}
		for (BlockPos p : platform) {
			level.updateNeighborsAt(p, Blocks.AIR);
		}
		for (Entity e : riders) {
			// (0.7.281) carried up or down with the platform, held or not
			Attention.carry(() -> e.teleportTo(e.getX(), e.getY() + dy, e.getZ()));
		}
	}

	/** Who stands on the platform (on its top face, not riding something else). */
	private static List<Entity> riders(ServerLevel level, List<BlockPos> platform) {
		List<Entity> riders = new ArrayList<>();
		for (BlockPos p : platform) {
			AABB top = new AABB(p.getX(), p.getY() + 1, p.getZ(), p.getX() + 1, p.getY() + 1.5, p.getZ() + 1);
			for (Entity e : level.getEntities((Entity)null, top, e -> !e.isSpectator() && !e.isPassenger())) {
				if (!riders.contains(e) && e.getY() >= p.getY() + 0.9) {
					riders.add(e);
				}
			}
		}
		return riders;
	}

	/** The rope above it cut: the platform falls (each block with nothing under it, as a falling block). */
	private static void drop(ServerLevel level, List<BlockPos> platform) {
		for (BlockPos p : platform) {
			if (FallingBlock.isFree(level.getBlockState(p.below()))) {
				FallingBlockEntity.fall(level, p, level.getBlockState(p));
			}
		}
	}
}
