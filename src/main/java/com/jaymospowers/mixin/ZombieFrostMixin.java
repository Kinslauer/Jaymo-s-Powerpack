package com.jaymospowers.mixin;

import com.jaymospowers.frost.Frostbite;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.17.115) A zombie or husk left in powder snow turns into a Frostbite, a drowned into a zombie first (26.4). */
@Mixin(Zombie.class)
abstract class ZombieFrostMixin {
	@Unique
	private int jaymospowers$snowTicks;

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$freezeOver(CallbackInfo ci) {
		this.jaymospowers$snowTicks = Frostbite.snowTick((Zombie)(Object)this, this.jaymospowers$snowTicks);
	}
}
