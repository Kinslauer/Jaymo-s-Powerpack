package com.jaymoskingdoms.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What {@code PathNavigation.createPath} sets on a navigation when it finds a path (where a recomputed path goes, and the
 * stuck timer), so a trial path ({@code PathProbes}) can put them back as they were.
 */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
	@Accessor("targetPos")
	@Nullable BlockPos jaymoskingdoms$targetPos();

	@Accessor("targetPos")
	void jaymoskingdoms$setTargetPos(@Nullable BlockPos pos);

	@Accessor("reachRange")
	int jaymoskingdoms$reachRange();

	@Accessor("reachRange")
	void jaymoskingdoms$setReachRange(int reach);

	@Accessor("timeoutCachedNode")
	Vec3i jaymoskingdoms$timeoutCachedNode();

	@Accessor("timeoutCachedNode")
	void jaymoskingdoms$setTimeoutCachedNode(Vec3i node);

	@Accessor("timeoutTimer")
	long jaymoskingdoms$timeoutTimer();

	@Accessor("timeoutTimer")
	void jaymoskingdoms$setTimeoutTimer(long timer);

	@Accessor("timeoutLimit")
	double jaymoskingdoms$timeoutLimit();

	@Accessor("timeoutLimit")
	void jaymoskingdoms$setTimeoutLimit(double limit);

	@Accessor("isStuck")
	boolean jaymoskingdoms$isStuck();

	@Accessor("isStuck")
	void jaymoskingdoms$setStuck(boolean stuck);
}
