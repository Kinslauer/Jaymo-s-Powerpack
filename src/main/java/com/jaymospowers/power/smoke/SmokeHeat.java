package com.jaymospowers.power.smoke;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Heat feeds smoke (the way Advanced Chimneys' smoke grows with every heat source tied into a chimney): a Smoke user
 * with fires, lava, magma, lit campfires or lit furnaces within {@link #REACH} blocks makes {@link #PER_SOURCE} more
 * smoke for each (up to {@link #MAX_SOURCES}), and darker, sootier smoke. Counted once a second per player.
 * <p>
 * (0.14.0) Smoke merged into fire: an Emberborn's own body is a heat source ({@code EmberKindle.ember}), so their smoke
 * always pours {@link #PER_SOURCE} more and sootier.
 */
final class SmokeHeat {
	static final int REACH = 3;
	static final float PER_SOURCE = 0.25F;
	static final int MAX_SOURCES = 4;
	/** How sooty smoke is with no heat near, and how much each heat source adds (0 pale grey, 1 black). */
	static final float BASE_SOOT = 0.12F;
	static final float SOOT_PER_SOURCE = 0.18F;
	static final int RECOUNT = 20;

	private record Count(long at, int sources) {
	}

	private static final Map<UUID, Count> COUNTS = new HashMap<>();

	private SmokeHeat() {
	}

	/** A heat source: fire, lava, magma, a lit campfire or furnace. */
	static boolean hot(BlockState state) {
		if (state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.getFluidState().is(FluidTags.LAVA)) {
			return true;
		}
		if (state.is(BlockTags.CAMPFIRES)) {
			return state.getValueOrElse(CampfireBlock.LIT, false);
		}
		return state.getBlock() instanceof AbstractFurnaceBlock && state.getValueOrElse(AbstractFurnaceBlock.LIT, false);
	}

	static int sources(ServerPlayer player) {
		long now = player.level().getGameTime();
		Count count = COUNTS.get(player.getUUID());
		if (count == null || now - count.at >= RECOUNT || now < count.at) {
			int found = count(player.level(), player.blockPosition());
			if (com.jaymospowers.power.ember.EmberKindle.ember(player)) {
				found = Math.min(MAX_SOURCES, found + 1);
			}
			count = new Count(now, found);
			COUNTS.put(player.getUUID(), count);
		}
		return count.sources;
	}

	/** How much more smoke this player makes for the heat round them (1 with none, up to 2). */
	static float boost(ServerPlayer player) {
		return 1.0F + PER_SOURCE * sources(player);
	}

	static float soot(ServerPlayer player) {
		return Math.min(0.85F, BASE_SOOT + SOOT_PER_SOURCE * sources(player));
	}

	private static int count(ServerLevel level, BlockPos center) {
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH))) {
			if (hot(level.getBlockState(pos)) && ++found >= MAX_SOURCES) {
				break;
			}
		}
		return found;
	}

	static void forget(ServerPlayer player) {
		COUNTS.remove(player.getUUID());
	}
}
