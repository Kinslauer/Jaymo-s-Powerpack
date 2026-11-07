package com.jaymospowers.mixin.toolbelt;

import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.compat.ToolBeltCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * (0.21.42) Traveler Tool Belt (optional; all rights reserved, nothing of it copied) finds a player's belt anywhere in
 * their inventory. Andy: "it should go in the baubles slot": the belt it uses (radial menu, tool swap, the belt drawn on
 * the hip) is the one in the Baubles belt slot, or none ({@link ToolBeltCompat}). Skipped when the mod isn't there.
 */
@Pseudo
@Mixin(targets = "com.tiviacz.travelertoolbelt.ToolBeltHelper", remap = false)
abstract class ToolBeltHelperMixin {
	@Inject(method = "getEquippedToolBelt", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$wornBelt(LivingEntity entity, CallbackInfoReturnable<Optional<ItemStack>> cir) {
		if (entity instanceof Player player) {
			ItemStack belt = ToolBeltCompat.worn(player);
			cir.setReturnValue(belt.isEmpty() ? Optional.empty() : Optional.of(belt));
		}
	}

	@Inject(method = "getEquippedToolBeltForRendering", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$shownBelt(LivingEntity entity, CallbackInfoReturnable<Optional<ItemStack>> cir) {
		if (entity instanceof Player player) {
			ItemStack belt = ToolBeltCompat.shown(player);
			cir.setReturnValue(belt.isEmpty() ? Optional.empty() : Optional.of(belt));
		}
	}

	/**
	 * Andy: "lanterns and coin purses can work from inside the belt": the belt takes them as well as tools, and one
	 * carried in a worn belt hangs at the hip and works as if worn there ({@code Baubles.belt}).
	 */
	@Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$hipItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (Baubles.hipItem(stack)) {
			cir.setReturnValue(true);
		}
	}

	/** The swap changed the worn belt's tools in place: the wearer's client is told again. */
	@Inject(method = "swapTool", at = @At("RETURN"), require = 0)
	private static void jaymospowers$resync(Player player, int slot, int button, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			ToolBeltCompat.resync(serverPlayer);
		}
	}
}
