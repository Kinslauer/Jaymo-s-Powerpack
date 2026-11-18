package com.jaymospowers.buildblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import org.jspecify.annotations.Nullable;

/**
 * Paired doors: two doors side by side, facing the same way with their hinges on the outer sides (what two doors set down
 * beside each other become), open and shut together. A player who sneaks works one alone. It acts on
 * {@link DoorBlock} itself, so vanilla's doors and every mod's that extend it (Macaw's Doors, Spooky Doors...) pair with no
 * compat class; doors only redstone opens (iron) are left to the redstone. Called from {@code DoorPairMixin}.
 */
public final class PairedDoors {
	/** The partner's own opening is not followed in turn. */
	private static boolean busy;

	private PairedDoors() {
	}

	/** The door at {@code pos} has just been opened or shut by {@code by}: its partner, if it has one, goes the same way. */
	public static void follow(Level level, BlockPos pos, @Nullable Entity by) {
		if (busy || level.isClientSide() || (by instanceof Player player && player.isShiftKeyDown())) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock door) || !door.type().canOpenByHand()) {
			return;
		}
		Direction facing = state.getValue(DoorBlock.FACING);
		DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
		// a door set down with a door on its left takes the hinge on the right, and the other way round
		BlockPos otherPos = pos.relative(hinge == DoorHingeSide.RIGHT ? facing.getCounterClockWise() : facing.getClockWise());
		BlockState other = level.getBlockState(otherPos);
		boolean open = state.getValue(DoorBlock.OPEN);
		if (!(other.getBlock() instanceof DoorBlock partner) || !partner.type().canOpenByHand()
			|| other.getValue(DoorBlock.FACING) != facing || other.getValue(DoorBlock.HINGE) == hinge
			|| other.getValue(DoorBlock.HALF) != state.getValue(DoorBlock.HALF) || other.getValue(DoorBlock.OPEN) == open) {
			return;
		}
		busy = true;
		try {
			partner.setOpen(by, level, other, otherPos, open);
		} finally {
			busy = false;
		}
	}
}
