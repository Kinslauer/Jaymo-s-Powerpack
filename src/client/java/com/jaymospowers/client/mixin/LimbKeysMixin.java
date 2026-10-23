package com.jaymospowers.client.mixin;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.LimbUse;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.15.136) A power key whose ability is done with a severed limb ({@link LimbUse#need}) reads as up and its presses are
 * swallowed, the way {@link DownedKeysMixin} does for the downed: the ability keys, Mode, Ultimate and the power skill
 * key (X). Each fresh press says on the action bar what's missing. A key held as the limb goes counts as let go, so a held
 * ability ends. (The server refuses them too; the skill key's own handlers trust this.)
 */
@Mixin(KeyMapping.class)
abstract class LimbKeysMixin {
	private static final String OURS = "key.jaymospowers.";
	/** The power skill key (X), shared by every power's own handler. */
	private static final String SKILL = "key.jaymospowers.web_symbiote";

	@Shadow
	@Final
	private String name;

	@Shadow
	private int clickCount;

	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$limbIsDown(CallbackInfoReturnable<Boolean> cir) {
		if (this.refused()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$limbClick(CallbackInfoReturnable<Boolean> cir) {
		if (this.refused()) {
			cir.setReturnValue(false);
		}
	}

	/** Whether this key belongs to an ability the local player's limbs can't do; a press waiting is spent and explained. */
	private boolean refused() {
		if (!this.name.startsWith(OURS)) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		Power power = Powers.of(player);
		if (power == null) {
			return false;
		}
		String slot = SKILL.equals(this.name) ? "special" : this.name.substring(OURS.length());
		int need = LimbUse.need(power, player, slot);
		if (need == LimbUse.NONE || LimbUse.usable(player, need)) {
			return false;
		}
		if (this.clickCount > 0) {
			this.clickCount = 0;
			LimbUse.refuse(player, need);
		}
		return true;
	}
}
