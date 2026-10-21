package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.dragons.DragonsClient;
import com.jaymoskingdoms.dragons.NotusDragon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.137) On a flying dragon Shift dives ({@code DragonsClient} reads the key itself), so in the air it isn't sent as
 * sneaking and doesn't throw you out of the saddle; on the ground it dismounts as ever. (As Powers' bomb glider does.)
 */
@Mixin(KeyboardInput.class)
abstract class DragonDiveInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$dragonDive(CallbackInfo ci) {
		NotusDragon dragon = DragonsClient.steered(Minecraft.getInstance().player);
		if (dragon == null || !dragon.airborne()) {
			return;
		}
		Input k = this.keyPresses;
		if (k.shift()) {
			this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), false, k.sprint());
		}
	}
}
