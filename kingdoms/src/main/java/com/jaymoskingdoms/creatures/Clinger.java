package com.jaymoskingdoms.creatures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** A creature that clings to a host and can be pulled off ({@link Latches}). */
public interface Clinger {
	/** Who it clings to now, or null. */
	@Nullable LivingEntity host(ServerLevel level);

	/** {@code by} tugs at it: whether it came off. */
	boolean pulled(ServerLevel level, ServerPlayer by);
}
