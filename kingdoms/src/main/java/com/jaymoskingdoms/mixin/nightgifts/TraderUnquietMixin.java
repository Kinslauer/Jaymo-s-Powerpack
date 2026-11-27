package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.BloodPage;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A trader asks more of a vampire who took the Unquiet pact, from the moment they start trading ({@link BloodPage#tolls}). */
@Mixin(AbstractVillager.class)
abstract class TraderUnquietMixin {
	@Inject(method = "setTradingPlayer", at = @At("TAIL"))
	private void jaymoskingdoms$unquiet(Player player, CallbackInfo ci) {
		BloodPage.tolls((AbstractVillager)(Object)this, player);
	}
}
