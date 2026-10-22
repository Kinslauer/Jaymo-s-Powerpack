package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stonebound (with wear) and Dwarven (with depth) speed up a tinker tool as you mine; both sides work it out alike. */
@Mixin(Player.class)
abstract class TinkerMiningSpeedMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$liveTraits(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		ItemStack held = self.getMainHandItem();
		if (!(held.getItem() instanceof TinkerTool)) {
			return;
		}
		float base = held.getDestroySpeed(state);
		if (base > 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * ToolStats.liveSpeed(self, held, state, base) / base);
		}
	}
}
