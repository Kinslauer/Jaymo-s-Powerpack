package com.jaymospowers.power.chrono;

import com.jaymospowers.core.Powers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

/** Entry points for the chrono mixins (mixin classes can't be referenced from normal code, so they call out to here). */
public final class ChronoHooks {
	private ChronoHooks() {
	}

	/** True while a Stasis Field holds this entity still. Server side only. */
	public static boolean isFrozen(Entity entity) {
		return StasisField.isFrozen(entity);
	}

	/** A player placed a block (server side). Chrono players' habits are remembered for their echoes. */
	public static void placed(ServerPlayer player, Block block) {
		if (Powers.has(player, ChronoPower.INSTANCE)) {
			ChronoMemory.count(ChronoMemory.of(player).placed, BuiltInRegistries.BLOCK.getKey(block));
		}
	}
}
