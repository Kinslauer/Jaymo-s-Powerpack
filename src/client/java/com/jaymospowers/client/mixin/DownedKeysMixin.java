package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wounds.Downed;
import java.util.Set;
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
 * (0.15.21) Downed, all you can do is crawl. Andy (2026-10-21): "when players are downed, they should not be able to use
 * powers, or really do anything but crawl". While the local player is down, every key that acts reads as up and its
 * presses are swallowed: Jump, Sprint, attack, use, pick, drop, swap hands, and every one of ours (the ability keys, Mode,
 * Ultimate, Dodge, Crawl, the menu...). A key already held counts as let go, so a held ability ends. Moving (and the
 * double-tap roll on A / D), Sneak (held to give up), looking about, the hotbar, chat and the menus still work.
 */
@Mixin(KeyMapping.class)
abstract class DownedKeysMixin {
	/** Vanilla's keys that act in the world. */
	private static final Set<String> ACTING = Set.of("key.jump", "key.sprint", "key.attack", "key.use", "key.pickItem", "key.drop",
		"key.swapOffhand");
	/** Every key of ours starts with this. */
	private static final String OURS = "key.jaymospowers.";

	@Shadow
	@Final
	private String name;

	@Shadow
	private int clickCount;

	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$downedIsDown(CallbackInfoReturnable<Boolean> cir) {
		if (this.deadWhileDown()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$downedClick(CallbackInfoReturnable<Boolean> cir) {
		if (this.deadWhileDown()) {
			// (the press is spent, not saved for when they're up)
			this.clickCount = 0;
			cir.setReturnValue(false);
		}
	}

	private boolean deadWhileDown() {
		if (!this.name.startsWith(OURS) && !ACTING.contains(this.name)) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && Downed.isDowned(player);
	}
}
