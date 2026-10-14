package com.jaymospowers.client.mixin;

import com.jaymospowers.client.TerrainAhead;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The player's own moves are held back while the terrain ahead is still loading (see TerrainAhead). */
@Mixin(Entity.class)
abstract class TerrainAheadMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 jaymospowers$terrainAhead(Vec3 delta, MoverType type) {
		return TerrainAhead.limit((Entity) (Object) this, type, delta);
	}
}
