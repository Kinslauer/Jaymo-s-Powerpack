package com.jaymospowers.power.arsenal;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Surface (Andy's Awakening pick: "Pocket Arsenal"). Sprint + G: a rift tears straight up through the rock over you and you step
 * out at the highest safe spot above you (solid footing, room to stand, no fire, lava or water): out of any cave. Under a roof
 * that closes the world (the Nether's), the highest safe spot below the roof.
 */
final class ArsenalSurface {
	// ---- tuning ----
	private static final String TABLE = "arsenal_awakening";
	private static final int SLOT = 1;
	static final int COOLDOWN = 20 * 60;
	static final int VOID = 0x5A1E8C, RIM = 0xC07CFF;

	private ArsenalSurface() {
	}

	/** Sprint + G. */
	static void cast(ServerPlayer player) {
		if (!PickKit.ready(TABLE, player, SLOT) && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.surface_wait", (PickKit.left(TABLE, player, SLOT) + 19) / 20)
				.withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		ServerLevel level = player.level();
		BlockPos spot = highest(level, player);
		if (spot == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.surface_none").withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		Vec3 from = player.position();
		Vec3 to = Vec3.atBottomCenterOf(spot);
		MagicFx.beam(level, from.add(0.0, 1.0, 0.0), to.add(0.0, 1.0, 0.0), 0.5F, VOID, 16);
		MagicFx.ring(level, from.add(0.0, 0.1, 0.0), 1.2F, RIM);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.8F);
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		MagicFx.ring(level, to.add(0.0, 0.1, 0.0), 1.2F, RIM);
		level.sendParticles(MagicFx.VOID, to.x, to.y + 1.0, to.z, 40, 0.4, 0.9, 0.4, 0.3);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.6F);
	}

	/** The highest safe standing spot over the player's head, or null if there is none above them. */
	private static @Nullable BlockPos highest(ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		if (level.dimensionType().hasCeiling()) {
			// not out onto the roof of the world
			top = Math.min(top, level.getMinY() + level.dimensionType().logicalHeight() - 2);
		}
		top = Math.min(top, level.getMaxY() - 1);
		for (int y = top; y > at.getY() + 1; y--) {
			BlockPos feet = new BlockPos(at.getX(), y, at.getZ());
			if (safe(level, feet)) {
				return feet;
			}
		}
		return null;
	}

	private static boolean safe(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP) || floor.is(Blocks.MAGMA_BLOCK) || !level.getFluidState(below).isEmpty()) {
			return false;
		}
		return clear(level, feet) && clear(level, feet.above());
	}

	private static boolean clear(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE)
			&& !state.is(Blocks.POWDER_SNOW) && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.WITHER_ROSE) && !state.is(Blocks.COBWEB);
	}
}
