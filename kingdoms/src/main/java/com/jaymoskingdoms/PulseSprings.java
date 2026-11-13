package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The Verdant World's pulse trees (Jaymo's Powers; found by id) feed the attuned: their stems and heads beat together in a slow
 * time, and a caster standing among them draws mana in step with it. Each living pulse tree block within {@link #RANGE} gives
 * {@link #PER_BLOCK} mana a second, up to {@link #MOST} for a whole wood, so a grove is worth sitting in but is no fountain.
 * Read by {@link Mana#tick}, once a second per attuned player.
 */
final class PulseSprings {
	/** How far round the caster pulse trees are felt (blocks), the mana a second each gives, and the most they give together. */
	static final int RANGE = 5;
	static final float PER_BLOCK = 0.05F, MOST = 1.5F;

	private static Block tree, log;

	private PulseSprings() {
	}

	private static void look() {
		if (tree == null) {
			tree = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("jaymospowers", "pulse_tree"));
			log = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("jaymospowers", "pulse_tree_log"));
		}
	}

	/** The mana a second {@code player} draws from the pulse trees round them (0 where there are none, or Powers is missing). */
	static float draw(ServerPlayer player) {
		look();
		if (tree == Blocks.AIR) {
			return 0.0F;
		}
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-RANGE, -RANGE, -RANGE), at.offset(RANGE, RANGE, RANGE))) {
			Block block = level.getBlockState(pos).getBlock();
			if (block == tree || block == log) {
				found++;
			}
		}
		return Math.min(MOST, found * PER_BLOCK);
	}
}
