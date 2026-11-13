package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wounds.Limbo;
import com.jaymospowers.core.wounds.Mortality;
import com.jaymospowers.core.wounds.Revival;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two things on the death screen of a death that ends the character ({@link Mortality#FINAL}). Beside Reincarnate sits a
 * Limbo button (Andy: "give players a 'Limbo' button alongside reincarnate"): it tells the server, then respawns them
 * into the wait ({@link Limbo}). And while a minute is being held for someone who could bring them back
 * ({@link Revival#WAITING}), a line says so, with the seconds left; Limbo is not offered then, only Reincarnate.
 */
@Mixin(DeathScreen.class)
abstract class DeathScreenLimboMixin extends net.minecraft.client.gui.screens.Screen {
	@Shadow
	@Final
	private List<Button> exitButtons;

	@Shadow
	@Final
	private LocalPlayer player;

	@Unique
	private Button jaymospowers$limbo;

	protected DeathScreenLimboMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void jaymospowers$addLimbo(CallbackInfo ci) {
		jaymospowers$limbo = null;
		if (player == null || exitButtons == null || exitButtons.isEmpty()) {
			return;
		}
		Button first = exitButtons.get(0);
		int lowest = first.getY();
		for (Button button : exitButtons) {
			lowest = Math.max(lowest, button.getY());
		}
		jaymospowers$limbo = Button.builder(Component.translatable("deathScreen.jaymospowers.limbo"), button -> {
			ClientPlayNetworking.send(new Limbo.Enter());
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) {
				minecraft.player.respawn();
			}
			minecraft.gui.setScreen(null);
		}).bounds(first.getX(), lowest + 24, first.getWidth(), first.getHeight()).build();
		jaymospowers$limbo.visible = false;
		jaymospowers$limbo.active = false;
		addRenderableWidget(jaymospowers$limbo);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymospowers$limboOnTick(CallbackInfo ci) {
		if (jaymospowers$limbo == null || player == null) {
			return;
		}
		boolean offered = player.hasAttached(Mortality.FINAL) && !player.hasAttached(Revival.WAITING);
		jaymospowers$limbo.visible = offered;
		jaymospowers$limbo.active = offered && exitButtons.stream().anyMatch(b -> b.active);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void jaymospowers$waiting(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Long until = player == null ? null : player.getAttached(Revival.WAITING);
		if (until == null || player.level() == null) {
			return;
		}
		long left = Math.max(0L, until - player.level().getGameTime());
		graphics.centeredText(this.font, Component.translatable("deathScreen.jaymospowers.waiting", (left + 19) / 20), this.width / 2,
			this.height / 4 + 48, 0xFFC8C8C8);
	}
}
