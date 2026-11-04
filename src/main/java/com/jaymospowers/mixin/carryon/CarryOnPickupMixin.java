package com.jaymospowers.mixin.carryon;

import com.jaymospowers.core.CarryOnCompat;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.17) Carry On (LGPL-3.0, Tschipp; optional, shipped unchanged, nothing of it copied) carries blocks; bodies stay
 * ours ({@link CarryOnCompat}). Its pick-ups all go through {@code PickupHandler}: no entity is ever lifted by it, nothing is
 * lifted while our carry, a Kitchen corpse or being down has your arms, and the {@code jaymospowers:carry_on_no_lift}
 * blocks stay put. Skipped when Carry On isn't there.
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
abstract class CarryOnPickupMixin {
	@Inject(method = "canCarryGeneral", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$armsBusy(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		if (CarryOnCompat.armsBusy(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tryPickUpBlock", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$noLift(ServerPlayer player, BlockPos pos, Level level, BiFunction<BlockState, BlockPos, Boolean> pickupCallback,
		CallbackInfoReturnable<Boolean> cir) {
		if (CarryOnCompat.armsBusy(player) || CarryOnCompat.noLift(level.getBlockState(pos))) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tryPickupEntity", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$noBodies(ServerPlayer player, Entity entity, Function<Entity, Boolean> pickupCallback,
		CallbackInfoReturnable<Boolean> cir) {
		// players, animals, mobs and corpses are carried by our own Carry, never by Carry On
		cir.setReturnValue(false);
	}
}
