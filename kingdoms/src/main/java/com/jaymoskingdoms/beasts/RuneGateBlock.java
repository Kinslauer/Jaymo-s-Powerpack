package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Fx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A rune gate: one of the inner gates the labyrinth-builders set across a hall's doorways ({@link LabyrinthKeep}). It has
 * three sockets, one for each rune tablet ({@link LabyrinthCraft#TABLETS}). Use a tablet on it and the tablet is set in
 * its socket, in every block of the gate at once; with all three set the whole gate swings open for good. Nothing breaks
 * it. Used bare-handed it says how many sockets are still empty.
 */
public final class RuneGateBlock extends Block {
	/** One socket for each of the three tablets. */
	public static final BooleanProperty[] SOCKETS = {BooleanProperty.create("anthu"), BooleanProperty.create("kesh"), BooleanProperty.create("orrim")};
	/** The most blocks of one gate (the doorways are small). */
	static final int MOST_BLOCKS = 160;
	/** Amber's colour, for the set runes. */
	static final int GLOW = 0xFFB030;

	public RuneGateBlock(BlockBehaviour.Properties properties) {
		super(properties);
		BlockState state = this.stateDefinition.any();
		for (BooleanProperty socket : SOCKETS) {
			state = state.setValue(socket, false);
		}
		this.registerDefaultState(state);
	}

	/** How bright it burns: two for each tablet set. */
	static int light(BlockState state) {
		return lit(state) * 2;
	}

	static int lit(BlockState state) {
		int n = 0;
		for (BooleanProperty socket : SOCKETS) {
			n += state.getValue(socket) ? 1 : 0;
		}
		return n;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SOCKETS);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		for (int i = 0; i < SOCKETS.length; i++) {
			if (!held.is(LabyrinthCraft.TABLETS[i])) {
				continue;
			}
			if (state.getValue(SOCKETS[i])) {
				return InteractionResult.FAIL;
			}
			if (level instanceof ServerLevel server) {
				set(server, pos, SOCKETS[i]);
				if (!player.isCreative()) {
					held.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rune_gate.empty", SOCKETS.length - lit(state)));
		}
		return InteractionResult.SUCCESS;
	}

	/** Sets {@code socket} in every block of the gate at {@code pos}; when all three are set, the gate opens. */
	private static void set(ServerLevel level, BlockPos pos, BooleanProperty socket) {
		List<BlockPos> gate = gate(level, pos);
		boolean open = false;
		for (BlockPos at : gate) {
			BlockState state = level.getBlockState(at).setValue(socket, true);
			level.setBlock(at, state, Block.UPDATE_ALL);
			open = lit(state) == SOCKETS.length;
		}
		Vec3 at = Vec3.atCenterOf(pos);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F + 0.2F * lit(level.getBlockState(pos)));
		Fx.burst(level, at.x, at.y, at.z, GLOW, 1.0F, 12, 0.4, 0.4, 0.4);
		if (open) {
			for (BlockPos each : gate) {
				level.removeBlock(each, false);
			}
			level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.5F);
			Fx.ring(level, at, 2.5F, GLOW);
		}
	}

	/** The blocks of the gate {@code pos} is part of: the rune gate blocks joined to it. */
	private static List<BlockPos> gate(ServerLevel level, BlockPos start) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && found.size() < MOST_BLOCKS) {
			BlockPos at = queue.poll();
			found.add(at);
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (level.getBlockState(next).is(LabyrinthCraft.RUNE_GATE) && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		return found;
	}
}
