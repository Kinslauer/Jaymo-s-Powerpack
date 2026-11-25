package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A path search's mob, its view of the world and its nodes, for the climbs {@code Climbing} adds to it. */
@Mixin(NodeEvaluator.class)
public interface NodeEvaluatorAccessor {
	@Accessor("mob")
	Mob jaymoskingdoms$mob();

	@Accessor("currentContext")
	PathfindingContext jaymoskingdoms$context();

	@Invoker("getNode")
	Node jaymoskingdoms$node(int x, int y, int z);
}
