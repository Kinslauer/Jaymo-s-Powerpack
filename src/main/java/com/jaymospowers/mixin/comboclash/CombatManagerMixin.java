package com.jaymospowers.mixin.comboclash;

import com.jaymospowers.compat.ComboClashCompat;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Combo Clash's air game gives way to ours (Andy, 2026-11-28: "i like everything about except the air combos"). As its
 * server starts a blow of a string ({@code CombatManager.startM1}): whether the player is off the ground (its local 7)
 * is always no, so no blow hovers you, strings in the air or slams down; and its uppercut and downslam variants (its
 * local 8) are a plain fourth blow, the uppercut's jump becoming our launcher as it lands ({@link ComboClashCompat#variant}).
 * Combo Clash is "Combo Clash" by HanHoYoung, shipped unmodified; nothing of it is copied. Without it this applies to nothing.
 */
@Pseudo
@Mixin(targets = "com.comboclash.combat.CombatManager", remap = false)
abstract class CombatManagerMixin {
	private static final String START_M1 = "startM1(Lnet/minecraft/server/level/ServerPlayer;Lcom/comboclash/combat/CombatState;JZ)V";

	@Inject(method = START_M1, at = @At("HEAD"))
	private static void jaymospowers$starting(ServerPlayer player, @Coerce Object state, long now, boolean jump, CallbackInfo ci) {
		ComboClashCompat.starting(player);
	}

	@ModifyVariable(method = START_M1, at = @At("STORE"), index = 7)
	private static boolean jaymospowers$grounded(boolean air) {
		return false;
	}

	@ModifyVariable(method = START_M1, at = @At("STORE"), index = 8)
	private static int jaymospowers$noAirVariants(int variant) {
		return ComboClashCompat.variant(variant);
	}
}
