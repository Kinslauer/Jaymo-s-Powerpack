package com.jaymospowers.client.mixin.comboclash;

import com.jaymospowers.client.compat.ComboClashClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Combo Clash's combat mode is on for everyone now ({@code ComboClashClient}), so its client steps around ours where its
 * string has nothing to do. Its attack click ({@code attackKey}, from its pre-attack callback) and its attack held (the
 * string it sends every tick the key is down, in {@code tickStart}) give way when {@link ComboClashClient#yieldsAttack}
 * says so; its block (F held) gives way to a power's own guard or counter ({@link ComboClashClient#blockYields}); and it
 * no longer swallows Q and the swap key every tick (its dash is on Left Alt here, not Q). Combo Clash is "Combo Clash" by
 * HanHoYoung, shipped unmodified; nothing of it is copied. Without it this applies to nothing.
 */
@Pseudo
@Mixin(targets = "com.comboclash.client.ClientCombat", remap = false)
abstract class ClientCombatMixin {
	@Inject(method = "attackKey()Z", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$yieldClick(CallbackInfoReturnable<Boolean> cir) {
		if (ComboClashClient.yieldsAttack()) {
			cir.setReturnValue(false);
		}
	}

	/** The first key read in {@code tickStart} is its block's (F held). */
	@WrapOperation(method = "tickStart()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 0))
	private static boolean jaymospowers$yieldBlock(KeyMapping key, Operation<Boolean> original) {
		return original.call(key) && !ComboClashClient.blockYields();
	}

	/** The second is the attack key's, held. */
	@WrapOperation(method = "tickStart()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 1))
	private static boolean jaymospowers$yieldHold(KeyMapping key, Operation<Boolean> original) {
		return original.call(key) && !ComboClashClient.yieldsAttack();
	}

	/**
	 * Its dash (Left Alt here) holds off while Alt is the zoom's ({@code SneakZoom#ownsAlt}: Shift held with it, or a
	 * fishing line out); the press is spent, not kept for later. Its other keys' presses go through as ever.
	 */
	@WrapOperation(method = "tickStart()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
	private static boolean jaymospowers$zoomNotDash(KeyMapping key, Operation<Boolean> original) {
		if (DASH.equals(key.getName()) && com.jaymospowers.client.SneakZoom.ownsAlt()) {
			while (original.call(key)) {
				// spent
			}
			return false;
		}
		return original.call(key);
	}

	/** Combo Clash's dash key. */
	private static final String DASH = "key.comboclash.dash";

	/** Its third drain is the swap and drop keys', in combat mode. */
	@WrapOperation(method = "tickStart()V", at = @At(value = "INVOKE", target = "Lcom/comboclash/client/ClientCombat;drain([Lnet/minecraft/client/KeyMapping;)V", ordinal = 2))
	private static void jaymospowers$keepDrop(KeyMapping[] keys, Operation<Void> original) {
		// Q drops as ever (and the swap key, wherever a player has put it, swaps)
	}
}
