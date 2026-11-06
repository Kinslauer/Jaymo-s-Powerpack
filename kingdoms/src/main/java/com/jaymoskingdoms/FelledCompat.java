package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.627) Andy: "https://www.curseforge.com/minecraft/mc-mods/felled ... it captures what i wanted for our trees". Felled
 * (Raiiiden, all rights reserved, shipped unchanged from CurseForge with its Zarith physics library) brings a tree down as
 * a real rigid body: it tips over its stump, rolls, snaps on a hard landing, floats, and can be stood on. When it's in the
 * pack, a player's chopping still goes through ours (the cut narrows, the chop meter, a woodcutter's credit), but the
 * stroke that would fell the tree hands it to Felled's {@code FellingHandler.fellFrom} instead of our {@link FallingTree}.
 * Everything else that brings trees down (blasts, giants, tornadoes, gatherers, Grimsby's axe) stays ours. Called by
 * reflection only, so Kingdoms runs the same without it.
 */
public final class FelledCompat {
	private static boolean looked;
	private static @Nullable Method fellFrom;

	private FelledCompat() {
	}

	/** Whether Felled is loaded (and its felling found). */
	public static boolean loaded() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("felled")) {
				try {
					fellFrom = Class.forName("com.raiiiden.felled.event.FellingHandler")
						.getMethod("fellFrom", ServerLevel.class, BlockPos.class, net.minecraft.world.entity.player.Player.class, Vec3.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Felled is loaded but its felling wasn't found; trees fall our way", e);
				}
			}
		}
		return fellFrom != null;
	}

	/**
	 * {@code player}'s stroke at {@code cut} fells the tree (its {@code logs} over the cut): Felled takes it, as it would a
	 * log broken whole. The chopped logs are made whole again first (Felled only knows logs), and the cut itself is broken
	 * as the player's. How many logs came down, or 0 if Felled didn't take it (too big for it, no physics): then the tree
	 * is left whole, for ours to fell.
	 */
	static int fell(ServerLevel level, BlockPos cut, List<BlockPos> logs, ServerPlayer player) {
		if (!loaded()) {
			return 0;
		}
		mend(level, cut);
		for (BlockPos p : logs) {
			mend(level, p);
		}
		int felled;
		try {
			felled = (int)fellFrom.invoke(null, level, cut, player, player.getEyePosition());
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Felled couldn't fell the tree at {}; it falls our way", cut, e);
			return 0;
		}
		if (felled > 0 && level.getBlockState(cut).is(net.minecraft.tags.BlockTags.LOGS)) {
			level.destroyBlock(cut, true, player);
		}
		return felled;
	}

	/** A chopped log back to the whole log it was. */
	private static void mend(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ChoppedLog.BLOCK)) {
			level.setBlock(pos, ChoppedLog.real(level, pos, state), Block.UPDATE_ALL);
		}
	}
}
