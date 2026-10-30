package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.power.zfighter.farworld.FarWorld;
import com.jaymospowers.power.zfighter.farworld.FarWorlds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.55) Ore on a far world takes {@link FarWorlds#SLOWDOWN} times as long unless the tool's head is that world's first metal. */
@Mixin(Player.class)
abstract class FarOreSpeedMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$farOre(BlockState state, CallbackInfoReturnable<Float> cir) {
		if (!state.is(FarWorlds.FAR_ORE)) {
			return;
		}
		Player self = (Player) (Object) this;
		FarWorld world = FarWorld.of(self.level());
		if (world != null && !FarWorlds.answers(self, world)) {
			cir.setReturnValue(cir.getReturnValueF() / FarWorlds.SLOWDOWN);
		}
	}
}
