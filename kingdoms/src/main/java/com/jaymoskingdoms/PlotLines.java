package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plot stakes set in the ground as posts (after Better With Mods' way of laying out a site, our own code). String one
 * stake to the next (string in hand: the first stake, then the second; straight along x or z, {@link #STAKE_REACH} at
 * most) and a line of string runs between them; four stakes strung into a closed rectangle mark a plot of exactly that
 * size through {@link Plots#markBox} (its rules, rights and messages). A lone stake right-clicked with an empty hand marks
 * the square round it as the old stake item did (sneaking: the chunk). A broken stake drops itself and the string of
 * each line it held, and its lines come down.
 */
public final class PlotLines {
	/** The farthest one stake may be strung to the next: a plot's longest side, corner post to corner post. */
	static final int STAKE_REACH = Plots.MAX_SIDE - 1;
	/** The most two strung stakes' heights may differ by. */
	static final int STAKE_RISE = 4;
	/** The most lines one stake holds (a corner holds two; a fence of plots more). */
	static final int MOST_LINES = 4;

	public static Block STAKE;
	public static BlockEntityType<Stake> ENTITY;

	/** The stake each player last tied a line to, waiting for the next. */
	private static final Map<UUID, BlockPos> TYING = new HashMap<>();

	private PlotLines() {
	}

	/** The stake block and its entity (the item is {@link Plots#PLOT_STAKE}, registered after). */
	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("plot_stake"));
		STAKE = Registry.register(BuiltInRegistries.BLOCK, key, new StakeBlock(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.POPPED)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("plot_stake"),
			new BlockEntityType<>(Stake::new, java.util.Set.of(STAKE)));
	}

	/** A plot stake in the ground: a thin post with a knot at the top. */
	static final class StakeBlock extends BaseEntityBlock {
		private static final VoxelShape SHAPE = Block.box(6.5, 0, 6.5, 9.5, 12, 9.5);

		StakeBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.MODEL;
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			return Block.canSupportCenter(level, pos.below(), Direction.UP);
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		}

		/** String in hand: ties a line from this stake (the first one clicked), or to it (the second). */
		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (!stack.is(Items.STRING)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
				tie(server, p, pos, stack);
			}
			return InteractionResult.SUCCESS;
		}

		/** Empty hand on a stake with no lines: marks the square round it, as the old stake item did (sneaking: the chunk). */
		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!(level.getBlockEntity(pos) instanceof Stake stake) || !stake.lines.isEmpty()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer p) {
				Plots.mark(p, pos.below(), Plots.STAKE_SIDE, Plots.STAKE_SIDE, p.isShiftKeyDown());
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new Stake(pos, state);
		}
	}

	/** Ties {@code player}'s line at the stake at {@code pos}: its first end, or its second (and then looks for a closed plot). */
	static void tie(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack string) {
		BlockPos from = TYING.remove(player.getUUID());
		if (from == null || from.equals(pos) || !(level.getBlockEntity(from) instanceof Stake a)) {
			TYING.put(player.getUUID(), pos.immutable());
			level.playSound(null, pos, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.6F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stake.tied", STAKE_REACH));
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof Stake b)) {
			return;
		}
		int dx = pos.getX() - from.getX(), dz = pos.getZ() - from.getZ();
		String problem = null;
		if ((dx != 0) == (dz != 0)) {
			problem = "not_straight";
		} else if (Math.abs(dx) + Math.abs(dz) > STAKE_REACH) {
			problem = "too_far";
		} else if (Math.abs(pos.getY() - from.getY()) > STAKE_RISE) {
			problem = "too_steep";
		} else if (a.lines.contains(pos)) {
			problem = "already";
		} else if (a.lines.size() >= MOST_LINES || b.lines.size() >= MOST_LINES) {
			problem = "full";
		}
		if (problem != null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stake." + problem, STAKE_REACH).withStyle(ChatFormatting.RED));
			return;
		}
		a.link(pos);
		b.link(from);
		string.consume(1, player);
		level.playSound(null, pos, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stake.strung", Math.abs(dx) + Math.abs(dz) + 1));
		closed(level, player, from, pos);
	}

	/**
	 * The line {@code a}-{@code b} just strung: if it closes a rectangle of four strung stakes, marks it as a plot (floored at
	 * the lowest stake's ground).
	 */
	private static void closed(ServerLevel level, ServerPlayer player, BlockPos a, BlockPos b) {
		Stake sa = level.getBlockEntity(a) instanceof Stake s ? s : null, sb = level.getBlockEntity(b) instanceof Stake s ? s : null;
		if (sa == null || sb == null) {
			return;
		}
		for (BlockPos c : sb.lines) {
			if (c.equals(a) || !across(a, b, c)) {
				continue;
			}
			for (BlockPos d : sa.lines) {
				if (d.equals(b) || d.getX() - a.getX() != c.getX() - b.getX() || d.getZ() - a.getZ() != c.getZ() - b.getZ()) {
					continue;
				}
				if (level.getBlockEntity(c) instanceof Stake sc && sc.lines.contains(d)) {
					int y = Math.min(Math.min(a.getY(), b.getY()), Math.min(c.getY(), d.getY())) - 1;
					Plots.markBox(player, new BlockPos(a.getX(), y, a.getZ()), new BlockPos(c.getX(), y, c.getZ()));
					return;
				}
			}
		}
	}

	/** Whether the line {@code b}-{@code c} turns a right angle from {@code a}-{@code b}. */
	private static boolean across(BlockPos a, BlockPos b, BlockPos c) {
		boolean abAlongX = a.getZ() == b.getZ();
		return abAlongX ? c.getX() == b.getX() && c.getZ() != b.getZ() : c.getZ() == b.getZ() && c.getX() != b.getX();
	}

	/** A stake's lines: the stakes it's strung to (each line kept at both ends, drawn from the lower one). */
	public static final class Stake extends BlockEntity {
		private final List<BlockPos> lines = new ArrayList<>();

		public Stake(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		/** The stakes this one is strung to. */
		public List<BlockPos> lines() {
			return lines;
		}

		void link(BlockPos other) {
			lines.add(other.immutable());
			changed();
		}

		void unlink(BlockPos other) {
			if (lines.remove(other)) {
				changed();
			}
		}

		private void changed() {
			setChanged();
			if (level != null) {
				level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		/** Pulled up: its lines come down at the other ends too, and their string drops here. */
		@Override
		public void preRemoveSideEffects(BlockPos pos, BlockState state) {
			if (level == null || level.isClientSide() || lines.isEmpty()) {
				return;
			}
			for (BlockPos other : lines) {
				if (level.getBlockEntity(other) instanceof Stake s) {
					s.unlink(pos);
				}
			}
			Block.popResource(level, pos, new ItemStack(Items.STRING, lines.size()));
			lines.clear();
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			lines.clear();
			lines.addAll(input.read("lines", BlockPos.CODEC.listOf()).orElse(List.of()));
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			output.store("lines", BlockPos.CODEC.listOf(), List.copyOf(lines));
		}

		@Override
		public ClientboundBlockEntityDataPacket getUpdatePacket() {
			return ClientboundBlockEntityDataPacket.create(this);
		}

		@Override
		public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			output.store("lines", BlockPos.CODEC.listOf(), List.copyOf(lines));
			return output.buildResult();
		}
	}
}
