package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.FallingTree;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A fallen tree's logs are solid to everything moving about them: to stand on, to walk into ({@link FallingTree#collisions}). */
@Mixin(Entity.class)
abstract class EntityFallenTreeMixin {
	@WrapOperation(method = "collide", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> jaymoskingdoms$fallenTrees(Level level, Entity entity, AABB area, Operation<List<VoxelShape>> original) {
		return FallingTree.collisions(entity, area, original.call(level, entity, area));
	}
}
