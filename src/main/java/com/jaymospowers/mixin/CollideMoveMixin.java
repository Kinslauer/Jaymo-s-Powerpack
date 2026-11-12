package com.jaymospowers.mixin;

import com.jaymospowers.core.collide.Collisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every move that bumped into something, with where it meant to go and where it got, to {@link Collisions#moved}. */
@Mixin(Entity.class)
public abstract class CollideMoveMixin {
	@Unique
	private double jaymospowers$fromX, jaymospowers$fromY, jaymospowers$fromZ;

	@Inject(method = "move", at = @At("HEAD"))
	private void jaymospowers$collideFrom(MoverType type, Vec3 movement, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		jaymospowers$fromX = self.getX();
		jaymospowers$fromY = self.getY();
		jaymospowers$fromZ = self.getZ();
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void jaymospowers$collided(MoverType type, Vec3 movement, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self.horizontalCollision || self.verticalCollision) {
			Collisions.moved(self, type, movement, self.getX() - jaymospowers$fromX, self.getY() - jaymospowers$fromY, self.getZ() - jaymospowers$fromZ);
		}
	}
}
