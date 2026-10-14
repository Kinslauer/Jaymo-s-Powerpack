package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.Ships;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.7.7) Ships' blocks are solid to everything moving about them: their decks to stand on, their sides to walk into. */
@Mixin(Entity.class)
abstract class EntityShipDeckMixin {
	@WrapOperation(method = "collide", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> jaymoskingdoms$shipDecks(Level level, Entity entity, AABB area, Operation<List<VoxelShape>> original) {
		return Ships.collisions(entity, area, original.call(level, entity, area));
	}
}
