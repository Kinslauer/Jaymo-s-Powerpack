package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.NightCam;
import com.jaymoskingdoms.client.RestClient;
import com.jaymoskingdoms.client.films.FarmWakeFilmClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.5) A Sleep button above Leave Bed, shown while you rest at night, and dragging to look around ({@link RestClient}); while
 * the night runs by the drag swings the free view round the bed and the wheel zooms it ({@link NightCam}). While a farmhand's
 * wake film runs ({@link FarmWakeFilmClient}) the screen draws nothing and takes no keys or clicks (Escape would get you up
 * mid-talk), except Backspace, which skips the film.
 */
@Mixin(InBedChatScreen.class)
abstract class InBedChatScreenMixin extends ChatScreen {
	private InBedChatScreenMixin() {
		super("", false, false);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void jaymoskingdoms$sleepButton(CallbackInfo ci) {
		Button sleep = Button.builder(Component.translatable("gui.jaymoskingdoms.bed_rest_sleep"), button -> RestClient.pressSleep())
			.bounds(this.width / 2 - 100, this.height - 64, 200, 20)
			.build();
		this.addRenderableWidget(sleep);
		RestClient.sleepButton(sleep);
	}

	/** While the night runs by, the wheel brings the free view nearer or further ({@link NightCam}). */
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (NightCam.active()) {
			NightCam.zoom(scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (super.mouseDragged(event, dx, dy)) {
			return true;
		}
		RestClient.look(dx, dy);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!FarmWakeFilmClient.running()) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (FarmWakeFilmClient.running()) {
			if (event.key() == InputConstants.KEY_BACKSPACE) {
				FarmWakeFilmClient.skip();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return FarmWakeFilmClient.running() || super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return FarmWakeFilmClient.running() || super.mouseClicked(event, doubleClick);
	}
}
