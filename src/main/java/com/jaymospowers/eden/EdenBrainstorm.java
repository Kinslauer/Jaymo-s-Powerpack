package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How the rest of the world answers the Brainstorm (the storm country of the Verdant World): a bolt that strikes near its
 * brain trees sets every charged block about it off at once, and a Stormborn standing in it calls the Thunderclap back as
 * fast as in a thunderstorm ({@code HeritageMoves}), for the air there is always full of it.
 */
public final class EdenBrainstorm {
	/** How far (blocks) from a bolt the brain blocks answer it, and the most that go off for one bolt. */
	public static final int REACH = 8, MOST = 40;

	private EdenBrainstorm() {
	}

	/** Whether {@code player} stands in the Brainstorm. */
	public static boolean in(Player player) {
		return player.level().getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().equals(JaymosPowers.id("brainstorm"))).orElse(false);
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LightningBolt && level instanceof ServerLevel server) {
				answer(server, entity);
			}
		});
	}

	private static void answer(ServerLevel level, Entity bolt) {
		BlockPos centre = bolt.blockPosition();
		int gone = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-REACH, -REACH, -REACH), centre.offset(REACH, REACH, REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BrainBlock brain && gone < MOST) {
				brain.discharge(state, level, pos.immutable(), level.getRandom());
				gone++;
			}
		}
	}
}
