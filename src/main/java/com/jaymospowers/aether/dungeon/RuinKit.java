package com.jaymospowers.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The Sentry Ruins' building blocks, found by id (the Aether's own and a few vanilla ones), and how a wall is picked. */
final class RuinKit {
	// ---- tuning ----
	/** Chance a wall block is lit sentry stone instead (the notes: about 2 percent). */
	static final float SENTRY_CHANCE = 0.02F;
	/** Share of plain undershale (the rest is undershale bricks) in a wall. */
	static final float UNDERSHALE_SHARE = 0.4F;

	private static final Map<String, Block> BLOCKS = new ConcurrentHashMap<>();

	private RuinKit() {
	}

	/** One of our blocks by id. */
	static Block ours(String id) {
		return BLOCKS.computeIfAbsent("j:" + id, key -> {
			Block block = BuiltInRegistries.BLOCK.getValue(JaymosPowers.id(id));
			if (block == Blocks.AIR) {
				JaymosPowers.LOGGER.error("Sentry Ruins: no block {}", id);
				return Blocks.STONE;
			}
			return block;
		});
	}

	/** A vanilla block by id. */
	static Block vanilla(String id) {
		return BLOCKS.computeIfAbsent("m:" + id, key -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)));
	}

	static BlockState state(String id) {
		return ours(id).defaultBlockState();
	}

	static BlockState vstate(String id) {
		return vanilla(id).defaultBlockState();
	}

	/** Sentry stone, lit (it glows; a trap's pulse will light the rest). */
	static BlockState lit(String id) {
		BlockState state = state(id);
		return state.hasProperty(SentryBlock.LIT) ? state.setValue(SentryBlock.LIT, true) : state;
	}

	/** A wall block: undershale and undershale bricks, now and then a lit sentry block, chosen by {@code r}, {@code s} (0 to 1). */
	static BlockState wall(float r, float s) {
		if (r < SENTRY_CHANCE) {
			return lit(s < 0.4F ? "sentry_bricks" : s < 0.75F ? "sentry_flagstones" : "sentry_base_bricks");
		}
		return state(r < UNDERSHALE_SHARE ? "undershale" : "undershale_bricks");
	}

	/** The locked look-alike of a wall block (the boss room's shell). */
	static BlockState locked(BlockState wall) {
		String id = BuiltInRegistries.BLOCK.getKey(wall.getBlock()).getPath();
		return switch (id) {
			case "undershale" -> state("locked_undershale");
			case "sentry_bricks" -> state("locked_sentry_bricks");
			case "sentry_base_bricks" -> state("locked_sentry_base_bricks");
			case "sentry_flagstones" -> state("locked_sentry_flagstones");
			case "sentry_lightstone" -> state("locked_sentry_lightstone");
			default -> state("locked_undershale_bricks");
		};
	}
}
