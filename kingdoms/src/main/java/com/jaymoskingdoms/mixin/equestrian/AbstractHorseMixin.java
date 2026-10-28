package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Herding;
import com.jaymoskingdoms.equestrian.Mounted;
import com.jaymoskingdoms.mixin.MobAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.34) Horses ride at their rider's gait, are broken in by their temper, and pass their breed to their foals ({@link Equestrian}).
 * (0.5.44) They turn wide at speed without a bridle, side-pass, eat to fill their bellies and bond with whoever tames them ({@link Bonding}).
 * (0.7.305) They take their owner's orders ({@link Herding}), carry a second rider, swim with their rider and ride on
 * when told to ({@link Mounted}).
 */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseMixin {
	@Inject(method = "getRiddenSpeed", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$gait(Player controller, CallbackInfoReturnable<Float> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			cir.setReturnValue(Equestrian.riddenSpeed(self, controller, cir.getReturnValueF()));
		}
	}

	@Inject(method = "getMaxTemper", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$temper(CallbackInfoReturnable<Integer> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			cir.setReturnValue(Equestrian.maxTemper(self, cir.getReturnValueI()));
		}
	}

	@Inject(method = "setOffspringAttributes", at = @At("TAIL"))
	private void jaymoskingdoms$foal(AgeableMob partner, AbstractHorse baby, CallbackInfo ci) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			Equestrian.foal(self, partner, baby);
		}
	}

	@Inject(method = "getRiddenRotation", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$turn(LivingEntity controller, CallbackInfoReturnable<Vec2> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			Vec2 r = cir.getReturnValue();
			cir.setReturnValue(new Vec2(r.x, Bonding.turn(self, r.y, Equestrian.gaitFor(self, controller))));
		}
	}

	@Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$sidePass(Player controller, Vec3 selfInput, CallbackInfoReturnable<Vec3> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		Vec3 v = cir.getReturnValue();
		if (v != Vec3.ZERO && Equestrian.covered(self) && Bonding.sidePass(self, Equestrian.gaitFor(self, controller))) {
			cir.setReturnValue(new Vec3(controller.xxa, v.y, v.z));
		} else if (v != Vec3.ZERO && com.jaymoskingdoms.equestrian.FreeRein.active.test(controller)) {
			// (0.7.110) free rein: the rider is turned toward the camera, so the horse just goes on
			cir.setReturnValue(com.jaymoskingdoms.equestrian.FreeRein.input(controller, v));
		}
		if (Equestrian.covered(self)) {
			// (0.7.305) riding on without holding forward
			cir.setReturnValue(Mounted.input(self, controller, cir.getReturnValue()));
		}
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void jaymoskingdoms$orders(CallbackInfo ci) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			((MobAccessor)self).jaymoskingdoms$goals().addGoal(0, new Herding.OrderGoal(self));
		}
	}

	@Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$pillion(Entity passenger, EntityDimensions dimensions, float scale, CallbackInfoReturnable<Vec3> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			cir.setReturnValue(Mounted.seat(self, passenger, cir.getReturnValue(), scale));
		}
	}

	@Inject(method = "tickRidden", at = @At("TAIL"))
	private void jaymoskingdoms$swim(Player controller, Vec3 riddenInput, CallbackInfo ci) {
		Mounted.swim((AbstractHorse)(Object)this);
	}

	@Inject(method = "handleEating", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$feed(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Boolean ate = Bonding.feed((AbstractHorse)(Object)this, player, stack);
		if (ate != null) {
			cir.setReturnValue(ate);
		}
	}

	@Inject(method = "handleEating", at = @At("RETURN"))
	private void jaymoskingdoms$fed(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			Bonding.ateVanilla((AbstractHorse)(Object)this, player, stack);
		}
	}

	@Inject(method = "tameWithName", at = @At("TAIL"))
	private void jaymoskingdoms$tamed(Player player, CallbackInfoReturnable<Boolean> cir) {
		Bonding.tamed((AbstractHorse)(Object)this, player);
	}
}
