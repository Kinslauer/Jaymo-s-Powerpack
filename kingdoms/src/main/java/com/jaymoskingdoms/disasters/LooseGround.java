package com.jaymoskingdoms.disasters;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.103) Sinkholes, after Better With Mods' loose ground (MIT; its idea, our own code): sand, gravel or any other
 * falling block with nothing under it gives way a moment after a player steps on it, the way it would had the block
 * beside it been broken. Only players on foot in survival set it off; a weightless player (Jaymo's
 * Powers' Gravity, or anything else that takes most of their gravity away) treads too lightly.
 */
public final class LooseGround {
	/** Ticks between stepping on it and its fall. */
	static final int COLLAPSE_DELAY = 5;
	/** A player whose gravity is below this share of the usual is too light to set it off. */
	static final double LIGHT_GRAVITY = 0.5;
	/** The gravity modifier Jaymo's Powers' Weightless puts on a player (read by name: no code of theirs is used). */
	static final Identifier WEIGHTLESS = Identifier.fromNamespaceAndPath("jaymospowers", "gravity_weightless");

	private LooseGround() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LooseGround::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.onGround() || player.gameMode() != GameType.SURVIVAL || player.isPassenger() || light(player)) {
				continue;
			}
			ServerLevel level = player.level();
			BlockPos under = player.getOnPos();
			BlockState state = level.getBlockState(under);
			if (!(state.getBlock() instanceof FallingBlock) || !level.getBlockState(under.below()).isAir()
				|| under.getY() <= level.getMinY()) {
				continue;
			}
			Block block = state.getBlock();
			if (!level.getBlockTicks().hasScheduledTick(under, block)) {
				// the block's own tick drops it (FallingBlock#tick: free below, it falls)
				level.scheduleTick(under, block, COLLAPSE_DELAY);
			}
		}
	}

	/** Weightless, or near enough. */
	static boolean light(ServerPlayer player) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		return gravity != null && (gravity.hasModifier(WEIGHTLESS) || gravity.getValue() < gravity.getBaseValue() * LIGHT_GRAVITY);
	}
}
